# 3차 검색용 페이지: 인쇄용 연간 교대 달력(shift/print/)과 근무표 양식(form/).
# public_pages.py를 먼저 돌린 뒤 돌려요: python3 print_pages.py ../-   (같은 폴더의 public_pages.py에서 page()를 빌려 씀)
import json, os, html, sys, re
HERE = os.path.dirname(os.path.abspath(__file__))
_src = open(os.path.join(HERE, 'public_pages.py'), encoding='utf-8').read().split('# ---------- 주휴수당')[0]
exec(_src)  # ROOT, SITE, page(), write()

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.formatting.rule import CellIsRule
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation

def remote(name): return json.load(open(os.path.join(ROOT, 'remote/v1', name + '.json'), encoding='utf-8'))['data']
pre = [p for p in remote('presets')['patterns'] if p['codes'] != 'DDDDDOO']
HOL = {h['date']: h['name'] for h in remote('holidays')['days'] if h['date'][:4] in ('2026', '2027')}
NEW = []
TODAY = LASTMOD  # public_pages.py의 사이트맵 날짜와 같게

PRINT_CSS = '''<style>
.year { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
@media (min-width: 560px) { .year { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
.mini h3 { font-size: 14px; margin: 0 0 4px; }
.mini .g { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 1px; font-size: 10px; text-align: center; }
.mini .g .h { color: var(--text-2); }
.mini .g .d { background: var(--surface-2); border-radius: 3px; padding: 1px 0 2px; line-height: 1.15; }
.mini .g .d b { display: block; font-weight: 400; color: var(--text-2); font-size: 9px; }
.mini .g .d i { display: block; font-style: normal; font-weight: 700; border-radius: 3px; color: #fff; margin: 0 1px; }
.mini .g .d.hol b, .mini .g .su { color: var(--duty) !important; }
.mini .g .d i.O { color: var(--off); }
.mini .g i.D { background: var(--day); } .mini .g i.E { background: var(--eve); } .mini .g i.N { background: var(--night); }
.mini .g i.X { background: var(--duty); } .mini .g i.B { background: var(--off); }
.legend { display: flex; flex-wrap: wrap; gap: 6px 12px; font-size: 13px; margin: 8px 0; }
.legend span i { display: inline-block; width: 12px; height: 12px; border-radius: 3px; vertical-align: -1px; margin-right: 4px; }
.ptitle { font-size: 18px; font-weight: 700; margin: 4px 0 2px; }
.print-only { display: none; }
.wrap { overflow-x: auto; -webkit-overflow-scrolling: touch; }
table.form { border-collapse: collapse; font-size: 11px; min-width: 100%; }
table.form th, table.form td { border: 1px solid var(--line); padding: 3px 4px; text-align: center; white-space: nowrap; }
table.form th { background: var(--surface-2); font-weight: 600; }
table.form td.name { min-width: 64px; }
.btns { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; }
@media print {
  @page { size: A4 landscape; margin: 8mm; }
  html, body { background: #fff !important; color: #000 !important; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  main { max-width: none; padding: 0; }
  nav.top, .controls, .cta, h2, details, footer, .lead, h1, .no-print, .note, .result, ul.links { display: none !important; }
  .year { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 6px 10px; }
  .card { border: 0; padding: 0; margin: 0; background: #fff; }
  .mini .g .d { background: #f1f2f4; }
  .print-only { display: block; font-size: 10px; color: #444; margin-top: 6px; }
  .wrap { overflow: visible; }
  table.form { font-size: 9px; }
  table.form th, table.form td { border-color: #999; padding: 2px 3px; }
}
</style>'''

def add_css(s): return s.replace('</head>', PRINT_CSS + '\n</head>', 1)

PATS = [{'slug': p['id'][2:], 'name': p['name'], 'description': p['description'], 'codes': p['codes']} for p in pre]

