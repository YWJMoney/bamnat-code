"""밤낮 배경 생성기 2판: numpy + PIL 로만 그려요(외부 그림 없음). 같은 설정이면 같은 그림이 나와요."""
import numpy as np, math, io
from PIL import Image, ImageFilter, ImageDraw

W, H = 970, 2048


def hexc(h):
    h = h.lstrip('#'); return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float) / 255


def rgb255(c, a=None):
    t = tuple(int(max(0, min(1, v)) * 255) for v in c)
    return t + ((a,) if a is not None else ())


def to_img(a): return Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8))


def from_img(im): return np.asarray(im.convert('RGB')).astype(float) / 255


def blur(a, r): return from_img(to_img(a).filter(ImageFilter.GaussianBlur(r)))


def blur1(m, r):
    im = Image.fromarray((np.clip(m, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r))
    return np.asarray(im).astype(float) / 255


def grain(a, rng, amt=0.012):
    return np.clip(a + rng.normal(0, amt, (a.shape[0], a.shape[1], 1)), 0, 1)


def vgrad(stops, gamma=1.0):
    """stops: list of hex, evenly spaced top→bottom."""
    t = np.linspace(0, 1, H) ** gamma
    cs = [hexc(s) for s in stops]; n = len(cs) - 1
    idx = np.minimum((t * n).astype(int), n - 1); fr = (t * n - idx)[:, None]
    col = np.array(cs)[idx] * (1 - fr) + np.array(cs)[idx + 1] * fr
    return np.broadcast_to(col[:, None, :], (H, W, 3)).copy()


def smooth_noise(h, w, rng, cells, octaves=4, persist=0.5):
    out = np.zeros((h, w)); amp = 1; c = cells; tot = 0
    for _ in range(octaves):
        g = rng.uniform(0, 1, (c + 1, max(2, int(c * w / h) + 2)))
        im = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        out += amp * (np.asarray(im) / 255 - 0.5); tot += amp; amp *= persist; c *= 2
    return out / tot


def fbm1(n, rng, f=4, octaves=7):
    x = np.zeros(n); amp = 1
    for _ in range(octaves):
        pts = rng.uniform(-1, 1, f + 2); xs = np.linspace(0, f, n)
        i = xs.astype(int); fr = xs - i; fr = fr * fr * (3 - 2 * fr)
        x += amp * (pts[i] * (1 - fr) + pts[i + 1] * fr); amp *= 0.5; f *= 2
    return x


def calm_center(a, base, strength=0.35):
    """가운데(달력·카드 자리)를 바탕색 쪽으로 살짝 눌러 잔무늬를 줄여요."""
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt(((xx - W / 2) / (W * 0.55)) ** 2 + ((yy - H * 0.52) / (H * 0.36)) ** 2)
    m = np.clip(1 - d, 0, 1) ** 1.5 * strength
    return a * (1 - m[:, :, None]) + base * m[:, :, None]


YY, XX = np.mgrid[0:H, 0:W].astype(float)


# ---------------- 1. 알갱이 그라데이션 ----------------
def grad(rng, cols, bg, n=7, r=560, grain_amt=0.014):
    im = Image.new('RGB', (W, H), rgb255(hexc(bg))); d = ImageDraw.Draw(im)
    for i in range(n):
        c = cols[i % len(cols)]
        x, y = rng.uniform(-150, W + 150), rng.uniform(-150, H + 150); rr = rng.uniform(r * 0.6, r * 1.25)
        d.ellipse([x - rr, y - rr * 1.25, x + rr, y + rr * 1.25], fill=rgb255(hexc(c)))
    small = im.resize((W // 4, H // 4), Image.BILINEAR).filter(ImageFilter.GaussianBlur(70)).filter(ImageFilter.GaussianBlur(40))
    a = from_img(small.resize((W, H), Image.BICUBIC))
    return grain(a, rng, grain_amt)


# ---------------- 2. 오로라·밤하늘 ----------------
def stars(a, rng, density=0.0007, top=1.0, bright=1.0):
    n = int(W * H * density)
    xs = rng.integers(0, W, n); ys = (rng.random(n) ** 1.6 * H * top).astype(int)
    br = rng.uniform(0.35, 1.0, n) * bright
    layer = np.zeros((H, W))
    np.maximum.at(layer, (ys, xs), br)
    big = rng.random(n) > 0.93
    glow = np.zeros((H, W)); glow[ys[big], xs[big]] = br[big] * 6
    layer = np.maximum(layer, blur1(glow / 6, 2.2) * 2.2)
    return np.clip(a + layer[:, :, None] * 0.95, 0, 1)


def aurora(rng, sky, cols, horizon='#050A12', hills=True):
    a = vgrad(sky, 0.9)
    a = stars(a, rng, 0.0009, 0.9, 1.2)
    x = np.linspace(0, 1, W)
    for k, c in enumerate(cols):
        base = 0.30 + 0.13 * k + rng.uniform(-0.03, 0.03)
        ph = rng.uniform(0, 6.28)
        curve = base + 0.07 * np.sin(x * 5.1 + ph) + 0.035 * np.sin(x * 13 + ph * 1.7) + 0.02 * fbm1(W, rng, 3, 4)
        cy = curve * H
        dy = (YY - cy[None, :]) / H
        # 밑은 또렷하고 위로 길게 번지는 커튼
        body = np.where(dy > 0, np.exp(-(dy / 0.010) ** 2), np.exp(-(-dy / 0.11) ** 1.2))
        rn = fbm1(W, rng, 40, 4); rn = (rn - rn.min()) / np.ptp(rn)
        rays = 0.35 + 0.65 * rn[None, :] ** 1.5
        fade = np.clip(1 - np.abs(x - rng.uniform(0.3, 0.7)) * rng.uniform(0.6, 1.2), 0.15, 1)[None, :]
        rays = rays * fade
        flick = 0.6 + 0.4 * smooth_noise(H, W, rng, 3, 3)[:, :] * 2
        inten = body * rays * np.clip(flick, 0.3, 1.1) * 0.8
        col = hexc(c)
        top_col = hexc(cols[(k + 1) % len(cols)])
        mix = np.clip(-dy / 0.18, 0, 1)[:, :, None]
        cc = col * (1 - mix) + top_col * mix
        a = 1 - (1 - a) * (1 - np.clip(cc * inten[:, :, None], 0, 1))
    a = blur(a, 1.5)
    if hills:
        ridge = H * 0.86 - H * 0.05 * (fbm1(W, rng, 3, 6) + 0.3)
        m = (YY > ridge[None, :]).astype(float); m = blur1(m, 1)
        a = a * (1 - m[:, :, None]) + hexc(horizon) * m[:, :, None]
        # 물에 비친 빛
    return grain(a, rng, 0.01)


def nightsky(rng, sky, glow, moon=None):
    a = vgrad(sky, 1.0)
    if glow:
        g = np.exp(-(((YY - H) / (H * 0.45)) ** 2)) * 0.6
        a += hexc(glow) * g[:, :, None]
    # 은하수 띠
    n = smooth_noise(H, W, rng, 4, 6, 0.55)
    band = np.exp(-(((XX - W * 0.5) * 0.9 + (YY - H * 0.42) * 0.42) / (W * 0.22)) ** 2)
    neb = np.clip(n * 2.2 + 0.25, 0, 1) * band
    a += (hexc('#B9A8FF') * 0.35)[None, None, :] * neb[:, :, None]
    a = stars(a, rng, 0.0012, 1.0)
    a = stars(a, rng, 0.0025 * 0.5, 1.0, 0.5) if True else a
    if moon:
        mx, my, mr = moon
        d = np.sqrt((XX - mx) ** 2 + (YY - my) ** 2)
        a += hexc('#FFF6D8') * (np.exp(-(d / (mr * 3.2)) ** 2) * 0.35)[:, :, None]
        disc = np.clip((mr - d) / 2, 0, 1)
        shade = np.clip((np.sqrt((XX - mx - mr * 0.45) ** 2 + (YY - my + mr * 0.2) ** 2) - mr * 0.92) / 3, 0, 1)
        a = a * (1 - (disc * shade)[:, :, None]) + hexc('#FFF3C9') * (disc * shade)[:, :, None]
    return grain(np.clip(a, 0, 1), rng, 0.01)


# ---------------- 3. 산·노을 풍경 ----------------
def landscape(rng, sky, layers, sun=None, mist='#FFFFFF', mist_a=0.25, base=0.48, span=0.48, rough=7, stars_on=False):
    a = vgrad(sky, 1.0)
    if stars_on: a = stars(a, rng, 0.0008, 0.5)
    if sun:
        sx, sy, sc, sr = sun
        d = np.sqrt((XX - sx) ** 2 + (YY - sy) ** 2)
        a = a + hexc(sc) * (np.exp(-(d / (sr * 4.0)) ** 2) * 0.45)[:, :, None]
        disc = np.clip((sr - d) / 1.5, 0, 1)[:, :, None]
        a = a * (1 - disc) + np.clip(hexc(sc) * 1.05 + 0.03, 0, 1) * disc
    n = len(layers)
    for i, c in enumerate(layers):
        t = i / max(n - 1, 1)
        b = H * (base + span * t); amp = H * (0.11 - 0.06 * t)
        ridge = b - amp * (fbm1(W, rng, 2 + i, rough) * 0.8 + 0.5)
        m = blur1((YY > ridge[None, :]).astype(float), 1.0)
        col = hexc(c)
        depth = np.clip((YY - ridge[None, :]) / (H * 0.25), 0, 1)[:, :, None]
        layer_col = col * (1 - 0.12 * depth)
        a = a * (1 - m[:, :, None]) + layer_col * m[:, :, None]
        if i < n - 1:  # 산 사이 안개
            mm = np.exp(-((YY - (b + H * 0.01)) / (H * 0.035)) ** 2) * mist_a * (1 - t) * (YY > ridge[None, :])
            mm = blur1(mm, 6)
            a = a * (1 - mm[:, :, None]) + hexc(mist) * mm[:, :, None]
    return grain(np.clip(a, 0, 1), rng, 0.012)


# ---------------- 4. 바다·물결 ----------------
def waves(rng, sky, cols, top=0.38, foam='#FFFFFF', amp=36):
    a = vgrad(sky, 1.0)
    x = np.arange(W)
    n = len(cols)
    for i, c in enumerate(cols):
        b = H * (top + (0.95 - top) * i / n); ph = rng.uniform(0, 6)
        y = b + amp * np.sin(x / W * 2 * np.pi * (1.1 + 0.25 * i) + ph) + amp * 0.4 * np.sin(x / W * 2 * np.pi * 3.3 + ph * 1.7)
        m = blur1((YY > y[None, :]).astype(float), 1.2)
        dd = (YY - y[None, :]) / (H * 0.12)
        shade = np.clip(dd, 0, 1)[:, :, None]
        col = hexc(c) * (1 - 0.18 * shade)
        crest = np.exp(-np.clip(YY - y[None, :], 0, None) / 10) * m * 0.35
        a = a * (1 - m[:, :, None]) + col * m[:, :, None] + hexc(foam) * crest[:, :, None]
    return grain(np.clip(a, 0, 1), rng, 0.012)


def sunset_sea(rng, sky, sea, sun_col, horizon=0.55):
    a = vgrad(sky, 1.0)
    hy = H * horizon; sx = W * 0.5; sy = hy - 120; sr = 110
    d = np.sqrt((XX - sx) ** 2 + (YY - sy) ** 2)
    a += hexc(sun_col) * (np.exp(-(d / 520) ** 2) * 0.5)[:, :, None]
    disc = np.clip((sr - d) / 2, 0, 1)
    a = a * (1 - disc[:, :, None]) + hexc('#FFF4DA') * disc[:, :, None]
    s = vgrad(sea, 1.0)
    m = (YY > hy).astype(float)
    a = a * (1 - m[:, :, None]) + s * m[:, :, None]
    # 해 반사 띠: 가로 물결 조각
    refl = np.zeros((H, W))
    for _ in range(900):
        yv = rng.uniform(hy + 4, H); spread = 40 + (yv - hy) * 0.35
        xv = sx + rng.normal(0, spread * 0.6); ln = rng.uniform(10, 40) * (1 + (yv - hy) / H * 3)
        th = 1.5 + (yv - hy) / H * 5
        y0 = int(yv); x0 = int(xv - ln); x1 = int(xv + ln)
        if 0 <= y0 < H - 4: refl[y0:y0 + int(th), max(0, x0):min(W, x1)] = rng.uniform(0.4, 1)
    refl = blur1(refl, 1.2) * m
    a = a + hexc(sun_col) * refl[:, :, None] * 0.8
    # 수평선 물결 결
    rip = (0.5 + 0.5 * np.sin(YY / 3 + smooth_noise(H, W, rng, 6, 2) * 40)) * m * 0.03
    a = a - rip[:, :, None]
    return grain(np.clip(a, 0, 1), rng, 0.012)


def underwater(rng, top, bottom, ray='#E6FFFF'):
    a = vgrad([top, bottom], 0.8)
    rays = np.zeros((H, W))
    for _ in range(14):
        x0 = rng.uniform(-W * 0.2, W * 1.2); ang = rng.uniform(-0.25, 0.25); wdt = rng.uniform(20, 80)
        xx = XX - (x0 + YY * ang)
        rays += np.exp(-(xx / wdt) ** 2) * rng.uniform(0.2, 0.6)
    rays *= np.exp(-YY / (H * 0.6))
    a = 1 - (1 - a) * (1 - (hexc(ray) * np.clip(rays, 0, 1)[:, :, None] * 0.45))
    # 물방울
    im = Image.new('L', (W, H), 0); d = ImageDraw.Draw(im)
    for _ in range(60):
        x, y, r = rng.uniform(0, W), rng.uniform(H * 0.2, H), rng.uniform(3, 14)
        d.ellipse([x - r, y - r, x + r, y + r], outline=int(rng.uniform(80, 200)), width=2)
    b = np.asarray(im.filter(ImageFilter.GaussianBlur(0.8))).astype(float) / 255
    a = a + b[:, :, None] * 0.35
    caust = blur1(np.abs(np.sin(smooth_noise(H, W, rng, 7, 2) * 18)) ** 14, 2) * np.exp(-YY / (H * 0.35)) * 0.10
    a = a + caust[:, :, None]
    return grain(np.clip(a, 0, 1), rng, 0.012)


# ---------------- 5. 빛망울 ----------------
def bokeh(rng, sky, cols, counts=(18, 26, 30), center_calm=0.5, hexagon=False):
    a = vgrad(sky, 1.0)
    layer = np.zeros((H, W, 3))
    for bl, cnt, rad in ((36, counts[0], (90, 170)), (10, counts[1], (40, 85)), (2.5, counts[2], (12, 32))):
        im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im)
        for _ in range(cnt):
            c = hexc(cols[rng.integers(len(cols))]) * rng.uniform(.25, .65)
            x, y, r = rng.uniform(0, W), rng.uniform(0, H), rng.uniform(*rad)
            # 가운데는 덜
            if abs(y - H * 0.5) < H * 0.2 and rng.random() < center_calm: y = rng.choice([rng.uniform(0, H * 0.3), rng.uniform(H * 0.7, H)])
            if hexagon:
                pts = [(x + r * math.cos(k * math.pi / 3), y + r * math.sin(k * math.pi / 3)) for k in range(6)]
                d.polygon(pts, fill=rgb255(c))
            else:
                d.ellipse([x - r, y - r, x + r, y + r], fill=rgb255(c))
                d.ellipse([x - r, y - r, x + r, y + r], outline=rgb255(c * 1.3), width=3)
        layer += from_img(im.filter(ImageFilter.GaussianBlur(bl)))
    lum = float(hexc(sky[0]).mean())
    if lum > 0.6:  # 밝은 바탕: 빛망울을 색으로 얹어요
        L = np.clip(layer * 1.6, 0, 1); al = np.clip(L.max(axis=2, keepdims=True) * 0.9, 0, 0.85)
        col = np.clip(L / np.maximum(L.max(axis=2, keepdims=True), 1e-3), 0, 1)
        a = a * (1 - al) + col * al
    else:
        a = 1 - (1 - a) * (1 - np.clip(layer, 0, 1))
    return grain(a, rng, 0.012)


# ---------------- 6. 종이·천 ----------------
def paper(rng, base, kind='plain', ink='#9FB4D8', margin='#F28B9B', fiber=0.035, warm=0.0):
    a = np.broadcast_to(hexc(base), (H, W, 3)).copy()
    n = smooth_noise(H, W, rng, 10, 6, 0.6) * 0.05
    a += n[:, :, None]
    f = rng.normal(0, 1, (H, W)); fi = blur1((f - f.min()) / np.ptp(f), 0.6)
    a += (fi - 0.5)[:, :, None] * fiber
    if kind == 'kraft':
        fib = np.zeros((H, W)); im = Image.new('L', (W, H), 0); d = ImageDraw.Draw(im)
        for _ in range(2500):
            x, y = rng.uniform(0, W), rng.uniform(0, H); ang = rng.uniform(0, math.pi); l = rng.uniform(6, 22)
            d.line([x, y, x + l * math.cos(ang), y + l * math.sin(ang)], fill=int(rng.uniform(40, 120)), width=1)
        a -= (np.asarray(im).astype(float) / 255 * 0.08)[:, :, None]
    if kind == 'linen':
        wv = (np.sin(XX * 1.3) * 0.5 + np.sin(YY * 1.3) * 0.5) * 0.02 + smooth_noise(H, W, rng, 60, 2) * 0.03
        a += wv[:, :, None]
    if kind in ('lined', 'grid', 'dots'):
        step = 72
        ink_c = hexc(ink)
        if kind == 'lined':
            m = ((YY.astype(int) - 260) % step < 2) & (YY > 240)
            m = m.astype(float) * 0.55
            mg = ((XX > 128) & (XX < 131)).astype(float) * 0.6
            a = a * (1 - m[:, :, None]) + ink_c * m[:, :, None]
            a = a * (1 - mg[:, :, None]) + hexc(margin) * mg[:, :, None]
        elif kind == 'grid':
            s = 54
            m = (((YY.astype(int)) % s < 2) | ((XX.astype(int)) % s < 2)).astype(float) * 0.35
            a = a * (1 - m[:, :, None]) + ink_c * m[:, :, None]
        else:
            s = 54
            dx = (XX % s) - s / 2; dy = (YY % s) - s / 2
            m = np.clip(3.2 - np.sqrt(dx ** 2 + dy ** 2), 0, 1) * 0.55
            a = a * (1 - m[:, :, None]) + ink_c * m[:, :, None]
    if warm:
        vig = ((XX - W / 2) / W) ** 2 + ((YY - H / 2) / H) ** 2
        a -= (vig * warm)[:, :, None]
    return np.clip(a, 0, 1)


# ---------------- 7. 유리·물방울 ----------------
def glass(rng, sky, cols, n=4):
    a = vgrad(sky, 1.0)
    for i in range(n):
        c = hexc(cols[i % len(cols)])
        cx, cy = rng.uniform(0, W), rng.uniform(H * 0.05, H * 0.95)
        rx, ry = rng.uniform(220, 420), rng.uniform(220, 520)
        ang = rng.uniform(0, math.pi)
        X = (XX - cx) * math.cos(ang) + (YY - cy) * math.sin(ang); Y = -(XX - cx) * math.sin(ang) + (YY - cy) * math.cos(ang)
        d = np.sqrt((X / rx) ** 2 + (Y / ry) ** 2)
        body = np.clip((1 - d) * 40, 0, 1)
        # 유리 몸: 안쪽은 색이 옅고 가장자리 빛
        rim = np.exp(-((d - 0.985) / 0.012) ** 2) * (0.5 + 0.5 * np.clip(-Y / ry, 0, 1))
        hi = np.exp(-(((X + rx * 0.35) / (rx * 0.35)) ** 2 + ((Y + ry * 0.45) / (ry * 0.18)) ** 2)) * body
        tint = c * 0.6 + 0.4
        a = a * (1 - body[:, :, None] * 0.45) + (np.clip(tint * 1.0 + 0.06, 0, 1))[None, None, :] * body[:, :, None] * 0.45
        a = a + (rim * 0.30 + hi * 0.28)[:, :, None]
        sh = np.exp(-((d - 1.04) / 0.07) ** 2) * np.clip(Y / ry + 0.3, 0, 1) * 0.07
        a = a - sh[:, :, None]
    a = blur(a, 2)
    return grain(np.clip(a, 0, 1), rng, 0.01)


def droplets(rng, sky, count=420):
    base = vgrad(sky, 1.0)
    bg = blur(grain(base, rng, 0.02), 30)
    a = bg.copy()
    im_s = Image.new('L', (W, H), 0); im_h = Image.new('L', (W, H), 0); im_m = Image.new('L', (W, H), 0)
    ds, dh, dm = ImageDraw.Draw(im_s), ImageDraw.Draw(im_h), ImageDraw.Draw(im_m)
    for _ in range(count):
        x, y = rng.uniform(0, W), rng.uniform(0, H); r = rng.uniform(4, 26) * (1 if rng.random() > 0.15 else 1.8)
        ry = r * rng.uniform(1.0, 1.25)
        ds.ellipse([x - r + 2, y - ry + 5, x + r + 2, y + ry + 5], fill=110)
        dm.ellipse([x - r, y - ry, x + r, y + ry], fill=255)
        dh.ellipse([x - r * 0.45, y - ry * 0.65, x - r * 0.05, y - ry * 0.25], fill=230)
    sh = np.asarray(im_s.filter(ImageFilter.GaussianBlur(4))).astype(float) / 255
    m = np.asarray(im_m.filter(ImageFilter.GaussianBlur(1))).astype(float) / 255
    hi = np.asarray(im_h.filter(ImageFilter.GaussianBlur(1.5))).astype(float) / 255
    sharp = base
    a = a * (1 - sh[:, :, None] * 0.12)
    a = a * (1 - m[:, :, None]) + (1 - (1 - sharp) * 0.7) * m[:, :, None]
    a = a + hi[:, :, None] * 0.6
    return grain(np.clip(a, 0, 1), rng, 0.01)


# ---------------- 8. 계절 ----------------
def petals(rng, sky, petal_cols, count=140, kind='blossom', center_calm=0.6, scale=1.0):
    a = vgrad(sky, 1.0)
    for bl, cnt, sz in ((7, count // 4, (34, 54)), (1.5, count // 2, (16, 30)), (0.6, count // 4, (8, 14))):
        im = Image.new('RGBA', (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        for _ in range(cnt):
            x, y = rng.uniform(-30, W + 30), rng.uniform(-30, H + 30)
            if abs(y - H * 0.5) < H * 0.22 and rng.random() < center_calm: continue
            s = rng.uniform(*sz) * scale; ang = rng.uniform(0, 2 * math.pi)
            c = hexc(petal_cols[rng.integers(len(petal_cols))])
            if kind == 'blossom':
                pts = []
                for k in range(48):
                    t = k / 48 * 2 * math.pi
                    # 끝이 갈라진 벚꽃 잎: 몸은 물방울, 끝(위)에 작은 홈
                    px = 0.42 * s * math.sin(t) * (0.55 + 0.45 * (1 - math.cos(t)) / 2)
                    py = -0.5 * s * math.cos(t)
                    if abs(math.sin(t)) < 0.18 and math.cos(t) > 0: py += 0.12 * s * (1 - abs(math.sin(t)) / 0.18)
                    pts.append((x + px * math.cos(ang) - py * math.sin(ang), y + px * math.sin(ang) + py * math.cos(ang)))
            elif kind == 'leaf':
                pts = []
                for k in range(30):
                    t = k / 30 * 2 * math.pi
                    px = s * math.cos(t); py = s * 0.45 * math.sin(t) * (1 - 0.3 * math.cos(t))
                    pts.append((x + px * math.cos(ang) - py * math.sin(ang), y + px * math.sin(ang) + py * math.cos(ang)))
            elif kind == 'snow':
                r = s * 0.25
                d.ellipse([x - r, y - r, x + r, y + r], fill=rgb255(c, int(rng.uniform(150, 240)))); continue
            else:  # rain streak
                l = s * 3; d.line([x, y, x - l * 0.12, y + l], fill=rgb255(c, int(rng.uniform(60, 140))), width=2); continue
            d.polygon(pts, fill=rgb255(c, int(rng.uniform(170, 245))))
            if kind == 'leaf':
                d.line([pts[0], pts[15]], fill=rgb255(c * 0.75, 200), width=2)
        arr = np.asarray(im).astype(float) / 255
        al = arr[:, :, 3:4]
        pm = arr[:, :, :3] * al
        pm = from_img(to_img(pm).filter(ImageFilter.GaussianBlur(bl))) if bl > 0 else pm
        al2 = blur1(al[:, :, 0], bl)[:, :, None]
        a = a * (1 - al2) + pm
    return grain(np.clip(a, 0, 1), rng, 0.012)


def clouds(rng, sky, cloud='#FFFFFF', cover=0.5, sun=None):
    a = vgrad(sky, 1.0)
    if sun:
        sx, sy = sun; d = np.sqrt((XX - sx) ** 2 + (YY - sy) ** 2)
        a += hexc('#FFF2C4') * (np.exp(-(d / 380) ** 2) * 0.6)[:, :, None]
    n = smooth_noise(H, W, rng, 3, 6, 0.55)
    n2 = smooth_noise(H, W, rng, 8, 3, 0.5)
    m = np.clip((n + n2 * 0.4 + cover - 0.5) * 3.2, 0, 1)
    m *= np.clip(1 - np.exp(-((YY - H * 0.5) / (H * 0.16)) ** 2) * 0.7, 0, 1)
    shade = np.clip(np.roll(m, 14, axis=0) - m, 0, 1) * 0.25
    a = a * (1 - m[:, :, None]) + hexc(cloud) * m[:, :, None] - shade[:, :, None] * 0.4
    return grain(np.clip(a, 0, 1), rng, 0.01)


# ---------------- 9. 도시 밤 ----------------
def city(rng, sky, building='#0B0E1A', win=('#FFD27A', '#FFE9B0', '#9FD7FF'), moon=True, glow='#FF7AB0'):
    a = vgrad(sky, 1.0)
    a = stars(a, rng, 0.0005, 0.45)
    g = np.exp(-(((YY - H * 0.72) / (H * 0.22)) ** 2)) * 0.45
    a += hexc(glow) * g[:, :, None]
    if moon:
        mx, my, mr = W * 0.72, H * 0.16, 62
        d = np.sqrt((XX - mx) ** 2 + (YY - my) ** 2)
        a += hexc('#FFF0D0') * (np.exp(-(d / 230) ** 2) * 0.35)[:, :, None]
        a = a * (1 - np.clip((mr - d) / 2, 0, 1)[:, :, None]) + hexc('#FFF6E0') * np.clip((mr - d) / 2, 0, 1)[:, :, None]
    for layer, (base, hmin, hmax, dark, wins) in enumerate(((0.70, 120, 520, 0.55, 0.07), (0.78, 180, 700, 0.85, 0.13), (0.86, 220, 820, 1.0, 0.2))):
        im = Image.new('RGBA', (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        x = -20
        while x < W:
            bw = rng.uniform(70, 170); bh = rng.uniform(hmin, hmax); top = H * base - bh
            col = hexc(building) * (1.6 - 0.6 * dark) + hexc(sky[-1]) * (1 - dark) * 0.35
            d.rectangle([x, top, x + bw, H], fill=rgb255(col, 255))
            if rng.random() < 0.25: d.rectangle([x + bw * 0.4, top - 40, x + bw * 0.45, top], fill=rgb255(col, 255))
            for wy in np.arange(top + 18, H, 26):
                for wx in np.arange(x + 10, x + bw - 14, 20):
                    if rng.random() < wins * (1 - (wy - top) / (H - top) * 0.3):
                        wc = hexc(win[rng.integers(len(win))]) * rng.uniform(0.55, 1) * (0.5 + 0.5 * dark)
                        d.rectangle([wx, wy, wx + 9, wy + 12], fill=rgb255(wc, 255))
            x += bw + rng.uniform(-10, 6)
        arr = np.asarray(im).astype(float) / 255; al = arr[:, :, 3:4]
        a = a * (1 - al) + arr[:, :, :3] * al
        if layer < 2:
            haze = np.exp(-(((YY - H * (base + 0.02)) / (H * 0.03)) ** 2)) * 0.25
            a += hexc(glow) * haze[:, :, None] * 0.5
    return grain(np.clip(a, 0, 1), rng, 0.012)


# ---------------- 10. 기하 무늬 ----------------
def geo(rng, bg, cols, kind='memphis', calm=0.0):
    base = hexc(bg)
    a = np.broadcast_to(base, (H, W, 3)).copy()
    S = 2  # 2배로 그려 줄여서 매끈하게
    im = Image.new('RGB', (W * S, H * S), rgb255(base)); d = ImageDraw.Draw(im)
    C = [rgb255(hexc(c)) for c in cols]
    if kind == 'memphis':
        for _ in range(70):
            x, y, s = rng.uniform(0, W * S), rng.uniform(0, H * S), rng.uniform(30, 90) * S
            c = C[rng.integers(len(C))]; t = rng.integers(5)
            if t == 0: d.ellipse([x - s / 2, y - s / 2, x + s / 2, y + s / 2], fill=c)
            elif t == 1: d.polygon([(x, y - s / 2), (x + s / 2, y + s / 2), (x - s / 2, y + s / 2)], fill=c)
            elif t == 2:
                pts = [(x + i * 14 * S, y + (12 * S if i % 2 else 0)) for i in range(7)]; d.line(pts, fill=c, width=7 * S, joint='curve')
            elif t == 3: d.ellipse([x - s / 2, y - s / 2, x + s / 2, y + s / 2], outline=c, width=7 * S)
            else: d.rounded_rectangle([x - s / 2, y - s / 6, x + s / 2, y + s / 6], radius=s / 6, fill=c)
    elif kind == 'terrazzo':
        for _ in range(900):
            x, y, s = rng.uniform(0, W * S), rng.uniform(0, H * S), rng.uniform(6, 34) * S
            pts = [(x + s * rng.uniform(.4, 1) * math.cos(t), y + s * rng.uniform(.4, 1) * math.sin(t)) for t in np.linspace(0, 2 * math.pi, rng.integers(4, 8), endpoint=False)]
            d.polygon(pts, fill=C[rng.integers(len(C))])
    elif kind == 'arcs':
        s = 180 * S
        for gy in range(-1, H * S // s + 2):
            for gx in range(-1, W * S // s + 2):
                cx, cy = gx * s + (s / 2 if gy % 2 else 0), gy * s
                for k, rr in enumerate(np.linspace(s * 0.9, s * 0.2, 5)):
                    d.pieslice([cx - rr, cy - rr, cx + rr, cy + rr], 0, 180, fill=C[(k + gx + gy) % len(C)])
    elif kind == 'lines':
        for k in range(0, (H + W) * S, 34 * S):
            pts = []
            for xx in range(0, W * S + 40, 20 * S):
                pts.append((xx, k - xx * 0.0 + 26 * S * math.sin(xx / (180 * S) + k / (300 * S))))
            d.line(pts, fill=C[(k // (34 * S)) % len(C)], width=5 * S, joint='curve')
    elif kind == 'deco':
        s = 300 * S
        for gy in range(-1, H * S // (s // 2) + 2):
            for gx in range(-1, W * S // s + 2):
                cx = gx * s + (s / 2 if gy % 2 else 0); cy = gy * s / 2
                for k, rr in enumerate(np.linspace(s / 2, s / 10, 6)):
                    d.pieslice([cx - rr, cy - rr, cx + rr, cy + rr], 180, 360, fill=C[k % 2], outline=C[2 % len(C)], width=3 * S)
    elif kind == 'checker':
        s = 120 * S
        for gy in range(H * S // s + 1):
            for gx in range(W * S // s + 1):
                if (gx + gy) % 2: d.rounded_rectangle([gx * s + 6 * S, gy * s + 6 * S, gx * s + s - 6 * S, gy * s + s - 6 * S], radius=26 * S, fill=C[(gx // 2 + gy) % len(C)])
    elif kind == 'halftone':
        s = 34 * S
        for gy in range(H * S // s + 1):
            for gx in range(W * S // s + 1):
                t = gy / (H * S / s)
                r = s * 0.48 * (1 - t) ** 1.3
                if r > 1: d.ellipse([gx * s + s / 2 - r, gy * s + s / 2 - r, gx * s + s / 2 + r, gy * s + s / 2 + r], fill=C[0])
        for gy in range(H * S // s + 1):
            for gx in range(W * S // s + 1):
                t = 1 - gy / (H * S / s)
                r = s * 0.40 * (1 - t) ** 1.6
                if r > 1: d.ellipse([gx * s + s / 2 - r, gy * s + s / 2 - r, gx * s + s / 2 + r, gy * s + s / 2 + r], fill=C[1 % len(C)])
    a = from_img(im.resize((W, H), Image.LANCZOS))
    if calm: a = calm_center(a, base, calm)
    return a


STYLES = dict(grad=grad, aurora=aurora, nightsky=nightsky, landscape=landscape, waves=waves, sunset_sea=sunset_sea, underwater=underwater,
              bokeh=bokeh, paper=paper, glass=glass, droplets=droplets, petals=petals, clouds=clouds, city=city, geo=geo)


def save_budget(a, path, budget=150 * 1024):
    im = to_img(a); lo, hi, best = 40, 92, None
    while lo <= hi:
        q = (lo + hi) // 2; b = io.BytesIO(); im.save(b, 'WEBP', quality=q, method=6)
        if len(b.getvalue()) <= budget: best = (q, b.getvalue()); lo = q + 1
        else: hi = q - 1
    if best is None:
        b = io.BytesIO(); im.save(b, 'WEBP', quality=35, method=6); best = (35, b.getvalue())
    open(path, 'wb').write(best[1])
    arr = np.asarray(im).astype(float) / 255
    lum = float((0.2126 * arr[:, :, 0] + 0.7152 * arr[:, :, 1] + 0.0722 * arr[:, :, 2]).mean())
    avg = arr.reshape(-1, 3).mean(0)
    return dict(q=best[0], bytes=len(best[1]), light=round(lum, 3), avg='#%02X%02X%02X' % tuple(int(v * 255) for v in avg))


if __name__ == '__main__':
    import sys, json
    spec = json.loads(open(sys.argv[1]).read()) if sys.argv[1].endswith('.json') else json.loads(sys.argv[1])
    rng = np.random.default_rng(spec.get('seed', 1))
    arr = STYLES[spec['style']](rng, **spec['args'])
    print(json.dumps(save_budget(arr, sys.argv[2])))
