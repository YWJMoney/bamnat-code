# 공개 사이트(bamnat.app) 만드는 도구들이 함께 쓰는 것: 공개 저장소 폴더, 오늘·올해, 원격 자료, 머리·바닥, 쪽 틀.
# public_pages.py·print_pages.py·home_page.py가 `from site_common import *`로 써요. build.py가 한 프로세스 안에서 차례로 돌려요
# (쪽 목록 SITEMAP을 함께 쌓아 마지막에 sitemap.xml을 한 번 써요).
import datetime as dt
import html
import json
import os
import re
import sys

ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else '../-')  # YWJMoney/- 저장소를 받아 둔 곳
SITE = 'https://bamnat.app/'
# [WEB-22] 올해는 도구를 돌리는 날에서 정해요. 같은 결과를 다시 만들 때는 BAMNAT_SITE_TODAY=2026-11-12처럼 날을 박아요.
TODAY = dt.date.fromisoformat(os.environ.get('BAMNAT_SITE_TODAY') or dt.date.today().isoformat())
THIS = TODAY.year
LASTMOD = TODAY.isoformat()  # build.py가 내용이 바뀐 쪽에만 이 날을 남겨요


def remote(name):
    return json.load(open(os.path.join(ROOT, 'remote/v1', name + '.json'), encoding='utf-8'))['data']


RATES = remote('rates')
HOLS = remote('holidays')
PRESETS = remote('presets')['patterns']
# 공휴일 쪽: 올해부터 자료가 있는 해까지(1~2월에는 지난해도, 연말 정산·수당 확인으로 찾는 분이 있어요).
HOL_YEARS = [y for y in range(max(HOLS['fromYear'], THIS - 1 if TODAY.month <= 2 else THIS), HOLS['toYear'] + 1)]
# 인쇄 달력은 올해와 (공휴일 자료가 있으면) 내년. 10월부터는 내년을 먼저 보여 줘요.
PRINT_YEARS = [y for y in (THIS, THIS + 1) if y in HOL_YEARS] or [THIS]
PRINT_YEAR = PRINT_YEARS[-1] if TODAY.month >= 10 else PRINT_YEARS[0]
HOL_YEAR = PRINT_YEAR  # 메뉴에 걸 공휴일 해(연도 없는 /holiday/ 쪽이 늘 올해·내년을 보여 줘요)


def rate(year, key):
    """그해 1월 1일 기준 값(빈 칸은 앞 해 값)."""
    v = None
    for r in sorted(RATES['rates'], key=lambda r: r['from']):
        if r['from'] <= f'{year}-12-31' and r.get(key) is not None:
            v = r[key]
    return v


RATE_YEARS = sorted({int(r['from'][:4]) for r in RATES['rates'] if int(r['from'][:4]) >= THIS}) or [THIS]
NEXT = RATE_YEARS[1] if len(RATE_YEARS) > 1 else None  # 고시된 내년(없으면 None)


def pct(x, places=3):
    """0.0475 → '4.75%', 0.05 → '5%'."""
    s = f'{x * 100:.{places}f}'.rstrip('0').rstrip('.')
    return s + '%'


def josa(word, pair):
    """[WEB-21] 받침을 보고 조사를 골라요: josa('4조 2교대 8일', '는/은') → '4조 2교대 8일은'."""
    a, b = pair.split('/')
    c = re.sub(r'[\s)\]]+$', '', str(word))[-1:]
    code = ord(c) if c else 0
    if 0xAC00 <= code <= 0xD7A3:
        batchim = (code - 0xAC00) % 28 > 0
    else:
        batchim = bool(re.match(r'[0136781lmnr]', c, re.I))
    has_b = a in ('은', '이', '을', '과', '아')
    return word + ((a if batchim else b) if has_b else (b if batchim else a))


# ---------- 공통 머리·바닥 (웹 2판, 2026-11-10) ----------
LOGO = ('<svg viewBox="0 0 64 64" aria-hidden="true"><g stroke="#f2ece1" stroke-linecap="round">'
        + ''.join(f'<line x1="32" y1="{3 if i % 6 == 0 else 5}" x2="32" y2="{8 if i % 6 == 0 else 7.2}" stroke-width="{2.2 if i % 6 == 0 else 1.4}" opacity="{1 if i % 6 == 0 else .5}" transform="rotate({i * 15} 32 32)"/>' for i in range(24))
        + '</g><circle cx="32" cy="32" r="18" fill="none" stroke="#f2ece1" stroke-width="2.2"/>'
        '<path class="dial-day" d="M14 32a18 18 0 0 1 36 0z" fill="#f2ece1"/><circle cx="32" cy="41" r="2.4" fill="#c2a36b"/></svg>')
