# 인쇄용 교대 달력(shift/print/)과 근무표 양식(form/, form/files/*.xlsx).
# build.py가 public_pages.py 다음에 돌려요. 연도는 site_common의 PRINT_YEARS·PRINT_YEAR(오늘 날짜와 공휴일 자료)에서 정해요.
import html
import io
import json
import os
import zipfile

from openpyxl import Workbook
from openpyxl.formatting.rule import CellIsRule
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation

from site_common import *

PATS = []
for p in PRESETS:
    if p['codes'] == 'DDDDDOO': continue
    m = re.search(r'(\d)조', p['name'])
    teams = int(m.group(1)) if m else (len(p['codes']) if len(p['codes']) <= 4 else 0)
    PATS.append({'slug': p['id'][2:], 'name': p['name'], 'description': p['description'], 'codes': p['codes'], 'teams': teams})
HOL = {h['date']: h['name'] for h in HOLS['days'] if int(h['date'][:4]) in PRINT_YEARS}
Y_TXT = '·'.join(map(str, PRINT_YEARS))

PRINT_CSS = '''<style>
.year { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
@media (min-width: 560px) { .year { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
.mini h3 { font-size: 14px; margin: 0 0 4px; }
.mini .g { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 1px; font-size: 11px; text-align: center; }
.mini .g .h { color: var(--text-2); }
.mini .g .d { background: var(--surface-2); border-radius: 3px; padding: 1px 0 2px; line-height: 1.15; }
.mini .g .d b { display: block; font-weight: 400; color: var(--text-2); font-size: 10px; }
.mini .g .d i { display: block; font-style: normal; font-weight: 700; border-radius: 3px; margin: 0 1px; }
.mini .g .d.hol b, .mini .g .su { color: var(--duty) !important; }
.mini .g i.O { color: var(--text-2); }
.mini .g i.D, table.team i.D, .legend i.D { background: var(--chip-D); color: var(--chip-D-on); }
.mini .g i.E, table.team i.E, .legend i.E { background: var(--chip-E); color: var(--chip-E-on); }
.mini .g i.N, table.team i.N, .legend i.N { background: var(--chip-N); color: var(--chip-N-on); }
.mini .g i.X, table.team i.X, .legend i.X { background: var(--chip-X); color: var(--chip-X-on); }
.mini .g i.B, table.team i.B, .legend i.B { background: var(--chip-B); color: var(--chip-B-on); }
.legend i.O { box-shadow: inset 0 0 0 1.5px var(--line-2); }
.legend { display: flex; flex-wrap: wrap; gap: 6px 14px; font-size: 13px; margin: 8px 0; }
.legend span i { display: inline-block; width: 12px; height: 12px; border-radius: 3px; vertical-align: -1px; margin-right: 4px; }
.ptitle { font-size: 18px; font-weight: 700; margin: 4px 0 2px; }
.print-only { display: none; }
.times { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 8px 12px; }
.times label { margin: 8px 0 4px; font-size: 14px; }
.teamopt[hidden] { display: none; }
.wrap { overflow-x: auto; -webkit-overflow-scrolling: touch; }
table.team { border-collapse: collapse; font-size: 11px; width: 100%; margin: 4px 0 14px; }
table.team caption { text-align: left; font-weight: 700; font-size: 14px; padding: 2px 0 4px; }
table.team th, table.team td { border: 1px solid var(--line); padding: 1px; text-align: center; min-width: 20px; }
table.team thead th { font-weight: 500; color: var(--text-2); line-height: 1.2; }
table.team thead th.hol { color: var(--duty); }
table.team tbody th { text-align: left; padding: 2px 6px; white-space: nowrap; font-weight: 650; }
table.team i { display: block; font-style: normal; font-weight: 700; border-radius: 3px; padding: 1px 0; }
table.team i.O { color: var(--text-2); }
table.form { border-collapse: collapse; font-size: 11px; min-width: 100%; }
table.form th, table.form td { border: 1px solid var(--line-2); padding: 3px 4px; text-align: center; white-space: nowrap; }
table.form th { background: var(--surface-2); font-weight: 600; }
table.form td.name { min-width: 64px; }
table.form td { height: 26px; }
table.form tr.more { display: none; }
.fhead { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 4px 16px; font-weight: 700; font-size: 15px; margin: 0 0 8px; }
.fhead span { font-weight: 500; }
@media print {
  @page { size: A4 landscape; margin: 8mm; }
  html, body { background: #fff !important; color: #000 !important; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  main { max-width: none; padding: 0; }
  .controls, h2, details, .lead, h1, .no-print, .note, .result, ul.links, .crumbs { display: none !important; }
  .ptitle { font-size: 15px; margin: 0; }
  .legend { margin: 2px 0 4px; font-size: 10px; }
  .year { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 3px 10px; }
  .mini h3 { font-size: 11px; margin: 0 0 1px; }
  .mini .g { font-size: 8.5px; }
  .mini .g .d { padding: 0 0 1px; line-height: 1.1; }
  .mini .g .d b { font-size: 7.5px; }
  .card { border: 0; padding: 0; margin: 0; background: #fff; box-shadow: none; }
  .mini .g .d { background: #f1f2f4; }
  .print-only { display: block; font-size: 8.5px; color: #333; margin-top: 3px; }
  .psum { font-size: 10.5px; font-weight: 700; color: #000; margin: 1px 0 0; }
  .wrap { overflow: visible; }
  table.team { font-size: 9px; break-inside: avoid; margin-bottom: 8px; }
  table.team th, table.team td { border-color: #999; }
  #team.one table.team { font-size: 12px; }
  #team.one table.team tbody td { height: 13mm; }
  #team.one table.team i { padding: 3px 0; }
  /* [WEB-14] 손으로 쓰는 칸: 줄마다 9mm, 종이 한 장을 채우는 줄 수 */
  table.form { font-size: 10px; table-layout: fixed; width: 100%; }
  table.form th:first-child { width: 26mm; }
  table.form th.cnt { width: 9mm; }
  table.form th, table.form td { border-color: #777; padding: 1px 2px; }
  table.form td { height: 9mm; }
  table.form tr.more { display: table-row; }
  .fhead { font-size: 13px; }
}
</style>'''


