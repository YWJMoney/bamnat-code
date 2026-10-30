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
html{-webkit-text-size-adjust:100%;color-scheme:light dark;scroll-padding-top:5rem}
:root[data-theme]{color-scheme:light}
body{margin:0;min-height:100vh;min-height:100dvh;background:var(--bg);color:var(--fg);
font:1rem/1.55 system-ui,-apple-system,"Pretendard","Noto Sans KR","Apple SD Gothic Neo","Malgun Gothic",sans-serif;
-webkit-tap-highlight-color:transparent;word-break:keep-all;overflow-wrap:anywhere;font-feature-settings:"tnum" 1}
button,input,textarea,select{font:inherit;color:inherit}
h1,h2,h3{margin:0;line-height:1.3;letter-spacing:-.01em}
a{color:var(--accent)}
svg{fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
:focus-visible{outline:none;box-shadow:var(--focus);border-radius:var(--r-sm)}
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
.badge{min-width:1.25rem;height:1.25rem;padding:0 .4rem;border-radius:.65rem;background:var(--bad);color:#fff;font-size:.7rem;font-weight:800;
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
  color:var(--nav-fg);text-decoration:none;font-size:.72rem;font-weight:700;text-align:center}
  .nav a:hover{background:var(--nav-hi);color:var(--nav-on)}
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
  .side .group{display:block;margin:1rem .75rem .35rem;font-size:.72rem;font-weight:800;letter-spacing:.04em;color:color-mix(in srgb,var(--nav-fg) 70%,transparent)}
  .nav a{flex-direction:row;gap:.75rem;padding:.6rem .75rem;font-size:.92rem;text-align:left}
  .nav .badge{position:static;margin-left:auto}
  .nav li.open .subnav{display:grid}
  .nav .subnav a{padding:.45rem .75rem;font-size:.86rem;font-weight:700;color:var(--nav-fg)}
  .nav .subnav a[aria-current=page]{background:var(--nav-hi);color:var(--nav-on);box-shadow:inset 3px 0 0 var(--gold)}
  .side .who{display:flex;align-items:center;gap:.6rem;padding:.6rem .75rem;border-radius:var(--r-sm);color:var(--nav-on);text-decoration:none}
  .side .who:hover{background:var(--nav-hi)}
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
.hubtabs a:hover{background:var(--hover);color:var(--fg)}
.hubtabs a[aria-current=page]{background:var(--accent-soft);color:var(--accent)}
.subnav{display:none;list-style:none;margin:.1rem 0 .35rem;padding:0 0 0 2.15rem;gap:.05rem}
.waiting{display:flex;flex-wrap:wrap;align-items:center;gap:.4rem;margin:0 .25rem .85rem;font-size:.88rem;color:var(--muted)}
.waiting b{color:var(--fg);font-weight:800;margin-right:.2rem}
.waiting a{display:inline-flex;align-items:center;gap:.35rem;min-height:2.5rem;padding:0 .8rem;border-radius:999px;background:var(--soft);color:var(--fg);
font-weight:700;text-decoration:none}
.waiting a:hover{background:var(--hover)}
.waiting a .n{color:var(--bad);font-weight:900}
.waiting .dot{display:none}
.todo-card .waiting{margin:0 0 .75rem}
.todo-card .rows{box-shadow:none;margin-bottom:.5rem}
.rows.inbox>li>details.quick{margin:0 1rem .75rem;border-top:0;padding-top:0}
.quick form{display:grid;gap:.5rem}
.quick textarea{min-height:5.5rem}
.section svg{width:1.15rem;height:1.15rem}

/* ---- buttons and fields ---- */
.icon-btn{display:inline-grid;place-items:center;width:2.75rem;height:2.75rem;border:0;border-radius:var(--r-sm);background:none;cursor:pointer;flex:none;color:inherit}
.icon-btn:hover{background:var(--hover)}
.icon-btn svg{width:1.4rem;height:1.4rem}
.btn{white-space:nowrap;display:inline-flex;align-items:center;justify-content:center;gap:.4rem;min-height:2.75rem;padding:0 1.05rem;border-radius:var(--r-sm);
border:1px solid var(--line-strong);background:var(--surface);font-weight:800;font-size:.94rem;cursor:pointer;text-decoration:none;color:var(--fg);
transition:transform .1s,background .15s,border-color .15s;white-space:nowrap}
.btn:hover{background:var(--hover)}
.btn svg{width:1.15rem;height:1.15rem}
.btn.primary{background:var(--accent);border-color:var(--accent);color:var(--on-accent)}
.btn.primary:hover{background:color-mix(in srgb,var(--accent) 88%,#000)}
.btn.danger{color:var(--bad);border-color:color-mix(in srgb,var(--bad) 35%,var(--line))}
.btn.ghost{border-color:transparent;background:none}
.btn.small{min-height:2.25rem;padding:0 .75rem;font-size:.85rem}
.btn.wide{width:100%}
.btn:disabled{opacity:.45;cursor:default;transform:none}
.btn:active:not(:disabled),.icon-btn:active,.row:active,.tile:active{transform:scale(.98)}
.link{border:0;background:none;padding:0;color:var(--accent);font-weight:800;cursor:pointer;text-decoration:underline}
input:not([type]),input[type=search],input[type=text],input[type=email],input[type=password],input[type=url],input[type=date],input[type=number],textarea,select{width:100%;min-height:3rem;
padding:.6rem .9rem;border-radius:var(--r-sm);border:1px solid var(--line-strong);background:var(--surface);outline:none;transition:border-color .15s}
input:focus,textarea:focus,select:focus{border-color:var(--accent);box-shadow:var(--focus)}
input[readonly]{background:var(--soft)}
textarea{resize:vertical;min-height:7rem;line-height:1.55}
label,.label{display:block;font-size:.88rem;font-weight:800;margin:0 0 .4rem}
.help{display:block;margin:.35rem 0 0;font-size:.82rem;color:var(--muted);font-weight:500}
.fieldset{margin:0 0 1rem}
.field{display:flex;gap:.5rem}
.field input{flex:1;min-width:0}
.searchbar{display:flex;gap:.5rem;margin-bottom:.75rem;flex-wrap:wrap}
.searchbar input{flex:1 1 12rem;min-width:0}

/* ---- surfaces ---- */
.card{background:var(--surface);border:1px solid var(--line);border-radius:var(--r-lg);padding:1rem 1.1rem;margin:0 0 .75rem;box-shadow:var(--shadow)}
/* [ADMUX-05] One step per level: screen title 1.2, card 1.06, item .98, text .94. */
.card>h2,.card-head h2{font-size:1.06rem;font-weight:800}
.card-head{display:flex;align-items:center;justify-content:space-between;gap:.75rem;margin:0 0 .6rem}
.card h3{font-size:.98rem}
.grid{display:grid;grid-template-columns:minmax(0,1fr);gap:.75rem}
@media (min-width:600px){.grid.two{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}.grid.two>.card{margin:0}}
@media (min-width:1024px){.cols{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(0,1fr);gap:1rem;align-items:start}.cols>*{min-width:0}}
.head{display:flex;align-items:flex-start;justify-content:space-between;gap:.6rem}
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
.lines div{display:grid;grid-template-columns:minmax(5.5rem,30%) 1fr;gap:.6rem;font-size:.88rem}
.lines dt{color:var(--muted)}
.lines dd{margin:0}
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
.row:hover{background:var(--hover)}
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
.chips{display:flex;flex-wrap:wrap;gap:.5rem;margin:0 0 .75rem}
.chip{display:inline-flex;align-items:center;gap:.15rem;padding:.25rem .25rem .25rem .75rem;border-radius:999px;background:var(--soft);font-size:.88rem;font-weight:700}
.chip button{width:1.9rem;height:1.9rem;border:0;border-radius:50%;background:none;cursor:pointer;font-size:1.1rem;line-height:1;color:var(--muted)}
.chip.pick{padding:.35rem .8rem;border:1px solid var(--line-strong);background:var(--surface);cursor:pointer}
.chip.pick:hover{background:var(--hover)}
.chip svg{width:1rem;height:1rem}
.chip.pick[aria-pressed=true]{background:var(--accent-soft);border-color:var(--accent);color:var(--accent)}

/* ---- home ---- */
.hello{margin:.25rem .25rem 1rem}
.hello h2{font-size:1.35rem;font-weight:900}
.hello p{margin:.2rem 0 0;color:var(--muted);font-size:.92rem}
.tiles{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,10.5rem),1fr));gap:.65rem;margin:0 0 1rem}
.tile{display:flex;flex-direction:column;gap:.35rem;padding:.9rem 1rem;border-radius:var(--r-lg);background:var(--surface);border:1px solid var(--line);
box-shadow:var(--shadow);text-decoration:none;color:inherit;min-height:6.5rem;transition:border-color .15s,transform .1s}
a.tile:hover{border-color:var(--accent)}
.tile .k{font-size:.8rem;font-weight:800;color:var(--muted);display:flex;align-items:center;gap:.4rem}
.tile .k svg{width:1.05rem;height:1.05rem}
.tile .v{font-size:1.9rem;font-weight:900;line-height:1.1;letter-spacing:-.02em}
.tile .d{font-size:.78rem;color:var(--muted);margin-top:auto}
@media (max-width:599.98px){.tile{min-height:0;padding:.7rem .85rem;gap:.15rem}.tile .v{font-size:1.5rem}.tile .d{display:none}}
/* The phone lists what waits; wider screens show the counts as tiles, with the activity beside. [ADMUX-06] The
   queue of what to answer first shows on both. */
