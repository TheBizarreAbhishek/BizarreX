import requests
import re
URL = "https://drive.usercontent.google.com/download?id=14yCWezECjIHvF7KQwsveA_KgVTLB38U1&export=download"
session = requests.Session()
res = session.get(URL)
html = res.text

# Check Method
method = re.search(r'<form[^>]+method="([^"]+)"', html)
print("Form Method:", method.group(1) if method else "GET")

# Extract UUID
match_uuid = re.search(r'<input[^>]+name="uuid"[^>]+value="([^"]+)"', html)
if match_uuid:
    uuid = match_uuid.group(1)
    print("Found UUID:", uuid)
    
    # Download request!
    params = {
        'id': '14yCWezECjIHvF7KQwsveA_KgVTLB38U1',
        'export': 'download',
        'confirm': 't',
        'uuid': uuid
    }
    action = re.search(r'action="([^"]+)"', html).group(1)
    print("Action URL:", action)
    
    if (method and method.group(1).upper() == "POST"):
        res_dl = session.post(action, data=params, stream=True)
    else:
        res_dl = session.get(action, params=params, stream=True)
        
    print("DL Status:", res_dl.status_code)
    print("DL Content-Type:", res_dl.headers.get("Content-Type"))
    print("DL Content-Length:", res_dl.headers.get("Content-Length"))
else:
    print("UUID not found")
