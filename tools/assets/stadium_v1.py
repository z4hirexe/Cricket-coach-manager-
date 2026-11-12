"""Procedural cricket stadium -> stadium.glb (world: metres, striker stumps z=0, bowler stumps z=20.12, +x off side for RHB)."""
import numpy as np, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from glb import GLB, png_bytes, smooth_normals

RNG = np.random.default_rng(7)
CZ = 10.06            # ground centre (pitch middle)
BOUND = 65.0
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
BRANDS = ["NOVAPAY", "SKYRAIL", "AEROVA", "ZENTRA MOBILE", "PEAKFUEL", "ORBIT BANK", "MONSOON COLA", "TERRAVOLT", "QUILLA TEA", "VEGAAIR"]

# ---------------------------------------------------------------- textures
def tex_grass(n=512):
    base = RNG.normal(0, 1, (n, n)); img = np.zeros((n, n, 3))
    blades = Image.fromarray(((base - base.min()) / np.ptp(base) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6))
    b = np.asarray(blades, float) / 255
    low = Image.fromarray(((RNG.random((n // 16, n // 16))) * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC)
    l = np.asarray(low, float) / 255
    g = 0.78 + 0.22 * b * 0.9 + 0.10 * (l - 0.5)
    img[..., 0] = 0.20 * g + 0.04 * l; img[..., 1] = 0.46 * g; img[..., 2] = 0.14 * g
    col = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    # normal map from height
    h = b * 0.7 + l * 0.3; dx = np.roll(h, -1, 1) - np.roll(h, 1, 1); dy = np.roll(h, -1, 0) - np.roll(h, 1, 0)
    nm = np.stack([-dx * 2.5, -dy * 2.5, np.ones_like(h)], -1); nm /= np.linalg.norm(nm, axis=-1, keepdims=True)
    nrm = Image.fromarray(((nm * 0.5 + 0.5) * 255).astype(np.uint8))
    return col, nrm

def tex_pitch(w=256, h=1536):
    """u across (4.0 m), v along (24.12 m, z=-2 .. 22.12)."""
    nz = RNG.normal(0, 1, (h, w)); img = np.zeros((h, w, 3))
    t = np.asarray(Image.fromarray(((nz - nz.min()) / np.ptp(nz) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8)), float) / 255
    cr = (RNG.random((h, w)) > 0.985).astype(float)
    cracks = np.asarray(Image.fromarray((cr * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2)), float) / 255
    k = 0.85 + 0.2 * t - 0.4 * cracks
    img[..., 0] = 0.74 * k; img[..., 1] = 0.64 * k; img[..., 2] = 0.44 * k
    # edges of the strip fade into grass
    u = (np.arange(w) + 0.5) / w * 4.0 - 2.0; edge = np.clip((np.abs(u) - 1.45) / 0.45, 0, 1)[None, :, None]
    grass = np.array([0.26, 0.46, 0.15]) * (0.9 + 0.1 * t[..., None])
    img = img * (1 - edge) + grass * edge
    # foot marks at both ends (worn rough)
    for zc in (0.0, 20.12):
        for side in (0.3, -0.3):
            vv = ((np.arange(h) + 0.5) / h * 24.12 - 2.0)[:, None]; uu = u[None, :]
            d = ((vv - (zc + (1.6 if zc == 0 else -1.6))) / 0.9) ** 2 + ((uu - side) / 0.35) ** 2
            img *= (1 - 0.18 * np.exp(-d))[..., None]
    pim = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)); d = ImageDraw.Draw(pim)
    px = lambda x: (x + 2.0) / 4.0 * w; pz = lambda z: (z + 2.0) / 24.12 * h; lw = max(2, int(0.05 / 24.12 * h))
    for zs, sgn in ((0.0, 1), (20.12, -1)):
        pop = zs + 1.22 * sgn
        d.rectangle([px(-1.83), pz(pop) - lw / 2, px(1.83), pz(pop) + lw / 2], fill=(245, 245, 240))   # popping crease
        d.rectangle([px(-1.32), pz(zs) - lw / 2, px(1.32), pz(zs) + lw / 2], fill=(245, 245, 240))     # bowling crease
        for x in (-1.32, 1.32):                                                                          # return creases
            a, b = sorted((pz(zs - 1.22 * sgn), pz(pop)))
            d.rectangle([px(x) - lw / 2, a, px(x) + lw / 2, b], fill=(245, 245, 240))
    return pim

def tex_crowd(w=1024, h=256, rows=8, seed=3):
    r = np.random.default_rng(seed); im = Image.new("RGB", (w, h), (40, 42, 48)); d = ImageDraw.Draw(im)
    rh = h / rows; shirt = [(28, 70, 170), (230, 190, 40), (200, 40, 40), (240, 240, 240), (30, 140, 60), (250, 120, 30), (120, 40, 140), (20, 20, 25), (90, 160, 220)]
    skin = [(120, 80, 55), (160, 110, 80), (95, 65, 45), (190, 145, 110), (70, 50, 38)]
    for row in range(rows):
        y0 = row * rh; d.rectangle([0, y0 + rh * 0.78, w, y0 + rh], fill=(55, 58, 64))          # seat row step
        x = r.uniform(0, 6)
        while x < w:
            sw = r.uniform(9, 13)
            if r.random() > 0.08:
                c = shirt[r.integers(len(shirt))] if r.random() > 0.35 else tuple(int(v) for v in r.integers(30, 220, 3))
                d.rounded_rectangle([x, y0 + rh * 0.42, x + sw, y0 + rh * 0.86], 3, fill=c)
                hc = skin[r.integers(len(skin))]; hr = sw * 0.32; cx = x + sw / 2; cy = y0 + rh * 0.30
                d.ellipse([cx - hr, cy - hr, cx + hr, cy + hr * 1.1], fill=hc)
                if r.random() < 0.5: d.chord([cx - hr, cy - hr * 1.1, cx + hr, cy + hr * 0.6], 180, 360, fill=(20, 15, 12))
                if r.random() < 0.06: d.rectangle([x - 2, y0 + rh * 0.05, x + sw + 4, y0 + rh * 0.3], fill=shirt[r.integers(len(shirt))])  # flag/banner
            x += sw + r.uniform(1, 4)
    return im.filter(ImageFilter.GaussianBlur(0.4))

def tex_boards(w=2048, h=64):
    im = Image.new("RGB", (w, h), (10, 12, 18)); d = ImageDraw.Draw(im); f = ImageFont.truetype(FONT, int(h * 0.62))
    cols = [((12, 60, 160), (255, 255, 255)), ((230, 180, 20), (15, 15, 20)), ((180, 20, 30), (255, 255, 255)), ((10, 120, 80), (255, 240, 200)), ((20, 20, 24), (255, 200, 40))]
    n = len(BRANDS); sw = w / n
    for i, b in enumerate(BRANDS):
        bg, fg = cols[i % len(cols)]; d.rectangle([i * sw, 0, (i + 1) * sw, h], fill=bg)
        tw = d.textlength(b, font=f); d.text((i * sw + (sw - tw) / 2, h * 0.16), b, font=f, fill=fg)
    return im

def tex_concrete(n=256):
    nz = RNG.normal(0, 1, (n, n)); t = np.asarray(Image.fromarray(((nz - nz.min()) / np.ptp(nz) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.5)), float) / 255
    v = 0.55 + 0.25 * t; return Image.fromarray((np.stack([v, v, v * 1.02], -1).clip(0, 1) * 255).astype(np.uint8))

# ---------------------------------------------------------------- geometry helpers
def grid_disc(R, rings, segs, cz=CZ):
    v = [(0, 0, cz)]; f = []
    for i in range(1, rings + 1):
        r = R * (i / rings) ** 1.0
        for j in range(segs):
            a = 2 * np.pi * j / segs; v.append((r * np.sin(a), 0, cz + r * np.cos(a)))
    for j in range(segs): f.append((0, 1 + j, 1 + (j + 1) % segs))
    for i in range(1, rings):
        o0 = 1 + (i - 1) * segs; o1 = 1 + i * segs
        for j in range(segs):
            a, b, c, d = o0 + j, o0 + (j + 1) % segs, o1 + j, o1 + (j + 1) % segs
            f += [(a, c, b), (b, c, d)]
    return np.array(v, np.float32), np.array(f, np.int32)

def band(r0, y0, r1, y1, segs, a0=0.0, a1=2 * np.pi, cz=CZ, urep=1.0, vrep=1.0, flip=False):
    """surface between circle (r0,y0) and (r1,y1). returns v,n(placeholder),uv,f"""
    v, uv, f = [], [], []
    for j in range(segs + 1):
        a = a0 + (a1 - a0) * j / segs
        for k, (r, y) in enumerate(((r0, y0), (r1, y1))):
            v.append((r * np.sin(a), y, cz + r * np.cos(a))); uv.append((urep * j / segs, vrep * (1 - k)))
    for j in range(segs):
        a, b, c, d = 2 * j, 2 * j + 1, 2 * j + 2, 2 * j + 3
        f += [(a, c, b), (b, c, d)] if not flip else [(a, b, c), (b, d, c)]
    return np.array(v, np.float32), np.array(uv, np.float32), np.array(f, np.int32)

def cyl(p0, p1, r, segs=12, cap=True):
    p0 = np.asarray(p0, float); p1 = np.asarray(p1, float); ax = p1 - p0; L = np.linalg.norm(ax); ax /= L
    t = np.cross(ax, (0, 0, 1) if abs(ax[2]) < 0.9 else (1, 0, 0)); t /= np.linalg.norm(t); b = np.cross(ax, t)
    v, n, f = [], [], []
    for j in range(segs):
        a = 2 * np.pi * j / segs; d = np.cos(a) * t + np.sin(a) * b
        v += [p0 + r * d, p1 + r * d]; n += [d, d]
    for j in range(segs):
        a, b2, c, d = 2 * j, 2 * j + 1, 2 * ((j + 1) % segs), 2 * ((j + 1) % segs) + 1
        f += [(a, c, b2), (b2, c, d)]
    if cap:
        i0 = len(v); v.append(p1); n.append(ax)
        for j in range(segs): f.append((i0, 2 * j + 1, 2 * ((j + 1) % segs) + 1))
    return np.array(v, np.float32), np.array(n, np.float32), np.array(f, np.int32)

def boxm(c, hs, rot_y=0.0):
    c = np.asarray(c, float); hs = np.asarray(hs, float); v, n, uv, f = [], [], [], []
    cs, sn = np.cos(rot_y), np.sin(rot_y); R = np.array([[cs, 0, sn], [0, 1, 0], [-sn, 0, cs]])
    for ax in range(3):
        for s in (-1, 1):
            nn = np.zeros(3); nn[ax] = s; u = np.zeros(3); u[(ax + 1) % 3] = 1; w = np.cross(nn, u)
            base = len(v)
            for (a, b) in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
                v.append(c + R @ ((nn + a * u + b * w) * hs)); n.append(R @ nn); uv.append(((a + 1) / 2, (1 - b) / 2))
            f += [(base, base + 1, base + 2), (base, base + 2, base + 3)]
    return np.array(v, np.float32), np.array(n, np.float32), np.array(uv, np.float32), np.array(f, np.int32)

def sphere(c, r, seg=16, ring=10):
    v, n, f = [], [], []
    for i in range(ring + 1):
        th = np.pi * i / ring
        for j in range(seg + 1):
            ph = 2 * np.pi * j / seg; d = np.array((np.sin(th) * np.cos(ph), np.cos(th), np.sin(th) * np.sin(ph)))
            v.append(np.asarray(c) + r * d); n.append(d)
    for i in range(ring):
        for j in range(seg):
            a = i * (seg + 1) + j; b = a + seg + 1
            f += [(a, a + 1, b), (a + 1, b + 1, b)]
    return np.array(v, np.float32), np.array(n, np.float32), np.array(f, np.int32)

def merge(parts):
    V, N, UV, F, o = [], [], [], [], 0
    for p in parts:
        v, n, uv, f = p; V.append(v); N.append(n); UV.append(uv if uv is not None else np.zeros((len(v), 2), np.float32)); F.append(f + o); o += len(v)
    return np.concatenate(V), np.concatenate(N), np.concatenate(UV), np.concatenate(F)

# ---------------------------------------------------------------- build
def build(out):
    g = GLB(); stats = {}
    t_grass, t_grassN = tex_grass(); gi = g.texture(png_bytes(t_grass)); gn = g.texture(png_bytes(t_grassN))
    pi = g.texture(png_bytes(tex_pitch()), repeat=False)
    ci = [g.texture(png_bytes(tex_crowd(seed=s))) for s in (3, 11)]
    bi = g.texture(png_bytes(tex_boards())); coni = g.texture(png_bytes(tex_concrete()))
    M = dict(grass=g.material("grass", (1, 1, 1, 1), 0, 0.92, tex=gi, normal=gn), pitch=g.material("pitch", (1, 1, 1, 1), 0, 0.85, tex=pi),
             crowd0=g.material("crowd_a", (1, 1, 1, 1), 0, 0.9, tex=ci[0]), crowd1=g.material("crowd_b", (1, 1, 1, 1), 0, 0.9, tex=ci[1]),
             boards=g.material("ad_boards", (1, 1, 1, 1), 0, 0.4, tex=bi, emissive=(0.25, 0.25, 0.25)), concrete=g.material("concrete", (1, 1, 1, 1), 0, 0.9, tex=coni),
             rope=g.material("rope", (0.95, 0.95, 0.95, 1), 0, 0.6), white=g.material("sight_screen", (0.93, 0.93, 0.93, 1), 0, 0.7),
             roof=g.material("roof", (0.55, 0.58, 0.62, 1), 0.7, 0.35, double=True), steel=g.material("steel", (0.62, 0.64, 0.67, 1), 0.85, 0.3),
             lamp=g.material("flood_lamp", (1, 1, 1, 1), 0, 0.2, emissive=(1.0, 0.97, 0.9)), stump=g.material("stump", (0.93, 0.88, 0.75, 1), 0, 0.45),
             ball=g.material("ball", (0.62, 0.06, 0.06, 1), 0, 0.3), seam=g.material("seam", (0.95, 0.92, 0.85, 1), 0, 0.5),
             glass=g.material("pavilion_glass", (0.12, 0.16, 0.2, 1), 0.6, 0.12), screen=g.material("big_screen", (0.02, 0.02, 0.03, 1), 0.0, 0.3, emissive=(0.05, 0.12, 0.3)),
             outfield_edge=g.material("apron", (0.18, 0.30, 0.12, 1), 0, 0.95), seats=g.material("seats", (0.35, 0.37, 0.42, 1), 0, 0.7))
    nodes = []
    def node(name, prims, t=None):
        i = g.node(name, mesh=g.mesh(name, prims), t=t); nodes.append(i); return i
    def prim(v, n, uv, f, mat, col=None):
        stats[mat] = stats.get(mat, 0) + len(f); return g.primitive(v, n, f, M[mat], uv=uv, color=col)
    # outfield (vertex-colour mowing stripes + wear), uv tiled every 4 m
    v, f = grid_disc(BOUND + 6, 48, 96); n = np.tile([0, 1, 0], (len(v), 1)).astype(np.float32); uv = (v[:, [0, 2]] / 4.0).astype(np.float32)
    stripe = (np.floor((v[:, 2] - CZ + 200) / 6.0) % 2); rr = np.hypot(v[:, 0], v[:, 2] - CZ)
    shade = 0.86 + 0.14 * stripe; shade *= 1 - 0.10 * np.clip((rr - BOUND + 2) / 6, 0, 1)
    wear = np.exp(-((v[:, 0] / 3.5) ** 2 + ((v[:, 2] - CZ) / 16) ** 2)); shade *= 1 - 0.12 * wear
    col = np.stack([shade, shade, shade, np.ones_like(shade)], 1).astype(np.float32)
    node("outfield", [prim(v, n, uv, f, "grass", col)])
    # pitch strip (slightly raised, separate texture)
    pv = np.array([(-2.0, 0.006, -2.0), (2.0, 0.006, -2.0), (2.0, 0.006, 22.12), (-2.0, 0.006, 22.12)], np.float32)
    puv = np.array([(0, 0), (1, 0), (1, 1), (0, 1)], np.float32); pf = np.array([(0, 2, 1), (0, 3, 2)], np.int32)
    node("pitch", [prim(pv, np.tile([0, 1, 0], (4, 1)).astype(np.float32), puv, pf, "pitch")])
    # boundary rope (tube) + apron beyond rope
    segs = 220; rope = []
    for j in range(segs):
        a0 = 2 * np.pi * j / segs; a1 = 2 * np.pi * (j + 1) / segs
        rope.append(cyl((BOUND * np.sin(a0), 0.05, CZ + BOUND * np.cos(a0)), (BOUND * np.sin(a1), 0.05, CZ + BOUND * np.cos(a1)), 0.05, 6, cap=False))
    rv = np.concatenate([r[0] for r in rope]); rn = np.concatenate([r[1] for r in rope]); rf = np.concatenate([r[2] + 12 * i for i, r in enumerate(rope)])
    node("boundary_rope", [prim(rv, rn, None, rf, "rope")])
    # advertising boards
    v, uv, f = band(BOUND + 3.0, 0.0, BOUND + 3.0, 0.95, 160, urep=-4.0, flip=True); n = -np.c_[v[:, 0], np.zeros(len(v)), v[:, 2] - CZ]; n /= np.linalg.norm(n, axis=1, keepdims=True)
    node("ad_boards", [prim(v, n.astype(np.float32), uv, f, "boards")])
    # sight screens at both ends (behind bowler's arm), on the stand wall line
    ss = []
    for zc in (CZ - (BOUND + 5.5), CZ + (BOUND + 5.5)):
        bv, bn, buv, bf = boxm((0, 4.5, zc), (9.0, 4.5, 0.25)); ss.append((bv, bn, buv, bf))
    node("sight_screens", [prim(*merge(ss)[:2], merge(ss)[2], merge(ss)[3], "white")])
    # stands: front wall + 2 tiers of crowd + concourse + upper tier + roof
    R0 = BOUND + 6.0; parts_c0, parts_c1, parts_con, parts_seat = [], [], [], []
    gap = 0.16  # angular gap for sight screens at both ends
    arcs = [(gap, np.pi - gap), (np.pi + gap, 2 * np.pi - gap)]
    for (a0, a1) in arcs:
        def add(lst, r0, y0, r1, y1, segs, urep, vrep, flip=False):
            v, uv, f = band(r0, y0, r1, y1, segs, a0, a1, urep=urep, vrep=vrep, flip=flip); lst.append((v, None, uv, f))
        add(parts_con, R0, 0, R0, 2.4, 80, 40, 1, flip=True)                     # front wall
        add(parts_c0, R0, 2.4, R0 + 22, 13.0, 120, 70, 3, flip=True)              # lower tier crowd
        add(parts_con, R0 + 22, 13.0, R0 + 22, 15.5, 80, 40, 1, flip=True)        # tier fascia
        add(parts_con, R0 + 22, 15.5, R0 + 25, 15.5, 80, 40, 1, flip=True)        # concourse
        add(parts_c1, R0 + 25, 15.5, R0 + 43, 30.0, 120, 70, 3, flip=True)        # upper tier crowd
        add(parts_con, R0 + 43, 30.0, R0 + 43, 0.0, 80, 40, 1)                    # back wall (outside)
    def finalize(parts):
        v, _, uv, f = merge([(p[0], np.zeros_like(p[0]), p[2], p[3]) for p in parts]); n = smooth_normals(v, f); return v, n, uv, f
    node("stands", [prim(*finalize(parts_c0), "crowd0"), prim(*finalize(parts_c1), "crowd1"), prim(*finalize(parts_con), "concrete")])
    roof = []
    for (a0, a1) in arcs:
        v, uv, f = band(R0 + 18, 38.0, R0 + 46, 34.0, 100, a0, a1, urep=30, vrep=1); roof.append((v, None, uv, f))
        for k in range(14):  # roof trusses / columns
            a = a0 + (a1 - a0) * (k + 0.5) / 14; s, c = np.sin(a), np.cos(a)
            cv, cn, cf = cyl(((R0 + 44) * s, 0, CZ + (R0 + 44) * c), ((R0 + 44) * s, 34.5, CZ + (R0 + 44) * c), 0.5, 8)
            roof.append((cv, cn, None, cf))
    rv, rn, ruv, rf = merge([(p[0], p[1] if p[1] is not None else np.zeros_like(p[0]), p[2], p[3]) for p in roof]); rn = smooth_normals(rv, rf)
    node("roof", [prim(rv, rn, ruv, rf, "roof")])
    # pavilion (far end, behind bowler) and scoreboard block (near end) filling the sight-screen gaps
    pav, gl, scr = [], [], []
    zf = CZ + R0 + 14; pav.append(boxm((0, 9, zf), (13, 9, 10))); pav.append(boxm((0, 19.5, zf + 2), (14.5, 1.5, 12)))
    for k in range(3): gl.append(boxm((0, 4.5 + k * 5, zf - 10.05), (12, 1.7, 0.1)))
    zn = CZ - R0 - 14; pav.append(boxm((0, 12, zn), (13, 12, 8)))
    scr.append(boxm((0, 16, zn + 8.1), (11, 5.5, 0.1)))
    node("pavilion", [prim(*merge(pav), "concrete"), prim(*merge(gl), "glass"), prim(*merge(scr), "screen")])
    # floodlight towers (4, on diagonals) with lamp heads aimed at the centre
    steel, lamps = [], []
    for k in range(4):
        a = np.pi / 4 + k * np.pi / 2; s, c = np.sin(a), np.cos(a); rr_ = R0 + 50; base = np.array((rr_ * s, 0, CZ + rr_ * c)); top = base + (0, 52, 0)
        steel.append(cyl(base, top, 1.1, 10)); steel.append(cyl(base + (0, 0, 0), top + (0, 0, 0), 0.0001, 3))
        yaw = np.arctan2(-s, -c)
        hv, hn, huv, hf = boxm(top + np.array((0, 3.5, 0)) + 1.0 * np.array((-s, 0, -c)), (6.5, 4.0, 0.5), rot_y=yaw)
        steel.append((hv, hn, hf))
        lv, ln, luv, lf = boxm(top + np.array((0, 3.5, 0)) + 1.6 * np.array((-s, 0, -c)), (6.0, 3.6, 0.12), rot_y=yaw); lamps.append((lv, ln, luv, lf))
    sv, sn, suv, sf = merge([(p[0], p[1], None, p[2]) for p in steel])
    lv, ln, luv, lf = merge(lamps)
    node("floodlight_towers", [prim(sv, sn, None, sf, "steel")]); node("floodlight_lamps", [prim(lv, ln, luv, lf, "lamp")])
    # stumps & bails (separate nodes so they can be animated)
    for end, zc in (("bat", 0.0), ("bowl", 20.12)):
        kids = []
        for i, x in enumerate((-0.1143 + 0.019, 0.0, 0.1143 - 0.019)):
            v, n, f = cyl((0, 0, 0), (0, 0.711, 0), 0.019, 10)
            kids.append(g.node(f"stump_{end}_{i}", mesh=g.mesh(f"stump_{end}_{i}", [prim(v, n, None, f, "stump")]), t=(x, 0, zc)))
        for i, x in enumerate((-0.054, 0.054)):
            v, n, f = cyl((-0.054, 0, 0), (0.054, 0, 0), 0.007, 8)
            kids.append(g.node(f"bail_{end}_{i}", mesh=g.mesh(f"bail_{end}_{i}", [prim(v, n, None, f, "stump")]), t=(x, 0.718, zc)))
        nodes.append(g.node(f"stumps_{end}", children=kids))
    # ball
    bv, bn, bf = sphere((0, 0, 0), 0.036, 18, 12); sv_, sn_, sf_ = cyl((-0.0362, 0, 0), (0.0362, 0, 0), 0.0, 3, cap=False)
    ring = []
    for j in range(24):
        a0 = 2 * np.pi * j / 24; a1 = 2 * np.pi * (j + 1) / 24
        ring.append(cyl((0, 0.0362 * np.cos(a0), 0.0362 * np.sin(a0)), (0, 0.0362 * np.cos(a1), 0.0362 * np.sin(a1)), 0.0025, 4, cap=False))
    rv = np.concatenate([r[0] for r in ring]); rn = np.concatenate([r[1] for r in ring]); rf = np.concatenate([r[2] + 8 * i for i, r in enumerate(ring)])
    nodes.append(g.node("ball", mesh=g.mesh("ball", [prim(bv, bn, None, bf, "ball"), prim(rv, rn, None, rf, "seam")]), t=(0, 0.036, 18.0)))
    # sky domes (unlit, vertex-coloured gradients) - toggled at runtime for day / day-night / night
    skies = {"sky_day": ((0.20, 0.42, 0.78), (0.66, 0.80, 0.93), (0.82, 0.86, 0.88)),
             "sky_dusk": ((0.10, 0.14, 0.34), (0.86, 0.46, 0.26), (0.98, 0.70, 0.42)),
             "sky_night": ((0.004, 0.008, 0.025), (0.02, 0.04, 0.09), (0.06, 0.08, 0.13))}
    for name, (zen, mid, hor) in skies.items():
        mat = g.material(name, (1, 1, 1, 1), 0, 1, unlit=True, double=True)
        v, n, f = sphere((0, -20, CZ), 420.0, 32, 16); el = np.clip((v[:, 1] + 20) / 420.0, -1, 1)
        t = np.clip(el, 0, 1) ** 0.6; c = np.where(t[:, None] < 0.25, np.array(hor) + (np.array(mid) - np.array(hor)) * (t[:, None] / 0.25),
                                                   np.array(mid) + (np.array(zen) - np.array(mid)) * ((t[:, None] - 0.25) / 0.75))
        if name == "sky_day":   # soft procedural clouds
            ang = np.arctan2(v[:, 0], v[:, 2] - CZ); cl = np.clip(np.sin(ang * 7) * np.sin(ang * 3 + 1) * np.sin(t * 14), 0, 1) * (t > 0.05) * (t < 0.6)
            c = c + cl[:, None] * 0.25
        if name == "sky_night":
            st = (RNG.random(len(v)) > 0.93) * (t > 0.3) * 0.6; c = c + st[:, None]
        col = np.c_[np.clip(c, 0, 1.5), np.ones(len(v))].astype(np.float32)
        nodes.append(g.node(name, mesh=g.mesh(name, [g.primitive(v, -n, f[:, ::-1].copy(), mat, color=col)]))); stats[name] = len(f)
    g.node("stadium", children=nodes, root=True)
    size = g.save(out); return size, stats

if __name__ == "__main__":
    size, stats = build(sys.argv[1]); print("stadium.glb", size, "bytes; triangles:", stats, "total", sum(stats.values()))
