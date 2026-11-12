# 검색용 공개 쪽: 계산기(calc/), 교대 달력(shift/), 공휴일(holiday/), 순서별 설명(shift/p/).
# build.py가 돌려요: python3 build.py <YWJMoney/- 폴더>. 계산은 calc/calc.js의 Bamnat.f(앱과 같은 식)만 써요.
import html
import json

from site_common import *

DATA_MONEY = 'inputmode="numeric" autocomplete="off" data-money'
SHARE = '<div class="share"><button type="button" class="chip" id="share">결과 링크 보내기</button></div>'


def result(head='', big_id='out'):
    return f'''<div class="card result">
  <div class="muted" id="head">{head}</div>
  <div class="big" id="{big_id}" aria-live="polite">-</div>
  <table class="lines" id="lines"></table>
  {SHARE}
</div>'''


YEARS_TXT = f'{THIS}·{NEXT}' if NEXT else f'{THIS}'


def min_wage_faq():
    y = NEXT or THIS
    m = rate(y, 'minimumHourlyWage')
    return (f'{y}년 최저시급은 얼마인가요?', f'{y}년 최저시급은 {m:,}원으로 고시됐어요. 주 40시간이면 주휴 포함 월 환산액은 {m * 209:,}원이에요(209시간 기준).')


def ins_faq_text(y):
    p, h, c, u = rate(y, 'pensionRate'), rate(y, 'healthRate'), rate(y, 'longTermCareRate'), rate(y, 'unemploymentRate')
    return f'국민연금 {pct(p / 2)}({y}년), 건강보험 {pct(h / 2)}, 장기요양(건강보험료×{pct(c / h, 2)}), 고용보험 {pct(u / 2)}'


# ---------- 주휴수당 ----------
write('calc/juhyu/', page(
    'calc/juhyu/', f'주휴수당 계산기 ({YEARS_TXT} 최저시급) | 밤낮',
    f'시급과 1주 근무 시간만 넣으면 주휴수당과 월 환산액을 바로 계산해요. 주 15시간 조건, {YEARS_TXT} 최저시급 반영.',
    '주휴수당 계산기', '시급과 1주에 일하기로 한 시간만 넣으면 돼요. 알바·파트타임·교대 근무 모두 같은 방법으로 계산해요.',
    f'''<div class="card">
  <label for="wage">시급 (원)</label>
  <input id="wage" {DATA_MONEY} data-max="1000000">
  <div class="chips" id="years" role="group" aria-label="최저시급으로 채우기"></div>
  <div class="row">
    <div><label for="days">1주 근무일</label><input id="days" inputmode="decimal" value="5" data-max="7" data-unit="일"></div>
    <div><label for="hours">하루 근무 시간</label><input id="hours" inputmode="decimal" value="4" data-max="24" data-unit="시간"></div>
  </div>
  <span class="hint">휴게 시간은 빼고 적어요. 주마다 다르면 평균으로 넣어요.</span>
</div>
{result('1주 주휴수당')}''',
    r'''(function(){
var B=Bamnat,F=B.f,$=B.$,year=new Date().getFullYear(),save=function(){};
function calc(){
  var w=B.val('wage'),wm=Math.round(B.val('days')*B.val('hours')*60),wk=wm/60;
  if(!w||!wm){$('out').textContent='-';$('lines').innerHTML='';save();return;}
  var j=F.juhyu(w,wm),rows=[['1주 근무 시간',B.fmt(wk)+'시간'+(wk>40?' (40시간까지만 셈)':'')]];
  if(!j.holMin){$('out').textContent='0원';rows.push({note:'1주 15시간보다 적으면 주휴수당이 생기지 않아요. '+B.fmt(15-wk)+'시간 더 일하면 생겨요.'});}
  else{$('out').textContent=B.won(j.pay);
    rows.push(['주휴 시간 (근무 시간÷40×8)',B.fmt(j.holMin/60)+'시간'],['한 달 주휴수당 (4.345주)',B.won(j.monthPay)],['근무+주휴 한 달 (월 '+j.monthHours+'시간)',B.won(j.monthTotal),'sum']);
    if(wk>=40)rows.push({note:'주 40시간이면 고시 월 환산액(209시간)과 같아요.'});}
  var min=B.rateOn(B.dayOf(year)).minimumHourlyWage;if(min&&w<min)rows.push({note:year+'년 최저시급 '+B.won(min)+'보다 낮아요.',warn:1});
  $('lines').innerHTML=B.rows(rows);save();
}
B.ready.then(function(){
  year=B.years()[0];var put=function(y){$('wage').value=B.rateOn(B.dayOf(y)).minimumHourlyWage.toLocaleString('ko-KR');};
  B.yearChips($('years'),function(y){year=y;put(y);calc();});put(year);
  save=B.keep(['wage','days','hours']);calc();
});
['wage','days','hours'].forEach(function(i){$(i).addEventListener('input',calc);});
B.shareButton($('share'),'주휴수당 계산 결과');
})();''',
    [('주휴수당은 누가 받나요?', '1주에 일하기로 한 시간(소정근로시간)이 15시간 이상이고, 그 주에 정해진 날을 모두 나온 근로자가 받아요. 알바·계약직도 같아요(근로기준법 제55조·제18조 제3항).'),
     ('주휴수당은 어떻게 계산하나요?', '1주 소정근로시간 ÷ 40 × 8 × 시급이에요. 40시간을 넘게 일해도 40시간으로 계산해서 하루치(8시간)가 가장 많은 값이에요.'),
     min_wage_faq(),
     ('근무 시간이 주마다 달라요.', '보통 최근 4주 평균으로 1주 시간을 잡아요. 밤낮 앱에 근무를 넣어 두면 주마다 15시간을 넘었는지 저절로 셈해서 그달 주휴까지 월급에 넣어요.')],
    'juhyu', '근무표만 넣으면 주휴·야간수당까지 매달 저절로', '밤낮은 교대·알바·일근 근무를 달력에 넣으면 주마다 주휴 조건을 따져 이번 달 예상 월급을 보여 줘요. 근무 알람도 함께예요.', 2))

# ---------- 야간·연장·휴일 ----------
write('calc/allowance/', page(
    'calc/allowance/', '야간·연장·휴일수당 계산기 (밤 10시~아침 6시) | 밤낮',
    '출퇴근 시각과 시급으로 야간(22~06시)·연장·휴일 가산수당을 나눠 계산해요. 자정 넘는 야간, 5인 미만도 반영.',
    '야간·연장·휴일수당 계산기', '출근과 퇴근 시각만 넣으면 밤 10시~아침 6시 야간 시간과 8시간을 넘은 연장 시간을 저절로 나눠요. 자정을 넘는 야간 근무도 그대로 넣으세요.',
    f'''<div class="card">
  <span class="glabel" id="presetL">예시로 채우기</span>
  <div class="chips" id="preset" role="group" aria-labelledby="presetL">
    <button type="button" class="chip" data-s="22:00" data-e="07:00" data-r="60" data-h="0">야간 교대 22–07</button>
    <button type="button" class="chip" data-s="09:00" data-e="21:00" data-r="60" data-h="0">일근 야근 09–21</button>
    <button type="button" class="chip" data-s="07:00" data-e="19:00" data-r="60" data-h="0">주간 12시간 07–19</button>
    <button type="button" class="chip" data-s="09:00" data-e="18:00" data-r="60" data-h="1">휴일 근무 09–18</button>
  </div>
  <label for="wage">시급 (원)</label>
  <input id="wage" {DATA_MONEY} data-max="1000000">
  <div class="chips" id="years" role="group" aria-label="최저시급으로 채우기"></div>
  <div class="row">
    <div><label for="start">출근</label><input id="start" type="time" value="22:00"></div>
    <div><label for="end">퇴근</label><input id="end" type="time" value="07:00"></div>
  </div>
  <label for="rest">휴게 시간 (분)<span class="hint">휴게는 야간이 아닌 시간에서 먼저 빼요. 밤에 쉬었다면 아래를 켜요.</span></label>
  <input id="rest" inputmode="numeric" value="60" data-max="720" data-unit="분">
  <label class="check"><input type="checkbox" id="restNight"> 휴게를 밤 10시~아침 6시 사이에 했어요</label>
  <label class="check"><input type="checkbox" id="holiday"> 쉬는 날(주휴일·공휴일)에 일했어요</label>
  <label class="check"><input type="checkbox" id="small"> 상시 5명 미만 사업장이에요 (가산수당 없음)</label>
  <label for="times">이 근무를 한 달에 몇 번<span class="hint">넣으면 한 달 합계도 보여요. 비우면 하루만.</span></label>
  <input id="times" inputmode="decimal" data-max="31" data-unit="번">
</div>
{result('이날 받을 금액 (가산 포함)')}''',
    r'''(function(){
var B=Bamnat,F=B.f,$=B.$,year=new Date().getFullYear(),save=function(){};
function mins(t){var p=/^(\d{1,2}):(\d{2})/.exec(t||'');return p?(+p[1])*60+(+p[2]):null;}
function hm(x){return Math.floor(x/60)+'시간'+(x%60?' '+(x%60)+'분':'');}
function calc(){
  var w=B.val('wage'),s=mins($('start').value),e=mins($('end').value);
  if(!w||s===null||e===null){$('out').textContent='-';$('lines').innerHTML=B.rows([s===null||e===null?{note:'출근·퇴근 시각을 넣어 주세요.'}:null]);save();return;}
  var r=F.allowance({wage:w,start:s,end:e,rest:B.val('rest'),restNight:$('restNight').checked,holiday:$('holiday').checked,small:$('small').checked});
  $('out').textContent=B.won(r.sum);
  var rows=[['일한 시간 (휴게 '+r.rest+'분 뺌)',hm(r.work)],['그중 야간 (22~06시)',hm(r.night)],['그중 8시간 넘은 시간',hm(r.over)],['기본 (시간×시급)',B.won(r.base)]];
  if(s===e)rows.unshift({note:'출근과 퇴근이 같아 24시간 근무로 셈했어요.'});
  if($('small').checked)rows.push({note:'5명 미만 사업장은 야간·연장·휴일 가산을 주지 않아도 돼요(근로기준법 제11조).'});
  else{rows.push(['야간 가산 50%',B.won(r.nightP)]);rows.push($('holiday').checked?['휴일 가산 (8시간까지 50%, 넘으면 100%)',B.won(r.holP)]:['연장 가산 50%',B.won(r.ext)]);}
  rows.push(['합계',B.won(r.sum),'sum']);
  var n=B.val('times');if(n>0)rows.push(['한 달 '+B.fmt(n)+'번이면',B.won(r.sum*n),'sum']);
  $('lines').innerHTML=B.rows(rows);save();
}
$('preset').addEventListener('click',function(ev){var b=ev.target.closest('.chip');if(!b)return;$('start').value=b.dataset.s;$('end').value=b.dataset.e;$('rest').value=b.dataset.r;$('holiday').checked=b.dataset.h==='1';calc();});
B.ready.then(function(){
  year=B.years()[0];var put=function(y){$('wage').value=B.rateOn(B.dayOf(y)).minimumHourlyWage.toLocaleString('ko-KR');};
  B.yearChips($('years'),function(y){year=y;put(y);calc();});put(year);
  save=B.keep(['wage','start','end','rest','restNight','holiday','small','times']);calc();
});
['wage','start','end','rest','restNight','holiday','small','times'].forEach(function(i){$(i).addEventListener('input',calc);$(i).addEventListener('change',calc);});
B.shareButton($('share'),'야간·연장·휴일수당 계산 결과');
})();''',
    [('야간수당은 몇 시부터인가요?', '밤 10시부터 다음 날 아침 6시 사이에 일한 시간에 통상임금의 50%를 더 받아요(근로기준법 제56조 제3항). 야간이 연장이나 휴일과 겹치면 각각 더해요.'),
     ('연장수당은 언제 붙나요?', '하루 8시간이나 1주 40시간을 넘게 일한 시간에 50%를 더 받아요. 이 계산기는 하루 8시간 기준만 셈해요. 1주 40시간 초과까지 보려면 한 주 연장·야간·휴일 계산기에 요일마다 출퇴근을 넣어요.'),
     ('휴일에 일하면 얼마나 더 받나요?', '휴일에 일한 시간 중 8시간까지는 50%, 8시간을 넘은 시간은 100%를 더 받아요(근로기준법 제56조 제2항).'),
     ('5명 미만 회사도 받을 수 있나요?', '상시 근로자가 5명 미만이면 야간·연장·휴일 가산수당 규정이 적용되지 않아요. 다만 일한 시간만큼의 기본 시급과 주휴수당은 받아요.')],
    'allowance', '야간 근무가 많은 달, 이번 달 월급은?', '밤낮에 근무표를 넣으면 근무마다 야간·연장·휴일 시간을 나눠 수당까지 담은 이번 달 예상 월급을 보여 줘요. 명세서와 다르면 근무 종류마다 시수를 맞출 수 있어요.', 2))

