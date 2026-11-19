#!/usr/bin/env python3
"""칸이 새 모습 시안 (개발 전 전체 검토 2026-11-12). 모두 이 파일에서 직접 그린 벡터예요.
다른 앱·캐릭터의 그림을 가져오거나 따라 그리지 않았어요. viewBox 0 0 160 160."""
import itertools

_uid = itertools.count()

INK = '#2B3157'      # 테두리(진한 남색, 검정보다 부드럽게)
EYE = '#20243F'
CREAM = '#FFF8EA'
CHEEK = '#FF9FA6'
BAND = {'day': '#F6C24F', 'eve': '#F28C6A', 'night': '#3C4AA6', 'off': '#4DBE93'}
BAND_HI = {'day': '#FFE08A', 'eve': '#FFB497', 'night': '#5B6AD0', 'off': '#7BDDB4'}

# 몸: 달력 한 칸. 아래가 살짝 더 통통한 말랑한 네모.
BODY = ('M80 36C100 36 116 37 124 44C131 50 133 63 133 88C133 112 134 125 127 132'
        'C119 140 101 141 80 141C59 141 41 140 33 132C26 125 27 112 27 88C27 63 29 50 36 44C44 37 60 36 80 36Z')


def _star(cx, cy, r, fill='#F6C24F', op=1):
    k = r * 0.32
    return (f'<path d="M{cx} {cy - r}L{cx + k} {cy - k}L{cx + r} {cy}L{cx + k} {cy + k}L{cx} {cy + r}'
            f'L{cx - k} {cy + k}L{cx - r} {cy}L{cx - k} {cy - k}Z" fill="{fill}" opacity="{op}"/>')


def _eye(cx, cy, look=0.0, big=False):
    rx, ry = (7.2, 8.2) if big else (6.4, 7.4)
    return (f'<ellipse cx="{cx + look}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{EYE}"/>'
            f'<circle cx="{cx + look - 1.9 + look * 0.25}" cy="{cy - 2.8}" r="{3.1 if big else 2.5}" fill="#fff"/>'
            f'<circle cx="{cx + look + 2.3}" cy="{cy + 3}" r="1.15" fill="#fff" opacity=".9"/>')


def _arc_eye(cx, cy, up=True, w=7.0, h=7.0):
    s = -1 if up else 1
    return (f'<path d="M{cx - w} {cy}q{w} {s * h} {2 * w} 0" fill="none" stroke="{EYE}" stroke-width="3.2" '
            f'stroke-linecap="round"/>')


def _arm(side, pose):
    """작고 통통한 팔. side -1 왼쪽, 1 오른쪽."""
    if pose == 'down':
        cx, cy, rot, rx, ry = 80 + side * 54, 107, -side * 20, 7.5, 10.5
    elif pose == 'up':
        cx, cy, rot, rx, ry = 80 + side * 56, 70, side * 34, 7.5, 12
    elif pose == 'point':
        cx, cy, rot, rx, ry = 80 + side * 62, 100, side * 104, 7, 15
    elif pose == 'front':
        return ''
    else:
        raise ValueError(pose)
    return (f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" transform="rotate({rot} {cx} {cy})" '
            f'fill="{CREAM}" stroke="{INK}" stroke-width="3"/>')


def _badge(shift):
    """머리띠 오른쪽의 작은 핀: 주간 해, 저녁 노을, 야간 달, 쉬는 날 잎."""
    if shift == 'day':
        rays = ''.join(
            f'<path d="M113 53L113 45.2" transform="rotate({a} 113 53)" stroke="#FFF6D2" stroke-width="2" '
            f'stroke-linecap="round"/>' for a in range(0, 360, 45))
        return rays + '<circle cx="113" cy="53" r="4.8" fill="#FFF6D2"/>'
    if shift == 'eve':
        return ('<path d="M105 59a8 8 0 0 1 16 0z" fill="#FFF1D6"/>'
                '<path d="M103 62.5h20" stroke="#FFF1D6" stroke-width="2" stroke-linecap="round"/>')
    if shift == 'night':
        return ('<path d="M116 46.5a8.5 8.5 0 1 0 5.5 13.6a6.8 6.8 0 0 1 -5.5 -13.6z" fill="#FFE9A8"/>'
                + _star(49, 52, 2.6, '#FFE9A8', .9) + _star(77, 48.5, 1.8, '#FFE9A8', .75))
    if shift == 'off':
        return ('<path d="M107 60c0-8 6-12.5 13-12.5c0 8-5.5 12.5-13 12.5z" fill="#EFFFF6"/>'
                '<path d="M108.5 58.5l7-7" stroke="#4DBE93" stroke-width="1.6" stroke-linecap="round"/>')
    return ''


