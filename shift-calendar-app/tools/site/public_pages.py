import json, os, html
import sys
ROOT = sys.argv[1] if len(sys.argv) > 1 else '../-'  # YWJMoney/- 저장소를 받아 둔 곳
SITE = 'https://bamnat.app/'
LASTMOD = '2026-11-11'  # 사이트맵에 적는 고친 날. 쪽을 다시 만들면 그날로 바꿔요.


# ---------- 공통 머리·바닥 (웹 2판, 2026-11-10) ----------
# 첫 화면 index.html은 손으로 고치니, 머리·바닥을 바꾸면 index.html의 같은 조각도 함께 고쳐요.
LOGO = ('<svg viewBox="0 0 64 64" aria-hidden="true"><g stroke="#f2ece1" stroke-linecap="round">'
        + ''.join(f'<line x1="32" y1="{3 if i % 6 == 0 else 5}" x2="32" y2="{8 if i % 6 == 0 else 7.2}" stroke-width="{2.2 if i % 6 == 0 else 1.4}" opacity="{1 if i % 6 == 0 else .5}" transform="rotate({i * 15} 32 32)"/>' for i in range(24))
        + '</g><circle cx="32" cy="32" r="18" fill="none" stroke="#f2ece1" stroke-width="2.2"/>'
        '<path class="dial-day" d="M14 32a18 18 0 0 1 36 0z" fill="#f2ece1"/><circle cx="32" cy="41" r="2.4" fill="#c2a36b"/></svg>')
PLAY_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3.6v16.8c0 .8.9 1.3 1.6.9l14.1-8.4c.7-.4.7-1.4 0-1.8L6.6 2.7C5.9 2.3 5 2.8 5 3.6z" fill="currentColor"/></svg>'
NAV = [('calc/', '무료 계산기'), ('shift/', '교대 근무 달력'), ('holiday/2027/', '공휴일'), ('form/', '근무표 양식')]
MENU = [('계산기', [('calc/juhyu/', '주휴수당'), ('calc/alba/', '알바 월급'), ('calc/allowance/', '야간·연장·휴일수당'), ('calc/daily/', '일당'), ('calc/salary/', '실수령액'), ('calc/leave/', '연차'), ('calc/severance/', '퇴직금'), ('calc/', '계산기 모두')]),
        ('근무 달력', [('shift/', '교대 근무 달력 만들기'), ('shift/print/', '2027 달력 인쇄'), ('form/', '근무표 엑셀 양식'), ('holiday/2027/', '2027 공휴일'), ('holiday/2026/', '2026 공휴일')])]

def site_header(up, path=''):
    nav = ''.join(f'<a href="{up}{u}"' + (' aria-current="page"' if path.startswith(u.split("/")[0] + '/') else '') + f'>{t}</a>' for u, t in NAV)
    menu = ''.join(f'<li class="grp">{g}</li>' + ''.join(f'<li><a href="{up}{u}">{t}</a></li>' for u, t in items) for g, items in MENU)
    return f'''<a class="skip" href="#main">본문으로</a>
<header class="site-header">
  <div class="in">
    <a class="brand" href="{up}" aria-label="밤낮 처음으로">{LOGO}<span>밤낮</span></a>
    <nav class="site-nav" aria-label="사이트">{nav}</nav>
    <span class="grow"></span>
    <a class="get" data-play="header" href="https://play.google.com/store/apps/details?id=com.bamnat.app">{PLAY_ICON}앱 받기</a>
    <button class="menu-btn" type="button" aria-label="메뉴" aria-expanded="false"><svg class="bars" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 7h16M4 12h16M4 17h16"/></svg><svg class="x" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg></button>
  </div>
  <div class="menu"><div><ul>{menu}</ul></div></div>
</header>'''

def site_footer(up):
    col = lambda g, items: f'<div><h4>{g}</h4><ul>' + ''.join(f'<li><a href="{up}{u}">{t}</a></li>' for u, t in items) + '</ul></div>'
    return f'''<footer class="site-footer">
  <div class="in">
    <div class="top">
      <div class="about"><a class="brand" href="{up}">{LOGO}<span>밤낮</span></a><p>교대·일근·알바·현장·사장님까지, 근무가 있는 모든 사람의 근무 앱이에요.</p></div>
      {col('계산기', MENU[0][1][:7])}
      {col('근무 달력', MENU[1][1])}
      {col('안내', [('privacy/', '개인정보 처리방침'), ('terms/', '이용약관'), ('community/', '광장 운영 규칙')])}
    </div>
    <div class="biz">
      문의 <a href="mailto:yoj0790@naver.com">yoj0790@naver.com</a> (앱 안에서는 설정 › 도움말과 문의)<br>
      상호 밤낮 · 대표자 양원준 · 사업자등록번호 327-25-02565 (<a href="https://www.ftc.go.kr/bizCommPop.do?wrkr_no=3272502565" target="_blank" rel="noopener">사업자 정보 확인</a>)<br>
      통신판매업 신고번호 제2026-전남여수-0424호 · 전남광주통합특별시 여수시 안산1길 179, 2동 105호<br>
      전화 010-6408-8825 · 전자우편 yoj0790@naver.com · 호스팅 Cloudflare, Inc., GitHub, Inc.
      <div class="safe">계산기에 입력한 값은 이 화면에서만 계산하고 어디에도 보내지 않아요.</div>
    </div>
  </div>
</footer>'''

# 앱으로 이어 가기 카드에 보일 스토어 화면 (assets/app/<이름>-360|720.webp)
SHOTS = {'pay': '05-pay', 'field': '09-field', 'leave': '12-leave', 'holiday': '11-holiday', 'calendar': '01-calendar',
         'roster': '02-roster', 'team': '06-team', 'boss': '07-boss'}
def cta_shot(c):
    if c.startswith('holiday'): return SHOTS['holiday']
    if c in ('daily', 'form_site_days'): return SHOTS['field']
    if c == 'leave': return SHOTS['leave']
    if c == 'form_duty': return SHOTS['roster']
    if c == 'form_shift_team': return SHOTS['team']
    if c == 'form_alba_week': return SHOTS['boss']
    if c in ('juhyu', 'allowance', 'alba', 'salary', 'severance'): return SHOTS['pay']
    return SHOTS['calendar']

def hero_tags(path):
    t = (['석 달 달력', '같은 조에 링크로', '로그인 없이'] if path.startswith('shift/')
         else ['인쇄·엑셀 무료', '로그인 없이'] if path.startswith('form/')
         else ['대체공휴일 포함', '교대 근무자 쉬는 날'] if path.startswith('holiday/')
         else ['무료', '로그인 없이', '입력값은 보내지 않아요'])
    return ''.join(f'<span>{x}</span>' for x in t)


def page(path, title, desc, h1, lead, body, script, faqs, campaign, cta_title, cta_text, depth):
    up = '../' * depth
    canonical = SITE + path
    assert len(desc) <= 80, ('설명은 80자 안으로(네이버 사이트 진단)', path, len(desc), desc)
    ld = [{
        "@context": "https://schema.org", "@type": "WebApplication", "name": h1, "url": canonical,
        "applicationCategory": "FinanceApplication", "operatingSystem": "Any", "inLanguage": "ko",
        "offers": {"@type": "Offer", "price": "0", "priceCurrency": "KRW"},
        "publisher": {"@type": "Organization", "name": "밤낮"}
    }, {
        "@context": "https://schema.org", "@type": "FAQPage",
        "mainEntity": [{"@type": "Question", "name": q, "acceptedAnswer": {"@type": "Answer", "text": a}} for q, a in faqs]
    }]
    if path.startswith('shift/') and path != 'shift/':
        crumbs = [(f'{up}shift/', '교대 근무 달력')] + ([(f'{up}shift/print/', '인쇄')] if path.startswith('shift/print/') and path != 'shift/print/' else [])
    elif path.startswith('form/') and path != 'form/':
        crumbs = [(f'{up}form/', '근무표 양식')]
    elif path.startswith('holiday/'):
        crumbs = [(f'{up}calc/', '무료 계산기')]
    elif path in ('shift/', 'form/', 'calc/'):
        crumbs = []
    else:
        crumbs = [(f'{up}calc/', '무료 계산기')]
    # 위치 띠를 검색 결과에도 보이게 (BreadcrumbList)
    trail = [(SITE, '밤낮')] + [(SITE + u[len(up):], t) for u, t in crumbs] + [(canonical, h1)]
    ld.append({"@context": "https://schema.org", "@type": "BreadcrumbList",
               "itemListElement": [{"@type": "ListItem", "position": i + 1, "name": t, "item": u} for i, (u, t) in enumerate(trail)]})
    ogimg = 'shift' if path.startswith('shift') else 'holiday' if path.startswith('holiday') else 'calc'
    faq_html = '\n'.join(f'<details><summary>{html.escape(q)}</summary><p>{html.escape(a)}</p></details>' for q, a in faqs)
    shot = cta_shot(campaign)
    tags = hero_tags(path)
    crumb_html = f'<a href="{up}">밤낮</a>' + ''.join(f'<span class="sep">/</span><a href="{u}">{t}</a>' for u, t in crumbs)
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
<meta property="og:image" content="{SITE}og/{ogimg}.png">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="{html.escape(h1)} · 밤낮">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="{up}assets/icon.svg" type="image/svg+xml">
<link rel="preload" href="{up}assets/fonts/pretendard.css" as="style">
<link rel="stylesheet" href="{up}assets/fonts/pretendard.css">
<link rel="stylesheet" href="{up}calc/calc.css">
<script src="{up}calc/calc.js"></script>
<script type="application/ld+json">{json.dumps(ld, ensure_ascii=False)}</script>
</head>
<body>
{site_header(up, path)}
<header class="page-hero">
  <div class="in">
    <nav class="crumbs" aria-label="위치">{crumb_html}</nav>
    <h1>{h1}</h1>
    <p class="lead">{lead}</p>
    <div class="tags">{tags}</div>
  </div>