# ---------- 요일 7줄 (사장님 인건비·한 주 계산기) ----------
WD = ['월', '화', '수', '목', '금', '토', '일']


def week_grid(work='12345', start='09:00', end='18:00'):
    """요일 단추(누르면 일하는 날·쉬는 날)와 출근·퇴근 칸 7줄. 쉬는 날은 칸이 잠겨요(B.weekGrid)."""
    rows = []
    for i, d in enumerate(WD, 1):
        on = str(i) in work
        rows.append(
            f'<div class="wk-row{"" if on else " off"}" id="r{i}">'
            f'<button type="button" class="wk-day" id="w{i}" aria-pressed="{"true" if on else "false"}" aria-label="{d}요일에 일해요">{d}</button>'
            f'<input type="time" id="s{i}" value="{start}" aria-label="{d}요일 출근"{"" if on else " disabled"}>'
            f'<span class="wk-to" aria-hidden="true">~</span>'
            f'<input type="time" id="e{i}" value="{end}" aria-label="{d}요일 퇴근"{"" if on else " disabled"}></div>')
    return (f'<input type="hidden" id="on" value="{work}"><div class="wk" role="group" aria-label="요일별 출근·퇴근">' + ''.join(rows) + '</div>'
            '<span class="hint">요일을 누르면 일하는 날·쉬는 날이 바뀌어요. 자정을 넘기면 퇴근에 다음 날 시각을 적어요.</span>')


def boss_rate_text(y):
    p, h, c, u = rate(y, 'pensionRate') / 2, rate(y, 'healthRate') / 2, rate(y, 'longTermCareRate'), rate(y, 'unemploymentRate') / 2
    total = p + h + c / 2 + u + 0.0025
    return p, h, c / 2, u + 0.0025, total


def boss_faq_rates(y):
    p, h, c, u, total = boss_rate_text(y)
    return (f'{y}년 사업주 몫은 국민연금 {pct(p)}, 건강보험 {pct(h)}, 장기요양 {pct(c, 4)}, 고용보험 {pct(u, 2)}(실업급여 {pct(u - 0.0025, 2)} + '
            f'고용안정·직업능력개발 0.25%, 150명 미만)로 월급의 약 {total * 100:.1f}%예요. 산재보험은 업종마다 달라 따로 내요.')


# ---------- 사장님 인건비 [I50b-01·T50-09] ----------
write('calc/boss/', page(
    'calc/boss/', f'사장님 인건비 계산기 (주휴·4대보험 사업주 몫) | 밤낮',
    f'알바·직원 시급과 요일별 근무로 주휴수당과 사업주 4대보험까지 한 달 인건비를 계산해요. {YEARS_TXT} 요율.',
    '사장님 인건비 계산기', '직원 한 명을 쓰면 한 달에 얼마가 드는지 봐요. 시급과 일하는 요일만 넣으면 주휴수당과 사업주가 내는 4대보험까지 더해요.',
    f'''<div class="card">
  <label for="wage">시급 (원)</label>
  <input id="wage" {DATA_MONEY} data-max="1000000">
  <div class="chips" id="years" role="group" aria-label="최저시급으로 채우기"></div>
  <span class="glabel" id="daysL">일하는 요일과 시간</span>
  <div class="chips" id="preset" role="group" aria-label="자주 쓰는 근무로 채우기">
    <button type="button" class="chip" data-days="12345" data-s="09:00" data-e="18:00">평일 9&#8288;–&#8288;18</button>
    <button type="button" class="chip" data-days="12345" data-s="18:00" data-e="23:00">평일 저녁 18&#8288;–&#8288;23</button>
    <button type="button" class="chip" data-days="67" data-s="10:00" data-e="20:00">주말 10&#8288;–&#8288;20</button>
    <button type="button" class="chip" data-days="1234567" data-s="11:00" data-e="15:00">매일 점심 11&#8288;–&#8288;15</button>
  </div>
  {week_grid()}
  <label class="check"><input type="checkbox" id="breaks" checked> 휴게 시간 빼기 (4시간이면 30분, 8시간 30분부터 1시간)</label>
  <label class="check"><input type="checkbox" id="insured" checked> 4대보험에 가입해요</label>
  <span class="glabel" id="monthL">어느 달</span>
  <div class="chips" id="month" role="group" aria-labelledby="monthL"></div>
  <label for="people">이 근무로 일하는 직원 수</label>
  <input id="people" inputmode="numeric" value="1" data-min="1" data-max="50" data-unit="명">
</div>
{result('한 달 인건비 (사업주 부담 포함)')}''',
    r'''(function(){
var B=Bamnat,F=B.f,$=B.$,D=B.d,year=new Date().getFullYear(),save=function(){};
var grid=B.weekGrid(calc);
var t=D.today(),ms=[0,1].map(function(k){var x=D.plusMonths(new Date(Date.UTC(t.getUTCFullYear(),t.getUTCMonth(),1)),k);return D.iso(x).slice(0,7);});
$('month').innerHTML=ms.map(function(m,i){return '<button type="button" class="chip" data-v="'+m+'" aria-pressed="'+(i===0)+'">'+(+m.slice(5))+'월'+(i===0?' (이번 달)':'')+'</button>';}).join('');
var month=B.chips($('month'),function(){calc();});
$('preset').addEventListener('click',function(e){var b=e.target.closest('.chip');if(!b)return;grid.fill(b.dataset.days,b.dataset.s,b.dataset.e);calc();});
function hm(x){return Math.floor(x/60)+'시간'+(x%60?' '+(x%60)+'분':'');}
function calc(){
  var w=B.val('wage'),days=grid.read().filter(function(d){return d.work;}).map(function(d){return [d.day,d.s,d.e];}),n=Math.max(1,Math.round(B.val('people')||1));
  if(!w||!days.length){$('out').textContent='-';$('lines').innerHTML=B.rows([days.length?null:{note:'일하는 요일을 하나 이상 골라 주세요.'}]);save();return;}
  var m=month.get()||ms[0],r=F.labor({month:m,wage:w,days:days,breaks:$('breaks').checked,insured:$('insured').checked,shortOff:true}),rows=[];
  rows.push([(+m.slice(5))+'월 일한 시간 ('+r.workDays+'일)',hm(r.minutes)]);
  rows.push(['기본급 (시간 × 시급)',B.won(r.base)]);
  if(r.holMin)rows.push(['주휴수당 ('+B.fmt(r.holMin/60)+'시간 × 일요일 '+r.sundays+'번)',B.won(r.holiday)]);
  else rows.push({note:'1주 '+B.fmt(r.week/60)+'시간이라 주휴수당이 없어요(1주 15시간부터).'});
  rows.push(['직원이 받는 돈 (세전)',B.won(r.pay),'sum']);
  if(r.ins){var x=r.ins;
    if(r.short)rows.push({note:'주 15시간 미만은 국민연금·건강보험 가입 대상이 아니라 사업주 몫도 없어요(1개월 60시간 미만). 고용보험은 3개월 넘게 일하면 내요.'});
    else rows.push(['국민연금 '+B.fmt(x.pensionRate*100,3)+'%',B.won(x.pension)],['건강보험 '+B.fmt(x.healthRate*100,3)+'%',B.won(x.health)],['장기요양',B.won(x.care)]);
    rows.push(['고용보험 '+B.fmt(x.employmentRate*100,2)+'%',B.won(x.employment)]);}
  rows.push(['한 달 인건비',B.won(r.total),'sum']);
  rows.push(['주휴 포함 실제 시급',B.won(r.perHour)]);
  if(r.ins)rows.push(['사업주 부담까지 1시간에',B.won(r.costPerHour)]);
  if(n>1)rows.push(['직원 '+n+'명이면',B.won(r.total*n),'sum']);
  if(!r.short)rows.push({note:'퇴직금: 1년 넘게 일하면 따로 줘요. 한 달에 약 '+B.won(Math.round(r.pay/12/10)*10)+'씩(세전의 12분의 1쯤) 모아 두면 편해요(근로자퇴직급여 보장법 제8조).'});
  rows.push({note:'산재보험(업종마다 다름)은 빠졌어요. 밤 10시~아침 6시·하루 8시간·주 40시간을 넘는 가산은 <a href="../week/">한 주 연장·야간·휴일 계산기</a>에서 봐요.'});
  var min=B.rateOn(B.dayOf(+m.slice(0,4))).minimumHourlyWage;if(min&&w<min)rows.push({note:m.slice(0,4)+'년 최저시급 '+B.won(min)+'보다 낮아요.',warn:1});
  $('out').textContent=B.won(r.total*n);$('lines').innerHTML=B.rows(rows);save();
}
B.ready.then(function(){
  year=B.years()[0];var put=function(y){$('wage').value=B.rateOn(B.dayOf(y)).minimumHourlyWage.toLocaleString('ko-KR');};
  B.yearChips($('years'),function(y){year=y;put(y);calc();});put(year);
  save=B.keep(['wage','breaks','insured','people'].concat(grid.ids),{month:month});grid.sync();calc();
});
['wage','breaks','insured','people'].forEach(function(i){$(i).addEventListener('input',calc);$(i).addEventListener('change',calc);});
B.shareButton($('share'),'사장님 인건비 계산 결과');
})();''',
    [('알바 한 명 인건비는 얼마인가요?', '시급 × 일한 시간에 주휴수당(1주 15시간 이상)을 더한 것이 직원이 받는 돈이고, 4대보험에 가입하면 사업주 몫을 더 내요. ' + boss_faq_rates(THIS)),
     ('5명 미만 가게도 주휴수당을 줘야 하나요?', '네. 1주에 일하기로 한 시간이 15시간 이상이고 그 주를 다 나왔으면 사업장 크기와 상관없이 주휴수당을 줘요(근로기준법 제55조, 제18조 제3항). 5명 미만에서 빠지는 것은 야간·연장·휴일 가산수당 등이에요.'),
     ('주 15시간보다 적게 일하는 알바도 4대보험에 넣나요?', '1개월 소정근로시간이 60시간 미만이면 국민연금·건강보험 사업장 가입 대상이 아니에요(국민연금법 시행령 제2조, 국민건강보험법 시행령 제9조). 고용보험은 3개월 넘게 계속 일하면 넣고, 산재보험은 모두 적용돼요.'),
     ('주휴수당은 한 달에 몇 번 주나요?', '주휴일이 그달에 든 주마다 줘요. 이 계산기는 주휴일을 일요일로 보고 그달 일요일 수만큼 셈해서, 달마다 4번이나 5번이에요(밤낮 앱 사장님 인건비와 같은 셈).'),
     ('퇴직금도 인건비로 잡아야 하나요?', '1년 넘게 주 15시간 이상 일한 직원이 그만두면 1년에 30일분 평균임금 이상을 퇴직금으로 줘요(근로자퇴직급여 보장법 제8조). 그래서 매달 세전의 12분의 1쯤을 따로 모아 두는 사장님이 많아요.')],
    'boss', '직원 근무표로 인건비를 매달 저절로', '밤낮 사장님 화면에 직원과 요일별 근무를 넣으면 이번 달 인건비·인건비율을 매출과 함께 보여 줘요. 주휴와 4대보험 사업주 몫도 같은 식으로 셈해요.', 2))