def add_css(s): return s.replace('</head>', PRINT_CSS + '\n</head>', 1)


PRINT_BODY = '''<div class="card controls">
  <label for="pat">근무 순서</label>
  <select id="pat"></select>
  <label for="custom">직접 적기<span class="hint">주=D 오후=E 야=N 휴=O 당번=X 비번=B. 예: DDNNOO</span></label>
  <input id="custom" autocomplete="off" autocapitalize="characters" maxlength="62">
  <label for="first">이 순서의 첫날<span class="hint">순서 맨 앞 근무를 하는 날 아무 날이나. 예: 주야비휴면 주간 하는 날.</span></label>
  <input id="first" type="date">
  <div class="btns">
    <button type="button" class="chip" id="prev">◀ 하루 당기기</button>
    <button type="button" class="chip" id="next">하루 밀기 ▶</button>
  </div>
  <span class="say" id="todayis" aria-live="polite"></span>
  <span class="glabel" id="view-l">어떻게 찍을까요</span>
  <div class="chips" id="view" role="group" aria-labelledby="view-l">
    <button type="button" class="chip" data-v="year" aria-pressed="true">우리 조 열두 달</button>
    <button type="button" class="chip" data-v="team" aria-pressed="false">조 나란히 (팀 근무표)</button>
  </div>
  <div class="teamopt" id="teamopt" hidden>
    <label for="teams">조 수<span class="hint">순서 길이를 조 수로 똑같이 나눠 하루씩·이틀씩 밀어요.</span></label>
    <input id="teams" inputmode="numeric" autocomplete="off" data-min="2" data-max="8" data-unit="조">
    <label for="tnames">조 이름<span class="hint">쉼표로 나눠요. 예: A조, B조, C조, D조 (빈칸이면 1조, 2조…)</span></label>
    <input id="tnames" maxlength="60" autocomplete="off">
    <label for="tmonth">찍을 달</label>
    <select id="tmonth"></select>
  </div>
  <label for="label">달력 위에 적을 이름<span class="hint">예: 3조, 홍길동 (빈칸이면 안 적어요)</span></label>
  <input id="label" maxlength="20" autocomplete="off">
  <span class="glabel" id="times-l">근무 시각 <span class="hint">범례에 함께 찍혀요. 안 적어도 돼요. 예: 07–19</span></span>
  <div class="times" id="times" role="group" aria-labelledby="times-l"></div>
  <span class="glabel" id="years-l">해</span>
  <div class="chips" id="years" role="group" aria-labelledby="years-l"></div>
  <div class="btns">
    <button type="button" class="btn" id="print">인쇄하기 · PDF로 저장</button>
    <button type="button" class="chip" id="share">같은 조 동료에게 보내기</button>
  </div>
</div>
<div class="card">
  <div class="ptitle" id="ptitle"></div>
  <div class="print-only psum" id="psum"></div>
  <div class="legend" id="legend"></div>
  <div class="year" id="year"></div>
  <div id="team"></div>
  <div class="print-only" id="phol"></div>
  <div class="print-only">밤낮 · bamnat.app — 이 달력을 휴대폰에, 근무마다 다른 알람까지. 빨간 날짜는 일요일·공휴일이에요.</div>
</div>
<div class="card result" id="sum" aria-live="polite"></div>'''

