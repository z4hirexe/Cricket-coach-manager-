"""Procedural cricketer: SDF anatomy -> marching cubes -> decimation -> skinning -> glTF with baked animation clips."""
import numpy as np, sys, os
from skimage.measure import marching_cubes
import fast_simplification
from rig import *
from glb import GLB, smooth_normals
import clips as C

# ---------------------------------------------------------------- SDF primitives (vectorised over Nx3)
def ell(p, c, r):
    q = (p - c) / r; k0 = np.linalg.norm(q, axis=1); k1 = np.linalg.norm(q / r, axis=1)
    return k0 * (k0 - 1) / np.maximum(k1, 1e-9)
def rcone(p, a, b, ra, rb):
    a = np.asarray(a, float); b = np.asarray(b, float); ba = b - a; pa = p - a
    h = np.clip((pa @ ba) / (ba @ ba), 0, 1)
    return np.linalg.norm(pa - h[:, None] * ba, axis=1) - (ra + (rb - ra) * h)
def box(p, c, half, r=0.0):
    q = np.abs(p - c) - (np.asarray(half) - r)
    return np.linalg.norm(np.maximum(q, 0), axis=1) + np.minimum(q.max(1), 0) - r
def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0, 1); return b * (1 - h) + a * h - k * h * (1 - h)
def smax(a, b, k): return -smin(-a, -b, k)

def lerp(a, b, t): return np.asarray(a) * (1 - t) + np.asarray(b) * t