def _sprout():
    return ('<path d="M80 37V27" stroke="#3FA57F" stroke-width="3" stroke-linecap="round"/>'
            '<path d="M80 28c-1-7-7-10-13-9c0 7 6 10.5 13 9z" fill="#63CFA2" stroke="#3FA57F" stroke-width="1.6" stroke-linejoin="round"/>'
            '<path d="M80 28c1-6 6-9 12-8c0 6-5 9-12 8z" fill="#7BDDB4" stroke="#3FA57F" stroke-width="1.6" stroke-linejoin="round"/>')


def _zz(x=126, y=30, c='#8E9BE8'):
    return (f'<g fill="none" stroke="{c}" stroke-linecap="round" stroke-linejoin="round">'
            f'<path d="M{x} {y}h10l-10 12h10" stroke-width="3"/>'
            f'<path d="M{x + 14} {y - 15}h7l-7 8.5h7" stroke-width="2.4"/></g>')


def _coin(cx, cy, r=11):
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#F6C24F" stroke="#B9862A" stroke-width="2.4"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{r - 3.6}" fill="none" stroke="#FFE7A3" stroke-width="1.6"/>'
            f'<path d="M{cx - 4.6} {cy - 3.6}l2.3 7.4l2.3 -6.2l2.3 6.2l2.3 -7.4M{cx - 5.6} {cy}h11.2" fill="none" '
            f'stroke="#8A5F12" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>')


def _cup(cx, cy):
    return (f'<path d="M{cx - 14} {cy - 10}h28v9a11 11 0 0 1 -11 11h-6a11 11 0 0 1 -11 -11z" fill="#FFFFFF" stroke="{INK}" '
            f'stroke-width="2.8" stroke-linejoin="round"/>'
            f'<path d="M{cx - 10.5} {cy - 5.6}h21" stroke="#B9794A" stroke-width="3.4" stroke-linecap="round"/>'
            f'<path d="M{cx} {cy + 1.2}c-2.2 -2.6 -5.4 -0.4 -3.6 2.2l3.6 3.4l3.6 -3.4c1.8 -2.6 -1.4 -4.8 -3.6 -2.2z" fill="#FF9FA6"/>')