# ---------- 한 주 연장·야간·휴일 [I50b-03] ----------
write('calc/week/', page(
    'calc/week/', '한 주 연장·야간·휴일수당 계산기 (주 40·52시간) | 밤낮',
    '요일별 출퇴근을 넣으면 하루 8시간·주 40시간을 넘은 연장, 야간, 휴일 근로와 가산수당, 주 52시간을 함께 봐요.',
    '한 주 연장·야간·휴일 계산기', '한 주 출퇴근을 요일마다 넣으면 하루 8시간을 넘은 연장과 주 40시간을 넘은 연장 중 큰 쪽, 밤 10시~아침 6시 야간, 휴일 근로를 나눠 가산수당까지 셈해요.',
    f'''<div class="card">
  <label for="wage">시급 (원)</label>
  <input id="wage" {DATA_MONEY} data-max="1000000">
  <div class="chips" id="years" role="group" aria-label="최저시급으로 채우기"></div>
  <span class="glabel" id="daysL">이번 주 출근·퇴근</span>
  <div class="chips" id="preset" role="group" aria-label="예시로 채우기">
    <button type="button" class="chip" data-days="12345" data-s="09:00" data-e="18:00">평일 9&#8288;–&#8288;18</button>
    <button type="button" class="chip" data-days="12345" data-s="09:00" data-e="21:00">평일 야근 9&#8288;–&#8288;21</button>
    <button type="button" class="chip" data-days="123456" data-s="09:00" data-e="18:00">주 6일 9&#8288;–&#8288;18</button>
    <button type="button" class="chip" data-days="12345" data-s="22:00" data-e="07:00">야간 22&#8288;–&#8288;07</button>
    <button type="button" class="chip" data-plan="1@07:00-19:00,2@07:00-19:00,3@19:00-07:00,4@19:00-07:00">2교대 주주야야</button>
  </div>
  {week_grid()}
  <span class="glabel" id="holL">유급휴일 (주휴일·공휴일)<span class="hint">휴일에 일한 시간은 휴일 근로예요. 보통 일요일이 주휴일이에요.</span></span>
  <input type="hidden" id="hol" value="7">
  <div class="chips wd" id="hols" role="group" aria-labelledby="holL">{''.join(f'<button type="button" class="chip" data-d="{i}" aria-pressed="{"true" if i == 7 else "false"}" aria-label="{d}요일 휴일">{d}</button>' for i, d in enumerate(WD, 1))}</div>
  <label for="rest">하루 휴게 시간 (분)<span class="hint">비우면 법대로 빼요: 4시간이면 30분, 8시간 30분부터 1시간. 휴게는 근무 시간에 고르게 나눠 빼요.</span></label>
  <input id="rest" inputmode="numeric" placeholder="법대로" data-max="720" data-unit="분">
  <label class="check"><input type="checkbox" id="small"> 상시 5명 미만 사업장이에요 (가산수당·52시간 상한 없음)</label>
</div>
{result('이번 주 받을 금액 (가산 포함)')}''',
    r'''(function(){
var B=Bamnat,F=B.f,$=B.$,year=new Date().getFullYear(),save=function(){};
var grid=B.weekGrid(calc);
function holSync(){var v=$('hol').value;[].forEach.call($('hols').querySelectorAll('.chip'),function(c){c.setAttribute('aria-pressed',v.indexOf(c.dataset.d)>=0?'true':'false');});}
$('hols').addEventListener('click',function(e){var b=e.target.closest('.chip');if(!b)return;b.setAttribute('aria-pressed',b.getAttribute('aria-pressed')!=='true');
  $('hol').value=[].filter.call($('hols').querySelectorAll('.chip'),function(c){return c.getAttribute('aria-pressed')==='true';}).map(function(c){return c.dataset.d;}).join('');calc();});
$('preset').addEventListener('click',function(e){var b=e.target.closest('.chip');if(!b)return;if(b.dataset.plan)grid.plan(b.dataset.plan);else grid.fill(b.dataset.days,b.dataset.s,b.dataset.e);calc();});
function hm(x){x=Math.round(x);return Math.floor(x/60)+'시간'+(x%60?' '+(x%60)+'분':'');}
function calc(){
  var w=B.val('wage'),restIn=$('rest').value.trim(),rest=restIn===''?null:B.val('rest'),g=grid.read(),hol=$('hol').value;
  var days=g.map(function(d){if(!d.work)return null;var sp=F.span(d.s,d.e);return {s:d.s,e:d.e,rest:rest==null?F.legalBreak(sp):Math.min(rest,sp)};});
  if(!w||!days.some(Boolean)){$('out').textContent='-';$('lines').innerHTML=B.rows([days.some(Boolean)?null:{note:'일한 요일을 하나 이상 골라 주세요.'}]);save();return;}
  var small=$('small').checked,r=F.week({days:days,holidays:[1,2,3,4,5,6,7].map(function(i){return hol.indexOf(String(i))>=0;}),wage:w,small:small}),rows=[];
  rows.push(['일한 시간 (휴게 '+hm(r.rest)+' 뺌)',hm(r.worked)]);
  if(r.overtime)rows.push(['그중 연장'+(r.daily&&r.weekly?' (하루 8시간 넘음 '+hm(r.daily)+' · 주 40시간 넘음 '+hm(r.weekly)+')':r.weekly?' (주 40시간 넘음)':' (하루 8시간 넘음)'),hm(r.overtime)]);
  if(r.night)rows.push(['그중 야간 (22~06시)',hm(r.night)]);
  if(r.hWithin||r.hOver)rows.push(['그중 휴일 근로'+(r.hOver?' (8시간 넘음 '+hm(r.hOver)+')':''),hm(r.hWithin+r.hOver)]);
  rows.push(['기본 (시간 × 시급)',B.won(r.base)]);
  if(small)rows.push({note:'5명 미만 사업장은 연장·야간·휴일 가산을 주지 않아도 돼요(근로기준법 제11조). 일한 시간만큼의 시급과 주휴수당은 받아요.'});
  else{if(r.overtime)rows.push(['연장 가산 50%',B.won(r.ext)]);if(r.night)rows.push(['야간 가산 50%',B.won(r.nightP)]);
    if(r.hWithin||r.hOver)rows.push(['휴일 가산 (8시간까지 50%, 넘으면 100%)',B.won(r.holP)]);}
  rows.push(['이번 주 합계',B.won(r.sum),'sum']);
  if(r.holMin)rows.push(['주휴수당 (그 주를 다 나왔다면, 주휴 '+B.fmt(r.holMin/60)+'시간)',B.won(r.holPay)],['주휴 포함',B.won(r.sum+r.holPay),'sum']);
  if(r.over52&&!small)rows.push({note:'이번 주 '+hm(r.worked)+'이에요. 주 52시간(40시간 + 연장 12시간, 휴일 근로 포함)을 넘었어요(근로기준법 제53조).',warn:1});
  else if(r.over52)rows.push({note:'5명 미만 사업장은 주 52시간 상한이 적용되지 않아요(근로기준법 제11조).'});
  if(r.hWithin||r.hOver)rows.push({note:'휴일에 일한 시간도 주 40시간을 채워요. 그 시간은 휴일 가산만 받고, 그 뒤 날의 40시간 넘는 시간이 연장이에요(근로기준법 제2조 제1항 제7호).'});
  var min=B.rateOn(B.dayOf(year)).minimumHourlyWage;if(min&&w<min)rows.push({note:year+'년 최저시급 '+B.won(min)+'보다 낮아요.',warn:1});
  $('out').textContent=B.won(r.sum);$('lines').innerHTML=B.rows(rows);save();
}
B.ready.then(function(){
  year=B.years()[0];var put=function(y){$('wage').value=B.rateOn(B.dayOf(y)).minimumHourlyWage.toLocaleString('ko-KR');};
  B.yearChips($('years'),function(y){year=y;put(y);calc();});put(year);
  save=B.keep(['wage','rest','small','hol'].concat(grid.ids));grid.sync();holSync();calc();
});
['wage','rest','small'].forEach(function(i){$(i).addEventListener('input',calc);$(i).addEventListener('change',calc);});
B.shareButton($('share'),'한 주 연장·야간·휴일 계산 결과');
})();''',
    [('연장근로는 어떻게 세나요?', '하루 8시간을 넘은 시간과, 한 주(휴일 포함 7일) 40시간을 넘은 시간 가운데 큰 쪽이 연장이에요(근로기준법 제50조·제56조). 하루 8시간씩 6일이면 하루 넘은 시간은 없지만 주 40시간을 8시간 넘어서 8시간이 연장이에요.'),
     ('휴일에 일한 시간도 주 40시간에 들어가나요?', '네. 근로기준법은 1주를 휴일을 포함한 7일로 정해서(제2조 제1항 제7호) 휴일에 일한 시간도 주 40시간과 52시간을 채워요. 다만 그 시간에는 휴일 가산(8시간까지 50%, 넘으면 100%)만 붙고 연장 가산이 겹치지 않아요(제56조 제2항).'),
     ('주 52시간은 어떻게 보나요?', '1주 40시간에 연장 12시간을 더한 52시간이 한도예요(근로기준법 제53조). 휴일 근로도 들어가요. 상시 5명 미만 사업장에는 이 상한이 적용되지 않아요.'),
     ('야간에 쉬었는데 야간 시간이 다르게 나와요.', '휴게 시각을 모를 때는 휴게를 근무 시간에 고르게 나눠 빼요(밤낮 앱과 같은 방법). 밤에만 쉬었다면 야간 시간이 조금 더 줄어요. 하루짜리는 야간·연장·휴일수당 계산기에서 휴게를 밤에 했는지 고를 수 있어요.'),
     ('주휴수당도 함께 나오나요?', '1주 소정근로시간(휴일 근로를 빼고 연장 전)이 15시간 이상이면 그 주를 다 나왔다고 보고 주휴수당을 함께 보여 줘요. 월급제라면 주휴는 월급에 이미 들어 있어요.')],
    'week', '근무표만 넣으면 매주 저절로', '밤낮에 근무를 넣으면 주마다 하루 8시간·주 40시간을 따져 연장·야간·휴일 시간을 나누고 이번 달 예상 월급에 넣어요. 52시간이 다가오면 미리 알려 줘요.', 2))

