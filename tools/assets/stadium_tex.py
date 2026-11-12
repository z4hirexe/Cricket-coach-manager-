"""Procedural texture library for stadium v2 (realistic broadcast look).  All functions return PIL images.
Scales are physical: comments state how many metres one texture tile covers so geometry UVs can match."""
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FONT_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_C = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"
FONT_S = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
FONT_CJK = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"


def _u8(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))


def fbm(h, w, oct=5, base=4, seed=0, tile=True):
    """tileable fractal value noise in [0,1]"""
    r = np.random.default_rng(seed); out = np.zeros((h, w)); amp = 1.0; tot = 0
    for o in range(oct):
        gy, gx = base * 2 ** o, base * 2 ** o * max(1, w // h)
        g = r.random((gy, gx))
        if tile: g = np.pad(g, ((0, 1), (0, 1)), mode="wrap")
        im = Image.fromarray((g * 255).astype(np.uint8)).resize((w + (w // gx if tile else 0), h + (h // gy if tile else 0)), Image.BICUBIC)
        a = np.asarray(im, float)[:h, :w] / 255
        out += a * amp; tot += amp; amp *= 0.5
    out /= tot
    return (out - out.min()) / max(np.ptp(out), 1e-6)


def normal_from_height(hgt, strength=2.0):
    dx = np.roll(hgt, -1, 1) - np.roll(hgt, 1, 1); dy = np.roll(hgt, -1, 0) - np.roll(hgt, 1, 0)
    nm = np.stack([-dx * strength, -dy * strength, np.ones_like(hgt)], -1); nm /= np.linalg.norm(nm, axis=-1, keepdims=True)
    return _u8(nm * 0.5 + 0.5)


# ------------------------------------------------------------------ turf
def grass(n=1024, seed=11):
    """detail turf, one tile = 3 m.  Fine blades + clumps + soil specks, near-neutral so vertex colours drive the mowing tone."""
    r = np.random.default_rng(seed)
    blades = r.random((n, n))
    bl = np.asarray(Image.fromarray((blades * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.7)), float) / 255
    streak = np.asarray(Image.fromarray((r.random((n, n)) * 255).astype(np.uint8)).resize((n, n)).filter(ImageFilter.BoxBlur(1)), float) / 255
    clump = fbm(n, n, 5, 6, seed + 1)
    big = fbm(n, n, 3, 2, seed + 2)
    k = 0.80 + 0.22 * (bl - 0.5) * 2 * 0.5 + 0.10 * (clump - 0.5) + 0.06 * (big - 0.5) + 0.05 * (streak - 0.5)
    img = np.zeros((n, n, 3))
    img[..., 0] = (0.29 + 0.05 * (clump - 0.5)) * k
    img[..., 1] = 0.44 * k
    img[..., 2] = (0.14 - 0.03 * (big - 0.5)) * k
    dry = (fbm(n, n, 4, 8, seed + 3) > 0.78) * 0.12
    img[..., 0] += dry * 0.25; img[..., 1] += dry * 0.12
    specks = r.random((n, n)) > 0.9975
    img[specks] = img[specks] * 0.4 + np.array([0.30, 0.24, 0.15]) * 0.6
    hgt = bl * 0.6 + clump * 0.4
    return _u8(img), normal_from_height(hgt, 2.2)


def pitch(w=384, h=2496, seed=21):
    """Prepared strip.  u spans x=-1.8..1.8 m (3.6 m), v spans z=-2.4..22.52 m (24.92 m).  Light-brown Indian clay, rolled
    sheen, dry grass, fine crack network on a length, painted creases, wide guidelines, protected-area markers."""
    r = np.random.default_rng(seed)
    X = ((np.arange(w) + 0.5) / w * 3.6 - 1.8)[None, :]; Z = ((np.arange(h) + 0.5) / h * 24.92 - 2.4)[:, None]
    mott = fbm(h, w, 6, 6, seed, tile=False); fine = np.asarray(Image.fromarray((r.random((h, w)) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8)), float) / 255
    roll = 0.5 + 0.5 * np.sin(X * 2 * np.pi / 0.9 + 0.3 * mott)  # faint roller passes along the strip
    base = np.array([0.70, 0.58, 0.40])
    k = 0.86 + 0.10 * (mott - 0.5) + 0.08 * (fine - 0.5) + 0.03 * roll
    img = base[None, None] * k[..., None]
    # moisture / darker compacted centre line
    img *= (1 - 0.05 * np.exp(-(X / 0.5) ** 2))[..., None]
    # crack network (voronoi edges) concentrated on good length at both ends
    from scipy.spatial import cKDTree
    seeds = np.c_[r.uniform(-1.6, 1.6, 2600), r.uniform(-2, 22, 2600)]
    tree = cKDTree(seeds); P = np.stack(np.broadcast_arrays(X, Z), -1).reshape(-1, 2)
    d, _ = tree.query(P, k=2); edge = (d[:, 1] - d[:, 0]).reshape(h, w)
    zone = np.exp(-((Z - 6.5) / 4.0) ** 2) + np.exp(-((Z - 13.6) / 4.0) ** 2)
    crack = np.clip(1 - edge / 0.007, 0, 1) * (0.08 + 0.92 * zone) * (np.abs(X) < 1.45)
    crack *= np.clip((fbm(h, w, 3, 3, seed + 5, tile=False) - 0.45) * 4, 0, 1)
    img *= (1 - 0.30 * crack)[..., None]
    # dry grass tufts, denser towards the edges
    tuft = (r.random((h, w)) > 0.985 - 0.04 * np.clip((np.abs(X) - 0.8) / 0.7, 0, 1)) & (fbm(h, w, 4, 10, seed + 7, tile=False) > 0.45)
    tf = np.asarray(Image.fromarray((tuft * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.9)), float) / 255
    img = img * (1 - tf[..., None] * 0.8) + np.array([0.52, 0.55, 0.30])[None, None] * tf[..., None] * 0.8
    # blend strip edge into the square's grass
    grassc = np.array([0.21, 0.32, 0.105]) * (0.9 + 0.15 * mott[..., None])
    e = np.clip((np.abs(X) - 1.42) / 0.36, 0, 1)
    e = np.maximum(e, np.clip((-1.7 - Z) / 0.7, 0, 1)); e = np.maximum(e, np.clip((Z - 21.82) / 0.7, 0, 1))
    e = np.clip(e + (fbm(h, w, 4, 12, seed + 9, tile=False) - 0.5) * 0.5 * (e > 0) * (e < 1), 0, 1)
    img = img * (1 - e[..., None]) + grassc * e[..., None]
    # baked light foot-scuffs around both creases (the dynamic wear decal adds more as the match goes on)
    for zc, s in ((0.0, 1), (20.12, -1)):
        sc = np.exp(-(((Z - (zc + 1.25 * s)) / 0.55) ** 2 + (X / 0.55) ** 2)) * 0.10
        img *= (1 - sc)[..., None]
    pim = _u8(img); dr = ImageDraw.Draw(pim, "RGBA")
    px = lambda x: (x + 1.8) / 3.6 * w; pz = lambda z: (z + 2.4) / 24.92 * h; lw = max(2, round(0.05 / 3.6 * w))
    paint = (246, 246, 240, 235)
    def line(x0, z0, x1, z1):
        dr.rectangle([min(px(x0), px(x1)) - (lw / 2 if x0 == x1 else 0), min(pz(z0), pz(z1)) - (lw / 2 if z0 == z1 else 0),
                      max(px(x0), px(x1)) + (lw / 2 if x0 == x1 else 0), max(pz(z0), pz(z1)) + (lw / 2 if z0 == z1 else 0)], fill=paint)
    for zs, s in ((0.0, 1), (20.12, -1)):
        pop = zs + 1.22 * s
        line(-1.6, pop, 1.6, pop)                         # popping crease (runs to the strip edge, min 1.83 m from middle stump)
        line(-1.32, zs, 1.32, zs)                         # bowling crease 2.64 m
        for x in (-1.32, 1.32): line(x, zs - 1.22 * s, x, pop)   # return creases (drawn 1.22 m behind the bowling crease)
        for x in (-0.89, 0.89): line(x, pop, x, pop + 0.30 * s)  # limited-overs wide guidelines
        for x in (-0.305, 0.305): line(x, pop + 1.52 * s, x, pop + 1.52 * s + 0.15 * s)  # protected-area markers
    pim = pim.filter(ImageFilter.GaussianBlur(0.35))
    hgt = mott * 0.5 + fine * 0.3 - crack * 0.8
    return pim, normal_from_height(hgt, 3.0)


def pitch_wear(w=256, h=1664, seed=31):
    """RGBA decal over the strip (same mapping as pitch()) – bowlers' footholes, follow-through rough outside the RH off stump,
    batters' guard scrapes, ball marks on a length.  Runtime fades alpha 0 -> 1 as the match ages."""
    r = np.random.default_rng(seed)
    X = ((np.arange(w) + 0.5) / w * 3.6 - 1.8)[None, :]; Z = ((np.arange(h) + 0.5) / h * 24.92 - 2.4)[:, None]
    n = fbm(h, w, 5, 10, seed, tile=False); a = np.zeros((h, w))
    def blob(xc, zc, sx, sz, k): return k * np.exp(-(((X - xc) / sx) ** 2 + ((Z - zc) / sz) ** 2))
    for zc, s in ((0.0, 1), (20.12, -1)):
        bz = zc + (20.12 - 2 * zc) * 0 + (1.1 * -s if zc == 20.12 else 1.1 * -s)  # beyond the far crease at the bowler's end
    # bowler's front-foot landing just behind the popping crease (both ends, RA over / round)
    for zc, s in ((20.12, -1), (0.0, 1)):
        for xc in (-0.45, 0.45):
            a += blob(xc, zc + 1.0 * s, 0.22, 0.35, 0.95)
        # follow-through rough outside off stump (RHB) ~ 1.5-3.5 m in front of the popping crease
        a += blob(-0.75, zc + 3.6 * s, 0.30, 1.4, 0.7) + blob(0.75, zc + 3.6 * s, 0.30, 1.4, 0.45)
        # batter's guard mark & crease scrape
        a += blob(0.0, zc + 1.22 * s, 0.10, 0.05, 1.0) + blob(0.0, zc + 1.15 * s, 0.5, 0.22, 0.45)
    # ball marks on a length
    for _ in range(140):
        zc = r.choice([r.normal(6.2, 1.6), r.normal(13.9, 1.6)]); xc = r.normal(0, 0.28)
        a += blob(xc, zc, 0.035, 0.05, 0.8)
    a = np.clip(a * (0.55 + 0.9 * n), 0, 1) * (np.abs(X) < 1.5)
    col = np.zeros((h, w, 3)); col[...] = np.array([0.42, 0.31, 0.19]) * (0.8 + 0.4 * n[..., None])
    rgba = np.dstack([col, a * 0.92])
    return Image.fromarray((np.clip(rgba, 0, 1) * 255).astype(np.uint8), "RGBA")


# ------------------------------------------------------------------ crowd
SHIRTS = [((28, 78, 178), 18), ((22, 56, 140), 10), ((240, 120, 30), 9), ((236, 236, 232), 9), ((215, 180, 40), 6), ((190, 40, 45), 5),
          ((30, 130, 70), 3), ((25, 25, 30), 8), ((95, 150, 210), 7), ((120, 45, 120), 3), ((140, 120, 100), 5), ((70, 80, 95), 6)]
SKIN = [(118, 80, 56), (150, 104, 74), (96, 64, 44), (178, 134, 100), (72, 50, 36), (200, 160, 128)]


def _pick(r, pal):
    tot = sum(w for _, w in pal); x = r.uniform(0, tot)
    for c, w in pal:
        x -= w
        if x <= 0: return c
    return pal[-1][0]


def crowd_tile(w=1024, h=512, rows=8, seed=3, seat=(30, 70, 150), fill=0.86, members=False):
    """one atlas tile: `rows` seat rows (0.8 m each along the slope) x 23 m wide, seen from the field.  Aisle stairs at the left edge."""
    r = np.random.default_rng(seed); sc = 3
    W, H = w * sc, h * sc
    im = Image.new("RGB", (W, H), (58, 60, 66)); d = ImageDraw.Draw(im)
    rh = H / rows; sw = W / 46.0; aisle = sw * 2.2
    for row in range(rows):
        y0 = row * rh
        d.rectangle([0, y0, W, y0 + rh], fill=(70, 72, 78))                         # tread (concrete)
        d.rectangle([0, y0 + rh * 0.82, W, y0 + rh], fill=(46, 47, 52))             # riser shadow
        x = aisle
        k = 0
        while x + sw <= W + 1:
            cx = x + sw / 2
            # seat back + pan
            sb = tuple(int(v * r.uniform(0.85, 1.05)) for v in seat)
            d.rounded_rectangle([x + sw * 0.08, y0 + rh * 0.36, x + sw * 0.92, y0 + rh * 0.80], int(sw * 0.15), fill=sb)
            d.rectangle([x + sw * 0.1, y0 + rh * 0.62, x + sw * 0.9, y0 + rh * 0.80], fill=tuple(int(v * 0.7) for v in sb))
            if r.random() < fill:
                if members:
                    shirt = [(235, 232, 220), (30, 34, 60), (120, 30, 40), (200, 190, 160)][r.integers(4)]
                else:
                    shirt = _pick(r, SHIRTS)
                shirt = tuple(int(np.clip(v * r.uniform(0.82, 1.08), 0, 255)) for v in shirt)
                stand = r.random() < 0.05
                lift = rh * (0.22 if stand else 0.0)
                tw = sw * r.uniform(0.78, 0.94); ty0 = y0 + rh * 0.24 - lift; ty1 = y0 + rh * 0.84
                # torso with fake volume: darker flanks
                d.rounded_rectangle([cx - tw / 2, ty0, cx + tw / 2, ty1], int(tw * 0.3), fill=shirt)
                dk = tuple(int(v * 0.72) for v in shirt)
                d.rectangle([cx - tw / 2 + 1, ty0 + rh * 0.12, cx - tw / 2 + tw * 0.16, ty1], fill=dk)
                d.rectangle([cx + tw / 2 - tw * 0.16, ty0 + rh * 0.12, cx + tw / 2 - 1, ty1], fill=dk)
                sk = SKIN[r.integers(len(SKIN))]; hr = sw * r.uniform(0.24, 0.29); hy = ty0 - hr * 0.55
                d.ellipse([cx - hr * 0.55, ty0 - hr * 0.2, cx + hr * 0.55, ty0 + hr * 0.5], fill=tuple(int(v * 0.85) for v in sk))  # neck
                d.ellipse([cx - hr, hy - hr, cx + hr, hy + hr * 1.12], fill=sk)
                hv = r.random()
                if hv < 0.55: d.chord([cx - hr * 1.02, hy - hr * 1.08, cx + hr * 1.02, hy + hr * 0.5], 180, 360, fill=(22, 17, 14))
                elif hv < 0.68: d.chord([cx - hr * 1.15, hy - hr * 1.15, cx + hr * 1.15, hy + hr * 0.3], 180, 360, fill=_pick(r, SHIRTS))  # cap
                if r.random() < 0.10:  # arms raised / phone / flag
                    ac = sk if r.random() < 0.6 else shirt
                    for sgn in (-1, 1):
                        d.line([cx + sgn * tw * 0.4, ty0 + rh * 0.08, cx + sgn * tw * 0.55, ty0 - rh * 0.35], fill=ac, width=int(sw * 0.13))
                    if r.random() < 0.3:
                        fc = [(255, 153, 51), (19, 136, 8), (255, 255, 255), (28, 78, 178), (230, 190, 40)][r.integers(5)]
                        d.rectangle([cx + tw * 0.5, ty0 - rh * 0.65, cx + tw * 0.5 + sw * 1.3, ty0 - rh * 0.25], fill=fc)
            x += sw; k += 1
        # aisle stairs at the tile's left edge (two steps per seat row)
        d.rectangle([0, y0, aisle, y0 + rh], fill=(98, 99, 104))
        d.rectangle([0, y0 + rh * 0.45, aisle, y0 + rh * 0.52], fill=(60, 61, 66)); d.rectangle([0, y0 + rh * 0.93, aisle, y0 + rh], fill=(60, 61, 66))
        d.rectangle([aisle * 0.48, y0, aisle * 0.52, y0 + rh], fill=(170, 172, 176))  # handrail
    im = im.filter(ImageFilter.GaussianBlur(sc * 0.35)).resize((w, h), Image.LANCZOS)
    # gentle depth gradient (upper rows slightly darker / bluer under the roof)
    a = np.asarray(im, float) / 255; yy = np.linspace(0, 1, h)[:, None, None]
    a *= 0.92 + 0.08 * yy
    return _u8(a)


def crowd_atlas():
    """4 tiles stacked vertically (v bands of 0.25): blue seats, orange/yellow section, members (whites/blazers), dense mixed."""
    tiles = [crowd_tile(seed=3, seat=(30, 70, 150), fill=0.95), crowd_tile(seed=4, seat=(200, 120, 30), fill=0.93),
             crowd_tile(seed=5, seat=(235, 235, 235), fill=0.88, members=True), crowd_tile(seed=6, seat=(150, 30, 40), fill=0.97)]
    at = Image.new("RGB", (1024, 2048))
    for i, t in enumerate(tiles): at.paste(t, (0, i * 512))
    return at


# ------------------------------------------------------------------ advertising
BRANDS = [("NOVAPAY", (10, 70, 190), (255, 255, 255)), ("SKYRAIL", (240, 190, 20), (10, 15, 30)), ("CRICKET MANAGER PRO", (6, 14, 40), (233, 185, 73)),
          ("AEROVA", (200, 20, 35), (255, 255, 255)), ("ZENTRA MOBILE", (0, 120, 90), (255, 245, 210)), ("PEAKFUEL", (20, 20, 26), (255, 196, 40)),
          ("ORBIT BANK", (90, 30, 150), (255, 255, 255)), ("MONSOON COLA", (210, 30, 30), (255, 230, 120)), ("TERRAVOLT", (15, 90, 60), (200, 255, 140)),
          ("QUILLA TEA", (240, 240, 235), (120, 40, 20)), ("VEGAAIR", (0, 150, 220), (255, 255, 255)), ("CRICKET MANAGER PRO", (6, 14, 40), (233, 185, 73))]


def ad_boards(w=4096, h=128):
    """LED perimeter boards, whole strip = one lap section (tiled 3x around the ground)."""
    im = Image.new("RGB", (w, h), (8, 10, 16)); d = ImageDraw.Draw(im); n = len(BRANDS); sw = w / n
    for i, (b, bg, fg) in enumerate(BRANDS):
        x0 = i * sw
        # gradient background
        for yy in range(h):
            k = 0.78 + 0.32 * (1 - yy / h)
            d.line([x0, yy, x0 + sw, yy], fill=tuple(int(min(255, c * k)) for c in bg))
        fs = int(h * 0.60)
        while True:
            f = ImageFont.truetype(FONT_B, fs); tw = d.textlength(b, font=f)
            if tw + (h * 1.2 if len(b) < 12 else 0) < sw * 0.9 or fs < 20: break
            fs -= 2
        lx = x0 + (sw - tw) / 2 + (h * 0.45 if len(b) < 12 else 0)
        if len(b) < 12: d.ellipse([lx - h * 0.78, h * 0.2, lx - h * 0.18, h * 0.8], outline=fg, width=int(h * 0.08))   # logo mark
        if len(b) < 12: d.arc([lx - h * 0.70, h * 0.28, lx - h * 0.26, h * 0.72], 200, 340, fill=fg, width=int(h * 0.08))
        d.text((lx, h * 0.5), b, font=f, fill=fg, anchor="lm")
        d.line([x0, 0, x0, h], fill=(0, 0, 0), width=3)
    a = np.asarray(im, float) / 255
    # LED pixel grid
    yy, xx = np.mgrid[0:h, 0:w]; grid = 0.86 + 0.14 * ((xx % 4 != 0) & (yy % 4 != 0))
    return _u8(a * grid[..., None])


def ribbon(w=4096, h=96):
    """tier-front LED ribbon: navy/gold animated-looking banner with match messages."""
    im = Image.new("RGB", (w, h), (4, 10, 30)); d = ImageDraw.Draw(im)
    msgs = ["CRICKET MANAGER PRO", "WELCOME TO CMP ARENA", "FOUR", "SIX", "NOVAPAY", "WHAT A SHOT!", "SKYRAIL", "CMP ARENA"]
    f = ImageFont.truetype(FONT_B, int(h * 0.58)); x = 20; i = 0
    while x < w:
        m = msgs[i % len(msgs)]; tw = d.textlength(m, font=f)
        col = (233, 185, 73) if i % 2 == 0 else (240, 244, 255)
        d.text((x, h * 0.5), m, font=f, fill=col, anchor="lm"); x += tw + 60
        d.polygon([(x - 40, h * 0.3), (x - 20, h * 0.5), (x - 40, h * 0.7)], fill=(233, 185, 73)); i += 1
    return im


# ------------------------------------------------------------------ building surfaces
def facade(w=512, h=512, seed=41):
    """exterior: concrete frame with vertical aluminium fins and glazing, one tile = 12 m x 12 m (3 floors)."""
    r = np.random.default_rng(seed); im = Image.new("RGB", (w, h), (176, 178, 182)); d = ImageDraw.Draw(im)
    fl = h / 3
    for k in range(3):
        y0 = k * fl
        d.rectangle([0, y0 + fl * 0.18, w, y0 + fl * 0.92], fill=(38, 52, 70))       # glass band
        for x in range(0, w, 16):
            sh = int(30 + 40 * r.random())
            d.rectangle([x, y0 + fl * 0.18, x + 15, y0 + fl * 0.92], fill=(38 + sh // 3, 52 + sh // 3, 70 + sh // 3))
            d.rectangle([x, y0 + fl * 0.18, x + 3, y0 + fl * 0.92], fill=(200, 202, 206))  # fin
        d.rectangle([0, y0, w, y0 + fl * 0.18], fill=(192, 192, 194))
    n = fbm(h, w, 4, 4, seed); a = np.asarray(im, float) / 255 * (0.92 + 0.12 * n[..., None])
    return _u8(a)


def facade_emissive(w=512, h=512, seed=42):
    """night window glow for facade()."""
    r = np.random.default_rng(seed); im = Image.new("RGB", (w, h), (0, 0, 0)); d = ImageDraw.Draw(im); fl = h / 3
    for k in range(3):
        y0 = k * fl
        for x in range(0, w, 16):
            if r.random() < 0.7:
                c = (255, int(200 + 40 * r.random()), int(140 + 60 * r.random()))
                d.rectangle([x + 4, y0 + fl * 0.22, x + 15, y0 + fl * 0.88], fill=tuple(int(v * r.uniform(0.35, 0.8)) for v in c))
    return im


def vip_glass(w=1024, h=128, seed=43):
    """corporate boxes band between tiers: glass boxes with people/silhouettes and warm interior, one tile = 24 m."""
    r = np.random.default_rng(seed); im = Image.new("RGB", (w, h), (18, 24, 34)); d = ImageDraw.Draw(im); bw = w / 6
    for i in range(6):
        x0 = i * bw
        d.rectangle([x0 + 4, h * 0.1, x0 + bw - 4, h * 0.92], fill=(54, 46, 40))
        d.rectangle([x0 + 4, h * 0.1, x0 + bw - 4, h * 0.35], fill=(120, 100, 76))     # ceiling lights
        for p in range(int(r.integers(2, 7))):
            px = x0 + r.uniform(12, bw - 12); c = [(30, 32, 40), (220, 220, 215), (60, 40, 80), (150, 30, 40)][r.integers(4)]
            d.rectangle([px - 5, h * 0.52, px + 5, h * 0.9], fill=c); d.ellipse([px - 4, h * 0.40, px + 4, h * 0.53], fill=SKIN[r.integers(len(SKIN))])
        d.rectangle([x0, 0, x0 + 4, h], fill=(150, 152, 158)); d.rectangle([x0, h * 0.62, x0 + bw, h * 0.66], fill=(170, 175, 180))  # mullion + rail
    a = np.asarray(im, float) / 255
    a[: int(h * 0.6)] = a[: int(h * 0.6)] * 0.75 + np.array([0.20, 0.26, 0.32]) * 0.25          # reflection on glass
    return _u8(a)


def pavilion_front(w=1024, h=512, seed=44):
    """heritage-modern pavilion facade (56 m x 20 m): stone plinth with arches, dressing-room glass, members' lounge, media level."""
    r = np.random.default_rng(seed); im = Image.new("RGB", (w, h), (226, 220, 204)); d = ImageDraw.Draw(im)
    X = lambda m: m / 56 * w; Y = lambda m: h - m / 20 * h
    # stone texture
    n = fbm(h, w, 5, 6, seed)
    # ground floor arches
    for i in range(14):
        cx = X(2 + i * 4)
        d.rectangle([cx - X(1.3), Y(3.6), cx + X(1.3), Y(0)], fill=(60, 52, 44))
        d.ellipse([cx - X(1.3), Y(4.6), cx + X(1.3), Y(2.6)], fill=(60, 52, 44))
    d.rectangle([X(25), Y(4.0), X(31), Y(0)], fill=(28, 24, 22))   # players' tunnel
    d.rectangle([0, Y(4.6), w, Y(4.2)], fill=(200, 192, 174))
    # level 1: dressing rooms (left home, right away) + lounge
    for x0, x1 in ((1, 18), (38, 55)):
        d.rectangle([X(x0), Y(8.6), X(x1), Y(5.0)], fill=(34, 46, 60))
        for k in range(int((x1 - x0) / 1.5)): d.rectangle([X(x0 + k * 1.5), Y(8.6), X(x0 + k * 1.5) + 2, Y(5.0)], fill=(190, 186, 176))
    d.rectangle([X(19), Y(8.6), X(37), Y(5.0)], fill=(70, 60, 48))
    # level 2 VIP glass, level 3 media boxes
    d.rectangle([X(1), Y(13.4), X(55), Y(9.6)], fill=(30, 42, 56))
    for k in range(37): d.rectangle([X(1 + k * 1.5), Y(13.4), X(1 + k * 1.5) + 2, Y(9.6)], fill=(205, 200, 190))
    d.rectangle([X(4), Y(18.2), X(52), Y(14.6)], fill=(24, 30, 40))
    for k in range(16): d.rectangle([X(4 + k * 3), Y(18.2), X(4 + k * 3) + 3, Y(14.6)], fill=(210, 204, 192))
    f = ImageFont.truetype(FONT_S, int(h * 0.045)); d.text((X(28), Y(19.2)), "CMP ARENA  •  PAVILION", font=f, fill=(150, 112, 40), anchor="mm")
    a = np.asarray(im, float) / 255 * (0.9 + 0.14 * n[..., None])
    return _u8(a)


def pavilion_emissive(w=1024, h=512):
    im = Image.new("RGB", (w, h), (0, 0, 0)); d = ImageDraw.Draw(im); X = lambda m: m / 56 * w; Y = lambda m: h - m / 20 * h
    for x0, x1 in ((1, 18), (38, 55), (19, 37)): d.rectangle([X(x0), Y(8.4), X(x1), Y(5.2)], fill=(150, 120, 80))
    d.rectangle([X(1), Y(13.2), X(55), Y(9.8)], fill=(170, 140, 90)); d.rectangle([X(4), Y(18.0), X(52), Y(14.8)], fill=(120, 130, 150))
    f = ImageFont.truetype(FONT_S, int(h * 0.045)); d.text((X(28), Y(19.2)), "CMP ARENA  •  PAVILION", font=f, fill=(255, 210, 120), anchor="mm")
    return im


def roof_membrane(w=512, h=256, seed=45):
    """PTFE roof panels: off-white with radial seams; one tile = 18 m along the arc x whole roof depth."""
    n = fbm(h, w, 4, 4, seed); a = np.ones((h, w, 3)) * np.array([0.86, 0.87, 0.88]) * (0.94 + 0.08 * n[..., None])
    xx = np.arange(w)[None, :]; seam = (xx % (w // 6) < 3)
    a[np.broadcast_to(seam, (h, w))] *= 0.72
    yy = np.arange(h)[:, None]; a[np.broadcast_to(yy % (h // 4) < 2, (h, w))] *= 0.85
    return _u8(a)


def lamp_bank(w=256, h=192):
    """floodlight head: 8 x 6 lamps."""
    im = Image.new("RGB", (w, h), (30, 32, 36)); d = ImageDraw.Draw(im)
    for i in range(8):
        for j in range(6):
            cx = (i + 0.5) * w / 8; cy = (j + 0.5) * h / 6; rr = w / 8 * 0.42
            d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(235, 238, 240)); d.ellipse([cx - rr * 0.6, cy - rr * 0.6, cx + rr * 0.6, cy + rr * 0.6], fill=(255, 255, 255))
    return im


def big_screen(w=1024, h=448):
    """far-end giant screen content (static brand loop)."""
    im = Image.new("RGB", (w, h), (3, 8, 26)); d = ImageDraw.Draw(im)
    for y in range(h): d.line([0, y, w, y], fill=(int(4 + 20 * y / h), int(10 + 26 * y / h), int(30 + 60 * y / h)))
    d.rectangle([0, 0, w - 1, h - 1], outline=(233, 185, 73), width=10)
    f1 = ImageFont.truetype(FONT_B, 64); f2 = ImageFont.truetype(FONT_B, 40)
    d.text((w / 2, h * 0.36), "CRICKET MANAGER PRO", font=f1, fill=(233, 185, 73), anchor="mm")
    d.text((w / 2, h * 0.66), "WELCOME TO CMP ARENA", font=f2, fill=(240, 244, 255), anchor="mm")
    a = np.asarray(im, float) / 255; yy, xx = np.mgrid[0:h, 0:w]; grid = 0.8 + 0.2 * ((xx % 3 != 0) & (yy % 3 != 0))
    return _u8(a * grid[..., None])


def sight_screen(w=64, h=64):
    return Image.new("RGB", (w, h), (255, 255, 255))


def concrete(n=512, seed=46):
    nz = fbm(n, n, 6, 4, seed); r = np.random.default_rng(seed)
    v = 0.60 + 0.22 * (nz - 0.5) + 0.05 * (r.random((n, n)) - 0.5)
    return _u8(np.stack([v, v * 0.99, v * 0.97], -1))


def plaza(n=1024, seed=47):
    """exterior paving + lawn ring, one tile = 20 m."""
    r = np.random.default_rng(seed); im = Image.new("RGB", (n, n), (150, 146, 138)); d = ImageDraw.Draw(im)
    s = n // 20
    for i in range(20):
        for j in range(20):
            c = int(138 + 22 * r.random()); d.rectangle([i * s + 1, j * s + 1, (i + 1) * s - 1, (j + 1) * s - 1], fill=(c, c - 3, c - 9))
    nz = fbm(n, n, 4, 4, seed); a = np.asarray(im, float) / 255 * (0.9 + 0.15 * nz[..., None])
    return _u8(a)


def city_windows(w=256, h=512, seed=48):
    r = np.random.default_rng(seed); im = Image.new("RGB", (w, h), (70, 74, 82)); d = ImageDraw.Draw(im); em = Image.new("RGB", (w, h), (0, 0, 0)); de = ImageDraw.Draw(em)
    for y in range(4, h, 16):
        for x in range(4, w, 14):
            d.rectangle([x, y, x + 9, y + 10], fill=(40, 50, 64))
            if r.random() < 0.45: de.rectangle([x, y, x + 9, y + 10], fill=(int(200 + 55 * r.random()), int(170 + 50 * r.random()), int(110 + 60 * r.random())))
    return im, em


# ------------------------------------------------------------------ sky (equirect upper hemisphere: u = azimuth, v = 0 zenith .. 1 below horizon)
def sky(kind, w=2048, h=512, seed=51):
    v = (np.arange(h) + 0.5)[:, None] / h; el = (1 - v / 0.88) * 90  # degrees; v=0.88 is the horizon
    t = np.clip(el / 90, 0, 1)
    if kind == "day":
        zen, mid, hor = np.array([0.16, 0.36, 0.72]), np.array([0.42, 0.62, 0.88]), np.array([0.78, 0.84, 0.90])
    elif kind == "dusk":
        zen, mid, hor = np.array([0.10, 0.12, 0.30]), np.array([0.48, 0.34, 0.50]), np.array([1.0, 0.62, 0.34])
    else:
        zen, mid, hor = np.array([0.006, 0.010, 0.030]), np.array([0.018, 0.030, 0.070]), np.array([0.10, 0.09, 0.10])
    tt = t[..., None]
    col = np.where(tt < 0.18, hor + (mid - hor) * (tt / 0.18), mid + (zen - mid) * ((tt - 0.18) / 0.82) ** 0.8)
    col = np.broadcast_to(col, (h, w, 3)).copy()
    below = (el < 0)[..., 0] if el.ndim == 3 else (el < 0)
    col[np.broadcast_to(below, (h, w))] = (hor * 0.55)
    cl = fbm(h, w, 6, 3, seed)
    if kind in ("day", "dusk"):
        band = np.exp(-((el - 10) / 9) ** 2)  # cumulus near the horizon
        c = np.clip((cl - 0.55) * 4, 0, 1) * band
        cir = np.clip((fbm(h, w, 5, 2, seed + 1) - 0.62) * 3, 0, 1) * np.exp(-((el - 45) / 20) ** 2) * 0.5
        if kind == "day":
            lit = np.array([1.0, 1.0, 1.0]); shade = np.array([0.70, 0.74, 0.80])
        else:
            lit = np.array([1.0, 0.70, 0.55]); shade = np.array([0.42, 0.30, 0.42])
        top = np.clip(np.roll(cl, 3, 0) - cl + 0.5, 0, 1)
        ccol = shade + (lit - shade) * top[..., None]
        col = col * (1 - c[..., None]) + ccol * c[..., None]
        col = col * (1 - cir[..., None]) + lit * cir[..., None]
    else:
        r = np.random.default_rng(seed); st = (r.random((h, w)) > 0.9985) & (el[..., 0] if el.ndim == 3 else el > 15)
        col[st] += 0.5
        glow = np.exp(-np.clip(el, 0, 90) / 6)[..., None] * np.array([0.10, 0.07, 0.04])   # city haze
        col += glow
    col[np.broadcast_to(below, (h, w))] = hor * 0.55 if kind != "night" else np.array([0.03, 0.03, 0.04])
    return _u8(col)


def dot(n=32):
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0)); d = ImageDraw.Draw(im); d.ellipse([1, 1, n - 2, n - 2], fill=(255, 255, 255, 255)); return im