def body_prims(heavy=False, slim=False):
    """(name, bone, region, sdf_fn, k).  Builds: slim (wiry quick bowlers / young batters), athletic (default), heavy."""
    t = 1.13 if heavy else (0.92 if slim else 1.0); l = 1.07 if heavy else (0.94 if slim else 1.0)
    P = []
    add = lambda n, b, reg, f, k=0.035: P.append((n, b, reg, f, k))
    add("pelvis", "hips", "trousers", lambda p: ell(p, (0, 0.95, -0.005), np.array((0.165, 0.12, 0.115)) * (t ** 0.8)))
    add("glutes", "hips", "trousers", lambda p: ell(p, (0, 0.90, -0.045), np.array((0.15, 0.10, 0.09)) * t))
    add("abdomen", "spine", "jersey", lambda p: ell(p, (0, 1.10, 0.01 + (0.02 if heavy else 0)), np.array((0.142, 0.13, 0.108)) * t))
    add("chest", "chest", "jersey", lambda p: ell(p, (0, 1.30, 0.0), np.array((0.172, 0.15, 0.112)) * (t ** 0.7)))
    for s in (1, -1):
        add("pec", "chest", "jersey", lambda p, s=s: ell(p, (0.068 * s, 1.335, 0.055), np.array((0.072, 0.058, 0.05)) * (t ** 0.7)), 0.03)
        add("lat", "chest", "jersey", lambda p, s=s: ell(p, (0.11 * s, 1.27, -0.035), np.array((0.06, 0.12, 0.06)) * (t ** 0.7)), 0.03)
    add("traps", "chest", "jersey", lambda p: ell(p, (0, 1.435, -0.025), np.array((0.15, 0.06, 0.075)) * (t ** 0.6)))
    add("neck", "neck", "skin", lambda p: rcone(p, (0, 1.42, -0.005), (0, 1.62, 0.005), 0.057 * (t ** 0.5), 0.050), 0.03)
    # head
    add("skull", "head", "skin", lambda p: ell(p, (0, 1.715, -0.005), (0.080, 0.100, 0.098)), 0.02)
    add("jaw", "head", "skin", lambda p: ell(p, (0, 1.648, 0.035), (0.064, 0.048, 0.063)), 0.025)
    add("chin", "head", "skin", lambda p: ell(p, (0, 1.618, 0.074), (0.028, 0.022, 0.022)), 0.02)
    add("nose", "head", "skin", lambda p: rcone(p, (0, 1.712, 0.087), (0, 1.672, 0.106), 0.010, 0.016), 0.012)
    add("brow", "head", "skin", lambda p: ell(p, (0, 1.728, 0.077), (0.058, 0.014, 0.022)), 0.015)
    for s in (1, -1):
        add("cheek", "head", "skin", lambda p, s=s: ell(p, (0.045 * s, 1.684, 0.066), (0.024, 0.016, 0.02)), 0.015)
        add("ear", "head", "skin", lambda p, s=s: ell(p, (0.081 * s, 1.69, -0.008), (0.013, 0.031, 0.021)), 0.01)
        add("lip", "head", "skin", lambda p, s=s: ell(p, (0.0, 1.648 + 0.006 * s, 0.093), (0.022, 0.006, 0.01)), 0.008)
    # limbs
    for sd in "LR":
        s = 1 if sd == "L" else -1
        sh, el, wr, tip = BIND["upperarm_" + sd], BIND["forearm_" + sd], BIND["hand_" + sd], TIP["hand_" + sd]
        add("delt", "upperarm_" + sd, "jersey", lambda p, sh=sh, s=s: ell(p, sh + np.array((0.015 * s, -0.01, 0)), np.array((0.062, 0.07, 0.062)) * (t ** 0.6)), 0.03)
        add("uarm", "upperarm_" + sd, "arm", lambda p, sh=sh, el=el: rcone(p, sh, el, 0.050 * l, 0.038 * l), 0.025)
        add("bicep", "upperarm_" + sd, "arm", lambda p, sh=sh, el=el: ell(p, lerp(sh, el, 0.45) + np.array((0, 0, 0.012)), np.array((0.042, 0.06, 0.042)) * l), 0.02)
        add("farm", "forearm_" + sd, "skin", lambda p, el=el, wr=wr: rcone(p, el, wr, 0.040 * l, 0.026), 0.02)
        add("fmuscle", "forearm_" + sd, "skin", lambda p, el=el, wr=wr: ell(p, lerp(el, wr, 0.3), np.array((0.037, 0.06, 0.035)) * l), 0.02)
        add("hand", "hand_" + sd, "skin", lambda p, wr=wr, tip=tip: rcone(p, lerp(wr, tip, 0.12), lerp(wr, tip, 0.8), 0.028, 0.022), 0.012)
        add("thumb", "hand_" + sd, "skin", lambda p, wr=wr, tip=tip: rcone(p, lerp(wr, tip, 0.2) + np.array((0, 0, 0.02)), lerp(wr, tip, 0.55) + np.array((0, 0, 0.035)), 0.012, 0.009), 0.008)
        hp, kn, an, toe = BIND["thigh_" + sd], BIND["shin_" + sd], BIND["foot_" + sd], TIP["foot_" + sd]
        add("thigh", "thigh_" + sd, "trousers", lambda p, hp=hp, kn=kn: rcone(p, hp + np.array((0.005 * s, 0.02, 0)), kn, 0.088 * t ** 0.8, 0.054), 0.04)
        add("quad", "thigh_" + sd, "trousers", lambda p, hp=hp, kn=kn: ell(p, lerp(hp, kn, 0.4) + np.array((0, 0, 0.02)), np.array((0.06, 0.13, 0.055)) * l), 0.03)
        add("shin", "shin_" + sd, "trousers", lambda p, kn=kn, an=an: rcone(p, kn, an + np.array((0, 0.03, 0)), 0.050 * l, 0.032), 0.03)
        add("calf", "shin_" + sd, "trousers", lambda p, kn=kn, an=an: ell(p, lerp(kn, an, 0.28) + np.array((0, 0, -0.025)), np.array((0.046, 0.09, 0.046)) * l), 0.03)
        add("shoe", "foot_" + sd, "shoe", lambda p, an=an, toe=toe: rcone(p, an + np.array((0, -0.035, -0.04)), toe + np.array((0, 0.005, 0)), 0.042, 0.036), 0.02)
    return P

SOCKETS = [lambda p, s=s: ell(p, (0.031 * s, 1.704, 0.088), (0.017, 0.012, 0.014)) for s in (1, -1)]

def eval_field(prims, pts, sockets=True):
    d = np.full(len(pts), 1e3); lab = np.zeros(len(pts), int); best = np.full(len(pts), 1e3)
    for i, (n, b, reg, f, k) in enumerate(prims):
        di = f(pts); m = di < best; best[m] = di[m]; lab[m] = i
        d = smin(d, di, k)
    if sockets:
        for f in SOCKETS: d = smax(d, -f(pts), 0.006)
    return d, lab

