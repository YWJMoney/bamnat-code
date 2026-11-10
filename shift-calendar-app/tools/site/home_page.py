# 첫 화면 bamnat.app/ (웹 2판, 사용자 2026-11-10 '최정상의 인터랙션을 가지는 대표 페이지').
# public_pages.py·print_pages.py 다음에 돌려요: python3 home_page.py ../-
# 틀은 같은 폴더의 home.html, 움직임은 공개 저장소 assets/home.js·home.css, 머리·바닥은 public_pages.py와 같은 조각을 써요.
import json, os, html, sys
HERE = os.path.dirname(os.path.abspath(__file__))
_src = open(os.path.join(HERE, 'public_pages.py'), encoding='utf-8').read().split('# ---------- 주휴수당')[0]
exec(_src)  # ROOT, LOGO, PLAY_ICON, site_header(), site_footer()

def icon(d, extra=''):
    return f'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"{extra}>{d}</svg>'
I = {
    'ALARM': icon('<circle cx="12" cy="13" r="7.5"/><path d="M12 9.5V13l2.2 1.6M4.5 4.8 7 2.8M19.5 4.8 17 2.8"/>'),
    'MOON': icon('<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"/>'),
    'CAL': icon('<rect x="3.5" y="5" width="17" height="15.5" rx="3"/><path d="M3.5 10h17M8 3v4M16 3v4"/>'),
    'KEY': icon('<path d="M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17z"/><path d="M8.5 12.2 11 14.6l4.6-5"/>'),
    'QUIET': icon('<path d="M11 5 6.5 8.8H3.5v6.4h3L11 19z"/><path d="m16 9.5 5 5M21 9.5l-5 5"/>'),
    'LOCK': icon('<rect x="4.5" y="10.5" width="15" height="10" rx="2.5"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/>'),
    'CHECK': icon('<path d="m5 12.5 4.2 4L19 7"/>'),
    'ARROW': icon('<path d="M5 12h14M13 6l6 6-6 6"/>'),
}
JOBS1 = ['간호사 3교대', '공장 4조2교대', '발전소', '경찰 지구대', '소방 3조1교대', '반도체 4조3교대', '제철', '보안·경비', '관제 센터', '24시간 맞교대']
JOBS2 = ['편의점 알바', '카페 알바', '인테리어 현장', '건설 일용직', '식당 사장님', '팀 근무표 짜는 리더', '사무직 야근', '물류 센터', '요양 보호사', '버스·택시 기사님']
GALLERY = [
    ('03-alarm', '근무가 바뀌면 알람도 알아서', '주간·야간마다 다른 시각, 쉬는 날엔 조용해요'),
    ('04-today', '출근부터 잠까지 오늘 하루 한 줄', '퇴근까지 남은 시간과 잘 시간'),
    ('13-sleep', '오늘 몇 시에 잘까, 내 하루에 맞춰', '잠드는 소리와 아침 기분까지'),
    ('05-pay', '야간·연장 수당까지 이번 달 월급 미리', '항목마다 어떻게 계산했는지 보여 줘요'),
    ('11-holiday', '설 연휴에 일해도 휴일 수당까지', '공휴일·대체공휴일이 근무 달력에'),
    ('12-leave', '남은 연차, 세지 않아도 돼요', '입사일만 넣으면 생기는 날·없어지는 날'),
    ('14-ledger', '카드 쓰면 가계부가 알아서', '결제 알림을 켜면 분류까지 저절로'),
    ('15-memo', '사진 넣은 메모, 할 일까지 한 곳에', '날짜에 적으면 달력에 보이고 알려 줘요'),
    ('08-memo-studio', '근무 날 메모를 내 마음대로', '스튜디오에서 달력·위젯까지 꾸며요'),
]
FAQ = [
    ('밤낮은 무료인가요?', '근무표, 근무마다 다른 기본 알람, 위젯은 무료로 써요. 광고는 원할 때만 하루 두 편까지 소리 없이 나와요. 프로(구독 또는 자동 갱신 없는 1년 이용권)에는 광고가 없고 명세서 대조·급여 리포트 같은 기능이 더해져요. 팀원과 직원은 무료이고, 리더·사장님 기능은 팀·직원 요금제로 써요.'),
    ('회원가입을 해야 하나요?', '아니요. 로그인이나 회원가입 없이 설치하자마자 근무를 넣어요.'),
    ('우리 회사 교대 순서가 특이해요.', '주주야야휴휴처럼 순서를 직접 적어도 되고, 종이·엑셀·PDF 근무표를 찍거나 열면 내 줄을 찾아 달력에 넣어요. 공개 자료로 확인한 회사별 교대 방식도 목록에서 골라요.'),
    ('간호사처럼 근무표가 매달 바뀌어도 되나요?', '네. 근무표가 나오는 날 사진을 찍거나 파일을 열면 내 줄을 찾아 한 달을 채워요. 헷갈리는 날은 따로 보여 줘서 바로 고쳐요.'),
    ('아이폰에서도 쓸 수 있나요?', '지금 앱은 안드로이드(Google Play)에서 받을 수 있어요. 팀 리더나 사장님이 보낸 근무표·출퇴근 링크는 아이폰에서도 앱 없이 열어 볼 수 있어요.'),
    ('궁금한 게 있으면 어디로 물어요?', '앱의 설정 › 도움말과 문의에서 보내 주세요. 보내 주신 의견으로 고친 판이 나오면 앱에서 알려 드려요. 전자우편 yoj0790@naver.com으로도 받아요.'),
]

