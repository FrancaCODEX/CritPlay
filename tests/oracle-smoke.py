"""Checks for the disposable CI stack; local TLS verification is skipped only here."""
import base64
import http.cookiejar
import json
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(
    urllib.request.HTTPCookieProcessor(jar),
    urllib.request.HTTPSHandler(context=ssl._create_unverified_context()),
)
base = 'https://localhost'
state_file = Path('/tmp/critplay-oracle-smoke.json')

def request(path, method='GET', data=None, headers=None):
    req = urllib.request.Request(base + path, method=method, data=data, headers=headers or {})
    try:
        response = opener.open(req, timeout=15)
    except urllib.error.HTTPError as response:
        return response.code, response.read(), response.headers
    return response.status, response.read(), response.headers

def get_json(path):
    status, data, _ = request(path)
    assert status == 200, (path, status, data)
    return json.loads(data)

def csrf():
    token = get_json('/api/auth/csrf')
    return {token['headerName']: token['token']}

def login():
    headers = csrf()
    headers['Content-Type'] = 'application/x-www-form-urlencoded'
    status, _, _ = request('/api/auth/login', 'POST', urllib.parse.urlencode({
        'username': 'oracle_ci', 'password': 'OracleCiPassword42!',
    }).encode(), headers)
    assert status == 200, status
    assert any(cookie.name == 'JSESSIONID' and cookie.secure for cookie in jar), 'Session cookie must be Secure'
    assert get_json('/api/auth/me')['authenticated']

for attempt in range(30):
    try:
        assert get_json('/api/health')['status'] == 'UP'
        break
    except (OSError, AssertionError):
        if attempt == 29:
            raise
        time.sleep(1)
status, _, _ = request('/favicon.ico')
assert status == 200
login()
phase = sys.argv[1]
if phase == 'seed':
    game = get_json('/api/games')[0]
    headers = csrf()
    headers['Content-Type'] = 'application/json'
    entry = {'status': 'COMPLETED', 'rating': 4, 'review': 'Persistência Oracle CI',
             'favorite': True, 'wishlist': False, 'startDate': '2026-01-01', 'finishDate': '2026-02-01'}
    status, _, _ = request('/api/diary/' + str(game['id']), 'PUT', json.dumps(entry).encode(), headers)
    assert status == 200
    boundary = 'critplay-oracle-test-boundary'
    image = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAIAAAACUFjqAAAAFElEQVR4nGN0yzNiwA2Y8MiNYGkAIi0A+uIAXFoAAAAASUVORK5CYII=')
    multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="test.png"\r\n'
                 'Content-Type: image/png\r\n\r\n').encode() + image + f'\r\n--{boundary}--\r\n'.encode()
    headers = csrf()
    headers['Content-Type'] = f'multipart/form-data; boundary={boundary}'
    status, body, _ = request('/api/images', 'POST', multipart, headers)
    assert status == 200, (status, body)
    state_file.write_text(json.dumps({'gameId': game['id'], 'imageUrl': json.loads(body)['url']}))
    # Spoofing the forwarded IP at the public proxy must not bypass the limiter.
    headers = csrf()
    headers['Content-Type'] = 'application/json'
    for n in range(6):
        headers['X-Forwarded-For'] = f'198.51.100.{n+1}'
        headers['Forwarded'] = f'for=198.51.100.{n+1};proto=http'
        status, _, _ = request('/api/auth/forgot-password', 'POST',
            json.dumps({'username': 'not_a_user', 'email': 'test@example.com'}).encode(), headers)
        if n < 5:
            assert status != 429, status
        else:
            assert status == 429, 'Spoofed forwarded headers bypassed rate limit'
else:
    state = json.loads(state_file.read_text())
    entries = get_json('/api/diary')
    saved = next(entry for entry in entries if entry['gameId'] == state['gameId'])
    assert saved['rating'] == 4 and saved['review'] == 'Persistência Oracle CI'
    assert saved['favorite'] and saved['finishDate'] == '2026-02-01'
    assert request(state['imageUrl'])[0] == 200, 'Upload was lost'
print('Oracle stack:', phase, 'passed (health, HTTPS, secure login, persistence and uploads).')
