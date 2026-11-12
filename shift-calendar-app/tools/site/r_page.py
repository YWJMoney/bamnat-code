# 레시피 링크 받는 쪽 bamnat.app/r/?c=<코드> [WEB-18][I50a-05]: 웹 2판 머리·바닥과 calc.css, 카톡 미리보기(og), 코드가 없으면 단추를 숨겨요.
# 검색에는 내보내지 않아요(noindex). robots.txt로 막지 않아야 검색 로봇이 noindex를 읽어요.
# 받는 쪽 페이지라 Play 링크 출처는 bamnat_share예요(body data-source, calc.js가 읽어요).
import html

from site_common import *

TITLE = '밤낮 나눔 판 레시피'
DESC = '다른 분이 나눈 근무 색·알람·메모 틀·규칙이에요. 밤낮 앱에서 미리 보고, 고른 것만 내 달력에 넣어요.'

s = f'''<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="robots" content="noindex">
<title>{TITLE} | 밤낮</title>
<meta name="description" content="{html.escape(DESC)}">
<meta name="theme-color" content="#15192b">
<meta property="og:type" content="website">
<meta property="og:site_name" content="밤낮">
<meta property="og:title" content="{TITLE}">
<meta property="og:description" content="{html.escape(DESC)}">
<meta property="og:locale" content="ko_KR">
<meta property="og:image" content="{SITE}og/recipe.png">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="{TITLE} · 밤낮">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="../assets/icon.svg" type="image/svg+xml">
<link rel="preload" href="../assets/fonts/pretendard.css" as="style">
<link rel="stylesheet" href="../assets/fonts/pretendard.css">
<link rel="stylesheet" href="../calc/calc.css">
<script src="../calc/calc.js"></script>
<style>
.code {{ font: 800 30px/1.2 ui-monospace, 'SFMono-Regular', Menlo, monospace; letter-spacing: 5px; text-align: center; margin: 6px 0 4px; font-variant-numeric: tabular-nums; }}
.code.none {{ font: 650 17px/1.5 var(--font); letter-spacing: 0; color: var(--text-2); }}
.recipe .btns {{ justify-content: center; }}
.recipe .btn {{ min-width: 220px; }}
.recipe ol {{ margin: 8px 0 0; padding-left: 20px; color: var(--text-2); font-size: 14.5px; }}
.recipe ol li + li {{ margin-top: 4px; }}
</style>
</head>
<body class="k-recipe" data-source="bamnat_share">
{site_header('../', 'r/')}
<header class="page-hero">
  <div class="in">
    <h1>{TITLE}</h1>
    <p class="lead">{DESC}</p>
    <div class="tags"><span>7일 안에 되돌리기</span><span>고른 것만 넣기</span><span>로그인 없이</span></div>
  </div>
</header>
<main id="main" class="tool">
<div class="card recipe ios-skip">
  <div class="muted" style="text-align:center">레시피 코드</div>
  <div class="code" id="code" aria-live="polite">--------</div>
  <div class="btns">
    <a class="btn" id="open" href="#">밤낮 앱에서 열기</a>
    <button type="button" class="chip" id="copy">코드 복사</button>
  </div>
  <span class="say" id="copied" aria-live="polite"></span>
  <p class="note" id="after">앱이 없으면 아래에서 받은 뒤 이 링크를 다시 누르면 앱에서 열려요.</p>
  <p class="btns"><a class="chip" id="get" data-play="recipe" data-medium="recipe" href="{PLAY}">{PLAY_ICON}밤낮 앱 받기 · 무료</a></p>
  <strong>앱이 열리지 않으면</strong>
  <ol><li>밤낮 앱을 열어요.</li><li>광장 › 나눔 판 › 코드로 받기를 눌러요.</li><li>위 코드를 넣어요.</li></ol>
  <p class="note">이 쪽은 아무것도 저장하거나 보내지 않아요.</p>
</div>
</main>
{site_footer('../')}
<script>
(function () {{
  var $ = function (i) {{ return document.getElementById(i); }};
  var c = (new URLSearchParams(location.search).get('c') || '').toUpperCase();
  // 1.39 [SPREAD-03] 밤낮은 Android 앱이에요. 아이폰이면 받기·열기 단추 대신 알려요.
  if (Bamnat.ios(navigator.userAgent, navigator.maxTouchPoints || 0)) {{
    $('get').parentNode.hidden = true; $('open').hidden = true;
    $('after').textContent = '밤낮은 Android 앱이에요. 레시피는 Android 휴대폰의 밤낮 앱에서 받을 수 있어요.';
  }}
  if (!/^[2-9A-HJ-NP-Z]{{8}}$/.test(c)) {{
    // [WEB-18] 코드가 없거나 틀리면 누를 수 없는 단추를 남기지 않아요.
    $('code').textContent = '링크에 레시피 코드가 없어요. 받은 링크를 다시 눌러 주세요.'; $('code').className = 'code none';
    $('open').hidden = true; $('copy').hidden = true; $('after').hidden = true;
    return;
  }}
  $('code').textContent = c;
  $('open').href = 'bamnat://r/' + c;
  $('copy').onclick = function () {{
    if (navigator.clipboard) navigator.clipboard.writeText(c).then(function () {{ $('copied').textContent = '코드를 복사했어요.'; }}, function () {{ $('copied').textContent = '복사하지 못했어요. 코드를 길게 눌러 복사해 주세요.'; }});
    else $('copied').textContent = '코드를 길게 눌러 복사해 주세요.';
  }};
}})();
</script>
</body>
</html>
'''
write('r/', s, sitemap=False)
print('r ok')