PRINT_BODY = '''<div class="card controls">
  <label for="pat">근무 순서</label>
  <select id="pat"></select>
  <label for="custom">직접 적기<span class="hint">주=D 오후=E 야=N 휴=O 당번=X 비번=B. 예: DDNNOO</span></label>
  <input id="custom" autocomplete="off" autocapitalize="characters">
  <label for="first">이 순서의 첫날<span class="hint">순서 맨 앞 근무를 하는 날 아무 날이나. 예: 주야비휴면 주간 하는 날.</span></label>
  <input id="first" type="date">
  <div class="chips" style="margin-top:10px">
    <button type="button" class="chip" id="prev">◀ 하루 당기기</button>
    <button type="button" class="chip" id="next">하루 밀기 ▶</button>
    <span class="note" id="todayis" style="align-self:center"></span>
  </div>
  <label for="label">달력 위에 적을 이름<span class="hint">예: 3조, 홍길동 (빈칸이면 안 적어요)</span></label>
  <input id="label" maxlength="20" autocomplete="off">
  <div class="chips" id="years"></div>
  <div class="btns">
    <button type="button" class="btn" id="print">인쇄하기 · PDF로 저장</button>
    <button type="button" class="chip" id="share">같은 조 동료에게 보내기</button>
  </div>
  <span class="note" id="shared"></span>
</div>
<div class="card">
  <div class="ptitle" id="ptitle"></div>
  <div class="legend" id="legend"></div>
  <div class="year" id="year"></div>
  <div class="print-only">밤낮 · bamnat.app — 이 달력을 휴대폰에, 근무마다 다른 알람까지. 빨간 날짜는 공휴일이에요.</div>
</div>
<div class="card result" id="sum" aria-live="polite"></div>'''

