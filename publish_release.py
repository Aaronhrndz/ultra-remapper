import subprocess
import urllib.request
import json
import os

def get_github_token():
    p = subprocess.Popen(['git', 'credential', 'fill'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, _ = p.communicate(input='protocol=https\nhost=github.com\n')
    for line in out.splitlines():
        if line.startswith('password='):
            return line.split('=', 1)[1].strip()
    return None

token = get_github_token()
if not token:
    print('ERROR: No token found')
    exit(1)

with open(r'C:\temp\ultra-remapper\release_notes_1.5.0.md', 'r', encoding='utf-8') as f:
    notes = f.read()

payload = {
    'tag_name': 'v1.5.0-beta.1',
    'target_commitish': 'main',
    'name': 'Ultra Remapper v1.5.0-beta.1 (Linterna RGBW, Bisel y Botón Superior)',
    'body': notes,
    'draft': False,
    'prerelease': True
}

req = urllib.request.Request(
    'https://api.github.com/repos/Aaronhrndz/ultra-remapper/releases',
    data=json.dumps(payload).encode('utf-8'),
    headers={
        'Authorization': f'token {token}',
        'Accept': 'application/vnd.github.v3+json',
        'User-Agent': 'UltraRemapperReleaseScript'
    }
)

with urllib.request.urlopen(req) as resp:
    res = json.loads(resp.read().decode('utf-8'))
    release_id = res['id']
    upload_url = res['upload_url'].split('{')[0]
    print(f'Created Release ID: {release_id}')

apk_path = r'C:\temp\ultra-remapper\ultra-remapper-v1.5.0-beta.1.apk'
with open(apk_path, 'rb') as f:
    apk_data = f.read()

upload_req = urllib.request.Request(
    f'{upload_url}?name=ultra-remapper-v1.5.0-beta.1.apk',
    data=apk_data,
    headers={
        'Authorization': f'token {token}',
        'Content-Type': 'application/vnd.android.package-archive',
        'User-Agent': 'UltraRemapperReleaseScript'
    }
)

with urllib.request.urlopen(upload_req) as resp:
    up_res = json.loads(resp.read().decode('utf-8'))
    print('APK Uploaded successfully:', up_res.get('browser_download_url'))