# ---------- 알바 월급 ----------
write('calc/alba/', page(
    'calc/alba/', '알바 월급 계산기 (주휴수당·3.3%·4대보험) | 밤낮',
    f'시급과 근무 시간으로 주휴수당 포함 알바 월급, 3.3%·4대보험 뺀 실수령액을 계산해요. {YEARS_TXT} 최저시급 반영.',
    '알바 월급 계산기', '시급과 근무 시간으로 한 달 월급을 계산해요. 주휴수당을 넣고, 3.3% 떼는지 4대보험인지 골라 손에 쥐는 돈까지 봐요.',
    f'''<div class="card">
  <label for="wage">시급 (원)</label>
  <input id="wage" {DATA_MONEY} data-max="1000000">
  <div class="chips" id="years" role="group" aria-label="최저시급으로 채우기"></div>
  <div class="row">
    <div><label for="days">1주 근무일</label><input id="days" inputmode="decimal" value="5" data-max="7" data-unit="일"></div>
    <div><label for="hours">하루 근무 시간</label><input id="hours" inputmode="decimal" value="8" data-max="24" data-unit="시간"></div>
  </div>
  <label class="check"><input type="checkbox" id="month"> 이번 달 달력으로 셈하기 (일하는 요일을 골라요)</label>
  <div id="monthBox" hidden>
    <div class="row">
      <div><label for="ym">달</label><input id="ym" type="month"></div>
      <div><span class="glabel" id="wdL">일하는 요일</span><div class="chips wd" id="wd" role="group" aria-labelledby="wdL"></div></div>
    </div>
  </div>
  <span class="glabel" id="taxL">떼는 것</span>
  <div class="chips" id="tax" role="group" aria-labelledby="taxL">
    <button type="button" class="chip" data-v="none" aria-pressed="false">안 뗌</button>
    <button type="button" class="chip" data-v="biz" aria-pressed="true">3.3%</button>
    <button type="button" class="chip" data-v="ins" aria-pressed="false">4대보험</button>
  </div>
  <label class="check" id="over3mRow" hidden><input type="checkbox" id="over3m"> 3개월 넘게 계속 일해요 (주 15시간 미만이어도 고용보험은 떼요)</label>
</div>
{result('한 달 실수령 (예상)')}''',
    r'''(function(){
var B=Bamnat,F=B.f,$=B.$,D=B.d,year=new Date().getFullYear(),save=function(){},W='일월화수목금토';
$('wd').innerHTML=W.split('').map(function(w,i){return '<button type="button" class="chip" data-d="'+i+'" aria-pressed="'+(i>0&&i<6)+'">'+w+'</button>';}).join('');
$('wd').addEventListener('click',function(e){var b=e.target.closest('.chip');if(!b)return;b.setAttribute('aria-pressed',b.getAttribute('aria-pressed')!=='true');calc();});
var t=D.today();$('ym').value=D.iso(t).slice(0,7);
var mode=B.chips($('tax'),function(){calc();});
function calc(){
  var w=B.val('wage'),h=B.val('hours'),inMonth=$('month').checked,o;$('monthBox').hidden=!inMonth;$('days').disabled=inMonth;
  if(inMonth){var pick=[].map.call($('wd').querySelectorAll('[aria-pressed="true"]'),function(b){return +b.dataset.d;}),ym=($('ym').value||D.iso(t).slice(0,7)).split('-'),
      last=new Date(Date.UTC(+ym[0],+ym[1],0)).getUTCDate(),n=0;
    for(var d=1;d<=last;d++)if(pick.indexOf(new Date(Date.UTC(+ym[0],+ym[1]-1,d)).getUTCDay())>=0)n++;
    o={weekMin:Math.round(pick.length*h*60),monthWorkMin:Math.round(n*h*60),weeks:last/7,workDays:n,monthDays:last,month:+ym[1]};}
  else o={weekMin:Math.round(B.val('days')*h*60)};
  if(!w||!o.weekMin){$('out').textContent='-';$('lines').innerHTML='';save();return;}
  o.wage=w;o.mode=mode.get();o.day=B.dayOf(year);o.over3m=$('over3m').checked;
  var r=F.alba(o),wk=r.weekMin/60,rows=[];
  $('over3mRow').hidden=!(o.mode==='ins'&&r.short);
  rows.push(['1주 근무 '+B.fmt(wk)+'시간 + 주휴 '+B.fmt(r.holMin/60)+'시간',wk<15?'주 15시간 미만이라 주휴 없음':'']);
  rows.push(inMonth?[o.month+'월 '+o.workDays+'일 근무 + 주휴 ('+o.monthDays+'일 ÷ 7주)',r.hours+'시간']:['한 달 유급 시간 (×4.345주)',r.hours+'시간']);
  rows.push(['세전 월급 (시간×시급)',B.won(r.gross)]);
  if(o.mode==='biz')rows.push(['사업소득세 3%',B.minus(r.w.income)],['지방소득세 0.3%',B.minus(r.w.local)]);
  if(o.mode==='ins'){var x=r.th.ins;
    if(r.short)rows.push({note:'주 15시간 미만이면 국민연금·건강보험(장기요양)은 보통 빠지고, 고용보험은 3개월 넘게 계속 일할 때 떼요(산재보험은 사업주만 내요).'});
    else rows.push(['국민연금 '+B.fmt(x.pensionRate*50,3)+'%',B.minus(x.pension)],['건강보험 '+B.fmt(x.healthRate*50,3)+'%',B.minus(x.health)],['장기요양',B.minus(x.care)]);
    rows.push(['고용보험 '+B.fmt(x.employmentRate*50,2)+'%',B.minus(x.employment)]);
    rows.push(r.th.tax===null?{note:'근로소득세 표를 불러오지 못해 소득세는 빼지 않았어요.'}:['근로소득세 (간이세액표, 본인 1명)',B.minus(r.th.tax)]);
    if(r.th.tax!==null)rows.push(['지방소득세',B.minus(r.th.local)]);
    r.notes.forEach(function(n){rows.push({note:n});});
    rows.push({note:'가족 수·비과세 식대까지 넣으려면 <a href="../salary/">월급 실수령액 계산기</a>를 써요.'});}
  var net=Math.max(0,r.net);
  rows.push(['실수령',B.won(net),'sum']);
  if(r.net<0)rows.push({note:'떼는 돈이 월급보다 많아 0원으로 보여요.',warn:1});
  var ys=B.years();if(o.mode==='ins'&&ys.length>1){var oy=ys[0]===year?ys[1]:ys[0],o2=Object.assign({},o,{day:B.dayOf(oy)}),r2=F.alba(o2),dd=Math.max(0,r2.net)-net;
    if(dd)rows.push({note:oy+'년 요율이면 한 달 실수령이 '+B.won(Math.abs(dd))+' '+(dd<0?'줄어요':'늘어요')+'.'});}
  if(wk>40)rows.push({note:'주 40시간을 넘는 시간은 연장 가산(50%)이 붙어요. 1주 연장은 12시간까지예요(근로기준법 제53조). 가산은 <a href="../allowance/">야간·연장·휴일수당 계산기</a>에서 봐요.'});
  var min=B.rateOn(B.dayOf(year)).minimumHourlyWage;if(min&&w<min)rows.push({note:year+'년 최저시급 '+B.won(min)+'보다 낮아요.',warn:1});
  $('out').textContent=B.won(net);$('lines').innerHTML=B.rows(rows);save();
}
Promise.all([B.ready,B.loadTaxTable()]).then(function(){
  year=B.years()[0];var put=function(y){$('wage').value=B.rateOn(B.dayOf(y)).minimumHourlyWage.toLocaleString('ko-KR');};
  B.yearChips($('years'),function(y){year=y;put(y);calc();});put(year);
  save=B.keep(['wage','days','hours','month','ym','over3m'],{tax:mode});calc();
});
['wage','days','hours','month','ym','over3m'].forEach(function(i){$(i).addEventListener('input',calc);$(i).addEventListener('change',calc);});
B.shareButton($('share'),'알바 월급 계산 결과');
})();''',
    [('3.3%는 왜 떼나요?', '근로계약 대신 프리랜서(사업소득)로 처리하면 소득세 3%와 지방소득세 0.3%를 떼고 줘요. 실제로 사장님 지시를 받아 정해진 시간에 일하면 근로자라서 4대보험·주휴수당 대상일 수 있어요.'),
     ('한 달은 몇 주로 계산하나요?', '1년 365일 ÷ 7 ÷ 12 = 약 4.345주로 계산해요. 주 40시간이면 주휴 포함 월 209시간이 돼요. 이번 달 실제 날수로 보려면 "이번 달 달력으로"를 켜요.'),
     ('4대보험은 얼마나 떼나요?', f'근로자 몫은 {ins_faq_text(THIS)}예요.' + (f' {NEXT}년 국민연금은 {pct(rate(NEXT, "pensionRate") / 2)}예요.' if NEXT and rate(NEXT, 'pensionRate') != rate(THIS, 'pensionRate') else '')),
     ('주 15시간보다 적게 일해요.', '1주 소정근로시간이 15시간 미만이면 국민연금·건강보험은 보통 들지 않고, 고용보험은 3개월 넘게 계속 일하면 들어요. 주휴수당과 퇴직금도 생기지 않아요.'),
     ('주마다 근무 시간이 달라요.', '밤낮 앱에 실제 근무를 넣으면 주마다 주휴 조건을 따로 따져서 그달 월급을 계산해요. 근무 알람도 같이 맞출 수 있어요.')],
    'alba', '시간표가 매주 바뀌는 알바라면', '밤낮에 근무를 넣으면 주마다 주휴수당 조건을 따로 따져 이번 달 월급을 보여 줘요. 근무 30분 전 알람도 근무마다 달리 맞춰요.', 2))

# ---------- 일당 ----------
write('calc/daily/', page(
    'calc/daily/', '일당 계산기 · 일용직 세금(15만 원 공제)과 실수령 | 밤낮',
    '일당과 일한 날 수로 일용직 소득세(하루 15만 원 공제)·지방소득세·고용보험을 뺀 실수령액을 계산해요. 현장·건설용.',
    '일당 계산기 (일용직 세금)', '현장·인테리어·건설처럼 날마다 받는 일당의 세금과 손에 쥐는 돈을 계산해요.',
    f'''<div class="card">
  <div class="row">
    <div><label for="pay">일당 (원)</label><input id="pay" {DATA_MONEY} value="200,000" data-max="10000000"></div>
    <div><label for="days">일한 날 수<span class="hint">반나절은 0.5</span></label><input id="days" inputmode="decimal" value="20" data-max="31" data-unit="일"></div>
  </div>
  <span class="glabel" id="paidL">받는 방법</span>
  <div class="chips" id="paid" role="group" aria-labelledby="paidL">
    <button type="button" class="chip" data-v="together" aria-pressed="true">모아서 받아요 (주급·월급)</button>
    <button type="button" class="chip" data-v="daily" aria-pressed="false">날마다 받아요</button>
  </div>
  <label class="check"><input type="checkbox" id="emp" checked> 고용보험 떼요 (일용직 근로자)</label>
</div>
{result('실수령 합계')}''',
    r'''(function(){
var B=Bamnat,F=B.f,$=B.$,save=function(){};
var paid=B.chips($('paid'),function(){calc();});
function calc(){
  var p=B.val('pay'),n=B.val('days');if(!p||!n){$('out').textContent='-';$('lines').innerHTML='';save();return;}
  var day=B.dayOf(new Date().getFullYear()),r=F.daily({pay:p,days:n,paid:paid.get(),emp:$('emp').checked,day:day}),ur=B.rateOn(day).unemploymentRate;
  $('out').textContent=B.won(r.net);
  var rows=[['세전 합계 ('+B.fmt(n)+'일)',B.won(r.gross)],
    ['소득세 ((일당−15만)×2.7%'+(paid.get()==='together'?', 모은 합계로':', 하루씩')+')',B.minus(r.income)],
    ['지방소득세 (소득세의 10%)',B.minus(r.local)],
    ['고용보험'+($('emp').checked?' '+B.fmt(ur*50,2)+'%':''),B.minus(r.emp)],
    ['실수령 합계',B.won(r.net),'sum']];
  if(r.perDay<1000&&r.perDay>0&&paid.get()==='daily')rows.push({note:'날마다 받으면 하루 소득세 '+B.won(r.perDay)+'이 1,000원 미만이라 떼지 않아요(소액부징수). 모아서 받으면 합계로 따져요.'});
  if(!r.perDay)rows.push({note:'일당이 15만 원 이하면 소득세가 없어요.'});
  if(n>=8)rows.push({note:'같은 현장에서 한 달 8일 이상(또는 60시간 이상) 일하면 국민연금·건강보험도 뗄 수 있어요. 이 계산기는 고용보험까지만 셈해요.'});
  $('lines').innerHTML=B.rows(rows);save();
}
B.ready.then(function(){save=B.keep(['pay','days','emp'],{paid:paid});calc();});
['pay','days','emp'].forEach(function(i){$(i).addEventListener('input',calc);$(i).addEventListener('change',calc);});
B.shareButton($('share'),'일당 계산 결과');
})();''',
    [('일용직 세금은 어떻게 계산하나요?', '하루 일당에서 15만 원을 뺀 금액에 6%를 곱하고, 그중 55%를 깎아 줘요(근로소득세액공제). 그래서 15만 원 넘는 부분의 2.7%가 소득세, 그 10%가 지방소득세예요.'),
     ('1,000원 미만이면 안 떼나요?', '떼는 소득세가 1,000원 미만이면 떼지 않아요(소액부징수). 날마다 받으면 하루치로, 여러 날 치를 한꺼번에 받으면 그 합계로 따져서, 모아 받는 사람은 하루 소득세가 작아도 합계가 1,000원을 넘으면 떼요. 소득세와 지방소득세는 받을 때마다 10원 미만을 버려요.'),
     ('국민연금·건강보험도 떼나요?', '같은 현장에서 한 달 8일 이상 또는 60시간 이상 일하면 국민연금·건강보험 대상이 될 수 있어요. 이 계산기는 고용보험까지만 셈해요.'),
     ('현장이 날마다 바뀌어요.', '밤낮 앱에 일한 날과 현장, 일당을 적으면 이번 달 번 돈과 받을 돈을 달력에서 한눈에 봐요. 사장님이라면 기사님 일정도 함께 관리할 수 있어요.')],
    'daily', '현장이 바뀌어도 이번 달 일당을 한눈에', '밤낮 달력에 일한 날과 일당을 적으면 이번 달 번 돈·받을 돈을 바로 봐요. 직원·기사님을 둔 사장님은 일정과 급여 대장도 함께 써요.', 2))

