#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Classplus → Google Drive sync (hierarchical, daily-limited).
- Maintains Classplus folder structure in Drive
- Stops after --max-videos-total (default 35) videos per run
- Skips already-uploaded videos (recursive Drive search)
- 2s delay between URL fetches to respect 150 click/day limit
"""

import os, sys, re, json, time, requests, argparse, subprocess
from typing import Optional, Set
from pathlib import Path

# ── CONFIG ────────────────────────────────────────────────────────────────────
COURSE_ID     = "638946"
DRIVE_ROOT_ID = "1hDmJnrrUwmUpLHviiYCLrSFQ8uLC7jfn"
DOWNLOAD_DIR  = Path("./downloads")
DEFAULT_DAILY_LIMIT = 35   # Well under 150 click/day limit
# ─────────────────────────────────────────────────────────────────────────────

TOKEN         = os.environ.get("CLASSPLUS_TOKEN", "")
REFRESH_TOKEN = os.environ.get("GDRIVE_REFRESH_TOKEN", "")
CLIENT_ID     = os.environ.get("GDRIVE_CLIENT_ID", "")
CLIENT_SECRET = os.environ.get("GDRIVE_CLIENT_SECRET", "")

CP_HEADERS = {
    "User-Agent":     "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:149.0) Gecko/20100101 Firefox/149.0",
    "region":         "IN",
    "api-version":    "52",
    "x-access-token": TOKEN,
}
VID_HEADERS = {**CP_HEADERS, "Referer": "https://web.classplusapp.com/"}


def sanitize(name: str) -> str:
    name = re.sub(r'[<>:"/\\|?*]', '', name)
    return re.sub(r'\s+', ' ', name).strip()[:100]


# ── DRIVE ─────────────────────────────────────────────────────────────────────

def get_drive_service():
    from google.oauth2.credentials import Credentials
    from googleapiclient.discovery import build

    resp = requests.post("https://oauth2.googleapis.com/token", data={
        "client_id":     CLIENT_ID,
        "client_secret": CLIENT_SECRET,
        "refresh_token": REFRESH_TOKEN,
        "grant_type":    "refresh_token",
    }, timeout=15)
    data = resp.json()
    if "access_token" not in data:
        raise Exception(f"[ERR] Token refresh failed: {data}")

    print(f"  [AUTH] Got access token (type: {data.get('token_type','?')})")
    creds = Credentials(
        token=data["access_token"],
        refresh_token=REFRESH_TOKEN,
        token_uri="https://oauth2.googleapis.com/token",
        client_id=CLIENT_ID,
        client_secret=CLIENT_SECRET,
    )
    return build("drive", "v3", credentials=creds)


def get_or_create_folder(svc, name: str, parent: str) -> str:
    safe = name.replace("'", "\\'")
    q = (f"name='{safe}' and '{parent}' in parents "
         f"and mimeType='application/vnd.google-apps.folder' and trashed=false")
    res = svc.files().list(q=q, fields="files(id,name)").execute()
    if res.get("files"):
        fid = res["files"][0]["id"]
        print(f"  [FOLDER] Using: {name} ({fid})")
        return fid
    
    # Fallback to fuzzy search due to unpredictable Classplus Name Typos (e.g., '2.0' vs '2.O')
    q_all = f"'{parent}' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false"
    res_all = svc.files().list(q=q_all, fields="files(id,name)").execute()
    
    def normalize(text):
         import re
         return re.sub(r'[\s.\-]', '', text.lower()).replace('o', '0')
    
    norm_name = normalize(name)
    for f in res_all.get("files", []):
         if normalize(f["name"]) == norm_name:
             print(f"  [FOLDER] Fuzzy Matched: {name} to {f['name']} ({f['id']})")
             return f["id"]

    f = svc.files().create(
        body={"name": name, "mimeType": "application/vnd.google-apps.folder", "parents": [parent]},
        fields="id"
    ).execute()
    print(f"  [FOLDER] Created: {name} ({f['id']})")
    return f["id"]


def get_uploaded_filenames(svc, folder_id: str, depth: int=0) -> Set[str]:
    """Get all filenames in the specific Drive folder."""
    q = (f"'{folder_id}' in parents "
         f"and mimeType!='application/vnd.google-apps.folder' and trashed=false")
    names: Set[str] = set()
    page_token = None
    while True:
        r = svc.files().list(q=q, fields="files(name)", pageSize=500,
                             pageToken=page_token).execute()
        for f in r.get("files", []):
            names.add(f["name"])
        page_token = r.get("nextPageToken")
        if not page_token:
            break
    indent = "  " * depth
    print(f"{indent}[DRIVE] {len(names)} files already on Drive in this folder")
    return names


def upload_to_drive(svc, file_path: Path, folder_id: str) -> bool:
    from googleapiclient.http import MediaFileUpload
    name = file_path.name
    size_mb = file_path.stat().st_size / 1024 / 1024
    print(f"  [UP] {name} ({size_mb:.1f} MB)")
    for attempt in range(3):
        try:
            media = MediaFileUpload(str(file_path), mimetype="video/mp4",
                                    resumable=True, chunksize=8*1024*1024)
            req = svc.files().create(
                body={"name": name, "parents": [folder_id]},
                media_body=media, fields="id"
            )
            resp = None
            chunk = 0
            while resp is None:
                status, resp = req.next_chunk()
                chunk += 1
                if status and chunk % 5 == 0:
                    print(f"  [UP] {int(status.progress()*100)}%")
            print(f"  [OK] Uploaded: {name}")
            return True
        except Exception as e:
            print(f"  [ERR] Attempt {attempt+1}/3: {e}")
            if attempt < 2:
                time.sleep(5)
    return False


# ── CLASSPLUS API ─────────────────────────────────────────────────────────────

def fetch_folder_items(folder_id: str) -> list:
    url = (f"https://api.classplusapp.com/v2/course/content/get"
           f"?courseId={COURSE_ID}&folderId={folder_id}&storeContentEvent=false")
    try:
        r = requests.get(url, headers=CP_HEADERS, timeout=20)
        if r.status_code == 401:
            print("[ERR] Classplus token expired! Update CLASSPLUS_TOKEN secret.")
            sys.exit(1)
        data = r.json().get("data") or {}
        return (data.get("courseContent") if isinstance(data, dict) else data) or []
    except Exception as e:
        print(f"[ERR] fetch_folder_items({folder_id}): {e}")
        return []


def get_video_url(video_item: dict) -> Optional[str]:
    content_id = video_item.get("contentHashId") or str(video_item.get("id", ""))
    url = f"https://api.classplusapp.com/cams/uploader/video/jw-signed-url?contentId={content_id}"
    try:
        r = requests.get(url, headers=VID_HEADERS, timeout=15)
        time.sleep(2)  # Respect 150 click/day limit
        if r.status_code == 200 and r.text.strip():
            data = r.json()
            return data.get("url") or data.get("videoUrl") or data.get("hlsUrl")
        if r.status_code == 500:
            # Check if locked
            locked = video_item.get("isContentLocked") or video_item.get("isLocked")
            print(f"  [LOCKED] Content locked by teacher" if locked else f"  [ERR] 500 from server")
    except Exception as e:
        print(f"  [ERR] get_video_url: {e}")

    # Fallback: direct URL (YouTube etc.)
    direct = video_item.get("url", "")
    if direct and direct.startswith("http"):
        return direct
    return None


def download_video(url: str, out: Path, title: str) -> bool:
    out.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "yt-dlp", "--no-warnings", "--quiet", "--progress",
        "-o", str(out), "--merge-output-format", "mp4",
        "--concurrent-fragments", "16", "--no-part", url
    ]
    print(f"  [DL] {title}")
    res = subprocess.run(cmd)
    if res.returncode == 0 and out.exists():
        print(f"  [OK] {out.stat().st_size/1024/1024:.1f} MB")
        return True
    return False


# ── RECURSIVE TREE PROCESSOR ──────────────────────────────────────────────────

def process_folder_tree(svc, classplus_folder_id: str, drive_folder_id: str,
                         stats: dict, limit: int, depth: int = 0):
    """
    Recursively mirrors Classplus folder into Drive.
    - sub-folders → create matching Drive folder, recurse
    - videos → skip if already uploaded, else download + upload
    """
    if stats["done"] >= limit:
        return

    # Pre-load uploaded filenames for this directory to skip existing
    uploaded = get_uploaded_filenames(svc, drive_folder_id, depth)

    items = fetch_folder_items(classplus_folder_id)
    video_num = 0  # Per-folder counter for filename prefix

    for item in items:
        if stats["done"] >= limit:
            break

        ct = item.get("contentType")
        name = sanitize(item.get("name") or "Unknown")

        if ct in (1, "1"):  # Sub-folder → recurse
            indent = "  " * depth
            print(f"{indent}[DIR] {name}")
            child_drive = get_or_create_folder(svc, name, drive_folder_id)
            time.sleep(0.3)
            process_folder_tree(svc, str(item["id"]), child_drive,
                                  stats, limit, depth + 1)

        elif ct in (2, "2"):  # Video
            video_num += 1
            vid_title = sanitize(item.get("name") or f"Lecture_{video_num}")
            filename  = f"{video_num:02d}. {vid_title}.mp4"
            indent    = "  " * depth

            print(f"\n{indent}[{video_num}] {vid_title}")

            if filename in uploaded:
                print(f"{indent}[SKIP] Already on Drive")
                continue

            # Fetch signed URL (counts as 1 click toward 150/day limit)
            vid_url = get_video_url(item)
            if not vid_url:
                print(f"{indent}[SKIP] No URL (locked or unavailable)")
                continue

            local = DOWNLOAD_DIR / filename
            if not local.exists():
                if not download_video(vid_url, local, vid_title):
                    continue

            if upload_to_drive(svc, local, drive_folder_id):
                uploaded.add(filename)
                stats["done"] += 1
                print(f"{indent}[PROGRESS] {stats['done']}/{limit} videos uploaded today")
                try:
                    local.unlink()
                except Exception:
                    pass

            time.sleep(0.5)


# ── MAIN ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--subject-index", type=int, default=None,
                        help="Process only this subject (0-based). Default: all.")
    parser.add_argument("--max-videos", type=int, default=DEFAULT_DAILY_LIMIT,
                        help=f"Max videos to upload total this run (default: {DEFAULT_DAILY_LIMIT})")
    args = parser.parse_args()

    if not TOKEN:
        print("[ERR] CLASSPLUS_TOKEN env var not set!")
        sys.exit(1)
    if not REFRESH_TOKEN or not CLIENT_ID or not CLIENT_SECRET:
        print("[ERR] Missing GDRIVE_* env vars!")
        sys.exit(1)

    print("[DRIVE] Connecting...")
    svc = get_drive_service()
    print("[OK] Drive connected!\n")

    print("[FETCH] Getting subjects...")
    top_items = fetch_folder_items("0")
    subjects = [i for i in top_items if i.get("contentType") in (1, "1")]
    print(f"[INFO] Found {len(subjects)} subjects\n")

    if not subjects:
        print("[ERR] No subjects found — token expired?")
        sys.exit(1)

    to_process = ([subjects[args.subject_index]] if args.subject_index is not None
                  else subjects)

    DOWNLOAD_DIR.mkdir(exist_ok=True)
    stats = {"done": 0}

    for subj in to_process:
        if stats["done"] >= args.max_videos:
            print(f"\n[LIMIT] Reached {args.max_videos} videos for today. Will resume tomorrow!")
            break

        subj_name = sanitize(subj.get("name") or "Unknown")
        print(f"\n{'='*50}")
        print(f"  Subject: {subj_name}")
        print(f"{'='*50}")

        drive_folder = get_or_create_folder(svc, subj_name, DRIVE_ROOT_ID)

        # Process root folder directly
        process_folder_tree(
            svc=svc,
            classplus_folder_id=str(subj["id"]),
            drive_folder_id=drive_folder,
            stats=stats,
            limit=args.max_videos,
        )

    print(f"\n[DONE] Uploaded {stats['done']} videos this run.")


if __name__ == "__main__":
    main()
