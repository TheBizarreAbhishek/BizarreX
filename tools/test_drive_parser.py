import requests
import re
session = requests.Session()
res = session.get("https://docs.google.com/uc?export=download&id=1tBPhWRS_mG_hG_Cq6iJvG-YqIfIu2wZ8")
print(res.status_code)
html = res.text
print("HTML snippet containing 'confirm=':")
if 'confirm=' in html:
    idx = html.find('confirm=')
    print(html[idx-20:idx+60])
else:
    print("NO CONFIRM= FOUND")
match = re.search(r'confirm=([a-zA-Z0-9_-]+)', html)
if match:
    print("Matched:", match.group(1))