def kani_a(mood='basic', shift=None, curl=None, tilt=0, date=None, dark=False):
    """시안 A '말랑 칸이': 지금 칸이의 뜻(달력 한 칸, 근무 색 머리띠, 달력 고리)은 그대로 두고 생김새만 다듬은 것.
    curl: 내일 근무 색(있으면 오른쪽 아래 귀퉁이가 살짝 접혀 그 색이 보여요)."""
    u = next(_uid)
    if shift is None:
        shift = {'sleepy': 'night', 'off': 'off', 'nightshift': 'night'}.get(mood, 'day')
    band, band_hi = BAND[shift], BAND_HI[shift]
    arms = {'basic': ('down', 'down'), 'done': ('up', 'up'), 'stretch': ('up', 'up'), 'sleepy': ('down', 'down'),
            'off': ('down', 'down'), 'point': ('down', 'point'), 'worry': ('down', 'down'),
            'payday': ('down', 'up'), 'nightshift': ('front', 'front'), 'hello': ('down', 'up')}[mood]
    o = [f'<defs><clipPath id="kb{u}"><path d="{BODY}"/></clipPath>'
         f'<linearGradient id="kg{u}" x1="0" y1="0" x2="0" y2="1"><stop offset=".5" stop-color="#E6C894" stop-opacity="0"/>'
         f'<stop offset="1" stop-color="#E0BC80" stop-opacity=".5"/></linearGradient>'
         f'<linearGradient id="kh{u}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{band_hi}"/>'
         f'<stop offset="1" stop-color="{band}"/></linearGradient></defs>']
    o.append('<ellipse cx="80" cy="148" rx="40" ry="4.5" fill="#12162C" opacity=".13"/>')
    g = [f'<g transform="rotate({tilt} 80 140)">' if tilt else '<g>']
    g.append(_arm(-1, arms[0]) + _arm(1, arms[1]))
    # 발
    g.append(f'<ellipse cx="62" cy="141.5" rx="11" ry="6.5" fill="#F3E6CC" stroke="{INK}" stroke-width="3"/>'
             f'<ellipse cx="98" cy="141.5" rx="11" ry="6.5" fill="#F3E6CC" stroke="{INK}" stroke-width="3"/>')
    # 달력 고리 (몸 뒤에서 올라와 머리띠 앞으로 넘어오는 두 고리)
    g.append(f'<path d="{BODY}" fill="{CREAM}"/>')
    g.append(f'<g clip-path="url(#kb{u})">'
             f'<path d="M20 30H140V68Q80 50 20 68Z" fill="url(#kh{u})"/>'
             f'<path d="M20 68Q80 50 140 68" fill="none" stroke="{INK}" stroke-width="2" opacity=".28"/>'
             f'<path d="M38 50Q52 41 70 40" fill="none" stroke="#fff" stroke-width="3.2" stroke-linecap="round" opacity=".5"/>'
             f'{_badge(shift)}'
             f'<rect x="20" y="30" width="120" height="115" fill="url(#kg{u})"/>')
    if curl:
        g.append(f'<path d="M140 114L108 145H140Z" fill="{BAND[curl]}"/>')
    g.append('</g>')
    g.append(f'<path d="{BODY}" fill="none" stroke="{INK}" stroke-width="3" stroke-linejoin="round"/>')
    if curl:
        g.append(f'<path d="M133.5 116.5L110.5 140.5Q107.5 121 110.5 119Q113 116.5 133.5 116.5Z" fill="#FFFDF7" stroke="{INK}" '
                 f'stroke-width="2.4" stroke-linejoin="round"/>'
                 f'<path d="M114 122.5q9 -1.5 14 -1.5" fill="none" stroke="#E6D3AE" stroke-width="1.6" stroke-linecap="round"/>')
    for x in (58, 102):
        # 어두운 바탕에서는 고리를 한 단계 밝게 해서 묻히지 않게 해요.
        rc, rh = ('#8490E0', '#DCE2FF') if dark else (INK, '#8C97D8')
        g.append(f'<rect x="{x - 5.5}" y="25" width="11" height="22" rx="5.5" fill="{rc}"/>'
                 f'<path d="M{x - 2.2} 29.5v9" stroke="{rh}" stroke-width="2" stroke-linecap="round"/>')
    if mood == 'off':
        g.append(_sprout())
    if date:
        tc = '#FFFFFF' if shift in ('night', 'off', 'eve') else '#5B3F06'
        g.append(f'<text x="80" y="55" text-anchor="middle" font-size="15" font-weight="800" fill="{tc}" '
                 f'font-family="Pretendard Variable,Pretendard,sans-serif">{date}</text>')
    # 볼
    ck = .45 if mood == 'worry' else .7
    g.append(f'<ellipse cx="45" cy="108" rx="8.5" ry="5.2" fill="{CHEEK}" opacity="{ck}"/>'
             f'<ellipse cx="115" cy="108" rx="8.5" ry="5.2" fill="{CHEEK}" opacity="{ck}"/>')
    ms = f'fill="none" stroke="{EYE}" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"'
    if mood in ('basic', 'hello'):
        g.append(_eye(59, 97) + _eye(101, 97) + f'<path d="M74 104.5q6 5.5 12 0" {ms}/>')
    elif mood == 'point':
        g.append(_eye(61, 97, 2.2) + _eye(103, 97, 2.2) + f'<path d="M77 104.5q6 5.5 12 0" {ms}/>')
    elif mood == 'done':
        g.append(_arc_eye(59, 99) + _arc_eye(101, 99)
                 + f'<path d="M72 103h16q0 10 -8 10q-8 0 -8 -10z" fill="#3A2540" stroke="{EYE}" stroke-width="2.4" stroke-linejoin="round"/>'
                 + '<path d="M75.5 110.5q4.5 -3.2 9 0q-1.6 2.5 -4.5 2.5q-2.9 0 -4.5 -2.5z" fill="#FF8D97"/>')
    elif mood == 'stretch':
        g.append(_arc_eye(59, 96, up=False, h=6) + _arc_eye(101, 96, up=False, h=6)
                 + f'<ellipse cx="80" cy="107" rx="5" ry="6" fill="#3A2540" stroke="{EYE}" stroke-width="2.2"/>')
    elif mood == 'sleepy':
        g.append(_arc_eye(59, 97, up=False, h=5.5) + _arc_eye(101, 97, up=False, h=5.5)
                 + f'<ellipse cx="80" cy="107" rx="3.2" ry="3.8" fill="#3A2540"/>')
    elif mood == 'off':
        g.append(_arc_eye(59, 99, h=6) + _arc_eye(101, 99, h=6) + f'<path d="M74 104.5q6 5.5 12 0" {ms}/>')
    elif mood == 'worry':
        g.append(f'<ellipse cx="59" cy="99" rx="5" ry="5.8" fill="{EYE}"/><circle cx="57.6" cy="97" r="1.9" fill="#fff"/>'
                 f'<ellipse cx="101" cy="99" rx="5" ry="5.8" fill="{EYE}"/><circle cx="99.6" cy="97" r="1.9" fill="#fff"/>'
                 f'<path d="M51 89.5l12 -4M109 89.5l-12 -4" {ms}/>'
                 f'<path d="M73 108q3.5 -4 7 0t7 0" {ms}/>')
    elif mood == 'payday':
        g.append(_eye(59, 97, big=True) + _eye(101, 97, big=True)
                 + '<circle cx="56" cy="101.5" r="1.3" fill="#fff" opacity=".9"/><circle cx="98" cy="101.5" r="1.3" fill="#fff" opacity=".9"/>'
                 + f'<path d="M73 103.5h14q0 8.5 -7 8.5q-7 0 -7 -8.5z" fill="#3A2540" stroke="{EYE}" stroke-width="2.4" stroke-linejoin="round"/>')
    elif mood == 'nightshift':
        g.append(_eye(59, 93) + _eye(101, 93) + f'<path d="M75 100.5q5 4.5 10 0" {ms}/>')
    g.append('</g>')
    o += g
    # 바깥 장식
    if mood == 'done':
        o.append(_star(22, 40, 8) + _star(140, 26, 6) + _star(146, 54, 3.6, '#F28C6A') + _star(12, 64, 3.4, '#4DBE93')
                 + '<circle cx="34" cy="20" r="2.6" fill="#5B6AD0"/><circle cx="128" cy="44" r="2.2" fill="#FF9FA6"/>')
    elif mood == 'stretch':
        o.append(''.join(f'<path d="{d}" stroke="#F6C24F" stroke-width="3" stroke-linecap="round"/>'
                         for d in ('M13 40l-7 -5', 'M9 54h-8', 'M147 40l7 -5', 'M151 54h8')))
    elif mood == 'sleepy':
        o.append(_zz())
    elif mood == 'off':
        o.append('<path d="M138 44v-11l9 -2.4v10.6" fill="none" stroke="#3FA57F" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>'
                 '<ellipse cx="135.3" cy="44.4" rx="3.3" ry="2.6" fill="#3FA57F"/><ellipse cx="144.3" cy="41.8" rx="3.3" ry="2.6" fill="#3FA57F"/>')
    elif mood == 'worry':
        o.append(f'<path d="M132 62q6 9 0 13.5q-6 -4.5 0 -13.5z" fill="#9DD1FF" stroke="{INK}" stroke-width="2"/>')
    elif mood == 'payday':
        o.append(_coin(143, 46) + _star(126, 28, 4) + _star(154, 26, 3, '#F6C24F', .8))
    elif mood == 'nightshift':
        o.append(_cup(80, 126)
                 + f'<ellipse cx="64" cy="125" rx="6.5" ry="8" fill="{CREAM}" stroke="{INK}" stroke-width="2.8"/>'
                 + f'<ellipse cx="96" cy="125" rx="6.5" ry="8" fill="{CREAM}" stroke="{INK}" stroke-width="2.8"/>'
                 + _star(140, 40, 5, '#FFE9A8') + _star(150, 58, 3, '#FFE9A8', .8))
    elif mood == 'hello':
        o.append(''.join(f'<path d="{d}" fill="none" stroke="#F6C24F" stroke-width="2.6" stroke-linecap="round"/>'
                         for d in ('M148 50q5 4 4 10', 'M153 44q8 6 7 16')))
    return ''.join(o)


