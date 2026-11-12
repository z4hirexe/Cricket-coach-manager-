"""Procedural cricket stadium v2 -> stadium.glb  ("CMP Arena": realistic broadcast-style oval).
World: metres, striker stumps z=0, bowler stumps z=20.12, y up, ground centre (0, 0, CZ).
Angle a (radians) is measured around the ground centre from +z (behind the bowler) towards +x.
Node / material names used by the app (Arena3D.kt) are kept: outfield, pitch, pitch_wear, boundary_rope, ad_boards, stands, roof,
pavilion, floodlight_towers, floodlight_lamps, stump_<end>_<i>, bail_<end>_<i>, ball, sky_day/sky_dusk/sky_night;
materials flood_lamp, ad_boards, ball, sight_screen, pitch_wear, ..."""
import numpy as np, sys, json
from glb import GLB, smooth_normals
import stadium_tex as T

RNG = np.random.default_rng(7)
CZ = 10.06
BX, BZ = 63.0, 69.0          # boundary (rope) semi-axes: square / straight
PAV_GAP = 0.40               # half-angle of the pavilion opening at the near end (behind the striker, a = pi)
SS_GAP = 0.15                # half-angle of the lower-tier opening behind the far sight screen (a = 0)


def ring(d, a):
    """point on the boundary ellipse grown outward by d metres (approx. offset curve)"""
    return np.array([(BX + d) * np.sin(a), (BZ + d) * np.cos(a) + CZ])


def bdist(a):
    return 1.0 / np.sqrt((np.sin(a) / BX) ** 2 + (np.cos(a) / BZ) ** 2)


def outward(a):
    p = np.array([np.sin(a) / BX, np.cos(a) / BZ]); return p / np.linalg.norm(p)


class Mesh:
    def __init__(self): self.v, self.n, self.uv, self.f, self.c = [], [], [], [], []; self.o = 0
    def add(self, v, f, uv=None, n=None, c=None):
        v = np.asarray(v, np.float32); f = np.asarray(f, np.int32)
        self.v.append(v); self.f.append(f + self.o); self.o += len(v)
        self.uv.append(np.zeros((len(v), 2), np.float32) if uv is None else np.asarray(uv, np.float32))
        self.n.append(None if n is None else np.asarray(n, np.float32))
        self.c.append(np.ones((len(v), 4), np.float32) if c is None else np.asarray(c, np.float32))
    def quad(self, p0, p1, p2, p3, uv=((0, 1), (1, 1), (1, 0), (0, 0)), c=None):
        """p0..p3 counter-clockwise when seen from the front"""
        self.add([p0, p1, p2, p3], [(0, 1, 2), (0, 2, 3)], uv, c=c)
    def empty(self): return self.o == 0
    def build(self):
        v = np.concatenate(self.v); f = np.concatenate(self.f); uv = np.concatenate(self.uv); c = np.concatenate(self.c)
        sn = smooth_normals(v, f) if any(n is None for n in self.n) else None
        offs = np.cumsum([0] + [len(x) for x in self.v])[:-1]
        n = np.concatenate([nn if nn is not None else sn[o:o + len(vv)] for nn, vv, o in zip(self.n, self.v, offs)])
        return v, n.astype(np.float32), uv, f, c