def mc(fn, lo, hi, h, target=None):
    lo = np.asarray(lo, float); hi = np.asarray(hi, float)
    n = np.ceil((hi - lo) / h).astype(int) + 1
    g = np.stack(np.meshgrid(*[lo[i] + np.arange(n[i]) * h for i in range(3)], indexing="ij"), -1).reshape(-1, 3)
    vals = np.concatenate([fn(g[i:i + 400000]) for i in range(0, len(g), 400000)]).reshape(n)
    v, f, _, _ = marching_cubes(vals, 0.0, spacing=(h, h, h))
    v = v + lo; f = f[:, ::-1].copy()  # outward winding
    if target and len(f) > target:
        v, f = fast_simplification.simplify(v.astype(np.float32), f.astype(np.int32), target_reduction=1 - target / len(f))
    return v.astype(np.float32), f.astype(np.int32)

# ---------------------------------------------------------------- skin weights
ALLOWED = {}
for j in JOINTS:
    ALLOWED[j] = {j} | ({PARENT[j]} if PARENT[j] else set()) | {c for c, p in PARENT.items() if p == j}
def seg_dist(p, a, b):
    ba = b - a; h = np.clip(((p - a) @ ba) / (ba @ ba), 0, 1); return np.linalg.norm(p - a - h[:, None] * ba, axis=1)
def weights_for(v, bones):
    """bones: per-vertex primary bone name. returns joints (N,4) weights (N,4)"""
    N = len(v); D = np.full((N, len(JOINTS)), np.inf)
    for ji, j in enumerate(JOINTS):
        D[:, ji] = seg_dist(v, BIND[j], seg_end(j))
    W = np.zeros_like(D)
    for ji, j in enumerate(JOINTS):
        mask = np.array([j in ALLOWED[b] for b in bones])
        W[mask, ji] = 1.0 / np.maximum(D[mask, ji], 0.004) ** 5
    prim = np.array([JOINTS.index(b) for b in bones]); W[np.arange(N), prim] *= 1.6
    idx = np.argsort(-W, axis=1)[:, :4]; w = np.take_along_axis(W, idx, 1)
    w[:, 2:] *= (w[:, 2:] > 0.06 * w[:, :1])
    w = w / w.sum(1, keepdims=True)
    return idx.astype(np.uint16), w.astype(np.float32)

def rigid(v, bone):
    j = np.zeros((len(v), 4), np.uint16); j[:, 0] = JOINTS.index(bone); w = np.zeros((len(v), 4), np.float32); w[:, 0] = 1
    return j, w

# ---------------------------------------------------------------- body + accessories
def build_body(heavy, h, target, slim=False):
    prims = body_prims(heavy, slim)
    v, f = mc(lambda p: eval_field(prims, p)[0], (-0.75, -0.01, -0.2), (0.75, 1.86, 0.24), h, target)
    _, lab = eval_field(prims, v.astype(float))
    regions = np.array([prims[i][2] for i in lab], dtype=object); bones = [prims[i][1] for i in lab]
    # sleeves: upper arm jersey down to 55%, cuff trim band, collar trim
    for i in np.where(regions == "arm")[0]:
        sd = bones[i][-1]; a, b = BIND["upperarm_" + sd], BIND["forearm_" + sd]
        hh = np.dot(v[i] - a, b - a) / np.dot(b - a, b - a)
        regions[i] = "jersey" if hh < 0.5 else ("trim" if hh < 0.58 else "skin")
    col = (regions == "jersey") & (v[:, 1] > 1.435) & (np.abs(v[:, 0]) < 0.085)
    regions[col] = "trim"
    side = (regions == "trousers") & (np.abs(v[:, 0]) > 0.0) & (np.abs(np.abs(v[:, 0]) - 0.0) >= 0)
    jo, we = weights_for(v.astype(float), bones)
    return v, f, regions, jo, we