PRINT_JS = r'''(function(){
var B=Bamnat,$=B.$,D=B.d;
var NAME={D:'주',E:'오',N:'야',O:'휴',X:'당',B:'비'},FULL={D:'주간',E:'오후',N:'야간',O:'휴무',X:'당번',B:'비번'},WORK='DENX';
var HOL=%HOL%,PATS=%PATS%,FIX='%FIX%',YEARS=%YEARS%,year=%YEAR%,view='year',qs=new URLSearchParams(location.search),times={};
function esc(s){return String(s||'').replace(/[&<>"]/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];});}
function codes(){var c=$('custom').value.toUpperCase().replace(/[^DENOXB]/g,'');return c||$('pat').value;}
function pat(c){for(var i=0;i<PATS.length;i++)if(PATS[i].codes===c)return PATS[i];return null;}
function at(c,start,d,shift){var k=(D.between(start,d)-shift)%c.length;if(k<0)k+=c.length;return c[k];}
function uniq(c){return c.split('').filter(function(x,i,a){return a.indexOf(x)===i;});}
function teamInfo(c){var n=B.val('teams')|0;if(n<2)return{err:'조 수를 2부터 적어 주세요.'};if(c.length%n)return{err:'순서 '+c.length+'일을 '+n+'개 조로 똑같이 나눌 수 없어요. 조 수를 바꿔 주세요.'};
  var names=$('tnames').value.split(/[,，]/).map(function(s){return s.trim();});var list=[];for(var i=0;i<n;i++)list.push(names[i]||(i+1)+'조');return{n:n,step:c.length/n,names:list};}
// [U50-12] 근무마다 시각 칸(범례에 '주간 07–19'처럼 찍혀요)
function timeBoxes(c){var box=$('times'),have={};box.querySelectorAll('input').forEach(function(i){times[i.dataset.c]=i.value;});
  var w=uniq(c).filter(function(x){return WORK.indexOf(x)>=0;});
  box.innerHTML=w.map(function(x){return '<div><label for="t-'+x+'">'+FULL[x]+'</label><input id="t-'+x+'" data-c="'+x+'" maxlength="13" autocomplete="off" placeholder="'+({D:'07–19',E:'14–22',N:'19–07',X:'09–09'})[x]+'" value="'+esc(times[x]||'')+'"></div>';}).join('');
  box.querySelectorAll('input').forEach(function(i){i.addEventListener('input',function(){times[i.dataset.c]=i.value;paint();});});}
function legend(c){return uniq(c).map(function(x){var t=(times[x]||'').trim();return '<span><i class="'+x+'"></i>'+FULL[x]+(t?' '+esc(t):'')+'</span>';}).join('');}
function count(c,start,shift){var cnt={},hol=[],holWork=[],work=0;
  for(var m=0;m<12;m++){var last=new Date(Date.UTC(year,m+1,0)).getUTCDate();for(var d=1;d<=last;d++){var day=new Date(Date.UTC(year,m,d)),s=at(c,start,day,shift),k=D.iso(day),h=HOL[k];
    cnt[s]=(cnt[s]||0)+1;if(WORK.indexOf(s)>=0)work++;if(h)(WORK.indexOf(s)>=0?holWork:hol).push((m+1)+'/'+d+' '+h);}}
  return{cnt:cnt,work:work,off:hol,holWork:holWork};}
function line(c,r){return uniq(c).filter(function(x){return WORK.indexOf(x)>=0;}).map(function(x){return FULL[x]+' '+(r.cnt[x]||0);}).join(' · ')+' · 쉬는 공휴일 '+r.off.length+' · 일하는 공휴일 '+r.holWork.length;}
function yearView(c,start){var out='';
  for(var m=0;m<12;m++){var first=new Date(Date.UTC(year,m,1)),last=new Date(Date.UTC(year,m+1,0)).getUTCDate();
    out+='<div class="mini"><h3>'+(m+1)+'월</h3><div class="g">';
    '일월화수목금토'.split('').forEach(function(w,i){out+='<div class="h'+(i===0?' su':'')+'">'+w+'</div>';});
    for(var i=0;i<first.getUTCDay();i++)out+='<div></div>';
    for(var d=1;d<=last;d++){var day=new Date(Date.UTC(year,m,d)),s=at(c,start,day,0),h=HOL[D.iso(day)];
      out+='<div class="d'+(h||day.getUTCDay()===0?' hol':'')+'"'+(h?' title="'+esc(h)+'"':'')+'><b>'+d+'</b><i class="'+s+'">'+NAME[s]+'</i></div>';}
    out+='</div></div>';}
  return out;}
// [T50-10] 조 나란히: 한 달을 한 표로, 줄마다 조 하나
function teamView(c,start,t){var mo=$('tmonth').value,months=mo==='all'?[0,1,2,3,4,5,6,7,8,9,10,11]:[+mo],out='';
  months.forEach(function(m){var last=new Date(Date.UTC(year,m+1,0)).getUTCDate(),head='<th scope="col">조</th>',rows='';
    for(var d=1;d<=last;d++){var day=new Date(Date.UTC(year,m,d)),h=HOL[D.iso(day)];head+='<th scope="col"'+(h||day.getUTCDay()===0?' class="hol"':'')+(h?' title="'+esc(h)+'"':'')+'>'+d+'<br>'+'일월화수목금토'[day.getUTCDay()]+'</th>';}
    t.names.forEach(function(nm,k){rows+='<tr><th scope="row">'+esc(nm)+'</th>';for(var d=1;d<=last;d++){var s=at(c,start,new Date(Date.UTC(year,m,d)),k*t.step);rows+='<td><i class="'+s+'">'+NAME[s]+'</i></td>';}rows+='</tr>';});
    out+='<div class="wrap"><table class="team"><caption>'+year+'년 '+(m+1)+'월</caption><thead><tr>'+head+'</tr></thead><tbody>'+rows+'</tbody></table></div>';});
  return out;}
function paint(){
  var c=codes(),start=D.parse($('first').value);if(!c||!start)return;
  var p=pat(c),lab=$('label').value.trim(),now=D.today(),t=null;
  $('legend').innerHTML=legend(c);
  if(view==='team'){t=teamInfo(c);$('teamopt').hidden=false;}else $('teamopt').hidden=true;
  if(view==='team'&&t.err){B.say($('teams'),t.err,'warn');$('year').innerHTML='';$('team').innerHTML='';$('psum').textContent='';return;}
  var s0=at(c,start,now,0);
  $('todayis').textContent='오늘('+D.md(now)+')은 '+FULL[s0]+'. 우리 조와 맞는지 확인해요.';
  $('ptitle').textContent=year+'년 '+(p?p.name:'교대')+(view==='team'?' 팀 근무표':' 근무 달력')+(lab?' · '+lab:'');
  var holList=Object.keys(HOL).filter(function(k){return +k.slice(0,4)===year;}).map(function(k){return +k.slice(5,7)+'/'+(+k.slice(8))+' '+HOL[k];});
  $('phol').textContent=holList.length?'공휴일: '+holList.join(', '):'';
  if(view==='team'){
    $('year').innerHTML='';$('team').innerHTML=teamView(c,start,t);$('team').className=$('tmonth').value==='all'?'':'one';
    var rs=t.names.map(function(nm,k){return{nm:nm,r:count(c,start,k*t.step)};});
    $('psum').textContent=rs.map(function(x){return x.nm+' '+line(c,x.r);}).join(' / ');
    $('sum').innerHTML='<strong>'+year+'년 조마다</strong><table class="lines">'+rs.map(function(x){return '<tr><th scope="row">'+esc(x.nm)+'</th><td>일 '+x.r.work+'일'+(x.r.cnt.N?' · 야간 '+x.r.cnt.N:'')+' · 쉬는 공휴일 '+x.r.off.length+'</td></tr>';}).join('')+'</table>';
  }else{
    $('team').innerHTML='';$('year').innerHTML=yearView(c,start);
    var r=count(c,start,0);
    // [WEB-24] 종이에도 한 해 요약 한 줄
    $('psum').textContent=year+'년 '+line(c,r);
    $('sum').innerHTML='<strong>'+year+'년 한눈에</strong><table class="lines">'+B.rows([['일하는 날',r.work+'일'],r.cnt.N?['야간',r.cnt.N+'번']:null,r.cnt.X?['당번',r.cnt.X+'번']:null,['쉬는 공휴일',r.off.length+'일'],['일하는 공휴일',r.holWork.length+'일'],
      r.holWork.length?{note:'일하는 공휴일: '+esc(r.holWork.join(', '))}:null,r.off.length?{note:'쉬는 공휴일: '+esc(r.off.join(', '))}:null])+'</table>';
  }
  save();
}
function draw(){timeBoxes(codes());paint();}
function save(){var p=new URLSearchParams();p.set('p',codes());p.set('s',$('first').value);p.set('y',year);
  if($('label').value.trim())p.set('t',$('label').value.trim());if(view==='team'){p.set('v','team');p.set('n',$('teams').value);if($('tnames').value.trim())p.set('tn',$('tnames').value.trim());p.set('m',$('tmonth').value);}
  Object.keys(times).forEach(function(k){if(times[k]&&codes().indexOf(k)>=0)p.set('h'+k,times[k]);});
  try{history.replaceState(null,'','?'+p.toString());}catch(e){}}
function shift(n){var d=D.parse($('first').value);if(!d)return;$('first').value=D.iso(D.plusDays(d,n));paint();}
$('pat').innerHTML=PATS.map(function(p){return '<option value="'+esc(p.codes)+'">'+esc(p.name)+' · '+esc(p.description)+'</option>';}).join('');
$('tmonth').innerHTML='<option value="all">열두 달 모두</option>'+[1,2,3,4,5,6,7,8,9,10,11,12].map(function(m){return '<option value="'+(m-1)+'">'+m+'월</option>';}).join('');
var saved=null;try{saved=JSON.parse(localStorage.getItem('bn_shift')||'null');}catch(e){}
if(FIX)$('pat').value=FIX;
if(qs.get('p')){var q=qs.get('p').toUpperCase().replace(/[^DENOXB]/g,'');if(!FIX&&pat(q))$('pat').value=q;else if(q!==FIX)$('custom').value=q;}
else if(saved&&saved.p&&(!FIX||saved.p===FIX)){if(pat(saved.p))$('pat').value=saved.p;else if(!FIX)$('custom').value=saved.p;}
$('first').value=qs.get('s')||(saved&&saved.s&&(!FIX||saved.p===FIX)?saved.s:D.iso(D.today()));
if(YEARS.indexOf(+qs.get('y'))>=0)year=+qs.get('y');
$('label').value=qs.get('t')||'';
['D','E','N','X'].forEach(function(k){if(qs.get('h'+k))times[k]=qs.get('h'+k);});
var tp=pat(codes());$('teams').value=qs.get('n')||(tp&&tp.teams>1?tp.teams:'');$('tnames').value=qs.get('tn')||'';
var mq=qs.get('m');$('tmonth').value=mq!=null&&/^(all|[0-9]|1[01])$/.test(mq)?mq:(year===D.today().getUTCFullYear()?String(D.today().getUTCMonth()):'all');
var vc=B.chips($('view'),function(v){view=v;paint();});if(qs.get('v')==='team'){view='team';vc.set('team');}
$('years').innerHTML=YEARS.map(function(y){return '<button type="button" class="chip" data-v="'+y+'" aria-pressed="'+(y===year)+'">'+y+'년</button>';}).join('');
B.chips($('years'),function(v){year=+v;paint();});
$('pat').addEventListener('change',function(){$('custom').value='';var p=pat(codes());if(p&&p.teams>1)$('teams').value=p.teams;draw();});
$('custom').addEventListener('input',draw);
['first','label','teams','tnames'].forEach(function(i){$(i).addEventListener('input',paint);});
$('tmonth').addEventListener('change',paint);
$('prev').onclick=function(){shift(-1);};$('next').onclick=function(){shift(1);};
$('print').onclick=function(){window.print();};
B.shareButton($('share'),'우리 조 근무 달력');
draw();
})();'''


