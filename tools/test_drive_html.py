import requests
URL = "https://drive.usercontent.google.com/download?id=14yCWezECjIHvF7KQwsveA_KgVTLB38U1&export=download"
res = requests.get(URL)
html = res.text
import re
links = re.findall(r'href="([^"]+)"', html)
print("HREF LINKS:")
for l in links: print(l)
forms = re.findall(r'action="([^"]+)"', html)
print("FORM ACTIONS:")
for f in forms: print(f)
inputs = re.findall(r'<input[^>]+name="([^"]+)"[^>]+value="([^"]*)"', html)
print("INPUTS:")
for name, value in inputs: print(name, "=", value)