PLAY_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3.6v16.8c0 .8.9 1.3 1.6.9l14.1-8.4c.7-.4.7-1.4 0-1.8L6.6 2.7C5.9 2.3 5 2.8 5 3.6z" fill="currentColor"/></svg>'
PLAY = 'https://play.google.com/store/apps/details?id=com.bamnat.app'
NAV = [('calc/', '무료 계산기'), ('shift/', '교대 근무 달력'), ('holiday/', '공휴일'), ('form/', '근무표 양식')]
MENU = [('계산기', [('calc/juhyu/', '주휴수당'), ('calc/alba/', '알바 월급'), ('calc/allowance/', '야간·연장·휴일수당'), ('calc/week/', '한 주 연장·52시간'), ('calc/ordinary/', '통상임금·명세서'), ('calc/daily/', '일당'),
                  ('calc/salary/', '실수령액'), ('calc/leave/', '연차'), ('calc/severance/', '퇴직금'), ('calc/jobless/', '실업급여'), ('calc/parental/', '출산·육아휴직'), ('calc/boss/', '사장님 인건비'), ('calc/', '계산기 모두')]),
        ('근무 달력', [('shift/', '교대 근무 달력 만들기'), ('shift/print/', f'{PRINT_YEAR} 달력 인쇄'), ('form/', '근무표 엑셀 양식'), ('holiday/', '공휴일·연휴')]),
        # 2.0.0 [RVR-34] 길잡이 글: 법 조문 출처와 계산 예시(guide_pages.py)
        ('길잡이', [('guide/shift-4j2k-pay/', '4조 2교대 수당'), ('guide/nurse-night-pay/', '간호사 나이트 수당'), ('guide/holiday-work-pay/', '공휴일 근무 수당'),
                  ('guide/juhyu-not-paid/', '주휴수당 못 받는 경우'), ('guide/', '길잡이 모두')])]


def _here(path, u):
    return path == u or (u not in ('calc/', 'shift/', 'form/', 'holiday/', 'guide/') and path.startswith(u))


def site_header(up, path=''):
    nav = ''.join(f'<a href="{up}{u}"' + (' aria-current="page"' if path.startswith(u.split("/")[0] + '/') else '') + f'>{t}</a>' for u, t in NAV)
    # [U50-17] 휴대폰 메뉴 안에서도 지금 쪽을 표시해요.
    menu = ''.join(f'<li class="grp" role="presentation">{g}</li>' + ''.join(
        f'<li><a href="{up}{u}"' + (' aria-current="page"' if _here(path, u) else '') + f'>{t}</a></li>' for u, t in items) for g, items in MENU)
    return f'''<a class="skip" href="#main">본문으로</a>
<header class="site-header">
  <div class="in">
    <a class="brand" href="{up}" aria-label="밤낮 처음으로">{LOGO}<span>밤낮</span></a>
    <nav class="site-nav" aria-label="사이트">{nav}</nav>
    <span class="grow"></span>
    <a class="get" data-play="header" href="{PLAY}">{PLAY_ICON}앱 받기</a>
    <button class="menu-btn" type="button" aria-label="메뉴 열기" aria-expanded="false" aria-controls="site-menu"><svg class="bars" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16"/></svg><svg class="x" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg></button>
  </div>
  <nav class="menu" id="site-menu" aria-label="전체 메뉴" inert><div><ul>{menu}</ul></div></nav>
</header>'''


