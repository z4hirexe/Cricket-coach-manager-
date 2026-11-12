"""Cinematic match previews with the higher-fidelity renderer (shadows, specular, ACES).  usage: match_preview_hq.py stadium.glb cricketer.glb outdir"""
import numpy as np, sys, os
from preview import Model
from pbr_preview import render
S = Model(sys.argv[1]); P = Model(sys.argv[2]); out = sys.argv[3]; os.makedirs(out, exist_ok=True)
names = {n.name for n in P.g.nodes}
BAT = lambda n: n in ("body_athletic_lod0", "helmet", "grille", "pad_L", "pad_R", "glove_L", "glove_R", "eyes", "bat")
FLD = lambda n: n in ("body_athletic_lod0", "eyes", "hair_short")
UMP = lambda n: n in ("body_heavy_lod0", "eyes", "cap")
def actor(anim, t, x, z, yaw, inc, mirror=False, col=None):
    tr = P.triangles(inc, P.anim_index(anim), t); cy, sy = np.cos(np.radians(yaw)), np.sin(np.radians(yaw)); sx = -1 if mirror else 1
    R = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]) @ np.diag([sx, 1, 1]); o = []
    for v, n, f, c, m, *rest in tr:
        c2 = np.array(col) if (col is not None and np.allclose(c, [0.1, 0.3, 0.8])) else c
        o.append((v @ R.T + [x, 0, z], n @ R.T, f, c2, m) + tuple(rest))
    return o
off = -1
mode = sys.argv[4] if len(sys.argv) > 4 else "day"
def subdiv(t, maxlen=1.5):
    """split long triangles (preview has no near-plane clipping; Filament clips on GPU)"""
    v, n, f, c, m, tex, uv, vc, emi = (tuple(t) + (None,) * 9)[:9]
    if len(f) > 64: return t
    V = list(v); N = list(n); U = list(uv) if uv is not None else None; C = list(vc) if vc is not None else None; F = [tuple(x) for x in f]
    for _ in range(8):
        nf = []; done = True
        for a, b, cc in F:
            e = [np.linalg.norm(V[a] - V[b]), np.linalg.norm(V[b] - V[cc]), np.linalg.norm(V[cc] - V[a])]
            k = int(np.argmax(e))
            if e[k] <= maxlen: nf.append((a, b, cc)); continue
            done = False; p, q, r = [(a, b, cc), (b, cc, a), (cc, a, b)][k]
            V.append((V[p] + V[q]) / 2); N.append((N[p] + N[q]) / 2)
            if U is not None: U.append((U[p] + U[q]) / 2)
            if C is not None: C.append((C[p] + C[q]) / 2)
            mi = len(V) - 1; nf += [(p, mi, r), (mi, q, r)]
        F = nf
        if done: break
    return (np.array(V), np.array(N), np.array(F), c, m, tex, None if U is None else np.array(U), None if C is None else np.array(C), emi)
scene = [subdiv(t) for t in S.triangles(lambda n: not n.startswith("sky") or n == ("sky_day" if mode == "day" else "sky_night"))]
scene += actor("bat_cover_drive", 0.62, off * 0.25, 1.05, -90, BAT)
scene += actor("idle", 0.0, 0.9, 21.52, 180, BAT)
scene += actor("bowl_fast", 1.68, -0.45, 21.67, 180, FLD, col=(0.8, 0.1, 0.1))
scene += actor("keep_crouch", 0.0, off * 0.25, -8.0, 0, lambda n: n in ("body_athletic_lod0", "pad_L", "pad_R", "glove_L", "glove_R", "cap", "eyes"), col=(0.8, 0.1, 0.1))
scene += actor("ump_idle", 0.0, 0.63, 22.42, 180, UMP)
for a, r in ((160, 17), (55, 28), (300, 27), (100, 22)):
    x = off * np.sin(np.radians(a)) * r; z = np.cos(np.radians(a)) * r + 0.6
    scene += actor("field_ready", 0.0, x, z, np.degrees(np.arctan2(-x, 1 - z)), FLD, col=(0.8, 0.1, 0.1))
if mode == "day":
    light = dict(sun_dir=(0.45, -0.75, 0.48), sun=(3.2, 3.0, 2.7), amb=((0.62, 0.72, 0.9), (0.25, 0.28, 0.2)), exposure=0.85, bg=(0.45, 0.62, 0.85))
else:
    light = dict(sun_dir=(-0.25, -0.9, -0.3), sun=(3.4, 3.4, 3.6), amb=((0.22, 0.27, 0.42), (0.12, 0.14, 0.1)), exposure=1.0, bg=(0.03, 0.05, 0.12))
views = {"broadcast": ((0, 6.5, 62.12), (0, 1.0, 2.0), 13), "side_on": ((-6, 1.9, 5.5), (0, 1.0, 1.0), 38),
         "wide": ((38, 14, -18), (0, 0, 14), 50)}
sel = sys.argv[5].split(",") if len(sys.argv) > 5 else list(views)
for k in sel:
    e, t, fv = views[k]
    im = render(scene, e, t, 1280, 720, fov=fv, shadow_center=(0, 0, 10), shadow_extent=38, ss=2, **light)
    im.save(f"{out}/match_{mode}_{k}.png"); print("saved", k, flush=True)