def print_js(fix=''):
    return (PRINT_JS.replace('%HOL%', json.dumps(HOL, ensure_ascii=False)).replace('%PATS%', json.dumps(PATS, ensure_ascii=False))
            .replace('%FIX%', fix).replace('%YEARS%', json.dumps(PRINT_YEARS)).replace('%YEAR%', str(PRINT_YEAR)))


PRINT_FAQ = [
    ('인쇄는 어떻게 하나요?', "'인쇄하기 · PDF로 저장'을 누르면 A4 가로 한 장에 열두 달이 들어가요. 휴대폰에서는 인쇄 화면의 프린터를 'PDF로 저장'으로 바꾸면 파일로 남아요."),
    ('우리 조 첫날을 모르겠어요.', "아무 근무 날이나 넣고 '하루 당기기·밀기'로 '오늘은 ○○' 줄이 오늘 내 근무와 같아질 때까지 맞추면 돼요."),
    ('조 여러 개를 한 장에 찍을 수 있나요?', "'조 나란히 (팀 근무표)'를 고르면 한 달을 한 표로, 줄마다 조 하나씩 찍어요. 조 이름과 찍을 달을 고를 수 있어요."),
    ('빨간 날짜는 무엇인가요?', '일요일과 공휴일(대체공휴일 포함)이에요. 종이 아래에 그해 공휴일 이름과 쉬는·일하는 공휴일 수가 같이 찍혀요.'),
    ('대근이나 연차로 바뀐 날은요?', '종이 달력은 순서대로만 나와요. 바뀐 날까지 담고 근무 알람도 받으려면 밤낮 앱에서 같은 순서를 고른 뒤 그날만 눌러 고쳐요.'),
]