def site_footer(up):
    col = lambda g, items: f'<div><h2>{g}</h2><ul>' + ''.join(f'<li><a href="{up}{u}">{t}</a></li>' for u, t in items) + '</ul></div>'
    return f'''<footer class="site-footer">
  <div class="in">
    <div class="top">
      <div class="about"><a class="brand" href="{up}">{LOGO}<span>밤낮</span></a><p>교대·일근·알바·현장·가게·투자, 사장님과 팀 리더까지. 근무가 있는 모든 사람의 근무 앱이에요.</p></div>
      {col('계산기', MENU[0][1][:7])}
      {col('근무 달력', MENU[1][1])}
      {col('안내', [('privacy/', '개인정보 처리방침'), ('terms/', '이용약관'), ('community/', '광장 운영 규칙')])}
    </div>
    <div class="biz">
      문의 <a href="mailto:yoj0790@naver.com">yoj0790@naver.com</a> (앱 안에서는 설정 › 도움말과 문의)<br>
      상호 밤낮 · 대표자 양원준 · 사업자등록번호 327-25-02565 (<a href="https://www.ftc.go.kr/bizCommPop.do?wrkr_no=3272502565" target="_blank" rel="noopener">사업자 정보 확인</a>)<br>
      통신판매업 신고번호 제2026-전남여수-0424호 · 전남광주통합특별시 여수시 안산1길 179, 2동 105호<br>
      전화 010-6408-8825 · 전자우편 yoj0790@naver.com · 호스팅 Cloudflare, Inc., GitHub, Inc.
      <div class="safe">계산기·달력에 넣은 값은 이 화면에서만 쓰고 어디에도 보내지 않아요.</div>
    </div>
  </div>
</footer>'''


# 앱으로 이어 가기 카드에 보일 스토어 화면 (assets/app/<이름>-360|720.webp)과 [WEB-20] 그림마다 다른 설명
SHOTS = {'pay': ('05-pay', '밤낮 앱의 이번 달 예상 월급 화면'), 'field': ('09-field', '밤낮 앱의 현장 일정·일당 화면'),
         'leave': ('12-leave', '밤낮 앱의 남은 연차 화면'), 'holiday': ('11-holiday', '밤낮 앱의 공휴일이 표시된 근무 달력'),
         'calendar': ('01-calendar', '밤낮 앱의 한 달 근무 달력'), 'roster': ('02-roster', '밤낮 앱의 근무표 사진 넣기 화면'),
         'team': ('06-team', '밤낮 앱의 팀 근무표 화면'), 'boss': ('07-boss', '밤낮 앱의 사장님 직원 근무 화면')}


def cta_shot(c):
    if c.startswith('holiday'): return SHOTS['holiday']
    if c in ('daily', 'form_site_days'): return SHOTS['field']
    if c == 'leave': return SHOTS['leave']
    if c == 'form_duty': return SHOTS['roster']
    if c == 'form_shift_team': return SHOTS['team']
    if c in ('form_alba_week', 'boss'): return SHOTS['boss']
    if c in ('juhyu', 'allowance', 'week', 'ordinary', 'alba', 'salary', 'severance', 'jobless', 'parental'): return SHOTS['pay']
    if c == 'guide_leave': return SHOTS['leave']
    if c == 'guide_daily': return SHOTS['field']
    if c == 'guide_nurse': return SHOTS['roster']
    if c.startswith('guide'): return SHOTS['pay']
    return SHOTS['calendar']


def kind_of(path):
    return ('print' if path.startswith('shift/print/') else 'shift' if path.startswith('shift/') else 'form' if path.startswith('form/')
            else 'holiday' if path.startswith('holiday/') else 'guide' if path.startswith('guide/') else 'calc')


def hero_tags(path):
    k = kind_of(path)
    t = {'print': ['열두 달 A4 한 장', '공휴일 표시', '로그인 없이'], 'shift': ['석 달 달력', '같은 조에 링크로', '로그인 없이'],
         'form': ['인쇄·엑셀 무료', '로그인 없이'], 'holiday': ['대체공휴일 포함', '교대 근무자 쉬는 날'],
         'calc': ['무료', '로그인 없이', '입력값은 보내지 않아요'], 'guide': ['법 조문 출처', '계산기와 같은 식']}[k]
    return ''.join(f'<span>{x}</span>' for x in t)


def og_image(path):
    # [WEB-21] 공휴일은 그해 그림, 양식은 양식 그림.
    k = kind_of(path)
    if k == 'holiday':
        m = re.match(r'holiday/(\d{4})/', path)
        return f'holiday-{m.group(1)}' if m else f'holiday-{HOL_YEAR}'
    return {'print': 'shift', 'shift': 'shift', 'form': 'form', 'calc': 'calc', 'guide': 'calc'}[k]