def build_accessories():
    acc = {}
    # helmet: shell + peak + neck guard + grille
    def helmet(p):
        shell = ell(p, (0, 1.718, -0.008), (0.104, 0.118, 0.118))
        cut = smax(shell, (1.655 - p[:, 1]) + np.maximum(0, -p[:, 2]) * 0.0 - np.where(p[:, 2] < -0.02, 0.05, 0.0), 0.01)
        peak = ell(p, (0, 1.71, 0.102), (0.092, 0.011, 0.045))
        flap = smax(ell(p, (0, 1.64, -0.07), (0.09, 0.05, 0.05)), p[:, 2] + 0.03, 0.01)
        return smin(smin(cut, peak, 0.01), flap, 0.01)
    acc["helmet"] = (mc(helmet, (-0.13, 1.56, -0.15), (0.13, 1.86, 0.17), 0.006, 2200), "head", "helmet")
    def grille(p):
        d = 1e3
        for y in (1.672, 1.640, 1.610):
            d = np.minimum(d, smax(np.abs(np.linalg.norm((p - np.array((0, y, 0.0)))[:, [0, 2]], axis=1) - 0.118) - 0.0035,
                                   np.abs(p[:, 1] - y) - 0.0035, 0.001))
        d = smax(d, -(p[:, 2] - 0.02), 0.001)
        for x in (-0.03, 0.03):
            d = np.minimum(d, rcone(p, (x, 1.70, 0.110), (x * 0.8, 1.605, 0.105), 0.0035, 0.0035))
        return d
    acc["grille"] = (mc(grille, (-0.13, 1.59, 0.0), (0.13, 1.71, 0.14), 0.004, 1200), "head", "grille")
    def cap(p):
        dome = smax(ell(p, (0, 1.735, -0.005), (0.092, 0.075, 0.098)), 1.712 - p[:, 1], 0.008)
        brim = ell(p, (0, 1.714, 0.105), (0.082, 0.007, 0.07))
        return smin(dome, brim, 0.012)
    acc["cap"] = (mc(cap, (-0.12, 1.68, -0.12), (0.12, 1.82, 0.19), 0.005, 900), "head", "cap")
    def hair(p, longback=False):
        shell = ell(p, (0, 1.718, -0.008), (0.087, 0.107, 0.105))
        line = 1.715 + 0.03 * np.clip(p[:, 2] / 0.08, 0, 1) - (0.10 if longback else 0.05) * np.clip(-p[:, 2] / 0.06, 0, 1)
        ears = np.minimum(1, np.abs(np.abs(p[:, 0]) - 0.08) / 0.02 + np.maximum(0, p[:, 1] - 1.72) * 50)
        return smax(shell, (line - p[:, 1]) + 0.02 * (1 - ears) * (p[:, 1] < 1.72), 0.008)
    acc["hair_short"] = (mc(hair, (-0.11, 1.58, -0.13), (0.11, 1.83, 0.12), 0.005, 900), "head", "hair")
    acc["hair_long"] = (mc(lambda p: hair(p, True), (-0.11, 1.55, -0.13), (0.11, 1.83, 0.12), 0.005, 900), "head", "hair")
    # Phase C: more hair styles (each a separate mesh, picked per player)
    def buzz(p):
        shell = ell(p, (0, 1.716, -0.006), (0.0835, 0.1035, 0.1015))
        line = 1.722 + 0.025 * np.clip(p[:, 2] / 0.08, 0, 1) - 0.06 * np.clip(-p[:, 2] / 0.06, 0, 1)
        return smax(shell, line - p[:, 1], 0.006)
    acc["hair_buzz"] = (mc(buzz, (-0.11, 1.60, -0.13), (0.11, 1.83, 0.12), 0.004, 900), "head", "hair")
    def curly(p):
        bump = 0.006 * (np.sin(p[:, 0] * 160) * np.sin(p[:, 1] * 150) * np.sin(p[:, 2] * 170))
        shell = ell(p, (0, 1.735, -0.012), (0.100, 0.115, 0.115)) + bump
        line = 1.712 + 0.035 * np.clip(p[:, 2] / 0.08, 0, 1) - 0.06 * np.clip(-p[:, 2] / 0.06, 0, 1)
        return smax(shell, line - p[:, 1], 0.01)
    acc["hair_curly"] = (mc(curly, (-0.13, 1.60, -0.15), (0.13, 1.87, 0.14), 0.005, 1200), "head", "hair")
    def bun(p):
        knot = ell(p, (0, 1.80, -0.075), (0.034, 0.03, 0.034))
        return smin(hair(p), knot, 0.015)
    acc["hair_bun"] = (mc(bun, (-0.11, 1.58, -0.13), (0.11, 1.85, 0.12), 0.005, 1000), "head", "hair")
    def stubble(p):
        jaw = smin(ell(p, (0, 1.647, 0.035), (0.066, 0.050, 0.065)), ell(p, (0, 1.617, 0.075), (0.030, 0.024, 0.024)), 0.02)
        return smax(smax(jaw, p[:, 1] - 1.666 - 0.02 * (np.abs(p[:, 0]) > 0.05), 0.006), -p[:, 2] + 0.0, 0.006)
    acc["beard_stubble"] = (mc(stubble, (-0.09, 1.57, -0.02), (0.09, 1.70, 0.11), 0.003, 900), "head", "stubble")
    def goatee(p):
        chin = ell(p, (0, 1.622, 0.074), (0.034, 0.030, 0.029))
        tache = ell(p, (0, 1.662, 0.092), (0.030, 0.007, 0.012))
        return smin(smax(chin, p[:, 1] - 1.652, 0.004), tache, 0.008)
    acc["beard_goatee"] = (mc(goatee, (-0.06, 1.58, 0.03), (0.06, 1.68, 0.11), 0.003, 600), "head", "hair")
    acc["moustache"] = (mc(lambda p: ell(p, (0, 1.663, 0.093), (0.031, 0.0075, 0.012)), (-0.05, 1.645, 0.07), (0.05, 1.68, 0.11), 0.0025, 300), "head", "hair")
    def beard(p):
        jaw = smin(ell(p, (0, 1.646, 0.036), (0.069, 0.053, 0.068)), ell(p, (0, 1.616, 0.076), (0.033, 0.027, 0.027)), 0.02)
        return smax(smax(jaw, p[:, 1] - 1.668 - 0.02 * (np.abs(p[:, 0]) > 0.05), 0.006), -p[:, 2] + 0.0, 0.006)
    acc["beard"] = (mc(beard, (-0.09, 1.57, -0.02), (0.09, 1.70, 0.11), 0.004, 700), "head", "hair")
    for sd in "LR":
        s = 1 if sd == "L" else -1
        kn, an = BIND["shin_" + sd], BIND["foot_" + sd]
        def pad(p, kn=kn, an=an, s=s):
            ax = norm(kn - an); c = lerp(an, kn, 0.55) + np.array((0, 0, 0.045))
            q = p - c; y = q @ ax
            core = box(np.stack([q[:, 0], y, q[:, 2]], 1), (0, 0.06, 0), (0.075, 0.33, 0.035), 0.03)
            ridge = 0.006 * np.cos(q[:, 0] * 95)
            return core - ridge
        acc["pad_" + sd] = (mc(pad, (kn[0] - 0.11, 0.04, -0.06), (kn[0] + 0.11, 0.70, 0.13), 0.006, 900), "shin_" + sd, "pads")
        wr, tip = BIND["hand_" + sd], TIP["hand_" + sd]
        def glove(p, wr=wr, tip=tip):
            return smin(rcone(p, lerp(wr, tip, -0.05), lerp(wr, tip, 0.85), 0.040, 0.030), ell(p, lerp(wr, tip, 0.35) + np.array((0, 0, 0.03)), (0.03, 0.03, 0.03)), 0.015) - 0.003 * np.cos(np.linalg.norm(p - wr, axis=1) * 160)
        acc["glove_" + sd] = (mc(glove, np.minimum(wr, tip) - 0.08, np.maximum(wr, tip) + 0.08, 0.005, 700), "hand_" + sd, "gloves")
    acc["eyes"] = (mc(lambda p: np.minimum(ell(p, (0.031, 1.703, 0.084), (0.0125,) * 3), ell(p, (-0.031, 1.703, 0.084), (0.0125,) * 3)),
                      (-0.05, 1.68, 0.06), (0.05, 1.73, 0.105), 0.002, 300), "head", "eye")
    return acc