# ---------- 월급 실수령액 ----------
write('calc/salary/', page(
    'calc/salary/', f'월급·연봉 실수령액 계산기 {THIS} (4대보험·간이세액표) | 밤낮',
    f'월급·연봉에서 4대보험과 근로소득세(간이세액표)를 뺀 실수령액을 계산해요. 부양가족·비과세 식대' + (f', {NEXT}년 요율도.' if NEXT else '.'),
    '월급·연봉 실수령액 계산기', '세전 월급이나 연봉을 넣으면 4대보험과 소득세를 뺀 통장에 들어오는 돈을 계산해요. 소득세는 국세청 근로소득 간이세액표 원문 그대로예요.',
    f'''<div class="card">
  <div class="chips" id="kind" role="group" aria-label="금액 종류">
    <button type="button" class="chip" data-v="month" aria-pressed="true">월급</button>
    <button type="button" class="chip" data-v="year" aria-pressed="false">연봉</button>
    <button type="button" class="chip" data-v="yearsev" aria-pressed="false">퇴직금 포함 연봉</button>
  </div>
  <label for="amount" id="amountLabel">세전 월급 (원)</label>
  <input id="amount" {DATA_MONEY} value="3,000,000" data-max="10000000000">
  <label for="free">비과세 (식대 등, 월)<span class="hint">식대는 월 20만 원까지 비과세예요. 없으면 0.</span></label>
  <input id="free" {DATA_MONEY} value="200,000" data-max="100000000">
  <div class="row">
    <div><label for="family">부양가족 수 (본인 포함)</label><input id="family" inputmode="numeric" value="1" data-min="1" data-max="20" data-unit="명"></div>
    <div><label for="kids">그중 8~20세 자녀</label><input id="kids" inputmode="numeric" value="0" data-max="19" data-unit="명"></div>
  </div>
  <span class="glabel" id="yearsL">요율 연도</span>
  <div class="chips" id="years" role="group" aria-labelledby="yearsL"></div>
</div>
{result('한 달 실수령 (예상)')}''',
    r'''(function(){
var B=Bamnat,F=B.f,$=B.$,year=new Date().getFullYear(),save=function(){};
var DIV={month:1,year:12,yearsev:13},LABEL={month:'세전 월급 (원)',year:'세전 연봉 (원, 퇴직금 빼고)',yearsev:'세전 연봉 (원, 퇴직금 포함 ÷ 13)'},prev='month';
var kind=B.chips($('kind'),function(v){var m=B.val('amount')/(DIV[prev]||1);$('amount').value=Math.round(m*DIV[v]).toLocaleString('ko-KR');prev=v;$('amountLabel').textContent=LABEL[v];calc();});
function one(y){var fam=B.val('family')||1;$('kids').dataset.max=Math.max(0,fam-1);
  var gross=Math.floor(B.val('amount')/DIV[kind.get()]);return F.takeHome({gross:gross,free:B.val('free'),family:fam,kids:B.val('kids'),day:B.dayOf(y)});}
function calc(){
  if(!B.val('amount')){$('out').textContent='-';$('lines').innerHTML='';save();return;}
  var r=one(year),x=r.ins,k=kind.get(),rows=[];
  if(k!=='month')rows.push(['월 세전 (연봉÷'+DIV[k]+')',B.won(r.gross)]);
  if(r.free)rows.push(['비과세 (보험료·세금에서 빠져요)',B.won(r.free)]);
  rows.push(['국민연금 '+B.fmt(x.pensionRate*50,3)+'%',B.minus(x.pension)],['건강보험 '+B.fmt(x.healthRate*50,3)+'%',B.minus(x.health)],['장기요양',B.minus(x.care)],['고용보험 '+B.fmt(x.employmentRate*50,2)+'%',B.minus(x.employment)]);
  rows.push(r.tax===null?{note:'소득세 표를 불러오지 못했어요. 잠시 뒤 다시 열어 주세요.'}:['근로소득세 (간이세액표)',B.minus(r.tax)]);
  if(r.tax!==null)rows.push(['지방소득세 (10%)',B.minus(r.local)]);
  r.notes.forEach(function(n){rows.push({note:n});});
  rows.push(['공제 합계',B.minus(r.ded)],['월 실수령',B.won(r.net),'sum'],['1년 실수령 (×12)',B.won(r.net*12)]);
  if(k==='yearsev')rows.push({note:'퇴직금 포함 연봉은 13으로 나눠 한 달 월급을 잡아요. 13번째 몫은 퇴직금으로 따로 쌓여요.'});
  var ys=B.years();if(ys.length>1){var oy=ys[0]===year?ys[1]:ys[0],d=one(oy).net-r.net;if(d)rows.push({note:oy+'년 요율이면 한 달 실수령이 '+B.won(Math.abs(d))+' '+(d<0?'줄어요':'늘어요')+'.'});}
  $('out').textContent=B.won(r.net);$('lines').innerHTML=B.rows(rows);save();
}
Promise.all([B.ready,B.loadTaxTable()]).then(function(){
  year=B.years()[0];var yc=B.yearChips($('years'),function(y){year=y;calc();},function(y){return y+'년';});
  save=B.keep(['amount','free','family','kids'],{kind:kind,y:yc});prev=kind.get();$('amountLabel').textContent=LABEL[prev];year=+yc.get()||year;calc();
});
['amount','free','family','kids'].forEach(function(i){$(i).addEventListener('input',calc);});
B.shareButton($('share'),'월급 실수령액 계산 결과');
})();''',
    [('실수령액은 어떻게 계산하나요?', f'세전 월급에서 {ins_faq_text(THIS)}와 근로소득세(간이세액표), 지방소득세(소득세의 10%)를 빼요. 비과세 식대는 보험료와 세금 계산에서 빠져요.'),
     ('근로소득세는 왜 회사마다 다르게 떼나요?', '회사는 간이세액표 금액의 80%, 100%, 120% 중 하나를 고를 수 있고(소득세법 시행령 제194조), 1년치는 연말정산에서 맞춰요. 이 계산기는 100% 기준이에요.'),
     (f'{NEXT}년에는 얼마나 달라지나요?' if NEXT else '요율이 바뀌면 어떻게 하나요?',
      (f'{NEXT}년에는 국민연금 근로자 몫이 {pct(rate(NEXT, "pensionRate") / 2)}, 장기요양이 건강보험료×{pct(rate(NEXT, "longTermCareRate") / rate(NEXT, "healthRate"), 2)}예요. 요율 연도에서 {NEXT}년을 고르면 바로 볼 수 있어요.'
       if NEXT else '새 요율이 고시되면 밤낮 앱과 같은 자료를 고쳐 계산기에도 바로 반영돼요.')),
     ('연봉에 퇴직금이 들어 있어요.', "'퇴직금 포함 연봉'을 고르면 연봉을 13으로 나눠 한 달 월급을 잡아요. 퇴직금은 따로 쌓이는 몫이라 매달 받는 돈에서 빠져요."),
     ('야간·연장 수당이 달마다 달라요.', '밤낮 앱에 근무를 넣으면 그달 수당을 더한 세전 월급으로 실수령을 저절로 계산해요. 명세서 사진과 맞춰 볼 수도 있어요.')],
    'salary', '수당이 달마다 바뀐다면, 이번 달 실수령은?', '밤낮은 근무표로 야간·연장·휴일 수당을 더한 이번 달 세전 월급을 만들고, 같은 방식으로 실수령까지 보여 줘요.', 2))

# ---------- 연차 ----------
write('calc/leave/', page(
    'calc/leave/', '연차 계산기 (입사일·회계연도 기준, 1년 미만 월차) | 밤낮',
    '입사일만 넣으면 지금 연차 개수, 다음 연차가 생기는 날, 근속별 연차(15~25일)를 보여 줘요. 회계연도 기준도.',
    '연차 계산기', '입사일만 넣으면 돼요. 1년 미만은 한 달 개근마다 1일, 1년이 지나면 15일, 그 뒤 2년마다 1일씩 늘어 25일까지예요.',
    f'''<div class="card">
  <div class="row">
    <div><label for="start">입사일</label><input id="start" type="date"></div>
    <div><label for="today">기준일</label><input id="today" type="date"></div>
  </div>
  <span class="glabel" id="basisL">회사가 연차를 주는 기준</span>
  <div class="chips" id="basis" role="group" aria-labelledby="basisL">
    <button type="button" class="chip" data-v="hire" aria-pressed="true">입사일 기준</button>
    <button type="button" class="chip" data-v="fiscal" aria-pressed="false">회계연도 기준</button>
  </div>
  <div id="fiscalBox" hidden><label for="fm">회계연도 시작 달</label><select id="fm">{''.join(f'<option value="{m}">{m}월 1일</option>' for m in range(1, 13))}</select></div>
</div>
{result('지금 생긴 연차')}''',
    r'''(function(){
var B=Bamnat,F=B.f,$=B.$,D=B.d,save=function(){};
var basis=B.chips($('basis'),function(){calc();});
function sum(l){return Math.round(l.reduce(function(a,x){return a+x.days;},0)*100)/100;}
function calc(){
  $('fiscalBox').hidden=basis.get()!=='fiscal';
  var s=D.parse($('start').value),t=D.parse($('today').value);
  if(!s||!t||t<s){$('out').textContent='-';$('lines').innerHTML=B.rows([s&&t&&t<s?{note:'기준일이 입사일보다 빨라요.'}:null]);save();return;}
  var fiscal=basis.get()==='fiscal',fm=+$('fm').value,lots=F.leaveLots(s,t,fiscal?'fiscal':'hire',fm),rows=[];
  var future=F.leaveLots(s,D.plusYears(t,25),fiscal?'fiscal':'hire',fm).filter(function(l){return l.on>t;});
  var months=lots.filter(function(l){return l.kind==='month';}),years=lots.filter(function(l){return l.kind!=='month';}),cur=years[years.length-1];
  if(!cur){$('head').textContent='입사 1년 전, 지금까지 생긴 연차';$('out').textContent=months.length+'일';
    rows.push(['한 달 개근마다 1일 (최대 11일)',months.length+'일']);
    if(months.length)rows.push(['이 연차를 쓸 수 있는 마지막 날',D.dot(months[0].until)]);}
  else{$('head').textContent=(cur.kind==='part'?'첫 회계연도 (비례)':(fiscal?'회계연도 ':'근속 ')+cur.n+'년')+', 올해 생긴 연차';$('out').textContent=B.fmt(cur.days)+'일';
    if(months.length&&!fiscal)rows.push(['입사 1년 전 월 개근 연차',months.length+'일 (입사 1년까지 써요)']);}
  var nx=future[0];if(nx)rows.push(['다음 연차가 생기는 날 ('+D.dot(nx.on)+')',B.fmt(nx.days)+'일'+(nx.kind==='month'?' (월 개근)':'')]);
  if(fiscal){rows.push({note:'회계연도 기준이에요. 퇴사할 때 입사일 기준보다 적게 받았으면 모자란 만큼 연차수당으로 정산해요(고용노동부 행정해석).'});
    var gap=Math.round((sum(F.leaveLots(s,t,'hire'))-sum(lots))*100)/100;
    rows.push(['오늘 그만두면 입사일 기준과 차이',gap>0?B.fmt(gap)+'일 모자라요':'모자라지 않아요']);
    lots.filter(function(l){return l.kind!=='month';}).forEach(function(l){rows.push([D.dot(l.on)+(l.kind==='part'?' 첫해 ('+l.served+'일÷365×15)':' '+l.n+'년 근속'),B.fmt(l.days)+'일']);});}
  else{rows.push({note:'근속 연수별 (입사일 기준)'});
    [1,2,3,5,7,10,15,21].forEach(function(n){rows.push([n+'년 ('+D.dot(D.plusDays(D.periodEnd(s,12*n),1))+')',F.yearDays(n)+'일']);});}
  $('lines').innerHTML=B.rows(rows);save();
}
var t=D.today();$('today').value=D.iso(t);$('start').value=D.iso(D.plusMonths(t,-14));
save=B.keep(['start','today','fm'],{basis:basis});
['start','today','fm'].forEach(function(i){$(i).addEventListener('input',calc);$(i).addEventListener('change',calc);});calc();
B.shareButton($('share'),'연차 계산 결과');
})();''',
    [('연차는 몇 개 생기나요?', '1년 동안 80% 이상 출근하면 15일이 생기고, 3년 이상 일하면 처음 1년을 넘은 뒤 2년마다 1일씩 늘어 최대 25일이에요(근로기준법 제60조).'),
     ('입사 1년이 안 됐어요.', '1년 미만이거나 1년 동안 출근율이 80% 미만이면 한 달 개근할 때마다 1일씩, 최대 11일이 생겨요. 이 연차는 입사 1년이 되는 날까지 써야 해요.'),
     ('회사가 회계연도(1월 1일) 기준으로 줘요.', "'회계연도 기준'을 고르면 첫해는 재직 일수만큼 나눠(15일 × 재직일수 ÷ 365) 보여 줘요. 퇴사할 때 입사일 기준보다 적으면 그만큼 정산해야 해요."),
     ('5명 미만 회사도 연차가 있나요?', '상시 근로자 5명 미만 사업장은 연차 규정이 적용되지 않아요(근로기준법 제11조).'),
     ('교대 근무라 연차를 언제 썼는지 헷갈려요.', '밤낮 앱 달력에 연차를 적으면 올해 쓴 날과 남은 날을 함께 보여 줘요.')],
    'leave', '쓴 연차, 남은 연차를 달력에서', '밤낮 달력에 연차·반차를 적으면 남은 연차와 쓴 날이 한눈에 보이고, 연차 날엔 근무 알람이 쉬어요.', 2))

