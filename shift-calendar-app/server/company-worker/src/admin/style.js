// AX 1.30 [ADMX-12] The developer app's stylesheet, out of src/admin-app.js (served as /admin/app.css). Plain CSS:
// the CSP allows no style attributes, so every look is a class here. AX 1.30 rules are at the end.
export const ADMIN_CSS = `
.photos{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}.photos img{width:120px;height:120px;object-fit:cover;border-radius:12px;border:1px solid var(--line)}
.bars{list-style:none;display:flex;align-items:flex-end;gap:2px;height:96px;padding:0;margin:8px 0}.bars li{flex:1;height:100%;display:flex;align-items:flex-end}
.bars span{display:block;width:100%;min-height:2px;border-radius:3px 3px 0 0;background:var(--gold, #C2A36B)}
:root{--bg:#F5F3EE;--surface:#fff;--raised:#fff;--fg:#14182A;--muted:#5B6075;--faint:#62677A;--line:#E3DFD6;--line-strong:#D2CCBF;
--soft:#EFEBE3;--hover:#F2EFE8;--accent:#2B57E0;--accent-soft:#E6ECFD;--on-accent:#fff;--gold:#B8955A;--gold-soft:#F4EBDC;--nav:#15192B;
--nav-fg:#C9CCDA;--nav-on:#fff;--nav-hi:rgba(255,255,255,.08);--ok:#17784A;--ok-bg:#E1F2E8;--warn:#8A5300;--warn-bg:#FBEED5;--bad:#BE3326;
--bad-bg:#FBE5E2;--info:#2B57E0;--info-bg:#E6ECFD;--shadow:0 1px 2px rgba(20,24,42,.05),0 4px 16px rgba(20,24,42,.05);
--shadow-lg:0 20px 60px rgba(10,12,22,.28);--r:16px;--r-sm:12px;--r-lg:22px;--focus:0 0 0 3px color-mix(in srgb,var(--accent) 40%,transparent)}
/* [ADMUX-05] Light, dark or as the system: 설정 › 이 기기 sets data-theme; without it the system decides. */
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#0E111C;--surface:#161A28;--raised:#1C2131;--fg:#EEEAE2;--muted:#A3A7B8;--faint:#9095AB;--line:#272D40;
--line-strong:#343B52;--soft:#1F2435;--hover:#20263A;--accent:#8EA8FF;--accent-soft:#1E2747;--on-accent:#0B1022;--gold:#D2B47A;--gold-soft:#2C2618;
--nav:#0A0D17;--nav-hi:rgba(255,255,255,.06);--ok:#7DD3A3;--ok-bg:#12302A;--warn:#F0C272;--warn-bg:#352A12;--bad:#FF8F84;--bad-bg:#3A1C1B;
--info:#8EA8FF;--info-bg:#1E2747;--shadow:none;--shadow-lg:0 20px 60px rgba(0,0,0,.6);color-scheme:dark}}
:root[data-theme="dark"]{--bg:#0E111C;--surface:#161A28;--raised:#1C2131;--fg:#EEEAE2;--muted:#A3A7B8;--faint:#9095AB;--line:#272D40;
--line-strong:#343B52;--soft:#1F2435;--hover:#20263A;--accent:#8EA8FF;--accent-soft:#1E2747;--on-accent:#0B1022;--gold:#D2B47A;--gold-soft:#2C2618;
--nav:#0A0D17;--nav-hi:rgba(255,255,255,.06);--ok:#7DD3A3;--ok-bg:#12302A;--warn:#F0C272;--warn-bg:#352A12;--bad:#FF8F84;--bad-bg:#3A1C1B;
--info:#8EA8FF;--info-bg:#1E2747;--shadow:none;--shadow-lg:0 20px 60px rgba(0,0,0,.6);color-scheme:dark}
:root[data-theme="light"]{color-scheme:light}
/* [ADMUX-05] 글자 크기: everything is in rem, so one root size scales the app. */
:root[data-size="small"]{font-size:93.75%}:root[data-size="large"]{font-size:112.5%}:root[data-size="xlarge"]{font-size:125%}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%;color-scheme:light dark;scroll-padding-top:calc(var(--top-h,4.5rem) + .5rem)}
:root[data-theme]{color-scheme:light}
body{margin:0;min-height:100vh;min-height:100dvh;background:var(--bg);color:var(--fg);
font:1rem/1.55 system-ui,-apple-system,"Pretendard","Noto Sans KR","Apple SD Gothic Neo","Malgun Gothic",sans-serif;
-webkit-tap-highlight-color:transparent;word-break:keep-all;overflow-wrap:anywhere;font-feature-settings:"tnum" 1}
button,input,textarea,select{font:inherit;color:inherit}
h1,h2,h3{margin:0;line-height:1.3;letter-spacing:-.01em}
a{color:var(--accent)}
svg{fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
:focus-visible{outline:2px solid transparent;outline-offset:2px;box-shadow:var(--focus);border-radius:var(--r-sm)}
.sr{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}
.skip{position:fixed;left:.5rem;top:-4rem;z-index:50;padding:.6rem 1rem;border-radius:var(--r-sm);background:var(--accent);color:var(--on-accent);font-weight:800}
.skip:focus{top:.5rem}

/* ---- layout: phone (bottom bar), Fold unfolded and tablets (rail), computers (sidebar) ---- */
.shell{min-height:100vh;min-height:100dvh}
.side{display:none}
.content{min-width:0}
.top{position:sticky;top:0;z-index:5;background:color-mix(in srgb,var(--bg) 88%,transparent);backdrop-filter:saturate(1.4) blur(14px);
-webkit-backdrop-filter:saturate(1.4) blur(14px);border-bottom:1px solid var(--line);padding-top:env(safe-area-inset-top)}
.bar{display:flex;align-items:center;gap:.5rem;max-width:72rem;margin:0 auto;padding:.5rem .75rem;min-height:3.75rem}
.bar h1{flex:1;min-width:0;font-size:1.2rem;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.bar .sub{display:block;font-size:.78rem;font-weight:600;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.mark{width:2.1rem;height:2.1rem;margin:0 .35rem;border-radius:.65rem;flex:none}
.progress{height:2px;overflow:hidden}
.progress.on{background:linear-gradient(90deg,transparent,var(--accent),transparent) no-repeat;background-size:50% 100%;animation:slide 1s linear infinite}
@keyframes slide{from{background-position:-50% 0}to{background-position:150% 0}}
.main{max-width:72rem;margin:0 auto;padding:1rem 1rem calc(6.5rem + env(safe-area-inset-bottom))}
.main:focus{box-shadow:none}
.tabs{position:fixed;left:0;right:0;bottom:0;z-index:6;display:flex;justify-content:center;background:color-mix(in srgb,var(--surface) 94%,transparent);
backdrop-filter:blur(14px);-webkit-backdrop-filter:blur(14px);border-top:1px solid var(--line);padding:.35rem .35rem calc(.35rem + env(safe-area-inset-bottom))}
.tab{position:relative;flex:1;max-width:9rem;display:flex;flex-direction:column;align-items:center;gap:.1rem;padding:.25rem;border:0;background:none;
color:var(--muted);font-size:.8rem;font-weight:700;cursor:pointer;text-decoration:none;min-height:3.4rem;justify-content:center}
.tab{min-width:0}
.tab .nav-label{max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.tab svg{width:1.45rem;height:1.45rem;padding:.25rem min(1.1rem,3vw);box-sizing:content-box;border-radius:999px;transition:background .2s,color .2s}
.tab[aria-current=page]{color:var(--fg)}
.tab[aria-current=page] svg{background:var(--accent-soft);color:var(--accent)}
.badge{min-width:1.25rem;height:1.25rem;padding:0 .4rem;border-radius:.65rem;background:var(--accent);color:var(--on-accent);font-size:.7rem;font-weight:800;
line-height:1.25rem;text-align:center;font-style:normal}
.tab .badge{position:absolute;top:.1rem;left:calc(50% + .55rem)}
.badge:empty{display:none}
@media (min-width:600px){
  .shell{display:grid;grid-template-columns:5.5rem 1fr}
  .tabs{display:none}
  .side{display:flex;flex-direction:column;position:sticky;top:0;height:100vh;height:100dvh;background:var(--nav);color:var(--nav-fg);
  padding:calc(.75rem + env(safe-area-inset-top)) .5rem .75rem;overflow-y:auto;overscroll-behavior:contain}
  .side .brand-row{display:flex;justify-content:center;padding:.25rem 0 1rem}
  .side .brand-row img{width:2.4rem;height:2.4rem;border-radius:.75rem}
  .side .brand-row b,.side .who,.side .group{display:none}
  .nav{list-style:none;margin:0;padding:0;display:grid;gap:.15rem}
  .nav a{position:relative;display:flex;flex-direction:column;align-items:center;gap:.15rem;padding:.55rem .25rem;border-radius:var(--r-sm);
  color:var(--nav-fg);text-decoration:none;font-size:.75rem;font-weight:700;text-align:center}
  @media (hover:hover){.nav a:hover{background:var(--nav-hi);color:var(--nav-on)}}
  .nav a[aria-current=page]{background:rgba(255,255,255,.12);color:var(--nav-on)}
  .nav svg{width:1.4rem;height:1.4rem}
  .nav .badge{position:absolute;top:.2rem;right:.35rem}
  .side .foot{margin-top:auto;display:grid;gap:.25rem;padding-top:.75rem}
  .main{padding:1.25rem 1.5rem 3rem}
  .bar{padding:.5rem 1.5rem}
  .bar .mark{display:none}
  .hubtabs{padding:0 1.5rem .5rem}
  .toast{bottom:1.5rem}
}
@media (min-width:1024px){
  .shell{grid-template-columns:15.5rem 1fr}
  .side{padding:calc(1rem + env(safe-area-inset-top)) .75rem 1rem}
  .side .brand-row{justify-content:flex-start;align-items:center;gap:.65rem;padding:.25rem .5rem 1.25rem}
  .side .brand-row b{display:block;color:var(--nav-on);font-size:1.05rem}
  .side .group{display:block;margin:1rem .75rem .35rem;font-size:.75rem;font-weight:800;letter-spacing:.04em;color:color-mix(in srgb,var(--nav-fg) 70%,transparent)}
  .nav a{flex-direction:row;gap:.75rem;padding:.6rem .75rem;font-size:.92rem;text-align:left}
  .nav .badge{position:static;margin-left:auto}
  .nav li.open .subnav{display:grid}
  .nav .subnav a{padding:.45rem .75rem;font-size:.86rem;font-weight:700;color:var(--nav-fg)}
  .nav .subnav a[aria-current=page]{background:var(--nav-hi);color:var(--nav-on);box-shadow:inset 3px 0 0 var(--gold)}
  .side .who{display:flex;align-items:center;gap:.6rem;padding:.6rem .75rem;border-radius:var(--r-sm);color:var(--nav-on);text-decoration:none}
  @media (hover:hover){.side .who:hover{background:var(--nav-hi)}}
  .side .who small{display:block;color:var(--nav-fg);font-size:.75rem;font-weight:600}
  .side .compact-only{display:none}
  .avatar{flex:none;display:inline-grid;place-items:center;width:2.1rem;height:2.1rem;border-radius:50%;background:var(--gold);color:#15192B;font-weight:900}
}

/* ---- AN 1.28 [ADMUX-30][ADMUX-31][ADMUX-33] hubs: the tabs under the title, the sidebar's open hub, what waits ---- */
.hubtabs{display:flex;gap:.25rem;max-width:72rem;margin:0 auto;padding:0 .75rem .45rem;overflow-x:auto;scrollbar-width:none;overscroll-behavior-x:contain}
.hubtabs[hidden]{display:none}
.hubtabs::-webkit-scrollbar{display:none}
.hubtabs a{flex:none;display:inline-flex;align-items:center;gap:.35rem;min-height:2.75rem;padding:0 .95rem;border-radius:999px;color:var(--muted);
font-weight:800;font-size:.9rem;text-decoration:none;white-space:nowrap}
@media (hover:hover){.hubtabs a:hover{background:var(--hover);color:var(--fg)}}
.hubtabs a[aria-current=page]{background:var(--accent-soft);color:var(--accent)}
.subnav{display:none;list-style:none;margin:.1rem 0 .35rem;padding:0 0 0 2.15rem;gap:.05rem}
.waiting{display:flex;flex-wrap:wrap;align-items:center;gap:.4rem;margin:0 .25rem .85rem;font-size:.88rem;color:var(--muted)}
.waiting b{color:var(--fg);font-weight:800;margin-right:.2rem}
.waiting a{display:inline-flex;align-items:center;gap:.35rem;min-height:2.5rem;padding:0 .8rem;border-radius:999px;background:var(--soft);color:var(--fg);
font-weight:700;text-decoration:none}
@media (hover:hover){.waiting a:hover{background:var(--hover)}}
.waiting a .n{color:var(--fg);font-weight:900}
/* 2.0.0 [RVA-33] 검토 HQ-65(조건 6): 빨강은 기한 넘김·오류가 있는 칸의 글 한 조각에만. */
.waiting a .late{color:var(--bad);font-size:min(.8rem,16px);font-weight:800;white-space:nowrap}
.waiting .dot{display:none}
.todo-card .waiting{margin:0 0 .75rem}
/* 2.0.0 [RVA-24] 3/3(총괄 11-12): 글자를 크게 키운 폰 폭에서 '지금 기다리는 것' 칩이 한 줄에 하나씩 쌓이지 않게 두 개씩. */
.waiting{container:waiting/inline-size}
@container waiting (width < 14rem){.waiting b{flex-basis:100%}
  p.waiting a{min-height:min(2.75rem,48px)}
  .waiting a{font-size:min(.88rem,18px);flex:1 1 calc(50% - .4rem);min-width:0;justify-content:center;flex-wrap:wrap;gap:0 .3rem;padding:.2rem min(.8rem,10px);text-align:center;word-break:keep-all;line-height:1.25}}
.todo-card .rows{box-shadow:none;margin-bottom:.5rem}
.rows.inbox>li>details.quick{margin:0 1rem .75rem;border-top:0;padding-top:0}
.quick form{display:grid;gap:.5rem}
.quick textarea{min-height:5.5rem}
.bundle-list{margin:.4rem 0;display:grid;gap:.35rem}
.bundle-list p{margin:0;display:flex;flex-wrap:wrap;align-items:baseline;gap:.25rem .5rem;padding:.35rem 0;border-top:1px solid var(--line)}
.bundle-list p a{flex:1 1 12rem;min-width:0;overflow-wrap:anywhere}
.section svg{width:1.15rem;height:1.15rem}

/* ---- buttons and fields ---- */
.icon-btn{display:inline-grid;place-items:center;width:2.75rem;height:2.75rem;border:0;border-radius:var(--r-sm);background:none;cursor:pointer;flex:none;color:inherit}
@media (hover:hover){.icon-btn:hover{background:var(--hover)}}
.icon-btn svg{width:1.4rem;height:1.4rem}
.btn{white-space:nowrap;display:inline-flex;align-items:center;justify-content:center;gap:.4rem;min-height:2.75rem;padding:0 1.05rem;border-radius:var(--r-sm);
border:1px solid var(--line-strong);background:var(--surface);font-weight:800;font-size:.94rem;cursor:pointer;text-decoration:none;color:var(--fg);
transition:transform .1s,background .15s,border-color .15s;white-space:nowrap}
@media (hover:hover){.btn:hover{background:var(--hover)}}
.btn svg{width:1.15rem;height:1.15rem}
.btn.primary{background:var(--accent);border-color:var(--accent);color:var(--on-accent)}
@media (hover:hover){.btn.primary:hover{background:color-mix(in srgb,var(--accent) 88%,#000)}}
.btn.danger{color:var(--bad);border-color:color-mix(in srgb,var(--bad) 35%,var(--line))}
.btn.ghost{border-color:transparent;background:none}
.btn.small{min-height:2.25rem;padding:0 .75rem;font-size:.85rem}
.btn.wide{width:100%}
.btn:disabled{opacity:.45;cursor:default;transform:none}
.btn:active:not(:disabled),.icon-btn:active,.row:active,.tile:active{transform:scale(.98)}
.link{border:0;background:none;padding:0;color:var(--accent);font-weight:800;cursor:pointer;text-decoration:underline}
input:not([type]),input[type=search],input[type=text],input[type=email],input[type=password],input[type=url],input[type=date],input[type=number],textarea,select{width:100%;min-height:3rem;
padding:.6rem .9rem;border-radius:var(--r-sm);border:1px solid var(--line-strong);background:var(--surface);outline:2px solid transparent;outline-offset:1px;transition:border-color .15s}
input:focus,textarea:focus,select:focus{border-color:var(--accent);box-shadow:var(--focus)}
input[readonly]{background:var(--soft)}
textarea{resize:vertical;min-height:7rem;line-height:1.55}
label,.label{display:block;font-size:.88rem;font-weight:800;margin:0 0 .4rem}
.help{display:block;margin:.35rem 0 0;font-size:.82rem;color:var(--muted);font-weight:500}
.fieldset{margin:0 0 1rem}
.field{display:flex;gap:.5rem}
.field input{flex:1;min-width:0}
.field.stack{flex-direction:column;gap:.25rem}.field.stack>span{font-weight:600;font-size:.9rem}
.plain{list-style:none;margin:.25rem 0;padding:0;display:grid;gap:.4rem}.plain label{display:flex;gap:.5rem;align-items:baseline;flex-wrap:wrap}
.searchbar{display:flex;gap:.5rem;margin-bottom:.75rem;flex-wrap:wrap}
.searchbar input{flex:1 1 12rem;min-width:0}

/* ---- surfaces ---- */
.card{background:var(--surface);border:1px solid var(--line);border-radius:var(--r-lg);padding:1rem 1.1rem;margin:0 0 .75rem;box-shadow:var(--shadow)}
/* [ADMUX-05] One step per level: screen title 1.2, card 1.06, item .98, text .94. */
.card>h2,.card-head h2{font-size:1.06rem;font-weight:800}
.card-head{display:flex;align-items:center;justify-content:space-between;gap:.75rem;margin:0 0 .6rem}
/* 2.0.0 [RVQ-47] 검토 HQ-88·조건 12: 제목 + 알약 여럿인 머리는 840·글자 200%에서 제목이 한 글자 폭 기둥으로 서고 알약이 잘렸어요. 제목은 16rem(글자가
   크면 함께 커져요)을 지키고, 그만큼이 없으면 알약이 아래 줄로 감겨요. 알약도 줄 폭을 넘지 않게 감겨요. */
.card-head{flex-wrap:wrap;row-gap:.35rem}
.card-head:has(>:is(h2,h3)){justify-content:flex-start}
.card-head>:is(h2,h3){flex:1 1 16rem;min-width:0;overflow-wrap:anywhere}
.card-head>.pill{max-width:100%;white-space:normal}
/* 알약 여럿은 한 묶음으로 함께 옆에 있거나 함께 아래로 가요(하나만 아래로 떨어지지 않게). */
.head-pills{display:flex;flex-wrap:wrap;gap:.35rem;max-width:100%}.head-pills>.pill{max-width:100%;white-space:normal}
.card h3{font-size:.98rem}
.grid{display:grid;grid-template-columns:minmax(0,1fr);gap:.75rem}
.grid>h3.span{grid-column:1 / -1;margin:.75rem 0 0;display:flex;gap:.5rem;align-items:center;flex-wrap:wrap}
/* 2.0.0 [RVQ-07] 글자 200%에서 두 칸이 너무 좁아 글자가 세로로 섰어요(내 계정 › 로그인 목록, 840px): 칸마다 22rem은 있어야 둘로, 아니면 하나로. 글자가 크면 rem도 커져요. */
@media (min-width:600px){.grid.two{grid-template-columns:repeat(auto-fit,minmax(min(100%,max(22rem,calc(50% - .4rem))),1fr))}.grid.two>.card{margin:0}}
/* 2.0.0 [RVQ-47] 검토 HQ-88·조건 12: 두 칸(본문 · 옆 칸)은 칸이 줄 수 있는 폭(본문 18rem, 옆 16rem, 글자가 크면 함께 커져요)이 안 되면 위아래로 놓여요.
   격자(1.6fr · 1fr)일 때 글자 200%의 840·1440에서 본문 칸이 29px로 줄어 한 자씩 섰어요(개발사항 한 건). */
@media (min-width:1024px){.cols{display:flex;flex-wrap:wrap;gap:1rem;align-items:flex-start}.cols>*{min-width:0;flex:1 1 16rem}.cols>:first-child{flex:1.6 1 18rem}}
.head{display:flex;align-items:flex-start;justify-content:space-between;gap:.6rem;flex-wrap:wrap}
.head>:first-child{flex:1;min-width:0}
.dept{font-weight:500;color:var(--muted);font-size:.94rem}
.ver{font-size:.8rem;color:var(--muted);font-weight:800}
.meta{margin:.15rem 0 0;font-size:.82rem;color:var(--muted)}
.gist{margin:.6rem 0 0;font-size:.9rem}
.hint{margin:.1rem 0 .75rem;font-size:.88rem;color:var(--muted)}
.section{display:flex;align-items:center;gap:.5rem;font-size:.92rem;margin:1.5rem .25rem .6rem;color:var(--muted);font-weight:800}
.section .count{font-size:.75rem;background:var(--soft);padding:.05rem .5rem;border-radius:99px}
.cover-img { border-radius: 12px; object-fit: cover; }
.pill{flex:none;display:inline-flex;align-items:center;gap:.3rem;padding:.2rem .6rem;border-radius:999px;background:var(--soft);color:var(--muted);
font-size:.8rem;font-weight:800;white-space:nowrap;line-height:1.5}
.pill.warn{background:var(--warn-bg);color:var(--warn)}
.pill.ok{background:var(--ok-bg);color:var(--ok)}
.code-big{font-size:2.2rem;font-weight:700;letter-spacing:.12em;text-align:center;font-variant-numeric:tabular-nums;margin:.6rem 0}
.pill.bad{background:var(--bad-bg);color:var(--bad)}
.pill.info{background:var(--info-bg);color:var(--info)}
.pill.gold{background:var(--gold-soft);color:var(--gold)}
.score{display:flex;align-items:center;gap:.6rem;margin:.85rem 0 .25rem}
.score b{font-size:.85rem;white-space:nowrap}
meter{flex:1;width:100%;height:.6rem;-webkit-appearance:none;appearance:none;border:0;border-radius:99px;background:var(--soft);overflow:hidden}
meter::-webkit-meter-bar{height:.6rem;border:0;border-radius:99px;background:var(--soft)}
meter::-webkit-meter-optimum-value,meter::-webkit-meter-suboptimum-value,meter::-webkit-meter-even-less-good-value{background:var(--gold);border-radius:99px}
meter::-moz-meter-bar{background:var(--gold);border-radius:99px}
meter.strength::-webkit-meter-even-less-good-value{background:var(--bad)}
meter.strength::-webkit-meter-suboptimum-value{background:var(--gold)}
meter.strength::-webkit-meter-optimum-value{background:var(--ok)}
details{margin:.75rem 0 0;border-top:1px solid var(--line);padding-top:.5rem}
summary{cursor:pointer;font-size:.88rem;font-weight:800;color:var(--accent);padding:.35rem 0;list-style:none}
summary::-webkit-details-marker{display:none}
summary::after{content:" ▾"}
details[open]>summary::after{content:" ▴"}
.lines{margin:.4rem 0 0;display:grid;gap:.4rem}
/* 2.0.0 [RVA-04] 조건 12: 좁은 폭·큰 글자에서는 이름 줄 아래로 값이 내려가요(값이 한 글자씩 세로로 서지 않게). */
.lines div{display:flex;flex-wrap:wrap;column-gap:.6rem;row-gap:.1rem;font-size:.88rem}
.lines dt{color:var(--muted);flex:1 1 5.5rem}
.lines dd{margin:0;flex:999 1 11rem;min-width:0}
.diff{margin:.75rem 0 0;padding:.6rem .75rem;border-radius:var(--r-sm);background:var(--soft)}
.diff-title{margin:0 0 .4rem;font-size:.8rem;font-weight:800;color:var(--muted)}
.diff ul{margin:0;padding:0;list-style:none;display:grid;gap:.25rem}
.diff li{display:flex;gap:.4rem;font-size:.88rem}
.diff .sign{flex:none;width:1rem;text-align:center;font-weight:900}
.diff .add .sign{color:var(--ok)}
.diff .del{color:var(--muted)}
.diff .del .sign{color:var(--bad)}
blockquote{margin:.75rem 0 0;padding:.6rem .75rem;border-left:3px solid var(--gold);background:var(--soft);border-radius:4px var(--r-sm) var(--r-sm) 4px;
font-size:.9rem;white-space:pre-wrap}
cite{display:block;margin-top:.25rem;font-style:normal;font-size:.75rem;color:var(--muted)}
.note{margin:.75rem 0 0;font-size:.82rem;padding:.5rem .65rem;border-radius:var(--r-sm);background:var(--soft)}
.note.warn{background:var(--warn-bg);color:var(--warn)}
.notice-preview{margin:0 0 .75rem}.np{display:grid;gap:.2rem;padding:.65rem .8rem;border-radius:var(--r-sm);background:var(--info-bg);color:var(--fg);font-size:.88rem}.np span{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;color:var(--muted)}.np.important{background:var(--warn-bg)}.np.important b{color:var(--warn)}
.note.bad{background:var(--bad-bg);color:var(--bad)}
.note.ok{background:var(--ok-bg);color:var(--ok)}
.note.info{background:var(--info-bg);color:var(--info)}
.actions{display:flex;justify-content:flex-end;gap:.5rem;margin-top:.9rem;flex-wrap:wrap}
.actions.start{justify-content:flex-start}
#admins-top{margin:0 0 .75rem}
details.card{margin-top:0;padding-top:.6rem}
details.card>summary{color:var(--fg)}
details.card .steps{margin-top:.6rem}

/* ---- lists ---- */
.rows{list-style:none;margin:0 0 .75rem;padding:0;background:var(--surface);border:1px solid var(--line);border-radius:var(--r-lg);overflow:hidden;box-shadow:var(--shadow)}
.rows>li+li{border-top:1px solid var(--line)}
.row{display:flex;align-items:center;gap:.65rem;width:100%;min-height:4rem;padding:.75rem 1rem;border:0;background:none;text-align:left;cursor:pointer;color:inherit;text-decoration:none}
@media (hover:hover){.row:hover{background:var(--hover)}}
.row>svg{flex:none;width:1.25rem;height:1.25rem;color:var(--faint)}
.row-main{flex:1;min-width:0;display:flex;flex-direction:column;gap:.05rem}
.row-main b{font-size:1rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.row-main b.wrap{white-space:normal;overflow-wrap:anywhere}
.row-main small{font-size:.8rem;color:var(--muted)}
.row-main .preview{font-size:.9rem;color:var(--fg);display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.row.unread .row-main b::before{content:"";display:inline-block;width:.5rem;height:.5rem;border-radius:50%;background:var(--accent);margin-right:.4rem;vertical-align:middle}
.row-side{display:flex;flex-direction:column;align-items:flex-end;gap:.3rem;flex:none;font-size:.75rem;color:var(--muted)}
.item{display:flex;align-items:center;gap:.75rem;padding:.85rem 1rem;flex-wrap:wrap}
.item .row-main{flex:1 1 14rem}
.item .actions{margin:0 0 0 auto}
.memo{font-style:italic}
.row-main small.judge{color:var(--fg)}
.row-main small.judge.warn{color:var(--warn)}
.item label.row{display:flex;gap:.6rem;align-items:flex-start;flex:1;min-width:0}
.chips{display:flex;flex-wrap:wrap;gap:.5rem;margin:0 0 .75rem}
.chip{display:inline-flex;align-items:center;gap:.15rem;padding:.25rem .25rem .25rem .75rem;border-radius:999px;background:var(--soft);font-size:.88rem;font-weight:700}
.chip button{width:1.9rem;height:1.9rem;border:0;border-radius:50%;background:none;cursor:pointer;font-size:1.1rem;line-height:1;color:var(--muted)}
.chip.pick{padding:.35rem .8rem;border:1px solid var(--line-strong);background:var(--surface);cursor:pointer}
@media (hover:hover){.chip.pick:hover{background:var(--hover)}}
.chip svg{width:1rem;height:1rem}
.chip.pick[aria-pressed=true]{background:var(--accent-soft);border-color:var(--accent);color:var(--accent)}

/* ---- home ---- */
.hello{margin:.25rem .25rem 1rem}
.hello h2{font-size:1.35rem;font-weight:900}
.hello p{margin:.2rem 0 0;color:var(--muted);font-size:.92rem}
.tiles{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,10.5rem),1fr));gap:.65rem;margin:0 0 1rem}
.tile{display:flex;flex-direction:column;gap:.35rem;padding:.9rem 1rem;border-radius:var(--r-lg);background:var(--surface);border:1px solid var(--line);
box-shadow:var(--shadow);text-decoration:none;color:inherit;min-height:6.5rem;transition:border-color .15s,transform .1s}
@media (hover:hover){a.tile:hover{border-color:var(--accent)}}
.tile .k{font-size:.8rem;font-weight:800;color:var(--muted);display:flex;align-items:center;gap:.4rem}
.tile .k svg{width:1.05rem;height:1.05rem}
.tile .v{font-size:1.9rem;font-weight:900;line-height:1.1;letter-spacing:-.02em;overflow-wrap:normal;word-break:keep-all}
/* 숫자와 단위는 한 낱말로 붙여 두고(낱말 가운데서 끊지 않음, '살아 있음 · 판 다름'처럼 띄어 쓴 글은 띄어쓰기에서 줄을 바꿔요), 칸이 좁으면 한 단계 작게(칸 폭 기준, rem이라 글자가 크면 같이 커져요). */
.tile{container-type:inline-size}@container (max-width:9.5rem){.tile .v{font-size:1.45rem}}@container (max-width:7rem){.tile .v{font-size:1.15rem}}
.tile .d{font-size:.78rem;color:var(--muted);margin-top:auto}
@media (max-width:599.98px){.tile{min-height:0;padding:.7rem .85rem;gap:.15rem}.tile .v{font-size:1.5rem}.tile .d{display:none}}
/* The phone lists what waits; wider screens show the counts as tiles, with the activity beside. [ADMUX-06] The
   queue of what to answer first shows on both. */
.stats{display:flex;flex-wrap:wrap;gap:.4rem 1.25rem;margin:0 .25rem 1rem;font-size:.86rem;color:var(--muted)}
.stats b{color:var(--fg);font-size:1rem;margin-left:.3rem}
.tile.bad{border-color:color-mix(in srgb,var(--bad) 40%,var(--line))}
.tile.bad .v{color:var(--bad)}
.todo{list-style:none;margin:0;padding:0;display:grid;gap:.15rem}
.todo a{display:flex;align-items:center;gap:.65rem;padding:.6rem .5rem;border-radius:var(--r-sm);text-decoration:none;color:inherit;font-weight:700;font-size:.94rem}
@media (hover:hover){.todo a:hover{background:var(--hover)}}
.todo .dot{flex:none;width:.55rem;height:.55rem;border-radius:50%;background:var(--accent)}
.todo .dot.bad{background:var(--bad)}
.todo .dot.warn{background:var(--gold)}
.todo small{margin-left:auto;color:var(--muted);font-weight:600;white-space:nowrap}
.feed{list-style:none;margin:0;padding:0}
.feed li{display:grid;grid-template-columns:auto 1fr;gap:.15rem .65rem;padding:.55rem 0;border-top:1px solid var(--line);font-size:.88rem}
.feed li:first-child{border-top:0}
.feed .when{grid-row:span 2;color:var(--muted);font-size:.75rem;min-width:3.4rem;padding-top:.1rem}
.feed .who{color:var(--muted);font-size:.78rem}
/* 2.0.0 [RVA-61] 총괄 Q1 사진(380·글자 200%, 조건 12): 칸이 좁으면 시각을 글 위로(시각 칸이 폭을 먹어 글이 네댓 글자씩 줄바꿈되던 것). */
.feed{container:feed/inline-size}
@container feed (width < 22rem){.feed li{grid-template-columns:1fr;gap:.1rem}.feed .when{grid-row:auto;min-width:0;padding-top:0}}
.day-head{margin:1rem .25rem .4rem;font-size:.8rem;font-weight:800;color:var(--muted)}

/* ---- conversation ---- */
.thread{display:flex;flex-direction:column;gap:.6rem;margin:.25rem 0 .75rem}
.bubble{max-width:min(85%,36rem);padding:.65rem .85rem;border-radius:1.1rem;white-space:pre-wrap;font-size:.94rem;line-height:1.55}
.bubble.user{align-self:flex-start;background:var(--soft);border-bottom-left-radius:.35rem}
.bubble.admin{align-self:flex-end;background:var(--accent);color:var(--on-accent);border-bottom-right-radius:.35rem}
.bubble.admin.held{opacity:.7;outline:2px dashed var(--accent);outline-offset:2px}
.bubble-meta{font-size:.75rem;color:var(--muted);margin:-.3rem .35rem 0}
.bubble-meta.admin{align-self:flex-end}
.composer{position:sticky;bottom:calc(4.4rem + env(safe-area-inset-bottom));background:var(--surface);border:1px solid var(--line);border-radius:var(--r-lg);
padding:.75rem;box-shadow:var(--shadow)}
@media (min-width:600px){.composer{bottom:1rem}}
/* [J133-40] 순번 720 (1.32 실사용 테스트, 관리자 휴대폰 화면): on a phone the reply box stays in the page, under the talk, instead of
   floating over the last bubbles; the actions under it are never behind the bottom tabs. */
@media (max-width:599px){.composer{position:static;margin-bottom:1rem}
  /* 1.38 [ADM3-36] PC 13차 (접은 폴드): 답하기 stays above the bottom tabs while the answer is written, however long it is. */
  .composer .actions{position:sticky;bottom:calc(4.4rem + env(safe-area-inset-bottom));z-index:2;background:var(--surface);
    margin:.5rem -.75rem -.75rem;padding:.6rem .75rem;border-top:1px solid var(--line);border-radius:0 0 var(--r-lg) var(--r-lg)}}
.composer .count-box{margin-right:auto;display:grid;gap:.1rem;min-width:0}
.composer .saved{font-size:.7rem;color:var(--muted);white-space:nowrap}
.composer textarea{min-height:5.5rem;border:0;padding:.25rem;box-shadow:none;background:none}
.composer .actions{margin-top:.5rem;align-items:center}
.composer .count{margin-right:auto;font-size:.75rem;color:var(--muted)}

/* ---- permissions ---- */
.perm-table{display:grid;gap:.4rem;margin:.25rem 0 .75rem}
.perm{display:flex;align-items:center;justify-content:space-between;gap:.75rem;flex-wrap:wrap;padding:.5rem .1rem;border-top:1px solid var(--line)}
.perm:first-child{border-top:0}
.perm b{font-size:.92rem}
.levels{display:inline-flex;padding:.2rem;border-radius:var(--r-sm);background:var(--soft);gap:.15rem}
.levels button{min-height:2.25rem;min-width:3.6rem;padding:0 .6rem;border:0;border-radius:.6rem;background:none;font-weight:800;font-size:.82rem;color:var(--muted);cursor:pointer}
.levels button[aria-pressed=true]{background:var(--surface);color:var(--fg);box-shadow:var(--shadow)}
.levels button[aria-pressed=true][data-level=edit]{color:var(--accent)}
.levels button:disabled{opacity:.35;cursor:default}
.perm-sum{display:flex;flex-wrap:wrap;gap:.3rem;margin:.35rem 0 0}
.steps{list-style:none;margin:0 0 .5rem;padding:0;display:grid;gap:.6rem;counter-reset:s}
.steps li{display:grid;grid-template-columns:1.8rem 1fr;gap:.6rem;font-size:.9rem;counter-increment:s}
.steps li::before{content:counter(s);display:inline-grid;place-items:center;width:1.8rem;height:1.8rem;border-radius:50%;background:var(--accent-soft);color:var(--accent);font-weight:900;font-size:.85rem}
.steps li.done::before{content:"✓";background:var(--ok-bg);color:var(--ok)}

/* ---- toggles, segments, steppers ---- */
.switch{position:relative;display:flex;align-items:center;gap:.75rem;margin:.75rem 0 0;font-size:.94rem;font-weight:700;cursor:pointer}
.switch input{position:absolute;opacity:0;width:1px;height:1px}
.track{position:relative;flex:none;width:3.1rem;height:1.85rem;border-radius:99px;background:var(--line-strong);transition:background .2s}
.track::after{content:"";position:absolute;top:.2rem;left:.2rem;width:1.45rem;height:1.45rem;border-radius:50%;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.25);transition:transform .2s}
.switch input:checked+.track{background:var(--accent)}
.switch input:checked+.track::after{transform:translateX(1.25rem)}
.switch input:focus-visible+.track{box-shadow:var(--focus)}
.rule{padding:.75rem 0;border-top:1px solid var(--line)}
.rule:first-of-type{border-top:0;padding-top:.25rem}
.rule.dim{opacity:.72}
.segmented{display:flex;gap:.25rem;padding:.25rem;border-radius:var(--r);background:var(--soft);margin-bottom:.75rem;overflow-x:auto;scrollbar-width:none}
.seg{flex:1 0 auto;min-height:2.6rem;padding:0 .8rem;border:0;border-radius:.75rem;background:none;font-weight:800;font-size:.86rem;color:var(--muted);cursor:pointer;white-space:nowrap}
.seg[aria-pressed=true]{background:var(--surface);color:var(--fg);box-shadow:var(--shadow)}
.seg .n{margin-left:.3rem;font-size:.75rem;color:var(--faint)}
.stepper{display:flex;align-items:center;justify-content:space-between;gap:.75rem;margin:.6rem 0 .1rem;flex-wrap:wrap}
.stepper>span{font-weight:800}
.stepper-ctl{display:flex;align-items:center;gap:.1rem;background:var(--soft);border-radius:var(--r);padding:.1rem}
.stepper output{min-width:4.2rem;text-align:center;font-weight:900;font-size:1.05rem}
.checks{list-style:none;margin:0 0 .4rem;padding:0;display:grid;gap:.6rem}
.checks li{display:flex;gap:.6rem;font-size:.88rem;align-items:flex-start}
.checks span{flex:none;display:inline-grid;place-items:center;width:1.4rem;height:1.4rem;border-radius:50%;font-size:.8rem;font-weight:900}
.checks .ok span{background:var(--ok-bg);color:var(--ok)}
.checks .todo span{background:var(--warn-bg);color:var(--warn)}
.checks .off span{background:var(--soft);color:var(--muted)}
.checks .bad span{background:var(--bad-bg);color:var(--bad)}
.checks .bad{color:var(--bad);font-weight:700}
/* 2.0.0 [RVQ-14] 총괄 사진 검토(840·글자 200%): 넘치는 칸은 통째로 다음 줄로(좁은 칸에 밀려 두세 글자씩 서지 않게). */
.checks-row{display:flex;flex-wrap:wrap;gap:.5rem 1rem;margin:0 0 .9rem}.checks-row li{flex:0 0 auto;max-width:100%}
.server-sum{overflow-wrap:anywhere}
@media (max-width:480px){.checks-row{grid-auto-flow:row}}
.card h3.sub{margin:.9rem 0 .5rem}
.kv{display:grid;grid-template-columns:auto 1fr;gap:.35rem .9rem;margin:0;font-size:.88rem}
.kv dt{color:var(--muted)}
.kv dd{margin:0;font-weight:600}

/* ---- states ---- */
.empty{text-align:center;padding:3rem 1rem;color:var(--muted)}
.empty svg{width:3.25rem;height:3.25rem;color:var(--gold);stroke-width:1.5}
.empty h2{color:var(--fg);font-size:1.06rem;margin:.75rem 0 .25rem}
.empty p{margin:0 auto;font-size:.88rem;max-width:26rem}
.empty .btn{margin-top:1rem}
.skeleton{height:9rem;border-color:transparent;background:linear-gradient(90deg,var(--surface),var(--soft),var(--surface));background-size:200% 100%;animation:shine 1.2s ease-in-out infinite}
.skeleton.short{height:4.5rem}
@keyframes shine{from{background-position:100% 0}to{background-position:-100% 0}}
.center{min-height:100vh;min-height:100dvh;display:grid;place-items:center}
.spinner{width:2rem;height:2rem;border-radius:50%;border:3px solid var(--line);border-top-color:var(--gold);animation:spin .8s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}
.offline{position:sticky;top:0;z-index:7;background:var(--warn-bg);color:var(--warn);text-align:center;font-size:.85rem;font-weight:800;padding:.4rem 1rem}

/* ---- sign-in, invite and install pages ---- */
.gate{max-width:28rem;margin:0 auto;padding:calc(2.5rem + env(safe-area-inset-top)) 1rem 2rem}
/* 2.0.0 [RVA-14] 검토 T24-12: 로그인과 패스키 단추 사이를 띄워요 */
.gate form .btn.wide+.btn.wide{margin-top:.6rem}
.brand{text-align:center;margin-bottom:1.5rem}
.brand img{border-radius:1.35rem;box-shadow:var(--shadow)}
.brand h1{font-size:1.5rem;margin:.85rem 0 .25rem}
.brand p{margin:0;color:var(--muted)}
.error{min-height:1.4em;margin:.5rem 0;color:var(--bad);font-size:.88rem;font-weight:600}
.alt{text-align:center;margin:1rem 0 0;font-size:.88rem;color:var(--muted)}
/* 2.0.0 [RVA-16] 조건 12: 알림은 글 길이만큼 넓어져요(left:50%에 갇혀 큰 글자에서 좁은 기둥이 되지 않게). */
.toast{position:fixed;left:50%;bottom:calc(5.25rem + env(safe-area-inset-bottom));transform:translate(-50%,1rem);width:max-content;max-width:min(92vw,32rem);padding:.75rem 1.1rem;
border-radius:var(--r);background:var(--fg);color:var(--bg);font-size:.88rem;font-weight:700;opacity:0;pointer-events:none;transition:opacity .2s,transform .2s;
z-index:60;text-align:center;box-shadow:var(--shadow-lg)}
.toast.show{opacity:1;transform:translate(-50%,0)}
dialog{border:0;padding:0;border-radius:var(--r-lg);background:var(--raised);color:var(--fg);width:min(94vw,30rem);max-height:min(92vh,48rem);box-shadow:var(--shadow-lg)}
dialog.wide{width:min(94vw,40rem)}
dialog::backdrop{background:rgba(8,10,20,.55);backdrop-filter:blur(2px)}
dialog[open]{animation:pop .18s ease-out}
@keyframes pop{from{opacity:0;transform:translateY(.6rem) scale(.98)}to{opacity:1;transform:none}}
dialog form,dialog .box,dialog .key-once{padding:1.35rem 1.25rem 1.1rem}
/* 2.0.0 [RVA-04] 확인 창의 누구에게·무엇을·며칠·보일 글, [RVA-02] 입력 창의 미리 보기, [RVA-08] 닫기 전 경고 */
.confirm-rows{margin:.1rem 0 .4rem;padding:.7rem .8rem;border-radius:var(--r-sm);background:var(--soft)}
.confirm-rows dd{font-weight:700;white-space:pre-wrap;overflow-wrap:anywhere}
/* 2.0.0 [RVA-02] 조건 창 안에서 펴는 한 줄 칸(앱 판 번호): 칸과 단추가 한 줄, 좁으면 단추가 아래로. */
.inline-ask{margin-top:.6rem}
.inline-ask .row-input{display:flex;flex-wrap:wrap;align-items:center;gap:.5rem}
.inline-ask .row-input input{flex:1 1 8rem;min-width:0}
.ask-preview{margin:0 0 .25rem;padding:.75rem;border-radius:var(--r-sm);background:var(--soft);max-height:16rem;overflow:auto}
.ask-preview .thread{margin:0}
dialog .secret-warn{margin:.75rem 0 0}
dialog .error:empty{display:none}
/* 2.0.0 [RVA-05] 틀린 칸: 빨간 테와 그 아래 이유 한 줄 */
[aria-invalid=true]{border-color:var(--bad)!important;box-shadow:0 0 0 1px var(--bad)}
.field-error{display:block;margin:.3rem 0 0;color:var(--bad);font-size:.84rem;font-weight:700}
dialog h2{font-size:1.15rem;margin-bottom:.35rem}
dialog p{margin:0 0 .75rem;color:var(--muted);font-size:.88rem}
dialog .actions:last-child{position:sticky;bottom:0;margin:1rem -1.25rem -1.1rem;padding:.75rem 1.25rem calc(.9rem + env(safe-area-inset-bottom));
background:var(--raised);border-top:1px solid var(--line)}
/* 2.0.0 [RVQ-29] 아래에 붙은 단추 줄이 창 맨 아래를 덮어요: 틀린 칸으로 굴릴 때(초점·이유) 그 칸과 이유가 단추 줄 위에 오게. */
dialog{scroll-padding-bottom:calc(5.5rem + env(safe-area-inset-bottom))}
@media (max-width:599.98px){dialog{margin:auto 0 0;width:100vw;max-width:100vw;border-radius:var(--r-lg) var(--r-lg) 0 0;padding-bottom:env(safe-area-inset-bottom)}
@keyframes pop{from{transform:translateY(100%)}to{transform:none}}
/* AT 1.29: a wide box (복사, 개발사항에 추가) is a whole-width sheet on a phone too; dialog.wide's 94vw left a gap at the side. */
dialog.wide{width:100vw}}
.radios{display:grid;gap:.5rem;margin:.4rem 0}
.radio{display:flex;align-items:center;gap:.6rem;margin:0;padding:.75rem;border:1px solid var(--line-strong);border-radius:var(--r-sm);font-weight:600;cursor:pointer;font-size:.92rem}
.radio input{flex:none;width:1.25rem;height:1.25rem;accent-color:var(--accent)}
.radio .radio-sub{display:block;margin-top:.1rem;font-weight:400;font-size:.8rem;color:var(--muted)}
.radio:has(input:checked){border-color:var(--accent);background:var(--accent-soft)}
/* ---- 개발사항 [ADMIN-10] ---- */
.row .check{flex:none;width:1.35rem;height:1.35rem;margin:0;accent-color:var(--accent)}
.row.picked{background:var(--accent-soft)}
.bulkbar{position:sticky;bottom:calc(4.6rem + env(safe-area-inset-bottom));z-index:4;display:flex;align-items:center;gap:.5rem;flex-wrap:wrap;
margin:.75rem 0 0;padding:.6rem .75rem;background:var(--raised);border:1px solid var(--line-strong);border-radius:var(--r-lg);box-shadow:var(--shadow-lg)}
.bulkbar b{margin-right:auto;font-size:.9rem}
.toolbar.end{justify-content:flex-end}
.card.picked{outline:2px solid var(--accent)}
.versions{display:grid;gap:.75rem;grid-template-columns:repeat(auto-fill,minmax(min(100%,17rem),1fr))}
.version{border:1px solid var(--line);border-radius:12px;padding:.75rem;min-width:0}
.version.best{border-color:var(--gold)}
.version p{margin:0 0 .4rem}
.rows.inbox .row-side{flex-wrap:wrap;justify-content:flex-end}
.rows.inbox .row-main a{color:inherit;text-decoration:none}
@media (hover:hover){.rows.inbox .row-main a:hover{text-decoration:underline}}
@media (min-width:600px){.bulkbar{bottom:1rem}.rows.inbox .row-side{flex-direction:row;align-items:center}}
/* ---- AT 1.29 문의 복사 [ADMCP-01]~[ADMCP-05]: a copy button beside a row's link, the strip of new reports, the copy box ---- */
.rows>li.has-tool{display:flex;align-items:center}
.rows>li.has-tool>.row{flex:1;min-width:0}
.row-tool{flex:none;display:inline-grid;place-items:center;width:2.75rem;height:2.75rem;margin:0 .4rem 0 -.4rem;border:0;border-radius:var(--r-sm);background:none;color:var(--muted);cursor:pointer}
.row-tool:focus-visible{background:var(--hover);color:var(--fg)}@media (hover:hover){.row-tool:hover{background:var(--hover);color:var(--fg)}}
.row-tool svg{width:1.15rem;height:1.15rem}
.fresh{display:flex;flex-wrap:wrap;align-items:center;gap:.5rem;margin:0 0 .75rem;padding:.6rem .8rem;border-radius:var(--r-sm);background:var(--info-bg);font-size:.9rem}
.fresh span{flex:1 1 12rem;min-width:0}
.copy-text{display:block;width:100%;min-height:14rem;max-height:45vh;margin:.5rem 0;font:inherit;font-size:.82rem;line-height:1.5;white-space:pre-wrap;overflow-wrap:anywhere;resize:vertical}
.copybox .after{margin:.75rem 0 0;padding:.6rem .75rem;border:1px solid var(--line);border-radius:var(--r-sm)}
.copybox .after legend{padding:0 .3rem;font-size:.85rem;font-weight:800}
/* On a phone the bar keeps to 복사 and 다른 일, so it does not cover the list it picks from; wider, everything shows. */
.bulkbar .more-acts{display:contents}
@media (max-width:599.98px){.bulkbar:not(.open) .more-acts{display:none}}
@media (min-width:600px){.bulkbar .more-toggle{display:none}}
.dev-body{margin:.6rem 0 0;white-space:pre-wrap;font-size:.94rem}
.history{list-style:none;margin:0;padding:0}
.history li{padding:.6rem 0;border-top:1px solid var(--line);font-size:.88rem}
.history li:first-child{border-top:0}
.history .who{font-weight:800}
.history .when{color:var(--muted);font-size:.75rem;margin-left:.35rem}
.history .what{display:block;margin-top:.1rem}
.history .was{color:var(--muted);text-decoration:line-through}
.notes{list-style:none;margin:0 0 .5rem;padding:0;display:grid;gap:.5rem}
.notes li{padding:.5rem .65rem;border-radius:var(--r-sm);background:var(--gold-soft);font-size:.88rem;white-space:pre-wrap}
.notes cite{margin:0 0 .15rem}
.mini textarea{min-height:3.5rem}
.item a.row-main{color:inherit;text-decoration:none}
@media (hover:hover){.item a.row-main:hover b{text-decoration:underline}}
.kbd{display:inline-block;min-width:1.6rem;padding:.05rem .4rem;border:1px solid var(--line-strong);border-bottom-width:2px;border-radius:.4rem;font-size:.8rem;
font-weight:800;text-align:center;background:var(--surface)}
.keys{display:grid;grid-template-columns:auto 1fr;gap:.5rem .9rem;align-items:center;font-size:.9rem;margin:0 0 .5rem}
/* ---- 1.23 [ADMUX-01]~[ADMUX-08] ---- */
/* Touch: on a finger, every small control is still 44 px. */
@media (pointer:coarse){.btn.small,.seg,.levels button{min-height:2.75rem}.chip button{width:2.75rem;height:2.75rem}.chip.pick{min-height:2.75rem}.icon-btn{width:2.75rem;height:2.75rem}}
.btn[aria-busy=true]{opacity:.7;cursor:progress}
.row.static{cursor:default}@media (hover:hover){.row.static:hover{background:none}}.row.static:active{transform:none}
.rows .row.selected,.rows .item.selected{box-shadow:inset 3px 0 0 var(--accent);background:var(--hover)}
.row-icons{display:flex;flex-wrap:wrap;gap:.3rem;margin-top:.3rem}
.row-icons .pill{font-size:.8rem}
.more{display:flex;justify-content:center;margin:0 0 1rem}
.toolbar{display:flex;gap:.5rem;flex-wrap:wrap;align-items:center;margin:0 0 .75rem}
.toolbar input[type=search]{flex:1 1 12rem;min-width:0}
.toolbar select,.toolbar input[type=date]{width:auto;min-height:2.75rem;flex:0 1 auto}
select{appearance:auto}
.banner{position:sticky;top:0;z-index:7;display:flex;align-items:center;justify-content:center;gap:.75rem;flex-wrap:wrap;padding:.45rem 1rem;
background:var(--info-bg);color:var(--info);font-size:.86rem;font-weight:800;text-align:center}
.banner.warn{background:var(--warn-bg);color:var(--warn)}
.banner .btn{min-height:2.25rem}
.failed{text-align:center;padding:2.5rem 1rem;color:var(--muted)}
.failed h2{color:var(--fg);font-size:1.06rem;margin:.5rem 0 .25rem}
/* 2.0.0 [RVA-10] 검토 ADMX-19: 실패 화면 그림은 빈 화면보다 작고 차분하게 */
.empty.failed svg{width:2.25rem;height:2.25rem;color:var(--muted)}
.empty .actions.mid{justify-content:center;margin-top:1rem}.empty .actions.mid .btn{margin-top:0}
.toast .undo{margin-left:.75rem;border:0;border-radius:var(--r);padding:.45rem .9rem;background:var(--accent-soft);color:var(--fg);font-size:.95rem;font-weight:900;cursor:pointer;pointer-events:auto;min-height:2.75rem}
.toast.has-undo{font-size:.95rem;padding:.6rem .8rem .6rem 1.1rem;display:flex;align-items:center;gap:.25rem}
.toast.show.has-undo{pointer-events:auto}
.toast.has-undo.mini{left:auto;right:max(1rem,env(safe-area-inset-right));transform:none;padding:.3rem;box-shadow:var(--shadow-lg)}
.toast.has-undo.mini>span{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}
.toast.has-undo.mini .undo{margin-left:0}
/* Two panes: on a wide screen the list stays beside the open item. */
@media (min-width:1024px){.split{display:grid;grid-template-columns:clamp(15rem,30%,24rem) minmax(0,1fr);gap:1rem;align-items:start}
.split>.pane-list{position:sticky;top:calc(var(--top-h,4.75rem) + .5rem);max-height:calc(100vh - var(--top-h,4.75rem) - 1.25rem);max-height:calc(100dvh - var(--top-h,4.75rem) - 1.25rem);overflow:auto;border-radius:var(--r-lg)}
.split>.pane-list .rows{margin:0}.split .row[aria-current=true]{background:var(--accent-soft)}}
@media (max-width:1023.98px){.split>.pane-list{display:none}}
@media (min-width:1024px) and (max-width:1439.98px){.split .cols{display:block}}
/* Charts: drawn as SVG, coloured here (the CSP allows no style attributes). */
.chart{display:block;width:100%;height:auto;margin:.5rem 0}
.chart .bar{fill:var(--accent);stroke:none}
@media (hover:hover){.chart .bar:hover{fill:color-mix(in srgb,var(--accent) 75%,var(--fg))}}
.chart .grid{stroke:var(--line);stroke-width:1}
.chart .axis{fill:var(--muted);stroke:none;font-size:11px;font-family:inherit}
.chart .before{stroke:var(--gold);stroke-width:2;stroke-dasharray:5 4}
.legend{display:flex;gap:1rem;flex-wrap:wrap;font-size:.8rem;color:var(--muted)}
.legend .sw{display:inline-block;width:.9rem;height:.5rem;border-radius:2px;background:var(--accent);margin-right:.35rem;vertical-align:middle}
.legend .sw.before{background:none;border-top:2px dashed var(--gold);height:0}
ins{background:var(--ok-bg);color:var(--ok);text-decoration:none;border-radius:3px}
del{background:var(--bad-bg);color:var(--bad);border-radius:3px}
.wdiff{white-space:pre-wrap;overflow-wrap:anywhere}
.diffbox{white-space:pre-wrap;font-size:.9rem;padding:.6rem .75rem;background:var(--soft);border-radius:var(--r-sm);margin:.5rem 0 0}
/* The home queue and the dashboard numbers. */
.queue-list{list-style:none;margin:0;padding:0;display:grid;grid-template-columns:minmax(0,1fr);gap:.1rem}
.queue-list li{display:flex;align-items:center;gap:.6rem;padding:.55rem .25rem;border-top:1px solid var(--line);flex-wrap:wrap}
.queue-list li:first-child{border-top:0}
.queue-list .q-main{flex:1 1 12rem;min-width:0}
.queue-list .q-main b{display:block;font-size:.94rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.queue-list .q-main a{color:inherit;text-decoration:none}@media (hover:hover){.queue-list .q-main a:hover{text-decoration:underline}}
.queue-list .q-main small{color:var(--muted);font-size:.8rem}
.delta{font-size:.8rem;font-weight:800;margin-left:.35rem}
.delta.up{color:var(--warn)}.delta.down{color:var(--ok)}
.people{list-style:none;margin:0;padding:0;display:flex;flex-wrap:wrap;gap:.4rem}
.people li{padding:.3rem .7rem;border-radius:99px;background:var(--soft);font-size:.86rem;font-weight:700}
/* The timeline of a question. */
.timeline{list-style:none;margin:0;padding:0 0 0 .9rem;border-left:2px solid var(--line)}
.timeline li{position:relative;padding:.35rem 0 .35rem .6rem;font-size:.86rem}
.timeline li::before{content:"";position:absolute;left:-1.28rem;top:.75rem;width:.6rem;height:.6rem;border-radius:50%;background:var(--line-strong)}
.timeline li.user::before{background:var(--gold)}.timeline li.admin::before{background:var(--accent)}.timeline li.note::before{background:var(--warn)}
.timeline .when{color:var(--muted);font-size:.75rem;margin-left:.35rem}
.typing{font-size:.82rem;color:var(--accent);font-weight:700;margin:.25rem 0}
.typing::before{content:"✎ "}
.stepnav{display:flex;gap:.5rem;justify-content:space-between;align-items:center;margin:0 0 .75rem}
.queue-bar{position:sticky;top:var(--top-h,4rem);z-index:4;display:flex;align-items:center;gap:.5rem;flex-wrap:wrap;padding:.5rem .75rem;margin:0 0 .75rem;
background:var(--accent-soft);color:var(--accent);border-radius:var(--r);font-weight:800;font-size:.88rem}
.queue-bar b{margin-right:auto}
/* 2.0.0 [RVA-31] 검토 U23-08: 휴대폰에서는 위에 붙은 막대가 머리 아래로 가려지고 손이 닿지 않았어요. 아래 탭 바로 위에 한 손 막대로 두고,
   키보드 안내는 빼요. 답하기 줄과 마지막 글은 그 높이만큼 올라가요. */
@media (max-width:599.98px){
  .queue-bar{position:fixed;top:auto;left:.5rem;right:.5rem;bottom:calc(4.4rem + env(safe-area-inset-bottom));z-index:6;margin:0;flex-wrap:nowrap;
    min-height:3.25rem;padding:.4rem .5rem .4rem .85rem;background:var(--surface);border:1px solid var(--accent);box-shadow:var(--shadow-lg)}
  .queue-bar>span{display:none}
  .queue-bar .btn{min-height:2.75rem}
  body:has(.queue-bar) .composer .actions{bottom:calc(4.4rem + 3.75rem + env(safe-area-inset-bottom))}
  body:has(.queue-bar) .main{padding-bottom:calc(10.5rem + env(safe-area-inset-bottom))}
}
/* The command palette. */
dialog.palette{margin-top:12vh;width:min(94vw,38rem)}
@media (max-width:599.98px){dialog.palette{margin:0;width:100vw;max-width:100vw;max-height:100dvh;height:100dvh;border-radius:0}}
.palette .box{padding:.75rem}
.palette input{font-size:1.05rem}
.palette .rows{margin:.5rem 0 0;max-height:60vh;overflow:auto}
.palette .row{min-height:3rem}
.palette .row[aria-selected=true]{background:var(--accent-soft)}
.palette small.kind{color:var(--muted);font-size:.75rem;margin-left:auto;flex:none}
.pw{position:relative}.pw input{padding-right:5.5rem}
.pw .show{position:absolute;right:.35rem;top:50%;transform:translateY(-50%);min-height:2.25rem}
.saved{font-size:.75rem;color:var(--muted)}
.prefs .field select{flex:1}
.check-row{display:flex;align-items:center;gap:.5rem;margin:.4rem 0;font-weight:600;font-size:.92rem}
.check-row input{flex:none;width:1.2rem;height:1.2rem;accent-color:var(--accent)}
.who.link{background:none;border:0;padding:0;font:inherit;color:var(--accent);cursor:pointer;text-align:left;text-decoration:underline;text-underline-offset:2px}
.btn.disabled{opacity:.45;pointer-events:none}
.pane-main{min-width:0}
.toolbar .btn{flex:none}
/* AM 1.28 기능 관리 [ADMUX-21]~[ADMUX-27] */
.lead{margin:.2rem 0 .5rem;font-size:1rem;font-weight:600;line-height:1.5}
.pills{display:flex;flex-wrap:wrap;gap:.3rem;align-items:center;justify-content:flex-end}
.row-side.pills{flex-direction:row;max-width:45%}
@media (max-width:599.98px){.row-side.pills{max-width:55%}}
.checklist{list-style:none;margin:0;padding:0;display:grid;gap:.55rem}
.checklist li{display:flex;gap:.6rem;align-items:flex-start;flex-wrap:wrap;font-size:.92rem}
.checklist .mark{flex:none;width:1.6rem;height:1.6rem;border-radius:50%;display:inline-grid;place-items:center;font-weight:800;background:var(--soft);color:var(--muted)}
.checklist .met .mark{background:var(--ok-bg);color:var(--ok)}
.checklist .unmet .mark{background:var(--warn-bg);color:var(--warn)}
.checklist .phone .mark{background:var(--info-bg);color:var(--info)}
.checklist .c-main{flex:1 1 12rem;min-width:0;overflow-wrap:anywhere}
.checklist small{display:block;color:var(--muted);font-size:.8rem}
.card.flag-alert{border-color:var(--bad);background:var(--bad-bg)}
/* 1.39 PC 7차 1-8: 접힌 한 장은 한 줄 단추처럼(기본 details 위 줄·▾ 줄바꿈 없이), 줄 전체를 누르면 펼쳐져요. */
.card.flag-alert details{margin:0;border:0;padding:0}
.card.flag-alert summary{cursor:pointer;list-style:none;display:flex;flex-wrap:nowrap;align-items:center;gap:.5rem;min-height:44px;margin:0;-webkit-tap-highlight-color:transparent}
.card.flag-alert summary::-webkit-details-marker{display:none}.card.flag-alert summary::after,.card.flag-alert details[open]>summary::after{content:none}
.card.flag-alert summary h2{flex:1;min-width:0;margin:0;font-size:1rem}
.card.flag-alert summary .pill{flex:none}
.card.flag-alert summary .more{flex:none;display:inline-flex;align-items:center;gap:.2rem;font-size:.82rem;font-weight:700;color:var(--accent);padding:.35rem .5rem;border-radius:999px;background:var(--surface)}
.card.flag-alert summary .more::after{content:"▾"}.card.flag-alert details[open] summary .more::after{content:"▴"}
.card.flag-alert details[open]>summary{margin-bottom:.4rem}
@media (max-width:600px){.flag-alert-list .row{flex-wrap:wrap;row-gap:.4rem}.flag-alert-list .row-main{flex:1 1 100%}.flag-alert-list .row-side{flex-direction:row;flex-wrap:nowrap;gap:.4rem}}
.flag-alert-list .row-side{flex-wrap:wrap}.server-sum{margin:0 0 .6rem}
.agenda .date{flex:none;width:4.5rem;display:flex;flex-direction:column;font-weight:800;font-size:.9rem}
.agenda .date small{font-weight:600;color:var(--muted);font-size:.75rem}
.cond-out{margin:.5rem 0 0;font-size:.88rem}
.cond-out .lines{padding-left:1.2rem}
#flag-cond{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:.9rem}
.sim-grid{display:grid;gap:.25rem .75rem;grid-template-columns:repeat(auto-fit,minmax(min(9.5rem,100%),1fr))}
/* 2.0.0 [RVA-31] 검토 T24-09: 이름표가 두 줄인 칸 옆에서 입력 칸이 어긋났어요. 이름표는 위, 입력 칸은 줄 맨 아래로 맞춰요. */
.sim-grid>.fieldset{display:flex;flex-direction:column}.sim-grid>.fieldset>label{flex:1 0 auto}
@media (prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}
@media (forced-colors:active){.btn,.seg,.tab,.card{border:1px solid CanvasText}}
/* ---- AX 1.30 [ADMX-01]~[ADMX-11] ---- */
/* [ADMX-11] Wider content on big screens, the same gutters on a phone. */
.bar,.main{max-width:80rem}
.hubtabs{max-width:80rem;margin-left:auto;margin-right:auto}
/* [ADMX-01] The four numbers that need a hand: 2×2 on a phone, a row of four on a wide screen. */
/* 2.0.0 [RVQ-14] 총괄 사진 검토(글자 200%): '50ms'가 '50m/s'로 끊겼어요. 칸 수(--cols)는 넘지 않되 칸마다 9rem은 있게 해서 글자가 크면 칸 수가 줄어요. */
.tiles.core{--cols:2;grid-template-columns:repeat(auto-fit,minmax(min(100%,max(9rem,calc(100% / var(--cols) - .6rem))),1fr));gap:.6rem}
@media (min-width:900px){.tiles.core{--cols:4}.tiles.core.metric-kpis{--cols:6}}
@media (min-width:600px) and (max-width:899.98px){.tiles.core.metric-kpis{--cols:3}}
.tile.kpi{position:relative;min-height:0;padding:.75rem .85rem .6rem;gap:.15rem}
.tile.kpi .v{display:flex;align-items:baseline;gap:.4rem;font-size:1.7rem}
.tile.kpi .d{font-size:.75rem;line-height:1.35;color:var(--muted);display:block;margin-top:0}
/* 2.0.0 [RVA-31] 검토 T24-19: 그래프 자리를 같은 높이의 한 줄로(그래프·한 줄 모두 칸 맨 아래). */
.tile.kpi .spark,.tile.kpi .tile-go{margin-top:auto}
.tile-go{display:flex;align-items:center;gap:.25rem;min-height:1.6rem;padding-top:.2rem;font-size:.8rem;font-weight:800;color:var(--accent)}
.tile-go.done{color:var(--ok)}
.tile-go svg{width:1rem;height:1rem;flex:none}
.tile.kpi.rising{border-color:color-mix(in srgb,var(--warn) 55%,var(--line))}
.tile.skeleton{min-height:6.5rem;background:var(--soft);border-color:transparent}
.delta{font-size:.75rem;font-weight:800;letter-spacing:0}
.delta.up{color:var(--warn)}.delta.down{color:var(--ok)}
.chg{font-weight:800}.chg.bad{color:var(--bad)}.chg.ok{color:var(--ok)}
.spark{display:block;width:100%;height:1.6rem;margin-top:.2rem}
.spark polyline{fill:none;stroke:var(--accent);stroke-width:1.6;vector-effect:non-scaling-stroke}
.spark circle{fill:var(--accent);stroke:none}
.tile.bad .spark polyline{stroke:var(--bad)}.tile.bad .spark circle{fill:var(--bad)}
@media (max-width:599.98px){.tiles:not(.core){display:grid}.tile.kpi .v{font-size:1.45rem}.tile.kpi .d{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}}
.more-tiles{margin:0 0 .75rem}
.more-tiles>summary{cursor:pointer;font-weight:800;font-size:.9rem;color:var(--muted);padding:.4rem .25rem;list-style:none}
.more-tiles .tiles{margin:.5rem 0}
.home-grid{display:grid;grid-template-columns:minmax(0,1fr);gap:0 1rem;align-items:start}
/* [RVQ-47] 검토 HQ-88: 오늘의 옆 칸(최근 기록)도 16rem이 안 되면 아래로(글자 200%의 1440에서 기록 글이 한 줄 88px로 섰어요). */
@media (min-width:1024px){.home-grid{display:flex;flex-wrap:wrap}.home-grid>*{min-width:0;flex:1 1 16rem}.home-grid>.home-main{flex:1.75 1 20rem}}
.home-main,.home-side{min-width:0}
/* 주의 필요 */
.alerts{border-color:color-mix(in srgb,var(--bad) 35%,var(--line))}
.alert-list{list-style:none;margin:0;padding:0}
.alert-list li{display:flex;align-items:center;gap:.5rem;border-top:1px solid var(--line);padding:.15rem 0}
.alert-list li:first-child{border-top:0}
.alert-list li::before{content:"";flex:none;width:.55rem;height:.55rem;border-radius:50%;background:var(--warn)}
.alert-list li.bad::before{background:var(--bad)}
.alert-list a{flex:1;min-width:0;display:flex;flex-wrap:wrap;align-items:baseline;gap:0 .6rem;padding:.5rem 0;color:inherit;text-decoration:none}
@media (hover:hover){.alert-list a:hover b{text-decoration:underline}}
.alert-list b{font-size:.95rem}.alert-list small{color:var(--muted);font-size:.8rem}
.alert-list svg{flex:none;width:1.1rem;height:1.1rem;color:var(--faint)}
.calm{display:flex;align-items:center;gap:.5rem;margin:0 .25rem .75rem;font-size:.86rem;color:var(--muted)}
.calm svg{flex:none;width:1.1rem;height:1.1rem;color:var(--ok)}
/* [ADMX-01] 할 일 rows: one line on a wide screen. */
.rows.inbox .row{min-height:3.25rem;padding:.55rem .85rem}
.rows.inbox .kind{font-size:.75rem;font-weight:800;color:var(--muted);margin-right:.15rem}
@media (min-width:600px){.rows.inbox .row-main b{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.rows.inbox .row-side{flex-wrap:nowrap}}
@media (max-width:599.98px){.rows.inbox .row{flex-wrap:wrap;gap:.35rem .5rem}.rows.inbox .row-main b{white-space:normal}
.rows.inbox .row-side{flex-direction:row;flex-wrap:wrap;align-items:center;justify-content:flex-start;width:100%}}
/* [ADMX-04] 검토 rows that open in place. */
.review-rows>li>details{margin:0;border-top:0;padding:0}
.review-rows>li>details>summary{list-style:none;cursor:pointer;font-size:1rem;font-weight:400;color:var(--fg)}
.review-rows>li>details>summary::-webkit-details-marker{display:none}
.review-rows>li>details>summary::after,details.card.events>summary::after{content:none}
.review-rows>li>details>summary .row-main b::before{content:"▸";display:inline-block;width:1rem;color:var(--faint);font-size:.8rem}
.review-rows>li>details[open]>summary .row-main b::before{content:"▾"}
.review-rows>li>details[open]>summary{background:var(--hover)}
.review-rows .row{min-height:3.5rem;padding:.6rem .85rem}
.review-rows .gist{color:var(--fg)}
.review-rows>li.picked>details>summary{background:var(--accent-soft)}
.row-side.inline{flex-direction:row;flex-wrap:wrap;align-items:center;justify-content:flex-end;gap:.35rem;max-width:60%}
@media (max-width:599.98px){.review-rows .row{flex-wrap:wrap}.review-rows .row-side.inline{max-width:none;width:100%;justify-content:flex-start;padding-left:1rem}}
/* 2.0.0 [RVD-20] HQ-08: large letters on a wide screen too: the buttons go under the words once the list is under 40 of its letters wide (사진 D/HQ-13 06). */
.review-rows{container-type:inline-size}
.nw{white-space:nowrap}
.review-rows .row-acts{display:inline-flex;gap:.35rem}
@container (max-width:40rem){.review-rows .row{flex-wrap:wrap}.review-rows .row-side.inline{max-width:none;width:100%;justify-content:flex-start;padding-left:1rem}}
.score.mini{margin:0;gap:.3rem}.score.mini meter{width:3.5rem;height:.5rem}.score.mini b{font-size:.78rem}
.rv-body{padding:.25rem 1rem 1rem 1.85rem;border-top:1px dashed var(--line)}
@media (max-width:599.98px){.rv-body{padding-left:1rem}}
.rv-body>details{margin-top:.6rem}
.actions.start{justify-content:flex-start}
.toolbar .grow{flex:1 1 16rem;margin:0}
/* [ADMX-02] 판 차이: field by field, the words that went struck, the words that came marked. */
.diff.facts ul{gap:.15rem}
.diff.facts li{display:grid;grid-template-columns:minmax(6rem,auto) 1fr;gap:.1rem .6rem;align-items:baseline;padding:.3rem 0;border-top:1px solid color-mix(in srgb,var(--line) 70%,transparent)}
.diff.facts li:first-child{border-top:0}
.diff.facts li b{grid-row:span 2;font-size:.82rem;color:var(--muted)}
.diff.facts .arrow{display:none}
.diff.facts .was{color:var(--muted)}
.diff.facts li.add .pill,.diff.facts li.del .pill{justify-self:start;font-size:.75rem;padding:.05rem .45rem}
.diff-title .sum{display:block;margin-top:.15rem;color:var(--fg);font-size:.92rem;font-weight:800}
del{background:var(--bad-bg);color:var(--bad);text-decoration:line-through;text-decoration-thickness:1.5px;border-radius:3px;padding:0 .1rem}
ins{background:var(--ok-bg);color:var(--ok);text-decoration:underline;text-decoration-thickness:1.5px;text-underline-offset:2px;border-radius:3px;padding:0 .1rem;font-weight:700}
@media (min-width:600px){.diff.facts li{grid-template-columns:9rem minmax(0,1fr) auto minmax(0,1fr)}.diff.facts li b{grid-row:auto}.diff.facts .arrow{display:inline;color:var(--faint)}
.diff.facts li.add .now,.diff.facts li.del .was{grid-column:2 / span 3}.diff.facts li.add .pill,.diff.facts li.del .pill{grid-column:auto}}
/* [ADMX-03] 여러 판 as a table: a sticky label column, the differing cells marked, the recommended column gold. */
.vsum{list-style:none;margin:0 0 .6rem;padding:0;display:grid;gap:.3rem;font-size:.88rem}
.vsum li{display:flex;flex-wrap:wrap;align-items:center;gap:.4rem}
.table-wrap{overflow-x:auto;-webkit-overflow-scrolling:touch;border:1px solid var(--line);border-radius:var(--r-sm);margin:0 0 .5rem}
table.cmp,table.list{border-collapse:separate;border-spacing:0;width:100%;font-size:.86rem}
table.cmp th,table.cmp td,table.list th,table.list td{padding:.5rem .65rem;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}
table.cmp tbody tr:last-child>*,table.list tbody tr:last-child>*{border-bottom:0}
table.cmp thead th{background:var(--soft);font-weight:700;min-width:11rem;white-space:normal}
table.cmp thead th .meta{display:block}
table.cmp th[scope=row],table.cmp th.lab{position:sticky;left:0;z-index:1;background:var(--surface);color:var(--muted);font-weight:700;min-width:7rem;max-width:9rem;border-right:1px solid var(--line)}
table.cmp thead th.lab{background:var(--soft)}
table.cmp td.differs{background:color-mix(in srgb,var(--warn-bg) 80%,var(--surface))}
table.cmp .best{box-shadow:inset 3px 0 0 var(--gold),inset -3px 0 0 var(--gold)}
table.cmp thead th.best{background:var(--gold-soft)}
table.cmp tr.num td{font-weight:700}
table.cmp tr.act-row td{background:var(--soft)}
table.cmp td.acts .btn{margin:0 .25rem .25rem 0}
/* [ADMX-06] 회사 표: a table on a wide screen, a small card per company on a phone. */
table.list thead th{background:var(--soft);white-space:nowrap}
table.list .sort{border:0;background:none;padding:0;font:inherit;font-weight:800;color:var(--muted);cursor:pointer}
table.list .sort[aria-pressed=true]{color:var(--accent)}
table.list td.n{text-align:right;white-space:nowrap;font-variant-numeric:tabular-nums}
table.list td.n.hot{color:var(--bad);font-weight:800}
table.list td.name a{font-weight:800;color:inherit;text-decoration:none}
@media (hover:hover){table.list td.name a:hover{text-decoration:underline}}
table.list td.name small{display:block;font-size:.75rem;color:var(--muted)}
table.list td.name .pill{margin-left:.35rem;font-size:.75rem}
table.list td small{color:var(--muted);font-size:.75rem}
table.list tr.picked td{background:var(--accent-soft)}
@media (hover:hover) and (min-width:600px){table.list tbody tr:hover td{background:var(--hover)}}
@media (min-width:600px){table.list thead th:not(:first-child){text-align:right}table.list thead th.pickcol+th{text-align:left}}
@media (min-width:600px){table.list.nums td:not(:first-child){text-align:right;font-variant-numeric:tabular-nums}}
@media (min-width:600px){table.list .lab{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}}
/* 2.0.0 [RVA-32] 총괄 HQ-60: 표의 숨긴 이름표(.lab, position:absolute)가 가로 스크롤 상자 밖 쪽을 기준으로 놓여 840에서 쪽 전체가
   옆으로 123px 밀렸어요. 스크롤 상자를 기준으로 삼아 표는 상자 안에서만 밀려요(숫자 표를 쓰는 모든 쪽). */
.table-wrap{position:relative;max-width:100%}
/* 같은 까닭으로 380·글자 200%: 칸 최소 폭(15rem = 480px)이 화면보다 넓던 격자는 화면 폭에서 멈추고, 카드 머리 알약은 다음 줄로,
   카드 머리의 긴 단추(자동 순환 '동기화 열쇠 만들기' 등)는 칸 폭에서 줄을 바꿔요. */
.card-head>.btn{max-width:100%;white-space:normal;text-align:center;line-height:1.25}
.table-wrap .lab{left:0;top:0}
/* 표는 상자 안에서 밀리니 칸을 글자 하나 폭까지 줄이지 않아요(몸의 overflow-wrap:anywhere 때문에 '투자 이야기'가 '이야/기'로 갈라졌어요). */
@media (min-width:600px){.table-wrap table.list th,.table-wrap table.list td{overflow-wrap:break-word}}
@media (max-width:599.98px){
  .table-wrap:has(table.list){border:0;overflow:visible}
  table.list,table.list tbody{display:block}
  table.list thead{display:flex;overflow-x:auto;gap:.25rem;margin-bottom:.5rem}
  table.list thead tr{display:flex;gap:.25rem}
  table.list thead th{border:0;background:none;padding:.25rem .4rem}
  table.list tbody tr{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:.15rem .5rem;padding:.65rem .85rem;margin:0 0 .5rem;background:var(--surface);border:1px solid var(--line);border-radius:var(--r)}
  table.list tbody td{border:0;padding:0;text-align:left}
  table.list td.name{grid-column:1 / -1;font-size:1rem}
  table.list td.pick{grid-column:1 / -1}
  table.list td.n{text-align:left;font-size:.85rem;white-space:normal;overflow-wrap:anywhere}table.list td.n .lab{display:block}
  table.list .lab{color:var(--muted);font-size:.75rem;font-weight:600}
}
/* [ADMX-07] 지표: the period stays on top while scrolling. */
.timebar{position:sticky;top:var(--top-h,3.75rem);z-index:3;margin:0 -1rem .75rem;padding:.5rem 1rem .25rem;background:color-mix(in srgb,var(--bg) 92%,transparent);
backdrop-filter:blur(10px);-webkit-backdrop-filter:blur(10px);border-bottom:1px solid var(--line)}
.timebar .toolbar{margin:0}.timebar .segmented{margin:0}.timebar .meta{flex-basis:100%;margin:0}
@media (min-width:600px){.timebar{margin:0 -1.5rem .75rem;padding:.5rem 1.5rem .35rem}.timebar .meta{flex-basis:auto;margin-left:auto}}
.grid.two>.card.wide{grid-column:1 / -1}
.chart .mark line{stroke:var(--warn);stroke-width:1.5;stroke-dasharray:3 3}
.chart .mark circle{fill:var(--warn);stroke:none}
.legend .sw.mark{background:none;border-left:2px dashed var(--warn);width:0}
details.card.events>summary{list-style:none;cursor:pointer}
details.card.events>summary h2{display:inline;font-size:1.06rem}
/* [ADMX-05] 문의 filters: one line that scrolls sideways on a phone. */
.filterbar{display:flex;flex-direction:column;gap:0}
@media (max-width:599.98px){.filterbar{flex-direction:row;overflow-x:auto;gap:.5rem;scrollbar-width:none;margin:0 -1rem .5rem;padding:0 1rem}
.filterbar::-webkit-scrollbar{display:none}.filterbar .chips{flex-wrap:nowrap;margin:0}.filterbar .chip{white-space:nowrap}}
/* [ADMX-08] Search in the palette: the server's groups under a heading each. */
.palette .group-head{padding:.5rem 1rem .2rem;font-size:.75rem;font-weight:800;color:var(--muted);background:var(--soft)}
.palette .row small.hint2{display:block;font-size:.75rem;color:var(--muted)}
/* 1.35 [INBOX-01]~[INBOX-07] 문의 정리함: the piles as a strip of counts, the cards with their answers ready. */
.pile-strip{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(7.5rem,100%),1fr));gap:.5rem;margin:0 0 .9rem}
.pile-card{display:flex;flex-direction:column;gap:.1rem;padding:.65rem .8rem;border-radius:var(--r-sm);border:1px solid var(--line);background:var(--surface);color:inherit;text-decoration:none;box-shadow:var(--shadow)}
.pile-card .pile-n{font-size:1.35rem;font-weight:800;font-variant-numeric:tabular-nums;line-height:1.2}
.pile-card .pile-name{font-size:.78rem;color:var(--muted);font-weight:700}
.pile-card.good .pile-n{color:var(--ok)}.pile-card.warn .pile-n{color:var(--warn)}.pile-card.bad .pile-n{color:var(--bad)}.pile-card.info .pile-n{color:var(--info)}
.sort-top{display:grid;grid-template-columns:minmax(0,1fr);gap:.75rem;margin:0 0 .5rem}
@media (min-width:800px){.sort-top{grid-template-columns:minmax(0,1fr) minmax(0,1.4fr)}.sort-top>.card{margin:0}}
.auto-head small,.handoff-card>small{display:block;margin:.2rem 0 0;font-size:.82rem;color:var(--muted)}
.handoff-card>b,.auto-head b{font-size:1rem}
.pile{margin:1.4rem 0 0;scroll-margin-top:4.5rem}
.pile-head{display:flex;align-items:flex-end;justify-content:space-between;gap:.75rem;flex-wrap:wrap;margin:0 .25rem .6rem}
.pile-head h3{font-size:1rem;display:flex;align-items:center;gap:.4rem;margin:0}
.pile-head small{display:block;font-size:.82rem;color:var(--muted)}
.pile-card.zero{box-shadow:none;background:none;opacity:.5}
.sort-view{display:flex;flex-direction:column}
.sort-top{scroll-margin-top:4.5rem}
/* A phone: the piles in one swipeable line (empty ones left out), the questions first, 자동 대응·넘기기 at the end. */
@media (max-width:599px){.pile-strip{display:flex;overflow-x:auto;scrollbar-width:none;margin:0 -1rem .75rem;padding:0 1rem .2rem}
.pile-strip::-webkit-scrollbar{display:none}.pile-strip .pile-card{flex:none;min-width:6.5rem}.pile-strip .pile-card.zero:not(.handoff-chip){display:none}
.sort-view>.sort-top{order:2;margin-top:1.4rem}.sort-view>.pile,.sort-view>.empty{order:1}}
.sort-card{display:grid;grid-template-columns:minmax(0,1fr);gap:.25rem 1.25rem;border-bottom:1px solid var(--line);padding:.85rem 1rem}
.sort-card:last-child{border-bottom:0}
@media (min-width:1024px){.sort-card{grid-template-columns:minmax(0,1fr) minmax(0,1.15fr)}.sort-answer .sort-field:first-child{margin-top:0}}
.sort-ask{display:flex;flex-direction:column;gap:.1rem;min-width:0}
.sort-ask>b a{color:inherit;text-decoration:none}
@media (hover:hover){.sort-ask>b a:hover{text-decoration:underline}}
.sort-ask>small{font-size:.8rem;color:var(--muted)}
.sort-card .match{display:block;margin:.35rem 0 0;font-size:.82rem;color:var(--info);font-weight:700}
.sort-card details{margin:.4rem 0 0;padding-top:.25rem}
.sort-card .actions{margin:.5rem 0 0}
.sort-field{display:block;margin:.6rem 0 0;font-weight:700}
.sort-field>span{display:block;font-size:.8rem;color:var(--muted);margin:0 0 .25rem}
.sort-field textarea{min-height:4.5rem}
ul.mini{list-style:none;margin:.4rem 0 0;padding:0;display:flex;flex-direction:column;gap:.3rem;font-size:.86rem}
ul.mini small{color:var(--muted);margin-left:.3rem}
ul.mini small.answer{display:block;margin:.15rem 0 0;color:var(--fg);white-space:pre-line}
/* 1.35 [ADMHE-01] A phone: the list starts higher. 정렬·고르기·CSV fold behind one button; the waiting line that only
   repeats this tab's own number and the keyboard hint are left out. */
.tools-toggle{display:none}
.tool-more{display:contents}
@media (max-width:599.98px){.toolbar.fold{flex-wrap:nowrap}.toolbar.fold.open{flex-wrap:wrap}.tools-toggle{display:inline-flex}
.toolbar.fold .tool-more{display:none}.toolbar.fold.open .tool-more{display:flex;flex-wrap:wrap;gap:.5rem;flex-basis:100%}
.toolbar.fold.open .tool-more select{flex:1 1 10rem}
.toolbar.fold input[type=search]{flex:1 1 0}
.waiting.only-here{display:none}.fresh{padding:.45rem .7rem;font-size:.85rem;flex-wrap:nowrap}.fresh span{flex:1 1 auto}.fresh [data-act=fresh-bugs]{display:none}}
@media (pointer:coarse){.kbd-hint{display:none}}
.sort-nudge{display:flex;align-items:center;gap:.6rem;margin:0 0 .9rem;padding:.7rem .9rem;border-radius:var(--r-sm);background:var(--info-bg);color:var(--fg);text-decoration:none;font-size:.88rem}
.sort-nudge svg{flex:none;width:1.25rem;height:1.25rem}.sort-nudge span{flex:1;min-width:0}.sort-nudge b{color:var(--info);margin-right:.35rem}
/* 1.36 [GRW-07] 할 일함, [GRW-04] 스튜디오 요구 */
.rows.work .row{align-items:center}.rows.work .pick{flex:none;display:flex;align-items:center;padding-right:.25rem}
.rows.work .pick input{width:1.25rem;height:1.25rem}
.rows.work .row-side{flex-direction:row;flex-wrap:wrap;align-items:center;justify-content:flex-end;gap:.35rem;max-width:62%}
.rows.work .row-main b{display:block}
button.tile{font:inherit;text-align:left;cursor:pointer;color:inherit}button.tile[aria-pressed=true]{border-color:var(--accent)}
.rows.needs>li.item{display:block;padding:1rem;border:1px solid var(--line);border-radius:var(--r-lg);background:var(--surface);margin:0 0 .75rem}
.rows.needs h3{margin:0;font-size:1rem}
.samples{margin:.5rem 0;padding-left:1.1rem}.samples li{margin:.15rem 0}
.copy-box{white-space:pre-wrap;background:var(--soft);padding:.75rem;border-radius:var(--r)}
.rows.work .row-tags,.rows.work .row-acts{display:flex;flex-wrap:wrap;align-items:center;gap:.35rem}
/* PC 확인 8d290ec3: 휴대폰 폭에서 상태는 두 줄로 다 보이고, 단추는 제목 아래 한 줄에 같은 폭으로. */
hr.split{border:0;border-top:2px dashed var(--line);margin:1.5rem 0}
.linkish{background:none;border:0;padding:0;color:var(--accent,#2b6cb0);text-decoration:underline;cursor:pointer;font:inherit}
li.flash{outline:2px solid var(--accent,#2b6cb0);outline-offset:2px;transition:outline-color .3s}
img.photo-big{display:block;width:100%;max-width:512px;height:auto;margin:0 auto;image-rendering:auto;border-radius:8px}
@media (max-width:640px){.rows.work .row{flex-wrap:wrap}.rows.work .row-side{width:100%;max-width:none;flex-direction:column;align-items:stretch;gap:.4rem}
.rows.work .row-acts{flex-wrap:nowrap}.rows.work .row-acts .btn{flex:1 1 0;min-width:0;padding-left:.3rem;padding-right:.3rem}
.segmented.wrap{flex-wrap:wrap;overflow:visible}.segmented.wrap .seg{flex:1 1 30%}}

/* ==== 1.37 [ADM2-06] 디자인 2판: 차분한 중립색·촘촘한 간격·작은 둥글기·얇은 그림자 (아래가 위를 덮어요) ==== */
:root{--bg:#F6F7F9;--surface:#fff;--raised:#fff;--fg:#0F172A;--muted:#475569;--faint:#64748B;--line:#E5E7EB;--line-strong:#D3D7DE;
--soft:#F1F3F6;--hover:#F3F5F8;--accent:#3557E0;--accent-soft:#EEF2FE;--gold:#A9813F;--gold-soft:#F7F0E3;--nav:#0B1020;--nav-fg:#AEB4C6;
--ok:#15803D;--ok-bg:#E9F7EF;--warn:#9A5B00;--warn-bg:#FEF5E1;--bad:#C0362C;--bad-bg:#FDEDEB;--info:#3557E0;--info-bg:#EEF2FE;
--shadow:0 1px 2px rgba(15,23,42,.04),0 1px 3px rgba(15,23,42,.05);--shadow-lg:0 24px 64px rgba(2,6,23,.28);--r:12px;--r-sm:9px;--r-lg:14px}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#0A0E18;--surface:#111726;--raised:#161D2F;--fg:#E7EAF0;--muted:#A3ABBD;--faint:#8D95A8;
--line:#1F2737;--line-strong:#2C3648;--soft:#171F31;--hover:#1A2235;--accent:#8EA6FF;--accent-soft:#1A2448;--gold:#D4B47A;--gold-soft:#2A2416;--nav:#070A12;
--ok:#7DD3A3;--ok-bg:#10291F;--warn:#F0C272;--warn-bg:#30260F;--bad:#FF8F84;--bad-bg:#371A19;--info:#8EA6FF;--info-bg:#1A2448;--shadow:none}}
:root[data-theme="dark"]{--bg:#0A0E18;--surface:#111726;--raised:#161D2F;--fg:#E7EAF0;--muted:#A3ABBD;--faint:#8D95A8;--line:#1F2737;--line-strong:#2C3648;
--soft:#171F31;--hover:#1A2235;--accent:#8EA6FF;--accent-soft:#1A2448;--gold:#D4B47A;--gold-soft:#2A2416;--nav:#070A12;--ok:#7DD3A3;--ok-bg:#10291F;
--warn:#F0C272;--warn-bg:#30260F;--bad:#FF8F84;--bad-bg:#371A19;--info:#8EA6FF;--info-bg:#1A2448;--shadow:none}
body{letter-spacing:-.003em}
h1,h2,h3{letter-spacing:-.018em}
.bar h1{font-weight:750;letter-spacing:-.02em}
.card{border-radius:var(--r-lg);padding:1.05rem 1.2rem}
.card>h2,.card-head h2{font-size:1rem;font-weight:750}
.btn{font-weight:650;border-radius:var(--r-sm);transition:background .15s,border-color .15s,box-shadow .15s}
.btn.primary{box-shadow:0 1px 0 rgba(255,255,255,.18) inset,0 1px 2px rgba(15,23,42,.18)}
@media (hover:hover){.btn:not(.primary):not(.ghost):not(.danger):hover{border-color:color-mix(in srgb,var(--fg) 22%,var(--line))}}
.pill{font-weight:650;font-size:.76rem;padding:.16rem .55rem;border:1px solid color-mix(in srgb,currentColor 14%,transparent)}
.pill:not(.ok):not(.warn):not(.bad):not(.info):not(.gold):not(.late){border-color:var(--line)}
.tile{border-radius:var(--r-lg);box-shadow:var(--shadow)}
.tile .k{font-weight:650;letter-spacing:0}
.tile .v{font-weight:750;letter-spacing:-.03em}
.tile.hot{border-color:var(--line)}
/* 2.0.0 [RVA-35] 조건 6: 기다리는 일이 있는 칸(hot)은 파란 점과 보통 색 숫자, 빨강은 정말 급한 칸(bad: 오늘 답 목표 넘김)에만,
   주황은 주의(warn: 아쉬워요, 어제보다 늚 ▲)에만. 숫자 칸 넷이 모두 빨갛던 것을 줄였어요. */
.tile.hot .k::before{content:"";flex:none;width:.45rem;height:.45rem;border-radius:50%;background:var(--accent)}
.tile.warn .k::before{content:"";flex:none;width:.45rem;height:.45rem;border-radius:50%;background:var(--warn)}
.tile.bad .k::before{background:var(--bad)}
.tile.kpi[aria-pressed=true]{border-color:var(--accent);box-shadow:0 0 0 3px var(--accent-soft)}
.rows{border-radius:var(--r-lg)}
@media (hover:hover){a.row:hover,button.row:hover,label.row:hover{background:var(--hover)}}
.chip.pick{border-color:var(--line-strong);font-weight:650}
.segmented{border-radius:var(--r)}
.hubtabs{gap:.15rem}
.hubtabs a{border-radius:0;min-height:2.6rem;padding:0 .8rem;font-weight:650;box-shadow:inset 0 -2px 0 transparent}
@media (hover:hover){.hubtabs a:hover{background:none;color:var(--fg);box-shadow:inset 0 -2px 0 var(--line-strong)}}
.hubtabs a[aria-current=page]{background:none;color:var(--fg);box-shadow:inset 0 -2px 0 var(--accent)}
.top{background:color-mix(in srgb,var(--surface) 86%,transparent)}
@media (min-width:600px){
  .side{border-right:1px solid rgba(255,255,255,.04)}
  .nav a{font-weight:600}
  .nav a[aria-current=page]{background:rgba(255,255,255,.09)}
}
@media (min-width:1024px){
  .side .brand-row{border-bottom:1px solid rgba(255,255,255,.07);margin-bottom:.5rem;padding-bottom:1rem}
  .nav a{padding:.55rem .75rem;font-size:.9rem}
  .nav .subnav a{font-weight:600}
}
.sort-nudge{border-radius:var(--r)}
dialog{border-radius:var(--r-lg)}

/* ==== 1.37 [ADM2-01][ADM2-05] 흐름(관제) ==== */
.flow-head{display:flex;justify-content:space-between;align-items:flex-end;gap:.75rem 1rem;flex-wrap:wrap;margin:.25rem 0 .9rem}
.flow-head h2{font-size:1.3rem;font-weight:750}
.flow-head .meta{max-width:38rem}
.flow-state{display:flex;flex-wrap:wrap;align-items:center;gap:.4rem}
/* 2.0.0 [RVA-40] 검토 U23-02: 문의 옆 '이 휴대폰' 패널은 꼭 볼 것만 굵게, 나머지는 '자세히' 한 줄 */
.person-alerts{list-style:none;margin:.2rem 0 .4rem;padding:0;display:grid;gap:.3rem}
.person-alerts li{padding:.15rem 0 .15rem .6rem;border-left:3px solid var(--warn);font-size:.95rem;line-height:1.35}
.person-alerts li.bad{border-left-color:var(--bad)}
.person-calm{margin:.2rem 0 .3rem;color:var(--muted);font-size:.9rem}
details.person-more{margin:.5rem 0 0;border-top:1px solid var(--line)}
.billing-why{margin:.15rem 0 .35rem;font-size:.92rem;line-height:1.45}
/* 2.0.0 [RVA-43] 검토 ADMX-22: 볼 숫자가 없는 기능은 접힌 한 줄 */
/* 2.0.0 [RVA-45] 검토 U24-14: 내 계정 세 묶음(접힌 줄은 카드 한 줄 모양, 펴면 그 아래 카드들) */
.acct-groups{display:grid;gap:.5rem;margin:.75rem 0}
details.acct-group[open]>summary{margin-bottom:.6rem}
.acct-group .lf-t b{font-size:.98rem}
.acct-body{margin:0 0 .5rem}
/* 2.0.0 [RVA-44] 검토 U24-13: 관리자 초대·권한 창의 영역별 권한은 접힌 한 줄 */
.perm-sum{margin:.45rem 0 0;font-size:.9rem;font-weight:600}
details.perm-areas{margin:.6rem 0 .25rem;border-top:1px solid var(--line);border-bottom:1px solid var(--line)}
details.perm-areas>summary,details.acct-group>summary{display:flex;flex-wrap:nowrap;align-items:center;gap:.75rem;min-height:2.75rem;padding:.45rem 0;list-style:none;cursor:pointer}
details.perm-areas>summary::-webkit-details-marker,details.acct-group>summary::-webkit-details-marker{display:none}
details.perm-areas>summary::after,details.acct-group>summary::after{content:"";flex:none;width:.5rem;height:.5rem;border-right:2px solid var(--muted);border-bottom:2px solid var(--muted);transform:rotate(45deg);margin:0 .2rem}
details.perm-areas[open]>summary::after,details.acct-group[open]>summary::after{transform:rotate(-135deg)}
.perm-areas .lf-t,.acct-group .lf-t{flex:1;min-width:0;display:flex;flex-direction:column;gap:.1rem}
.perm-areas .lf-t small,.acct-group .lf-t small{font-size:min(.8rem,16px);font-weight:500;color:var(--muted)}
.perm-areas .perm-table{margin:.25rem 0 .5rem}
/* 2.0.0 [RVA-40][RVA-43][RVA-44][RVA-45] 새 접힌 줄은 기본 details·summary(파란 굵은 글, 위 선)를 쓰지 않아요 */
details.acct-group,details.card.fx-blank{border-top:0;padding-top:0}
details.acct-group{margin:0}
details.acct-group>summary,details.person-more>summary,details.perm-areas>summary,details.card.fx-blank>summary{color:var(--fg);font-size:inherit;font-weight:inherit}
details.acct-group>summary{padding:.6rem 1.1rem;background:var(--surface);border:1px solid var(--line);border-radius:var(--r);min-height:3.25rem}
details.card.fx-blank{margin:.75rem 0 0}
details.card.fx-blank>summary{display:flex;flex-wrap:nowrap;align-items:center;gap:.75rem;min-height:2.75rem;list-style:none;cursor:pointer}
details.card.fx-blank>summary::-webkit-details-marker{display:none}
details.card.fx-blank>summary::after{content:"";flex:none;width:.5rem;height:.5rem;border-right:2px solid var(--muted);border-bottom:2px solid var(--muted);transform:rotate(45deg);margin:0 .2rem}
details.card.fx-blank[open]>summary::after{transform:rotate(-135deg)}
.fx-blank .lf-t{flex:1;min-width:0;display:flex;flex-direction:column;gap:.1rem}
.fx-blank .lf-t small{font-size:min(.8rem,16px);font-weight:500;color:var(--muted)}
.fx-names{list-style:none;margin:.6rem 0 0;padding:0;display:grid;grid-template-columns:repeat(auto-fill,minmax(min(14rem,100%),1fr));gap:.15rem 1rem}
.fx-names li{min-width:0;border-top:1px solid var(--line)}
.fx-names a,.fx-names span{display:flex;flex-direction:column;justify-content:center;min-height:2.75rem;padding:.35rem 0;color:var(--fg);text-decoration:none;overflow-wrap:anywhere}
.fx-names b{font-weight:600}
.fx-names small{color:var(--muted);font-size:min(.78rem,16px)}
details.person-more>summary{display:flex;flex-wrap:nowrap;align-items:center;gap:.75rem;min-height:2.75rem;padding:.5rem 0 .2rem;cursor:pointer;list-style:none}
details.person-more>summary::-webkit-details-marker{display:none}
details.person-more>summary::after{content:"";flex:none;width:.5rem;height:.5rem;border-right:2px solid var(--muted);border-bottom:2px solid var(--muted);transform:rotate(45deg);margin:0 .2rem}
details.person-more[open]>summary::after{transform:rotate(-135deg)}
.person-more .lf-t{flex:1;min-width:0;display:flex;flex-direction:column;gap:.1rem}
.person-more .lf-t small{font-size:min(.8rem,16px);font-weight:500;color:var(--muted)}
/* 2.0.0 [RVA-38] 검토 U23-04: 자동 순환 맨 위 한 줄 상태와 다음 한 걸음, 그 아래 접힌 줄 */
.loop-now{margin:.25rem 0 .75rem}
.loop-now .loop-state{display:flex;flex-wrap:wrap;align-items:center;gap:.3rem .9rem;font-size:1.05rem;font-weight:600;margin:0}
.loop-now .loop-state b{font-weight:750}
.loop-now>.meta{margin:.35rem 0 0}
.loop-next{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:.5rem .9rem;margin:.7rem 0 0;padding:.65rem .75rem;border-radius:var(--r-sm);background:var(--accent-soft)}
.loop-next p{margin:0;flex:1 1 16rem;min-width:0}
.loop-next p b{margin-right:.35rem}
.loop-now .calm{margin:.6rem 0 0}
.loop-now .hint{margin:.6rem 0 0}
.loop-folds{gap:.5rem}
details.card.loop-fold>summary{display:flex;flex-wrap:nowrap;align-items:center;gap:.75rem;min-height:2.75rem}
details.card.loop-fold>summary::after{flex:none;margin:0 .2rem}
.loop-fold .lf-t{flex:1;min-width:0;display:flex;flex-direction:column;gap:.1rem}
.loop-fold .lf-t small{font-size:min(.8rem,16px);font-weight:500;color:var(--muted)}
.loop-fold .lf-t small.warn{color:var(--warn);font-weight:700}
.loop-next .btn{max-width:100%;white-space:normal;text-align:center;line-height:1.25}
details.card.loop-fold[open]>summary{margin-bottom:.6rem}
.check-seen.just b{color:var(--ok)}
.row-main .changed{color:var(--fg);font-weight:600}
.dev-rows .row-main b{white-space:normal;overflow:hidden;overflow-wrap:anywhere;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}
.dev-rows .dev-st{font-weight:600;color:var(--fg)}
.dev-rows .dev-st.todo{color:var(--accent)}
.dev-rows .dev-st.done{color:var(--ok)}
.dev-rows .dev-st.rejected{color:var(--muted)}
.dev-rows .dev-st.ask{color:var(--warn)}
.tier-rows .row-main small{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.tier-rows details[open] .row-main small{display:block}
.dates-rows .item{padding:.55rem .85rem}
.dates-rows .dl-soon{font-weight:700;color:var(--fg)}
.dates-rows .dl-before{color:var(--muted)}
.dl-later{margin-top:.5rem}
/* 2.0.0 [RVA-46] 검토 HQ-69(조건 6·14): 넘긴 줄은 빨간 띠·칩 대신 '기한 넘김 N' 머리 하나, 줄의 칸은 굵은 보통 글자('넘음'·'넘김'은 글로) */
.pill.late{background:transparent;border-color:transparent;color:var(--fg);font-weight:800;padding-inline:0}
.seg .n.warm{color:var(--warn)}
.mh{display:grid;gap:.75rem}
.mh-head p{margin:0}
.mh-form{display:grid;gap:.5rem;margin-top:.5rem}
.mh-form .hint{margin:0}
.mh-sum{display:flex;flex-wrap:wrap;align-items:baseline;gap:.25rem .75rem;font-size:1.05rem}
.mh-rows>li.lg-group{display:flex;flex-wrap:wrap;align-items:center;gap:.25rem .6rem;padding:.5rem .85rem;background:var(--soft)}
.mh-rows>li.lg-group small{font-size:min(.8rem,16px);color:var(--muted)}
.sig-steps{display:flex;flex-wrap:wrap;gap:.25rem .5rem;margin:.5rem 0 0;padding:0;list-style:none;font-size:.82rem}
.sig-steps li{display:inline-flex;align-items:baseline;gap:.25rem;color:var(--muted)}
.sig-steps li+li::before{content:"›";color:var(--muted);margin-inline-end:.25rem}
.sig-steps li.done{color:var(--fg)}
.sig-steps li.done>span{font-weight:600}
.sig-steps li.todo>span{opacity:.75}
.sig-steps li.ok>span{color:var(--ok)}
.sig-steps li.warn>span{color:var(--warn)}
.sig-steps small{color:var(--muted)}
.sig-flow{list-style:none;margin:0;padding:0;display:grid;gap:.75rem}
.sig-flow>li>a{color:var(--fg);text-decoration:none}
.due-late{color:var(--fg);font-weight:800}
.rows.ledger>li.lg-group{display:flex;flex-wrap:wrap;align-items:center;gap:.25rem .6rem;padding:.5rem .85rem;background:var(--soft)}
.rows.ledger>li.lg-group small{font-size:min(.8rem,16px);color:var(--muted)}
.rows.ledger>li.lg-group.rest b{font-size:min(.8rem,16px);font-weight:700;color:var(--muted)}
/* 2.0.0 [RVA-49] 검토 U23-05: 노트북(1280px~)은 목록 옆 칸에 상세, 줄은 제자리. 표는 칸 너비에 맞춰 덜 중요한 칸부터 접어요(.lg-table 컨테이너) */
.lg-split.on{display:grid;grid-template-columns:minmax(0,1fr) minmax(20rem,27rem);gap:1rem;align-items:start}
.lg-side{position:sticky;top:calc(var(--top-h,4.75rem) + .5rem);max-height:calc(100dvh - var(--top-h,4.75rem) - 1.25rem);overflow:auto;background:var(--surface);
border:1px solid var(--line);border-radius:var(--r-lg);box-shadow:var(--shadow)}
.lg-side-head{display:flex;align-items:center;justify-content:space-between;gap:.75rem;padding:.6rem .6rem .6rem .95rem;border-bottom:1px solid var(--line)}
.lg-side-head>span{min-width:0;font-weight:700;overflow-wrap:anywhere}
.lg-side-head b{font-variant-numeric:tabular-nums;color:var(--muted);margin-right:.35rem}
.lg-side .lg-detail{border-top:0;padding-top:.6rem}
.rows.ledger>li.lg-line>.lg-row{cursor:pointer}
.rows.ledger>li.lg-line.on>.lg-row{background:var(--hover);box-shadow:inset 3px 0 0 var(--accent)}
.next-bar{display:flex;align-items:center;gap:.7rem;padding:.7rem .75rem .7rem 1rem;margin:0 0 .9rem;border-radius:var(--r);background:var(--accent-soft);
border:1px solid color-mix(in srgb,var(--accent) 22%,transparent)}
.next-bar>svg{flex:none;width:1.1rem;height:1.1rem;color:var(--accent)}
.next-bar>span{flex:1;min-width:0;font-size:.92rem}
.next-bar b{margin-right:.35rem}
.next-bar.stuck{background:var(--bad-bg);border-color:color-mix(in srgb,var(--bad) 25%,transparent)}
.next-bar.stuck>svg{color:var(--bad)}
.next-bar.calm{background:var(--ok-bg);border-color:color-mix(in srgb,var(--ok) 22%,transparent)}
.next-bar.calm>svg{color:var(--ok)}
.pipe{list-style:none;margin:0 0 1rem;padding:0;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:.6rem}
@media (min-width:1200px){.pipe{grid-template-columns:repeat(6,minmax(0,1fr))}
  .pipe-step:not(:last-child)::after{content:"";position:absolute;z-index:1;right:-.5rem;top:1.05rem;width:.5rem;height:.5rem;
  border-top:2px solid var(--line-strong);border-right:2px solid var(--line-strong);transform:translateX(-.05rem) rotate(45deg)}}
.pipe-step{position:relative;display:flex;flex-direction:column;gap:.3rem;min-width:0;padding:.85rem .9rem .8rem;background:var(--surface);
border:1px solid var(--line);border-radius:var(--r-lg);box-shadow:var(--shadow)}
.pipe-top{display:flex;align-items:center;gap:.45rem;font-size:.86rem;min-width:0}
.pipe-top b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.pipe-top .pill{margin-left:auto}
.pipe-no{flex:none;display:inline-grid;place-items:center;width:1.35rem;height:1.35rem;border-radius:50%;background:var(--soft);color:var(--muted);font-size:.75rem;font-weight:800}
.pipe-n{display:flex;align-items:baseline}
.pipe-n .v{font-size:2rem;font-weight:750;letter-spacing:-.03em;line-height:1.05}
.pipe-n .u{margin-left:.2rem;color:var(--muted);font-size:.85rem}
.pipe-old{margin:0;font-size:.8rem;color:var(--muted)}
.pipe-hint{flex:1;margin:0;font-size:.78rem;line-height:1.45;color:var(--faint)}
.pipe-act{margin-top:.3rem}
.pipe-act .btn{width:100%}
.pipe-step.zero .pipe-n .v{color:var(--faint)}
.pipe-step.next{border-color:var(--accent);box-shadow:0 0 0 3px var(--accent-soft)}
.pipe-step.next .pipe-no{background:var(--accent);color:var(--on-accent)}
.pipe-step.stuck{border-color:color-mix(in srgb,var(--bad) 45%,var(--line))}
.pipe-step.stuck .pipe-n .v{color:var(--bad)}
.pipe-skel{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(10rem,100%),1fr));gap:.6rem;margin:3.5rem 0 1rem}
@media (max-width:599.98px){
  .pipe{grid-template-columns:1fr;gap:.45rem}
  .pipe-step{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:.15rem .75rem;padding:.7rem .85rem}
  .pipe-top{grid-column:1}.pipe-old{grid-column:1}
  .pipe-n{grid-column:2;grid-row:1/3;justify-content:flex-end}
  .pipe-n .v{font-size:1.6rem}
  .pipe-hint{display:none}
  .pipe-step.next .pipe-hint{display:block;grid-column:1/-1;margin-top:.2rem}
  .pipe-act{grid-column:1/-1}
  .pipe-step.zero .pipe-act,.pipe-step:not(.next):not(.stuck) .pipe-act{display:none}
  .next-bar{flex-wrap:wrap}.next-bar>span{flex-basis:calc(100% - 2rem)}.next-bar .btn{margin-left:auto}
}
.setup-card{border-color:color-mix(in srgb,var(--warn) 35%,var(--line))}
.setup{margin:0;padding-left:1.25rem;display:grid;gap:.65rem;font-size:.92rem}
.setup .btn{margin-left:.35rem;vertical-align:middle}
.how>summary{display:flex;align-items:center;justify-content:space-between;gap:.75rem;cursor:pointer;list-style:none;min-height:1.75rem}
.how>summary::-webkit-details-marker{display:none}
.how>summary::after{content:"";width:.5rem;height:.5rem;border-right:2px solid var(--muted);border-bottom:2px solid var(--muted);transform:rotate(45deg);margin-left:.25rem}
.how[open]>summary::after{transform:rotate(-135deg)}
.how>summary .meta{margin:0 0 0 auto}
.how-steps{list-style:none;margin:.85rem 0 0;padding:0;display:grid;gap:.85rem;grid-template-columns:repeat(auto-fit,minmax(min(15rem,100%),1fr))}
.how-steps li{display:flex;align-items:flex-start;gap:.7rem}
.how-steps p{margin:.15rem 0 0;font-size:.86rem;color:var(--muted)}
.step-no{flex:none;display:inline-grid;place-items:center;width:1.7rem;height:1.7rem;border-radius:50%;background:var(--accent-soft);color:var(--accent);font-weight:800;font-size:.85rem}
.flow-cols{display:grid;gap:.75rem}
/* [RVQ-47] 검토 HQ-88: 넓은 화면도 칸이 22rem보다 좁으면 한 칸으로(글자 200%의 1440에서 자동 순환 카드 안 줄이 62px로 섰어요), 두 칸까지만. */
@media (min-width:1024px){.flow-cols{grid-template-columns:repeat(auto-fit,minmax(min(100%,max(22rem,calc(50% - .4rem))),1fr));align-items:start}}
.week-chart{margin:0}
.week-chart svg{display:block;width:100%;height:auto}
.week-chart text{font-size:10px;fill:var(--faint)}
.week-chart .grid line{stroke:var(--line);stroke-width:1}
.bar-in{fill:var(--accent)}.bar-out{fill:var(--gold)}
.week-chart figcaption{margin-top:.35rem;font-size:.8rem;color:var(--muted)}
.week-chart .key{display:inline-block;width:.65rem;height:.65rem;border-radius:2px;margin:0 .3rem 0 .5rem;vertical-align:-.05rem}
.week-chart .key:first-child{margin-left:0}
.key.in{background:var(--accent)}.key.out{background:var(--gold)}
.data-table{margin-top:.6rem;font-size:.85rem}
.data-table summary{cursor:pointer;color:var(--muted)}
.data-table table{width:100%;margin-top:.4rem;border-collapse:collapse;font-variant-numeric:tabular-nums}
.data-table th,.data-table td{padding:.35rem .5rem;border-bottom:1px solid var(--line);text-align:right}
.data-table th:first-child,.data-table td:first-child{text-align:left}
.chk{display:flex;align-items:center;gap:.5rem;margin:.6rem 0}
.chk input{width:1.15rem;height:1.15rem}
.mono{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:.85rem}
ul.plain{margin:.25rem 0 .75rem;padding-left:1.1rem;font-size:.88rem;color:var(--muted)}
.key-once h2{margin-bottom:.5rem}
.week-chart text{font-size:13px}
.flow-cols>:only-child{grid-column:1/-1}
.flow-cols .card-head{flex-wrap:wrap}
.flow-cols .card-head .meta{flex-basis:100%;margin:0}
@media (min-width:600px){.flow-cols .card-head .meta{flex-basis:auto}}
.np.notice-full p{margin:.2rem 0 0;white-space:pre-line;color:var(--fg)}
dialog h3{font-size:.95rem;margin:1rem 0 .4rem}
ul.mini li.batch{font-weight:600}
.handoff-log ul.mini{margin-top:.4rem}
form[data-form="dev-ship-note"] textarea{min-height:5rem}
/* 1.38 [DESK-02][DESK-03] 운영 장부: 휴대폰은 카드 한 장(번호·요약 위, 단계·담당·기한 아래), 태블릿은 다섯 칸, 컴퓨터는 열 칸 표. */
.ledger-view .lg-tiles{grid-template-columns:repeat(auto-fit,minmax(min(9.5rem,100%),1fr))}
.lg-tools>summary{display:flex;flex-wrap:wrap;gap:.25rem .75rem;align-items:baseline;cursor:pointer}
.lg-tools .sort-top{margin-top:.75rem}
.lg-card .segmented{margin-bottom:.5rem}
.lg-bar{display:flex;flex-wrap:wrap;gap:.5rem;align-items:center;margin:.5rem 0}
.lg-bar input[type=search]{flex:1 1 14rem;min-width:0}
.lg-bar select{flex:0 0 auto;max-width:9rem}
.lg-hint{display:flex;flex-wrap:wrap;gap:.25rem .75rem;justify-content:space-between}
.lg-hint .meta{display:none}
.rows.ledger{margin:0}
/* 2.0.0 [RVA-24] 검토 HQ-03(조건 1): 줄마다 접는 칸(details)의 공통 위 여백·선(21px)이 장부 줄 사이에 끼지 않게. */
.rows.ledger>li>details{margin:0;border-top:0;padding-top:0}
.rows.ledger>li>details>summary{list-style:none}
.rows.ledger>li>details>summary::-webkit-details-marker{display:none}
.lg-row{display:grid;grid-template-columns:auto minmax(0,1fr) auto;grid-template-areas:"no sum due" "st st who";gap:.35rem .6rem;align-items:center;min-height:3.5rem;padding:.65rem .85rem}
.lg-row>span{min-width:0}
.rows.ledger .lg-row{position:relative;padding-right:1.4rem}
.rows.ledger .lg-row::after{position:absolute;right:.45rem;top:50%;transform:translateY(-50%);font-size:.75rem;color:var(--muted)}
.lg-row .c-no{grid-area:no;font-variant-numeric:tabular-nums;font-size:.8rem;color:var(--muted)}
.lg-row .c-sum{grid-area:sum;display:flex;flex-wrap:wrap;gap:.2rem .35rem;align-items:center}
.lg-row .c-sum>b{flex:1 1 100%;font-size:.95rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.lg-row .c-sum .sub{flex:1 1 100%;color:var(--muted);font-size:.75rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.lg-row .c-sum .pill{font-size:.7rem}
.lg-row .c-st{grid-area:st}
.lg-row .c-who{grid-area:who;justify-self:end}
.lg-row .c-due{grid-area:due;justify-self:end}
.lg-row .c-in,.lg-row .c-ov,.lg-row .c-bu,.lg-row .c-an,.lg-row .c-log{display:none}
.lg-row small{font-size:.75rem;color:var(--muted)}
.lg-row .kind{font-size:.75rem;font-weight:700}
.lg-row .who-claude,.feed .who-claude{color:var(--accent);font-weight:700}
.lg-pick{grid-area:no;display:flex;align-items:center}
.lg-table.picking .lg-row{grid-template-columns:auto auto minmax(0,1fr) auto;grid-template-areas:"pk no sum due" "pk st st who"}
.lg-table.picking .lg-row .lg-pick{grid-area:pk}
.lg-line.shut .lg-row .c-sum>b{color:var(--muted);font-weight:600}
.lg-line details[open]>.lg-row{background:var(--hover)}
.lg-detail{padding:.25rem .85rem .9rem;display:grid;gap:.5rem;border-top:1px dashed var(--line)}
.lg-detail .lg-text{margin:.5rem 0 0;padding:.5rem .75rem;border-left:3px solid var(--line-strong);background:var(--soft);border-radius:var(--r-sm);white-space:pre-wrap;overflow-wrap:anywhere}
.lg-detail .meta,.lg-detail p{margin:0}
.lg-detail .lg-block ol.mini{margin:.25rem 0 0;padding-left:1.25rem}
.lg-detail .lg-block small{display:block;color:var(--muted)}
.lg-detail .lg-sort{display:grid;gap:.4rem;padding:.6rem .75rem;border:1px solid var(--line);border-radius:var(--r-sm)}
.lg-detail .lg-sort textarea{width:100%}
.lg-head{display:none}
/* 2.0.0 [RVA-24] 검토 ADM-08·U23-05: 칸 수는 화면 폭이 아니라 표 자신의 폭(rem)으로 정해요. 노트북 폭(1024~1472)에서 왼쪽 메뉴를 뺀 표가
   아홉 칸보다 좁으면 다섯 칸으로 접혀 오른쪽 칸이 잘리지 않아요(가로 넘침 0). 글자가 커지면 같은 폭에서도 일찍 접혀요. */
.lg-table{container:lg/inline-size}
@media (min-width:600px){.lg-hint .meta{display:inline}}
@container lg (width >= 34rem){
  .lg-head{display:grid;padding:.45rem .85rem;font-size:.75rem;font-weight:700;color:var(--muted);background:var(--soft);border:1px solid var(--line);border-bottom:0;border-radius:var(--r-lg) var(--r-lg) 0 0}
  .lg-table .rows.ledger{border-radius:0 0 var(--r-lg) var(--r-lg)}
  .lg-head,.lg-row{grid-template-columns:4.2rem minmax(0,1fr) 7.5rem 6.5rem 6rem;grid-template-areas:none;gap:.5rem}
  .lg-head>span,.lg-row>span,.lg-row .c-no,.lg-row .c-sum,.lg-row .c-st,.lg-row .c-who,.lg-row .c-due,.lg-row .lg-pick{grid-area:auto}
  .lg-head .c-in,.lg-head .c-ov,.lg-head .c-bu,.lg-head .c-an,.lg-head .c-log{display:none}
  .lg-row .c-who,.lg-row .c-due{justify-self:start}
  .lg-table.picking .lg-head,.lg-table.picking .lg-row{grid-template-columns:1.5rem 4.2rem minmax(0,1fr) 7.5rem 6.5rem 6rem;grid-template-areas:none}
}
@container lg (width >= 66rem){
  .lg-head,.lg-row{grid-template-columns:4.2rem 5.5rem minmax(11rem,1fr) 5.5rem 6rem 5.5rem 6rem 6rem 7.5rem}
  .lg-head .c-in,.lg-head .c-bu,.lg-head .c-an,.lg-head .c-log,.lg-row .c-in,.lg-row .c-bu,.lg-row .c-an,.lg-row .c-log{display:block}
  .lg-row .c-log small{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;overflow-wrap:anywhere}
  .lg-table.picking .lg-head,.lg-table.picking .lg-row{grid-template-columns:1.5rem 4.2rem 5.5rem minmax(11rem,1fr) 5.5rem 6rem 5.5rem 6rem 6rem 7.5rem}
}
@container lg (width >= 74rem){
  .lg-head,.lg-row{grid-template-columns:4.2rem 5.5rem minmax(11rem,1.6fr) minmax(7rem,1fr) 5.5rem 6rem 5.5rem 6rem 6rem 7.5rem}
  .lg-head .c-ov,.lg-row .c-ov{display:block}
  .lg-row .c-ov small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  .lg-table.picking .lg-head,.lg-table.picking .lg-row{grid-template-columns:1.5rem 4.2rem 5.5rem minmax(11rem,1.6fr) minmax(7rem,1fr) 5.5rem 6rem 5.5rem 6rem 6rem 7.5rem}
}

/* ---- 1.38 [ADM3] 새 화면들과 갤럭시 폴드(접은 화면 ~344–412px, 편 화면 ~690–884px)·데스크톱 ---- */
.alert-list li>span{flex:1;min-width:0;padding:.5rem 0}
.alert-list li>span>small{display:block;margin-top:.1rem}.alert-list li.quiet::before{background:var(--faint)}
.banner.bad{background:var(--bad-bg);color:var(--bad)}
.incident-band{justify-content:flex-start;text-align:left;text-decoration:none;gap:.5rem}
.incident-band svg{flex:none;width:1.15rem;height:1.15rem}
.incident-band span{flex:1;min-width:0}
.incident-open{border-color:color-mix(in srgb,var(--bad) 45%,var(--line))}
.day-bars circle.mark{fill:var(--gold);stroke:var(--surface);stroke-width:1.5}
.key.mark{background:var(--gold);border-radius:50%}
.table-wrap{overflow-x:auto;margin:.25rem 0 .5rem;-webkit-overflow-scrolling:touch}
.grid-table{width:100%;border-collapse:collapse;font-size:.86rem;font-variant-numeric:tabular-nums}
.grid-table th,.grid-table td{padding:.45rem .55rem;border-bottom:1px solid var(--line);text-align:right;white-space:nowrap}
.grid-table th:first-child,.grid-table td:first-child{text-align:left;position:sticky;left:0;background:var(--surface)}
.grid-table thead th{font-size:.75rem;color:var(--muted);font-weight:800}
.grid-table tr.bad th,.grid-table tr.bad td{color:var(--bad)}
.grid-table input{width:6.5rem;min-height:2.5rem;text-align:right}
.stars{color:var(--gold);letter-spacing:.05em;font-size:1rem}
.stars .off{color:var(--line-strong)}
.review-list{display:grid;gap:.75rem}
.review-list>li{list-style:none}
.review-card{margin:0}
.review-card.low{border-color:color-mix(in srgb,var(--bad) 35%,var(--line))}
.review-card>p{margin:.35rem 0;white-space:pre-wrap;overflow-wrap:anywhere}
.review-card .bubble{display:block;max-width:none}
.review-card textarea{width:100%}
.launch-steps{list-style:none;margin:.5rem 0 0;padding:0;display:grid;gap:.25rem}
.launch-steps>li{display:flex;align-items:flex-start;gap:.75rem;padding:.75rem .5rem;border-radius:var(--r-sm)}
.launch-steps>li+li{border-top:1px solid var(--line)}
.launch-steps>li.next{background:var(--accent-soft)}
.launch-steps>li.done .step-no{background:var(--ok-bg);color:var(--ok)}
.launch-steps>li.done b,.launch-steps>li.skipped b{color:var(--muted)}
.launch-steps .step-main{flex:1;min-width:0}
.launch-steps .step-main p{margin:.2rem 0 0;font-size:.86rem}
.launch-steps .actions{margin-top:.5rem}
.chip.on{background:var(--accent-soft);color:var(--accent)}
a.chip{text-decoration:none;color:inherit;padding:.4rem .8rem}
.person h3{font-size:.86rem;margin:.75rem 0 .25rem}
.person .lines{margin:.5rem 0}
.person .chips{margin:.35rem 0}
.person .rows .row{padding:.5rem .25rem}
.person.skeleton{min-height:12rem}
.held-row{flex-wrap:wrap}
.held-row .row-main b{white-space:pre-wrap;font-weight:600}
.err-group{border-radius:var(--r-sm)}
.err-group>summary{list-style:none;cursor:pointer}
.err-group>summary::-webkit-details-marker{display:none}
.err-group .row-side{display:flex;align-items:center;gap:.5rem}
.err-sample{margin:.25rem .5rem .5rem;padding:.6rem .75rem;background:var(--soft);border-radius:var(--r-sm);font-size:.78rem;white-space:pre-wrap;overflow-wrap:anywhere;max-height:14rem;overflow:auto}
.story summary{cursor:pointer;display:flex;flex-wrap:wrap;gap:.25rem .75rem;align-items:baseline}
.timeline{list-style:none;margin:.75rem 0 0;padding:0 0 0 .5rem;border-left:2px solid var(--line);display:grid;gap:.6rem}
.timeline li{display:flex;align-items:flex-start;gap:.6rem;padding-left:.5rem}
.timeline li>span:nth-child(2){flex:1;min-width:0;display:grid}
.timeline small{color:var(--muted);font-size:.78rem}
.timeline .ev-ship .pill{background:var(--gold-soft);color:var(--gold)}
.timeline .ev-ask .pill{background:var(--info-bg);color:var(--info)}
.undo-link{font-size:.8rem;font-weight:800;margin-left:.35rem;white-space:nowrap}
.rollout-pick{margin:.25rem 0 .75rem}
.rollout-pick .chips{margin:.35rem 0 0}
.rows.wrap .row-main b,.person .rows .row-main b,.held-row .row-main b{white-space:normal;overflow:visible;text-overflow:clip}
/* 2.0.0 [RVQ-07] 좁은 폭·큰 글자에서 줄 끝 단추(끊기·지우기)가 글 칸을 눌러 글자가 한 자씩 꺾였어요: 글 칸이 12rem보다 좁아지면 단추를 아래 줄로. */
.rows.side-wrap .row{flex-wrap:wrap}.rows.side-wrap .row-main{flex:1 1 12rem}.rows.side-wrap .row-main b{white-space:normal;overflow:visible;text-overflow:clip;overflow-wrap:anywhere}
details.card>summary{display:flex;flex-wrap:wrap;gap:.25rem .75rem;align-items:baseline;cursor:pointer}

@media (max-width:719.98px){.flow-cols{grid-template-columns:minmax(0,1fr)}}
.tabs{overflow:hidden}
/* 접은 화면(좁은 폰): 아래 탭 다섯 칸이 잘리지 않게, 숫자 칸 두 줄, 표는 옆으로 밀어 보기 */
@media (max-width:420px){
  .tab .nav-label{font-size:.75rem;letter-spacing:-.02em}
  .tab svg{padding:.22rem min(.8rem,2.4vw)}
  .main{padding-left:.75rem;padding-right:.75rem}
  .filterbar,.pile-strip,.timebar{margin-left:-.75rem;margin-right:-.75rem;padding-left:.75rem;padding-right:.75rem}
  .card{padding:.85rem .85rem}
  .card-head{flex-wrap:wrap;gap:.35rem .5rem}
  .launch-steps>li{gap:.55rem;padding:.65rem .25rem}
  .held-row .row-side{width:100%;justify-content:flex-end}
  .segmented{overflow-x:auto;flex-wrap:nowrap;scrollbar-width:none}
  .segmented::-webkit-scrollbar{display:none}
  .grid-table input{width:5.25rem}
}
/* 편 화면(폴드 안쪽 가로 ~720–1023px): 흐름 칸 둘, 숫자 넷. 세로(~690px)는 한 칸이 더 읽기 좋아요. */
@media (min-width:720px) and (max-width:1023.98px){
  /* 2.0.0 [RVQ-28] 글자 200%에서는 두 칸이 너무 좁아 카드 안 줄이 한 자씩 섰어요(자동 순환 › 연결 점검): 칸이 22rem보다 좁으면 한 칸으로. */
  .flow-cols{grid-template-columns:repeat(auto-fit,minmax(min(100%,22rem),1fr));align-items:start}
  .tiles.core{--cols:4}
}
/* 2.0.0 [RVA-30] 검토 U24-02·T24-16·T24-17: 840에서 목록 칸이 170px라 제목이 "문제 신고 · …"만 보이고 사람 정보가 답 칸 아래로
   한참 내려갔어요. 840~1023은 목록을 접고 '‹ 이전 1/12 다음 ›'만(twoPane() 1024부터), 그 폭으로 답 칸과 사람 정보를 나란히 둬요. */
@media (min-width:840px) and (max-width:1023.98px){
  .cols{display:flex;flex-wrap:wrap;gap:.85rem;align-items:flex-start}.cols>*{min-width:0;flex:1 1 16rem}.cols>:first-child{flex:1.6 1 18rem}
  .cols>aside{position:sticky;top:calc(var(--top-h,4.25rem) + .5rem);max-height:calc(100dvh - var(--top-h,4.25rem) - 1.25rem);overflow:auto}
}
/* 데스크톱: 세 칸(메뉴 · 목록 · 열린 것), 넓으면 사람 패널이 오른쪽에 붙어 있어요. */
@media (min-width:1440px){
  .split .cols>:first-child{flex:1.7 1 18rem}.split .cols>:last-child{flex:1 1 19rem}
  .cols>aside{position:sticky;top:calc(var(--top-h,4.75rem) + .5rem);max-height:calc(100dvh - var(--top-h,4.75rem) - 1.25rem);overflow:auto}
}

/* 1.38 [ADM3-02] 휴대폰 장부 머리 접기: 숫자 칸은 아래 보기 칸과 같은 숫자라 숨기고, 보기·종류는 옆으로 미는 한 줄, 찾기 줄도 한 줄. 첫 화면에 줄이 3개 넘게 보여요. */
@media (max-width:599.98px){
  .ledger-view .lg-tiles{display:none}
  .ledger-view .segmented.wrap,.lg-card>nav.chips{flex-wrap:nowrap;overflow-x:auto;scrollbar-width:none;margin-left:-.25rem;margin-right:-.25rem;padding:0 .25rem}
  .ledger-view .segmented.wrap::-webkit-scrollbar,.lg-card>nav.chips::-webkit-scrollbar{display:none}
  .ledger-view .segmented.wrap .seg,.lg-card>nav.chips .chip{flex:0 0 auto;white-space:nowrap}
  .lg-card>nav.chips{margin-bottom:.5rem}
  .lg-bar{flex-wrap:wrap}.lg-bar input[type=search]{flex-basis:100%}
  .lg-bar input[type=search]{flex:1 1 auto}
  .lg-bar select{max-width:5.5rem}
  .lg-bar .btn{padding-left:.55rem;padding-right:.55rem;white-space:nowrap}
  .lg-hint{display:-webkit-box;-webkit-line-clamp:1;-webkit-box-orient:vertical;overflow:hidden;margin:.25rem 0 .5rem}
}
.stage-strip{display:flex;align-items:center;gap:.3rem;overflow-x:auto;scrollbar-width:none;margin:0 0 .75rem;padding:.15rem 0}
.stage-strip::-webkit-scrollbar{display:none}
.stage-strip a{flex:0 0 auto;display:inline-flex;align-items:baseline;gap:.35rem;padding:.35rem .7rem;border-radius:999px;background:var(--surface);border:1px solid var(--line);color:inherit;text-decoration:none;font-size:.8rem;font-weight:700}
.stage-strip a b{font-size:.92rem}
.stage-strip a.zero{color:var(--faint)}
.stage-strip a.next{border-color:var(--accent);background:var(--accent-soft)}
.stage-strip a.stuck{border-color:color-mix(in srgb,var(--bad) 45%,var(--line));color:var(--bad)}
.stage-strip .sep{color:var(--faint);flex:none}
/* 1.39 [STAT2-01] 숫자 › 한눈에. 한 줄 흐름(폴드 접힘 380) → 두 칸(펼침 840) → 네 칸(웹 1024+). 색은 모두 토큰이라 어두운 화면도 같아요. */
.glance{display:grid;gap:.9rem;font-variant-numeric:tabular-nums}
.glance .gl-k{display:block;font-size:.75rem;font-weight:800;letter-spacing:.04em;color:var(--muted)}
.glance small{display:block;font-size:.76rem;color:var(--muted);line-height:1.4}
.gl-hero{--tone:var(--ok);--tone-bg:var(--ok-bg);position:relative;overflow:hidden;border-radius:var(--r-lg);padding:1.1rem 1rem 1rem;border:1px solid color-mix(in srgb,var(--tone) 28%,var(--line));
  background:linear-gradient(160deg,color-mix(in srgb,var(--tone-bg) 92%,var(--surface)) 0%,var(--surface) 62%);box-shadow:var(--shadow)}
.gl-hero.warn{--tone:var(--warn);--tone-bg:var(--warn-bg)}.gl-hero.bad{--tone:var(--bad);--tone-bg:var(--bad-bg)}
.gl-hero.skeleton{min-height:14rem;background:var(--soft);border-color:var(--line)}
.gl-hero-top{display:flex;align-items:flex-start;gap:.85rem}
.gl-pulse{position:relative;flex:none;width:2.6rem;height:2.6rem;border-radius:50%;background:color-mix(in srgb,var(--tone) 16%,transparent);display:grid;place-items:center;margin-top:.15rem}
.gl-pulse span{width:.9rem;height:.9rem;border-radius:50%;background:var(--tone);box-shadow:0 0 0 4px color-mix(in srgb,var(--tone) 22%,transparent)}
.gl-pulse::after{content:"";position:absolute;inset:0;border-radius:50%;border:2px solid var(--tone);opacity:0;animation:gl-ping 2.4s ease-out infinite}
.gl-hero.ok .gl-pulse::after{animation-duration:3.6s}
@keyframes gl-ping{0%{transform:scale(.55);opacity:.55}80%,100%{transform:scale(1.25);opacity:0}}
.gl-hero-text{min-width:0}
.gl-eyebrow{margin:0;font-size:.75rem;font-weight:800;letter-spacing:.05em;color:var(--tone)}
.gl-hero h2{margin:.15rem 0 0;font-size:1.45rem;font-weight:900;letter-spacing:-.025em;line-height:1.2;display:flex;align-items:center;gap:.45rem;flex-wrap:wrap;word-break:keep-all;overflow-wrap:anywhere}
.gl-count{display:inline-grid;place-items:center;min-width:1.7rem;height:1.7rem;padding:0 .45rem;border-radius:999px;background:var(--tone);color:var(--surface);font-size:.9rem;font-weight:900}
.gl-sub{margin:.3rem 0 0;font-size:.88rem;color:var(--muted)}
.gl-signals{list-style:none;display:flex;flex-wrap:wrap;gap:.4rem;margin:.9rem 0 0;padding:0}
.gl-chip{display:inline-flex;align-items:center;gap:.4rem;min-height:2.25rem;padding:.35rem .75rem;border-radius:999px;font-size:.82rem;font-weight:700;text-decoration:none;
  color:var(--fg);background:var(--surface);border:1px solid var(--line);transition:border-color .15s,transform .15s}
.gl-chip::before{content:"";flex:none;width:.5rem;height:.5rem;border-radius:50%;background:var(--warn)}
.gl-chip.bad::before{background:var(--bad)}.gl-chip.bad{border-color:color-mix(in srgb,var(--bad) 40%,var(--line))}
@media (hover:hover){.gl-chip:hover{border-color:var(--tone);transform:translateY(-1px)}}
.gl-gauges{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.5rem;margin-top:1rem}
.gl-gauge{--g:var(--faint);display:flex;flex-direction:column;gap:.2rem;min-width:0;padding:.7rem .75rem;border-radius:var(--r);background:color-mix(in srgb,var(--surface) 88%,transparent);
  border:1px solid var(--line);color:inherit;text-decoration:none;transition:border-color .15s}
@media (hover:hover){a.gl-gauge:hover{border-color:var(--g)}}
.gl-gauge.ok{--g:var(--ok)}.gl-gauge.warn{--g:var(--warn)}.gl-gauge.bad{--g:var(--bad)}
.gl-gauge .gl-v{font-size:1.35rem;font-weight:900;letter-spacing:-.02em;line-height:1.15}
.gl-gauge.warn .gl-v,.gl-gauge.bad .gl-v{color:var(--g)}
.gl-meter{display:block;width:100%;height:6px;margin:.15rem 0;stroke:none}
.gl-meter .gl-track{fill:var(--soft)}.gl-meter .gl-fill{fill:var(--g)}
.gl-gauge .gl-d{font-size:.75rem;color:var(--muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.gl-early{border-radius:var(--r-lg);padding:1rem;background:var(--surface);border:1px dashed color-mix(in srgb,var(--info) 45%,var(--line))}
.gl-early-head{display:flex;align-items:flex-start;gap:.6rem}.gl-early-head p{margin:0;font-size:.86rem;color:var(--muted)}
.gl-early-grid{display:grid;grid-template-columns:minmax(0,1fr);gap:.5rem;margin-top:.8rem}
.gl-early-grid>*{display:flex;flex-direction:column;gap:.15rem;padding:.75rem .85rem;border-radius:var(--r);background:var(--soft);color:inherit;text-decoration:none}
.gl-early-grid b{font-size:1.4rem;font-weight:900;letter-spacing:-.02em}
@media (hover:hover){.gl-early-grid a:hover{background:var(--hover)}}
.gl-flow{padding-bottom:1.1rem}
.gl-off{margin:0 0 .75rem}
.gl-steps{list-style:none;margin:0;padding:0;display:grid;grid-template-columns:minmax(0,1fr);gap:.6rem;counter-reset:gl}
.gl-step{--s:var(--accent);position:relative;display:flex;flex-direction:column;gap:.2rem;min-width:0;padding:.85rem .9rem .8rem;border-radius:var(--r);background:var(--soft);
  border-top:3px solid var(--s)}
.gl-step.keep{--s:var(--ok)}.gl-step.out{--s:var(--bad)}.gl-step.back{--s:var(--gold)}
.gl-step>b{font-size:1.75rem;font-weight:900;letter-spacing:-.03em;line-height:1.1;color:var(--fg)}
.gl-step .spark polyline{stroke:var(--s)}.gl-step .spark circle{fill:var(--s)}
.gl-rings{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:.35rem;margin-top:.35rem}
.gl-ring{display:flex;flex-direction:column;align-items:center;text-align:center;gap:.05rem;position:relative}
.gl-ring svg{width:4.1rem;height:4.1rem;display:block}
.gl-ring circle{fill:none;stroke-width:4.5}.gl-ring .gl-track{stroke:color-mix(in srgb,var(--ok) 14%,var(--surface))}.gl-ring .gl-fill{stroke:var(--ok);stroke-linecap:round}
.gl-ring b{position:absolute;top:1.5rem;left:0;right:0;font-size:.86rem;font-weight:900}
.gl-ring span{font-size:.75rem;font-weight:800;color:var(--fg);margin-top:.15rem}
.gl-ring small{font-size:.75rem}
.gl-active{margin-top:1rem;padding-top:.9rem;border-top:1px solid var(--line)}
.gl-active-head{display:flex;align-items:baseline;justify-content:space-between;gap:.75rem}
.gl-active-head b{font-size:1.5rem;font-weight:900;letter-spacing:-.02em;display:flex;align-items:baseline;gap:.3rem}
.gl-active-head b small{display:inline;font-weight:700}
.gl-more{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.5rem}
.gl-more a{display:flex;flex-direction:column;gap:.15rem;padding:.8rem .9rem;border-radius:var(--r);background:var(--surface);border:1px solid var(--line);color:inherit;text-decoration:none;
  transition:border-color .15s,transform .15s}
.gl-more a b{font-size:.92rem;font-weight:800}
.gl-more a b::after{content:" ›";color:var(--faint)}
@media (hover:hover){.gl-more a:hover{border-color:var(--accent);transform:translateY(-1px)}}
.ob-cols{display:flex;flex-direction:column;gap:.9rem;min-width:0}.ob-main,.ob-side{display:contents}.ob-signals{order:-1}.ob-gaps{order:9}

.ob-tiles{display:grid;grid-template-columns:minmax(0,1fr);gap:.6rem}
.ob-tile{--s:var(--accent);position:relative;display:flex;flex-direction:column;gap:.2rem;min-width:0;padding:.85rem .9rem .7rem;border-radius:var(--r);background:var(--soft);border-top:3px solid var(--s)}
.ob-tile.out{--s:var(--bad)}.ob-tile.out.plus{--s:var(--ok)}.ob-tile.keep{--s:var(--ok)}.ob-tile.ver{--s:var(--info)}.ob-tile.star{--s:var(--gold)}
.ob-tile.need{border-top-style:dashed;--s:var(--faint)}.ob-tile.need small{color:var(--fg)}.ob-tile.wide{grid-column:1/-1}.ob-tile.wide>b{font-size:1.2rem}.ob-tile.voice{--s:var(--accent)}
.ob-cap{font-size:.75rem!important;color:var(--faint)!important}
.ob-tile>b{font-size:1.65rem;font-weight:900;letter-spacing:-.03em;line-height:1.1}.ob-tile>b small{display:inline;font-size:.82rem;font-weight:700;margin-left:.15rem}
.ob-tile .spark polyline{stroke:var(--s)}.ob-tile .spark circle{fill:var(--s)}
.ob-src{margin-top:auto;padding-top:.45rem;font-size:.75rem;font-weight:700;color:var(--faint);letter-spacing:.02em}
.ob-sub{margin-top:.5rem;padding-top:.5rem;border-top:1px solid var(--line);display:grid;gap:.1rem}.ob-sub b{font-size:1.15rem;font-weight:900}
.ob-sources,.ob-vers{list-style:none;margin:.45rem 0 0;padding:0;display:grid;gap:.25rem;font-size:.8rem}
.ob-sources li{display:grid;grid-template-columns:minmax(0,1fr) auto auto;gap:.5rem;align-items:baseline}.ob-sources li span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.ob-sources li small{display:inline;font-size:.7rem}
.ob-vers li{--g:var(--info);display:grid;grid-template-columns:3.2rem minmax(0,1fr) 3.2rem;gap:.45rem;align-items:center}.ob-vers li b{text-align:right;font-size:.78rem}
.ob-vers li:not(.top){--g:var(--faint)}.ob-vers .gl-meter{margin:0}
.ob-signal-list,.ob-gap-list,.ob-better ul{list-style:none;margin:0;padding:0;display:grid;gap:.55rem}
.ob-signal{--t:var(--warn);padding:.75rem .85rem;border-radius:var(--r);background:var(--soft);border-left:3px solid var(--t)}.ob-signal.bad{--t:var(--bad)}
.ob-signal-head{display:flex;flex-wrap:wrap;align-items:center;gap:.4rem}.ob-signal-head a{flex:1 1 12rem;font-weight:800;color:inherit;text-decoration:none;line-height:1.35}
@media (hover:hover){.ob-signal-head a:hover{text-decoration:underline}}
.ob-signal p{margin:.35rem 0 0;font-size:.82rem;color:var(--muted);line-height:1.45}.ob-signal .ob-metric{color:var(--fg)}
.ob-signal .actions{margin-top:.55rem;flex-wrap:wrap;gap:.35rem}
.ob-better{margin-top:.75rem}.ob-better summary{cursor:pointer;font-weight:800;font-size:.86rem;color:var(--ok);min-height:2.25rem;display:flex;align-items:center}
.ob-better li{display:grid;gap:.1rem;padding:.55rem .7rem;border-radius:var(--r);background:var(--ok-bg)}
.ob-gap-list li{display:grid;gap:.15rem;padding:.65rem .8rem;border-radius:var(--r);border:1px dashed var(--line)}.ob-gap-list li.policy{border-color:color-mix(in srgb,var(--info) 45%,var(--line))}
.ob-gap-list li b{font-size:.88rem;line-height:1.4}.ob-gap-list li .pill{justify-self:start;margin-top:.2rem}
@media (min-width:600px){.ob-tiles{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media (min-width:1100px){.ob-cols{display:grid;grid-template-columns:minmax(0,1.75fr) minmax(19rem,1fr);align-items:start}.ob-main,.ob-side{display:grid;gap:.9rem;align-content:start;min-width:0}.ob-signals,.ob-gaps{order:0}.ob-tiles{grid-template-columns:repeat(3,minmax(0,1fr))}}
@media (min-width:1500px){.ob-tiles{grid-template-columns:repeat(5,minmax(0,1fr))}}
@media (min-width:600px){
  .gl-hero{padding:1.4rem 1.4rem 1.25rem}
  .gl-hero h2{font-size:1.75rem}
  .gl-gauges{grid-template-columns:repeat(4,minmax(0,1fr))}
  .gl-early-grid{grid-template-columns:repeat(3,minmax(0,1fr))}
  .gl-steps{grid-template-columns:repeat(2,minmax(0,1fr))}
  .gl-more{grid-template-columns:repeat(3,minmax(0,1fr))}
}
@media (min-width:1024px){
  .gl-hero{padding:1.6rem 1.75rem 1.5rem}
  .gl-hero h2{font-size:2rem}
  .gl-pulse{width:3rem;height:3rem}
  .gl-gauge .gl-v{font-size:1.6rem}
  .gl-steps{grid-template-columns:minmax(0,.8fr) minmax(0,1.6fr) minmax(0,.8fr) minmax(0,1fr);gap:1.1rem}
  .gl-step:not(:last-child)::after{content:"›";position:absolute;right:-.85rem;top:50%;transform:translateY(-50%);color:var(--faint);font-size:1.1rem;font-weight:900}
  .gl-more{grid-template-columns:repeat(5,minmax(0,1fr))}
}
@media (max-width:599.98px){.gl-steps{grid-template-columns:repeat(2,minmax(0,1fr));grid-auto-flow:row dense}.gl-step.keep,.gl-step.back{grid-column:1/-1}.gl-step>b{font-size:1.5rem}}
@media (max-width:399.98px){.gl-ring svg{width:3.5rem;height:3.5rem}.gl-ring b{top:1.27rem;font-size:.8rem}.gl-hero h2{font-size:1.3rem}}
.gl-trend{display:inline-flex;align-items:center;gap:.15rem;margin-left:.35rem;padding:.05rem .4rem;border-radius:999px;font-size:.7rem;font-weight:800;letter-spacing:0;vertical-align:1px}
.gl-trend.up{background:var(--ok-bg);color:var(--ok)}.gl-trend.down{background:var(--bad-bg);color:var(--bad)}.gl-trend.flat{background:var(--soft);color:var(--muted)}
.gl-cohort-wrap{margin-top:1rem;padding-top:.9rem;border-top:1px solid var(--line)}
/* 1.39 7차 PC 6장 2: Claude 추천이 아직 없을 때 한 줄 */
.claude-draft-empty{display:flex;flex-wrap:wrap;align-items:center;gap:.4rem;margin:.2rem 0 .6rem}
/* 1.39 머리띠의 지금 도는 서버 판(사용자 11-08): 한 줄, 좁으면 가로로 밀어 봐요 */
/* 2.0.0 [RVQ-14] 총괄 사진 검토(840·200%에서 오른쪽이 잘림): 다 들어가지 않는 폭(글자 크기와 함께 rem으로 잼)에서는 커밋·배포 시각을 접고
   판만 한 줄로(자세한 것은 눌러 가는 서버 상태 맨 위 줄과 읽어 주는 이름에), 그래도 좁으면 서버 덩이째 줄을 바꿔요. 위에 붙어 있는 띠라 높이를 아껴요. */
.deploy-wrap{container-type:inline-size;container-name:deploy}
/* 2.0.0 [RVQ-16] 검토 U21-08: 되살리기 연습의 표마다 줄 수. 표가 아니라 줄바꿈 되는 목록이라 좁은 폭·큰 글자에서도 글자가 서지 않아요. */
/* 2.0.0 [RVQ-17] 검토 U21-07: 데이터 요청 줄. 단추는 가로로(좁으면 글 아래로), 대신 한 증빙은 줄 안쪽에 들여서. */
.req-rows .req-side{flex-direction:row;flex-wrap:wrap;justify-content:flex-end;align-items:center;max-width:55%}
/* 2.0.0 [RVQ-17][RVQ-18] 총괄 사진 검토(380·200%에서 글이 두세 글자씩 세로로 섬): 칸이 좁거나 글자가 크면(rem이라 글자를 키우면 함께 커져요)
   상태·단추는 글 아래로 내리고, 제목은 자르지 않고 줄을 바꿔요. 폭이 아니라 이 목록의 너비로 봐요. */
.req-rows,.security-rows{container-type:inline-size}
.req-rows .row-main b,.security-rows .row-main b{white-space:normal;overflow-wrap:anywhere}
@container (max-width:34rem){.req-rows .row,.security-rows .row{flex-wrap:wrap;row-gap:.45rem}.req-rows .row-main,.security-rows .row-main{flex:1 1 100%}
.req-rows .req-side,.security-rows .row-side{max-width:none;width:100%;flex-direction:row;flex-wrap:wrap;justify-content:flex-start;align-items:center}}
.card-head.wraps{flex-wrap:wrap;row-gap:.25rem}.card-head.wraps h2{flex:0 1 auto;min-width:0}.card-head.wraps .meta{flex:1 1 12rem}
.req-rows ul.proof{margin:-.25rem 1rem .75rem;padding:.35rem .7rem;border-left:3px solid var(--line-strong);font-size:.8rem;color:var(--muted)}
.drill-rows{list-style:none;margin:.4rem 0 0;padding:0;display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,16rem),1fr));gap:.15rem 1rem;font-variant-numeric:tabular-nums}
.drill-rows li{display:flex;flex-wrap:wrap;gap:0 .5rem;padding:.25rem 0;border-bottom:1px solid var(--line);min-width:0}
.drill-rows li b{font-weight:600;overflow-wrap:anywhere}.drill-rows li span{color:var(--muted)}.drill-rows li.bad span{color:var(--bad)}
@container deploy (max-width:36rem){.deploy-line .dl-part small,.deploy-line .dl-part code{display:none}}
.deploy-line{display:flex;flex-wrap:wrap;align-items:center;gap:.15rem .6rem;min-height:28px;padding:.2rem 1rem;font-size:.75rem;color:var(--muted);text-decoration:none;border-bottom:1px solid var(--line)}
.deploy-line[hidden]{display:none}
.deploy-line .dl-tag{font-weight:700;color:var(--accent)}.deploy-line .dl-tag.local{color:var(--muted)}
.deploy-line .dl-part{display:inline-flex;flex-wrap:wrap;align-items:baseline;gap:0 .3rem;min-width:0}
.deploy-line .dl-part small,.deploy-line .dl-part code,.deploy-line .dl-part b{white-space:nowrap}
.deploy-line .dl-part b{color:var(--fg);font-weight:700}
.deploy-line .dl-part.bad b{color:var(--bad)}
.deploy-line code{font-size:.75rem;padding:0 .25rem;border-radius:4px;background:var(--surface)}
.deploy-line small{font-size:.7rem}
.deploy-line .pill{font-size:.75rem}
@media (max-width:600px){.deploy-line{gap:.5rem;padding:.15rem .75rem}.deploy-line .dl-tag:not(.local),.deploy-line small{display:none}}
/* 8차 PC 1-21: 커밋이 없는(로컬) 서버는 좁은 폭에서도 '로컬 서버'·'로컬'이 보여요 */
.deploy-line code.local{color:var(--muted);font-style:normal}
/* 1.39 [OBS2-09] 관측 빈틈: 할 일만 펼치고 기다리는 것·정한 것은 접기 */
.ob-gap-rest{margin-top:.6rem;border-top:1px solid var(--line);padding-top:.5rem}
.ob-gap-rest summary{cursor:pointer;font-size:.84rem;font-weight:700;color:var(--muted);min-height:44px;display:flex;align-items:center}
.ob-gap-list li.wait b,.ob-gap-list li.decided b{color:var(--muted)}
.ob-vers li small{grid-column:1/-1;font-size:.7rem;color:var(--muted)}
/* 1.39 [OBS2-08] 유형별 흐름·기능과 머묾: 주마다 머묾 표와 같은 모양 */
.ob-kind-table td small{display:block;font-size:.75rem;font-weight:600;color:var(--muted)}
.ob-kind-table tr.ob-all-row th,.ob-kind-table tr.ob-all-row td{border-top:2px solid var(--line)}
.ob-keep-table td.up{color:var(--ok)}
.ob-keep-table td.down{color:var(--bad)}
.ob-keep-table td.none{color:var(--faint);font-weight:600}
.gl-cohort-wrap .gl-active-head{flex-wrap:wrap;align-items:baseline}
.gl-cohort{width:100%;border-collapse:separate;border-spacing:3px;margin-top:.5rem;font-size:.84rem}
.gl-cohort th{font-size:.75rem;font-weight:800;color:var(--muted);text-align:left;padding:.3rem .45rem}
.gl-cohort tbody th{color:var(--fg);white-space:nowrap}
.gl-cohort td{text-align:center;padding:.45rem .4rem;border-radius:8px;font-weight:800;background:var(--soft)}
.gl-cohort td.none{color:var(--faint);font-weight:600}
.gl-cohort td.h0{color:var(--muted)}
.gl-cohort td.h1{background:color-mix(in srgb,var(--ok) 12%,var(--surface))}
.gl-cohort td.h2{background:color-mix(in srgb,var(--ok) 24%,var(--surface))}
.gl-cohort td.h3{background:color-mix(in srgb,var(--ok) 38%,var(--surface))}
.gl-cohort td.h4{background:color-mix(in srgb,var(--ok) 55%,var(--surface));color:var(--fg)}
@media (max-width:599.98px){
  .gl-cohort thead{display:none}
  .gl-cohort,.gl-cohort tbody{display:block}
  .gl-cohort tr{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:3px;margin-bottom:.5rem}
  .gl-cohort tbody th{grid-column:1/-1;padding:.2rem 0}
  .gl-cohort td{display:flex;flex-direction:column;gap:.1rem;font-size:.86rem}
  .gl-cohort td::before{content:attr(data-label);font-size:.75rem;font-weight:700;color:var(--muted)}
}
/* 1.39 [ADM3-45] 같은 회사일까요? 짝 카드: 넓으면 두 이름이 한 줄, 접힌 폭이면 위아래. */
.similar-rows li{display:flex;flex-direction:column;gap:.35rem;padding:.75rem 0;border-top:1px solid var(--line)}
.similar-rows li:first-child{border-top:0}
.similar-rows .pair{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);gap:.5rem;align-items:center}
.similar-rows .pair a{display:flex;flex-direction:column;gap:.1rem;padding:.55rem .7rem;border-radius:var(--r-sm);background:var(--soft);color:inherit;text-decoration:none;min-width:0}
.similar-rows .pair a b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.similar-rows .pair a:last-child{background:var(--ok-bg)}
.similar-rows .arrow{color:var(--faint);font-weight:900}
.similar-rows .why{color:var(--muted);font-size:.78rem}
@media (max-width:599.98px){.similar-rows .pair{grid-template-columns:minmax(0,1fr)}.similar-rows .arrow{transform:rotate(90deg);justify-self:center}}
/* 1.39 [ADM3-46] 여러 판 카드 맨 위 한 줄: 무엇이 다르고 어느 판을 왜 추천하는지. */
.vrec{margin:.1rem 0 .6rem;padding:.55rem .75rem;border-radius:var(--r-sm);background:var(--gold-soft);font-size:.86rem;line-height:1.5}
.vrec.none{background:var(--soft);color:var(--muted)}.vrec.none b{color:var(--fg)}
/* 1.39 [ADM3-47] 오늘 첫 화면: 상태 한 문장 + 지금 할 3가지(숫자 › 한눈에의 큰 신호와 같은 모양). */
.today-hero{margin:0 0 .6rem}
.today-three{list-style:none;margin:.9rem 0 0;padding:0;display:grid;grid-template-columns:minmax(0,1fr);gap:.4rem;counter-reset:none}.today-three li{min-width:0}
.today-three a{display:flex;align-items:center;gap:.65rem;padding:.65rem .75rem;border-radius:var(--r);background:var(--surface);border:1px solid var(--line);color:inherit;text-decoration:none;
  transition:border-color .15s,transform .15s}
@media (hover:hover){.today-three a:hover{border-color:var(--tone);transform:translateY(-1px)}}
.today-three .n{flex:none;display:grid;place-items:center;width:1.7rem;height:1.7rem;border-radius:50%;background:var(--accent-soft);color:var(--accent);font-weight:900;font-size:.85rem}
.today-why{color:var(--fg);font-weight:700}
.today-three li.bad .n{background:var(--bad-bg);color:var(--bad)}.today-three li.warn .n{background:var(--warn-bg);color:var(--warn)}
.today-three li.todo .n{background:var(--accent-soft);color:var(--accent)}
.today-three .t{flex:1;min-width:0;display:flex;flex-direction:column;gap:.1rem}
.today-three .t b{font-size:.92rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.today-three .t small{font-size:.76rem;color:var(--muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.today-three .t .sub{display:flex;align-items:center;gap:.4rem;min-width:0}.today-three .t .sub>*{flex:none}.today-three .t .sub small{flex:1;min-width:0}
.today-three svg{flex:none;width:1.1rem;height:1.1rem;color:var(--faint)}
.today-rest{margin:.6rem 0 0;font-size:.8rem;color:var(--muted)}
.shown-above{margin:.35rem 0 0}.shown-above>summary{cursor:pointer;list-style:none;font-size:.82rem;font-weight:700;color:var(--muted);padding:.35rem .1rem}
.shown-above>summary::before{content:"▸ ";color:var(--faint)}.shown-above[open]>summary::before{content:"▾ "}
.shown-above .alert-list,.shown-above .rows{opacity:.85}
.claude-draft{margin:.5rem 0;padding:.6rem .75rem;border-radius:var(--r);background:var(--accent-soft);border:1px solid var(--line)}
.claude-draft p{margin:.15rem 0}.claude-draft .why{font-size:.88rem}.claude-draft .meta a{color:var(--accent)}
.hello-who{margin:0 0 .75rem;font-size:.8rem;color:var(--muted)}
/* 2.0.0 [RVA-36] 검토 U23-01·I23c-01·ADM-12(도약 41번): 오늘은 '내가 할 것' 셋 + 저절로 끝난 일만 펴고, 나머지는 접힌 줄 하나씩.
   셋마다 [내일 볼게요] 단추(44px), 휴대폰은 줄을 밀어도 돼요(왼쪽 미루기·오른쪽 열기). */
.today-three li{display:flex;align-items:center;gap:.3rem;position:relative;touch-action:pan-y}
/* 도약 43번: 좁은 폭에서도 제목은 두 줄까지(8~10자에서 잘리던 것). */
.today-three .t b{white-space:normal;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;line-height:1.35}
.today-three li>a{flex:1;min-width:0;position:relative;z-index:1}
.today-three .pick-later{color:var(--muted);border-radius:var(--r)}
.today-three .pick-later svg{width:1.15rem;height:1.15rem;color:inherit}
@media (hover:hover){.today-three .pick-later:hover{background:var(--soft);color:var(--fg)}}
.today-three li.swiping>a{transform:translateX(var(--dx,0));transition:none}
.today-three li[data-dir]::after{position:absolute;top:50%;transform:translateY(-50%);z-index:0;font-size:.8rem;font-weight:800;color:var(--muted)}
.today-three li[data-dir=later]::after{content:"내일 볼게요";right:3.6rem}.today-three li[data-dir=open]::after{content:"열기";left:.9rem;color:var(--accent)}
.today-go{display:flex;flex-wrap:wrap;align-items:center;gap:.5rem .9rem;margin:.8rem 0 0}.today-go .today-rest{margin:0}
.today-go .btn{max-width:100%;white-space:normal;text-align:center;line-height:1.25}
/* 380·글자 200%(오늘 큰 카드 폭이 약 18rem): 셋의 제목이 0칸으로 접히던 것. 좁으면 번호·꺾쇠를 줄이고 맥박 점은 제목 줄 위로,
   [내일 볼게요]는 48px까지. 숫자 › 한눈에의 큰 신호도 같은 틀이라 함께예요(같은 구조는 함께). */
.gl-hero{container-type:inline-size;container-name:hero}
.today-three .pick-later{width:min(2.75rem,48px);height:min(2.75rem,48px)}
@container hero (width < 24rem){
  .gl-hero-top{flex-direction:column;gap:.4rem}
  .gl-pulse{width:min(2.6rem,40px);height:min(2.6rem,40px)}
  .today-three a{gap:.45rem;padding:.55rem .6rem}
  .today-three a>svg,.today-three .n{display:none}
  .today-three{margin-inline:-.45rem}.today-three li{gap:.15rem}
}
.home-more{display:grid;grid-template-columns:minmax(0,1fr);gap:.5rem;margin:.25rem 0 .75rem}
details.card.home-fold{margin:0;padding:0}
.home-fold>summary{display:flex;flex-wrap:nowrap;align-items:center;gap:.75rem;min-height:3.25rem;padding:.6rem 1.1rem;list-style:none}
.home-fold>summary::after{content:"";flex:none;width:.5rem;height:.5rem;border-right:2px solid var(--muted);border-bottom:2px solid var(--muted);transform:rotate(45deg);margin:0 .2rem}
.home-fold[open]>summary::after{transform:rotate(-135deg)}
.home-fold .hf-t{flex:1;min-width:0;display:flex;flex-direction:column;gap:.1rem}
.home-fold .hf-t b{font-size:.98rem}
.home-fold .hf-t small{font-size:min(.8rem,16px);font-weight:500;color:var(--muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.home-fold .hf-t .late{color:var(--bad);font-weight:800}
.home-fold .hf-body{padding:0 1.1rem 1rem;min-width:0}
.home-fold .hf-body>.card{border:0;box-shadow:none;background:none;padding:0;margin:.25rem 0 1rem;border-radius:0}
.home-fold .hf-body>.card.alerts{border-bottom:1px solid var(--line);padding-bottom:.75rem}
.hf-h{font-size:.9rem;margin:.9rem 0 .35rem}
.home-foot,.home-views{display:flex;flex-wrap:wrap;gap:.4rem;margin:0 0 .5rem}.home-views{margin:0}
.home-foot a,.home-views a{display:inline-flex;align-items:center;gap:.4rem;min-height:2.75rem;padding:0 .85rem;border-radius:var(--r-sm);background:var(--info-bg);color:var(--fg);text-decoration:none;font-size:.88rem;font-weight:700}
.home-foot svg{flex:none;width:1rem;height:1rem;color:var(--info)}
/* 2.0.0 [RVA-37] 검토 I24c-01: 읽지 못해 빼 둔 줄. 안내 카드 한 모양(바탕 하나, 왼쪽 띠 색으로 뜻): 주의예요(장부는 돌아요). */
.card.stuck{border-left:4px solid var(--warn)}
.stuck-list{list-style:none;margin:0;padding:0;display:grid;gap:.6rem}
.stuck-list p{margin:0 0 .35rem;overflow-wrap:anywhere}.stuck-list small{color:var(--muted);font-size:min(.8rem,16px)}
/* 2.0.0 [RVD-16] 디자인 품질(총괄 11-12): 설명은 한 줄 + 펼치기, 줄 안 칩은 글자 폭, 여러 개 고르기 줄(label.row: 보상·문의·개발사항)의
   글이 label의 굵은 글씨를 물려받지 않아요. */
label.row{font-weight:inherit;font-size:inherit;margin:0}
details.tip{margin:0 0 .5rem;border-top:0;padding-top:0}
details.tip>summary{font-weight:650;color:var(--muted)}
details.tip>.hint{margin:.25rem 0 .5rem}
@media (pointer:coarse){details.tip>summary{min-height:2.75rem;display:flex;align-items:center;gap:.25rem}}
.row-main>.pill{align-self:flex-start}
.row-main .row-tags{display:flex;flex-wrap:wrap;gap:.35rem;margin-top:.3rem}
/* 2.0.0 [RVD-16] 목록 머리 한 줄(조건 1, 관리자 목록 화면의 본보기 HQ-03): 구간 고르기 + 찾기·거르기·고르기·CSV. 좁은 폭은 단추 하나 뒤로.
   구간 고르기는 바깥 여백 없이 한 줄 높이(2.75rem)에, 큰 글자에서는 구간이 줄을 바꿔요(조건 12). */
.list-head{display:flex;flex-wrap:wrap;align-items:center;gap:.5rem;margin:0 0 .5rem;container:list-head/inline-size}
/* 2.0.0 [RVA-24] 380·글자 200%에서 보기 줄이 칸마다 한 줄씩 세로로 쌓였어요(검토·개발사항·보상). 늘 한 줄, 넘치면 옆으로 밀고 끝이 흐려져요. */
.list-head>.segmented{margin:0;padding:0;flex:0 1 auto;flex-wrap:nowrap;overflow-x:auto;scrollbar-width:none}
.list-head>.segmented::-webkit-scrollbar{display:none}
.list-head>.segmented .seg{flex:1 1 auto}
.list-head .list-more{display:none;position:relative}
.list-head .list-more.busy::after{content:"";position:absolute;top:.5rem;right:.5rem;width:.5rem;height:.5rem;border-radius:50%;background:var(--accent)}
.list-head .list-tools{flex:1 1 22rem;margin:0;flex-wrap:wrap;min-width:0}
.list-head .list-tools input[type=search]{flex:1 1 8rem;min-width:min(8rem,100%)}
/* 접는 기준은 머리 줄 자신의 폭을 rem으로 재요(총괄 11-12 3차): 글자가 커지면 같은 폭에서도 접혀, 찾기 칸이 눌려 잘리지 않아요(조건 12).
   한 줄에 다 들어가는 폭(구간 고르기 + 찾기 9rem + 종류 + 아이콘 둘, 약 40rem)보다 조금 넉넉히 42rem. */
@container list-head (width > 42rem){.list-head .btn.icon-wide{width:2.75rem;padding:0}.list-head .btn.icon-wide .lbl{display:none}}
@container list-head (width <= 42rem){.list-head>.segmented{flex:1 1 0;min-width:0}.list-head .list-more{display:inline-grid}
.list-head .list-tools{display:none;flex-basis:100%}.list-head.open .list-tools{display:flex}.list-head .list-tools select{flex:1 1 8rem}}
/* 2.0.0 [RVA-24] 거르기 칩 줄은 '거르기' 단추 뒤로 접어요(HQ-03 첫 줄을 화면 위 25% 안으로). 고른 조건 수는 단추 위 숫자, 좁은 폭은 '찾기·거르기' 하나로 같이 펴요. */
.list-head .filter-panel{flex-basis:100%;display:none;min-width:0}
.list-head.f-open .filter-panel{display:block}
.filter-panel .chips{margin:0 0 .5rem}.filter-panel .chips:last-child{margin-bottom:0}
.btn.filter-toggle{gap:.35rem}
.btn.filter-toggle.on{border-color:var(--accent);color:var(--accent)}
.btn.filter-toggle .n{min-width:1.25rem;padding:0 .3rem;border-radius:999px;background:var(--accent);color:var(--on-accent,#fff);font-size:.8rem;line-height:1.25rem;text-align:center;white-space:nowrap}
.list-meta{display:flex;flex-wrap:wrap;align-items:center;gap:.25rem .5rem}
.list-meta .link{min-height:0}
.filter-panel .filter-row{display:flex;flex-wrap:wrap;align-items:center;gap:.5rem;margin:0 0 .5rem}
.filter-panel .filter-row label{font-weight:700}
.filter-panel .filter-row select{flex:0 1 16rem;min-width:0}
.filter-panel .filter-row input[type=date]{flex:0 1 11rem;min-width:0}
/* 2.0.0 [RVA-24] 검토 U24-06(조건 2·9): 줄 단추는 주된 것 하나 + ⋯ 메뉴(나머지). 메뉴는 줄 오른쪽 아래에 떠요. */
.row-acts{flex-direction:row;align-items:center;gap:.35rem}
details.row-more{position:relative;margin:0;border:0;padding:0}
details.row-more>summary{list-style:none;cursor:pointer}
details.row-more>summary::-webkit-details-marker{display:none}
details.row-more>summary::after,details.row-more[open]>summary::after{content:none}
details.row-more[open]>summary{background:var(--hover)}
details.row-more>.menu{position:absolute;right:0;top:calc(100% + .25rem);z-index:4;transform:translateX(var(--menu-dx,0));display:grid;gap:.25rem;min-width:11rem;max-width:min(18rem,80vw);padding:.4rem;
  border:1px solid var(--line);border-radius:var(--r-sm);background:var(--surface);box-shadow:var(--shadow-lg)}
details.row-more>.menu .btn{justify-content:flex-start;width:100%;border-color:transparent;background:none}
@media (hover:hover){details.row-more>.menu .btn:hover{background:var(--hover)}}
.rows:has(details.row-more[open]){overflow:visible}
@container rows (width < 30rem){details.row-more>.menu{left:0;right:auto}}
/* 2.0.0 [RVA-25] 검토 U24-07: 카드의 왼쪽 맞춤 단추 줄에서는 ⋯가 왼쪽에 있어 메뉴를 오른쪽으로 펴요(화면 밖으로 나가지 않게). */
.actions.start details.row-more>.menu{left:0;right:auto}
.list-note{margin:.5rem 0 .75rem}
.list-head .lg-bar{display:flex;flex:1 1 8rem;min-width:min(8rem,100%);margin:0}.list-head .lg-bar input[type=search]{flex:1 1 auto;min-width:0}
.seg .n.hot{color:var(--bad)}
.ledger-view>.stage-strip{margin:.75rem 0}
/* 2.0.0 [RVA-25] 검토 I24a-01·U24-01·U24-03·T24-03·T24-04·T24-05·T24-06·ADMX-10: 좁은 목록의 줄 틀 하나. 목록 자신의 폭이 30rem보다 좁으면
   (폴드 접은 폭, 큰 글자) 위에 제목 2줄, 아래에 배지·시간·단추 한 줄이에요. 제목이 8~10자에서 잘리거나 오른쪽 칸이 화면 밖으로 나가지 않아요. */
#main ul.rows{container:rows/inline-size}
@container rows (width < 30rem){
  .row{flex-wrap:wrap;row-gap:.35rem}
  .row>.row-main{flex:1 1 0;min-width:0}
  .row>svg{order:2}
  .row>.row-side{order:3;flex:1 1 100%;flex-direction:row;flex-wrap:wrap;align-items:center;justify-content:flex-start;gap:.3rem .45rem;max-width:none}
  .row-main>b{white-space:normal;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;overflow-wrap:anywhere}
  /* 오늘 할 일 줄(.rows.inbox)은 넓은 화면의 한 줄 규칙이 더 세서 840·200%에서 내일 볼게요가 잘리고 제목이 한 줄로 잘렸어요. */
  .rows.inbox .row>.row-side{flex-wrap:wrap;justify-content:flex-start}.rows.inbox .row-main>b{white-space:normal}
}
@container lg (width < 34rem){.lg-row .c-sum>b{white-space:normal;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;overflow-wrap:anywhere}}
/* 결정함: 넓은 화면에서도 줄이 화면 끝까지 늘어나지 않게(조건 12 '본문 최대 폭'), 초안은 줄 아래 한 칸. */
.decide-view{max-width:52rem}
.company-top input[type=search]{flex:1 1 8rem;min-width:0}
.btn.icon-only{width:2.75rem;padding:0;flex:none}
@media (max-width:599.98px){.company-top .btn.primary{width:2.75rem;padding:0;flex:none}.company-top .btn.primary .lbl{display:none}}
/* 2.0.0 [RVA-62] PC-158(조건 1): 좁은 폭은 표 머리 대신 찾기 옆 정렬 고르기 하나(첫 회사 줄이 화면 위 25% 안에서). 큰 글자에서는 다음 줄로 내려가요(조건 12). */
.company-top .sort-pick{display:none}
@media (max-width:599.98px){.company-top .sort-pick{display:block;flex:0 0 auto;width:auto;max-width:100%;padding-inline:.5rem 1.4rem;min-height:min(2.75rem,48px)}.company-top input[type=search]{flex-basis:min(6.5rem,40vw);min-width:min(6.5rem,40vw)}.toolbar.list-top.company-top{flex-wrap:wrap}table.list.companies thead{display:none}}
@media (max-width:599.98px){.timebar .meta{display:none}}
.decide-rows .draft-line{padding:0 1rem .75rem;display:grid;gap:.35rem}
.decide-rows .draft-line .answer{margin:0;padding:.5rem .7rem;border-left:3px solid var(--line-strong);background:var(--soft);border-radius:var(--r-sm);white-space:pre-wrap;overflow-wrap:anywhere}
.decide-rows .draft-line .actions{margin:0}
.toolbar.list-top{flex-wrap:nowrap}.toolbar.list-top .grow{flex:1 1 auto;min-width:0}
.day-head.bad{color:var(--bad)}
.list-head + h2.section,.list-head + h2.day-head,.list-head + .note + h2.day-head{margin-top:.25rem}
/* 2.0.0 [RVA-62] PC-158(조건 1): 380×800 기능 관리의 첫 줄이 25.4%. 목록 머리 바로 밑 묶음 제목의 위아래 틈을 줄여요(모든 목록 같은 값). */
.list-head{margin-bottom:.35rem}
.list-head + h2.day-head{margin-top:.1rem;margin-bottom:.3rem}
/* 보기가 많은 화면: 구간 줄은 옆으로 밀리고(줄 바꿈 없음) 남은 폭을 다 써요. 끝 흐림은 허브 탭 줄과 같아요. */
.list-head.seg-scroll>.segmented{flex:1 1 12rem;min-width:0;flex-wrap:nowrap;overflow-x:auto;scrollbar-width:none}
.list-head.seg-scroll>.segmented .seg{flex:0 0 auto}
.list-head.seg-scroll .list-tools{flex:0 1 20rem}
@container list-head (width > 42rem){.list-head.seg-scroll .list-tools{flex-wrap:nowrap}.list-head.seg-scroll .filter-toggle>span:not(.n){display:none}}
.segmented[data-more=right]{-webkit-mask-image:linear-gradient(90deg,#000 calc(100% - min(2rem,28px)),transparent);mask-image:linear-gradient(90deg,#000 calc(100% - min(2rem,28px)),transparent)}
.segmented[data-more=left]{-webkit-mask-image:linear-gradient(90deg,transparent,#000 min(2rem,28px));mask-image:linear-gradient(90deg,transparent,#000 min(2rem,28px))}
.segmented[data-more=both]{-webkit-mask-image:linear-gradient(90deg,transparent,#000 min(2rem,28px),#000 calc(100% - min(2rem,28px)),transparent);mask-image:linear-gradient(90deg,transparent,#000 min(2rem,28px),#000 calc(100% - min(2rem,28px)),transparent)}
/* 큰 글자에서 구간 고르기가 한 칸에 88px까지 커지지 않게 52px에서 멈춰요(누르는 곳 48px은 지킴, 조건 9·12). */
.seg{min-height:min(2.6rem,52px)}@media (pointer:coarse){.seg{min-height:min(2.75rem,52px)}}
@container list-head (width <= 42rem){.list-head .filter-toggle{display:none}.list-head.open .filter-panel{display:block}.list-head.seg-scroll>.segmented{flex-basis:0}}
.list-foot{margin:.25rem 0 .75rem}.list-foot .claude-draft-empty{margin:0 0 .25rem}
/* 2.0.0 [RVD-16] 고르기 막대의 할 일 두 단추는 함께, 좁은 폭에서 줄이 바뀌면 오른쪽에. */
.bulkbar .bulk-acts{display:flex;gap:.5rem;flex-wrap:nowrap}
@media (max-width:599.98px){.bulkbar .bulk-acts{margin-left:auto}}
/* ---- 2.0.0 갈래 Q2 2/3 화면 배치 ---- */
/* [RVA-18] 검토 HQ-26·HQ-02(조건 12): 위·아래 막대의 아이콘·배지는 글자를 키워도 48px·알맞은 크기에서 멈추고(누르는 곳은 그대로 48px),
   제목은 줄이지 않고 두 줄까지, 아래 막대 이름은 잘리지 않고 줄을 바꿔요. 380 폭·글자 200%에서 옆으로 넘치지 않아요. */
.bar{gap:min(.5rem,8px);min-height:min(3.75rem,60px)}
.bar .icon-btn{width:min(2.75rem,48px);height:min(2.75rem,48px)}
.bar .icon-btn svg{width:min(1.4rem,26px);height:min(1.4rem,26px)}
.bar .mark{width:min(2.1rem,36px);height:min(2.1rem,36px);margin:0 min(.35rem,6px)}
.bar h1{white-space:normal;overflow:visible;font-size:min(1.2rem,26px)}
.bar .sub{font-size:min(.78rem,15px)}
.bar h1 .t{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;line-height:1.2}
/* 2.0.0 [RVA-24] 3/3(총괄 11-12 오늘 380·200% 사진): 제목이 글자마다 줄을 바꿔 세로로 서지 않게(낱말 단위), 막대가 좁으면(글자 150% 넘게 키운 폰 폭)
   꾸밈 그림(달 표시)을 먼저 빼고 판 알약의 안쪽 여백도 알맞은 크기에서 멈춰요. */
.bar{container:bar/inline-size}
.bar h1 .t{word-break:keep-all;overflow-wrap:anywhere}
@container bar (width < 20rem){.bar .mark{display:none}}
@media (hover:none) and (pointer:coarse){.bar [data-act=shortcuts]{display:none}}
@media (max-width:599.98px){.bar{padding-left:min(.75rem,12px);padding-right:min(.5rem,8px)}}
.tab{font-size:min(.8rem,15px);padding:.25rem 2px;min-height:min(3.4rem,64px)}
.tab svg{width:min(1.45rem,26px);height:min(1.45rem,26px);padding:min(.25rem,5px) min(1.1rem,3vw)}
.tab .nav-label{white-space:normal;overflow:visible;text-overflow:clip;line-height:1.15;text-align:center;word-break:keep-all;overflow-wrap:anywhere}
@media (max-width:420px){.tab .nav-label{font-size:min(.75rem,14px);letter-spacing:-.01em}.tab svg{padding:min(.22rem,4px) min(.8rem,2.4vw)}}
.badge{min-width:min(1.25rem,22px);height:min(1.25rem,22px);line-height:min(1.25rem,22px);padding:0 min(.4rem,7px);font-size:min(.7rem,13px);border-radius:min(.65rem,11px)}
.tab .badge{left:calc(50% + min(.55rem,10px));top:min(.1rem,2px)}
.deploy-line{font-size:min(.75rem,15px)}
.deploy-line code,.deploy-line small,.deploy-line .pill{font-size:min(.75rem,14px)}
/* [RVA-19] 검토 ADMX-05·ADM-15·U23-07: 허브 탭 줄. 더 있으면 끝이 흐려지고, '모두'로 한눈에 펼쳐요. */
.hubrow{display:flex;align-items:center;max-width:80rem;margin:0 auto}
.hubrow[hidden]{display:none}
.hubrow .hubtabs{flex:1 1 auto;min-width:0;max-width:none;margin:0;padding-bottom:.25rem}
.hubtabs[data-more=right]{-webkit-mask-image:linear-gradient(90deg,#000 calc(100% - min(2.5rem,32px)),transparent);mask-image:linear-gradient(90deg,#000 calc(100% - min(2.5rem,32px)),transparent)}
.hubtabs[data-more=left]{-webkit-mask-image:linear-gradient(90deg,transparent,#000 min(2.5rem,32px));mask-image:linear-gradient(90deg,transparent,#000 min(2.5rem,32px))}
.hubtabs[data-more=both]{-webkit-mask-image:linear-gradient(90deg,transparent,#000 min(2.5rem,32px),#000 calc(100% - min(2.5rem,32px)),transparent);mask-image:linear-gradient(90deg,transparent,#000 min(2.5rem,32px),#000 calc(100% - min(2.5rem,32px)),transparent)}
.chip.hub-all{flex:none;gap:.2rem;margin:0 .6rem .25rem .25rem;font-size:.82rem;white-space:nowrap}
.chip.hub-all[hidden]{display:none}
.chip.pick[aria-current=page]{background:var(--accent-soft);border-color:var(--accent);color:var(--accent)}
a.chip.pick{color:var(--fg);text-decoration:none}
.chip .badge{margin-left:.35rem}
@media (min-width:600px){.chip.hub-all{margin-right:1.5rem}}
/* [RVA-20] 검토 ADM-16·ADM-26(조건 14): 빨간 배지 글자, 금색 알약, 가장 작은 글자의 대비. 고대비 모드에서도 초점이 보여요. */
:root{--on-bad:#fff;--gold-ink:#7A5A22}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--on-bad:#2B0D0A;--gold-ink:#D2B47A}}
:root[data-theme="dark"]{--on-bad:#2B0D0A;--gold-ink:#D2B47A}
/* 2.0.0 [RVA-35] 검토 HQ-65(조건 6): 메뉴·탭의 숫자는 기다리는 일의 수라 위험(빨강)이 아니에요. 파랑 바탕에 그 위 글자색. */
.badge{color:var(--on-accent)}
.pill.gold,.stars{color:var(--gold-ink)}
@media (forced-colors:active){:focus-visible{outline:2px solid Highlight}.badge{border:1px solid CanvasText}}
[hidden]{display:none!important}
/* [RVA-18] 검토 HQ-34: 두 자리 숫자 배지가 좁은 칸에서 '1' 아래 '0'으로 갈라졌어요(본문 overflow-wrap:anywhere를 물려받음). 숫자는 늘 한 줄. */
.badge{white-space:nowrap;overflow-wrap:normal;word-break:keep-all;width:max-content;box-sizing:border-box}
/* [RVA-19] 허브 탭 줄도 글자를 키우면 알맞은 크기에서 멈춰요(200%에서 탭 하나만 보이지 않게). 1024 이상은 왼쪽 메뉴가 같은 탭을 보여 줘서 숨겨요. */
.hubtabs a{font-size:min(.9rem,17px);min-height:min(2.75rem,48px);padding:0 min(.95rem,16px);gap:min(.35rem,6px)}
.chip.hub-all{font-size:min(.82rem,15px);min-height:min(2.75rem,48px);padding:0 min(.8rem,13px)}
.chip.hub-all svg{width:min(1.1rem,20px);height:min(1.1rem,20px)}
@media (min-width:1024px){.hubrow{display:none}}
/* [RVA-22] 총괄 #465 사진(2): 채운 단추는 값이 하나(--accent)예요. 마우스를 올린 색이 손가락 화면에 남지 않게 올린 상태는 (hover:hover)에서만.
   (3) 좁은 폭 창의 단추는 한 줄에 나란히(글이 길면 단추 안에서 줄을 바꿔요). 그래도 띠가 창의 4분의 1을 넘으면 띠가 내용과 함께 내려가요. */
@media (max-width:599.98px){
  dialog .actions:last-child{flex-wrap:nowrap;padding:.6rem 1rem calc(.7rem + env(safe-area-inset-bottom));margin-left:-1.25rem;margin-right:-1.25rem}
  dialog .actions:last-child>.btn{flex:1 1 0;min-width:0;white-space:normal;text-align:center;line-height:1.25;padding:.45rem .6rem}
}
dialog .actions.loose:last-child{position:static;margin-bottom:0;border-top:0;padding-left:0;padding-right:0;margin-left:0;margin-right:0}
/* [RVA-23] 검토 HQ-33: 새 공지의 단추·대상. 고르는 상자는 손가락 크기(44px), 두 칸은 넓으면 나란히. */
.pickset{display:flex;flex-wrap:wrap;gap:.45rem;margin:0 0 .9rem}
.pickbox{display:inline-flex;align-items:center;gap:.45rem;min-height:2.75rem;margin:0;padding:0 .8rem;border:1px solid var(--line-strong);border-radius:999px;font-size:.88rem;font-weight:700;cursor:pointer;background:var(--surface)}
.pickbox input{width:1.1rem;height:1.1rem;margin:0;accent-color:var(--accent)}
.pickbox:has(input:checked){border-color:var(--accent);background:var(--accent-soft);color:var(--accent)}
.two-fields{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,11rem),1fr));column-gap:.75rem}
.act-more{margin-top:.6rem;display:grid;gap:.35rem}
.act-more label{margin-top:.25rem}
details.fold-field{margin:0 0 1rem;padding:.2rem 0 0}
details.fold-field>summary{display:flex;flex-wrap:wrap;align-items:center;gap:.15rem .5rem;min-height:2.75rem;color:var(--fg)}
details.fold-field>summary .meta{font-weight:600;color:var(--muted)}
details.fold-field[open]>summary{margin-bottom:.5rem}
.np .np-acts{display:flex;justify-content:flex-end;flex-wrap:wrap;gap:.25rem 1rem;margin-top:.45rem;font-size:.84rem;font-weight:800}
.np .np-acts i{font-style:normal;color:var(--muted)}
.np .np-acts i.on{color:var(--accent)}
.np.important .np-acts i.on{color:var(--warn)}
/* [RVA-21] 검토 ADMX-17(조건 9): 손가락 화면에서 누르는 곳은 44px 이상. 글 속 이름 링크·작은 글자 단추는 줄 간격은 그대로 두고 누르는 범위만 위아래로 넓혀요. */
.dl-mini{display:none}
/* 2.0.0 [RVA-24] 검토 HQ-03(조건 1): 배포 줄은 넓은 화면(1024px 이상)에서만 한 줄로. 그보다 좁으면 위 막대의 알약 하나(머리 28px 줄임). */
@media (max-width:1023.98px){
  .deploy-line{display:none!important}
  .dl-mini:not([hidden]){flex:none;display:inline-flex;align-items:center;min-height:min(2.75rem,48px);padding:0 min(.15rem,3px);text-decoration:none}
  .dl-pill{display:inline-block;padding:min(.15rem,3px) min(.5rem,9px);border-radius:999px;background:var(--soft);color:var(--muted);font-size:min(.75rem,14px);font-weight:800;white-space:nowrap;line-height:1.4}
  .dl-pill.live{background:var(--accent-soft);color:var(--accent)}
  .dl-pill.warn{background:var(--warn-bg);color:var(--warn)}
  .dl-pill.bad{background:var(--bad-bg);color:var(--bad)}
}
/* 2.0.0 [RVA-61] 총괄 Q1 사진(380·글자 200%, 조건 12): 막대가 좁으면(약 22글자 아래) 판 알약은 점 하나(이름은 읽어 주는 이름과 title에, '판 다름'·'서버 닿지 않음'은 글 그대로),
   제목은 한 줄 말줄임(본문 첫 줄에 같은 제목이 다시 나와요). 전에는 '로컬 2.0.0'에 밀려 제목이 두세 글자씩 두 줄로 섰어요. */
@container bar (width < 22rem){
  .dl-pill:not(.warn,.bad){width:min(.7rem,12px);height:min(.7rem,12px);padding:0;font-size:0;line-height:0;background:var(--muted)}
  .dl-pill.live{background:var(--accent)}
  .bar h1 .t{-webkit-line-clamp:1}
}
@media (pointer:coarse){
  .waiting a,.alert-list a,.stage-strip a{min-height:2.75rem}
  .chip.hub-all,.hubtabs a{min-height:min(2.75rem,48px)}
  .stage-strip a{align-items:center}
  summary,.more-tiles>summary,.shown-above>summary{padding-top:.7rem;padding-bottom:.7rem}
  .switch{min-height:2.75rem}
  .chk{min-height:2.75rem;margin:.3rem 0}
  .chk input{width:1.35rem;height:1.35rem}
  b>a:not([class]),h3>a:not([class]),td.name a,.lines a{padding-top:.75rem;padding-bottom:.75rem}
  a.meta{padding-top:.95rem;padding-bottom:.95rem}
  .who.link{padding-top:.7rem;padding-bottom:.7rem;margin-top:-.7rem;margin-bottom:-.7rem}
  th button.sort{min-height:2.75rem;padding-left:.15rem;padding-right:.15rem}
  a.pill{position:relative}
  a.pill::after{content:"";position:absolute;left:0;right:0;top:calc(50% - 1.375rem);bottom:calc(50% - 1.375rem)}
}
.filepick{position:relative;display:flex;flex-wrap:wrap;align-items:center;gap:.4rem .75rem;margin:0 0 .75rem;font-weight:600}
.filepick input{position:absolute;opacity:0;width:1px;height:1px;left:0;top:0}
.filepick input:focus-visible+.btn{box-shadow:var(--focus)}
.filepick .meta{min-width:0;overflow-wrap:anywhere}
/* 2.0.0 [RVA-21] 380 폭·글자 200%에서 긴 단추(파일 고르기(여러 개), 내 계정의 '다른 기기 모두 로그아웃' 같은 것)가 화면 밖으로 나가지 않게:
   한 줄에 안 들어갈 때만 띄어쓰기에서 줄을 바꿔요(낱말은 가르지 않아요). 차트의 읽어 주는 표는 1px 상자 안에(가로 넘침 0). */
.actions .btn,.filepick .btn{max-width:100%;white-space:normal;word-break:keep-all;text-align:center;padding-top:.35rem;padding-bottom:.35rem}
`;
