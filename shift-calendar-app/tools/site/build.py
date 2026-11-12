#!/usr/bin/env python3
"""공개 사이트 bamnat.app 만들기 [WEB-22][WEB-23].

    python3 build.py <YWJMoney/- 폴더> [--today 2026-11-12] [--og]   # 공개 저장소 폴더에 다시 만들기
    python3 build.py --check                                          # 임시 폴더에 만들어 검사·시험만(CI)
    python3 build.py <폴더> --verify                                    # 만들지 않고 4의 검사만

하는 일
1. static/(calc.css·calc.js·home.css·home.js·robots.txt)을 공개 폴더로 옮겨요. calc.js의 '자료를 못 받을 때 값'은
   공개 폴더의 remote/v1/rates.json을 그대로 채워 넣어요(손으로 적지 않아요).
2. public_pages.py → print_pages.py → r_page.py → home_page.py를 한 프로세스에서 차례로 돌려요(쪽 목록을 함께 쌓아요).
3. sitemap.xml: 쪽 목록은 새로, 고친 날(lastmod)은 내용이 바뀐 쪽만 오늘로.
4. 검사: 공개 저장소에 둘 수 있는 파일만 있는지, 공개 파일에 내부 주소·비밀 이름·도구 경로·추적 번호가 없는지,
   지난해 숫자가 남지 않았는지. 하나라도 걸리면 실패해요. 그리고 '공개로 나가는 파일 목록'(git 기준 바뀐 것)을 찍어요.
5. --check: 저장소의 remote/v1로 임시 폴더에 만들고 4의 검사와 node 시험(test/*.test.mjs)을 돌려요.
"""
import argparse
import glob
import hashlib
import json
import os
import re
import runpy
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_REMOTE = os.path.normpath(os.path.join(HERE, '../../../remote/v1'))  # bamnat-code/remote/v1 (공개 저장소 remote/v1과 같은 파일)

# 공개 저장소에 있어도 되는 것(맨 위 이름). 이 밖의 파일이 섞이면 실패해요.
ALLOWED_TOP = {'.git', '.nojekyll', 'README.md', 'index.html', 'sitemap.xml', 'robots.txt', 'privacy.html',
               'assets', 'calc', 'shift', 'holiday', 'form', 'r', 'og', 'css', 'privacy', 'terms', 'community', 'remote'}
INDEXNOW_KEY = re.compile(r'^[0-9a-f]{32}\.txt$')
# 이 도구가 만드는 곳(아래 글자 검사를 받는 곳)
GENERATED = ('index.html', 'sitemap.xml', 'robots.txt', 'calc/', 'shift/', 'holiday/', 'form/', 'r/', 'assets/home.', 'og/')
TEXT_EXT = ('.html', '.js', '.css', '.txt', '.xml', '.json', '.svg')
# 공개 파일에 있으면 안 되는 글자: 내부 주소, 비밀 이름, 개발 저장소·도구 경로, 내부 문서 이름
FORBIDDEN = ['10.0.2.2', 'localhost', '127.0.0.1', '/api/admin', 'ADMIN_PASSWORD', 'RATE_SALT', 'MAIL_API_KEY', 'PLAY_SERVICE_ACCOUNT',
             'BAMNAT_OPS_KEY', 'keystore', 'shift-calendar-app', 'bamnat-code', 'tools/site', 'core/src', 'docs/plans', 'tracker.csv',
             '갈래', 'HANDOFF']
TRACKER_ID = re.compile(r'\[(?:[A-Z][A-Z0-9]*\d*-)+[A-Z0-9]*\d[a-z]?\]')
# 공개로 내보낼 때 주석의 추적 번호([WEB-12] 같은 것)는 지워요. 소스에는 남아 있어 개발 기록과 이어져요.
SCRUB = re.compile(r'(?:\[(?:[A-Z][A-Z0-9]*\d*-)+[A-Z0-9]*\d[a-z]?\]\s?)+')


def scrub(text):
    return SCRUB.sub('', text)