PRINT_JS = '''(function(){
var B=Bamnat,$=function(i){return document.getElementById(i)};
var NAME={D:'주',E:'오',N:'야',O:'휴',X:'당',B:'비'},FULL={D:'주간',E:'오후',N:'야간',O:'휴무',X:'당번',B:'비번'};
var HOL=%HOL%,PATS=%PATS%,FIX='%FIX%',year=2027;
function iso(d){return d.getFullYear()+'-'+('0'+(d.getMonth()+1)).slice(-2)+'-'+('0'+d.getDate()).slice(-2);}
function esc(s){return String(s||'').replace(/[&<>"]/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];});}
function codes(){var c=$('custom').value.toUpperCase().replace(/[^DENOXB]/g,'');return c||$('pat').value;}
function pname(c){for(var i=0;i<PATS.length;i++)if(PATS[i].codes===c)return PATS[i].name;return '교대';}
function draw(){
  var c=codes(),f=$('first').value;if(!c||!f)return;
  var p=f.split('-'),start=new Date(+p[0],+p[1]-1,+p[2]);
  var at=function(d){var k=Math.round((d-start)/864e5)%c.length;if(k<0)k+=c.length;return c[k];};
  var now=new Date();now=new Date(now.getFullYear(),now.getMonth(),now.getDate());
  $('todayis').textContent='오늘('+(now.getMonth()+1)+'/'+now.getDate()+')은 '+(FULL[at(now)]||at(now))+'. 우리 조와 맞는지 확인해요.';
  var out='',cnt={},nights=0,work=0,holOff=[],holWork=[];
  for(var m=0;m<12;m++){
    var first=new Date(year,m,1),last=new Date(year,m+1,0);
    out+='<div class="mini"><h3>'+(m+1)+'월</h3><div class="g">';
    '일월화수목금토'.split('').forEach(function(w,i){out+='<div class="h'+(i===0?' su':'')+'">'+w+'</div>';});
    for(var i=0;i<first.getDay();i++)out+='<div></div>';
    for(var d=1;d<=last.getDate();d++){
      var day=new Date(year,m,d),s=at(day),k=iso(day),h=HOL[k];
      cnt[s]=(cnt[s]||0)+1;if(s==='N')nights++;if(s!=='O'&&s!=='B')work++;
      if(h){(s==='O'||s==='B'?holOff:holWork).push((m+1)+'/'+d+' '+h);}
      out+='<div class="d'+(h||day.getDay()===0?' hol':'')+'"'+(h?' title="'+esc(h)+'"':'')+'><b>'+d+'</b><i class="'+s+'">'+(NAME[s]||s)+'</i></div>';
    }
    out+='</div></div>';
  }
  $('year').innerHTML=out;
  var lab=$('label').value.trim();
  $('ptitle').textContent=year+'년 '+pname(c)+' 근무 달력'+(lab?' · '+lab:'');
  $('legend').innerHTML=c.split('').filter(function(x,i,a){return a.indexOf(x)===i;}).map(function(x){return '<span><i class="s '+x+'" style="background:var(--'+({D:'day',E:'eve',N:'night',O:'off',X:'duty',B:'off'})[x]+')"></i>'+FULL[x]+'</span>';}).join('');
  $('sum').innerHTML='<strong>'+year+'년 한눈에</strong><table class="lines"><tr><td>일하는 날</td><td>'+work+'일</td></tr>'+(nights?'<tr><td>야간</td><td>'+nights+'번</td></tr>':'')+
    '<tr><td>쉬는 공휴일</td><td>'+holOff.length+'일</td></tr><tr><td>일하는 공휴일</td><td>'+holWork.length+'일</td></tr></table>'+
    (holWork.length?'<p class="note">일하는 공휴일: '+esc(holWork.join(', '))+'</p>':'')+(holOff.length?'<p class="note">쉬는 공휴일: '+esc(holOff.join(', '))+'</p>':'');
  try{history.replaceState(null,'','?p='+c+'&s='+f+'&y='+year+(lab?'&t='+encodeURIComponent(lab):''));}catch(e){}
}
function shift(n){var p=$('first').value.split('-'),d=new Date(+p[0],+p[1]-1,+p[2]+n);$('first').value=iso(d);draw();}
var qs=new URLSearchParams(location.search);
$('pat').innerHTML=PATS.map(function(p){return '<option value="'+esc(p.codes)+'">'+esc(p.name)+' · '+esc(p.description)+'</option>';}).join('');
if(FIX)$('pat').value=FIX;
if(qs.get('p')){var q=qs.get('p').toUpperCase().replace(/[^DENOXB]/g,'');if(PATS.some(function(p){return p.codes===q;}))$('pat').value=q;else $('custom').value=q;}
$('first').value=qs.get('s')||iso(new Date());
if(qs.get('y')==='2026')year=2026;
$('label').value=qs.get('t')||'';
$('years').innerHTML=[2026,2027].map(function(y){return '<button type="button" class="chip" data-y="'+y+'" aria-pressed="'+(y===year)+'">'+y+'년</button>';}).join('');
$('years').querySelectorAll('button').forEach(function(b){b.onclick=function(){year=+b.dataset.y;$('years').querySelectorAll('button').forEach(function(x){x.setAttribute('aria-pressed',x===b);});draw();};});
$('pat').addEventListener('change',function(){$('custom').value='';draw();});
['custom','first','label'].forEach(function(i){$(i).addEventListener('input',draw);});
$('prev').onclick=function(){shift(-1);};$('next').onclick=function(){shift(1);};
$('print').onclick=function(){window.print();};
$('share').onclick=function(){var u=location.href,t=year+'년 우리 조 근무 달력';
  if(navigator.share){navigator.share({title:t,url:u}).catch(function(){});}
  else if(navigator.clipboard){navigator.clipboard.writeText(u).then(function(){$('shared').textContent=' 링크를 복사했어요. 카톡에 붙여 넣어 보내요.';});}};
draw();
})();'''

def print_js(fix=''):
    return PRINT_JS.replace('%HOL%', json.dumps(HOL, ensure_ascii=False)).replace('%PATS%', json.dumps(PATS, ensure_ascii=False)).replace('%FIX%', fix)

PRINT_FAQ = [
    ('인쇄는 어떻게 하나요?', "'인쇄하기 · PDF로 저장'을 누르면 A4 가로 한 장에 열두 달이 들어가요. 휴대폰에서는 인쇄 화면의 프린터를 'PDF로 저장'으로 바꾸면 파일로 남아요."),
    ('우리 조 첫날을 모르겠어요.', "아무 근무 날이나 넣고 '하루 당기기·밀기'로 '오늘은 ○○' 줄이 오늘 내 근무와 같아질 때까지 맞추면 돼요."),
    ('빨간 날짜는 무엇인가요?', '일요일과 공휴일(대체공휴일 포함)이에요. 아래 한눈에 칸에서 올해 쉬는 공휴일과 일하는 공휴일을 따로 세어 줘요.'),
    ('대근이나 연차로 바뀐 날은요?', '종이 달력은 순서대로만 나와요. 바뀐 날까지 담고 근무 알람도 받으려면 밤낮 앱에서 같은 순서를 고른 뒤 그날만 눌러 고쳐요.'),
]

