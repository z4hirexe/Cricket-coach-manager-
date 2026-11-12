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
    "broadcast": ((0, 7.0, 62.0), (0, 1.2, 0.0), 22, (0, 0, 10), 200),
    "wide": ((52, 26, 58), (0, 4, 4), 50, (0, 0, 10), 220),
    "exterior": ((210, 150, 250), (0, 10, 4), 34, (0, 0, 10), 330),
    "aerial": ((0, 190, CZ + 0.1), (0, 0, CZ), 58, (0, 0, 10), 330),
    "pavilion": ((8, 9, 40), (0, 12, -72), 46, (0, 0, -20), 220),
    "pitch": ((2.6, 2.2, -4.2), (0, 0, 4.5), 50, (0, 0, 2), 24),
    "stands": ((-30, 2.0, 30), (-75, 18, 50), 60, (-50, 0, 30), 180),
    "boundary": ((40, 1.6, -18), (0, 4, 30), 62, (20, 0, 10), 170),
    "tv": ((0, 11.0, 92.0), (0, 1.0, 2.5), 10.5, (0, 0, 8), 200),
    "dusk_wide": ((-50, 22, 64), (0, 3, 4), 52, (0, 0, 10), 220),
}
scn = Scene()
# white-ball (ODI/T20, dusk & night) -> black sight screens, as Arena3D.setBallColor does
scn.add_model(S, lambda n: (not n.startswith("sky")) or n == sky, tint=(lambda m: m == "sight_screen", (0.012, 0.012, 0.014)) if mode != "day" else None)
if len(sys.argv) > 7:
    P = Model(sys.argv[7])
    BAT = lambda n: n in ("body_athletic_lod0", "helmet", "grille", "pad_L", "pad_R", "glove_L", "glove_R", "eyes", "bat")
    FLD = lambda n: n in ("body_athletic_lod0", "eyes", "hair_short")
    KEEP = lambda n: n in ("body_athletic_lod0", "pad_L", "pad_R", "glove_L", "glove_R", "cap", "eyes")
    UMP = lambda n: n in ("body_heavy_lod0", "eyes", "cap")
    def place(x, z, yaw):
        cy, sy = np.cos(np.radians(yaw)), np.sin(np.radians(yaw)); Rm = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
        return lambda v, n: (v @ Rm.T + [x, 0, z], n @ Rm.T)
    blue = (lambda m: m in ("jersey", "trousers"), (0.05, 0.22, 0.75)); red = (lambda m: m in ("jersey", "trousers"), (0.75, 0.08, 0.06))
    umpk = (lambda m: m in ("jersey",), (0.30, 0.55, 0.80))
    off = -1
    scn.add_model(P, BAT, P.anim_index("bat_stance"), 0.0, place(off * 0.25, 1.05, -90), tint=blue)
    scn.add_model(P, BAT, P.anim_index("idle"), 0.0, place(0.9, 21.52, 180), tint=blue)
    scn.add_model(P, FLD, P.anim_index("bowl_fast"), 1.2, place(-0.45, 22.4, 180), tint=red)
    scn.add_model(P, KEEP, P.anim_index("keep_crouch"), 0.0, place(off * 0.25, -8.0, 0), tint=red)
    scn.add_model(P, UMP, P.anim_index("ump_idle"), 0.0, place(0.63, 22.6, 180), tint=umpk)
    for a, r in ((160, 17), (150, 18), (55, 28), (300, 27), (100, 22), (20, 30), (240, 26)):
        x = off * np.sin(np.radians(a)) * r; z = np.cos(np.radians(a)) * r + 0.6
        scn.add_model(P, FLD, P.anim_index("field_ready"), 0.0, place(x, z, np.degrees(np.arctan2(-x, 1 - z))), tint=red)
for k in views:
    e, t, fv, sc, ext = VIEWS[k]; t0 = time.time()
    im = render(scn, e, t, W, W * 9 // 16, fov=fv, ss=ss, light=LIGHTS[mode], fog=FOG[mode], emissive_scale=EMI[mode], shadow_center=sc, shadow_extent=ext,
                shadow_res=2048 if ss > 1 else 1024, wear_alpha=0.85)
    if k == "tv" or mode == "night": pass
    im.save(f"{out}/{k}_{mode}.png"); print("saved", k, mode, round(time.time() - t0, 1), "s", flush=True)