def old_kani(mood='basic', dark=False):
    """지금 앱의 칸이(docs/brand/kani/make_kani.py와 같은 모양, viewBox 130 → 160 안에 맞춤)."""
    u = next(_uid)
    out = '#C9D1F5' if dark else '#15192B'
    ink = '#15192B'
    shift = {'sleepy': '#34418F', 'off': '#43B58A'}.get(mood, '#E9B95A')
    face = {
        'basic': '<circle cx="51" cy="76" r="5"/><circle cx="79" cy="76" r="5"/><path d="M57 86q8 7 16 0" stroke-width="3.5" fill="none" stroke="#15192B" stroke-linecap="round"/>',
        'sleepy': '<path d="M45 77q6 5 12 0M73 77q6 5 12 0" stroke-width="3.5" fill="none" stroke="#15192B" stroke-linecap="round"/><ellipse cx="65" cy="90" rx="3.5" ry="4"/>',
        'done': '<circle cx="51" cy="76" r="5"/><path d="M73 77q6-5 12 0M56 85q9 9 18 0" stroke-width="3.5" fill="none" stroke="#15192B" stroke-linecap="round"/>',
        'point': '<circle cx="54" cy="77" r="5"/><circle cx="82" cy="77" r="5"/><path d="M60 87q7 5 14 0" stroke-width="3.5" fill="none" stroke="#15192B" stroke-linecap="round"/>',
        'stretch': '<path d="M45 78q6-6 12 0M73 78q6-6 12 0" stroke-width="3.5" fill="none" stroke="#15192B" stroke-linecap="round"/><path d="M57 85q8 10 16 0z" stroke="#15192B" stroke-width="2"/>',
    }[mood]
    arms = {'basic': 'M20 80q-9 6-7 15M110 80q9 6 7 15', 'sleepy': 'M20 80q-9 6-7 15M110 80q9 6 7 15',
            'done': 'M20 80q-9 6-7 15M110 70q12-10 9-24', 'point': 'M20 80q-9 6-7 15M110 82q10 4 14 14',
            'stretch': 'M20 70q-12-10-9-24M110 70q12-10 9-24'}[mood]
    icon = ('<path d="M90 31a7 7 0 1 0 0 12a5.5 5.5 0 1 1 0-12z" fill="#FFE9A8"/>' if mood == 'sleepy'
            else '<circle cx="88" cy="37" r="5.5" fill="#FFF3C4"/>')
    extra = ''
    if mood == 'sleepy':
        extra = ('<g fill="none" stroke="#7C8BD6" stroke-linecap="round" stroke-linejoin="round">'
                 '<path d="M104 11h9l-9 11h9" stroke-width="2.6"/><path d="M115 3h6l-6 7h6" stroke-width="2"/></g>')
    if mood == 'stretch':
        extra = '<g stroke="#E9B95A" stroke-width="3" stroke-linecap="round"><path d="M8 30l-6-4"/><path d="M6 42h-6"/><path d="M122 30l6-4"/><path d="M124 42h6"/></g>'
    if mood == 'done':
        extra = ('<g fill="#E9B95A"><path d="M12 22l2.5 6l6 2.5l-6 2.5l-2.5 6l-2.5-6l-6-2.5l6-2.5z"/>'
                 '<path d="M112 8l1.8 4.2l4.2 1.8l-4.2 1.8l-1.8 4.2l-1.8-4.2l-4.2-1.8l4.2-1.8z"/></g>')
    return (f'<g transform="translate(15 15)"><defs><clipPath id="ob{u}"><rect x="21" y="24" width="88" height="88" rx="24"/></clipPath></defs>'
            f'<path d="{arms}" fill="none" stroke="{out}" stroke-width="4" stroke-linecap="round"/>'
            f'<ellipse cx="49" cy="116" rx="11" ry="5" fill="{out}"/><ellipse cx="81" cy="116" rx="11" ry="5" fill="{out}"/>'
            f'<rect x="21" y="24" width="88" height="88" rx="24" fill="#F7F1E6"/>'
            f'<g clip-path="url(#ob{u})"><rect x="21" y="24" width="88" height="27" fill="{shift}"/></g>{icon}'
            f'<rect x="21" y="24" width="88" height="88" rx="24" fill="none" stroke="{out}" stroke-width="3.5"/>'
            f'<rect x="44" y="14" width="9" height="20" rx="4.5" fill="{out}"/><rect x="77" y="14" width="9" height="20" rx="4.5" fill="{out}"/>'
            f'<ellipse cx="38" cy="88" rx="6.5" ry="3.8" fill="#FF9EA0" opacity=".7"/><ellipse cx="92" cy="88" rx="6.5" ry="3.8" fill="#FF9EA0" opacity=".7"/>'
            f'<g fill="{ink}">{face}</g>{extra}</g>')