# ---------- 인쇄용 연간 교대 달력 ----------
links = ''.join(f'<li><a href="{q["slug"]}/">{y} {html.escape(q["name"])} 달력</a> · {html.escape(q["description"])}</li>' for q in PATS for y in ['2027'])
s = page('shift/print/', '2027 교대 근무 달력 인쇄 · 4조2교대·3조2교대 연간 근무표 PDF | 밤낮',
    '4조2교대, 3조2교대, 4조3교대, 24시간 맞교대 순서와 첫날만 고르면 2027년 열두 달 근무 달력을 A4 한 장으로 인쇄하거나 PDF로 저장해요. 공휴일과 야간 횟수도 세어 줘요.',
    '2027 교대 근무 달력 인쇄', '근무 순서와 첫날만 고르면 열두 달이 A4 한 장에 들어가요. 벽이나 사물함에 붙여 두고, 같은 조 동료에게 링크로 보내요.',
    PRINT_BODY + f'<h2>순서별 2027 달력</h2><ul class="links">{links}</ul>', print_js(), PRINT_FAQ,
    'print', '종이 말고 휴대폰에, 알람까지', '밤낮 앱에서 같은 순서를 고르면 몇 년치 근무가 채워지고 주간·야간마다 다른 근무 알람이 울려요. 대근·연차도 눌러서 바로 고쳐요.', 2)
write('shift/print/', add_css(s)); NEW.append('shift/print/')
for q in PATS:
    others = ''.join(f'<li><a href="../{o["slug"]}/">{html.escape(o["name"])}</a></li>' for o in PATS if o is not q)
    s = page(f'shift/print/{q["slug"]}/', f'2027 {q["name"]} 달력 인쇄 ({q["description"]}) · 연간 근무표 PDF | 밤낮',
        f'2027년 {q["name"]}({q["description"]}) 근무 달력을 열두 달 한 장으로 인쇄하거나 PDF로 저장해요. 첫날만 넣으면 우리 조 달력이 되고, 공휴일·야간 횟수도 세어 줘요.',
        f'2027 {q["name"]} 달력 인쇄', f'{html.escape(q["description"])} 순서예요. 우리 조 첫날만 넣으면 열두 달이 A4 한 장에 들어가요.',
        PRINT_BODY + f'<p class="note no-print"><a href="../../p/{q["slug"]}/">{html.escape(q["name"])} 순서 설명</a> · <a href="../">다른 순서 달력</a></p><h2>다른 순서</h2><ul class="links">{others}</ul>',
        print_js(q['codes']), PRINT_FAQ,
        f'print_{q["slug"]}', f'{q["name"]}, 종이 말고 휴대폰에', f'밤낮 앱에서 {q["name"]}를 고르면 몇 년치 근무가 채워지고 근무마다 다른 알람이 울려요.', 3)
    write(f'shift/print/{q["slug"]}/', add_css(s)); NEW.append(f'shift/print/{q["slug"]}/')
print('print', len(PATS) + 1)

# ---------- 근무표 양식 (엑셀) ----------
thin = Side(style='thin', color='B8BEC8')
BOX = Border(left=thin, right=thin, top=thin, bottom=thin)
HEAD = PatternFill('solid', fgColor='EEF1F5')
FILLS = {'D': 'DCE9FD', 'E': 'FCEBD0', 'N': 'E6E0FA', 'OFF': 'F1F2F4', '휴': 'F1F2F4', '비': 'ECEDEF', '연차': 'E3F4E8', '당': 'FBE1E1'}
CENTER = Alignment(horizontal='center', vertical='center')

def sheet_base(wb, title, heading, cols):
    ws = wb.active; ws.title = title
    ws['A1'] = heading; ws['A1'].font = Font(bold=True, size=14)
    ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=min(cols, 20))
    ws.sheet_view.showGridLines = False
    ws.page_setup.orientation = 'landscape'; ws.page_setup.paperSize = ws.PAPERSIZE_A4
    ws.page_setup.fitToWidth = 1; ws.page_setup.fitToHeight = 1; ws.sheet_properties.pageSetUpPr.fitToPage = True
    return ws