# ---------- 인쇄용 연간 교대 달력 ----------
links = ''.join(f'<li><a href="{q["slug"]}/">{html.escape(q["name"])} 달력</a> · {html.escape(q["description"])}</li>' for q in PATS)
write('shift/print/', add_css(page(
    'shift/print/', f'{PRINT_YEAR} 교대 근무 달력 인쇄 · 연간 근무표 PDF | 밤낮',
    f'교대 순서와 첫날만 고르면 {Y_TXT}년 열두 달 근무 달력을 A4 한 장으로 인쇄·PDF 저장해요. 조 나란히도.',
    f'{PRINT_YEAR} 교대 근무 달력 인쇄', '근무 순서와 첫날만 고르면 열두 달이 A4 한 장에 들어가요. 팀 리더는 조 여러 개를 한 표에 나란히 찍어요.',
    PRINT_BODY + f'<h2>순서별 달력</h2><ul class="links">{links}</ul>', print_js(), PRINT_FAQ,
    'print', '종이 말고 휴대폰에, 알람까지', '밤낮 앱에서 같은 순서를 고르면 몇 년치 근무가 채워지고 주간·야간마다 다른 근무 알람이 울려요. 대근·연차도 눌러서 바로 고쳐요.', 2)))
for q in PATS:
    others = ''.join(f'<li><a href="../{o["slug"]}/">{html.escape(o["name"])}</a></li>' for o in PATS if o is not q)
    # [WEB-21] 제목은 이름만(설명 괄호는 본문으로)
    title = f'{PRINT_YEAR} {q["name"]} 달력 인쇄 · 연간 근무표 PDF | 밤낮'
    if len(title) > 60: title = f'{PRINT_YEAR} {q["name"]} 달력 인쇄 | 밤낮'
    write(f'shift/print/{q["slug"]}/', add_css(page(
        f'shift/print/{q["slug"]}/', title,
        (f'{PRINT_YEAR}년 {q["name"]} 근무 달력을 열두 달 한 장으로 인쇄·PDF 저장해요. 첫날만 넣으면 우리 조 달력.')[:80],
        f'{PRINT_YEAR} {q["name"]} 달력 인쇄', f'{html.escape(q["description"])} 순서예요. 우리 조 첫날만 넣으면 열두 달이 A4 한 장에 들어가요.',
        PRINT_BODY + f'<p class="note no-print"><a href="../../p/{q["slug"]}/">{html.escape(q["name"])} 순서 설명</a> · <a href="../">다른 순서 달력</a></p><h2>다른 순서</h2><ul class="links">{others}</ul>',
        print_js(q['codes']), PRINT_FAQ,
        f'print_{q["slug"]}', f'{q["name"]}, 종이 말고 휴대폰에', f'밤낮 앱에서 {josa(q["name"], "를/을")} 고르면 몇 년치 근무가 채워지고 근무마다 다른 알람이 울려요.', 3)))
print('print', len(PATS) + 1)

# ---------- 근무표 양식 (엑셀) ----------
thin = Side(style='thin', color='B8BEC8')
BOX = Border(left=thin, right=thin, top=thin, bottom=thin)
HEAD = PatternFill('solid', fgColor='EEF1F5')
FILLS = {'D': 'DCE9FD', 'E': 'FCEBD0', 'N': 'E6E0FA', 'OFF': 'F1F2F4', '휴': 'F1F2F4', '비': 'ECEDEF', '연차': 'E3F4E8', '당': 'FBE1E1'}
CENTER = Alignment(horizontal='center', vertical='center')
GREY = '5B6370'


def sheet_base(wb, title, heading, cols):
    ws = wb.active; ws.title = title
    ws['A1'] = heading; ws['A1'].font = Font(bold=True, size=14)
    ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=min(cols, 20))
    ws.sheet_view.showGridLines = False
    ws.page_setup.orientation = 'landscape'; ws.page_setup.paperSize = ws.PAPERSIZE_A4
    ws.page_setup.fitToWidth = 1; ws.page_setup.fitToHeight = 1; ws.sheet_properties.pageSetUpPr.fitToPage = True
    return ws


def footer(ws, row, cols):
    ws.cell(row=row, column=1, value='이 근무표를 팀원 휴대폰 달력과 근무 알람으로: 밤낮 앱 (무료) · bamnat.app').font = Font(size=9, color=GREY)
    ws.merge_cells(start_row=row, start_column=1, end_row=row, end_column=min(cols, 20))