def svg(inner, size=160, vb='0 0 160 160', title=''):
    t = f'<title>{title}</title>' if title else ''
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}" width="{size}" height="{size}" role="img">'
            f'{t}{inner}</svg>')


# ───────────────────────── 시안 B '해달이' (해달 = 해 + 달) ─────────────────────────
FUR, FUR_D, HEAD, MUZ, BELLY, OINK = '#9A7964', '#7B5F52', '#EDDCC6', '#FFF9EE', '#EBD8C0', '#3D3046'


def _dial_stone(cx, cy, r=11, u=0):
    """밤낮 로고(반은 낮, 반은 밤인 동그라미)를 닮은 조약돌."""
    return (f'<defs><clipPath id="ds{u}"><rect x="{cx - r - 2}" y="{cy}" width="{2 * r + 4}" height="{r + 2}"/></clipPath></defs>'
            f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#FFF6DC" stroke="{OINK}" stroke-width="2.6"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{r - 1.3}" fill="#3C4AA6" clip-path="url(#ds{u})"/>'
            f'<circle cx="{cx}" cy="{cy + r * 0.45}" r="2" fill="#F6C24F"/>'
            f'<circle cx="{cx}" cy="{cy - r * 0.42}" r="2.6" fill="#F6C24F"/>')


def otter(mood='basic', shift='day'):
    """시안 B '해달이': 해달(해 + 달). 좋아하는 조약돌(밤낮 로고 모양)을 품고 다니고, 목도리 색이 오늘 근무 색이에요."""
    u = next(_uid)
    band = BAND[shift]
    st = f'stroke="{OINK}" stroke-width="3" stroke-linejoin="round"'
    ms = f'fill="none" stroke="{OINK}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"'
    o = ['<ellipse cx="80" cy="150" rx="40" ry="4.5" fill="#12162C" opacity=".13"/>']
    # 넓적한 꼬리
    o.append(f'<path d="M104 134C124 139 143 131 146 114C147 104 134 102 132 111C130 120 120 124 108 122Z" fill="{FUR_D}" {st}/>')
    o.append(f'<ellipse cx="64" cy="144" rx="11.5" ry="6.5" fill="{FUR_D}" {st}/><ellipse cx="96" cy="144" rx="11.5" ry="6.5" fill="{FUR_D}" {st}/>')
    o.append(f'<path d="M80 86C102 86 115 100 115 118C115 136 100 145 80 145C60 145 45 136 45 118C45 100 58 86 80 86Z" fill="{FUR}" {st}/>')
    o.append(f'<ellipse cx="80" cy="122" rx="21" ry="19" fill="{BELLY}"/>')
    if mood == 'done':
        o.append(f'<ellipse cx="37" cy="93" rx="8" ry="13" transform="rotate(-38 37 93)" fill="{FUR}" {st}/>'
                 f'<ellipse cx="123" cy="93" rx="8" ry="13" transform="rotate(38 123 93)" fill="{FUR}" {st}/>')
    # 작고 낮은 귀
    for x in (37, 123):
        o.append(f'<circle cx="{x}" cy="46" r="7.5" fill="{FUR}" {st}/>')
    # 머리(밝은 털)
    o.append(f'<path d="M80 27C109 27 129 43 129 65C129 88 109 102 80 102C51 102 31 88 31 65C31 43 51 27 80 27Z" fill="{HEAD}" {st}/>')
    o.append(f'<path d="M52 41Q64 33 80 32" fill="none" stroke="#fff" stroke-width="3.2" stroke-linecap="round" opacity=".6"/>')
    # 목도리 = 오늘 근무 색
    o.append(f'<path d="M50 94Q80 109 110 94L113 102Q80 119 47 102Z" fill="{band}" {st}/>'
             f'<path d="M97 105l11 -4l4 16q-6 4 -11 2z" fill="{band}" {st}/>')
    o.append(f'<ellipse cx="46" cy="78" rx="8" ry="5" fill="{CHEEK}" opacity=".75"/><ellipse cx="114" cy="78" rx="8" ry="5" fill="{CHEEK}" opacity=".75"/>')
    # 통통한 주둥이, 큰 코, 수염
    o.append(f'<ellipse cx="70.5" cy="85" rx="11.5" ry="8.5" fill="{MUZ}"/><ellipse cx="89.5" cy="85" rx="11.5" ry="8.5" fill="{MUZ}"/>'
             f'<path d="M72 74.5q8 -4 16 0q-1.5 7.5 -8 9q-6.5 -1.5 -8 -9z" fill="{OINK}"/>'
             f'<path d="M76.5 76.3q3.5 -1.6 7 0" fill="none" stroke="#8C7E96" stroke-width="1.4" stroke-linecap="round"/>'
             f'<path d="M73 87.5q3.5 3.6 7 0q3.5 3.6 7 0" {ms}/>'
             + ''.join(f'<path d="{d}" stroke="#B9A08A" stroke-width="1.7" stroke-linecap="round"/>'
                       for d in ('M58 84l-10 -2.5', 'M58 88.5l-10 2', 'M102 84l10 -2.5', 'M102 88.5l10 2')))
    def eye(cx, cy, **k):
        return _eye(cx, cy, **k).replace(EYE, OINK)
    def arc(cx, cy, **k):
        return _arc_eye(cx, cy, **k).replace(EYE, OINK)
    if mood in ('basic', 'surprise'):
        o.append(eye(57, 67) + eye(103, 67))
    elif mood == 'done':
        o.append(arc(57, 69) + arc(103, 69))
    elif mood == 'sleepy':
        o.append(arc(57, 68, up=False, h=5.5) + arc(103, 68, up=False, h=5.5))
    if mood == 'basic':
        o.append(_dial_stone(80, 122, 11, u)
                 + f'<ellipse cx="66" cy="123" rx="6.5" ry="8.5" fill="{FUR}" {st}/><ellipse cx="94" cy="123" rx="6.5" ry="8.5" fill="{FUR}" {st}/>')
    elif mood == 'sleepy':
        o.append(f'<circle cx="80" cy="123" r="12.5" fill="#FFDD77" {st}/>'
                 f'<circle cx="84.5" cy="119" r="2.6" fill="#F3C24E"/><circle cx="76" cy="127.5" r="1.8" fill="#F3C24E"/>'
                 f'<ellipse cx="65.5" cy="125" rx="6.5" ry="8.5" fill="{FUR}" {st}/><ellipse cx="94.5" cy="125" rx="6.5" ry="8.5" fill="{FUR}" {st}/>'
                 + _zz(130, 20))
        o.append(f'<path d="M38 47C42 22 76 8 112 20C127 25 135 38 137 52C129 45 121 45 117 49Q84 25 38 47Z" fill="#3C4AA6" {st}/>'
                 f'<circle cx="138" cy="54" r="6" fill="#FFE9A8" {st}/>' + _star(84, 25, 4, '#FFE9A8'))
    elif mood == 'done':
        o.append(_dial_stone(80, 13, 9, u) + _star(20, 34, 7) + _star(144, 30, 6) + _star(152, 62, 3.4, '#F28C6A') + _star(10, 62, 3.2, '#4DBE93'))
    elif mood == 'surprise':
        o.append(f'<ellipse cx="47" cy="85" rx="8" ry="9.5" fill="{FUR}" {st}/><ellipse cx="113" cy="85" rx="8" ry="9.5" fill="{FUR}" {st}/>'
                 f'<ellipse cx="80" cy="91" rx="3" ry="3.4" fill="#3A2540"/>')
    return ''.join(o)


