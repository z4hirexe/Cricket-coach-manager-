"""Compose a match frame exactly as Arena3D/MatchDirector place actors (validates handedness / facing / positions)."""
import numpy as np, sys
from preview import Model, render
from PIL import Image
S = Model(sys.argv[1]); P = Model(sys.argv[2]); out = sys.argv[3]
BAT = lambda n: n in ("body_athletic_lod0", "helmet", "grille", "pad_L", "pad_R", "glove_L", "glove_R", "eyes", "bat")
FLD = lambda n: n in ("body_athletic_lod0", "eyes", "hair_short")
UMP = lambda n: n in ("body_heavy_lod0", "eyes", "cap")
def actor(anim, t, x, z, yaw, inc, mirror=False, col=None):
    tr = P.triangles(inc, P.anim_index(anim), t); cy, sy = np.cos(np.radians(yaw)), np.sin(np.radians(yaw)); sx = -1 if mirror else 1
    R = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]) @ np.diag([sx, 1, 1]); outl = []
    for v, n, f, c, m, *rest in tr:
        c2 = c
        if col is not None and np.allclose(c, [0.1, 0.3, 0.8]): c2 = np.array(col)
        outl.append((v @ R.T + [x, 0, z], n @ R.T, f, c2, m) + tuple(rest))
    return outl
off = -1  # RHB off side is -x
scene = S.triangles(lambda n: not n.startswith("sky") or n == "sky_day")
scene += actor("bat_cover_drive", 0.62, off * 0.25, 1.05, -90, BAT)
scene += actor("idle", 0.0, 0.9, 21.52, 180, BAT)
scene += actor("bowl_fast", 1.68, -0.45, 21.67, 180, FLD, col=(0.8, 0.1, 0.1))
scene += actor("keep_crouch", 0.0, off * 0.25, -8.0, 0, lambda n: n in ("body_athletic_lod0", "pad_L", "pad_R", "glove_L", "glove_R", "cap", "eyes"), col=(0.8, 0.1, 0.1))
scene += actor("ump_idle", 0.0, 0.63, 22.42, 180, UMP)
for a, r in ((160, 17), (55, 28), (300, 27)):
    x = off * np.sin(np.radians(a)) * r; z = np.cos(np.radians(a)) * r + 0.6
    scene += actor("field_ready", 0.0, x, z, np.degrees(np.arctan2(-x, 1 - z)), FLD, col=(0.8, 0.1, 0.1))
views = [((0, 6.5, 62.12), (0, 1.0, 2.0), 14), ((34, 3.2, 10), (0, 1, 6), 26), ((-5, 2.0, 6), (0, 1.0, 1.0), 40), ((0, 1.8, -7), (0, 1.3, 18), 30)]
sheet = Image.new("RGB", (640 * 2, 360 * 2))
for i, (e, t, fv) in enumerate(views):
    im = render(scene, np.array(e, float), np.array(t, float), 640, 360, fov=fv, bg=(90, 130, 180)); sheet.paste(im, ((i % 2) * 640, (i // 2) * 360))
sheet.save(out)