.stats{display:flex;flex-wrap:wrap;gap:.4rem 1.25rem;margin:0 .25rem 1rem;font-size:.86rem;color:var(--muted)}
.stats b{color:var(--fg);font-size:1rem;margin-left:.3rem}
.tile.hot{border-color:color-mix(in srgb,var(--bad) 40%,var(--line))}
.tile.hot .v{color:var(--bad)}
.todo{list-style:none;margin:0;padding:0;display:grid;gap:.15rem}
.todo a{display:flex;align-items:center;gap:.65rem;padding:.6rem .5rem;border-radius:var(--r-sm);text-decoration:none;color:inherit;font-weight:700;font-size:.94rem}
.todo a:hover{background:var(--hover)}
.todo .dot{flex:none;width:.55rem;height:.55rem;border-radius:50%;background:var(--accent)}
.todo .dot.bad{background:var(--bad)}
.todo .dot.warn{background:var(--gold)}
.todo small{margin-left:auto;color:var(--muted);font-weight:600;white-space:nowrap}
.feed{list-style:none;margin:0;padding:0}
.feed li{display:grid;grid-template-columns:auto 1fr;gap:.15rem .65rem;padding:.55rem 0;border-top:1px solid var(--line);font-size:.88rem}
.feed li:first-child{border-top:0}
.feed .when{grid-row:span 2;color:var(--muted);font-size:.75rem;min-width:3.4rem;padding-top:.1rem}
.feed .who{color:var(--muted);font-size:.78rem}
.day-head{margin:1rem .25rem .4rem;font-size:.8rem;font-weight:800;color:var(--muted)}