def h(path):
    try:
        return hashlib.sha256(open(path, 'rb').read()).hexdigest()
    except FileNotFoundError:
        return None


def page_file(root, url):
    return os.path.join(root, url, 'index.html') if url else os.path.join(root, 'index.html')


def copy_static(root):
    rates = json.load(open(os.path.join(root, 'remote/v1/rates.json'), encoding='utf-8'))['data']
    fallback = json.dumps({'rates': rates['rates'], 'pensionBases': rates.get('pensionBases', [])}, ensure_ascii=False, separators=(',', ':'))
    for src in glob.glob(os.path.join(HERE, 'static', '**', '*'), recursive=True):
        if os.path.isdir(src): continue
        rel = os.path.relpath(src, os.path.join(HERE, 'static'))
        dst = os.path.join(root, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        t = open(src, encoding='utf-8').read()
        if rel == os.path.join('calc', 'calc.js'):
            assert '/*@rates*/null/*@*/' in t
            t = t.replace('/*@rates*/null/*@*/', '/*@rates*/' + fallback + '/*@*/')
        open(dst, 'w', encoding='utf-8').write(scrub(t))


def patch_privacy_html(root):
    """[WEB-19] 다른 앱(살림왕) 처리방침 보관본이 밤낮 것으로 검색되지 않게: noindex와 밤낮 처리방침 안내 한 줄."""
    p = os.path.join(root, 'privacy.html')
    if not os.path.exists(p): return
    t = open(p, encoding='utf-8').read()
    if 'name="robots"' not in t:
        t = t.replace('<meta charset="utf-8" />', '<meta charset="utf-8" />\n    <meta name="robots" content="noindex" />', 1)
    if 'data-bamnat-note' not in t:
        t = t.replace('<main>', '<main>\n      <p data-bamnat-note style="padding:10px 12px;border-radius:10px;background:#f2eee5;color:#15192b">'
                      '이 문서는 다른 앱(살림왕)의 처리방침 보관본이에요. 밤낮 개인정보 처리방침은 '
                      '<a href="https://bamnat.app/privacy/">bamnat.app/privacy</a>에 있어요.</p>', 1)
    open(p, 'w', encoding='utf-8').write(t)


def generate(root, today=None):
    if today: os.environ['BAMNAT_SITE_TODAY'] = today
    sys.path.insert(0, HERE)
    sys.argv = [sys.argv[0], root]
    for name in [m for m in list(sys.modules) if m == 'site_common']: del sys.modules[name]
    before = {}
    sm = os.path.join(root, 'sitemap.xml')
    old_urls = re.findall(r'<loc>https://bamnat\.app/(.*?)</loc>', open(sm, encoding='utf-8').read()) if os.path.exists(sm) else []
    for u in old_urls: before[u] = h(page_file(root, u))
    copy_static(root)
    for g in ('public_pages.py', 'print_pages.py', 'r_page.py', 'home_page.py'):
        runpy.run_path(os.path.join(HERE, g), run_name='__main__')
    import site_common as sc
    # 만든 쪽에서 추적 번호를 지워요(소스 주석에만 남아요).
    for u in sc.SITEMAP + ['', 'r/']:
        f = page_file(root, u)
        if os.path.exists(f):
            t = open(f, encoding='utf-8').read(); s = scrub(t)
            if s != t: open(f, 'w', encoding='utf-8').write(s)
    patch_privacy_html(root)
    sc.write_sitemap()
    # 고친 날: 내용이 바뀌었거나 새로 생긴 쪽만 오늘로
    t = open(sm, encoding='utf-8').read()
    def lm(m):
        u = m.group(1)
        if u in ('privacy/', 'terms/', 'community/'): return m.group(0)
        changed = before.get(u, 'new') != h(page_file(root, u))
        return f'<loc>https://bamnat.app/{u}</loc><lastmod>{sc.LASTMOD if changed else m.group(2)}</lastmod>'
    open(sm, 'w', encoding='utf-8').write(re.sub(r'<loc>https://bamnat\.app/(.*?)</loc><lastmod>(.*?)</lastmod>', lm, t))
    return sc


def check(root, sc):
    problems = []
    for name in os.listdir(root):
        if name not in ALLOWED_TOP and not INDEXNOW_KEY.match(name):
            problems.append(f'공개 저장소에 둘 수 없는 파일: {name}')
    for dirpath, dirs, files in os.walk(root):
        if '.git' in dirs: dirs.remove('.git')
        for f in files:
            full = os.path.join(dirpath, f); rel = os.path.relpath(full, root).replace(os.sep, '/')
            if rel.endswith(('.py', '.mjs', '.kt', '.md')) and rel != 'README.md':
                problems.append(f'코드·문서 파일은 공개하지 않아요: {rel}')
            if not rel.startswith(GENERATED) or not rel.endswith(TEXT_EXT): continue
            t = open(full, encoding='utf-8', errors='replace').read()
            for w in FORBIDDEN:
                if w in t: problems.append(f'{rel}: 공개 파일에 "{w}"')
            m = TRACKER_ID.search(t)
            if m: problems.append(f'{rel}: 추적 번호 {m.group(0)}')
            # [WEB-22] 지난해 숫자: 공휴일 쪽(해마다 쪽이 따로 있음) 말고는 올해보다 앞선 'NNNN년'이 없어야 해요.
            if rel.endswith('.html') and not rel.startswith('holiday/'):
                for y in set(re.findall(r'(20\d\d)년', t)):
                    if int(y) < sc.THIS: problems.append(f'{rel}: 지난해 "{y}년"')
    return problems


def public_list(root):
    """공개로 나가는 파일(공개 저장소 git 기준으로 바뀌었거나 새로 생긴 것)."""
    try:
        out = subprocess.run(['git', '-C', root, 'status', '--porcelain', '--untracked-files=all'], capture_output=True, text=True, check=True).stdout
    except Exception:
        return None
    return [l[3:] for l in out.splitlines() if l.strip()]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('root', nargs='?')
    ap.add_argument('--today')
    ap.add_argument('--og', action='store_true')
    ap.add_argument('--check', action='store_true')
    ap.add_argument('--verify', action='store_true')
    a = ap.parse_args()
    if a.check:
        tmp = tempfile.mkdtemp(prefix='bamnat-site-')
        try:
            shutil.copytree(REPO_REMOTE, os.path.join(tmp, 'remote/v1'))
            sc = generate(tmp, a.today)
            problems = check(tmp, sc)
            n = sum(len(f) for _, _, f in os.walk(tmp))
            print(f'만든 파일 {n}개 (임시 폴더)')
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
        if problems:
            print('\n'.join(problems)); sys.exit(1)
        r = subprocess.run(['node', '--test'] + sorted(glob.glob(os.path.join(HERE, 'test', '*.test.mjs'))))
        sys.exit(r.returncode)
    if not a.root: ap.error('공개 저장소 폴더를 적어 주세요 (예: ../-)')
    root = os.path.abspath(a.root)
    if a.verify:
        if a.today: os.environ['BAMNAT_SITE_TODAY'] = a.today
        sys.path.insert(0, HERE); sys.argv = [sys.argv[0], root]
        import site_common as sc
        problems = check(root, sc)
        if problems: print('\n'.join(problems)); sys.exit(1)
        print('검사 통과'); return
    sc = generate(root, a.today)
    if a.og:
        subprocess.run(['node', os.path.join(HERE, 'og_images.js'), root, ','.join(map(str, sc.HOL_YEARS))], check=True)
    problems = check(root, sc)
    files = public_list(root)
    if files is not None:
        print(f'공개로 나가는 파일 {len(files)}개 (공개 저장소 git 기준 바뀐 것):')
        for f in files: print('  ' + f)
    if problems:
        print('\n'.join(problems)); sys.exit(1)
    print('검사 통과: 공개 파일 목록·금지 글자·연도')


if __name__ == '__main__':
    main()