</header>
<main id="main" class="tool">
{body}
<div class="cta reveal">
  <div class="acts">
    <strong>{cta_title}</strong>
    <span class="muted">{cta_text}</span><br>
    <a class="btn" data-play="{campaign}" href="https://play.google.com/store/apps/details?id=com.bamnat.app">{PLAY_ICON}밤낮 무료로 받기</a>
  </div>
  <div class="shot"><img src="{up}assets/app/{shot}-360.webp" srcset="{up}assets/app/{shot}-360.webp 360w, {up}assets/app/{shot}-720.webp 720w" sizes="260px" width="360" height="640" alt="밤낮 앱 화면" loading="lazy" decoding="async"></div>
</div>
<h2>자주 묻는 것</h2>
{faq_html}
<p class="note" style="margin-top:18px">참고용 계산이에요. 실제 지급액은 근로계약·회사 규정·세법 적용에 따라 달라질 수 있어요. 요율은 밤낮 앱과 같은 공개 자료(최저임금 고시, 4대보험 요율)를 써요.</p>
</main>
{site_footer(up)}
<div class="dock" id="dock" aria-hidden="true">
  <img class="logo" src="{up}assets/icon.svg" alt="" width="34" height="34">
  <div class="t"><strong>{cta_title}</strong><span>밤낮 앱 · 무료 · 로그인 없이</span></div>
  <a class="btn" data-play="{campaign}_dock" href="https://play.google.com/store/apps/details?id=com.bamnat.app" tabindex="-1">받기</a>
</div>
<script>
{script}
</script>
</body>
</html>
'''

def write(path, s):
    full = os.path.join(ROOT, path, 'index.html')
    os.makedirs(os.path.dirname(full), exist_ok=True)
    open(full, 'w').write(s)

# ---------- 주휴수당 ----------
write('calc/juhyu/', page(
    'calc/juhyu/', '주휴수당 계산기 (2026·2027 최저시급 반영) | 밤낮',
    '시급과 1주 근무 시간만 넣으면 주휴수당과 월 환산액을 바로 계산해요. 주 15시간 조건, 2026·2027 최저시급 반영.',
    '주휴수당 계산기', '시급과 1주에 일하기로 한 시간만 넣으면 돼요. 알바·파트타임·교대 근무 모두 같은 방법으로 계산해요.',
    '''<div class="card">
  <label for="wage">시급 (원)</label>
  <input id="wage" inputmode="numeric" autocomplete="off">
  <div class="chips" id="years"></div>
  <div class="row">
    <div><label for="days">1주 근무일</label><input id="days" inputmode="decimal" value="5"></div>
    <div><label for="hours">하루 근무 시간</label><input id="hours" inputmode="decimal" value="4"></div>
  </div>
  <span class="hint">휴게 시간은 빼고 적어요. 주마다 다르면 평균으로 넣어요.</span>
</div>
<div class="card result" aria-live="polite">
  <div class="muted">1주 주휴수당</div>
  <div class="big" id="out">-</div>
  <table class="lines" id="lines"></table>
</div>''',
    '''(function(){
var B=Bamnat,$=function(i){return document.getElementById(i)};
function calc(){
  var w=B.num($('wage').value),d=B.num($('days').value),h=B.num($('hours').value),week=d*h,lines=[];
  if(!w||!week){$('out').textContent='-';$('lines').innerHTML='';return;}
  if(week<15){$('out').textContent='0원';
    $('lines').innerHTML='<tr><td>1주 근무 시간</td><td>'+week+'시간</td></tr><tr><td colspan="2">1주 15시간보다 적으면 주휴수당이 생기지 않아요.</td></tr>';return;}
  var hrs=Math.min(week,40)/40*8,pay=hrs*w,month=pay*365/7/12,tot=Math.round((week+hrs)*365/7/12);
  $('out').textContent=B.won(pay);
  $('lines').innerHTML='<tr><td>1주 근무 시간</td><td>'+week+'시간'+(week>40?' (40시간까지만 셈)':'')+'</td></tr>'+
    '<tr><td>주휴 시간 (근무시간÷40×8)</td><td>'+(Math.round(hrs*100)/100)+'시간</td></tr>'+
    '<tr><td>한 달 환산 (4.345주)</td><td>'+B.won(month)+'</td></tr>'+
    '<tr class="sum"><td>근무+주휴 한 달 합계 (월 '+tot+'시간)</td><td>'+B.won(tot*w)+'</td></tr>'+
    (week>=40?'<tr><td colspan="2" class="note">주 40시간이면 고시 월 환산액(209시간)과 같아요.</td></tr>':'');
}
B.ready.then(function(){
  var y=B.years()[0]||new Date().getFullYear();$('wage').value=B.rateFor(y).minimumHourlyWage;
  B.yearChips($('years'),function(y){$('wage').value=B.rateFor(y).minimumHourlyWage;calc();});calc();});
['wage','days','hours'].forEach(function(i){$(i).addEventListener('input',calc);});
})();''',
    [('주휴수당은 누가 받나요?', '1주에 일하기로 한 시간(소정근로시간)이 15시간 이상이고, 그 주에 정해진 날을 모두 나온 근로자가 받아요. 알바·계약직도 같아요(근로기준법 제55조·제18조 제3항).'),
     ('주휴수당은 어떻게 계산하나요?', '1주 소정근로시간 ÷ 40 × 8 × 시급이에요. 40시간을 넘게 일해도 40시간으로 계산해서 하루치(8시간)가 가장 많은 값이에요.'),
     ('2027년 최저시급은 얼마인가요?', '2027년 최저시급은 10,700원으로 고시됐어요. 주 40시간이면 주휴 포함 월 환산액은 약 2,236,300원이에요(209시간 기준).'),
     ('근무 시간이 주마다 달라요.', '보통 최근 4주 평균으로 1주 시간을 잡아요. 밤낮 앱에 근무를 넣어 두면 주마다 15시간을 넘었는지 저절로 셈해서 그달 주휴까지 월급에 넣어요.')],
    'juhyu', '근무표만 넣으면 주휴·야간수당까지 매달 저절로', '밤낮은 교대·알바·일근 근무를 달력에 넣으면 주마다 주휴 조건을 따져 이번 달 예상 월급을 보여 줘요. 근무 알람도 함께예요.', 2))

# ---------- 야간·연장·휴일 ----------
write('calc/allowance/', page(
    'calc/allowance/', '야간수당·연장수당·휴일수당 계산기 (밤 10시~아침 6시 50%) | 밤낮',
    '출퇴근 시각과 시급으로 야간(22~06시)·연장·휴일 가산수당을 나눠 계산해요. 자정 넘는 야간, 5인 미만도 반영.',
    '야간·연장·휴일수당 계산기', '출근과 퇴근 시각만 넣으면 밤 10시~아침 6시 야간 시간과 8시간을 넘은 연장 시간을 저절로 나눠요. 자정을 넘는 야간 근무도 그대로 넣으세요.',
    '''<div class="card">
  <label for="wage">시급 (원)</label>
  <input id="wage" inputmode="numeric" autocomplete="off">
  <div class="chips" id="years"></div>
  <div class="row">
    <div><label for="start">출근</label><input id="start" type="time" value="22:00"></div>
    <div><label for="end">퇴근</label><input id="end" type="time" value="07:00"></div>
  </div>
  <label for="rest">휴게 시간 (분)<span class="hint">휴게는 야간이 아닌 시간에서 먼저 빼요. 밤에 쉬었다면 아래를 켜요.</span></label>
  <input id="rest" inputmode="numeric" value="60">
  <label class="check"><input type="checkbox" id="restNight"> 휴게를 밤 10시~아침 6시 사이에 했어요</label>
  <label class="check"><input type="checkbox" id="holiday"> 쉬는 날(주휴일·공휴일)에 일했어요</label>
  <label class="check"><input type="checkbox" id="small"> 상시 5명 미만 사업장이에요 (가산수당 없음)</label>
</div>
<div class="card result" aria-live="polite">
  <div class="muted">이날 받을 금액 (가산 포함)</div>
  <div class="big" id="out">-</div>
  <table class="lines" id="lines"></table>