def grid(ws, r0, names, codes_list, count_codes):
    # r0: 머리 줄. 이름 | 1..31 | 개수들
    ws.cell(row=r0, column=1, value='이름')
    for d in range(1, 32): ws.cell(row=r0, column=1 + d, value=d)
    for i, c in enumerate(count_codes): ws.cell(row=r0, column=33 + i, value=c)
    last_col = 32 + len(count_codes)
    for col in range(1, last_col + 1):
        cell = ws.cell(row=r0, column=col); cell.fill = HEAD; cell.font = Font(bold=True); cell.alignment = CENTER; cell.border = BOX
    for i in range(names):
        r = r0 + 1 + i
        for col in range(1, last_col + 1):
            ws.cell(row=r, column=col).border = BOX; ws.cell(row=r, column=col).alignment = CENTER
        for j, c in enumerate(count_codes):
            ws.cell(row=r, column=33 + j, value=f'=COUNTIF($B{r}:$AF{r},"{c}")')
    rng = f'B{r0+1}:AF{r0+names}'
    dv = DataValidation(type='list', formula1='"' + ','.join(codes_list) + '"', allow_blank=True, showErrorMessage=False)
    ws.add_data_validation(dv); dv.add(rng)
    for c in codes_list:
        if c in FILLS: ws.conditional_formatting.add(rng, CellIsRule(operator='equal', formula=[f'"{c}"'], fill=PatternFill('solid', fgColor=FILLS[c])))
    ws.column_dimensions['A'].width = 11
    for col in range(2, 33): ws.column_dimensions[get_column_letter(col)].width = 4.2
    for j in range(len(count_codes)): ws.column_dimensions[get_column_letter(33 + j)].width = 5.5
    # 날마다 인원
    r = r0 + names + 1
    work = [x for x in count_codes if x not in ('OFF', '휴', '비', '연차')]
    for k, c in enumerate(work):
        ws.cell(row=r + k, column=1, value=f'{c} 인원').font = Font(bold=True)
        for d in range(1, 32):
            col = get_column_letter(1 + d)
            cell = ws.cell(row=r + k, column=1 + d, value=f'=COUNTIF({col}{r0+1}:{col}{r0+names},"{c}")'); cell.alignment = CENTER; cell.border = BOX
    return r + len(work)


os.makedirs(os.path.join(ROOT, 'form/files'), exist_ok=True)
FIXED = (2026, 1, 1, 0, 0, 0)


def save(wb, name):
    """내용이 같으면 파일도 같게: 엑셀 안의 만든·고친 시각과 zip 안 파일 시각을 고정해요(공개 저장소에 날짜만 바뀐 변경이 쌓이지 않게)."""
    wb.properties.creator = '밤낮'; wb.properties.title = name
    buf = io.BytesIO(); wb.save(buf)
    src = zipfile.ZipFile(io.BytesIO(buf.getvalue()))
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        for info in src.infolist():
            data = src.read(info.filename)
            if info.filename == 'docProps/core.xml':
                data = re.sub(rb'(<dcterms:(?:created|modified)[^>]*>)[^<]*', rb'\g<1>2026-01-01T00:00:00Z', data)
            zi = zipfile.ZipInfo(info.filename, FIXED); zi.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(zi, data)
    open(os.path.join(ROOT, 'form/files', name), 'wb').write(out.getvalue())


# 1) 병동 듀티표
wb = Workbook(); ws = sheet_base(wb, '듀티표', '      년     월 근무표 (병동·부서:              )', 36)
ws['A2'] = 'D 데이 · E 이브닝 · N 나이트 · OFF 휴무 · 연차. 칸을 누르면 고를 수 있어요.'; ws['A2'].font = Font(size=9, color=GREY)
end = grid(ws, 3, 25, ['D', 'E', 'N', 'OFF', '연차'], ['D', 'E', 'N', 'OFF', '연차'])
footer(ws, end + 1, 36); ws.freeze_panes = 'B4'; save(wb, 'bamnat-duty.xlsx')
# 2) 교대 조별 근무표
wb = Workbook(); ws = sheet_base(wb, '조별 근무표', '      년     월 교대 근무표 (부서:              )', 36)
ws['A2'] = '주 주간 · 야 야간 · 오 오후 · 비 비번 · 휴 휴무. 이름 칸에 조나 사람 이름을 적어요.'; ws['A2'].font = Font(size=9, color=GREY)
end = grid(ws, 3, 12, ['주', '오', '야', '비', '휴', '연차'], ['주', '오', '야', '비', '휴'])
footer(ws, end + 1, 36); ws.freeze_panes = 'B4'; save(wb, 'bamnat-shift-team.xlsx')
# 3) 알바 주간 근무표 — [U50-14] 휴게는 한 주 합계(분)라고 머리에 적어요.
wb = Workbook(); ws = sheet_base(wb, '주간 근무표', '      년     월     일 주  알바 근무표 (매장:              )', 20)
days = '월화수목금토일'
hdr = ['이름'] + [f'{d} {x}' for d in days for x in ('출근', '퇴근')] + ['한 주 휴게 합계(분)', '주 시간', '주휴', '시급', '예상 주급']
for i, h in enumerate(hdr, 1):
    c = ws.cell(row=3, column=i, value=h); c.fill = HEAD; c.font = Font(bold=True); c.alignment = Alignment(horizontal='center', vertical='center', wrap_text=True); c.border = BOX
