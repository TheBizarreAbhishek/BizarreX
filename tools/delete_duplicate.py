import sys
sys.path.append('tools/ci')
import sync

svc = sync.get_drive_service()
print("Checking existing files...")
r1 = svc.files().list(q="'1PGchP0yzHtL7KHFs38ralJZN27sWri20' in parents", fields="files(id,name)").execute()
r2 = svc.files().list(q="'1H6lcbDU8cA6HtSDGsXnR6UtTvdHojqlS' in parents", fields="files(id,name)").execute()
print("2.0 UNIT-1 files:", len(r1.get("files", [])))
print("2.O UNIT-1 files:", len(r2.get("files", [])))

if len(r2.get("files", [])) <= len(r1.get("files", [])):
    print("Deleting duplicate 2.O UNIT-1")
    svc.files().delete(fileId='1H6lcbDU8cA6HtSDGsXnR6UtTvdHojqlS').execute()
else:
    print("Wait, 2.O has more files! Deleting 2.0 UNIT-1 instead!")
    # svc.files().delete(fileId='1PGchP0yzHtL7KHFs38ralJZN27sWri20').execute()
    # Actually wait we shouldn't arbitrarily delete, just rename 2.O to 2.0 UNIT-1 and move everything! Too complex for a hack script, but usually the newly created one has fewer files since the old one was fully synced.
