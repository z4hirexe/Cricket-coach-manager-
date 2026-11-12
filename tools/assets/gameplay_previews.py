"""Render gameplay previews from REAL DeliveryPlan frames (dumped by the DeliveryPlanDump unit test).

usage: gameplay_previews.py plan_frames.json stadium.glb cricketer.glb outdir [W] [ss] [only=a,b]
Every actor's position, facing, mirroring, animation clip and clip time - and the ball and stumps - come straight from the
game's planner, so these images show what the in-game 3D view does at that instant (the Android renderer is Filament; this is
the offline CPU renderer used for all previews).
"""
import sys, os, json, time, numpy as np
from preview import Model
from render_hq import Scene, render, LIGHTS, FOG, EMI

frames_path, stadium, cricketer, out = sys.argv[1:5]
W = int(sys.argv[5]) if len(sys.argv) > 5 else 1280
ss = int(sys.argv[6]) if len(sys.argv) > 6 else 2
only = sys.argv[7].split(",") if len(sys.argv) > 7 else None
os.makedirs(out, exist_ok=True)
D = json.load(open(frames_path))
S = Model(stadium); P = Model(cricketer)
SW = S.world(); NODE = {n.name: i for i, n in enumerate(S.g.nodes) if n.name}

BAT = ("helmet", "grille", "pad_L", "pad_R", "glove_L", "glove_R", "eyes", "bat")
KEEP = ("pad_L", "pad_R", "glove_L", "glove_R", "cap", "eyes")
def meshes(role, i):
    body = "body_heavy_lod0" if role == "ump" or i in (8, 13) else "body_athletic_lod0"
    extra = {"bat": BAT, "keep": KEEP, "ump": ("eyes", "cap")}.get(role, ("eyes", "hair_short" if i % 3 else "cap"))
    keep = set((body,) + extra)
    return lambda n: n in keep
ROLE = ["bat", "bat", "fld", "keep", "ump", "ump"] + ["fld"] * 9
BLUE = (0.04, 0.20, 0.72); GOLD = (0.95, 0.70, 0.08); GREEN = (0.05, 0.30, 0.14)
def tint(role):
    if role == "ump": return (lambda m: m == "jersey", (0.30, 0.55, 0.80))
    if role == "bat": return (lambda m: m in ("jersey", "trousers", "helmet"), BLUE)
    return (lambda m: m in ("jersey",), GOLD)

def place(x, z, yaw, mirror):
    cy, sy = np.cos(np.radians(yaw)), np.sin(np.radians(yaw)); R = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    M = np.diag([-1.0, 1, 1]) if mirror else np.eye(3)
    A = R @ M
    return lambda v, n: (v @ A.T + [x, 0, z], n @ A.T)

def rot(pitch, roll):
    cp, sp = np.cos(np.radians(pitch)), np.sin(np.radians(pitch)); cr, sr = np.cos(np.radians(roll)), np.sin(np.radians(roll))
    Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]); Rz = np.array([[cr, -sr, 0], [sr, cr, 0], [0, 0, 1]])
    return Rx @ Rz

def stumps_xf(end, z, k, dirz):
    """same motion as MatchDirector.breakStumps"""
    xf = {}
    e = min(1.0, max(0.0, k * 3))
    for i in range(3):
        base = np.array([(i - 1) * 0.0953, 0, z]); R = rot(dirz * 55 * e * (0.6 + 0.3 * i), (i - 1) * 25 * e)
        new = np.array([(i - 1) * 0.0953, 0, z + dirz * 0.05 * e * (i + 1)])
        xf[f"stump_{end}_{i}"] = (lambda b, R, nw: (lambda v, n: ((v - b) @ R.T + nw, n @ R.T)))(base, R, new)
    for i in range(2):
        tt = min(0.95, max(0.0, k * 2.7)); y = max(0.02, 0.718 + 1.6 * tt - 3.5 * tt * tt)
        base = np.array([(i * 2 - 1) * 0.054, 0.718, z]); new = np.array([(i * 2 - 1) * (0.054 + 1.2 * tt), y, z + dirz * 1.8 * tt])
        R = rot(720 * tt, 360 * tt * (i * 2 - 1))
        xf[f"bail_{end}_{i}"] = (lambda b, R, nw: (lambda v, n: ((v - b) @ R.T + nw, n @ R.T)))(base, R, new)
    return xf

