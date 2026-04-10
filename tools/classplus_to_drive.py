#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BizarreX - Classplus to Google Drive Automation
Downloads all batch videos and uploads to Google Drive with folder structure.
Usage: python3 classplus_to_drive.py
"""

import os, sys, re, json, time, requests
from typing import Optional, List
from pathlib import Path
from tqdm import tqdm

# ── CONFIG ───────────────────────────────────────────────────────────────────
COURSE_ID        = "638946"
DRIVE_ROOT_ID    = "1hDmJnrrUwmUpLHviiYCLrSFQ8uLC7jfn"
DOWNLOAD_DIR     = Path("./classplus_downloads")
CREDENTIALS_FILE = Path("./drive_credentials.json")
# ─────────────────────────────────────────────────────────────────────────────

HEADERS = {
    "User-Agent":  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:149.0) Gecko/20100101 Firefox/149.0",
    "Accept":      "application/json",
    "Content-Type":"application/json",
    "region":      "IN",
    "deviceType":  "7",
    "appVersion":  "1.0.0",
    "api-version": "52",
}


# ── LOGIN ────────────────────────────────────────────────────────────────────

def try_post(url: str, payload: dict) -> dict:
    try:
        r = requests.post(url, json=payload, headers=HEADERS, timeout=15)
        raw = r.text.strip()
        if not raw:
            return {}
        return r.json()
    except Exception:
        return {}


def verify_otp(phone: str, otp: str) -> Optional[str]:
    endpoints = [
        ("https://api.classplusapp.com/v2/auth/student/login/verifyOtp",
         {"countryCode": "+91", "number": phone, "otp": otp}),
        ("https://api.classplusapp.com/v2/auth/otp/verify",
         {"countryCode": "+91", "mobileNo": phone, "otp": otp}),
    ]
    for url, payload in endpoints:
        data = try_post(url, payload)
        if data.get("status") == "success":
            d = data.get("data") or {}
            token = d.get("token") or d.get("accessToken") or data.get("token")
            if token:
                return token
    return None


def login() -> str:
    print("\n[LOGIN] Classplus")
    print("  Tip: Press Enter for phone, then paste JWT token directly")
    phone = input("  Mobile (Enter to skip): ").strip()
    val   = input("  OTP or JWT Token: ").strip()

    # JWT token pasted directly
    if val.startswith("eyJ") and len(val) > 100:
        print("  [OK] JWT token accepted!")
        return val

    # OTP flow
    if phone and val and len(val) <= 10:
        token = verify_otp(phone, val)
        if token:
            print("  [OK] Logged in via OTP!")
            return token

    # Final fallback
    token = input("  Paste JWT token: ").strip()
    if token.startswith("eyJ"):
        print("  [OK] Token accepted!")
        return token

    print("[ERR] No valid token. Exiting.")
    sys.exit(1)


# ── HEADERS ──────────────────────────────────────────────────────────────────

def authed(token: str) -> dict:
    return {
        "User-Agent":  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:149.0) Gecko/20100101 Firefox/149.0",
        "Accept":      "application/json, text/plain, */*",
        "region":      "IN",
        "api-version": "52",
        "x-access-token": token,
    }


# ── FETCH CONTENT ─────────────────────────────────────────────────────────────

def fetch_subjects(token: str) -> List[dict]:
    url = (f"https://api.classplusapp.com/v2/course/content/get"
           f"?courseId={COURSE_ID}&folderId=0&storeContentEvent=false")
    try:
        r = requests.get(url, headers=authed(token), timeout=20)
        print(f"  [{r.status_code}] subjects endpoint")
        if r.status_code == 401:
            print("  [WARN] 401 - token expired! Get fresh token from browser.")
            return []
        data = r.json()
        # Handle different response shapes
        inner = data.get("data") or {}
        if isinstance(inner, list):
            items = inner
        else:
            items = inner.get("courseContent") or []
        folders = [i for i in items if i.get("contentType") in (1, "1")]
        print(f"  Found {len(folders)} subject folders ({len(items)} total)")
        return folders
    except Exception as e:
        print(f"  [ERR] fetch_subjects: {e}")
        return []


def fetch_videos_in_folder(token: str, folder_id: str) -> List[dict]:
    url = (f"https://api.classplusapp.com/v2/course/content/get"
           f"?courseId={COURSE_ID}&folderId={folder_id}&storeContentEvent=false")
    try:
        r     = requests.get(url, headers=authed(token), timeout=20)
        data  = r.json()
        items = (data.get("data") or {}).get("courseContent") or []
    except Exception as e:
        print(f"  [ERR] fetch_videos_in_folder {folder_id}: {e}")
        return []

    videos = []
    for item in items:
        ct = item.get("contentType")
        if ct in (2, "2"):
            videos.append(item)
        elif ct in (1, "1"):
            time.sleep(0.3)
            videos.extend(fetch_videos_in_folder(token, str(item.get("id"))))
    return videos


def get_video_url(token: str, video_id: str, video_item: dict = None) -> Optional[str]:
    h = {
        "x-access-token": token,
        "region":         "IN",
        "Referer":        "https://web.classplusapp.com/",
        "User-Agent":     "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:149.0) Gecko/20100101 Firefox/149.0",
        "api-version":    "52",
    }
    v = video_item or {}

    # Only use contentHashId — avoid extra API calls that waste click limit
    content_id = v.get("contentHashId") or str(v.get("id", ""))

    try:
        url = f"https://api.classplusapp.com/cams/uploader/video/jw-signed-url?contentId={content_id}"
        r = requests.get(url, headers=h, timeout=15)
        time.sleep(2)  # ← 2s delay to avoid click limit
        if r.status_code == 200 and r.text.strip():
            data = r.json()
            result = data.get("url") or data.get("videoUrl") or data.get("hlsUrl")
            if result:
                return result
        elif r.status_code == 500:
            print(f"  [LOCKED] Server 500 — video may be content-locked by teacher")
    except Exception as e:
        print(f"  [ERR] get_video_url: {e}")

    # Fallback: direct url field (YouTube/external)
    direct_url = v.get("url")
    if direct_url and direct_url.startswith("http"):
        return direct_url

    return None





# ── DOWNLOAD ─────────────────────────────────────────────────────────────────

def sanitize(name: str) -> str:
    name = re.sub(r'[<>:"/\\|?*]', '', name)
    return re.sub(r'\s+', ' ', name).strip()[:100]


def download_video(url: str, out: Path, title: str) -> bool:
    import subprocess
    out.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "yt-dlp", "--no-warnings", "--quiet", "--progress",
        "-o", str(out),
        "--merge-output-format", "mp4",
        "--concurrent-fragments", "16",
        "--buffer-size", "16K",
        "--no-part", url
    ]
    print(f"  [DL] {title}")
    res = subprocess.run(cmd)
    if res.returncode == 0 and out.exists():
        print(f"  [OK] Downloaded ({out.stat().st_size/1024/1024:.1f} MB)")
        return True
    print(f"  [ERR] Download failed: {title}")
    return False


# ── DRIVE UPLOAD ──────────────────────────────────────────────────────────────

def get_drive_service():
    from google.oauth2.credentials import Credentials
    from google_auth_oauthlib.flow import InstalledAppFlow
    from google.auth.transport.requests import Request
    from googleapiclient.discovery import build

    SCOPES     = ["https://www.googleapis.com/auth/drive"]
    token_path = Path("./drive_token.json")
    creds      = None

    if token_path.exists():
        creds = Credentials.from_authorized_user_file(str(token_path), SCOPES)

    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            flow  = InstalledAppFlow.from_client_secrets_file(str(CREDENTIALS_FILE), SCOPES)
            creds = flow.run_local_server(port=0)
        with open(token_path, "w") as f:
            f.write(creds.to_json())

    return build("drive", "v3", credentials=creds)


def get_or_create_folder(svc, name: str, parent: str) -> str:
    q   = (f"name='{name}' and '{parent}' in parents "
           f"and mimeType='application/vnd.google-apps.folder' and trashed=false")
    res = svc.files().list(q=q, fields="files(id)").execute()
    if res.get("files"):
        return res["files"][0]["id"]
    meta   = {"name": name, "mimeType": "application/vnd.google-apps.folder", "parents": [parent]}
    folder = svc.files().create(body=meta, fields="id").execute()
    print(f"  [DIR] Created Drive folder: {name}")
    return folder["id"]


def upload_to_drive(svc, file_path: Path, folder_id: str) -> bool:
    from googleapiclient.http import MediaFileUpload

    name = file_path.name
    q    = f"name='{name}' and '{folder_id}' in parents and trashed=false"
    if svc.files().list(q=q, fields="files(id)").execute().get("files"):
        print(f"  [SKIP] Already on Drive: {name}")
        return True

    size_mb = file_path.stat().st_size / 1024 / 1024
    print(f"  [UP] Uploading: {name} ({size_mb:.1f} MB)")

    for attempt in range(3):  # 3 retries
        try:
            media = MediaFileUpload(str(file_path), mimetype="video/mp4",
                                    resumable=True, chunksize=5*1024*1024)
            req  = svc.files().create(body={"name": name, "parents": [folder_id]},
                                      media_body=media, fields="id")
            resp = None
            with tqdm(total=100, desc="  Upload", unit="%", leave=False) as pbar:
                prev = 0
                while resp is None:
                    status, resp = req.next_chunk()
                    if status:
                        now = int(status.progress() * 100)
                        pbar.update(now - prev)
                        prev = now
            print(f"  [OK] Uploaded: {name}")
            return True
        except Exception as e:
            print(f"  [WARN] Upload attempt {attempt+1}/3 failed: {e}")
            if attempt < 2:
                print(f"  Retrying in 5s...")
                time.sleep(5)
    print(f"  [ERR] Upload failed after 3 attempts: {name}")
    return False


# ── MAIN ──────────────────────────────────────────────────────────────────────

def main():
    print("=" * 60)
    print("  BizarreX - Classplus to Google Drive")
    print("=" * 60)

    token = login()
    DOWNLOAD_DIR.mkdir(exist_ok=True)

    print("\n[DRIVE] Connecting...")
    drive = get_drive_service()
    print("  [OK] Drive connected!")

    print(f"\n[FETCH] Course {COURSE_ID} content...")
    subjects = fetch_subjects(token)

    if not subjects:
        print("  [ERR] No subjects found. Check token or course ID.")
        return

    log_path = Path("./upload_log.json")
    log      = json.loads(log_path.read_text()) if log_path.exists() else {}

    for subj in subjects:
        subj_name = sanitize(subj.get("name") or subj.get("title") or "Unknown")
        subj_id   = str(subj.get("id"))

        print(f"\n\n[DIR] Subject: {subj_name}")
        print("-" * 40)

        drive_folder = get_or_create_folder(drive, subj_name, DRIVE_ROOT_ID)
        local_folder = DOWNLOAD_DIR / subj_name
        local_folder.mkdir(exist_ok=True)

        print(f"  Fetching videos...")
        videos = fetch_videos_in_folder(token, subj_id)
        print(f"  Found {len(videos)} videos")

        for idx, video in enumerate(videos, 1):
            vid_id    = str(video.get("id"))
            vid_title = sanitize(video.get("name") or video.get("title") or f"Lecture_{idx}")
            filename  = f"{idx:02d}. {vid_title}.mp4"
            local     = local_folder / filename

            print(f"\n  [{idx}/{len(videos)}] {vid_title}")

            # Debug: print first video structure once
            if idx == 1:
                print(f"  [DEBUG] Video keys: {list(video.keys())}")
                print(f"  [DEBUG] Video data: {json.dumps({k:v for k,v in video.items() if k != 'thumbnail'}, ensure_ascii=True)[:300]}")

            if log.get(vid_id) == "done":
                print(f"  [SKIP] Already processed")
                continue

            # Get URL first — skip Drive check if no URL available
            vid_url = get_video_url(token, vid_id, video)
            if not vid_url:
                print(f"  [WARN] No URL found for: {vid_title}")
                continue

            # Check Drive BEFORE downloading
            q_check = f"name='{filename}' and '{drive_folder}' in parents and trashed=false"
            if drive.files().list(q=q_check, fields="files(id)").execute().get("files"):
                print(f"  [SKIP] Already on Drive")
                log[vid_id] = "done"
                log_path.write_text(json.dumps(log, indent=2))
                continue

            if not local.exists():
                if not download_video(vid_url, local, vid_title):
                    continue

            if upload_to_drive(drive, local, drive_folder):
                log[vid_id] = "done"
                log_path.write_text(json.dumps(log, indent=2))
                local.unlink()
                print(f"  [DEL] Local file removed (space saved)")

            time.sleep(0.5)


    print("\n\n[DONE] All videos uploaded to Google Drive!")


if __name__ == "__main__":
    main()
