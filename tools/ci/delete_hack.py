import sys
import sync

svc = sync.get_drive_service()
duplicate_id = '1H6lcbDU8cA6HtSDGsXnR6UtTvdHojqlS'  # 2.O UNIT-1

try:
    print(f"Attempting to delete duplicate folder {duplicate_id}")
    svc.files().delete(fileId=duplicate_id).execute()
    print("Deleted successfully!")
except Exception as e:
    print("Deletion failed:", e)

