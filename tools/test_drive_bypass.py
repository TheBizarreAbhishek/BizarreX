import requests
import sys

def download_file_from_google_drive(id):
    URL = "https://docs.google.com/uc?export=download&confirm=t"
    session = requests.Session()
    response = session.get(URL, params = { 'id' : id }, stream = True)
    token = get_confirm_token(response)
    print("Token: ", token)
    if token:
        params = { 'id' : id, 'confirm' : token }
        response = session.get(URL, params = params, stream = True)
    print("Response: ", response.status_code, response.headers.get('Content-Type'))
    print("Length: ", response.headers.get('Content-Length'))

def get_confirm_token(response):
    for key, value in response.cookies.items():
        if key.startswith('download_warning'):
            return value
    return None

if __name__ == "__main__":
    download_file_from_google_drive(sys.argv[1])
