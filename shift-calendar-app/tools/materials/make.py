"""밤낮 재료(배경 100·색 아이콘 200)를 다시 만들어요. 남의 그림·사진·생성 AI 그림 없이 이 폴더의 코드로만 그려요(라이선스: 밤낮).

    cd shift-calendar-app/tools/materials
    python3 make.py            # out/bg, out/bg-thumb, out/icon, out/목록.json
    python3 make.py --list     # 그림은 그대로 두고 목록만 다시

- 배경: bg.py(numpy·PIL), 970×2048 WebP, 한 장 150KB 이하(서버 사진 한도 160KB, 앱은 2048px 넘으면 줄여서 읽어요).
- 아이콘: icons/*.mjs(SVG) → Chromium으로 256px 투명 PNG → WebP.
- 목록의 id는 그림 바이트 SHA-256 앞 16자리(서버 recipe_images id와 같음). 서버에 넣기는 server/company-worker/tools/materials-seed.mjs.
"""
import hashlib, json, os, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'out')
sys.path.insert(0, HERE)


def sha16(path):
    return hashlib.sha256(open(path, 'rb').read()).hexdigest()[:16]


def one_bg(s):
    out = os.path.join(OUT, 'bg', s['id'] + '.webp')
    r = subprocess.run([sys.executable, os.path.join(HERE, 'bg.py'), json.dumps(s['spec']), out], capture_output=True, text=True)
    if r.returncode: raise SystemExit(r.stderr[-800:])
    Image.open(out).convert('RGB').resize((216, 456), Image.LANCZOS).save(os.path.join(OUT, 'bg-thumb', s['id'] + '.webp'), 'WEBP', quality=80, method=6)
    return s['id'], json.loads(r.stdout)


def icons():
    png = os.path.join(OUT, '_png'); os.makedirs(png, exist_ok=True)
    root = subprocess.run(['npm', 'root', '-g'], capture_output=True, text=True).stdout.strip()
    subprocess.run(['node', 'build.mjs', 'set1,set2,set3', os.path.join(OUT, '_icons.png'), png, os.path.join(OUT, '_icons.json')],
                   cwd=os.path.join(HERE, 'icons'), env={**os.environ, 'PWROOT': root}, check=True)
    for f in os.listdir(png):
        Image.open(os.path.join(png, f)).save(os.path.join(OUT, 'icon', f[:-4] + '.webp'), 'WEBP', quality=90, method=6, alpha_quality=95)


def manifest():
    from bg_specs import BACKGROUNDS
    items = []
    fam_ord = {}
    for i, s in enumerate(BACKGROUNDS):
        f = os.path.join(OUT, 'bg', s['id'] + '.webp'); t = os.path.join(OUT, 'bg-thumb', s['id'] + '.webp')
        im = Image.open(f).convert('L'); light = sum(im.resize((16, 32)).getdata()) / (16 * 32 * 255)
        fam_ord[s['family']] = fam_ord.get(s['family'], 0) + 1
        items.append(dict(key=s['id'], kind='bg', pack=s['family'], name=s['name'], ord=fam_ord[s['family']], file=f'bg/{s["id"]}.webp',
                          thumb=f'bg-thumb/{s["id"]}.webp', id=sha16(f), thumbId=sha16(t), light=round(light, 3), w=im.width, h=im.height))
    meta = json.load(open(os.path.join(OUT, '_icons.json'))) if os.path.exists(os.path.join(OUT, '_icons.json')) else json.load(open(os.path.join(HERE, 'icons', 'icons.json')))
    ord_ = {}
    for m in meta:
        f = os.path.join(OUT, 'icon', m['id'] + '.webp')
        ord_[m['group']] = ord_.get(m['group'], 0) + 1
        items.append(dict(key=m['id'], kind='icon', pack=m['group'], name=m['name'], ord=ord_[m['group']], file=f'icon/{m["id"]}.webp', id=sha16(f), w=256, h=256))
    json.dump(dict(license='밤낮 자체 제작(tools/materials 코드로 그림). 남의 그림·사진·생성 AI 그림 없음', items=items),
              open(os.path.join(OUT, '목록.json'), 'w'), ensure_ascii=False, indent=1)
    print(len(items), '개 목록')


if __name__ == '__main__':
    for d in ('bg', 'bg-thumb', 'icon'): os.makedirs(os.path.join(OUT, d), exist_ok=True)
    if '--list' not in sys.argv:
        from bg_specs import BACKGROUNDS
        with ThreadPoolExecutor(4) as ex:
            for k, r in ex.map(one_bg, BACKGROUNDS): print(k, r)
        icons()
    manifest()