def main():
    t = open(os.path.join(HERE, 'home.html'), encoding='utf-8').read()
    firms = len(json.load(open(os.path.join(ROOT, 'remote/v1/cycles.json'), encoding='utf-8'))['data']['cycles'])
    gallery = ''.join(
        f'<figure><div class="img"><img src="assets/app/{k}-360.webp" srcset="assets/app/{k}-360.webp 360w, assets/app/{k}-720.webp 720w" sizes="270px" width="360" height="640" alt="{html.escape(a)}" loading="lazy" decoding="async"></div><figcaption><b>{html.escape(a)}</b>{html.escape(b)}</figcaption></figure>'
        for k, a, b in GALLERY)
    jobs = lambda xs: ''.join(f'<span>{html.escape(x)}</span>' for x in xs + xs)
    faq = '\n    '.join(f'<details><summary>{html.escape(q)}</summary><p>{html.escape(a)}</p></details>' for q, a in FAQ)
    faq_ld = json.dumps([{"@type": "Question", "name": q, "acceptedAnswer": {"@type": "Answer", "text": a}} for q, a in FAQ], ensure_ascii=False)
    qr = open(os.path.join(HERE, 'home-qr.svg'), encoding='utf-8').read().strip()
    rep = {
        '<!--HEADER-->': site_header('', ''), '<!--FOOTER-->': site_footer(''),
        '<!--LOGO-->': LOGO, '<!--LOGO_DOCK-->': LOGO.replace('<svg ', '<svg class="logo" ', 1), '<!--PLAY-->': PLAY_ICON, '<!--QR-->': qr,
        '<!--GALLERY-->': gallery, '<!--JOBS1-->': jobs(JOBS1), '<!--JOBS2-->': jobs(JOBS2),
        '<!--FAQ-->': faq, '<!--FAQ_LD-->': faq_ld, '<!--FIRMS-->': str(firms),
    }
    for k, v in I.items(): rep[f'<!--I_{k}-->'] = v
    rep['<!--CHECK-->'] = I['CHECK']; rep['<!--ARROW-->'] = I['ARROW']
    for k, v in rep.items(): t = t.replace(k, v)
    assert '<!--' not in t.replace('<!-- ', ''), [l for l in t.splitlines() if '<!--' in l and '<!-- ' not in l][:3]
    open(os.path.join(ROOT, 'index.html'), 'w', encoding='utf-8').write(t)
    print('home ok', len(t))

main()