ws.row_dimensions[3].height = 30
ws['A2'] = '시각은 18:00처럼 적어요. 밤을 넘겨 퇴근해도 계산돼요. 휴게는 한 주에 쉰 시간을 모두 더해 분으로 적어요. 주 15시간 이상이면 주휴수당 대상이에요(참고용).'; ws['A2'].font = Font(size=9, color=GREY)
for r in range(4, 16):
    for col in range(1, len(hdr) + 1):
        ws.cell(row=r, column=col).border = BOX; ws.cell(row=r, column=col).alignment = CENTER
    for col in range(2, 16): ws.cell(row=r, column=col).number_format = 'hh:mm'
    parts = '+'.join(f'IF(AND({get_column_letter(2+2*k)}{r}<>"",{get_column_letter(3+2*k)}{r}<>""),MOD({get_column_letter(3+2*k)}{r}-{get_column_letter(2+2*k)}{r},1)*24,0)' for k in range(7))
    ws.cell(row=r, column=17, value=f'=ROUND(MAX(0,{parts}-P{r}/60),2)')
    ws.cell(row=r, column=18, value=f'=IF(Q{r}>=15,"대상","아님")')
    ws.cell(row=r, column=20, value=f'=IF(S{r}="","",ROUND(Q{r}*S{r}+IF(Q{r}>=15,MIN(Q{r},40)/40*8*S{r},0),0))')
    ws.cell(row=r, column=20).number_format = '#,##0'; ws.cell(row=r, column=19).number_format = '#,##0'
ws.column_dimensions['A'].width = 10
for col in range(2, 16): ws.column_dimensions[get_column_letter(col)].width = 7
for col, w in zip(range(16, 21), (11, 8, 6, 8, 10)): ws.column_dimensions[get_column_letter(col)].width = w
footer(ws, 17, 20); ws.freeze_panes = 'B4'; save(wb, 'bamnat-alba-week.xlsx')
# 4) 현장 출역표·일당 장부
wb = Workbook(); ws = sheet_base(wb, '출역표', '      년     월 현장 출역표 · 일당 장부 (현장:              )', 36)
ws['A2'] = '나온 날 1, 반나절 0.5. 일당을 적으면 출역 일수와 금액을 더해 줘요(세금 빼기 전).'; ws['A2'].font = Font(size=9, color=GREY)
hdr = ['이름', '일당'] + [str(d) for d in range(1, 32)] + ['일수', '금액']
for i, h in enumerate(hdr, 1):
    c = ws.cell(row=3, column=i, value=int(h) if h.isdigit() else h); c.fill = HEAD; c.font = Font(bold=True); c.alignment = CENTER; c.border = BOX
for r in range(4, 24):
    for col in range(1, len(hdr) + 1):
        ws.cell(row=r, column=col).border = BOX; ws.cell(row=r, column=col).alignment = CENTER
    ws.cell(row=r, column=2).number_format = '#,##0'
    ws.cell(row=r, column=34, value=f'=SUM(C{r}:AG{r})')
    ws.cell(row=r, column=35, value=f'=IF(B{r}="","",AH{r}*B{r})'); ws.cell(row=r, column=35).number_format = '#,##0'
ws.cell(row=24, column=1, value='합계').font = Font(bold=True)
ws.cell(row=24, column=34, value='=SUM(AH4:AH23)'); ws.cell(row=24, column=35, value='=SUM(AI4:AI23)'); ws.cell(row=24, column=35).number_format = '#,##0'
ws.column_dimensions['A'].width = 10; ws.column_dimensions['B'].width = 9
for col in range(3, 34): ws.column_dimensions[get_column_letter(col)].width = 3.6
ws.column_dimensions['AH'].width = 6; ws.column_dimensions['AI'].width = 12
footer(ws, 26, 36); ws.freeze_panes = 'C4'; save(wb, 'bamnat-site-days.xlsx')

# (slug, 파일, h1, 제목, 설명, 인쇄 칩, 인쇄 줄 수, 머리 칸, 캠페인, CTA 제목, CTA 글)
FORMS = [
    ('duty', 'bamnat-duty.xlsx', '간호사 듀티표 양식 (엑셀·인쇄)', '간호사 듀티표 양식 엑셀 · 병동 근무표 무료 다운로드',
     '병동·요양원·센터에서 쓰는 한 달 듀티표 양식이에요. D·E·N·OFF를 칸에서 고르면 색이 칠해지고, 사람별 개수와 날마다 인원을 세어 줘요.',
     ['D', 'E', 'N', 'OFF', '연차'], 16, '병동·부서', 'duty', '듀티표를 받는 사람 폰에 알람까지', '밤낮 앱에서 근무표 사진을 찍으면 내 줄을 찾아 달력에 넣고, 데이·이브닝·나이트마다 다른 알람이 울려요.'),
    ('shift-team', 'bamnat-shift-team.xlsx', '교대 근무표 양식 (조별, 엑셀)', '교대 근무표 양식 엑셀 · 3조2교대·4조2교대 조별 근무표 무료',
     '공장·발전소·경비·물류처럼 조를 나눠 도는 곳의 한 달 근무표 양식이에요. 주·오·야·비·휴를 고르면 색이 칠해지고 날마다 인원을 세어 줘요.',
     ['주', '오', '야', '비', '휴'], 16, '부서', 'shift_team', '조 근무표를 팀원 폰에', '밤낮 앱 팀 기능으로 근무표를 한 번 올리면 팀원 달력에 들어가고, 바뀌면 팀원 알람도 저절로 바뀌어요.'),
    ('alba-week', 'bamnat-alba-week.xlsx', '알바 주간 근무표 양식 (엑셀)', '알바 근무표 양식 엑셀 · 주간 스케줄표와 주휴수당 자동 계산',
     '매장 알바의 한 주 출퇴근 시각을 적으면 주 근무 시간, 주휴수당 대상, 예상 주급을 계산해 주는 양식이에요.',
     None, 0, '', 'alba_week', '알바생 폰에 근무와 예상 월급까지', '밤낮 앱에서 사장님이 직원 근무표를 보내면 직원 폰에 근무와 알람, 이번 달 예상 월급까지 보여요.'),
    ('site-days', 'bamnat-site-days.xlsx', '현장 출역표·일당 장부 양식 (엑셀)', '출역표 양식 엑셀 · 현장 일용직 일당 장부 무료',
     '인테리어·건설 현장의 한 달 출역표예요. 나온 날에 1(반나절 0.5)을 적으면 사람별 일수와 일당 합계를 계산해요.',
     None, 0, '', 'site_days', '기사님께 일정과 일당을 링크로', '밤낮 앱에서 사장님이 현장 일정을 보내면 기사님 폰에 일정·출퇴근이 들어가고, 일당도 같이 기록돼요.'),
]