</div>''',
    '''(function(){
var B=Bamnat,$=function(i){return document.getElementById(i)};
function mins(t){var p=(t||'0:0').split(':');return (+p[0])*60+(+p[1]);}
function calc(){
  var w=B.num($('wage').value);if(!w){$('out').textContent='-';$('lines').innerHTML='';return;}
  var s=mins($('start').value),e=mins($('end').value);if(e<=s)e+=1440;
  var total=0,night=0;for(var m=s;m<e;m++){total++;var t=m%1440;if(t>=1320||t<360)night++;}
  var rest=Math.min(B.num($('rest').value),total);
  if($('restNight').checked){var r=Math.min(rest,night);night-=r;}else{var dayPart=total-night,r2=Math.max(0,rest-dayPart);night-=Math.min(r2,night);}
  var work=total-rest,h=function(x){return x/60;};
  var hol=$('holiday').checked,small=$('small').checked;
  var over=Math.max(0,work-480);
  var base=h(work)*w,ext=0,nightP=0,holP=0;
  if(!small){nightP=h(night)*w*0.5;
    if(hol){holP=h(Math.min(work,480))*w*0.5+h(over)*w*1.0;}else{ext=h(over)*w*0.5;}}
  var sum=base+ext+nightP+holP;$('out').textContent=B.won(sum);
  var fmt=function(x){return Math.floor(x/60)+'시간'+(x%60?' '+(x%60)+'분':'');};
  var rows='<tr><td>일한 시간 (휴게 '+rest+'분 뺌)</td><td>'+fmt(work)+'</td></tr>'+
    '<tr><td>그중 야간 (22~06시)</td><td>'+fmt(night)+'</td></tr>'+
    '<tr><td>그중 8시간 넘은 시간</td><td>'+fmt(over)+'</td></tr>'+
    '<tr><td>기본 (시간×시급)</td><td>'+B.won(base)+'</td></tr>';
  if(small)rows+='<tr><td colspan="2">5명 미만 사업장은 야간·연장·휴일 가산을 주지 않아도 돼요(근로기준법 제11조).</td></tr>';
  else{rows+='<tr><td>야간 가산 50%</td><td>'+B.won(nightP)+'</td></tr>';
    rows+=hol?'<tr><td>휴일 가산 (8시간까지 50%, 넘으면 100%)</td><td>'+B.won(holP)+'</td></tr>':'<tr><td>연장 가산 50%</td><td>'+B.won(ext)+'</td></tr>';}
  rows+='<tr class="sum"><td>합계</td><td>'+B.won(sum)+'</td></tr>';$('lines').innerHTML=rows;
}
B.ready.then(function(){var y=B.years()[0]||new Date().getFullYear();$('wage').value=B.rateFor(y).minimumHourlyWage;
  B.yearChips($('years'),function(y){$('wage').value=B.rateFor(y).minimumHourlyWage;calc();});calc();});
['wage','start','end','rest','restNight','holiday','small'].forEach(function(i){$(i).addEventListener('input',calc);$(i).addEventListener('change',calc);});
})();''',
    [('야간수당은 몇 시부터인가요?', '밤 10시부터 다음 날 아침 6시 사이에 일한 시간에 통상임금의 50%를 더 받아요(근로기준법 제56조 제3항). 야간이 연장이나 휴일과 겹치면 각각 더해요.'),
     ('연장수당은 언제 붙나요?', '하루 8시간이나 1주 40시간을 넘게 일한 시간에 50%를 더 받아요. 이 계산기는 하루 8시간 기준만 셈해요. 1주 40시간 초과까지 보려면 한 주 근무를 모두 넣어야 해서 밤낮 앱에서 저절로 계산해요.'),
     ('휴일에 일하면 얼마나 더 받나요?', '휴일에 일한 시간 중 8시간까지는 50%, 8시간을 넘은 시간은 100%를 더 받아요(근로기준법 제56조 제2항).'),
     ('5명 미만 회사도 받을 수 있나요?', '상시 근로자가 5명 미만이면 야간·연장·휴일 가산수당 규정이 적용되지 않아요. 다만 일한 시간만큼의 기본 시급과 주휴수당은 받아요.')],
    'allowance', '야간 근무가 많은 달, 이번 달 월급은?', '밤낮에 근무표를 넣으면 근무마다 야간·연장·휴일 시간을 나눠 수당까지 담은 이번 달 예상 월급을 보여 줘요. 명세서와 다르면 근무 종류마다 시수를 맞출 수 있어요.', 2))

# ---------- 알바 월급 ----------
write('calc/alba/', page(
    'calc/alba/', '알바 월급 계산기 · 시급으로 월급 (주휴수당·3.3%·4대보험) | 밤낮',
    '시급과 근무 시간으로 주휴수당 포함 알바 월급, 3.3%·4대보험 뺀 실수령액을 계산해요. 2026·2027 최저시급 반영.',
    '알바 월급 계산기', '시급과 근무 시간으로 한 달 월급을 계산해요. 주휴수당을 넣고, 3.3% 떼는지 4대보험인지 골라 손에 쥐는 돈까지 봐요.',
    '''<div class="card">
  <label for="wage">시급 (원)</label>
  <input id="wage" inputmode="numeric" autocomplete="off">
  <div class="chips" id="years"></div>
  <div class="row">
    <div><label for="days">1주 근무일</label><input id="days" inputmode="decimal" value="5"></div>
    <div><label for="hours">하루 근무 시간</label><input id="hours" inputmode="decimal" value="8"></div>
  </div>
  <label>떼는 것</label>
  <div class="chips" id="tax">
    <button type="button" class="chip" data-v="none" aria-pressed="false">안 뗌</button>
    <button type="button" class="chip" data-v="biz" aria-pressed="true">3.3%</button>
    <button type="button" class="chip" data-v="ins" aria-pressed="false">4대보험</button>
  </div>
</div>
<div class="card result" aria-live="polite">
  <div class="muted">한 달 실수령 (예상)</div>
  <div class="big" id="out">-</div>
  <table class="lines" id="lines"></table>
</div>''',
    '''(function(){
var B=Bamnat,$=function(i){return document.getElementById(i)},mode='biz',year=new Date().getFullYear();
function calc(){
  var w=B.num($('wage').value),d=B.num($('days').value),h=B.num($('hours').value),week=d*h;
  if(!w||!week){$('out').textContent='-';$('lines').innerHTML='';return;}
  var weeks=365/7/12,juh=week>=15?Math.min(week,40)/40*8:0,hrs=Math.round((week+juh)*weeks),gross=hrs*w,rows='',ded=0;
  rows+='<tr><td>1주 근무 '+week+'시간 + 주휴 '+(Math.round(juh*100)/100)+'시간'+(week<15?' (주 15시간 미만이라 주휴 없음)':'')+'</td><td></td></tr>';
  rows+='<tr><td>한 달 유급 시간 (×4.345주)</td><td>'+hrs+'시간</td></tr>';
  rows+='<tr><td>세전 월급 (시간×시급)</td><td>'+B.won(gross)+'</td></tr>';
  if(mode==='biz'){var it=B.floor10(gross*0.03),lt=B.floor10(it*0.1);ded=it+lt;
    rows+='<tr><td>사업소득세 3% + 지방소득세 0.3%</td><td>-'+B.won(ded)+'</td></tr>';}
  if(mode==='ins'){var x=B.insurance(gross,year),r=x.rates,tax=B.incomeTax(gross,1,0),lt=tax?Math.floor(tax/100)*10:0;
    ded=x.pension+x.health+x.care+x.employment+(tax||0)+lt;
    rows+='<tr><td>국민연금 '+(r.pensionRate*50).toFixed(2)+'%</td><td>-'+B.won(x.pension)+'</td></tr>'+
      '<tr><td>건강보험 '+(r.healthRate*50).toFixed(3)+'%</td><td>-'+B.won(x.health)+'</td></tr>'+
      '<tr><td>장기요양</td><td>-'+B.won(x.care)+'</td></tr>'+
      '<tr><td>고용보험 '+(r.unemploymentRate*50).toFixed(1)+'%</td><td>-'+B.won(x.employment)+'</td></tr>'+
      (tax===null?'<tr><td colspan="2" class="note">근로소득세 표를 불러오지 못해 소득세는 빼지 않았어요.</td></tr>':
      '<tr><td>근로소득세 (간이세액표, 본인 1명)</td><td>-'+B.won(tax)+'</td></tr><tr><td>지방소득세</td><td>-'+B.won(lt)+'</td></tr>')+
      '<tr><td colspan="2" class="note">가족 수·비과세 식대까지 넣으려면 <a href="../salary/">월급 실수령액 계산기</a>를 써요.</td></tr>';}
  rows+='<tr class="sum"><td>실수령</td><td>'+B.won(gross-ded)+'</td></tr>';
  $('out').textContent=B.won(gross-ded);$('lines').innerHTML=rows;
}
$('tax').querySelectorAll('.chip').forEach(function(b){b.onclick=function(){$('tax').querySelectorAll('.chip').forEach(function(c){c.setAttribute('aria-pressed','false')});b.setAttribute('aria-pressed','true');mode=b.dataset.v;calc();};});
Promise.all([B.ready,B.loadTaxTable().catch(function(){})]).then(function(){year=B.years()[0]||year;$('wage').value=B.rateFor(year).minimumHourlyWage;
  B.yearChips($('years'),function(y){year=y;$('wage').value=B.rateFor(y).minimumHourlyWage;calc();});calc();});
['wage','days','hours'].forEach(function(i){$(i).addEventListener('input',calc);});
})();''',
    [('3.3%는 왜 떼나요?', '근로계약 대신 프리랜서(사업소득)로 처리하면 소득세 3%와 지방소득세 0.3%를 떼고 줘요. 실제로 사장님 지시를 받아 정해진 시간에 일하면 근로자라서 4대보험·주휴수당 대상일 수 있어요.'),
     ('한 달은 몇 주로 계산하나요?', '1년 365일 ÷ 7 ÷ 12 = 약 4.345주로 계산해요. 주 40시간이면 주휴 포함 월 209시간이 돼요.'),
     ('4대보험은 얼마나 떼나요?', '근로자 몫은 국민연금 4.75%(2026년), 건강보험 3.595%, 장기요양(건강보험료의 약 13%), 고용보험 0.9%예요. 2027년 국민연금은 5%예요.'),
     ('주마다 근무 시간이 달라요.', '밤낮 앱에 실제 근무를 넣으면 주마다 주휴 조건을 따로 따져서 그달 월급을 계산해요. 근무 알람도 같이 맞출 수 있어요.')],
    'alba', '시간표가 매주 바뀌는 알바라면', '밤낮에 근무를 넣으면 주마다 주휴수당 조건을 따로 따져 이번 달 월급을 보여 줘요. 근무 30분 전 알람도 근무마다 달리 맞춰요.', 2))

# ---------- 일당 ----------
write('calc/daily/', page(
    'calc/daily/', '일당 계산기 · 일용직 세금(15만 원 공제, 2.7%)과 실수령 | 밤낮',
    '일당과 일한 날 수로 일용직 소득세(하루 15만 원 공제)·지방소득세·고용보험을 뺀 실수령액을 계산해요. 현장·건설용.',
    '일당 계산기 (일용직 세금)', '현장·인테리어·건설처럼 날마다 받는 일당의 세금과 손에 쥐는 돈을 계산해요.',
    '''<div class="card">
  <div class="row">
    <div><label for="pay">일당 (원)</label><input id="pay" inputmode="numeric" value="200000"></div>
    <div><label for="days">일한 날 수</label><input id="days" inputmode="numeric" value="20"></div>
  </div>
  <label class="check"><input type="checkbox" id="emp" checked> 고용보험 떼요 (일용직 근로자 0.9%)</label>