/* ---- conversation ---- */
.thread{display:flex;flex-direction:column;gap:.6rem;margin:.25rem 0 .75rem}
.bubble{max-width:min(85%,36rem);padding:.65rem .85rem;border-radius:1.1rem;white-space:pre-wrap;font-size:.94rem;line-height:1.55}
.bubble.user{align-self:flex-start;background:var(--soft);border-bottom-left-radius:.35rem}
.bubble.admin{align-self:flex-end;background:var(--accent);color:var(--on-accent);border-bottom-right-radius:.35rem}
.bubble-meta{font-size:.72rem;color:var(--muted);margin:-.3rem .35rem 0}
.bubble-meta.admin{align-self:flex-end}
.composer{position:sticky;bottom:calc(4.4rem + env(safe-area-inset-bottom));background:var(--surface);border:1px solid var(--line);border-radius:var(--r-lg);
padding:.75rem;box-shadow:var(--shadow)}
@media (min-width:600px){.composer{bottom:1rem}}
/* [J133-40] 순번 720 (1.32 실사용 테스트, 관리자 휴대폰 화면): on a phone the reply box stays in the page, under the talk, instead of
   floating over the last bubbles; the actions under it are never behind the bottom tabs. */
@media (max-width:599px){.composer{position:static;margin-bottom:1rem}}
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
.brand{text-align:center;margin-bottom:1.5rem}
.brand img{border-radius:1.35rem;box-shadow:var(--shadow)}
.brand h1{font-size:1.5rem;margin:.85rem 0 .25rem}
.brand p{margin:0;color:var(--muted)}
.error{min-height:1.4em;margin:.5rem 0;color:var(--bad);font-size:.88rem;font-weight:600}
.alt{text-align:center;margin:1rem 0 0;font-size:.88rem;color:var(--muted)}
.toast{position:fixed;left:50%;bottom:calc(5.25rem + env(safe-area-inset-bottom));transform:translate(-50%,1rem);max-width:min(92vw,32rem);padding:.75rem 1.1rem;
border-radius:var(--r);background:var(--fg);color:var(--bg);font-size:.88rem;font-weight:700;opacity:0;pointer-events:none;transition:opacity .2s,transform .2s;
z-index:60;text-align:center;box-shadow:var(--shadow-lg)}
.toast.show{opacity:1;transform:translate(-50%,0)}
dialog{border:0;padding:0;border-radius:var(--r-lg);background:var(--raised);color:var(--fg);width:min(94vw,30rem);max-height:min(92vh,48rem);box-shadow:var(--shadow-lg)}
dialog.wide{width:min(94vw,40rem)}
dialog::backdrop{background:rgba(8,10,20,.55);backdrop-filter:blur(2px)}
dialog[open]{animation:pop .18s ease-out}
@keyframes pop{from{opacity:0;transform:translateY(.6rem) scale(.98)}to{opacity:1;transform:none}}
dialog form,dialog .box{padding:1.35rem 1.25rem 1.1rem}
dialog h2{font-size:1.15rem;margin-bottom:.35rem}
dialog p{margin:0 0 .75rem;color:var(--muted);font-size:.88rem}
dialog .actions:last-child{position:sticky;bottom:0;margin:1rem -1.25rem -1.1rem;padding:.75rem 1.25rem calc(.9rem + env(safe-area-inset-bottom));
background:var(--raised);border-top:1px solid var(--line)}
@media (max-width:599.98px){dialog{margin:auto 0 0;width:100vw;max-width:100vw;border-radius:var(--r-lg) var(--r-lg) 0 0;padding-bottom:env(safe-area-inset-bottom)}
@keyframes pop{from{transform:translateY(100%)}to{transform:none}}
/* AT 1.29: a wide box (복사, 개발사항에 추가) is a whole-width sheet on a phone too; dialog.wide's 94vw left a gap at the side. */
dialog.wide{width:100vw}}
.radios{display:grid;gap:.5rem;margin:.4rem 0}
.radio{display:flex;align-items:center;gap:.6rem;margin:0;padding:.75rem;border:1px solid var(--line-strong);border-radius:var(--r-sm);font-weight:600;cursor:pointer;font-size:.92rem}
.radio input{flex:none;width:1.25rem;height:1.25rem;accent-color:var(--accent)}
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
.rows.inbox .row-main a:hover{text-decoration:underline}
@media (min-width:600px){.bulkbar{bottom:1rem}.rows.inbox .row-side{flex-direction:row;align-items:center}}
/* ---- AT 1.29 문의 복사 [ADMCP-01]~[ADMCP-05]: a copy button beside a row's link, the strip of new reports, the copy box ---- */
.rows>li.has-tool{display:flex;align-items:center}
.rows>li.has-tool>.row{flex:1;min-width:0}
.row-tool{flex:none;display:inline-grid;place-items:center;width:2.75rem;height:2.75rem;margin:0 .4rem 0 -.4rem;border:0;border-radius:var(--r-sm);background:none;color:var(--muted);cursor:pointer}
.row-tool:hover,.row-tool:focus-visible{background:var(--hover);color:var(--fg)}
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
.item a.row-main:hover b{text-decoration:underline}
.kbd{display:inline-block;min-width:1.6rem;padding:.05rem .4rem;border:1px solid var(--line-strong);border-bottom-width:2px;border-radius:.4rem;font-size:.8rem;
font-weight:800;text-align:center;background:var(--surface)}
.keys{display:grid;grid-template-columns:auto 1fr;gap:.5rem .9rem;align-items:center;font-size:.9rem;margin:0 0 .5rem}
/* ---- 1.23 [ADMUX-01]~[ADMUX-08] ---- */
/* Touch: on a finger, every small control is still 44 px. */
@media (pointer:coarse){.btn.small,.seg,.levels button{min-height:2.75rem}.chip button{width:2.75rem;height:2.75rem}.chip.pick{min-height:2.75rem}.icon-btn{width:2.75rem;height:2.75rem}}
.btn[aria-busy=true]{opacity:.7;cursor:progress}
.row.static{cursor:default}.row.static:hover{background:none}.row.static:active{transform:none}
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
.toast .undo{margin-left:.75rem;border:0;background:none;color:var(--accent-soft);font-weight:900;text-decoration:underline;cursor:pointer;pointer-events:auto;min-height:2rem}
.toast.show.has-undo{pointer-events:auto}
/* Two panes: on a wide screen the list stays beside the open item. */
@media (min-width:1024px){.split{display:grid;grid-template-columns:minmax(17rem,24rem) minmax(0,1fr);gap:1rem;align-items:start}
.split>.pane-list{position:sticky;top:4.75rem;max-height:calc(100vh - 6rem);max-height:calc(100dvh - 6rem);overflow:auto;border-radius:var(--r-lg)}
.split>.pane-list .rows{margin:0}.split .row[aria-current=true]{background:var(--accent-soft)}}
@media (max-width:1023.98px){.split>.pane-list{display:none}}
@media (min-width:1024px) and (max-width:1439.98px){.split .cols{display:block}}
/* Charts: drawn as SVG, coloured here (the CSP allows no style attributes). */
.chart{display:block;width:100%;height:auto;margin:.5rem 0}
.chart .bar{fill:var(--accent);stroke:none}
.chart .bar:hover{fill:color-mix(in srgb,var(--accent) 75%,var(--fg))}
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
.queue-list .q-main a{color:inherit;text-decoration:none}.queue-list .q-main a:hover{text-decoration:underline}
.queue-list .q-main small{color:var(--muted);font-size:.8rem}
.delta{font-size:.8rem;font-weight:800;margin-left:.35rem}
.delta.up{color:var(--bad)}.delta.down{color:var(--ok)}
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
.queue-bar{position:sticky;top:4rem;z-index:4;display:flex;align-items:center;gap:.5rem;flex-wrap:wrap;padding:.5rem .75rem;margin:0 0 .75rem;
background:var(--accent-soft);color:var(--accent);border-radius:var(--r);font-weight:800;font-size:.88rem}
.queue-bar b{margin-right:auto}
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
.check-row input{width:1.2rem;height:1.2rem;accent-color:var(--accent)}
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
.agenda .date{flex:none;width:4.5rem;display:flex;flex-direction:column;font-weight:800;font-size:.9rem}
.agenda .date small{font-weight:600;color:var(--muted);font-size:.75rem}
.cond-out{margin:.5rem 0 0;font-size:.88rem}
.cond-out .lines{padding-left:1.2rem}
#flag-cond{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:.9rem}
.sim-grid{display:grid;gap:.25rem .75rem;grid-template-columns:repeat(auto-fit,minmax(9.5rem,1fr))}
@media (prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}
@media (forced-colors:active){.btn,.seg,.tab,.card{border:1px solid CanvasText}}
/* ---- AX 1.30 [ADMX-01]~[ADMX-11] ---- */
/* [ADMX-11] Wider content on big screens, the same gutters on a phone. */
.bar,.main{max-width:80rem}
.hubtabs{max-width:80rem;margin-left:auto;margin-right:auto}
/* [ADMX-01] The four numbers that need a hand: 2×2 on a phone, a row of four on a wide screen. */
.tiles.core{grid-template-columns:repeat(2,minmax(0,1fr));gap:.6rem}
@media (min-width:900px){.tiles.core{grid-template-columns:repeat(4,minmax(0,1fr))}.tiles.core.metric-kpis{grid-template-columns:repeat(6,minmax(0,1fr))}}
@media (min-width:600px) and (max-width:899.98px){.tiles.core.metric-kpis{grid-template-columns:repeat(3,minmax(0,1fr))}}
.tile.kpi{position:relative;min-height:0;padding:.75rem .85rem .6rem;gap:.15rem}
.tile.kpi .v{display:flex;align-items:baseline;gap:.4rem;font-size:1.7rem}
.tile.kpi .d{font-size:.75rem;line-height:1.35;color:var(--muted);display:block}
.tile.kpi.rising{border-color:color-mix(in srgb,var(--warn) 55%,var(--line))}
.tile.skeleton{min-height:6.5rem;background:var(--soft);border-color:transparent}
.delta{font-size:.75rem;font-weight:800;letter-spacing:0}
.delta.up{color:var(--bad)}.delta.down{color:var(--ok)}
.chg{font-weight:800}.chg.bad{color:var(--bad)}.chg.ok{color:var(--ok)}
.spark{display:block;width:100%;height:1.6rem;margin-top:.2rem}
.spark polyline{fill:none;stroke:var(--accent);stroke-width:1.6;vector-effect:non-scaling-stroke}
.spark circle{fill:var(--accent);stroke:none}
.tile.hot .spark polyline{stroke:var(--bad)}.tile.hot .spark circle{fill:var(--bad)}
@media (max-width:599.98px){.tiles:not(.core){display:grid}.tile.kpi .v{font-size:1.45rem}.tile.kpi .d{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}}
.more-tiles{margin:0 0 .75rem}
.more-tiles>summary{cursor:pointer;font-weight:800;font-size:.9rem;color:var(--muted);padding:.4rem .25rem;list-style:none}
.more-tiles .tiles{margin:.5rem 0}
.home-grid{display:grid;grid-template-columns:minmax(0,1fr);gap:0 1rem;align-items:start}
@media (min-width:1024px){.home-grid{grid-template-columns:minmax(0,1.75fr) minmax(0,1fr)}}
.home-main,.home-side{min-width:0}
/* 주의 필요 */
.alerts{border-color:color-mix(in srgb,var(--bad) 35%,var(--line))}
.alert-list{list-style:none;margin:0;padding:0}
.alert-list li{display:flex;align-items:center;gap:.5rem;border-top:1px solid var(--line);padding:.15rem 0}
.alert-list li:first-child{border-top:0}
.alert-list li::before{content:"";flex:none;width:.55rem;height:.55rem;border-radius:50%;background:var(--warn)}
.alert-list li.bad::before{background:var(--bad)}
.alert-list a{flex:1;min-width:0;display:flex;flex-wrap:wrap;align-items:baseline;gap:0 .6rem;padding:.5rem 0;color:inherit;text-decoration:none}
.alert-list a:hover b{text-decoration:underline}
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
.diff.facts li.add .pill,.diff.facts li.del .pill{justify-self:start;font-size:.72rem;padding:.05rem .45rem}
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
table.list td.name a:hover{text-decoration:underline}
table.list td.name small{display:block;font-size:.75rem;color:var(--muted)}
table.list td.name .pill{margin-left:.35rem;font-size:.72rem}
table.list td small{color:var(--muted);font-size:.75rem}
table.list tr.picked td{background:var(--accent-soft)}
@media (hover:hover) and (min-width:600px){table.list tbody tr:hover td{background:var(--hover)}}
@media (min-width:600px){table.list thead th:not(:first-child){text-align:right}table.list thead th.pickcol+th{text-align:left}}
@media (min-width:600px){table.list .lab{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}}
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
  table.list td.n{text-align:left;font-size:.85rem}
  table.list .lab{color:var(--muted);font-size:.75rem;font-weight:600}
}
/* [ADMX-07] 지표: the period stays on top while scrolling. */
.timebar{position:sticky;top:3.75rem;z-index:3;margin:0 -1rem .75rem;padding:.5rem 1rem .25rem;background:color-mix(in srgb,var(--bg) 92%,transparent);
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
.pile-strip{display:grid;grid-template-columns:repeat(auto-fill,minmax(7.5rem,1fr));gap:.5rem;margin:0 0 .9rem}
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
.sort-ask>b a:hover{text-decoration:underline}
.sort-ask>small{font-size:.8rem;color:var(--muted)}
.sort-card .match{display:block;margin:.35rem 0 0;font-size:.82rem;color:var(--info);font-weight:700}
.sort-card details{margin:.4rem 0 0;padding-top:.25rem}
.sort-card .actions{margin:.5rem 0 0}
.sort-field{display:block;margin:.6rem 0 0;font-weight:700}
.sort-field>span{display:block;font-size:.8rem;color:var(--muted);margin:0 0 .25rem}
.sort-field textarea{min-height:4.5rem}
ul.mini{list-style:none;margin:.4rem 0 0;padding:0;display:flex;flex-direction:column;gap:.3rem;font-size:.86rem}
ul.mini small{color:var(--muted);margin-left:.3rem}
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
`;
