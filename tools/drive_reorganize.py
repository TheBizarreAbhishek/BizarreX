#!/usr/bin/env python3
"""
Reorganizes flat Drive folder into Classplus-matching hierarchy.
Moves files (no re-upload) and renames with per-unit numbering.
"""
import re, sys
from pathlib import Path
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build

DRIVE_ROOT_ID   = "1hDmJnrrUwmUpLHviiYCLrSFQ8uLC7jfn"
SUBJECT_NAME    = "Engg. Maths-II"

# Maps filename prefix → (batch_folder, unit_folder)
MAPPING = [
    (r"Maths-II U-1",  "Maths-II 2.0", "2.0 UNIT-1"),
    (r"Maths-II U-2",  "Maths-II 2.0", "2.0 UNIT-2"),
    (r"Maths-II U-3",  "Maths-II 2.0", "2.0 UNIT-3"),
    (r"M-2 U-1",       "Maths-II 1.0", "UNIT-1"),
    (r"M-2 U-2",       "Maths-II 1.0", "UNIT-2"),
    (r"M-2 U-3",       "Maths-II 1.0", "UNIT-3"),
    (r"M-2 U-4",       "Maths-II 1.0", "UNIT-4"),
    (r"M-2 U-5",       "Maths-II 1.0", "UNIT-5"),
]

def connect():
    creds = Credentials.from_authorized_user_file(
        str(Path(__file__).parent / "drive_token.json"),
        ["https://www.googleapis.com/auth/drive"]
    )
    return build("drive", "v3", credentials=creds)

def get_or_create_folder(svc, name, parent):
    q = f"name='{name}' and '{parent}' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false"
    res = svc.files().list(q=q, fields="files(id,name)").execute().get("files", [])
    if res:
        print(f"  [EXISTS] {name}")
        return res[0]["id"]
    f = svc.files().create(body={"name": name, "mimeType": "application/vnd.google-apps.folder", "parents": [parent]}, fields="id").execute()
    print(f"  [CREATED] {name} → {f['id']}")
    return f["id"]

def list_all_files(svc, folder_id):
    files, page_token = [], None
    while True:
        r = svc.files().list(
            q=f"'{folder_id}' in parents and mimeType!='application/vnd.google-apps.folder' and trashed=false",
            fields="files(id,name)", pageSize=200, pageToken=page_token
        ).execute()
        files.extend(r.get("files", []))
        page_token = r.get("nextPageToken")
        if not page_token:
            break
    return files

def classify(filename):
    """Returns (batch, unit) or None if unrecognized."""
    for pattern, batch, unit in MAPPING:
        if re.search(pattern, filename, re.IGNORECASE):
            return batch, unit
    return None, None

def main():
    print("Connecting to Drive...")
    svc = connect()

    # Find subject folder
    q = f"name='{SUBJECT_NAME}' and '{DRIVE_ROOT_ID}' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false"
    res = svc.files().list(q=q, fields="files(id,name)").execute().get("files", [])
    if not res:
        print(f"[ERR] Folder '{SUBJECT_NAME}' not found in Drive root!")
        sys.exit(1)
    subject_id = res[0]["id"]
    print(f"[OK] Found: {SUBJECT_NAME} ({subject_id})")

    # Get all flat files
    files = list_all_files(svc, subject_id)
    print(f"[INFO] {len(files)} files to reorganize\n")

    # Group by (batch, unit)
    groups = {}
    unmatched = []
    for f in files:
        batch, unit = classify(f["name"])
        if batch:
            key = (batch, unit)
            groups.setdefault(key, []).append(f)
        else:
            unmatched.append(f)

    if unmatched:
        print(f"[WARN] {len(unmatched)} unrecognized files:")
        for f in unmatched:
            print(f"  {f['name']}")
        print()

    # Create folder structure and move files
    folder_cache = {}
    for (batch, unit), file_list in sorted(groups.items()):
        print(f"\n>>> {SUBJECT_NAME} / {batch} / {unit} ({len(file_list)} files)")

        if batch not in folder_cache:
            batch_id = get_or_create_folder(svc, batch, subject_id)
            folder_cache[batch] = batch_id
        batch_id = folder_cache[batch]

        if (batch, unit) not in folder_cache:
            unit_id = get_or_create_folder(svc, unit, batch_id)
            folder_cache[(batch, unit)] = unit_id
        unit_id = folder_cache[(batch, unit)]

        # Sort by current number prefix, then renumber within unit
        def sort_key(f):
            m = re.match(r"(\d+)\.", f["name"])
            return int(m.group(1)) if m else 9999

        file_list.sort(key=sort_key)

        for new_idx, f in enumerate(file_list, 1):
            old_name = f["name"]
            # Strip old global index, add new per-unit index
            clean = re.sub(r"^\d+\.\s*", "", old_name)
            new_name = f"{new_idx:02d}. {clean}"

            print(f"  [{new_idx:02d}] {old_name} → {new_name}")
            # Move to new folder AND rename
            svc.files().update(
                fileId=f["id"],
                addParents=unit_id,
                removeParents=subject_id,
                body={"name": new_name},
                fields="id,name,parents"
            ).execute()

    print("\n[DONE] Reorganization complete!")

if __name__ == "__main__":
    main()