# camera per shot: (scene, frame, eye, target, fov, mode, title)
def cams():
    return [
        ("delivery", "runup", (2.2, 2.4, 33.0), (-0.4, 1.3, 20.0), 34, "night", "01 Run-up: bowler approaching the crease, non-striker backing up"),
        ("delivery", "release", (-6.5, 2.0, 24.5), (-0.4, 1.4, 19.5), 40, "night", "02 Release: ball leaves the bowling hand at the delivery stride"),
        ("contact", "impact", (-3.4, 1.0, 3.3), (-0.1, 0.6, 1.2), 34, "night", "03 Contact: the ball meets the bat sweet spot on the impact frame"),
        ("contact", "after", (0.0, 9.0, 42.0), (-1.0, 0.6, 2.5), 13, "night", "04 Cover drive: ball leaving the bat towards the off side (TV camera)"),
        ("catch", "skied", (36.0, 1.5, 24.0), (16.0, 10.0, 9.0), 62, "dusk", "05 Skied: ball in the air, midwicket settling under it"),
        ("catch", "taken", (17.0, 1.8, 18.5), (23.0, 1.4, 13.1), 34, "dusk", "06 Caught: catch taken at midwicket, ball in the hands"),
        ("running", "first", (-26.0, 7.0, 10.0), (0.0, 0.8, 10.0), 38, "day", "07 Running: batters in their lanes, fielder chasing, keeper to the stumps"),
        ("running", "throw", (24.0, 4.0, -18.0), (8.0, 1.5, -3.0), 44, "day", "08 Throw: the return to the keeper while the batters complete the 2nd run"),
        ("runout", "break", (7.0, 1.5, 14.5), (-0.5, 0.7, 19.6), 46, "day", "09 Run-out: direct hit, the striker short of his ground"),
        ("stumping", "stumped", (3.6, 1.3, -2.6), (0.0, 0.7, 1.0), 44, "night", "10 Stumping: batter out of his crease, keeper breaks the wicket"),
        ("four_dive", "dive", (-53.5, 1.3, 13.0), (-61.0, 0.4, 18.8), 38, "night", "11 Boundary: full-length dive, ball racing over the rope for FOUR"),
        ("bowled", "hit", (-3.6, 0.9, 4.2), (0.0, 0.5, 0.0), 36, "night", "12 Bowled: stumps knocked back, bails flying"),
        ("lbw", "finger", (2.8, 2.2, 29.0), (0.0, 1.4, 21.0), 36, "day", "13 LBW: appeal and the umpire's finger"),
    ]

for sc, fr, eye, tgt, fov, mode, title in cams():
    name = f"{title[:2]}_{sc}_{fr}"
    if only and sc not in only and name not in only: continue
    f = D[sc]["frames"][fr]; t0 = time.time()
    scn = Scene()
    xf = {}
    if f["breakBat"] >= 0: xf.update(stumps_xf("bat", 0.0, f["breakBat"], -1))
    if f["breakBowl"] >= 0: xf.update(stumps_xf("bowl", 20.12, f["breakBowl"], 1))
    sky = "sky_" + mode
    scn.add_model(S, lambda n: ((not n.startswith("sky")) or n == sky) and n != "ball" and n not in xf,
                  tint=(lambda m: m == "sight_screen", (0.012, 0.012, 0.014)) if mode != "day" else None)
    # moving stumps / bails: re-add each piece in local coordinates of its rest position
    for nm, fn in xf.items():
        scn.add_model(S, (lambda k: (lambda n: n == k))(nm), xform=fn)
    # ball (stadium.glb node "ball" rests at (0, 0.036, 18))
    b = np.array(f["ball"]); bo = SW[NODE["ball"]][:3, 3]
    scn.add_model(S, lambda n: n == "ball", xform=lambda v, n: (v - bo + b, n), tint=(lambda m: True, (0.92, 0.92, 0.88)) if mode != "day" else None)
    for i, a in enumerate(f["actors"]):
        role = ROLE[i]
        anim = P.anim_index(a["clip"])
        scn.add_model(P, meshes(role, i), anim, a["ct"], place(a["x"], a["z"], a["yaw"], a["mirror"]), tint=tint(role), flip=a["mirror"])
    im = render(scn, eye, tgt, W, W * 9 // 16, fov=fov, ss=ss, light=LIGHTS[mode], fog=FOG[mode], emissive_scale=EMI[mode],
                shadow_center=(tgt[0], 0, tgt[2]), shadow_extent=60, shadow_res=2048 if ss > 1 else 1024, wear_alpha=0.45)
    # caption bar
    from PIL import ImageDraw, ImageFont
    d = ImageDraw.Draw(im); H = im.size[1]
    try: font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", max(14, W // 64))
    except Exception: font = ImageFont.load_default()
    d.rectangle([0, H - W // 26, W, H], fill=(5, 10, 24))
    d.text((W // 80, H - W // 26 + W // 160), title + f"   [t={f['t']:.2f}s]", fill=(233, 185, 73), font=font)
    im.save(f"{out}/{name}.png"); print("saved", name, round(time.time() - t0, 1), "s", flush=True)