def footer(ws, row, cols):
    ws.cell(row=row, column=1, value='이 근무표를 팀원 휴대폰 달력과 근무 알람으로: 밤낮 앱 (무료) · bamnat.app').font = Font(size=9, color='5B6370')
    ws.merge_cells(start_row=row, start_column=1, end_row=row, end_column=min(cols, 20))

def grid(ws, r0, names, codes_list, extra_cols, count_codes):
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
    for k, c in enumerate([x for x in count_codes if x not in ('OFF', '휴', '비', '연차')]):
        ws.cell(row=r + k, column=1, value=f'{c} 인원').font = Font(bold=True)
        for d in range(1, 32):
            col = get_column_letter(1 + d)
            cell = ws.cell(row=r + k, column=1 + d, value=f'=COUNTIF({col}{r0+1}:{col}{r0+names},"{c}")'); cell.alignment = CENTER; cell.border = BOX
    return r + len([x for x in count_codes if x not in ('OFF', '휴', '비', '연차')])

os.makedirs(os.path.join(ROOT, 'form/files'), exist_ok=True)
def save(wb, name):
    wb.properties.creator = '밤낮'; wb.properties.title = name
    wb.save(os.path.join(ROOT, 'form/files', name))

# 1) 병동 듀티표
wb = Workbook(); ws = sheet_base(wb, '듀티표', '      년     월 근무표 (병동·부서:              )', 36)
ws['A2'] = 'D 데이 · E 이브닝 · N 나이트 · OFF 휴무 · 연차. 칸을 누르면 고를 수 있어요.'; ws['A2'].font = Font(size=9, color='5B6370')
end = grid(ws, 3, 25, ['D', 'E', 'N', 'OFF', '연차'], 0, ['D', 'E', 'N', 'OFF', '연차'])
footer(ws, end + 1, 36); ws.freeze_panes = 'B4'; save(wb, 'bamnat-duty.xlsx')
# 2) 교대 조별 근무표
wb = Workbook(); ws = sheet_base(wb, '조별 근무표', '      년     월 교대 근무표 (부서:              )', 36)
ws['A2'] = '주 주간 · 야 야간 · 오 오후 · 비 비번 · 휴 휴무. 이름 칸에 조나 사람 이름을 적어요.'; ws['A2'].font = Font(size=9, color='5B6370')
end = grid(ws, 3, 12, ['주', '오', '야', '비', '휴', '연차'], 0, ['주', '오', '야', '비', '휴'])
footer(ws, end + 1, 36); ws.freeze_panes = 'B4'; save(wb, 'bamnat-shift-team.xlsx')
# 3) 알바 주간 근무표
wb = Workbook(); ws = sheet_base(wb, '주간 근무표', '      년     월     일 주  알바 근무표 (매장:              )', 20)
days = '월화수목금토일'
hdr = ['이름'] + [f'{d} {x}' for d in days for x in ('출근', '퇴근')] + ['휴게(분)', '주 시간', '주휴', '시급', '예상 주급']
for i, h in enumerate(hdr, 1):
    c = ws.cell(row=3, column=i, value=h); c.fill = HEAD; c.font = Font(bold=True); c.alignment = CENTER; c.border = BOX
ws['A2'] = '시각은 18:00처럼 적어요. 밤을 넘겨 퇴근해도 계산돼요. 주 15시간 이상이면 주휴수당 대상이에요(참고용).'; ws['A2'].font = Font(size=9, color='5B6370')
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
for col, w in zip(range(16, 21), (8, 8, 6, 8, 10)): ws.column_dimensions[get_column_letter(col)].width = w
footer(ws, 17, 20); ws.freeze_panes = 'B4'; save(wb, 'bamnat-alba-week.xlsx')
# 4) 현장 출역표·일당 장부
wb = Workbook(); ws = sheet_base(wb, '출역표', '      년     월 현장 출역표 · 일당 장부 (현장:              )', 36)
ws['A2'] = '나온 날 1, 반나절 0.5. 일당을 적으면 출역 일수와 금액을 더해 줘요(세금 빼기 전).'; ws['A2'].font = Font(size=9, color='5B6370')
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