# ---------- 퇴직금 ----------
write('calc/severance/', page(
    'calc/severance/', '퇴직금 계산기 (평균임금 × 30일 × 재직일수/365) | 밤낮',
    '입사일·퇴사일과 최근 3개월 월급으로 평균임금과 예상 퇴직금을 법정 방식 그대로 계산해요. 상여·연차수당 반영.',
    '퇴직금 계산기', '입사일과 마지막 근무일, 최근 석 달 월급만 넣으면 돼요. 상여와 연차수당이 있으면 함께 넣어요.',
    f'''<div class="card">
  <div class="row">
    <div><label for="start">입사일</label><input id="start" type="date"></div>
    <div><label for="last">마지막 근무일</label><input id="last" type="date"></div>
  </div>
  <label for="pay">최근 3개월 월 평균 세전 임금 (원)<span class="hint">기본급과 매달 받은 수당(야간·연장 포함)을 더한 금액, 세금 떼기 전</span></label>
  <input id="pay" {DATA_MONEY} value="3,000,000" data-max="1000000000">
  <div class="row">
    <div><label for="bonus">1년 상여 합계</label><input id="bonus" {DATA_MONEY} value="0" data-max="10000000000"></div>
    <div><label for="leavepay">1년 연차수당</label><input id="leavepay" {DATA_MONEY} value="0" data-max="1000000000"></div>
  </div>
  <label for="ordinary">1일 통상임금 (모르면 비워 둬요)<span class="hint">평균임금이 통상임금보다 낮으면 통상임금으로 셈해요(근로기준법 제2조 제2항).</span></label>
  <input id="ordinary" {DATA_MONEY} data-max="10000000">
</div>
{result('예상 퇴직금 (세전)')}''',
    r'''(function(){
var B=Bamnat,F=B.f,$=B.$,D=B.d,save=function(){};
function calc(){
  var s=D.parse($('start').value),l=D.parse($('last').value);
  if(!s||!l||l<s){$('out').textContent='-';$('lines').innerHTML=B.rows([s&&l&&l<s?{note:'마지막 근무일이 입사일보다 빨라요.'}:null]);save();return;}
  var r=F.severance({hire:s,last:l,monthPay:B.val('pay'),bonus:B.val('bonus'),leavePay:B.val('leavepay'),ordinaryDaily:B.val('ordinary')});
  var rows=[['재직 일수',r.days.toLocaleString('ko-KR')+'일 (약 '+B.fmt(Math.floor(r.days/365*10)/10)+'년)'],['최근 3개월 날수',r.n3+'일'],
    ['3개월 임금 + 상여·연차수당의 3/12',B.won(r.total)],['1일 평균임금',B.won(r.avg)]];
  if(r.byOrdinary)rows.push(['통상임금이 더 높아 그 값으로',B.won(r.daily)]);
  if(!r.fullYear){$('out').textContent='0원';var need=D.between(D.plusDays(l,1),D.plusYears(s,1));
    rows.push({note:'계속 근로 1년 미만이면 퇴직금이 생기지 않아요. '+need+'일 더 다니면 생겨요.'});}
  else{$('out').textContent=B.won(r.amount);rows.push(['1일 평균임금 × 30일 × 재직일수 ÷ 365',B.won(r.amount),'sum']);}
  rows.push({note:'4주 평균 1주 15시간 이상 일했을 때 받아요. 퇴직소득세를 떼기 전 금액이에요.'});
  $('lines').innerHTML=B.rows(rows);save();
}
var t=D.today();$('last').value=D.iso(t);$('start').value=D.iso(D.plusYears(t,-4));
save=B.keep(['start','last','pay','bonus','leavepay','ordinary']);
['start','last','pay','bonus','leavepay','ordinary'].forEach(function(i){$(i).addEventListener('input',calc);});calc();
B.shareButton($('share'),'퇴직금 계산 결과');
})();''',
    [('퇴직금은 누가 받나요?', '같은 회사에서 1년 이상 일했고 4주 평균 1주 15시간 이상 일한 근로자가 받아요(근로자퇴직급여 보장법 제4조). 알바·계약직도 같아요.'),
     ('평균임금은 어떻게 구하나요?', '퇴직 전 3개월 동안 받은 임금 총액을 그 3개월의 날수(89~92일)로 나눠요. 1년 상여와 연차수당은 3/12만 더해요(근로기준법 제2조). 이렇게 구한 값이 통상임금보다 낮으면 통상임금으로 셈해요.'),
     ('1년은 365일인가요?', '달력으로 만 1년이에요. 2월 29일이 끼면 366일을 다녀야 1년이에요. 마지막 근무일 다음 날이 입사 1년 되는 날이거나 그 뒤면 받아요.'),
     ('야간·연장 수당도 들어가나요?', '네. 매달 받은 야간·연장·휴일 수당도 임금이라 평균임금에 들어가요. 교대 근무자는 수당이 많은 달이 마지막 3개월이면 퇴직금이 커져요.'),
     ('세금은 얼마나 떼나요?', '퇴직금에는 퇴직소득세가 따로 붙어요. 근속 연수가 길수록 공제가 커서 근로소득세보다 훨씬 적어요. 이 계산기는 세전 금액이에요.')],
    'severance', '퇴직 전 3개월 수당까지 기록해 두세요', '밤낮에 근무를 넣어 두면 달마다 야간·연장 수당을 담은 예상 월급이 남아 퇴직금 평균임금을 맞춰 보기 좋아요.', 2))
print('calc ok')