def page(path, title, desc, h1, lead, body, script, faqs, campaign, cta_title, cta_text, depth, crumbs=None, head=''):
    up = '../' * depth
    canonical = SITE + path
    assert len(desc) <= 80, ('설명은 80자 안으로(네이버 사이트 진단)', path, len(desc), desc)
    assert len(title) <= 60, ('제목은 60자 안으로(검색 결과에서 잘리지 않게)', path, len(title), title)
    k = kind_of(path)
    # [RVR-34] 길잡이 글은 글(Article)이에요. 날짜는 넣지 않아요(빌드 날마다 쪽이 바뀐 것으로 보이지 않게).
    ld = [{"@context": "https://schema.org", "@type": "Article", "headline": h1, "description": desc, "url": canonical, "inLanguage": "ko",
           "mainEntityOfPage": canonical, "image": f"{SITE}og/{og_image(path)}.png", "author": {"@type": "Organization", "name": "밤낮"},
           "publisher": {"@type": "Organization", "name": "밤낮"}}] if k == 'guide' and path != 'guide/' else [{
        "@context": "https://schema.org", "@type": "CollectionPage", "name": h1, "url": canonical, "inLanguage": "ko", "description": desc}] if k == 'guide' else [{
        "@context": "https://schema.org", "@type": "WebApplication", "name": h1, "url": canonical,
        "applicationCategory": "FinanceApplication" if k == 'calc' else "UtilitiesApplication", "operatingSystem": "Any", "inLanguage": "ko",
        "offers": {"@type": "Offer", "price": "0", "priceCurrency": "KRW"},
        "publisher": {"@type": "Organization", "name": "밤낮"}
    }]
    if faqs:
        ld.append({"@context": "https://schema.org", "@type": "FAQPage",
                   "mainEntity": [{"@type": "Question", "name": q, "acceptedAnswer": {"@type": "Answer", "text": a}} for q, a in faqs]})
    if crumbs is None:
        if path.startswith('shift/') and path != 'shift/':
            crumbs = [(f'{up}shift/', '교대 근무 달력')] + ([(f'{up}shift/print/', '인쇄')] if path.startswith('shift/print/') and path != 'shift/print/' else [])
        elif path.startswith('form/') and path != 'form/':
            crumbs = [(f'{up}form/', '근무표 양식')]
        elif path.startswith('holiday/') and path != 'holiday/':
            crumbs = [(f'{up}holiday/', '공휴일')]
        elif path.startswith('guide/') and path != 'guide/':
            crumbs = [(f'{up}guide/', '길잡이')]
        elif path in ('shift/', 'form/', 'calc/', 'holiday/', 'guide/'):
            crumbs = []
        else:
            crumbs = [(f'{up}calc/', '무료 계산기')]
    trail = [(SITE, '밤낮')] + [(SITE + u[len(up):], t) for u, t in crumbs] + [(canonical, h1)]
    ld.append({"@context": "https://schema.org", "@type": "BreadcrumbList",
               "itemListElement": [{"@type": "ListItem", "position": i + 1, "name": t, "item": u} for i, (u, t) in enumerate(trail)]})
    faq_html = ('<h2>자주 묻는 것</h2>\n' + '\n'.join(f'<details><summary>{html.escape(q)}</summary><p>{html.escape(a)}</p></details>' for q, a in faqs)) if faqs else ''
    shot, shot_alt = cta_shot(campaign)
    crumb_html = f'<a href="{up}">밤낮</a>' + ''.join(f'<span class="sep">/</span><a href="{u}">{t}</a>' for u, t in crumbs)
    # [WEB-21] 계산하는 쪽에만 '참고용 계산' 안내를 붙여요.
    note = ('<p class="note" style="margin-top:18px">참고용 계산이에요. 실제 지급액은 근로계약·회사 규정·세법 적용에 따라 달라질 수 있어요. '
            '요율은 밤낮 앱과 같은 공개 자료(최저임금 고시, 4대보험 요율, 근로소득 간이세액표)를 써요.</p>') if k == 'calc' and path != 'calc/' else (
        '<p class="note" style="margin-top:18px">법과 고용노동부·국세청 안내를 쉬운 말로 옮긴 글이에요. 근로계약·취업규칙에 따라 다를 수 있으니, '
        '내 경우가 궁금하면 고용노동부 고객상담센터(국번 없이 1350)에 물어보세요.</p>') if k == 'guide' and path != 'guide/' else ''
    return f'''<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(desc)}">
<link rel="canonical" href="{canonical}">
<meta name="robots" content="max-image-preview:large">
<meta name="theme-color" content="#15192b">
<meta property="og:type" content="website">
<meta property="og:site_name" content="밤낮">
<meta property="og:title" content="{html.escape(title)}">
<meta property="og:description" content="{html.escape(desc)}">
<meta property="og:url" content="{canonical}">
<meta property="og:locale" content="ko_KR">
<meta property="og:image" content="{SITE}og/{og_image(path)}.png">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="{html.escape(h1)} · 밤낮">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="{up}assets/icon.svg" type="image/svg+xml">{head}
<link rel="preload" href="{up}assets/fonts/pretendard.css" as="style">
<link rel="stylesheet" href="{up}assets/fonts/pretendard.css">
<link rel="stylesheet" href="{up}calc/calc.css">
<script src="{up}calc/calc.js"></script>
<script type="application/ld+json">{json.dumps(ld, ensure_ascii=False)}</script>
</head>
<body class="k-{k}">
{site_header(up, path)}
<header class="page-hero">
  <div class="in">
    <nav class="crumbs" aria-label="위치">{crumb_html}</nav>
    <h1>{h1}</h1>
    <p class="lead">{lead}</p>
    <div class="tags">{hero_tags(path)}</div>
  </div>
</header>
<main id="main" class="tool">
{body}
<div class="cta reveal">
  <div class="acts">
    <strong>{cta_title}</strong>
    <span class="muted">{cta_text}</span><br>
    <a class="btn" data-play="{campaign}" href="{PLAY}">{PLAY_ICON}밤낮 무료로 받기</a>
  </div>
  <div class="shot"><img src="{up}assets/app/{shot}-360.webp" srcset="{up}assets/app/{shot}-360.webp 360w, {up}assets/app/{shot}-720.webp 720w" sizes="260px" width="360" height="640" alt="{shot_alt}" loading="lazy" decoding="async"></div>
</div>
{faq_html}
{note}
</main>
{site_footer(up)}
<div class="dock" id="dock" aria-hidden="true">
  <img class="logo" src="{up}assets/icon.svg" alt="" width="34" height="34">
  <div class="t"><strong>{cta_title}</strong><span>밤낮 앱 · 무료 · 로그인 없이</span></div>
  <a class="btn" data-play="{campaign}_dock" href="{PLAY}" tabindex="-1">받기</a>
</div>
<script>
{script}
</script>
</body>
</html>
'''


