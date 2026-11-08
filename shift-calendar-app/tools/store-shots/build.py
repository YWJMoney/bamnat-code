"""All 8 store panels, then the RGB (no alpha) PNGs Play wants: out/<name>.png → out/play/<name>.png."""
import json, os, subprocess
from PIL import Image
subprocess.run(['node', 'compose.js', 'panels.json'], check=True)
os.makedirs('out/play', exist_ok=True)
for p in json.load(open('panels.json'))['panels']:
    Image.open(f"out/{p['name']}.png").convert('RGB').save(f"out/play/{p['name']}.png", optimize=True)
subprocess.run(['node', 'feature.js', 'screens/store_1b_calendar.png', 'screens/store_7_team.png'], check=True)
Image.open('out/feature.png').convert('RGB').save('out/play/feature.png')