# ---------- 교대 근무 달력 ----------
FALLBACK_PATS = [{'name': p['name'], 'description': p['description'], 'codes': p['codes']} for p in PRESETS if p['codes'] != 'DDDDDOO'][:6]
write('shift/', page(
    'shift/', '교대 근무 달력 만들기 · 4조2교대·3조2교대 근무표 | 밤낮',
    '4조2교대·3조2교대·4조3교대·주야비휴 순서와 첫날만 고르면 석 달 근무 달력이 바로 나와요. 앱에선 알람까지.',
    '교대 근무 달력 만들기', '근무 순서와 첫날만 고르면 석 달 근무 달력이 바로 나와요. 마음에 들면 같은 순서로 앱에 옮겨 알람까지 맞춰요.',
    '''<div class="card">
  <label for="pat">근무 순서</label>
  <select id="pat"></select>
  <label for="custom">직접 적기<span class="hint">주=D 오후=E 야=N 휴=O 당번=X 비번=B. 예: DDNNOO</span></label>
  <input id="custom" autocomplete="off" autocapitalize="characters" maxlength="62">
  <label for="first">이 순서의 첫날<span class="hint">순서 맨 앞 근무를 하는 날이에요. 예: 주주야야휴휴면 첫 주간 날.</span></label>
  <input id="first" type="date">
  <label class="check"><input type="checkbox" id="once"> 반복하지 않아요 (한 달 듀티표처럼 적은 만큼만)</label>
  <label class="check"><input type="checkbox" id="keep" checked> 이 브라우저에 기억해요 (다음에 열면 바로 내 달력)</label>
</div>
<div class="card" id="calCard">
  <div class="today-big" id="today" aria-live="polite"></div>
  <div class="calnav">
    <button type="button" class="chip" id="prev">◀ 앞 석 달</button>
    <button type="button" class="chip" id="now">오늘로</button>
    <button type="button" class="chip" id="next">다음 석 달 ▶</button>
  </div>
  <div id="cal"></div>
  <label for="ask">그날 나는?</label>
  <input id="ask" type="date">
  <div id="askOut" class="say" aria-live="polite"></div>
  <div class="share"><button type="button" class="chip" id="share">같은 조 동료에게 이 달력 보내기</button></div>
  <p class="note">휴대폰 홈 화면에 이 쪽을 더해 두면 매일 오늘 근무가 바로 보여요. (크롬 ⋮ › 홈 화면에 추가 · 사파리 공유 › 홈 화면에 추가)</p>
</div>
<div class="card app-next">
  <strong class="t">이 달력, 앱에서는 이렇게 이어져요</strong>
  <ul class="ticks">
    <li><b>몇 년치가 한 번에</b><span>같은 순서와 첫날을 고르면 앞으로의 근무가 다 채워져요. 대근·연차로 바뀐 날만 눌러 고쳐요.</span></li>
    <li><b>근무마다 다른 알람</b><span>주간·야간 시각이 따로 울리고, 쉬는 날엔 조용해요.</span></li>
    <li><b>우리 회사 방식은 회사 이름으로</b><span>순서를 몰라도 앱의 회사 찾기에서 고르면 근무 순서와 시간이 맞춰져요.</span></li>
    <li><b>이번 달 예상 월급</b><span>야간·휴일 수당까지 근무를 넣는 대로 따라와요.</span></li>
  </ul>
  <a class="btn" data-play="shift_cal" href="''' + PLAY + '''">''' + PLAY_ICON + '''앱 받고 같은 순서 고르기</a>
  <span class="note">로그인·회원가입 없이 무료예요. 앱 첫 화면에서 이 순서를 고르면 몇 년치가 채워져요.</span>
</div>
<p class="btns"><a class="chip" href="print/">''' + str(PRINT_YEAR) + ''' 열두 달 달력 인쇄 (A4 한 장, 조 나란히도)</a><a class="chip" href="../form/">근무표 엑셀 양식</a></p>
<h2 id="order-list">교대 순서별 설명</h2>
<div class="card" style="margin-top:18px">
  <strong>간호사처럼 매달 근무표가 바뀌나요?</strong><br>
  <span class="muted">위 '직접 적기'에 한 달 순서를 적고 '반복하지 않아요'를 켜면 그달 듀티를 달력으로 볼 수 있어요. 밤낮 앱에서는 근무표 사진을 찍으면 내 줄을 찾아 달력에 넣어 줘요.</span>
</div>''',
    r'''(function(){
var B=Bamnat,$=B.$,D=B.d,pats=[],off=0;
var NAME={D:'주',E:'오',N:'야',O:'휴',X:'당',B:'비'},FULL={D:'주간',E:'오후',N:'야간',O:'휴무',X:'당번',B:'비번'};
function codes(){var c=$('custom').value.toUpperCase().replace(/[^DENOXB]/g,'');return c||$('pat').value;}
function store(on){try{if(on)localStorage.setItem('bn_shift',JSON.stringify({p:codes(),s:$('first').value,o:$('once').checked?1:0}));else localStorage.removeItem('bn_shift');}catch(e){}}
function at(c,start,d){var k=D.between(start,d);if($('once').checked)return k>=0&&k<c.length?c[k]:'';k%=c.length;if(k<0)k+=c.length;return c[k];}
function draw(){
  var c=codes(),start=D.parse($('first').value);if(!c||!start){$('cal').innerHTML='';$('today').textContent='';return;}
  var now=D.today(),t=at(c,start,now);
  $('today').innerHTML='오늘 '+D.md(now)+' <b class="s '+t+'">'+(FULL[t]||'근무 없음')+'</b> <span class="muted">· 순서 '+c.length+'일</span>';
  var out='',base=D.plusMonths(new Date(Date.UTC(now.getUTCFullYear(),now.getUTCMonth(),1)),off*3);
  for(var m=0;m<3;m++){var first=D.plusMonths(base,m),y=first.getUTCFullYear(),mo=first.getUTCMonth(),last=new Date(Date.UTC(y,mo+1,0)).getUTCDate();
    out+='<h3 class="month-title">'+y+'년 '+(mo+1)+'월</h3><div class="cal">';
    '일월화수목금토'.split('').forEach(function(w){out+='<div class="h">'+w+'</div>';});
    for(var i=0;i<first.getUTCDay();i++)out+='<div></div>';
    for(var d=1;d<=last;d++){var day=new Date(Date.UTC(y,mo,d)),s=at(c,start,day);
      out+='<div class="d'+(+day===+now?' today':'')+'"><span class="n">'+d+'</span>'+(s?'<span class="s '+s+'" title="'+FULL[s]+'">'+NAME[s]+'</span>':'')+'</div>';}
    out+='</div>';}
  $('cal').innerHTML=out;
  try{history.replaceState(null,'','?p='+c+'&s='+$('first').value+($('once').checked?'&o=1':''));}catch(e){}
  store($('keep').checked);ask();
  var a=document.querySelector('a[data-play="shift_cal"]');if(a)a.href=B.playLink('shift_cal','shift','&utm_content='+c.slice(0,24));
}
function ask(){var d=D.parse($('ask').value),c=codes(),s=D.parse($('first').value);if(!d||!c||!s){$('askOut').textContent='';return;}var x=at(c,s,d);
  $('askOut').textContent=D.dot(d)+' '+(FULL[x]?B.josa(FULL[x],'이에요/예요').replace(/(이에요|예요)$/,'')+(x==='O'||x==='B'?', 쉬는 날이에요.':' 근무예요.'):'근무가 없어요.');}
function esc(s){return String(s||'').replace(/[&<>"]/g,function(ch){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[ch];});}
var qs=new URLSearchParams(location.search),saved=null;
try{saved=JSON.parse(localStorage.getItem('bn_shift')||'null');}catch(e){}
if(!qs.get('p')&&saved&&saved.p){$('custom').value=saved.p;$('first').value=saved.s;$('once').checked=!!saved.o;}
else{$('first').value=qs.get('s')||D.iso(D.today());if(qs.get('p'))$('custom').value=qs.get('p');$('once').checked=qs.get('o')==='1';}
fetch(B.base+'remote/v1/presets.json').then(function(r){return r.json();}).catch(function(){return null;}).then(function(j){
  pats=(j&&j.data&&j.data.patterns)||%FALLBACK%;
  $('pat').innerHTML=pats.filter(function(p){return p.codes!=='DDDDDOO';}).map(function(p){return '<option value="'+esc(p.codes)+'">'+esc(p.name)+' · '+esc(p.description)+'</option>';}).join('');
  var c=$('custom').value.toUpperCase();if(pats.some(function(p){return p.codes===c;})){$('pat').value=c;$('custom').value='';}
  draw();
});
B.shareButton($('share'),'우리 조 근무 달력');
$('pat').addEventListener('change',function(){$('custom').value='';draw();});
['custom','first','once'].forEach(function(i){$(i).addEventListener('input',draw);$(i).addEventListener('change',draw);});
$('keep').addEventListener('change',function(){store($('keep').checked);});
$('ask').addEventListener('input',ask);
$('prev').onclick=function(){off--;draw();};$('next').onclick=function(){off++;draw();};$('now').onclick=function(){off=0;draw();};
})();'''.replace('%FALLBACK%', json.dumps(FALLBACK_PATS, ensure_ascii=False)),
    [('4조 2교대는 어떤 순서인가요?', '가장 흔한 것은 주간·야간·비번·휴무(주야비휴) 4일 순서예요. 회사에 따라 주주야야휴휴휴휴(8일)처럼 길게 도는 곳도 있어요. 위에서 순서를 골라 첫날만 넣으면 달력이 나와요.'),
     ('3조 2교대와 4조 3교대는 어떻게 다른가요?', '3조 2교대는 세 조가 주간·야간 두 근무를 돌아가며(예: 주주야야휴휴), 4조 3교대는 네 조가 주간·오후·야간 세 근무를 돌아가요(예: 주주오오야야휴휴).'),
     ('우리 회사 교대 순서를 잘 모르겠어요.', '밤낮 앱의 회사 찾기에서 회사 이름으로 고르면 근무 순서와 시간이 맞춰져요. 없는 회사는 앱에서 알려 주시면 확인해서 더하고, 그동안은 순서를 직접 적어 써요.'),
     ('고른 순서는 어디에 남나요?', "'이 브라우저에 기억해요'를 켜 두면 이 기기 브라우저에만 남아 다음에 열 때 바로 내 달력이 보여요. 서버로는 보내지 않고, 끄면 바로 지워요."),
     ('달력을 휴대폰에 넣고 알람도 맞출 수 있나요?', '밤낮 앱에서 같은 순서를 고르면 몇 년치가 한 번에 채워지고, 주간·야간마다 다른 시각에 울리는 근무 알람도 맞춰요. 쉬는 날엔 조용해요.')],
    'shift', '이 달력을 휴대폰에, 근무 알람까지', '밤낮 앱에서 같은 순서를 고르면 몇 년치 근무가 한 번에 채워지고, 주간·야간마다 다른 근무 알람과 이번 달 예상 월급까지 봐요.', 1))
print('shift ok')

# ---------- 공휴일 ----------
WD = '월화수목금토일'


def hol_days(year):
    return [h for h in HOLS['days'] if h['date'].startswith(str(year))]


HOL_SCRIPT = r'''(function(){
var B=Bamnat,$=B.$,D=B.d,H=%H%,NAME={D:'주간',E:'오후',N:'야간',O:'휴무',X:'당번',B:'비번'};
var t=D.today(),next=null;
document.querySelectorAll('tr[data-d]').forEach(function(tr){var d=D.parse(tr.dataset.d);if(d<t)tr.classList.add('past');else if(!next||d<next.d)next={d:d,n:tr.dataset.n};});
if($('dday')){if(next){var k=D.between(t,next.d);$('dday').innerHTML='<span class="muted">다음 공휴일</span><span class="big">'+(k?'D-'+k:'오늘')+'</span><span>'+D.md(next.d)+' '+next.n+'</span>';}else $('dday').hidden=true;}
function draw(){var c=$('pat').value,s=D.parse($('first').value);if(!c||!s)return;var rest=[],work=[];
  H.forEach(function(h){var d=D.parse(h[0]),k=D.between(s,d)%c.length;if(k<0)k+=c.length;var code=c[k],x=D.md(d)+' '+h[1];
    (code==='O'||code==='B'?rest:work).push(x+(code==='O'||code==='B'?'':' · '+NAME[code]));});
  $('split').innerHTML='<strong>쉬는 공휴일 '+rest.length+'일</strong><br><span class="muted">'+(rest.join(', ')||'없음')+'</span><br><br><strong>일하는 공휴일 '+work.length+'일</strong><br><span class="muted">'+(work.join(', ')||'없음')+'</span><br><br><a href="%UP%calc/allowance/">휴일에 일하면 수당은? 휴일수당 계산기</a>';}
if($('pat')){fetch(B.base+'remote/v1/presets.json').then(function(r){return r.json();}).then(function(j){
  $('pat').innerHTML=j.data.patterns.filter(function(p){return p.codes!=='DDDDDOO';}).map(function(p){return '<option value="'+p.codes+'">'+p.name+' · '+p.description+'</option>';}).join('');draw();}).catch(function(){});
  $('first').value=D.iso(t);$('pat').addEventListener('change',draw);$('first').addEventListener('input',draw);}
})();'''


def hol_table(days):
    return ''.join(f"<tr data-d=\"{h['date']}\" data-n=\"{html.escape(h['name'])}\"><th scope=\"row\">{int(h['date'][5:7])}월 {int(h['date'][8:])}일 ({WD[dt.date.fromisoformat(h['date']).weekday()]})</th><td>{html.escape(h['name'])}</td></tr>" for h in days)


def long_runs(year, days):
    hd = {h['date']: h['name'] for h in days}
    def off(x): return x.weekday() >= 5 or x.isoformat() in hd
    runs, cur = [], []
    x = dt.date(year, 1, 1)
    while x.year == year:
        if off(x): cur.append(x)
        else:
            if len(cur) >= 3 and any(c.isoformat() in hd for c in cur): runs.append(cur)
            cur = []
        x += dt.timedelta(days=1)
    if len(cur) >= 3 and any(c.isoformat() in hd for c in cur): runs.append(cur)
    return ''.join(f"<li>{r[0].month}/{r[0].day}({WD[r[0].weekday()]}) ~ {r[-1].month}/{r[-1].day}({WD[r[-1].weekday()]}) · <strong>{len(r)}일</strong> · {html.escape(', '.join(sorted(set(hd[c.isoformat()] for c in r if c.isoformat() in hd))))}</li>" for r in runs)