def bat_mesh():
    """bat along +y from s=-0.12 (handle top) to 0.74 (toe); x = width, z = thickness (face +z)."""
    def bat(p):
        handle = rcone(p, (0, -0.17, 0), (0, 0.18, 0), 0.016, 0.017)
        y = p[:, 1]; bulge = 0.012 * np.exp(-((y - 0.55) / 0.12) ** 2)
        blade = box(p - np.array((0, 0.46, -0.004)), (0, 0, 0), (0.054, 0.28, 0.018 + 0.0), 0.012)
        spine = rcone(p, (0, 0.20, -0.012), (0, 0.72, -0.012), 0.012, 0.012 + 0.0) - bulge
        return smin(smin(handle, blade, 0.02), spine, 0.02)
    v, f = mc(bat, (-0.07, -0.19, -0.05), (0.07, 0.76, 0.04), 0.0045, 1400)
    reg = np.where(v[:, 1] < 0.17, "grip", "willow")
    return v, f, reg

# ---------------------------------------------------------------- materials
MATS = {"skin": ((0.55, 0.38, 0.28, 1), 0.0, 0.55), "jersey": ((0.1, 0.3, 0.8, 1), 0.0, 0.75), "trim": ((1, 0.75, 0.1, 1), 0.0, 0.7),
        "trousers": ((0.08, 0.15, 0.45, 1), 0.0, 0.8), "shoe": ((0.92, 0.92, 0.92, 1), 0.0, 0.5), "eye": ((0.12, 0.08, 0.06, 1), 0.0, 0.15),
        "helmet": ((0.06, 0.1, 0.3, 1), 0.15, 0.3), "grille": ((0.75, 0.76, 0.78, 1), 0.9, 0.35), "cap": ((0.1, 0.3, 0.8, 1), 0.0, 0.8),
        "hair": ((0.05, 0.035, 0.03, 1), 0.0, 0.65), "stubble": ((0.2, 0.14, 0.11, 1), 0.0, 0.8), "number": ((1, 0.8, 0.2, 1), 0.0, 0.6), "pads": ((0.95, 0.95, 0.93, 1), 0.0, 0.6), "gloves": ((0.95, 0.95, 0.93, 1), 0.0, 0.65),
        "willow": ((0.86, 0.72, 0.48, 1), 0.0, 0.5), "grip": ((0.55, 0.05, 0.05, 1), 0.0, 0.8)}

