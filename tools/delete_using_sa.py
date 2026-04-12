from google.oauth2.service_account import Credentials
from googleapiclient.discovery import build
import os

key_path = "/Users/abhishek/Downloads/bizarrex-8e0aaf574c16.json"
if os.path.exists(key_path):
    print("Found key!")
    creds = Credentials.from_service_account_file(key_path, scopes=["https://www.googleapis.com/auth/drive"])
    svc = build("drive", "v3", credentials=creds)
    try:
        f = svc.files().get(fileId="1H6lcbDU8cA6HtSDGsXnR6UtTvdHojqlS", fields="id,name,permissions").execute()
        print("Found duplicate folder:", f.get("name"))
        svc.files().delete(fileId="1H6lcbDU8cA6HtSDGsXnR6UtTvdHojqlS").execute()
        print("Deleted!")
    except Exception as e:
        print("Error:", e)
else:
    print("Key not found")