</div>
<div class="card result" aria-live="polite">
  <div class="muted">실수령 합계</div>
  <div class="big" id="out">-</div>
  <table class="lines" id="lines"></table>
</div>''',
    '''(function(){
var B=Bamnat,$=function(i){return document.getElementById(i)};
function calc(){
  var p=B.num($('pay').value),n=B.num($('days').value);if(!p||!n){$('out').textContent='-';$('lines').innerHTML='';return;}
  var r=B.rateFor(new Date().getFullYear());
  var tax=Math.max(0,p-150000)*0.06*0.45;tax=Math.floor(tax/10)*10;if(tax<1000)tax=0;
  var local=Math.floor(tax*0.1/10)*10,emp=$('emp').checked?B.floor10(p*r.unemploymentRate/2):0,day=p-tax-local-emp;
  $('out').textContent=B.won(day*n);
  $('lines').innerHTML='<tr><td>하루 소득세 ((일당-15만)×6%×45%)</td><td>-'+B.won(tax)+'</td></tr>'+
    '<tr><td>하루 지방소득세 (10%)</td><td>-'+B.won(local)+'</td></tr>'+
    '<tr><td>하루 고용보험</td><td>-'+B.won(emp)+'</td></tr>'+
    '<tr><td>하루 실수령</td><td>'+B.won(day)+'</td></tr>'+
    '<tr><td>세전 합계 ('+n+'일)</td><td>'+B.won(p*n)+'</td></tr>'+
    '<tr class="sum"><td>실수령 합계</td><td>'+B.won(day*n)+'</td></tr>'+
    (tax===0?'<tr><td colspan="2" class="note">하루 소득세가 1,000원 미만이면 떼지 않아요(소액부징수).</td></tr>':'');
}
B.ready.then(calc);['pay','days','emp'].forEach(function(i){$(i).addEventListener('input',calc);$(i).addEventListener('change',calc);});
})();''',
    [('일용직 세금은 어떻게 계산하나요?', '하루 일당에서 15만 원을 뺀 금액에 6%를 곱하고, 그중 55%를 깎아 줘요(근로소득세액공제). 그래서 15만 원 넘는 부분의 2.7%가 소득세, 그 10%가 지방소득세예요.'),
     ('일당 15만 원 이하면 세금이 없나요?', '네. 하루 15만 원까지는 소득세가 없고, 계산한 소득세가 1,000원 미만이어도 떼지 않아요. 고용보험은 따로 떼요.'),
     ('국민연금·건강보험도 떼나요?', '같은 현장에서 한 달 8일 이상 또는 60시간 이상 일하면 국민연금·건강보험 대상이 될 수 있어요. 이 계산기는 고용보험만 셈해요.'),
     ('현장이 날마다 바뀌어요.', '밤낮 앱에 일한 날과 현장, 일당을 적으면 이번 달 번 돈과 받을 돈을 달력에서 한눈에 봐요. 사장님이라면 기사님 일정도 함께 관리할 수 있어요.')],
    'daily', '현장이 바뀌어도 이번 달 일당을 한눈에', '밤낮 달력에 일한 날과 일당을 적으면 이번 달 번 돈·받을 돈을 바로 봐요. 직원·기사님을 둔 사장님은 일정과 급여 대장도 함께 써요.', 2))
print('pages ok')

# ---------- 교대 근무 달력 ----------
write('shift/', page(
    'shift/', '교대 근무 달력 만들기 · 4조2교대·3조2교대·주야비휴 근무표 | 밤낮',
    '4조2교대·3조2교대·4조3교대·주야비휴 순서와 첫날만 고르면 석 달 근무 달력이 바로 나와요. 앱에선 알람까지.',
    '교대 근무 달력 만들기', '근무 순서와 첫날만 고르면 석 달 근무 달력이 바로 나와요. 마음에 들면 같은 순서로 앱에 옮겨 알람까지 맞춰요.',
    '''<div class="card">
  <label for="pat">근무 순서</label>
  <select id="pat"></select>
  <label for="custom">직접 적기<span class="hint">주=D 오후=E 야=N 휴=O 당번=X 비번=B. 예: DDNNOO</span></label>
  <input id="custom" autocomplete="off" autocapitalize="characters">
  <label for="first">이 순서의 첫날<span class="hint">순서 맨 앞 근무를 하는 날이에요. 예: 주주야야휴휴면 첫 주간 날.</span></label>
  <input id="first" type="date">
</div>
<div class="card" aria-live="polite">
  <div id="today" style="font-weight:700"></div>
  <div id="cal"></div>
  <button type="button" class="chip" id="share" style="margin-top:12px">같은 조 동료에게 이 달력 보내기</button>
  <span class="note" id="shared"></span>
</div>
<div class="card app-next">
  <strong class="t">이 달력, 앱에서는 이렇게 이어져요</strong>
  <ul class="ticks">
    <li><b>몇 년치가 한 번에</b><span>같은 순서와 첫날을 고르면 앞으로의 근무가 다 채워져요. 대근·연차로 바뀐 날만 눌러 고쳐요.</span></li>
    <li><b>근무마다 다른 알람</b><span>주간·야간 시각이 따로 울리고, 쉬는 날엔 조용해요.</span></li>
    <li><b>우리 회사 방식은 회사 이름으로</b><span>순서를 몰라도 앱의 회사 찾기에서 고르면 근무 순서와 시간이 맞춰져요.</span></li>
    <li><b>이번 달 예상 월급</b><span>야간·휴일 수당까지 근무를 넣는 대로 따라와요.</span></li>
  </ul>
  <a class="btn" data-play="shift_cal" href="https://play.google.com/store/apps/details?id=com.bamnat.app">''' + PLAY_ICON + '''이 순서로 앱에서 이어 쓰기</a>
  <span class="note">로그인·회원가입 없이 무료예요.</span>
</div>
<h2 id="order-list">교대 순서별 설명</h2>
<div class="card" style="margin-top:18px">
  <strong>간호사처럼 매달 근무표가 바뀌나요?</strong><br>
  <span class="muted">순서가 정해지지 않은 듀티표는 밤낮 앱에서 근무표 사진을 찍거나 열면 내 줄을 찾아 달력에 넣어 줘요.</span>