# ───────────────────────── 시안 C '해니와 달이' (번갈아 깨어 있는 두 친구) ─────────────────────────
def sun_pal(cx, cy, mood='basic', r=34):
    u = next(_uid)
    st = f'stroke="{INK}" stroke-width="3" stroke-linejoin="round"'
    ms = f'fill="none" stroke="{EYE}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"'
    o = [f'<defs><radialGradient id="sg{u}" cx=".4" cy=".35" r=".8"><stop offset="0" stop-color="#FFE89A"/><stop offset="1" stop-color="#F7BC45"/></radialGradient></defs>']
    o.append(f'<ellipse cx="{cx}" cy="{cy + r + 9}" rx="{r * .8}" ry="4" fill="#12162C" opacity=".13"/>')
    for a in (-56, -28, 0, 28, 56):
        o.append(f'<rect x="{cx - 5}" y="{cy - r - 12}" width="10" height="18" rx="5" transform="rotate({a} {cx} {cy})" fill="#F6A63A" {st}/>')
    arm_up = mood == 'hello'
    o.append(f'<ellipse cx="{cx - r - 1}" cy="{cy + 8}" rx="6" ry="9" transform="rotate(24 {cx - r - 1} {cy + 8})" fill="#F9C753" {st}/>')
    if arm_up:
        o.append(f'<ellipse cx="{cx + r + 2}" cy="{cy - 10}" rx="6" ry="10" transform="rotate(30 {cx + r + 2} {cy - 10})" fill="#F9C753" {st}/>')
    else:
        o.append(f'<ellipse cx="{cx + r + 1}" cy="{cy + 8}" rx="6" ry="9" transform="rotate(-24 {cx + r + 1} {cy + 8})" fill="#F9C753" {st}/>')
    o.append(f'<ellipse cx="{cx - 12}" cy="{cy + r + 3}" rx="9" ry="5.5" fill="#F3AE3C" {st}/><ellipse cx="{cx + 12}" cy="{cy + r + 3}" rx="9" ry="5.5" fill="#F3AE3C" {st}/>')
    o.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#sg{u})" {st}/>')
    o.append(f'<path d="M{cx - 22} {cy - 16}q8 -11 20 -13" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".55"/>')
    o.append(f'<ellipse cx="{cx - 21}" cy="{cy + 12}" rx="7" ry="4.4" fill="#FF8F88" opacity=".7"/><ellipse cx="{cx + 21}" cy="{cy + 12}" rx="7" ry="4.4" fill="#FF8F88" opacity=".7"/>')
    if mood == 'sleepy':
        o.append(_arc_eye(cx - 13, cy + 4, up=False, w=6, h=5) + _arc_eye(cx + 13, cy + 4, up=False, w=6, h=5)
                 + f'<ellipse cx="{cx}" cy="{cy + 12}" rx="2.6" ry="3.2" fill="#3A2540"/>' + _zz(cx + 24, cy - r - 8, '#F6A63A'))
    else:
        o.append(_eye(cx - 13, cy + 3) + _eye(cx + 13, cy + 3) + f'<path d="M{cx - 5} {cy + 10}q5 5 10 0" {ms}/>')
    return ''.join(o)