# ---------------------------------------------------------------- shirt numbers (Phase C)
SEGS = {"a": (0, 1, 1, 0.14), "b": (1, 1, 0.5, 1), "c": (1, 0.5, 0, 1), "d": (0, 0, 1, 0.14), "e": (0, 0.5, 0, 0), "f": (0, 1, 0.5, 0), "g": (0, 0.5, 1, 0.5)}
DIGITS = {0: "abcdef", 1: "bc", 2: "abged", 3: "abgcd", 4: "fgbc", 5: "afgcd", 6: "afgedc", 7: "abc", 8: "abcdefg", 9: "abcdfg"}
NUM_W, NUM_H, NUM_B, NUM_Y = 0.064, 0.120, 0.016, 1.285

def back_z(x, y):
    """outer surface of the athletic jersey back (chest ellipsoid + smin bulge) at (x, y)."""
    q = 1 - (x / 0.172) ** 2 - ((y - 1.30) / 0.15) ** 2
    return -0.112 * np.sqrt(np.maximum(q, 0.05)) - 0.013

def digit_mesh(d, cx):
    """seven-segment digit on the shirt back, centred at x=cx (character space), as boxes facing -z.  Returns v, n, f in chest-local space."""
    V, N, F = [], [], []
    def quad(a, b, c, e, nrm):
        # wind so the geometric normal agrees with nrm (back-face culling on the GPU would hide a mis-wound digit)
        if np.dot(np.cross(np.asarray(b) - a, np.asarray(c) - a), nrm) < 0: a, b, c, e = a, e, c, b
        i = len(V); V.extend([a, b, c, e]); N.extend([nrm] * 4); F.extend([(i, i + 1, i + 2), (i, i + 2, i + 3)])
    for s in DIGITS[d]:
        kind = SEGS[s]
        # segment rectangle in digit units (u right, v up), u,v in [0,1]
        if s in "adg":
            v0 = {"a": 1, "d": 0, "g": 0.5}[s]; u0, u1 = 0.0, 1.0; vv0, vv1 = v0 - 0.5 * NUM_B / NUM_H, v0 + 0.5 * NUM_B / NUM_H
            vv0, vv1 = max(vv0, 0), min(vv1, 1); 
            if s == "a": vv0 = 1 - NUM_B / NUM_H
            if s == "d": vv1 = NUM_B / NUM_H
        else:
            u = 1.0 if s in "bc" else 0.0; u0, u1 = (1 - NUM_B / NUM_W, 1.0) if u == 1 else (0.0, NUM_B / NUM_W)
            vv0, vv1 = (0.5, 1.0) if s in "bf" else (0.0, 0.5)
        corners = [(u0, vv0), (u1, vv0), (u1, vv1), (u0, vv1)]
        def P(u, v, inset):
            x = cx - (u - 0.5) * NUM_W          # viewer behind the player: viewer's right is character -x
            y = NUM_Y + (v - 0.5) * NUM_H
            z = back_z(x, y) + inset
            return np.array([x, y, z]) - BIND["chest"]
        # raised 7 mm proud of the jersey (skinned cloth moves a little under the rigid digits), 3 mm sunk in
        out = [P(u, v, -0.007) for u, v in corners]; inn = [P(u, v, 0.003) for u, v in corners]
        quad(out[0], out[3], out[2], out[1], (0, 0, -1))          # facing -z (CCW seen from behind)
        for k in range(4):
            a, b = out[k], out[(k + 1) % 4]; c, e = inn[(k + 1) % 4], inn[k]
            nn = np.cross(b - a, e - a); nn = nn / (np.linalg.norm(nn) + 1e-9)
            mid = (a + b) / 2; cen = np.mean(out, axis=0)
            if np.dot(nn[:2], (mid - cen)[:2]) < 0: nn = -nn          # side walls face away from the segment centre
            quad(a, b, c, e, tuple(nn))
    return np.array(V, np.float32), np.array(N, np.float32), np.array(F, np.int32)