FORMS = [
    ('duty', 'bamnat-duty.xlsx', '간호사 듀티표 양식 (엑셀·인쇄)', '간호사 듀티표 양식 엑셀 · 병동 근무표 무료 다운로드',
     '병동·요양원·센터에서 쓰는 한 달 듀티표 양식이에요. D·E·N·OFF를 칸에서 고르면 색이 칠해지고, 사람별 개수와 날마다 인원을 세어 줘요.',
     ['D', 'E', 'N', 'OFF', '연차'], 8, 'duty', 'shift', '듀티표를 받는 사람 폰에 알람까지', '밤낮 앱에서 근무표 사진을 찍으면 내 줄을 찾아 달력에 넣고, 데이·이브닝·나이트마다 다른 알람이 울려요.'),
    ('shift-team', 'bamnat-shift-team.xlsx', '교대 근무표 양식 (조별, 엑셀)', '교대 근무표 양식 엑셀 · 3조2교대·4조2교대 조별 근무표 무료',
     '공장·발전소·경비·물류처럼 조를 나눠 도는 곳의 한 달 근무표 양식이에요. 주·오·야·비·휴를 고르면 색이 칠해지고 날마다 인원을 세어 줘요.',
     ['주', '오', '야', '비', '휴'], 4, 'shift_team', 'shift', '조 근무표를 팀원 폰에', '밤낮 앱 팀 기능으로 근무표를 한 번 올리면 팀원 달력에 들어가고, 바뀌면 팀원 알람도 저절로 바뀌어요.'),
    ('alba-week', 'bamnat-alba-week.xlsx', '알바 주간 근무표 양식 (엑셀)', '알바 근무표 양식 엑셀 · 주간 스케줄표와 주휴수당 자동 계산',
     '매장 알바의 한 주 출퇴근 시각을 적으면 주 근무 시간, 주휴수당 대상, 예상 주급을 계산해 주는 양식이에요.',
     None, 0, 'alba_week', 'calc', '알바생 폰에 근무와 예상 월급까지', '밤낮 앱에서 사장님이 직원 근무표를 보내면 직원 폰에 근무와 알람, 이번 달 예상 월급까지 보여요.'),
    ('site-days', 'bamnat-site-days.xlsx', '현장 출역표·일당 장부 양식 (엑셀)', '출역표 양식 엑셀 · 현장 일용직 일당 장부 무료',
     '인테리어·건설 현장의 한 달 출역표예요. 나온 날에 1(반나절 0.5)을 적으면 사람별 일수와 일당 합계를 계산해요.',
     None, 0, 'site_days', 'calc', '기사님께 일정과 일당을 링크로', '밤낮 앱에서 사장님이 현장 일정을 보내면 기사님 폰에 일정·출퇴근이 들어가고, 일당도 같이 기록돼요.'),
]

def preview(codes, rows):
    if not codes: return ''
    seq = (codes * 20)
    head = '<tr><th>이름</th>' + ''.join(f'<th>{d}</th>' for d in range(1, 32)) + ''.join(f'<th>{c}</th>' for c in codes) + '</tr>'
    body = ''.join('<tr><td class="name"></td>' + ''.join('<td></td>' for _ in range(31)) + ''.join('<td></td>' for _ in codes) + '</tr>' for _ in range(rows))
    return f'<div class="card"><div class="ptitle">　　년 　월 근무표</div><div class="wrap"><table class="form">{head}{body}</table></div><div class="print-only">밤낮 · bamnat.app — 이 근무표를 팀원 폰 달력·알람으로</div></div>'