def moon_pal(cx, cy, mood='basic', r=34):
    u = next(_uid)
    st = f'stroke="{INK}" stroke-width="3" stroke-linejoin="round"'
    ms = f'fill="none" stroke="{EYE}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"'
    o = [f'<defs><radialGradient id="mg{u}" cx=".4" cy=".35" r=".85"><stop offset="0" stop-color="#F6F7FF"/><stop offset="1" stop-color="#C5CEF7"/></radialGradient>'
         f'<clipPath id="mc{u}"><circle cx="{cx}" cy="{cy}" r="{r}"/></clipPath></defs>']
    o.append(f'<ellipse cx="{cx}" cy="{cy + r + 9}" rx="{r * .8}" ry="4" fill="#12162C" opacity=".13"/>')
    o.append(f'<ellipse cx="{cx - r - 1}" cy="{cy + 8}" rx="6" ry="9" transform="rotate(24 {cx - r - 1} {cy + 8})" fill="#DCE2FF" {st}/>')
    if mood == 'lamp':
        o.append(f'<ellipse cx="{cx + r + 2}" cy="{cy - 6}" rx="6" ry="10" transform="rotate(40 {cx + r + 2} {cy - 6})" fill="#DCE2FF" {st}/>')
    else:
        o.append(f'<ellipse cx="{cx + r + 1}" cy="{cy + 8}" rx="6" ry="9" transform="rotate(-24 {cx + r + 1} {cy + 8})" fill="#DCE2FF" {st}/>')
    o.append(f'<ellipse cx="{cx - 12}" cy="{cy + r + 3}" rx="9" ry="5.5" fill="#B4BFF0" {st}/><ellipse cx="{cx + 12}" cy="{cy + r + 3}" rx="9" ry="5.5" fill="#B4BFF0" {st}/>')
    o.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#mg{u})"/>')
    o.append(f'<g clip-path="url(#mc{u})"><circle cx="{cx + 20}" cy="{cy - 8}" r="{r}" fill="#9FACEA" opacity=".0"/>'
             f'<circle cx="{cx + 19}" cy="{cy + 20}" r="5" fill="#AEB9EE" opacity=".55"/><circle cx="{cx + 27}" cy="{cy + 6}" r="2.6" fill="#AEB9EE" opacity=".55"/>'
             f'<circle cx="{cx - 24}" cy="{cy + 22}" r="3" fill="#AEB9EE" opacity=".5"/></g>')
    o.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" {st}/>')
    # 잠옷 모자
    o.append(f'<path d="M{cx - 31} {cy - 15}C{cx - 30} {cy - 46} {cx + 2} {cy - 58} {cx + 38} {cy - 36}C{cx + 28} {cy - 37} {cx + 25} {cy - 28} {cx + 30} {cy - 17}Q{cx} {cy - 34} {cx - 31} {cy - 15}Z" fill="#3C4AA6" {st}/>'
             f'<path d="M{cx - 30} {cy - 14}Q{cx} {cy - 32} {cx + 29} {cy - 16}" fill="none" stroke="{INK}" stroke-width="10" stroke-linecap="round"/>'
             f'<path d="M{cx - 30} {cy - 14}Q{cx} {cy - 32} {cx + 29} {cy - 16}" fill="none" stroke="#FFF8EA" stroke-width="5.5" stroke-linecap="round"/>'
             f'<circle cx="{cx + 40}" cy="{cy - 35}" r="5.5" fill="#F6C24F" {st}/>' + _star(cx - 2, cy - 38, 3.6, '#FFE9A8'))
    o.append(f'<ellipse cx="{cx - 21}" cy="{cy + 13}" rx="7" ry="4.4" fill="{CHEEK}" opacity=".7"/><ellipse cx="{cx + 21}" cy="{cy + 13}" rx="7" ry="4.4" fill="{CHEEK}" opacity=".7"/>')
    if mood == 'sleepy':
        o.append(_arc_eye(cx - 13, cy + 5, up=False, w=6, h=5) + _arc_eye(cx + 13, cy + 5, up=False, w=6, h=5)
                 + f'<ellipse cx="{cx}" cy="{cy + 13}" rx="2.6" ry="3.2" fill="#3A2540"/>' + _zz(cx + 30, cy - r - 22))
    else:
        o.append(_eye(cx - 13, cy + 4) + _eye(cx + 13, cy + 4) + f'<path d="M{cx - 5} {cy + 11}q5 5 10 0" {ms}/>')
    if mood == 'lamp':
        lx, ly = cx + r + 14, cy - 24
        o.append(f'<circle cx="{lx}" cy="{ly}" r="13" fill="#FFE9A8" opacity=".35"/>' + _star(lx, ly, 8.5, '#FFD86B')
                 + f'<path d="M{lx} {ly + 9}v6" stroke="{INK}" stroke-width="2.4" stroke-linecap="round"/>')
    return ''.join(o)


def duo(scene='both'):
    """viewBox 0 0 240 160."""
    if scene == 'both':
        return sun_pal(74, 84, 'hello') + moon_pal(166, 86, 'basic')
    if scene == 'day':      # 주간: 해니가 깨어 있고 달이는 자요
        return moon_pal(170, 92, 'sleepy', r=30) + sun_pal(78, 82, 'hello', r=36)
    if scene == 'night':    # 야간: 달이가 등불을 들고 해니는 자요
        return sun_pal(70, 94, 'sleepy', r=30) + moon_pal(158, 84, 'lamp', r=36)
    raise ValueError(scene)
