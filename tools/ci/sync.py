#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Classplus -> Google Drive sync script for GitHub Actions.
Uses Service Account auth (no browser needed).
Usage: python3 sync.py --subject-index <N>
"""

import os, sys, re, json, time, requests, argparse
from typing import Optional, List
from pathlib import Path
from tqdm import tqdm

# ── CONFIG ───────────────────────────────────────────────────────────────────
COURSE_ID     = "638946"
DRIVE_ROOT_ID = "1hDmJnrrUwmUpLHviiYCLrSFQ8uLC7jfn"
DOWNLOAD_DIR  = Path("./downloads")
# ─────────────────────────────────────────────────────────────────────────────

TOKEN = os.environ.get("CLASSPLUS_TOKEN", "")
SA_KEY = os.environ.get("GDRIVE_SA_KEY", "")

HEADERS = {
    "User-Agent":  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:149.0) Gecko/20100101 Firefox/149.0",
    "Accept":      "application/json, text/plain, */*",
    "region":      "IN",
    "api-version": "52",
    "x-access-token": TOKEN,
}

VIDEO_HEADERS = {
    "x-access-token": TOKEN,
    "region":         "IN",
    "Referer":        "https://web.classplusapp.com/",
    "User-Agent":     "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:149.0) Gecko/20100101 Firefox/149.0",
    "api-version":    "52",
}


def sanitize(name: str) -> str:
    name = re.sub(r'[<>:"/\\|?*]', '', name)
    return re.sub(r'\s+', ' ', name).strip()[:100]


# ── DRIVE (Service Account) ───────────────────────────────────────────────────

def get_drive_service():
    from google.oauth2 import service_account
    from googleapiclient.discovery import build

    sa_info = json.loads(SA_KEY)
    creds = service_account.Credentials.from_service_account_info(
        sa_info,
        scopes=["https://www.googleapis.com/auth/drive"]
    )
    return build("drive", "v3", credentials=creds)


def get_or_create_folder(svc, name: str, parent: str) -> str:
    q = (f"name='{name}' and '{parent}' in parents "
         f"and mimeType='application/vnd.google-apps.folder' and trashed=false")
    res = svc.files().list(q=q, fields="files(id,name)").execute()
    print(f"  [FOLDER] Search '{name}': found {len(res.get('files',[]))} results")
    if res.get("files"):
        fid = res["files"][0]["id"]
        print(f"  [FOLDER] Using existing: {name} ({fid})")
        return fid
    meta = {"name": name, "mimeType": "application/vnd.google-apps.folder", "parents": [parent]}
    f = svc.files().create(body=meta, fields="id").execute()
    print(f"  [FOLDER] Created new: {name} ({f['id']})")
    return f["id"]


def upload_to_drive(svc, file_path: Path, folder_id: str) -> bool:
    from googleapiclient.http import MediaFileUpload

    name = file_path.name
    size_mb = file_path.stat().st_size / 1024 / 1024
    print(f"  [UP] Starting upload: {name} ({size_mb:.1f} MB) -> folder {folder_id}")

    q = f"name='{name}' and '{folder_id}' in parents and trashed=false"
    existing = svc.files().list(q=q, fields="files(id)").execute().get("files")
    if existing:
        print(f"  [SKIP] Already on Drive: {name}")
        return True

    for attempt in range(3):
        try:
            media = MediaFileUpload(str(file_path), mimetype="video/mp4",
                                    resumable=True, chunksize=8*1024*1024)
            req = svc.files().create(
                body={"name": name, "parents": [folder_id]},
                media_body=media, fields="id"
            )
            resp = None
            chunk_count = 0
            while resp is None:
                status, resp = req.next_chunk()
                chunk_count += 1
                if status and chunk_count % 5 == 0:
                    print(f"  [UP] Progress: {int(status.progress()*100)}%")
            print(f"  [OK] Uploaded: {name}")
            return True
        except Exception as e:
            print(f"  [ERR] Upload attempt {attempt+1}/3 failed: {type(e).__name__}: {e}")
            if attempt < 2:
                time.sleep(5)
    return False



# ── CLASSPLUS API ─────────────────────────────────────────────────────────────

def fetch_subjects() -> List[dict]:
    url = (f"https://api.classplusapp.com/v2/course/content/get"
           f"?courseId={COURSE_ID}&folderId=0&storeContentEvent=false")
    r = requests.get(url, headers=HEADERS, timeout=20)
    items = (r.json().get("data") or {}).get("courseContent") or []
    folders = [i for i in items if i.get("contentType") in (1, "1")]
    print(f"[INFO] Found {len(folders)} subjects")
    return folders


def fetch_videos(folder_id: str) -> List[dict]:
    url = (f"https://api.classplusapp.com/v2/course/content/get"
           f"?courseId={COURSE_ID}&folderId={folder_id}&storeContentEvent=false")
    try:
        r = requests.get(url, headers=HEADERS, timeout=20)
        items = (r.json().get("data") or {}).get("courseContent") or []
    except Exception as e:
        print(f"[ERR] fetch_videos {folder_id}: {e}")
        return []

    videos = []
    for item in items:
        ct = item.get("contentType")
        if ct in (2, "2"):
            videos.append(item)
        elif ct in (1, "1"):
            time.sleep(0.3)
            videos.extend(fetch_videos(str(item.get("id"))))
    return videos


def get_video_url(video_item: dict) -> Optional[str]:
    content_hash = video_item.get("contentHashId") or str(video_item.get("id"))
    url = f"https://api.classplusapp.com/cams/uploader/video/jw-signed-url?contentId={content_hash}"
    try:
        r = requests.get(url, headers=VIDEO_HEADERS, timeout=15)
        if r.status_code == 200:
            return r.json().get("url")
    except Exception as e:
        print(f"[ERR] get_video_url: {e}")
    return None


def download_video(url: str, out: Path, title: str) -> bool:
    import subprocess
    out.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "yt-dlp", "--no-warnings", "--quiet", "--progress",
        "-o", str(out),
        "--merge-output-format", "mp4",
        "--concurrent-fragments", "16",
        "--no-part", url
    ]
    print(f"[DL] {title}")
    res = subprocess.run(cmd)
    if res.returncode == 0 and out.exists():
        print(f"[OK] {out.stat().st_size/1024/1024:.1f} MB")
        return True
    return False


# ── MAIN ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--subject-index", type=int, default=None)
    parser.add_argument("--max-videos", type=int, default=None,
                        help="Max videos per subject (for testing)")
    args = parser.parse_args()

    if not TOKEN:
        print("[ERR] CLASSPLUS_TOKEN env var not set!")
        sys.exit(1)
    if not SA_KEY:
        print("[ERR] GDRIVE_SA_KEY env var not set!")
        sys.exit(1)

    print("[DRIVE] Connecting with service account...")
    svc = get_drive_service()
    print("[OK] Drive connected!")

    print("[FETCH] Getting subjects...")
    subjects = fetch_subjects()
    if not subjects:
        print("[ERR] No subjects found!")
        sys.exit(1)

    # Select subjects to process
    if args.subject_index is not None:
        if args.subject_index >= len(subjects):
            print(f"[ERR] Subject index {args.subject_index} out of range (0-{len(subjects)-1})")
            sys.exit(1)
        to_process = [subjects[args.subject_index]]
    else:
        to_process = subjects

    log_path = Path("upload_log.json")
    log = json.loads(log_path.read_text()) if log_path.exists() else {}

    DOWNLOAD_DIR.mkdir(exist_ok=True)

    for subj in to_process:
        subj_name = sanitize(subj.get("name") or "Unknown")
        subj_id   = str(subj.get("id"))

        print(f"\n=== {subj_name} ===")
        drive_folder = get_or_create_folder(svc, subj_name, DRIVE_ROOT_ID)
        local_folder = DOWNLOAD_DIR / subj_name
        local_folder.mkdir(exist_ok=True)

        videos = fetch_videos(subj_id)
        if args.max_videos:
            videos = videos[:args.max_videos]
        print(f"[INFO] {len(videos)} videos")

        for idx, video in enumerate(videos, 1):
            vid_id    = str(video.get("id"))
            vid_title = sanitize(video.get("name") or f"Lecture_{idx}")
            filename  = f"{idx:02d}. {vid_title}.mp4"
            local     = local_folder / filename

            print(f"\n[{idx}/{len(videos)}] {vid_title}")

            # Check Drive FIRST — skip download if already uploaded
            q = f"name='{filename}' and '{drive_folder}' in parents and trashed=false"
            if svc.files().list(q=q, fields="files(id)").execute().get("files"):
                print(f"[SKIP] Already on Drive")
                continue

            vid_url = get_video_url(video)
            if not vid_url:
                print(f"[WARN] No URL for: {vid_title}")
                continue

            if not local.exists():
                if not download_video(vid_url, local, vid_title):
                    continue

            if upload_to_drive(svc, local, drive_folder):
                local.unlink()

            time.sleep(0.5)


    print("\n[DONE] Finished!")


if __name__ == "__main__":
    main()
