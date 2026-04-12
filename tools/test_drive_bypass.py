import requests
import re

URL = "https://docs.google.com/uc?export=download&id=14yCWezECjIHvF7KQwsveA_KgVTLB38U1"
session = requests.Session()

# 1. First request
res = session.get(URL, allow_redirects=False)
print("1. docs.google.com status:", res.status_code)
print("1. Location:", res.headers.get("Location"))
print("1. Set-Cookie:", res.headers.get("Set-Cookie", ""))

# Follow redirect manually
next_url = res.headers.get("Location")
if next_url:
    res2 = session.get(next_url, allow_redirects=False)
    print("2. drive.usercontent status:", res2.status_code)
    print("2. Content-Type:", res2.headers.get("Content-Type"))
    
    if 'html' in str(res2.headers.get("Content-Type")):
        html = res2.text
        match = re.search(r'confirm=([a-zA-Z0-9_-]+)', html)
        if match:
            token = match.group(1)
            print("2. Extracted token:", token)
            
            # Now retry with token using session cookies
            final_url = URL + f"&confirm={token}"
            print("3. Retrying URL:", final_url)
            print("3. Sending Cookies:", session.cookies.get_dict())
            
            res3 = session.get(final_url, allow_redirects=False)
            print("4. status:", res3.status_code)
            print("4. Location:", res3.headers.get("Location"))
            
            if res3.status_code == 303:
                res4 = session.get(res3.headers.get("Location"), allow_redirects=False)
                print("5. FINAL status:", res4.status_code)
                print("5. FINAL length:", res4.headers.get("Content-Length"))
                print("5. FINAL Type:", res4.headers.get("Content-Type"))
        else:
            print("2. Token NOT FOUND in HTML")
            print("HTML sample:", html[:500])