# ---------------------------------------------------------------- ground contact (Phase C: no clipping into the turf)
def skin_points(local, root, v, jo, we):
    R, P = fk(local, root)
    M = np.stack([qmat(R[j]) for j in JOINTS]); T = np.stack([P[j] for j in JOINTS]); B = np.stack([BIND[j] for j in JOINTS])
    out = np.zeros((len(v), 3))
    for k in range(4):
        j = jo[:, k].astype(int); w = we[:, k][:, None]
        out += w * (np.einsum("nij,nj->ni", M[j], v - B[j]) + T[j])
    return out

def build(out_path, lod1=True, preview=None):
    g = GLB(); mi = {k: g.material(k, c, m, r) for k, (c, m, r) in MATS.items()}
    # joints
    jn = {}
    for j in JOINTS:
        p = PARENT[j]; t = BIND[j] - (BIND[p] if p else 0)
        jn[j] = g.node(j, t=t)
    for j in JOINTS:
        ch = [jn[c] for c, p in PARENT.items() if p == j]
        if ch: g.g["nodes"][jn[j]]["children"] = ch
    ibm = []
    for j in JOINTS:
        m = np.eye(4); m[:3, 3] = -BIND[j]; ibm.append(m.T.reshape(-1))
    skin = g.skin([jn[j] for j in JOINTS], ibm, skeleton=jn["hips"])
    meshes = []
    stats = {}
    def add_skinned(name, v, f, regions, jo, we):
        prims = []
        nrm = smooth_normals(v, f)
        for reg in sorted(set(regions)):
            fm = f[np.all(regions[f] == reg, axis=1) | (regions[f[:, 0]] == reg) & ~np.all(regions[f] == regions[f[:, 0:1]], axis=1) & (regions[f[:, 1]] != reg) & False]
            fm = f[regions[f[:, 0]] == reg]
            used = np.unique(fm); remap = -np.ones(len(v), int); remap[used] = np.arange(len(used))
            prims.append(g.primitive(v[used], nrm[used], remap[fm], mi[reg], joints=jo[used], weights=we[used]))
        meshes.append(g.node(name, mesh=g.mesh(name, prims), skin=skin)); stats[name] = len(f)
    body_cache = {}
    for build_name in ("athletic", "heavy", "slim"):
        heavy = build_name == "heavy"; slim = build_name == "slim"
        for lod, (h, tgt) in enumerate([(0.008, 11000), (0.014, 3200)]):
            if lod == 1 and not lod1: continue
            v, f, reg, jo, we = build_body(heavy, h, tgt, slim)
            body_cache[(build_name, lod)] = (v, f, reg, jo, we)
            add_skinned(f"body_{build_name}_lod{lod}", v, f, reg, jo, we)
            print("built body", build_name, lod, len(f), flush=True)
    for name, ((v, f), bone, mat) in build_accessories().items():
        if name.startswith("pad") or name.startswith("glove"):
            jo, we = weights_for(v.astype(float), [bone] * len(v))
        else: jo, we = rigid(v, bone)
        add_skinned(name, v, f, np.array([mat] * len(v), dtype=object), jo, we)
    # bat (rigid child of hand_R) oriented along bind hand direction
    bv, bf, breg = bat_mesh(); bn = smooth_normals(bv, bf)
    prims = []
    for reg in ("willow", "grip"):
        fm = bf[breg[bf[:, 0]] == reg]; used = np.unique(fm); remap = -np.ones(len(bv), int); remap[used] = np.arange(len(used))
        prims.append(g.primitive(bv[used], bn[used], remap[fm], mi[reg]))
    q = qarc((0, 1, 0), bind_dir("hand_R"))
    batn = g.node("bat", mesh=g.mesh("bat", prims), r=q)
    g.g["nodes"][jn["hand_R"]].setdefault("children", []).append(batn); stats["bat"] = len(bf)
    # shirt numbers: rigid children of the chest joint; tens (t), units (u) and centred single digit (c)
    for slot, cx in (("t", 0.040), ("u", -0.040), ("c", 0.0)):
        for d in range(10):
            dv, dn, df = digit_mesh(d, cx)
            nd = g.node(f"num_{slot}{d}", mesh=g.mesh(f"num_{slot}{d}", [g.primitive(dv, dn, df, mi["number"])]))
            g.g["nodes"][jn["chest"]].setdefault("children", []).append(nd); stats[f"num_{slot}{d}"] = len(df)
    rig_root = g.node("cricketer", children=[jn["hips"]] + meshes, root=True)
    # ground probe: the heaviest body + both pads (fielding dives, sweeps and keeper crouches must stay above the turf)
    probe = [body_cache[("heavy", 1 if lod1 else 0)]]
    acc_probe = [(v, *weights_for(v.astype(float), [bone] * len(v))) for name, ((v, f), bone, mat) in build_accessories().items() if name.startswith("pad")]
    ground = {}
    # animations
    for cname, keys in C.CLIPS.items():
        res_keys = [(t, solve(p)[2]) for t, p in keys]
        T = res_keys[-1][0]; fps = 24; times = np.linspace(0, T, max(2, int(np.ceil(T * fps)) + 1))
        rots = {j: [] for j in JOINTS}; hipT = []
        for t in times:
            k = max(i for i in range(len(res_keys)) if res_keys[i][0] <= t + 1e-9); k = min(k, len(res_keys) - 2)
            t0, a = res_keys[k]; t1, b = res_keys[k + 1]
            u = 0 if t1 == t0 else np.clip((t - t0) / (t1 - t0), 0, 1); u = u * u * (3 - 2 * u)
            spec = lerp_spec(a, b, u)
            for _it in range(5):
                local, root, _ = solve(spec)
                lo = min(min(skin_points(local, root, pv[0].astype(float), pv[3], pv[4])[:, 1].min() for pv in probe),
                         min(skin_points(local, root, av.astype(float), aj, aw)[:, 1].min() for av, aj, aw in acc_probe))
                if lo >= 0.0035: break
                # raise the hips in the IK spec: planted feet stay on their targets, the knees simply bend less
                r0 = np.asarray(spec.get("root", BIND["hips"]), float); spec = dict(spec); spec["root"] = tuple(r0 + np.array((0, 0.004 - lo, 0)))
            lift = max(0.0, 0.004 - lo)          # residual: lift the whole body (keeps 4 mm of clearance)
            root = np.asarray(root, float) + np.array((0, lift, 0))
            ground[cname] = min(ground.get(cname, 9.0), lo + lift)
            ground[cname + "#lift"] = max(ground.get(cname + "#lift", 0.0), lift)
            for j in JOINTS: rots[j].append(local[j])
            hipT.append(root)
        ch = [(jn[j], "rotation", times, np.array(rots[j])) for j in JOINTS] + [(jn["hips"], "translation", times, np.array(hipT))]
        g.animation(cname, ch)
    size = g.save(out_path)
    import json
    json.dump({k: round(float(v), 4) for k, v in ground.items()}, open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "ground_report.json"), "w"), indent=1)
    return size, stats, body_cache

if __name__ == "__main__":
    out = sys.argv[1]
    size, stats, _ = build(out)
    print("cricketer.glb", size, "bytes; triangles:", stats)
