import json, subprocess
from PIL import Image
cfg = json.load(open('panels.json'))
pick = {'01-달력':('store_1b_calendar',0),'02-첫설정':('store_2_setup',0),'03-사진읽기':('819_u_setup_which_line',680),
        '04-알람':('store_4b_shift_alarms',0),'05-잠':('store_5_sleep',0),'06-급여':('store_6c_pay_result',0),
        '07-팀근무표':('store_7_team',0),'08-메모':('store_8_memos',0)}
for p in cfg['panels']:
    f, off = pick[p['name']]
    im = Image.open(f'screens/{f}.png').convert('RGB')
    p['image'] = f'screens/{f}.png'
    p['offset'] = round(off * 760 / im.width)
    r, g, b = im.getpixel((12, off + 4))
    p['bar'] = '#%02x%02x%02x' % (r, g, b)
    p['barInk'] = '#1b1b1f' if (0.299*r + 0.587*g + 0.114*b) > 140 else '#f2f2f2'
json.dump(cfg, open('panels-final.json', 'w'), ensure_ascii=False, indent=1)
subprocess.run(['node', 'compose.js', 'panels-final.json'], check=True)