SHOW_ROWS = 5  # 화면에는 다섯 줄만, 인쇄하면 종이를 채우는 줄 수


def preview(codes, rows, where):
    """[WEB-14] 빈 양식: 손으로 쓸 수 있는 9mm 칸, 엑셀 양식처럼 해·달·부서 칸."""
    if not codes: return ''
    head = '<tr><th scope="col">이름</th>' + ''.join(f'<th scope="col">{d}</th>' for d in range(1, 32)) + ''.join(f'<th scope="col" class="cnt">{c}</th>' for c in codes) + '</tr>'
    cells = '<td class="name"></td>' + '<td></td>' * (31 + len(codes))
    body = ''.join(('<tr class="more">' if i >= SHOW_ROWS else '<tr>') + cells + '</tr>' for i in range(rows))
    return (f'<div class="card"><div class="fhead"><span>　　　　년 　　월 근무표</span><span>{where}: 　　　　　　　　</span></div>'
            f'<div class="wrap"><table class="form"><thead>{head}</thead><tbody>{body}</tbody></table></div>'
            f'<p class="note no-print">화면에는 {SHOW_ROWS}줄만 보여요. 인쇄하면 A4 가로 한 장에 {rows}줄, 칸 높이 9mm로 나와요.</p>'
            '<div class="print-only">' + ' · '.join(codes) + ' · 밤낮 · bamnat.app — 이 근무표를 팀원 폰 달력·알람으로</div></div>')


form_links = []
for slug, fn, h1, title, lead, codes, rows, where, camp, cta_t, cta_x in FORMS:
    body = f'''<div class="btns no-print">
  <a class="btn" href="../files/{fn}" download>엑셀 양식 받기 (.xlsx)</a>
  {'<button type="button" class="chip" id="print">빈 양식 바로 인쇄</button>' if codes else ''}
</div>
<p class="note">엑셀·구글 시트·한셀에서 열려요. 구글 시트는 드라이브에 올린 뒤 'Google 스프레드시트로 열기'를 눌러요.</p>
{preview(codes, rows, where)}
<h2>다른 양식</h2>
<ul class="links">''' + ''.join(f'<li><a href="../{o[0]}/">{html.escape(o[2])}</a></li>' for o in FORMS if o[0] != slug) + f'<li><a href="../../shift/print/">{PRINT_YEAR} 교대 근무 달력 인쇄 (조 나란히도)</a></li></ul>'
    faqs = [('무료인가요?', '네, 로그인 없이 받아서 마음대로 고쳐 써도 돼요.'),
            ('근무표를 사람마다 따로 보내기 번거로워요.', '밤낮 앱에 근무표를 한 번 올리면 받는 사람 폰 달력에 들어가고, 근무가 바뀌면 그 사람 알람도 저절로 바뀌어요.')]
    if codes:
        faqs.insert(1, ('빈 양식을 바로 인쇄하면 몇 줄인가요?', f'A4 가로 한 장에 {rows}줄, 칸 높이 9mm라 손으로 적기 좋아요. 위에 해·달과 {where}을 적는 칸이 있어요.'))
    desc = lead if len(lead) <= 80 else lead.split('. ')[0] + '.'
    write(f'form/{slug}/', add_css(page(f'form/{slug}/', title + ' | 밤낮', desc, h1, lead, body,
        "var b=document.getElementById('print');if(b)b.onclick=function(){window.print();};", faqs, f'form_{camp}', cta_t, cta_x, 2)))
    form_links.append((slug, h1))
hub_body = '<ul class="links">' + ''.join(f'<li><a href="{s}/"><strong>{html.escape(h)}</strong></a></li>' for s, h in form_links) + f'<li><a href="../shift/print/"><strong>{PRINT_YEAR} 교대 근무 달력 인쇄</strong></a><br><span class="muted">순서와 첫날로 열두 달 한 장, 조 나란히도</span></li></ul>'
write('form/', add_css(page('form/', '근무표 양식 무료 다운로드 (듀티표·교대 근무표·알바 스케줄표·출역표) | 밤낮',
    '간호사 듀티표·교대 조별 근무표·알바 스케줄표·현장 출역표 엑셀 양식을 무료로 받아요. 개수·시간·금액은 저절로.',
    '근무표 양식 모음', '엑셀로 바로 쓰는 무료 근무표 양식이에요. 근무를 고르면 색과 개수가 저절로 바뀌어요.', hub_body, '',
    [('어떤 프로그램에서 열리나요?', '엑셀, 구글 시트, 한셀, 휴대폰 엑셀 앱에서 열려요.')], 'form_hub', '양식 대신 앱으로', '밤낮 앱은 근무표 사진을 찍으면 달력이 채워지고, 근무마다 다른 알람과 예상 월급까지 보여요.', 1)))
print('forms', len(FORMS) + 1)
