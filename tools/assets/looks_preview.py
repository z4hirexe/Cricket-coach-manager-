"""Phase C validation render: the shipped cricketer.glb dressed as several individual players (build / height / skin / hair /
facial hair / shirt number / kit / gear) mid-animation, mirroring Actor.dress() in Arena3D.kt.  Software renderer (render_hq),
so it is an approximation of the Filament output on the phone.
usage: python3 looks_preview.py out.png [cricketer.glb]"""
import sys, numpy as np
from preview import Model
from render_hq import Scene, render, LIGHTS

OUT = sys.argv[1]; GLB = sys.argv[2] if len(sys.argv) > 2 else "../../app/src/main/assets/models/cricketer.glb"
P = Model(GLB)
SKINS = [(0.62, 0.43, 0.31), (0.48, 0.31, 0.21), (0.36, 0.23, 0.15), (0.25, 0.16, 0.11), (0.78, 0.60, 0.48), (0.70, 0.52, 0.40)]
HAIRS = [(0.03, 0.025, 0.02), (0.08, 0.05, 0.03), (0.20, 0.13, 0.07), (0.55, 0.42, 0.24), (0.42, 0.17, 0.06), (0.42, 0.41, 0.40)]
HAIR_MESH = {"B": "hair_buzz", "S": "hair_short", "L": "hair_long", "C": "hair_curly", "U": "hair_bun", "N": None}
FACE_MESH = {"S": "beard_stubble", "B": "beard", "G": "beard_goatee", "M": "moustache", "N": None}
BUILD = {"S": "slim", "A": "athletic", "H": "heavy"}
def lin(hexrgb): return tuple(((hexrgb >> s & 255) / 255) ** 2.2 for s in (16, 8, 0))

# (label, appearance code, number, jersey, trim, gear, clip, t)
PLAYERS = [
    ("Batter · curated", "0 S 0 B A 175", 18, 0x1565C0, 0xFF9800, "BAT", "bat_cover_drive", 0.62),
    ("Pacer · curated", "1 S 0 S S 178", 93, 0x0D47A1, 0xFFC107, "BOWL", "bowl_fast", 1.62),
    ("Keeper · curated", "0 L 0 S A 178", 7, 0xFDD835, 0x0D47A1, "KEEP", "keep_crouch", 0.5),
    ("Fictional · heavy", "2 C 0 B H 186", 45, 0x7B1F3A, 0xFFC107, "FIELD", "field_catch", 0.55),
    ("Fictional · slim", "4 S 3 N S 183", 9, 0x0D2B6B, 0xE53935, "FIELD", "field_throw", 0.45),
    ("Fictional · celebration", "5 U 0 M A 170", 31, 0x006B3F, 0xFFC107, "FIELD", "celebrate", 0.55),
]

scn = Scene()
for i, (label, code, num, jer, trim, gear, clip, t) in enumerate(PLAYERS):
    skin, hair, hc, face, build, h = code.split()
    s = int(h) / 180.0
    show = {f"body_{BUILD[build]}_lod0", "eyes"}
    if gear == "BAT": show |= {"helmet", "grille", "pad_L", "pad_R", "glove_L", "glove_R", "bat"}
    if gear == "KEEP": show |= {"pad_L", "pad_R", "glove_L", "glove_R", "cap"}
    headgear = gear in ("BAT", "KEEP")
    hm = HAIR_MESH[hair]
    if hm and (not headgear or hair == "L"): show.add(hm)
    if FACE_MESH[face]: show.add(FACE_MESH[face])
    if num >= 10: show |= {f"num_t{num // 10}", f"num_u{num % 10}"}
    else: show.add(f"num_c{num}")
    x0 = (i - (len(PLAYERS) - 1) / 2) * 1.55
    dz = {"heavy": -0.011, "slim": 0.005}.get(BUILD[build], 0.0)
    yaw = np.radians({"BAT": 60, "BOWL": -70, "KEEP": 20}.get(gear, 25))
    c, sn = np.cos(yaw), np.sin(yaw)
    Ry = np.array([[c, 0, sn], [0, 1, 0], [-sn, 0, c]])
    def xf(v, n, x0=x0, s=s, Ry=Ry): return (v * s) @ Ry.T + np.array([x0, 0, 0]), n @ Ry.T
    def xfn(v, n, x0=x0, s=s, Ry=Ry, dz=dz): return ((v + np.array([0, 0, dz])) * s) @ Ry.T + np.array([x0, 0, 0]), n @ Ry.T
    a = P.anim_index(clip)
    n0 = len(scn.items)
    scn.add_model(P, lambda n, show=show: n in show and not n.startswith("num_"), a, t, xf)
    scn.add_model(P, lambda n, show=show: n in show and n.startswith("num_"), a, t, xfn)
    sk = SKINS[int(skin)]; hcol = HAIRS[int(hc)]
    col = {"skin": sk, "hair": hcol, "stubble": tuple(sk[k] * 0.55 + hcol[k] * 0.45 for k in range(3)), "jersey": lin(jer), "trim": lin(trim),
           "trousers": tuple(x * 0.35 for x in lin(jer)), "number": lin(trim), "cap": lin(jer), "helmet": tuple(x * 0.6 for x in lin(jer))}
    for it in scn.items[n0:]:
        if it["mname"] in col: it["base"][:3] = col[it["mname"]]

# turf disc
th = np.linspace(0, 2 * np.pi, 49)[:-1]
V = np.vstack([[0, 0, 0], np.c_[6.5 * np.cos(th), np.zeros_like(th), 3.2 * np.sin(th)]])
F = np.array([[0, 1 + (k + 1) % 48, 1 + k] for k in range(48)])
scn.items.append(dict(P=V, N=np.tile([0, 1, 0], (len(V), 1)).astype(float), F=F, name="turf", mname="turf", base=np.array([0.10, 0.22, 0.07, 1.0]),
                      metal=0.0, rough=1.0, cast=False, unlit=False, blend=False, double=True, emi=np.zeros(3), tex=None, etex=None, UV=None, C=None))
light = dict(LIGHTS["day"]); light["dir"] = (0.3, -0.8, -0.5)
import os
EYE = (0.0, 1.45, -5.2) if os.environ.get("VIEW") == "back" else (0.0, 1.45, 5.2)
img = render(scn, EYE, (0.0, 0.95, 0.0), W=1280, H=560, fov=42, ss=2, light=light, shadow_center=(0, 0, 0), shadow_extent=12.0, shadow_res=1024)
img.save(OUT); print("saved", OUT)
