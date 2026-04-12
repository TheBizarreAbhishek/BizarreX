import requests

def get_confirm_token(response):
    for key, value in response.cookies.items():
        if key.startswith('download_warning'):
            return value
    return None

def test_drive():
    URL = "https://docs.google.com/uc?export=download&id=1u3eE5VJ0tNtLiKnJ4_fI3pOH8nktAwNN"
    session = requests.Session()
    # Initial request
    response = session.get(URL, stream=True)
    print("Initial Redirect ->", response.url)
    
    token = get_confirm_token(response)
    print("Found token:", token)
    
    if token:
        params = { 'id': '1u3eE5VJ0tNtLiKnJ4_fI3pOH8nktAwNN', 'export': 'download', 'confirm': token }
        response = session.get(response.url, params=params, stream=True, headers={'Range': 'bytes=0-1000'})
        print("Final Status:", response.status_code)
        print("Final Content-Type:", response.headers.get('Content-Type'))
test_drive()