form_links = []
for slug, fn, h1, title, lead, codes, rows, camp, kind, cta_t, cta_x in FORMS:
    body = f'''<div class="btns no-print">
  <a class="btn" href="../files/{fn}" download>엑셀 양식 받기 (.xlsx)</a>
  {'<button type="button" class="chip" id="print">빈 양식 바로 인쇄</button>' if codes else ''}
</div>
<p class="note">엑셀·구글 시트·한셀에서 열려요. 구글 시트는 드라이브에 올린 뒤 'Google 스프레드시트로 열기'를 눌러요.</p>
{preview(codes, rows)}
<h2>다른 양식</h2>
<ul class="links">''' + ''.join(f'<li><a href="../{o[0]}/">{html.escape(o[2])}</a></li>' for o in FORMS if o[0] != slug) + '<li><a href="../../shift/print/">2027 교대 근무 달력 인쇄</a></li></ul>'
    faqs = [('무료인가요?', '네, 로그인 없이 받아서 마음대로 고쳐 써도 돼요.'),
            ('근무표를 사람마다 따로 보내기 번거로워요.', '밤낮 앱에 근무표를 한 번 올리면 받는 사람 폰 달력에 들어가고, 근무가 바뀌면 그 사람 알람도 저절로 바뀌어요.')]
    s = page(f'form/{slug}/', title + ' | 밤낮', lead, h1, lead, body, "var b=document.getElementById('print');if(b)b.onclick=function(){window.print();};", faqs, f'form_{camp}', cta_t, cta_x, 2)
    s = s.replace('<a href="../../calc/">근무·급여 계산기</a>', '<a href="../">근무표 양식</a>', 1)
    write(f'form/{slug}/', add_css(s)); NEW.append(f'form/{slug}/'); form_links.append((slug, h1))
hub_body = '<ul class="links">' + ''.join(f'<li><a href="{s}/"><strong>{html.escape(h)}</strong></a></li>' for s, h in form_links) + '<li><a href="../shift/print/"><strong>2027 교대 근무 달력 인쇄</strong></a><br><span class="muted">순서와 첫날로 열두 달 한 장</span></li></ul>'
s = page('form/', '근무표 양식 무료 다운로드 (듀티표·교대 근무표·알바 스케줄표·출역표 엑셀) | 밤낮',
    '간호사 듀티표, 교대 조별 근무표, 알바 주간 근무표, 현장 출역표·일당 장부 엑셀 양식을 무료로 받아요. 칸을 고르면 색이 칠해지고 개수·시간·금액을 세어 줘요.',
    '근무표 양식 모음', '엑셀로 바로 쓰는 무료 근무표 양식이에요. 근무를 고르면 색과 개수가 저절로 바뀌어요.', hub_body, '',
    [('어떤 프로그램에서 열리나요?', '엑셀, 구글 시트, 한셀, 휴대폰 엑셀 앱에서 열려요.')], 'form_hub', '양식 대신 앱으로', '밤낮 앱은 근무표 사진을 찍으면 달력이 채워지고, 근무마다 다른 알람과 예상 월급까지 보여요.', 1)
write('form/', add_css(s)); NEW.append('form/')
print('forms', len(FORMS) + 1)

# ---------- 기존 쪽에 이어 붙이기·사이트맵 ----------
def patch(path, old, new, mark):
    full = os.path.join(ROOT, path); t = open(full, encoding='utf-8').read()
    if mark in t: return
    if old not in t: print('patch miss', path); return
    open(full, 'w', encoding='utf-8').write(t.replace(old, new, 1))
patch('shift/index.html', '<h2 id="order-list">', '<p><a href="print/">2027 연간 달력 인쇄하기 (A4 한 장)</a> · <a href="../form/">근무표 엑셀 양식</a></p>\n<h2 id="order-list">', 'href="print/"')
for q in PATS:
    patch(f'shift/p/{q["slug"]}/index.html', '내 첫날로 석 달 달력 보기</a></p>', f'내 첫날로 석 달 달력 보기</a> <a class="chip" href="../../print/{q["slug"]}/">2027 달력 인쇄</a></p>', '../../print/')
patch('calc/index.html', '쉬는 공휴일 확인</span></li>\n</ul>', '쉬는 공휴일 확인</span></li>\n<li><a href="../form/"><strong>근무표 양식 (엑셀)</strong></a><br><span class="muted">듀티표·조별 근무표·알바 스케줄표·출역표</span></li>\n<li><a href="../shift/print/"><strong>2027 교대 근무 달력 인쇄</strong></a></li>\n</ul>', '근무표 양식 (엑셀)')
sm_path = os.path.join(ROOT, 'sitemap.xml'); sm = open(sm_path, encoding='utf-8').read()
add = ''.join(f'  <url><loc>{SITE}{u}</loc><lastmod>{TODAY}</lastmod></url>\n' for u in NEW if f'<loc>{SITE}{u}</loc>' not in sm)
open(sm_path, 'w', encoding='utf-8').write(sm.replace('</urlset>', add + '</urlset>'))
print('new', len(NEW), 'sitemap', sm.count('<url>') + add.count('<url>'))