</div>''',
    '''(function(){
var B=Bamnat,$=function(i){return document.getElementById(i)},pats=[];
var NAME={D:'주',E:'오',N:'야',O:'휴',X:'당',B:'비'};
function iso(d){return d.getFullYear()+'-'+('0'+(d.getMonth()+1)).slice(-2)+'-'+('0'+d.getDate()).slice(-2);}
function codes(){var c=$('custom').value.toUpperCase().replace(/[^DENOXB]/g,'');return c||$('pat').value;}
function draw(){
  var c=codes(),f=$('first').value;if(!c||!f){$('cal').innerHTML='';$('today').textContent='';return;}
  var p=f.split('-'),start=new Date(+p[0],+p[1]-1,+p[2]),now=new Date();now=new Date(now.getFullYear(),now.getMonth(),now.getDate());
  var at=function(d){var k=Math.round((d-start)/864e5)%c.length;if(k<0)k+=c.length;return c[k];};
  var t=at(now);$('today').textContent='오늘('+(now.getMonth()+1)+'/'+now.getDate()+')은 '+(NAME[t]||t)+' · 순서 '+c.length+'일';
  var out='';for(var m=0;m<3;m++){var first=new Date(now.getFullYear(),now.getMonth()+m,1),last=new Date(first.getFullYear(),first.getMonth()+1,0);
    out+='<div class="month-title">'+first.getFullYear()+'년 '+(first.getMonth()+1)+'월</div><div class="cal">';
    '일월화수목금토'.split('').forEach(function(w){out+='<div class="h">'+w+'</div>';});
    for(var i=0;i<first.getDay();i++)out+='<div></div>';
    for(var d=1;d<=last.getDate();d++){var day=new Date(first.getFullYear(),first.getMonth(),d),s=at(day);
      out+='<div class="d'+(+day===+now?' today':'')+'"><span class="n">'+d+'</span><span class="s '+s+'">'+(NAME[s]||s)+'</span></div>';}
    out+='</div>';}
  $('cal').innerHTML=out;
  try{history.replaceState(null,'','?p='+c+'&s='+f);}catch(e){}
  var a=document.querySelector('a[data-play="shift_cal"]');if(a)a.href=B.playLink('shift_cal','shift')+encodeURIComponent('&utm_content='+c.slice(0,24));
}
function esc(s){return String(s||'').replace(/[&<>"]/g,function(ch){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[ch];});}
var qs=new URLSearchParams(location.search);
$('first').value=qs.get('s')||iso(new Date());if(qs.get('p'))$('custom').value=qs.get('p');
fetch(B.base+'remote/v1/presets.json').then(function(r){return r.json();}).catch(function(){return null;}).then(function(j){
  pats=(j&&j.data&&j.data.patterns)||[{name:'4조 2교대',description:'주야비휴',codes:'DNOO'},{name:'3조 2교대',description:'주주야야비휴',codes:'DDNNOO'}];
  $('pat').innerHTML=pats.map(function(p){return '<option value="'+esc(p.codes)+'">'+esc(p.name)+' · '+esc(p.description)+'</option>';}).join('');
  draw();
});
$('share').onclick=function(){var u=location.href,t='우리 조 근무 달력 ('+codes()+')';
  if(navigator.share){navigator.share({title:t,url:u}).catch(function(){});}
  else if(navigator.clipboard){navigator.clipboard.writeText(u).then(function(){$('shared').textContent=' 링크를 복사했어요. 카톡에 붙여 넣어 보내요.';});}};
$('pat').addEventListener('change',function(){$('custom').value='';draw();});
['custom','first'].forEach(function(i){$(i).addEventListener('input',draw);});
})();''',
    [('4조 2교대는 어떤 순서인가요?', '가장 흔한 것은 주간·야간·비번·휴무(주야비휴) 4일 순서예요. 회사에 따라 주주야야휴휴휴휴(8일)처럼 길게 도는 곳도 있어요. 위에서 순서를 골라 첫날만 넣으면 달력이 나와요.'),
     ('3조 2교대와 4조 3교대는 어떻게 다른가요?', '3조 2교대는 세 조가 주간·야간 두 근무를 돌아가며(예: 주주야야휴휴), 4조 3교대는 네 조가 주간·오후·야간 세 근무를 돌아가요(예: 주주오오야야휴휴).'),
     ('우리 회사 교대 순서를 잘 모르겠어요.', '밤낮 앱의 회사 찾기에서 회사 이름으로 고르면 근무 순서와 시간이 맞춰져요. 없는 회사는 앱에서 알려 주시면 확인해서 더하고, 그동안은 순서를 직접 적어 써요.'),
     ('달력을 휴대폰에 넣고 알람도 맞출 수 있나요?', '밤낮 앱에서 같은 순서를 고르면 몇 년치가 한 번에 채워지고, 주간·야간마다 다른 시각에 울리는 근무 알람도 맞춰요. 쉬는 날엔 조용해요.')],
    'shift', '이 달력을 휴대폰에, 근무 알람까지', '밤낮 앱에서 같은 순서를 고르면 몇 년치 근무가 한 번에 채워지고, 주간·야간마다 다른 근무 알람과 이번 달 예상 월급까지 봐요.', 1))
print('shift ok')

# ---------- 모음 ----------
hub_body = '''<ul class="links">
  <li><a href="juhyu/"><strong>주휴수당 계산기</strong></a><br><span class="muted">시급과 1주 근무 시간으로 주휴수당과 월 환산</span></li>
  <li><a href="alba/"><strong>알바 월급 계산기</strong></a><br><span class="muted">주휴 포함 월급, 3.3%·4대보험 뺀 실수령</span></li>
  <li><a href="allowance/"><strong>야간·연장·휴일수당 계산기</strong></a><br><span class="muted">출퇴근 시각으로 밤 10시~아침 6시, 8시간 초과 시간을 나눠 계산</span></li>
  <li><a href="daily/"><strong>일당 계산기 (일용직 세금)</strong></a><br><span class="muted">현장·인테리어·건설 일당의 세금과 실수령</span></li>
  <li><a href="../shift/"><strong>교대 근무 달력 만들기</strong></a><br><span class="muted">4조2교대·3조2교대·주야비휴 순서로 석 달 달력, 교대 순서별 설명</span></li>
</ul>'''
write('calc/', page('calc/', '근무·급여 계산기 모음 (주휴수당·야간수당·알바 월급·일당) | 밤낮',
    '주휴수당·알바 월급·야간수당·실수령액·연차·퇴직금·일당 세금을 무료로 계산해요. 2026·2027 최저시급 반영.',
    '근무·급여 계산기', '일하는 시간이 남다른 모든 분을 위한 무료 계산기예요. 입력한 값은 이 화면에서만 계산해요.',
    hub_body, '', [
     ('계산기는 무료인가요?', '네, 로그인 없이 무료예요. 입력한 값은 휴대폰·컴퓨터 화면 안에서만 계산하고 보내지 않아요.'),
     ('요율은 언제 바뀌나요?', '최저시급과 4대보험 요율이 고시되면 밤낮 앱과 같은 자료를 고쳐서 계산기에도 바로 반영돼요.')],
    'hub', '매달 계산하기 번거롭다면', '밤낮 앱에 근무를 넣으면 주휴·야간·연장 수당까지 이번 달 예상 월급을 저절로 보여 주고, 근무마다 다른 알람도 맞춰요.', 1))
print('hub ok')

# =================== 2차: 더 많은 검색용 페이지 ===================
import datetime as dt
def remote(name): return json.load(open(os.path.join(ROOT, 'remote/v1', name + '.json')))['data']
SITEMAP = ['', 'calc/', 'calc/juhyu/', 'calc/alba/', 'calc/allowance/', 'calc/daily/', 'shift/']

# ---------- 월급 실수령액 ----------
write('calc/salary/', page(
    'calc/salary/', '월급·연봉 실수령액 계산기 2026 (4대보험·간이세액표) | 밤낮',
    '월급·연봉에서 4대보험과 근로소득세(간이세액표)를 뺀 실수령액을 계산해요. 부양가족·비과세 식대, 2027년 요율 반영.',
    '월급·연봉 실수령액 계산기', '세전 월급이나 연봉을 넣으면 4대보험과 소득세를 뺀 통장에 들어오는 돈을 계산해요. 소득세는 국세청 근로소득 간이세액표 원문 그대로예요.',
    '''<div class="card">
  <div class="chips" id="kind">
    <button type="button" class="chip" data-v="month" aria-pressed="true">월급</button>
    <button type="button" class="chip" data-v="year" aria-pressed="false">연봉</button>
  </div>
  <label for="amount" id="amountLabel">세전 월급 (원)</label>
  <input id="amount" inputmode="numeric" value="3000000" autocomplete="off">
  <label for="free">비과세 (식대 등, 월)<span class="hint">식대는 월 20만 원까지 비과세예요. 없으면 0.</span></label>
  <input id="free" inputmode="numeric" value="200000">
  <div class="row">
    <div><label for="family">부양가족 수 (본인 포함)</label><input id="family" inputmode="numeric" value="1"></div>
    <div><label for="kids">그중 8~20세 자녀</label><input id="kids" inputmode="numeric" value="0"></div>
  </div>
  <label>요율 연도</label>
  <div class="chips" id="years"></div>
</div>
<div class="card result" aria-live="polite">
  <div class="muted">한 달 실수령 (예상)</div>
  <div class="big" id="out">-</div>
  <table class="lines" id="lines"></table>
</div>''',
    '''(function(){
var B=Bamnat,$=function(i){return document.getElementById(i)},kind='month',year=new Date().getFullYear();
function calc(){
  var a=B.num($('amount').value);if(!a){$('out').textContent='-';$('lines').innerHTML='';return;}
  var gross=Math.floor(kind==='year'?a/12:a),free=Math.min(B.num($('free').value),gross),taxable=gross-free;
  var x=B.insurance(taxable,year),tax=B.incomeTax(taxable,B.num($('family').value)||1,B.num($('kids').value)),lt=tax?Math.floor(tax/100)*10:0;
  var ded=x.pension+x.health+x.care+x.employment+(tax||0)+lt,net=gross-ded,r=x.rates;
  $('out').textContent=B.won(net);
  $('lines').innerHTML=(kind==='year'?'<tr><td>월 세전 (연봉÷12)</td><td>'+B.won(gross)+'</td></tr>':'')+
    '<tr><td>국민연금 '+(r.pensionRate*50).toFixed(2)+'%</td><td>-'+B.won(x.pension)+'</td></tr>'+
    '<tr><td>건강보험 '+(r.healthRate*50).toFixed(3)+'%</td><td>-'+B.won(x.health)+'</td></tr>'+
    '<tr><td>장기요양</td><td>-'+B.won(x.care)+'</td></tr>'+
    '<tr><td>고용보험 '+(r.unemploymentRate*50).toFixed(1)+'%</td><td>-'+B.won(x.employment)+'</td></tr>'+
    (tax===null?'<tr><td colspan="2" class="note">소득세 표를 불러오지 못했어요. 잠시 뒤 다시 열어 주세요.</td></tr>':
    '<tr><td>근로소득세 (간이세액표)</td><td>-'+B.won(tax)+'</td></tr><tr><td>지방소득세 (10%)</td><td>-'+B.won(lt)+'</td></tr>')+
    '<tr><td>공제 합계</td><td>-'+B.won(ded)+'</td></tr>'+
    '<tr class="sum"><td>월 실수령</td><td>'+B.won(net)+'</td></tr>'+
    '<tr><td>1년 실수령 (×12)</td><td>'+B.won(net*12)+'</td></tr>';
}
$('kind').querySelectorAll('.chip').forEach(function(b){b.onclick=function(){if(b.getAttribute('aria-pressed')==='true')return;$('kind').querySelectorAll('.chip').forEach(function(c){c.setAttribute('aria-pressed','false')});b.setAttribute('aria-pressed','true');kind=b.dataset.v;
  $('amountLabel').textContent=kind==='year'?'세전 연봉 (원, 퇴직금 빼고)':'세전 월급 (원)';var v=B.num($('amount').value);$('amount').value=kind==='year'?v*12:Math.floor(v/12);calc();};});
Promise.all([B.ready,B.loadTaxTable().catch(function(){})]).then(function(){
  var box=$('years');B.years().forEach(function(y,i){var b=document.createElement('button');b.type='button';b.className='chip';b.textContent=y+'년';b.setAttribute('aria-pressed',i===0?'true':'false');
    b.onclick=function(){box.querySelectorAll('.chip').forEach(function(c){c.setAttribute('aria-pressed','false')});b.setAttribute('aria-pressed','true');year=y;calc();};box.appendChild(b);});
  year=B.years()[0]||year;calc();});
['amount','free','family','kids'].forEach(function(i){$(i).addEventListener('input',calc);});
})();''',
    [('실수령액은 어떻게 계산하나요?', '세전 월급에서 국민연금(2026년 4.75%), 건강보험(3.595%), 장기요양(건강보험료×13.14%), 고용보험(0.9%)과 근로소득세(간이세액표), 지방소득세(소득세의 10%)를 빼요. 비과세 식대는 보험료와 세금 계산에서 빠져요.'),
     ('근로소득세는 왜 회사마다 다르게 떼나요?', '회사는 간이세액표 금액의 80%, 100%, 120% 중 하나를 고를 수 있고(소득세법 시행령 제194조), 1년치는 연말정산에서 맞춰요. 이 계산기는 100% 기준이에요.'),
     ('2027년에는 얼마나 달라지나요?', '2027년에는 국민연금 근로자 몫이 5%로 올라요. 요율 연도에서 2027년을 고르면 바로 볼 수 있어요.'),
     ('야간·연장 수당이 달마다 달라요.', '밤낮 앱에 근무를 넣으면 그달 수당을 더한 세전 월급으로 실수령을 저절로 계산해요. 명세서 사진과 맞춰 볼 수도 있어요.')],
    'salary', '수당이 달마다 바뀐다면, 이번 달 실수령은?', '밤낮은 근무표로 야간·연장·휴일 수당을 더한 이번 달 세전 월급을 만들고, 같은 방식으로 실수령까지 보여 줘요.', 2))
SITEMAP.append('calc/salary/')

# ---------- 연차 ----------
write('calc/leave/', page(
    'calc/leave/', '연차 계산기 (입사일 기준 · 1년 미만 월차 · 근속 가산) | 밤낮',
    '입사일만 넣으면 지금 연차 개수, 다음 연차가 생기는 날, 근속별 연차(15~25일)를 바로 보여 줘요. 1년 미만 월차도.',
    '연차 계산기', '입사일만 넣으면 돼요. 1년 미만은 한 달 개근마다 1일, 1년이 지나면 15일, 그 뒤 2년마다 1일씩 늘어 25일까지예요.',
    '''<div class="card">
  <div class="row">
    <div><label for="start">입사일</label><input id="start" type="date" value="2025-03-02"></div>
    <div><label for="today">기준일</label><input id="today" type="date"></div>
  </div>
</div>
<div class="card result" aria-live="polite">
  <div class="muted" id="headline">지금 생긴 연차</div>
  <div class="big" id="out">-</div>
  <table class="lines" id="lines"></table>
</div>''',
    '''(function(){
var $=function(i){return document.getElementById(i)};
function d(v){var p=v.split('-');return new Date(+p[0],+p[1]-1,+p[2]);}
function addY(x,n){return new Date(x.getFullYear()+n,x.getMonth(),x.getDate());}
function addM(x,n){var m=x.getMonth()+n,last=new Date(x.getFullYear(),m+1,0).getDate();return new Date(x.getFullYear(),m,Math.min(x.getDate(),last));}
function f(x){return x.getFullYear()+'.'+(x.getMonth()+1)+'.'+x.getDate();}
function per(n){return Math.min(25,15+Math.floor((n-1)/2));}
function calc(){
  if(!$('start').value||!$('today').value){$('out').textContent='-';$('lines').innerHTML='';return;}var s=d($('start').value),t=d($('today').value);if(t<s){$('out').textContent='-';$('lines').innerHTML='';return;}
  var years=0;while(addY(s,years+1)<=t)years++;
  var rows='';
  if(years<1){var m=0;while(m<11&&addM(s,m+1)<=t)m++;
    $('headline').textContent='입사 1년 전, 지금까지 생긴 연차';$('out').textContent=m+'일';
    rows+='<tr><td>한 달 개근마다 1일 (최대 11일)</td><td>'+m+'일</td></tr>';
    if(m<11)rows+='<tr><td>다음 1일이 생기는 날</td><td>'+f(addM(s,m+1))+'</td></tr>';
    rows+='<tr><td>입사 1년 되는 날 ('+f(addY(s,1))+')</td><td>15일 생김</td></tr>';
  }else{$('headline').textContent='근속 '+years+'년, 올해 생긴 연차';$('out').textContent=per(years)+'일';
    rows+='<tr><td>다음 연차가 생기는 날 ('+f(addY(s,years+1))+')</td><td>'+per(years+1)+'일</td></tr>';}
  rows+='<tr><td colspan="2" class="note">근속 연수별 (입사일 기준)</td></tr>';
  [1,2,3,5,7,10,15,21].forEach(function(n){rows+='<tr><td>'+n+'년 ('+f(addY(s,n))+')</td><td>'+per(n)+'일</td></tr>';});
  $('lines').innerHTML=rows;
}
var n=new Date();$('today').value=n.getFullYear()+'-'+('0'+(n.getMonth()+1)).slice(-2)+'-'+('0'+n.getDate()).slice(-2);
['start','today'].forEach(function(i){$(i).addEventListener('input',calc);});calc();
})();''',
    [('연차는 몇 개 생기나요?', '1년 동안 80% 이상 출근하면 15일이 생기고, 3년 이상 일하면 처음 1년을 넘은 뒤 2년마다 1일씩 늘어 최대 25일이에요(근로기준법 제60조).'),
     ('입사 1년이 안 됐어요.', '1년 미만이거나 1년 동안 출근율이 80% 미만이면 한 달 개근할 때마다 1일씩, 최대 11일이 생겨요. 이 연차는 입사 1년이 되는 날까지 써야 해요.'),
     ('회사가 회계연도(1월 1일) 기준으로 줘요.', '회계연도 기준으로 줄 수도 있지만, 퇴사할 때 입사일 기준보다 적으면 그만큼 정산해야 해요. 이 계산기는 입사일 기준이에요.'),
     ('5명 미만 회사도 연차가 있나요?', '상시 근로자 5명 미만 사업장은 연차 규정이 적용되지 않아요(근로기준법 제11조).'),
     ('교대 근무라 연차를 언제 썼는지 헷갈려요.', '밤낮 앱 달력에 연차를 적으면 올해 쓴 날과 남은 날을 함께 보여 줘요.')],
    'leave', '쓴 연차, 남은 연차를 달력에서', '밤낮 달력에 연차·반차를 적으면 남은 연차와 쓴 날이 한눈에 보이고, 연차 날엔 근무 알람이 쉬어요.', 2))
SITEMAP.append('calc/leave/')

# ---------- 퇴직금 ----------
write('calc/severance/', page(
    'calc/severance/', '퇴직금 계산기 (평균임금 × 30일 × 재직일수/365) | 밤낮',
    '입사일·퇴사일과 최근 3개월 월급으로 평균임금과 예상 퇴직금을 법정 방식 그대로 계산해요. 상여·연차수당 반영.',
    '퇴직금 계산기', '입사일과 마지막 근무일, 최근 석 달 월급만 넣으면 돼요. 상여와 연차수당이 있으면 함께 넣어요.',
    '''<div class="card">
  <div class="row">
    <div><label for="start">입사일</label><input id="start" type="date" value="2022-03-02"></div>
    <div><label for="last">마지막 근무일</label><input id="last" type="date"></div>
  </div>
  <label for="pay">최근 3개월 월 평균 세전 임금 (원)<span class="hint">기본급과 매달 받은 수당(야간·연장 포함)을 더한 금액, 세금 떼기 전</span></label>
  <input id="pay" inputmode="numeric" value="3000000">
  <div class="row">
    <div><label for="bonus">1년 상여 합계</label><input id="bonus" inputmode="numeric" value="0"></div>
    <div><label for="leavepay">1년 연차수당</label><input id="leavepay" inputmode="numeric" value="0"></div>
  </div>
</div>
<div class="card result" aria-live="polite">
  <div class="muted">예상 퇴직금 (세전)</div>
  <div class="big" id="out">-</div>
  <table class="lines" id="lines"></table>
</div>''',
    '''(function(){
var B=Bamnat,$=function(i){return document.getElementById(i)};
function d(v){var p=v.split('-');return new Date(+p[0],+p[1]-1,+p[2]);}
function days(a,b){return Math.round((b-a)/864e5);}
function addM(x,n){var m=x.getMonth()+n,last=new Date(x.getFullYear(),m+1,0).getDate();return new Date(x.getFullYear(),m,Math.min(x.getDate(),last));}
function calc(){
  if(!$('start').value||!$('last').value){$('out').textContent='-';$('lines').innerHTML='';return;}
  var s=d($('start').value),l=d($('last').value),retire=new Date(l.getFullYear(),l.getMonth(),l.getDate()+1);
  var served=days(s,retire);if(served<=0){$('out').textContent='-';$('lines').innerHTML='';return;}
  var from=addM(retire,-3),n3=days(from,retire),year=new Date(s.getFullYear()+1,s.getMonth(),s.getDate())<=retire;
  var pay=B.num($('pay').value)*3,bonus=B.num($('bonus').value)*3/12,lp=B.num($('leavepay').value)*3/12;
  var avg=(pay+bonus+lp)/n3,sev=Math.floor(avg*30*served/365);
  var rows='<tr><td>재직 일수</td><td>'+served.toLocaleString('ko-KR')+'일 (약 '+(Math.floor(served/365*10)/10)+'년)</td></tr>'+
    '<tr><td>최근 3개월 일수</td><td>'+n3+'일</td></tr>'+
    '<tr><td>3개월 임금 + 상여·연차수당의 3/12</td><td>'+B.won(pay+bonus+lp)+'</td></tr>'+
    '<tr><td>1일 평균임금</td><td>'+B.won(avg)+'</td></tr>';
  if(!year){$('out').textContent='0원';rows+='<tr><td colspan="2">계속 근로 1년 미만이면 퇴직금이 생기지 않아요.</td></tr>';}
  else{$('out').textContent=B.won(sev);rows+='<tr class="sum"><td>평균임금 × 30일 × 재직일수 ÷ 365</td><td>'+B.won(sev)+'</td></tr>';}
  $('lines').innerHTML=rows;
}
var n=new Date();$('last').value=n.getFullYear()+'-'+('0'+(n.getMonth()+1)).slice(-2)+'-'+('0'+n.getDate()).slice(-2);
['start','last','pay','bonus','leavepay'].forEach(function(i){$(i).addEventListener('input',calc);});calc();
})();''',
    [('퇴직금은 누가 받나요?', '같은 회사에서 1년 이상 일했고 4주 평균 1주 15시간 이상 일한 근로자가 받아요(근로자퇴직급여 보장법 제4조). 알바·계약직도 같아요.'),
     ('평균임금은 어떻게 구하나요?', '퇴직 전 3개월 동안 받은 임금 총액을 그 3개월의 날수(89~92일)로 나눠요. 1년 상여와 연차수당은 3/12만 더해요(근로기준법 제2조).'),
     ('야간·연장 수당도 들어가나요?', '네. 매달 받은 야간·연장·휴일 수당도 임금이라 평균임금에 들어가요. 교대 근무자는 수당이 많은 달이 마지막 3개월이면 퇴직금이 커져요.'),
     ('세금은 얼마나 떼나요?', '퇴직금에는 퇴직소득세가 따로 붙어요. 근속 연수가 길수록 공제가 커서 근로소득세보다 훨씬 적어요. 이 계산기는 세전 금액이에요.')],
    'severance', '퇴직 전 3개월 수당까지 기록해 두세요', '밤낮에 근무를 넣어 두면 달마다 야간·연장 수당을 담은 예상 월급이 남아 퇴직금 평균임금을 맞춰 보기 좋아요.', 2))
SITEMAP.append('calc/severance/')
print('salary leave severance ok')

# ---------- 공휴일 ----------
hol = remote('holidays')
WD = '월화수목금토일'
for year in range(max(hol['fromYear'], 2026), hol['toYear'] + 1):
    days = [h for h in hol['days'] if h['date'].startswith(str(year))]
    hd = {h['date']: h['name'] for h in days}
    def off(x): return x.weekday() >= 5 or x.isoformat() in hd
    rows = ''.join(f"<tr><td>{int(h['date'][5:7])}월 {int(h['date'][8:])}일 ({WD[dt.date.fromisoformat(h['date']).weekday()]})</td><td>{html.escape(h['name'])}</td></tr>" for h in days)
    weekday_n = sum(1 for h in days if dt.date.fromisoformat(h['date']).weekday() < 5)
    # 사흘 이상 이어 쉬는 연휴 (주말 포함, 공휴일이 하루 이상)
    runs, cur = [], []
    x = dt.date(year, 1, 1)
    while x.year == year:
        if off(x): cur.append(x)
        else:
            if len(cur) >= 3 and any(c.isoformat() in hd for c in cur): runs.append(cur)
            cur = []
        x += dt.timedelta(days=1)
    if len(cur) >= 3 and any(c.isoformat() in hd for c in cur): runs.append(cur)
    longs = ''.join(f"<li>{r[0].month}/{r[0].day}({WD[r[0].weekday()]}) ~ {r[-1].month}/{r[-1].day}({WD[r[-1].weekday()]}) · <strong>{len(r)}일</strong> · {html.escape(', '.join(sorted(set(hd[c.isoformat()] for c in r if c.isoformat() in hd))))}</li>" for r in runs)
    body = f'''<div class="card">
  <strong>{year}년 공휴일 {len(days)}일</strong> <span class="muted">· 대체공휴일 포함, 그중 월~금 {weekday_n}일</span>
  <table class="lines" style="margin-top:8px">{rows}</table>
</div>
<h2>{year}년 사흘 이상 연휴</h2>
<ul class="links">{longs}</ul>
<h2>교대 근무자라면: 공휴일에 나는 쉬나, 일하나</h2>
<p class="note">교대 근무는 공휴일과 상관없이 순서대로 돌아요. 근무 순서와 첫날을 넣으면 {year}년 공휴일 중 내가 쉬는 날과 일하는 날(휴일 근로 수당이 붙을 수 있는 날)을 나눠 보여 줘요.</p>
<div class="card">
  <label for="pat">근무 순서</label><select id="pat"></select>
  <label for="first">이 순서의 첫날</label><input id="first" type="date">
  <div id="split" aria-live="polite" style="margin-top:12px"></div>
</div>'''
    script = '''(function(){
var B=Bamnat,$=function(i){return document.getElementById(i)},H=%s,NAME={D:'주간',E:'오후',N:'야간',O:'휴무',X:'당번',B:'비번'};
function draw(){var c=$('pat').value,f=$('first').value;if(!c||!f)return;var p=f.split('-'),s=new Date(+p[0],+p[1]-1,+p[2]),rest=[],work=[];
  H.forEach(function(h){var q=h[0].split('-'),d=new Date(+q[0],+q[1]-1,+q[2]),k=Math.round((d-s)/864e5)%%c.length;if(k<0)k+=c.length;var code=c[k],t=(+q[1])+'/'+(+q[2])+' '+h[1];
    (code==='O'||code==='B'?rest:work).push(t+(code==='O'||code==='B'?'':' · '+NAME[code]));});
  $('split').innerHTML='<strong>쉬는 공휴일 '+rest.length+'일</strong><br><span class="muted">'+(rest.join(', ')||'없음')+'</span><br><br><strong>일하는 공휴일 '+work.length+'일</strong><br><span class="muted">'+(work.join(', ')||'없음')+'</span><br><br><a href="../../calc/allowance/">휴일에 일하면 수당은? 휴일수당 계산기</a>';}
fetch(B.base+'remote/v1/presets.json').then(function(r){return r.json();}).then(function(j){
  $('pat').innerHTML=j.data.patterns.filter(function(p){return p.codes!=='DDDDDOO';}).map(function(p){return '<option value="'+p.codes+'">'+p.name+' · '+p.description+'</option>';}).join('');draw();});
var n=new Date();$('first').value=n.getFullYear()+'-'+('0'+(n.getMonth()+1)).slice(-2)+'-'+('0'+n.getDate()).slice(-2);
$('pat').addEventListener('change',draw);$('first').addEventListener('input',draw);
})();''' % json.dumps([[h['date'], h['name']] for h in days], ensure_ascii=False)
    write(f'holiday/{year}/', page(
        f'holiday/{year}/', f'{year}년 공휴일·대체공휴일·연휴 달력 (교대 근무자 쉬는 날 확인) | 밤낮',
        f'{year}년 공휴일 {len(days)}일과 대체공휴일, 연휴를 한눈에. 교대 순서를 넣으면 공휴일 중 쉬는 날과 일하는 날을 나눠 봐요.',
        f'{year}년 공휴일·연휴 달력', f'{year}년 공휴일과 대체공휴일, 주말을 붙여 쉬는 연휴를 모았어요. 교대 근무자는 아래에서 내 근무 순서로 공휴일에 쉬는지 바로 봐요.',
        body, script,
        [(f'{year}년 공휴일은 며칠인가요?', f'관공서의 공휴일에 관한 규정과 한국천문연구원 월력요항 기준으로 대체공휴일까지 {len(days)}일이에요(일요일과 겹친 날 포함, 그중 월~금 {weekday_n}일). 임시공휴일이 정해지면 고쳐요.'),
         ('민간 회사도 공휴일에 쉬나요?', '상시 5명 이상 사업장은 공휴일이 유급휴일이에요(근로기준법 제55조 제2항). 쉬는 날에 일하면 휴일 근로 수당(8시간까지 50%)을 받아요. 교대 근무는 대체휴일 합의가 있으면 다른 날 쉬기도 해요.'),
         ('5월 1일 노동절(근로자의 날)도 쉬나요?', '5월 1일은 법으로 정한 유급휴일이라 근로자는 쉬고, 일하면 휴일 근로 수당을 받아요.'),
         ('공휴일 근무 알람을 따로 맞춰야 하나요?', '밤낮 앱은 공휴일을 달력에 표시하고, 근무 순서대로 근무 알람을 울려요. 쉬는 날엔 알람이 조용해요.')],
        f'holiday{year}', f'{year}년 근무와 공휴일을 한 달력에', '밤낮 앱은 공휴일·대체공휴일을 근무 달력에 함께 보여 주고, 공휴일 근무엔 휴일 수당까지 이번 달 월급에 넣어요.', 2))
    SITEMAP.append(f'holiday/{year}/')
print('holiday ok')

# ---------- 근무 순서별 ----------
pre = remote('presets')['patterns']
NAME = {'D': '주간', 'E': '오후', 'N': '야간', 'O': '휴무', 'X': '당번', 'B': '비번'}
SHORT = {'D': '주', 'E': '오', 'N': '야', 'O': '휴', 'X': '당', 'B': '비'}
def strip(codes, n):
    cells = ''.join(f'<div class="d"><span class="n">{i+1}일</span><span class="s {c}">{SHORT[c]}</span></div>' for i, c in enumerate((codes * 10)[:n]))
    return f'<div class="cal">{cells}</div>'
def stats(codes):
    L = len(codes); work = sum(1 for c in codes if c not in 'OB'); nights = codes.count('N')
    per_month = 365 / 12 * work / L
    return L, work, nights, per_month
pat_links = []
for p in pre:
    if p['codes'] == 'DDDDDOO': continue
    slug = p['id'][2:]
    L, work, nights, pm = stats(p['codes'])
    seq = '·'.join(NAME[c] for c in p['codes'])
    others = ''.join(f'<li><a href="../{q["id"][2:]}/">{html.escape(q["name"])}</a> · {html.escape(q["description"])}</li>' for q in pre if q is not p and q['codes'] != 'DDDDDOO')
    src = f'<p class="note">출처: {html.escape(p["source"])}</p>' if p.get('source') else ''
    body = f'''<div class="card">
  <table class="lines info">
    <tr><td>순서</td><td>{html.escape(p["description"])}</td></tr>
    <tr><td>한 바퀴</td><td>{L}일</td></tr>
    <tr><td>한 바퀴 중 근무</td><td>{work}일{f" (야간 {nights}일)" if nights else ""}</td></tr>
    <tr><td>한 달 평균 근무일</td><td>약 {pm:.1f}일</td></tr>
  </table>
  <p class="note">순서: {html.escape(seq)}</p>
  {src}
</div>
<h2>4주 미리 보기</h2>
{strip(p["codes"], 28)}
<p><a class="btn" href="../../?p={p["codes"]}">내 첫날로 석 달 달력 보기</a></p>
<h2>다른 교대 순서</h2>
<ul class="links">{others}</ul>
<p class="note">우리 회사 방식을 모르면 밤낮 앱의 회사 찾기에서 회사 이름으로 골라요.</p>'''
    write(f'shift/p/{slug}/', page(
        f'shift/p/{slug}/', f'{p["name"]} 근무표 ({p["description"]}) 달력 만들기 | 밤낮',
        f'{p["name"]} 근무표: {L}일 주기, 한 달 평균 약 {pm:.0f}일 근무. 첫날만 넣으면 석 달 근무 달력을 바로 보여 줘요.',
        f'{p["name"]} 근무표', f'{html.escape(p["description"])} 순서로 {L}일마다 돌아요. 첫 근무 날만 고르면 몇 달치 달력이 나와요.',
        body, '',
        [(f'{p["name"]}는 한 달에 며칠 일하나요?', f'{L}일 중 {work}일 일해서 한 달(30.4일)에 평균 약 {pm:.1f}일이에요. 달마다 하루 이틀 차이가 나요.'),
         (f'{p["name"]} 근무 알람을 맞추려면?', '밤낮 앱에서 같은 순서를 고르면 근무마다 다른 시각에 알람이 울리고, 쉬는 날엔 조용해요. 대근·연차로 바뀐 날만 눌러 고치면 돼요.')],
        f'shift_{slug}', f'{p["name"]}, 알람까지 한 번에', f'밤낮 앱에서 {p["name"]}를 고르면 몇 년치 근무가 채워지고 주간·야간마다 다른 근무 알람이 울려요.', 3))
    SITEMAP.append(f'shift/p/{slug}/')
    pat_links.append((slug, p))

pl = ''.join(f'<li><a href="p/{s}/">{html.escape(p["name"])}</a> · {html.escape(p["description"])}</li>' for s, p in pat_links)
open(os.path.join(ROOT, 'shift/index.html'), 'a').close()
s = open(os.path.join(ROOT, 'shift/index.html')).read()
s = s.replace('<h2 id="order-list">교대 순서별 설명</h2>', f'<h2 id="order-list">교대 순서별 설명</h2><ul class="links">{pl}</ul>', 1)
open(os.path.join(ROOT, 'shift/index.html'), 'w').write(s)

# ---------- 모음·첫 화면·사이트맵 ----------
hub = open(os.path.join(ROOT, 'calc/index.html')).read()
hub = hub.replace('<li><a href="daily/">', '''<li><a href="salary/"><strong>월급·연봉 실수령액 계산기</strong></a><br><span class="muted">4대보험과 간이세액표 소득세를 뺀 통장 금액</span></li>
  <li><a href="leave/"><strong>연차 계산기</strong></a><br><span class="muted">입사일로 지금 연차와 근속별 개수</span></li>
  <li><a href="severance/"><strong>퇴직금 계산기</strong></a><br><span class="muted">평균임금 × 30일 × 재직일수/365</span></li>
  <li><a href="daily/">''', 1).replace('교대 순서별 설명</span></li>\n</ul>', '교대 순서별 설명</span></li>\n' + ''.join(f'<li><a href="../holiday/{y}/"><strong>{y}년 공휴일·연휴 달력</strong></a><br><span class="muted">교대 근무자 쉬는 공휴일 확인</span></li>' for y in (2026, 2027)) + '\n</ul>', 1)
open(os.path.join(ROOT, 'calc/index.html'), 'w').write(hub)
sm = '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' + ''.join(f'  <url><loc>{SITE}{u}</loc><lastmod>{LASTMOD}</lastmod></url>\n' for u in SITEMAP + ['privacy/', 'terms/', 'community/']) + '</urlset>\n'
open(os.path.join(ROOT, 'sitemap.xml'), 'w').write(sm)
print('shift pages', len(pat_links), 'sitemap', len(SITEMAP))