def box(m, c, hs, yaw=0.0, uvscale=None):
    c = np.asarray(c, float); hs = np.asarray(hs, float); cs, sn = np.cos(yaw), np.sin(yaw); R = np.array([[cs, 0, sn], [0, 1, 0], [-sn, 0, cs]])
    for ax in range(3):
        for s in (-1, 1):
            nn = np.zeros(3); nn[ax] = s; u = np.zeros(3); u[(ax + 1) % 3] = 1; w = np.cross(nn, u)
            P = [c + R @ ((nn + a * u + b * w) * hs) for a, b in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
            if uvscale: su = 2 * hs[(ax + 1) % 3] / uvscale; sv = 2 * hs[(ax + 2) % 3] / uvscale; uv = ((0, 0), (su, 0), (su, sv), (0, sv))
            else: uv = ((0, 0), (1, 0), (1, 1), (0, 1))
            m.add(P, [(0, 1, 2), (0, 2, 3)], uv, n=[R @ nn] * 4)


def cyl(m, p0, p1, r0, r1=None, segs=12, cap=True):
    r1 = r0 if r1 is None else r1
    p0 = np.asarray(p0, float); p1 = np.asarray(p1, float); ax = p1 - p0; L = np.linalg.norm(ax); ax /= L
    t = np.cross(ax, (0, 0, 1) if abs(ax[2]) < 0.9 else (1, 0, 0)); t /= np.linalg.norm(t); b = np.cross(ax, t)
    v, n, f, uv = [], [], [], []
    for j in range(segs + 1):
        a = 2 * np.pi * j / segs; d = np.cos(a) * t + np.sin(a) * b
        v += [p0 + r0 * d, p1 + r1 * d]; n += [d, d]; uv += [(j / segs, 0), (j / segs, L / 4)]
    for j in range(segs): f += [(2 * j, 2 * j + 2, 2 * j + 1), (2 * j + 1, 2 * j + 2, 2 * j + 3)]
    if cap:
        i0 = len(v); v.append(p1); n.append(ax); uv.append((0.5, 0.5))
        for j in range(segs): f.append((i0, 2 * j + 1, 2 * j + 3))
    m.add(v, f, uv, n=n)


def sphere_uv(c, r, seg=24, ring_=14):
    v, n, f, t = [], [], [], []
    for i in range(ring_ + 1):
        th = np.pi * i / ring_
        for j in range(seg + 1):
            ph = 2 * np.pi * j / seg; d = np.array((np.sin(th) * np.sin(ph), np.cos(th), np.sin(th) * np.cos(ph)))
            v.append(np.asarray(c) + r * d); n.append(d); t.append((j / seg, i / ring_))
    for i in range(ring_):
        for j in range(seg):
            a = i * (seg + 1) + j; b = a + seg + 1
            f += [(a, b, a + 1), (a + 1, b, b + 1)]
    return np.array(v, np.float32), np.array(n, np.float32), np.array(f, np.int32), np.array(t, np.float32)


def arc_band(m, d0, y0, d1, y1, a0, a1, segs, u_len, v0=0.0, v1=1.0, c=None):
    """surface between offset-ellipse curves (d0,y0) and (d1,y1) over [a0,a1]; u advances by arc length / u_len.
    (all stadium materials are two-sided, normals follow the winding)"""
    v, uv, f, s = [], [], [], 0.0
    prev = None
    for j in range(segs + 1):
        a = a0 + (a1 - a0) * j / segs; p0 = ring(d0, a); p1 = ring(d1, a)
        if prev is not None: s += np.linalg.norm((p0 + p1) / 2 - prev)
        prev = (p0 + p1) / 2
        v += [(p0[0], y0, p0[1]), (p1[0], y1, p1[1])]; uv += [(-s / u_len, v1), (-s / u_len, v0)]   # u runs left->right seen from the field
    for j in range(segs):
        a, b, cc, d = 2 * j, 2 * j + 1, 2 * j + 2, 2 * j + 3
        f += [(a, b, cc), (b, d, cc)]
    m.add(v, f, uv, c=None if c is None else np.tile(c, (len(v), 1)))


def arc_ranges(gaps):
    """split [0, 2pi) into arcs avoiding the given (centre, half-width) gaps"""
    N = 2000; A = np.linspace(0, 2 * np.pi, N, endpoint=False); ok = np.ones(N, bool)
    for cn, hw in gaps:
        dd = np.abs((A - cn + np.pi) % (2 * np.pi) - np.pi); ok &= dd > hw
    out = []
    starts = [k for k in range(N) if ok[k] and not ok[k - 1]]
    for s in starts:
        e = s
        while ok[(e + 1) % N] and (e + 1) % N != s: e += 1
        out.append((A[s], A[s] + (e - s + 1) * 2 * np.pi / N))
    if not starts and ok.all(): out = [(0, 2 * np.pi)]
    return out


# ================================================================== build
def build(out, meta_out=None):
    g = GLB(); stats = {}; nodes = []
    TX = {}
    def tex(name, img, jpeg=True, repeat=True, q=86):
        TX[name] = g.texture(img, repeat=repeat, jpeg=jpeg, quality=q); return TX[name]
    gc, gn = T.grass(); tex("grass", gc); tex("grassN", gn, q=90)
    pc, pn = T.pitch(); tex("pitch", pc, q=90); tex("pitchN", pn, q=90)
    tex("wear", T.pitch_wear(), jpeg=False)
    tex("crowd", T.crowd_atlas(), q=84)
    tex("boards", T.ad_boards(), q=88); tex("ribbon", T.ribbon(), q=88)
    tex("facade", T.facade()); tex("facadeE", T.facade_emissive())
    tex("vip", T.vip_glass()); tex("pav", T.pavilion_front(), q=88); tex("pavE", T.pavilion_emissive())
    tex("roof", T.roof_membrane()); tex("lamp", T.lamp_bank()); tex("screen", T.big_screen(), q=90)
    tex("concrete", T.concrete()); tex("plaza", T.plaza())
    cw, ce = T.city_windows(); tex("city", cw); tex("cityE", ce)
    for k in ("day", "dusk", "night"): tex("sky_" + k, T.sky(k), repeat=False, q=90)
    tex("dot", T.dot(), jpeg=False)

    M = {}
    def mat(name, *a, **k):
        k.setdefault("double", True)   # two-sided lighting: normals follow winding, Filament flips them for back faces
        M[name] = g.material(name, *a, **k); return M[name]
    mat("grass", (1, 1, 1, 1), 0, 0.92, tex=TX["grass"], normal=TX["grassN"])
    mat("pitch", (1, 1, 1, 1), 0, 0.85, tex=TX["pitch"], normal=TX["pitchN"])
    mat("pitch_wear", (1, 1, 1, 1), 0, 0.95, tex=TX["wear"], blend=True)
    mat("marking", (0.95, 0.95, 0.93, 1), 0, 0.7, tex=TX["dot"], blend=True)
    mat("rope", (1, 1, 1, 1), 0, 0.6, tex=TX["ribbon"], emissive=(0.25, 0.25, 0.25), emissive_tex=TX["ribbon"])
    mat("ad_boards", (1, 1, 1, 1), 0, 0.35, tex=TX["boards"], emissive=(0.85, 0.85, 0.85), emissive_tex=TX["boards"])
    mat("ribbon", (1, 1, 1, 1), 0, 0.35, tex=TX["ribbon"], emissive=(0.9, 0.9, 0.9), emissive_tex=TX["ribbon"])
    mat("board_frame", (0.05, 0.05, 0.06, 1), 0.3, 0.5)
    mat("crowd", (1, 1, 1, 1), 0, 0.85, tex=TX["crowd"])
    mat("concrete", (0.92, 0.92, 0.94, 1), 0, 0.9, tex=TX["concrete"])
    mat("vip_glass", (1, 1, 1, 1), 0.2, 0.15, tex=TX["vip"], emissive=(0.35, 0.3, 0.25), emissive_tex=TX["vip"])
    mat("facade", (1, 1, 1, 1), 0.1, 0.5, tex=TX["facade"], emissive=(1.0, 1.0, 1.0), emissive_tex=TX["facadeE"])
    mat("pavilion_front", (1, 1, 1, 1), 0, 0.6, tex=TX["pav"], emissive=(0.25, 0.22, 0.18), emissive_tex=TX["pavE"])
    mat("stone", (0.88, 0.85, 0.78, 1), 0, 0.8, tex=TX["concrete"])
    mat("gold", (0.85, 0.64, 0.25, 1), 1.0, 0.32)
    mat("roof", (0.80, 0.82, 0.85, 1), 0.05, 0.5, tex=TX["roof"])
    mat("steel", (0.55, 0.57, 0.6, 1), 0.85, 0.35)
    mat("dark_steel", (0.12, 0.13, 0.15, 1), 0.7, 0.45)
    mat("pavilion_glass", (0.10, 0.14, 0.18, 1), 0.7, 0.1)
    mat("flood_lamp", (1, 1, 1, 1), 0, 0.2, tex=TX["lamp"], emissive=(1.0, 0.97, 0.9), emissive_tex=TX["lamp"])
    mat("roof_led", (1, 1, 1, 1), 0, 0.3, emissive=(1.0, 0.95, 0.85))
    mat("big_screen", (1, 1, 1, 1), 0, 0.3, tex=TX["screen"], emissive=(1.0, 1.0, 1.0), emissive_tex=TX["screen"])
    mat("sight_screen", (0.95, 0.95, 0.95, 1), 0, 0.8)
    mat("stump", (0.93, 0.88, 0.74, 1), 0, 0.45); mat("ball", (0.62, 0.06, 0.06, 1), 0, 0.3); mat("seam", (0.95, 0.92, 0.85, 1), 0, 0.5)
    mat("plaza", (0.62, 0.62, 0.62, 1), 0, 0.9, tex=TX["plaza"])
    mat("ground_far", (0.55, 0.6, 0.5, 1), 0, 0.95, tex=TX["grass"])
    mat("tree", (0.16, 0.30, 0.12, 1), 0, 0.9); mat("trunk", (0.30, 0.22, 0.15, 1), 0, 0.9)
    mat("city", (1, 1, 1, 1), 0.2, 0.6, tex=TX["city"], emissive=(1.0, 1.0, 1.0), emissive_tex=TX["cityE"])
    mat("flag", (1, 1, 1, 1), 0, 0.8)
    mat("perspex", (0.55, 0.62, 0.70, 1), 0.3, 0.1)

    def node(name, parts, t=None):
        prims = []
        for mname, m in parts:
            if m.empty(): continue
            v, n, uv, f, c = m.build(); stats[name + ":" + mname] = len(f)
            prims.append(g.primitive(v, n, f, M[mname], uv=uv, color=c if not np.allclose(c, 1) else None))
        i = g.node(name, mesh=g.mesh(name, prims), t=t); nodes.append(i); return i

    # ---------------------------------------------------------------- outfield: 2 m cells, crisp chequered mowing, low-freq variation
    nz = T.fbm(256, 256, 4, 3, 99)
    def lowf(x, z):
        i = ((z - CZ + 90) / 180 * 255).clip(0, 255).astype(int); j = ((x + 90) / 180 * 255).clip(0, 255).astype(int); return nz[i, j]
    of = Mesh(); cs = 2.0
    xs = np.arange(-84, 84, cs); zs = np.arange(CZ - 90, CZ + 90, cs)
    V, F, UVs, C = [], [], [], []
    for z0 in zs:
        for x0 in xs:
            xc, zc = x0 + cs / 2, z0 + cs / 2; a = np.arctan2(xc, zc - CZ); r = np.hypot(xc, zc - CZ); d = r - bdist(a)
            if d > 10: continue
            P = np.array([(x0, 0, z0), (x0 + cs, 0, z0), (x0 + cs, 0, z0 + cs), (x0, 0, z0 + cs)], float)
            if d < 0:
                band_z = int(np.floor((zc - CZ + 300) / 6.0)) % 2; band_x = int(np.floor((xc + 300) / 6.0)) % 2
                shade = 0.84 + 0.16 * band_z + 0.04 * band_x
                ring_ = np.clip((d + 3) / 3, 0, 1); shade *= 1 - 0.05 * ring_
                inner = np.exp(-((xc / 30) ** 2 + ((zc - CZ) / 36) ** 2)); shade *= 1 - 0.035 * inner
            else:
                shade = 0.80 - 0.03 * np.clip(d / 8, 0, 1)
            lf = 0.92 + 0.16 * lowf(P[:, 0], P[:, 2])
            ru = np.exp(-((P[:, 0] / 2.2) ** 2)) * (np.exp(-(((P[:, 2] - 30) / 7) ** 2)) + np.exp(-(((P[:, 2] + 10) / 7) ** 2)))
            col = np.zeros((4, 4)); col[:, 0] = shade * lf * (1 + 0.25 * ru); col[:, 1] = shade * lf * (1 - 0.12 * ru); col[:, 2] = shade * lf * (1 - 0.2 * ru); col[:, 3] = 1
            o = len(V); V += list(P); UVs += list(P[:, [0, 2]] / 3.0); C += list(col); F += [(o, o + 2, o + 1), (o, o + 3, o + 2)]
    of.add(V, F, UVs, n=[(0, 1, 0)] * len(V), c=C)
    node("outfield", [("grass", of)])

    # ---------------------------------------------------------------- pitch square (9 strips of 3.05 m, alternate shades, two used strips)
    sq = Mesh()
    for k in range(-4, 5):
        if k == 0: continue
        x0 = k * 3.05 - 1.525; shade = 1.02 if k % 2 else 0.95
        used = k in (-2, 3)
        for s in range(16):
            z0 = -3.6 + s * (27.3 / 16); z1 = z0 + 27.3 / 16
            P = [(x0, 0.004, z0), (x0 + 3.05, 0.004, z0), (x0 + 3.05, 0.004, z1), (x0, 0.004, z1)]
            col = []
            for p in P:
                w = 0.0
                if used: w = max(np.exp(-((p[2] - 1.2) / 1.8) ** 2), np.exp(-((p[2] - 18.9) / 1.8) ** 2)) * 0.55
                col.append((shade * (1 + 0.9 * w), shade * (1 - 0.15 * w), shade * (1 - 0.45 * w), 1))
            sq.add(P, [(0, 2, 1), (0, 3, 2)], [(p[0] / 3.0, p[2] / 3.0) for p in P], n=[(0, 1, 0)] * 4, c=col)
    node("pitch_square", [("grass", sq)])

    # ---------------------------------------------------------------- pitch strip + dynamic wear decal
    def strip_grid(y, nx=6, nzs=48):
        V, UV, F = [], [], []
        for i in range(nzs + 1):
            for j in range(nx + 1):
                x = -1.8 + 3.6 * j / nx; z = -2.4 + 24.92 * i / nzs; V.append((x, y, z)); UV.append(((x + 1.8) / 3.6, (z + 2.4) / 24.92))
        for i in range(nzs):
            for j in range(nx):
                a = i * (nx + 1) + j; b = a + 1; c = a + nx + 1; d = c + 1; F += [(a, c, b), (b, c, d)]
        return V, UV, F
    pm = Mesh(); V, UV, F = strip_grid(0.008); pm.add(V, F, UV, n=[(0, 1, 0)] * len(V)); node("pitch", [("pitch", pm)])
    wm = Mesh(); V, UV, F = strip_grid(0.011, 4, 32); wm.add(V, F, UV, n=[(0, 1, 0)] * len(V)); node("pitch_wear", [("pitch_wear", wm)])

    # ---------------------------------------------------------------- field markings: 30-yard (27.43 m) inner circle as painted discs
    mk = Mesh(); R30 = 27.43; pts = []
    for zc, a0, a1 in ((0.0, np.pi / 2, 3 * np.pi / 2), (20.12, -np.pi / 2, np.pi / 2)):
        L = R30 * np.pi; n = int(L / 2.0)
        for i in range(n + 1):
            a = a0 + (a1 - a0) * i / n; pts.append((R30 * np.sin(a), zc + R30 * np.cos(a)))
    for x in (-R30, R30):
        for i in range(1, 10): pts.append((x, 20.12 * i / 10))
    for (x, z) in pts:
        r = 0.16; mk.add([(x - r, 0.012, z - r), (x + r, 0.012, z - r), (x + r, 0.012, z + r), (x - r, 0.012, z + r)], [(0, 2, 1), (0, 3, 2)],
                         [(0, 0), (1, 0), (1, 1), (0, 1)], n=[(0, 1, 0)] * 4)
    node("field_markings", [("marking", mk)])

    # ---------------------------------------------------------------- boundary cushion (triangular, branded) at the rope line
    rp = Mesh()
    arc_band(rp, -0.22, 0.0, 0.0, 0.22, 0, 2 * np.pi, 420, 9.0, 0.15, 0.85)
    arc_band(rp, 0.0, 0.22, 0.22, 0.0, 0, 2 * np.pi, 420, 9.0, 0.15, 0.85)
    node("boundary_rope", [("rope", rp)])

    # ---------------------------------------------------------------- LED boards (slanted) + frames, d = 4
    bd = Mesh(); fr = Mesh()
    arc_band(bd, 4.0, 0.05, 4.25, 0.95, 0, 2 * np.pi, 360, 72.0, 0, 1)
    arc_band(fr, 4.25, 0.95, 4.6, 0.0, 0, 2 * np.pi, 180, 10.0)
    arc_band(fr, 4.0, 0.95, 4.25, 0.95, 0, 2 * np.pi, 180, 10.0)
    node("ad_boards", [("ad_boards", bd), ("board_frame", fr)])

    # ---------------------------------------------------------------- stands
    gaps_low = [(np.pi, PAV_GAP), (0.0, SS_GAP)]; gaps_up = [(np.pi, PAV_GAP)]
    crowd = Mesh(); con = Mesh(); vip = Mesh(); rib = Mesh(); rf = Mesh(); trus = Mesh(); led = Mesh(); fac = Mesh(); dk = Mesh()
    VARIANT = [0, 0, 3, 1, 0, 3, 0, 1, 0, 0, 3, 1]
    def tier(d0, y0, d1, y1, nb, gaps, seed):
        for (a0, a1) in arc_ranges(gaps):
            mid = (d0 + d1) / 2; A = np.linspace(a0, a1, 400)
            P = np.array([ring(mid, a) for a in A]); L = np.sum(np.linalg.norm(np.diff(P, axis=0), axis=1))
            ns = max(1, int(round(L / 23.0)))
            for s in range(ns):
                s0 = a0 + (a1 - a0) * s / ns; s1 = a0 + (a1 - a0) * (s + 1) / ns; var = VARIANT[(s + seed) % len(VARIANT)]
                for b in range(nb):
                    t0 = b / nb; t1 = (b + 1) / nb
                    k = (1 - 0.22 * t1) if seed else (1 - 0.04 * t1)       # darker towards the back under the roof
                    arc_band(crowd, d0 + (d1 - d0) * t0, y0 + (y1 - y0) * t0, d0 + (d1 - d0) * t1, y0 + (y1 - y0) * t1, s0, s1, 8, 23.0,
                             var * 0.25 + 0.002, var * 0.25 + 0.248, c=(k, k, k, 1))
    tier(8.0, 2.4, 31.0, 13.5, 4, gaps_low, 0)
    tier(32.5, 19.2, 52.0, 37.0, 4, gaps_up, 5)
    arc_band(dk, 8.0, 2.4, 31.0, 13.5, -SS_GAP, SS_GAP, 10, 6.0)                                       # covered seats behind the sight screen
    for (a0, a1) in arc_ranges(gaps_low) + [(-SS_GAP, SS_GAP)]:
        n = max(8, int((a1 - a0) * 60))
        arc_band(con, 8.0, 0.0, 8.0, 2.4, a0, a1, n, 6.0)                                              # front wall
    for (a0, a1) in arc_ranges(gaps_up):
        n = max(8, int((a1 - a0) * 60))
        arc_band(rib, 31.0, 13.5, 31.0, 14.6, a0, a1, n, 48.0)                                          # LED ribbon fascia
        arc_band(vip, 31.6, 14.6, 31.6, 18.2, a0, a1, n, 24.0)                                          # corporate boxes glass
        arc_band(con, 31.0, 14.6, 31.6, 14.6, a0, a1, n, 6.0)
        arc_band(con, 31.6, 18.2, 32.5, 18.2, a0, a1, n, 6.0)
        arc_band(rib, 32.5, 18.2, 32.5, 19.2, a0, a1, n, 48.0)                                          # upper tier parapet ribbon
        arc_band(con, 52.0, 37.0, 55.0, 37.0, a0, a1, n, 6.0)                                           # top concourse
        arc_band(con, 52.0, 37.0, 52.0, 39.0, a0, a1, n, 6.0)                                           # rear parapet
        for y0_, y1_ in ((0.0, 12.0), (12.0, 24.0), (24.0, 37.0)):
            arc_band(fac, 55.0, y0_, 55.0, y1_, a0, a1, n * 2, 12.0, 0, 1)                             # exterior facade
        arc_band(rf, 26.0, 42.0, 57.0, 47.0, a0, a1, n, 18.0, 0, 1)                                     # roof membrane
        arc_band(led, 26.0, 41.2, 26.0, 42.0, a0, a1, n, 6.0)                                           # LED edge strip
        nt = max(2, int((a1 - a0) / 0.10))
        for k in range(nt + 1):
            a = a0 + (a1 - a0) * k / nt; p0 = ring(57.0, a); p1 = ring(26.5, a); pg = ring(56.0, a)
            cyl(trus, (p0[0], 46.4, p0[1]), (p1[0], 41.4, p1[1]), 0.45, 0.25, 6, cap=False)
            cyl(trus, (pg[0], 0.0, pg[1]), (pg[0], 46.6, pg[1]), 0.8, 0.6, 8, cap=False)                # rear mast
            pm_ = ring(45.0, a); cyl(trus, (pm_[0], 37.0, pm_[1]), (pm_[0], 44.6, pm_[1]), 0.3, 0.3, 6, cap=False)
            pr = ring(58.0, a); pt = ring(40.0, a); pe = ring(26.0, a)                                   # exposed steel ribs above the membrane
            cyl(trus, (pr[0], 47.2, pr[1]), (pt[0], 50.5, pt[1]), 0.35, 0.3, 6, cap=False)
            cyl(trus, (pt[0], 50.5, pt[1]), (pe[0], 42.4, pe[1]), 0.3, 0.22, 6, cap=False)
    prof = [(8.0, 0), (8.0, 2.4), (31.0, 13.5), (31.0, 18.2), (32.5, 19.2), (52.0, 37.0), (55.0, 37.0), (55.0, 0)]
    for a in (np.pi - PAV_GAP, np.pi + PAV_GAP):                                                       # end caps at the pavilion opening
        P3 = [(ring(d, a)[0], y, ring(d, a)[1]) for d, y in prof]; ctr = (np.mean([p[0] for p in P3]), 6.0, np.mean([p[2] for p in P3]))
        V = [ctr] + P3; F = [(0, i, i + 1) for i in range(1, len(P3))] + [(0, len(P3), 1)]
        con.add(V, F, [(p[0] / 6, p[1] / 6) for p in V])
    node("stands", [("crowd", crowd), ("concrete", con), ("vip_glass", vip), ("ribbon", rib), ("dark_steel", dk)])
    node("roof", [("roof", rf), ("roof_led", led), ("steel", trus)])
    node("facade", [("facade", fac)])

    # ---------------------------------------------------------------- pavilion (near end, behind the striker)
    zf = CZ - (BZ + 8.0)
    pf = Mesh(); gl = Mesh(); gd = Mesh(); st = Mesh(); mem = Mesh(); fl = Mesh(); stl = Mesh()
    W = 28.0; zb = zf - 7.0
    for x0, x1 in ((-W + 1, -3.2), (3.2, W - 1)):                                                      # members' terraces
        mem.quad((x0, 0.6, zf), (x1, 0.6, zf), (x1, 4.4, zb), (x0, 4.4, zb), uv=((x0 / 23, 0.748), (x1 / 23, 0.748), (x1 / 23, 0.502), (x0 / 23, 0.502)))
        box(st, ((x0 + x1) / 2, 0.3, zf), ((x1 - x0) / 2, 0.3, 0.3), uvscale=3)
    box(st, (0, 0.05, zf - 3.5), (3.2, 0.05, 3.5), uvscale=3)                                          # players' walkway
    pf.quad((-W, 0, zb), (W, 0, zb), (W, 20, zb), (-W, 20, zb), uv=((0, 1), (1, 1), (1, 0), (0, 0)))
    box(st, (0, 10, zb - 13.0), (W, 10, 13.0), uvscale=4)
    for xc in (-18.5, 18.5):                                                                           # dressing-room balconies
        box(st, (xc, 4.55, zb + 1.4), (8.6, 0.15, 1.4), uvscale=3)
        box(gl, (xc, 5.25, zb + 2.75), (8.6, 0.55, 0.04))
        box(gd, (xc, 5.84, zb + 2.75), (8.6, 0.05, 0.08))
        box(st, (xc, 8.95, zb + 1.6), (8.8, 0.12, 1.6), uvscale=3)
    box(st, (0, 9.45, zb + 1.0), (W, 0.15, 1.0), uvscale=3)
    box(gd, (0, 9.62, zb + 1.95), (W, 0.04, 0.05))
    box(st, (0, 14.3, zb + 0.6), (W, 0.12, 0.6), uvscale=3)
    box(st, (0, 21.2, zb - 11.0), (W + 3, 0.5, 15.5), uvscale=4)                                       # crown roof
    box(gd, (0, 20.8, zb + 4.5), (W + 3, 0.6, 0.12))
    box(st, (0, 24.5, zb - 2.0), (5.0, 3.5, 2.5), uvscale=3)                                           # central gable
    box(gd, (0, 28.3, zb - 2.0), (5.4, 0.3, 2.9))
    cyl(gd, (0, 28.6, zb - 2.0), (0, 33.0, zb - 2.0), 0.9, 0.05, 8)
    cv, cn, cf, cuv = sphere_uv((0, 24.8, zb + 0.55), 1.8, 18, 8)                                      # clock face
    cv = cv.copy(); cv[:, 2] = np.minimum(cv[:, 2], zb + 0.6); gd.add(cv, cf, cuv)
    for xf, colr in ((-W, (0.95, 0.6, 0.2, 1)), (-W / 2, (0.05, 0.1, 0.35, 1)), (W / 2, (0.05, 0.1, 0.35, 1)), (W, (0.95, 0.6, 0.2, 1))):
        cyl(stl, (xf, 21.7, zb + 2.0), (xf, 28.0, zb + 2.0), 0.07, 0.05, 6)
        fl.add([(xf, 27.9, zb + 2.0), (xf + 2.2, 27.7, zb + 2.3), (xf + 2.2, 26.4, zb + 2.3), (xf, 26.6, zb + 2.0)], [(0, 1, 2), (0, 2, 3)], c=[colr] * 4)
    node("pavilion", [("pavilion_front", pf), ("stone", st), ("crowd", mem), ("pavilion_glass", gl), ("gold", gd), ("flag", fl), ("steel", stl)])

    # ---------------------------------------------------------------- sight screens (black/white switched at runtime), dugouts
    ss = Mesh(); ssf = Mesh()
    for zc, w, h, face in ((CZ + BZ + 5.2, 9.0, 7.5, -1), (CZ - BZ - 5.2, 9.5, 6.5, 1)):
        p = [(-w, 0.4, zc), (w, 0.4, zc), (w, 0.4 + h, zc), (-w, 0.4 + h, zc)]
        if face < 0: p = p[::-1]
        ss.add(p, [(0, 1, 2), (0, 2, 3)], n=[(0, 0, face)] * 4)
        box(ssf, (0, 0.2 + h / 2, zc - face * 0.35), (w, 0.2 + h / 2, 0.25))
    dg = Mesh(); dgr = Mesh()
    for a in (np.pi - 0.62, np.pi + 0.62):
        p = ring(5.9, a); yaw = np.arctan2(-outward(a)[0], -outward(a)[1])
        box(dg, (p[0], 1.1, p[1]), (3.2, 1.1, 0.9), yaw=yaw)
        box(dgr, (p[0], 2.3, p[1]), (3.4, 0.05, 1.2), yaw=yaw)
    node("sight_screens", [("sight_screen", ss), ("dark_steel", ssf), ("board_frame", dg), ("perspex", dgr)])

    # ---------------------------------------------------------------- giant screen above the far-end roof
    scr = Mesh(); scf = Mesh(); zc = CZ + BZ + 44.0
    scr.quad((15, 46.5, zc), (-15, 46.5, zc), (-15, 59.5, zc), (15, 59.5, zc), uv=((0, 1), (1, 1), (1, 0), (0, 0)))
    box(scf, (0, 53.0, zc + 0.5), (15.6, 6.9, 0.45))
    for x in (-10, 10): cyl(scf, (x, 44.0, zc + 1.0), (x, 46.6, zc + 1.0), 0.5, 0.5, 8)
    node("big_screen", [("big_screen", scr), ("dark_steel", scf)])

    # ---------------------------------------------------------------- floodlight towers (6) aimed at the square
    tw = Mesh(); lp = Mesh(); hb = Mesh(); towers = []
    for adeg in (38, 90, 142, -38, -90, -142):
        a = np.radians(adeg); p = ring(64.0, a); base = np.array((p[0], 0, p[1])); top = base + (0, 70, 0)
        cyl(tw, base, top, 1.5, 0.75, 10, cap=False)
        for hgt in (20, 40, 60): cyl(tw, base + (0, hgt, 0), base + (0, hgt + 0.6, 0), 2.2 - hgt / 60, 2.2 - hgt / 60, 10)
        head = top + (0, 4.5, 0); tgt = np.array((0, 0, CZ)); nrm = tgt - head; nrm /= np.linalg.norm(nrm)
        right = np.cross((0, 1, 0), nrm); right /= np.linalg.norm(right); up = np.cross(nrm, right)
        c0 = head + nrm * 0.6; w2, h2 = 8.0, 5.5
        P = [c0 - right * w2 - up * h2, c0 + right * w2 - up * h2, c0 + right * w2 + up * h2, c0 - right * w2 + up * h2]
        lp.add(P, [(0, 1, 2), (0, 2, 3)], [(0, 1), (1, 1), (1, 0), (0, 0)], n=[nrm] * 4)
        bc = head - nrm * 0.3
        Pb = [bc - right * (w2 + .4) - up * (h2 + .4), bc + right * (w2 + .4) - up * (h2 + .4), bc + right * (w2 + .4) + up * (h2 + .4), bc - right * (w2 + .4) + up * (h2 + .4)]
        hb.add(Pb, [(0, 2, 1), (0, 3, 2)], n=[-nrm] * 4)
        for i in range(4): cyl(hb, Pb[i], P[i], 0.12, 0.12, 4, cap=False)
        cyl(hb, top, bc, 0.6, 0.5, 6, cap=False)
        towers.append([float(x) for x in head])
    node("floodlight_towers", [("steel", tw), ("dark_steel", hb)]); node("floodlight_lamps", [("flood_lamp", lp)])

    # ---------------------------------------------------------------- surroundings (exterior): plaza, lawns, trees, ramps, city
    sr0 = Mesh(); arc_band(sr0, 9.0, -0.03, 105.0, -0.03, 0, 2 * np.pi, 160, 20.0)
    v_, n_, uv_, f_, c_ = sr0.build(); sr = Mesh(); sr.add(v_, f_, v_[:, [0, 2]] / 20.0, n=[(0, 1, 0)] * len(v_))
    gf0 = Mesh(); arc_band(gf0, 105.0, -0.05, 420.0, -0.05, 0, 2 * np.pi, 96, 40.0)
    v_, n_, uv_, f_, c_ = gf0.build(); gf = Mesh(); gf.add(v_, f_, v_[:, [0, 2]] / 9.0, n=[(0, 1, 0)] * len(v_))
    tr = Mesh(); tk = Mesh(); ct = Mesh(); rmp = Mesh()
    for i in range(90):
        a = RNG.uniform(0, 2 * np.pi); d = RNG.uniform(70, 112); p = ring(d, a)
        if abs(((a - np.pi + np.pi) % (2 * np.pi)) - np.pi) < 0.1: continue
        h = RNG.uniform(6, 11); cyl(tk, (p[0], 0, p[1]), (p[0], h * 0.45, p[1]), 0.35, 0.25, 5, cap=False)
        v, n, f, _ = sphere_uv((p[0], h * 0.7, p[1]), h * 0.32, 8, 5); v[:, 1] = h * 0.7 + (v[:, 1] - h * 0.7) * 1.25
        k = RNG.uniform(0.75, 1.15); tr.add(v, f, n=n, c=np.tile((k, k, k, 1), (len(v), 1)))
    for adeg in (0, 60, 120, 180, 240, 300):                                                           # spiral ramp drums
        a = np.radians(adeg); p = ring(61.0, a); cyl(rmp, (p[0], 0, p[1]), (p[0], 30, p[1]), 6.0, 6.0, 16)
    for i in range(70):
        a = RNG.uniform(0, 2 * np.pi); d = RNG.uniform(220, 360); p = ring(d, a); h = RNG.uniform(14, 75); w = RNG.uniform(14, 30)
        box(ct, (p[0], h / 2, p[1]), (w / 2, h / 2, w / 2 * RNG.uniform(0.6, 1.2)), yaw=RNG.uniform(0, np.pi))
        for j in range(6): ct.uv[-6 + j] = ct.uv[-6 + j] * np.array([w / 18.0, h / 36.0], np.float32)
    node("surroundings", [("plaza", sr), ("ground_far", gf), ("tree", tr), ("trunk", tk), ("concrete", rmp), ("city", ct)])

    # ---------------------------------------------------------------- stumps & bails (animated nodes), ball
    for end, zc in (("bat", 0.0), ("bowl", 20.12)):
        kids = []
        for i, x in enumerate((-0.0953, 0.0, 0.0953)):
            sm = Mesh(); cyl(sm, (0, 0, 0), (0, 0.70, 0), 0.0185, 0.0185, 12, cap=False)
            v, n, f, _ = sphere_uv((0, 0.70, 0), 0.0185, 12, 6); keep = 4 * 13; f = f[np.all(f < keep, axis=1)]; sm.add(v[:keep], f, n=n[:keep])
            v, n, uv, f, c = sm.build()
            kids.append(g.node(f"stump_{end}_{i}", mesh=g.mesh(f"stump_{end}_{i}", [g.primitive(v, n, f, M["stump"], uv=uv)]), t=(x, 0, zc)))
        for i, x in enumerate((-0.054, 0.054)):
            bm = Mesh(); cyl(bm, (-0.054, 0, 0), (-0.035, 0, 0), 0.004, 0.004, 6, cap=False); cyl(bm, (-0.035, 0, 0), (0.035, 0, 0), 0.0075, 0.0075, 8)
            cyl(bm, (0.035, 0, 0), (0.054, 0, 0), 0.004, 0.004, 6, cap=False)
            v, n, uv, f, c = bm.build()
            kids.append(g.node(f"bail_{end}_{i}", mesh=g.mesh(f"bail_{end}_{i}", [g.primitive(v, n, f, M["stump"], uv=uv)]), t=(x, 0.718, zc)))
        nodes.append(g.node(f"stumps_{end}", children=kids))
    bv, bn, bf, _ = sphere_uv((0, 0, 0), 0.036, 18, 12)
    sm = Mesh()
    for j in range(24):
        a0 = 2 * np.pi * j / 24; a1 = 2 * np.pi * (j + 1) / 24
        cyl(sm, (0, 0.0362 * np.cos(a0), 0.0362 * np.sin(a0)), (0, 0.0362 * np.cos(a1), 0.0362 * np.sin(a1)), 0.0025, 0.0025, 4, cap=False)
    sv, sn, suv, sf, _ = sm.build()
    nodes.append(g.node("ball", mesh=g.mesh("ball", [g.primitive(bv, bn, bf, M["ball"]), g.primitive(sv, sn, sf, M["seam"])]), t=(0, 0.036, 18.0)))

    # ---------------------------------------------------------------- sky domes (unlit, textured)
    for k in ("day", "dusk", "night"):
        mt = g.material("sky_" + k, (1, 1, 1, 1), 0, 1, tex=TX["sky_" + k], unlit=True, double=True)
        v, n, f, _ = sphere_uv((0, -30, CZ), 450.0, 48, 20)
        d = (v - np.array((0, 0, CZ))); d /= np.linalg.norm(d, axis=1, keepdims=True)
        el = np.degrees(np.arcsin(np.clip(d[:, 1], -1, 1))); az = np.tile(np.arange(49), 21) / 48.0
        vv = np.clip(np.where(el >= 0, 0.88 * (1 - el / 90), 0.88 + (-el / 12) * 0.12), 0, 0.999)
        uv = np.c_[az, vv].astype(np.float32)
        nodes.append(g.node("sky_" + k, mesh=g.mesh("sky_" + k, [g.primitive(v, -n, f[:, ::-1].copy(), mt, uv=uv)]))); stats["sky_" + k] = len(f)

    g.node("stadium", children=nodes, root=True)
    size = g.save(out)
    if meta_out:
        json.dump({"CZ": CZ, "BX": BX, "BZ": BZ, "towers": towers}, open(meta_out, "w"))
    return size, stats


if __name__ == "__main__":
    size, stats = build(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
    print("stadium.glb", size, "bytes; triangles total", sum(stats.values()))
    for k, v in sorted(stats.items(), key=lambda x: -x[1])[:10]: print("  ", k, v)