SITEMAP = []


def write(path, s, sitemap=True):
    full = os.path.join(ROOT, path, 'index.html')
    os.makedirs(os.path.dirname(full), exist_ok=True)
    open(full, 'w', encoding='utf-8').write(s)
    if sitemap and path not in SITEMAP:
        SITEMAP.append(path)


def patch(path, old, new, mark):
    full = os.path.join(ROOT, path)
    t = open(full, encoding='utf-8').read()
    if mark in t: return
    assert old in t, ('이어 붙일 자리를 못 찾음', path, old[:40])
    open(full, 'w', encoding='utf-8').write(t.replace(old, new, 1))


def write_sitemap():
    """쪽 목록만 적어요. 고친 날(lastmod)은 build.py가 내용이 바뀐 쪽에만 오늘로 바꿔요."""
    old = {}
    p = os.path.join(ROOT, 'sitemap.xml')
    if os.path.exists(p):
        old = dict(re.findall(r'<loc>https://bamnat\.app/(.*?)</loc><lastmod>(.*?)</lastmod>', open(p, encoding='utf-8').read()))
    urls = [''] + [u for u in SITEMAP if u] + ['privacy/', 'terms/', 'community/']
    sm = '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' + ''.join(
        f'  <url><loc>{SITE}{u}</loc><lastmod>{old.get(u, LASTMOD)}</lastmod></url>\n' for u in dict.fromkeys(urls)) + '</urlset>\n'
    open(p, 'w', encoding='utf-8').write(sm)
