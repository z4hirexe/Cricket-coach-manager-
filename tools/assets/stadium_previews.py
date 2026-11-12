"""Render stadium v2 previews.  usage: stadium_previews.py stadium.glb outdir [views] [mode] [W] [ss] [cricketer.glb]"""
import sys, os, time, numpy as np
from preview import Model
from render_hq import Scene, render, LIGHTS, FOG, EMI
glb, out = sys.argv[1], sys.argv[2]; os.makedirs(out, exist_ok=True)
views = sys.argv[3].split(",") if len(sys.argv) > 3 else ["broadcast"]
mode = sys.argv[4] if len(sys.argv) > 4 else "day"; W = int(sys.argv[5]) if len(sys.argv) > 5 else 1280; ss = int(sys.argv[6]) if len(sys.argv) > 6 else 2
S = Model(glb); CZ = 10.06
sky = "sky_" + mode
VIEWS = {
    "broadcast": ((0, 7.0, 62.0), (0, 1.2, 0.0), 22, (0, 0, 10), 70),
    "wide": ((52, 26, 58), (0, 4, 4), 50, (0, 0, 10), 160),
    "exterior": ((210, 150, 250), (0, 10, 4), 34, (0, 0, 10), 330),
    "aerial": ((0, 190, CZ + 0.1), (0, 0, CZ), 58, (0, 0, 10), 330),
    "pavilion": ((8, 9, 40), (0, 12, -72), 46, (0, 0, -20), 160),
    "pitch": ((2.6, 2.2, -4.2), (0, 0, 4.5), 50, (0, 0, 2), 24),
    "stands": ((-30, 2.0, 30), (-75, 18, 50), 60, (-50, 0, 30), 180),
    "boundary": ((40, 1.6, -18), (0, 4, 30), 62, (20, 0, 10), 170),
}
scn = Scene()
scn.add_model(S, lambda n: (not n.startswith("sky")) or n == sky)
if len(sys.argv) > 7:
    P = Model(sys.argv[7])
    BAT = lambda n: n in ("body_athletic_lod0", "helmet", "grille", "pad_L", "pad_R", "glove_L", "glove_R", "eyes", "bat")
    def place(x, z, yaw):
        cy, sy = np.cos(np.radians(yaw)), np.sin(np.radians(yaw)); Rm = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
        return lambda v, n: (v @ Rm.T + [x, 0, z], n @ Rm.T)
    scn.add_model(P, BAT, P.anim_index("bat_stance"), 0.0, place(-0.25, 1.05, -90))
for k in views:
    e, t, fv, sc, ext = VIEWS[k]; t0 = time.time()
    im = render(scn, e, t, W, W * 9 // 16, fov=fv, ss=ss, light=LIGHTS[mode], fog=FOG[mode], emissive_scale=EMI[mode], shadow_center=sc, shadow_extent=ext,
                shadow_res=2048 if ss > 1 else 1024, wear_alpha=0.85)
    im.save(f"{out}/{k}_{mode}.png"); print("saved", k, mode, round(time.time() - t0, 1), "s", flush=True)