for year in HOL_YEARS:
    days = hol_days(year)
    weekday_n = sum(1 for h in days if dt.date.fromisoformat(h['date']).weekday() < 5)
    body = f'''<div class="card dday" id="dday"></div>
<div class="card">
  <strong>{year}년 공휴일 {len(days)}일</strong> <span class="muted">· 대체공휴일 포함, 그중 월~금 {weekday_n}일 · 지난 날은 흐리게</span>
  <table class="lines hol" style="margin-top:8px">{hol_table(days)}</table>
</div>
<h2>{year}년 사흘 이상 연휴</h2>
<ul class="links">{long_runs(year, days)}</ul>
<h2>교대 근무자라면: 공휴일에 나는 쉬나, 일하나</h2>
<p class="note">교대 근무는 공휴일과 상관없이 순서대로 돌아요. 근무 순서와 첫날을 넣으면 {year}년 공휴일 중 내가 쉬는 날과 일하는 날(휴일 근로 수당이 붙을 수 있는 날)을 나눠 보여 줘요.</p>
<div class="card">
  <label for="pat">근무 순서</label><select id="pat"></select>
  <label for="first">이 순서의 첫날</label><input id="first" type="date">
  <div id="split" aria-live="polite" style="margin-top:12px"></div>
</div>
<p><a href="../">올해·내년 공휴일 한눈에</a>{"".join(f' · <a href="../{y}/">{y}년 공휴일</a>' for y in HOL_YEARS if y != year)}</p>'''
    script = HOL_SCRIPT.replace('%H%', json.dumps([[h['date'], h['name']] for h in days], ensure_ascii=False)).replace('%UP%', '../../')
    write(f'holiday/{year}/', page(
        f'holiday/{year}/', f'{year}년 공휴일·대체공휴일·연휴 달력 | 밤낮',
        f'{year}년 공휴일 {len(days)}일과 대체공휴일, 연휴를 한눈에. 교대 순서를 넣으면 공휴일 중 쉬는 날과 일하는 날을 나눠 봐요.',
        f'{year}년 공휴일·연휴 달력', f'{year}년 공휴일과 대체공휴일, 주말을 붙여 쉬는 연휴를 모았어요. 교대 근무자는 아래에서 내 근무 순서로 공휴일에 쉬는지 바로 봐요.',
        body, script,
        [(f'{year}년 공휴일은 며칠인가요?', f'관공서의 공휴일에 관한 규정과 한국천문연구원 월력요항 기준으로 대체공휴일까지 {len(days)}일이에요(일요일과 겹친 날 포함, 그중 월~금 {weekday_n}일). 임시공휴일이 정해지면 고쳐요.'),
         ('민간 회사도 공휴일에 쉬나요?', '상시 5명 이상 사업장은 공휴일이 유급휴일이에요(근로기준법 제55조 제2항). 쉬는 날에 일하면 휴일 근로 수당(8시간까지 50%)을 받아요. 교대 근무는 대체휴일 합의가 있으면 다른 날 쉬기도 해요.'),
         ('5월 1일 노동절(근로자의 날)도 쉬나요?', '5월 1일은 법으로 정한 유급휴일이라 근로자는 쉬고, 일하면 휴일 근로 수당을 받아요.'),
         ('공휴일 근무 알람을 따로 맞춰야 하나요?', '밤낮 앱은 공휴일을 달력에 표시하고, 근무 순서대로 근무 알람을 울려요. 쉬는 날엔 알람이 조용해요.')],
        f'holiday{year}', f'{year}년 근무와 공휴일을 한 달력에', '밤낮 앱은 공휴일·대체공휴일을 근무 달력에 함께 보여 주고, 공휴일 근무엔 휴일 수당까지 이번 달 월급에 넣어요.', 2))

# [I50b-06][WEB-22] 연도 없는 /holiday/: 올해와 내년을 한 쪽에. 메뉴는 늘 여기로 걸어 해마다 고치지 않아요.
show = [y for y in (THIS, THIS + 1) if y in HOL_YEARS]
blocks = ''.join(f'''<div class="card">
  <h2 class="in-card"><a href="{y}/">{y}년 공휴일 {len(hol_days(y))}일</a></h2>
  <table class="lines hol">{hol_table(hol_days(y))}</table>
</div>''' for y in show)
all_days = [h for y in show for h in hol_days(y)]
write('holiday/', page(
    'holiday/', f'공휴일·대체공휴일 달력 ({"·".join(map(str, show))}) | 밤낮',
    f'{"·".join(map(str, show))}년 공휴일과 대체공휴일, 다음 공휴일까지 남은 날을 한눈에. 교대 근무자 쉬는 공휴일도 확인해요.',
    '공휴일·연휴 달력', '올해와 내년 공휴일을 한 쪽에 모았어요. 지난 날은 흐리게, 다음 공휴일까지 며칠 남았는지 맨 위에 보여 줘요.',
    f'<div class="card dday" id="dday"></div>\n{blocks}\n<ul class="links">' + ''.join(f'<li><a href="{y}/"><strong>{y}년 연휴·교대 근무자 쉬는 날</strong></a></li>' for y in HOL_YEARS) + '</ul>',
    HOL_SCRIPT.replace('%H%', json.dumps([[h['date'], h['name']] for h in all_days], ensure_ascii=False)).replace('%UP%', '../'),
    [('공휴일은 어떻게 정해지나요?', '관공서의 공휴일에 관한 규정과 한국천문연구원 월력요항을 따라요. 대체공휴일·임시공휴일이 정해지면 밤낮 앱과 같은 자료를 고쳐 바로 반영해요.'),
     ('민간 회사도 공휴일에 쉬나요?', '상시 5명 이상 사업장은 공휴일이 유급휴일이에요(근로기준법 제55조 제2항). 쉬는 날에 일하면 휴일 근로 수당을 받아요.')],
    f'holiday{show[0]}', '공휴일과 근무를 한 달력에', '밤낮 앱은 공휴일·대체공휴일을 근무 달력에 함께 보여 주고, 공휴일 근무엔 휴일 수당까지 이번 달 월급에 넣어요.', 1, crumbs=[]))
print('holiday ok')

# ---------- 근무 순서별 ----------
NAME = {'D': '주간', 'E': '오후', 'N': '야간', 'O': '휴무', 'X': '당번', 'B': '비번'}
SHORT = {'D': '주', 'E': '오', 'N': '야', 'O': '휴', 'X': '당', 'B': '비'}


def strip(codes, n):
    cells = ''.join(f'<div class="d"><span class="n">{i+1}일</span><span class="s {c}">{SHORT[c]}</span></div>' for i, c in enumerate((codes * 10)[:n]))
    return f'<div class="cal">{cells}</div>'


pat_links = []
for p in PRESETS:
    if p['codes'] == 'DDDDDOO': continue
    slug = p['id'][2:]
    L = len(p['codes']); work = sum(1 for c in p['codes'] if c not in 'OB'); nights = p['codes'].count('N'); pm = 365 / 12 * work / L
    seq = '·'.join(NAME[c] for c in p['codes'])
    others = ''.join(f'<li><a href="../{q["id"][2:]}/">{html.escape(q["name"])}</a> · {html.escape(q["description"])}</li>' for q in PRESETS if q is not p and q['codes'] != 'DDDDDOO')
    src = f'<p class="note">출처: {html.escape(p["source"])}</p>' if p.get('source') else ''
    body = f'''<div class="card">
  <table class="lines info">
    <tr><th scope="row">순서</th><td>{html.escape(p["description"])}</td></tr>
    <tr><th scope="row">한 바퀴</th><td>{L}일</td></tr>
    <tr><th scope="row">한 바퀴 중 근무</th><td>{work}일{f" (야간 {nights}일)" if nights else ""}</td></tr>
    <tr><th scope="row">한 달 평균 근무일</th><td>약 {pm:.1f}일</td></tr>
  </table>
  <p class="note">순서: {html.escape(seq)}</p>
  {src}
</div>
<h2>4주 미리 보기</h2>
{strip(p["codes"], 28)}
<p class="btns"><a class="btn" href="../../?p={p["codes"]}">내 첫날로 석 달 달력 보기</a><a class="chip" href="../../print/{slug}/">{PRINT_YEAR} 달력 인쇄</a></p>
<h2>다른 교대 순서</h2>
<ul class="links">{others}</ul>
<p class="note">우리 회사 방식을 모르면 밤낮 앱의 회사 찾기에서 회사 이름으로 골라요.</p>'''
    name = p['name']
    title = f'{name} 근무표 달력 만들기 | 밤낮'
    if len(title) > 60: title = f'{name} 근무표 | 밤낮'
    write(f'shift/p/{slug}/', page(
        f'shift/p/{slug}/', title,
        (f'{name} 근무표: {L}일 주기, 한 달 평균 약 {pm:.0f}일 근무. 첫날만 넣으면 석 달 근무 달력을 바로 보여 줘요.')[:80],
        f'{name} 근무표', f'{html.escape(p["description"])} 순서로 {L}일마다 돌아요. 첫 근무 날만 고르면 몇 달치 달력이 나와요.',
        body, '',
        [(f'{josa(name, "는/은")} 한 달에 며칠 일하나요?', f'{L}일 중 {work}일 일해서 한 달(30.4일)에 평균 약 {pm:.1f}일이에요. 달마다 하루 이틀 차이가 나요.'),
         (f'{name} 근무 알람을 맞추려면?', '밤낮 앱에서 같은 순서를 고르면 근무마다 다른 시각에 알람이 울리고, 쉬는 날엔 조용해요. 대근·연차로 바뀐 날만 눌러 고치면 돼요.')],
        f'shift_{slug}', f'{name}, 알람까지 한 번에', f'밤낮 앱에서 {josa(name, "를/을")} 고르면 몇 년치 근무가 채워지고 주간·야간마다 다른 근무 알람이 울려요.', 3))
    pat_links.append((slug, p))

pl = ''.join(f'<li><a href="p/{s}/">{html.escape(p["name"])}</a> · {html.escape(p["description"])}</li>' for s, p in pat_links)
patch('shift/index.html', '<h2 id="order-list">교대 순서별 설명</h2>', f'<h2 id="order-list">교대 순서별 설명</h2><ul class="links">{pl}</ul>', 'href="p/')

# ---------- 계산기 모음 ----------
hub_body = '<ul class="links">' + ''.join(f'<li><a href="{u}"><strong>{t}</strong></a><br><span class="muted">{d}</span></li>' for u, t, d in [
    ('juhyu/', '주휴수당 계산기', '시급과 1주 근무 시간으로 주휴수당과 월 환산'),
    ('alba/', '알바 월급 계산기', '주휴 포함 월급, 3.3%·4대보험 뺀 실수령, 이번 달 날수로'),
    ('allowance/', '야간·연장·휴일수당 계산기', '출퇴근 시각으로 밤 10시~아침 6시, 8시간 초과 시간을 나눠 계산'),
    ('week/', '한 주 연장·야간·휴일 계산기', '요일별 출퇴근으로 하루 8시간·주 40시간 넘은 연장과 주 52시간'),
    ('boss/', '사장님 인건비 계산기', '주휴수당과 사업주 4대보험까지 직원 한 달 인건비'),
    ('salary/', '월급·연봉 실수령액 계산기', '4대보험과 간이세액표 소득세를 뺀 통장 금액'),
    ('leave/', '연차 계산기', '입사일·회계연도 기준으로 지금 연차와 근속별 개수'),
    ('severance/', '퇴직금 계산기', '평균임금 × 30일 × 재직일수/365, 통상임금 비교'),
    ('daily/', '일당 계산기 (일용직 세금)', '현장·인테리어·건설 일당의 세금과 실수령, 날마다·모아서'),
    ('../shift/', '교대 근무 달력 만들기', '4조2교대·3조2교대·주야비휴 순서로 석 달 달력, 교대 순서별 설명'),
    ('../holiday/', '공휴일·연휴 달력', '올해·내년 공휴일, 다음 공휴일까지 남은 날, 교대 근무자 쉬는 공휴일'),
    ('../form/', '근무표 양식 (엑셀)', '듀티표·조별 근무표·알바 스케줄표·출역표'),
    ('../shift/print/', f'{PRINT_YEAR} 교대 근무 달력 인쇄', '순서와 첫날로 열두 달 한 장')]) + '</ul>'
write('calc/', page('calc/', '근무·급여 계산기 모음 (주휴수당·야간수당·실수령액) | 밤낮',
    f'주휴수당·알바 월급·야간수당·실수령액·연차·퇴직금·일당 세금을 무료로 계산해요. {YEARS_TXT} 최저시급 반영.',
    '근무·급여 계산기', '일하는 시간이 남다른 모든 분을 위한 무료 계산기예요. 입력한 값은 이 화면에서만 계산해요.',
    hub_body, '', [
     ('계산기는 무료인가요?', '네, 로그인 없이 무료예요. 입력한 값은 휴대폰·컴퓨터 화면 안에서만 계산하고 보내지 않아요.'),
     ('계산 결과를 남에게 보낼 수 있나요?', "계산기마다 '결과 링크 보내기'를 누르면 넣은 값이 담긴 주소를 카톡 등으로 보내요. 값은 주소에만 있고 서버로 가지 않아요."),
     ('요율은 언제 바뀌나요?', '최저시급과 4대보험 요율이 고시되면 밤낮 앱과 같은 자료를 고쳐서 계산기에도 바로 반영돼요.')],
    'hub', '매달 계산하기 번거롭다면', '밤낮 앱에 근무를 넣으면 주휴·야간·연장 수당까지 이번 달 예상 월급을 저절로 보여 주고, 근무마다 다른 알람도 맞춰요.', 1))
print('shift pages', len(pat_links), 'pages', len(SITEMAP))
