# 공개 사이트를 다시 만들어 main에 합친 뒤, 바뀐 쪽을 검색 엔진에 바로 알려요(IndexNow: 네이버·빙·얀덱스 등).
# 쓰는 법: python3 indexnow.py <YWJMoney/- 폴더> [주소 경로 ...]   경로를 안 주면 sitemap.xml의 모든 주소를 보내요.
# 열쇠 파일은 공개 저장소 맨 위 <KEY>.txt(내용이 KEY와 같음)예요. 공개해도 되는 값이에요(누가 우리 사이트 주소를 대신 알릴 수 있을 뿐).
import json, os, re, sys, urllib.request
KEY = '751074329dd2075434ddb1304ff965d4'
HOST = 'bamnat.app'
ROOT = sys.argv[1] if len(sys.argv) > 1 else '../-'
paths = sys.argv[2:]
if paths:
    urls = [f'https://{HOST}/{p.lstrip("/")}' for p in paths]
else:
    urls = re.findall(r'<loc>(.*?)</loc>', open(os.path.join(ROOT, 'sitemap.xml'), encoding='utf-8').read())
body = json.dumps({'host': HOST, 'key': KEY, 'keyLocation': f'https://{HOST}/{KEY}.txt', 'urlList': urls}).encode()
for ep in ('https://searchadvisor.naver.com/indexnow', 'https://api.indexnow.org/indexnow'):
    req = urllib.request.Request(ep, data=body, headers={'Content-Type': 'application/json; charset=utf-8'})
    try:
        with urllib.request.urlopen(req, timeout=20) as r: print(ep, r.status)
    except urllib.error.HTTPError as e: print(ep, e.code, e.read()[:200])
    except Exception as e: print(ep, 'fail', e)
print(len(urls), 'urls')
