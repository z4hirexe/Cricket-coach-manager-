"""Skeleton, quaternion maths, FK and the IK pose solver shared by the model builder and the preview renderer.
Character frame: x = character's LEFT, y = up, z = forward (chest direction). Metres."""
import numpy as np

def qmul(a, b):
    ax, ay, az, aw = a; bx, by, bz, bw = b
    return np.array([aw * bx + ax * bw + ay * bz - az * by, aw * by - ax * bz + ay * bw + az * bx,
                     aw * bz + ax * by - ay * bx + az * bw, aw * bw - ax * bx - ay * by - az * bz])
def qaxis(axis, deg):
    a = np.asarray(axis, float); a = a / np.linalg.norm(a); h = np.radians(deg) / 2
    return np.array([*(a * np.sin(h)), np.cos(h)])
def qeuler(rx=0, ry=0, rz=0):  # yaw * pitch * roll
    return qmul(qaxis((0, 1, 0), ry), qmul(qaxis((1, 0, 0), rx), qaxis((0, 0, 1), rz)))
def qinv(q): return np.array([-q[0], -q[1], -q[2], q[3]])
def qrot(q, v):
    u = q[:3]; w = q[3]; v = np.asarray(v, float)
    return v + 2 * np.cross(u, np.cross(u, v) + w * v)
def qarc(u, v):
    u = np.asarray(u, float) / np.linalg.norm(u); v = np.asarray(v, float) / np.linalg.norm(v)
    d = np.dot(u, v)
    if d < -0.9999:
        ax = np.cross(u, (1, 0, 0)); ax = ax if np.linalg.norm(ax) > 1e-3 else np.cross(u, (0, 1, 0))
        return qaxis(ax, 180)
    c = np.cross(u, v); q = np.array([*c, 1 + d]); return q / np.linalg.norm(q)
def qmat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
def norm(v): v = np.asarray(v, float); return v / np.linalg.norm(v)

# ------------------------------------------------------------------ skeleton (bind = A-pose, arms 45° down)
UA, FA, HA = 0.29, 0.26, 0.17
TH, SH = 0.43, 0.42
def _arm(side):
    s = 1 if side == "L" else -1; c = np.cos(np.radians(45)); sn = np.sin(np.radians(45))
    sh = np.array([0.18 * s, 1.45, 0.0]); el = sh + np.array([UA * sn * s, -UA * c, 0]); wr = el + np.array([FA * sn * s, -FA * c, 0])
    tip = wr + np.array([HA * sn * s, -HA * c, 0])
    return sh, el, wr, tip
JOINTS = ["hips", "spine", "chest", "neck", "head", "clav_L", "upperarm_L", "forearm_L", "hand_L", "clav_R", "upperarm_R", "forearm_R", "hand_R",
          "thigh_L", "shin_L", "foot_L", "thigh_R", "shin_R", "foot_R"]
PARENT = {"hips": None, "spine": "hips", "chest": "spine", "neck": "chest", "head": "neck", "clav_L": "chest", "upperarm_L": "clav_L", "forearm_L": "upperarm_L",
          "hand_L": "forearm_L", "clav_R": "chest", "upperarm_R": "clav_R", "forearm_R": "upperarm_R", "hand_R": "forearm_R",
          "thigh_L": "hips", "shin_L": "thigh_L", "foot_L": "shin_L", "thigh_R": "hips", "shin_R": "thigh_R", "foot_R": "shin_R"}
BIND = {"hips": np.array([0, 0.97, 0.0]), "spine": np.array([0, 1.08, 0.0]), "chest": np.array([0, 1.27, 0.0]), "neck": np.array([0, 1.49, 0.0]), "head": np.array([0, 1.60, 0.01])}
TIP = {"head": np.array([0, 1.81, 0.01])}
for sd in "LR":
    s = 1 if sd == "L" else -1
    sh, el, wr, tip = _arm(sd)
    BIND["clav_" + sd] = np.array([0.03 * s, 1.44, 0.01]); BIND["upperarm_" + sd] = sh; BIND["forearm_" + sd] = el; BIND["hand_" + sd] = wr; TIP["hand_" + sd] = tip
    BIND["thigh_" + sd] = np.array([0.095 * s, 0.93, 0]); BIND["shin_" + sd] = np.array([0.10 * s, 0.50, 0.0]); BIND["foot_" + sd] = np.array([0.105 * s, 0.08, -0.01])
    TIP["foot_" + sd] = np.array([0.11 * s, 0.025, 0.16])
CHILD = {"hips": "spine", "spine": "chest", "chest": "neck", "neck": "head", "clav_L": "upperarm_L", "upperarm_L": "forearm_L", "forearm_L": "hand_L",
         "clav_R": "upperarm_R", "upperarm_R": "forearm_R", "forearm_R": "hand_R", "thigh_L": "shin_L", "shin_L": "foot_L", "thigh_R": "shin_R", "shin_R": "foot_R"}
def seg_end(j): return BIND[CHILD[j]] if j in CHILD else TIP[j]
def bind_dir(j): return norm(seg_end(j) - BIND[j])

def fk(local, hips_pos):
    """local: joint -> quaternion. returns world rotation & position dicts."""
    R, P = {}, {}
    for j in JOINTS:
        p = PARENT[j]; q = local.get(j, np.array([0, 0, 0, 1.0]))
        if p is None: R[j] = q; P[j] = np.asarray(hips_pos, float)
        else: R[j] = qmul(R[p], q); P[j] = P[p] + qrot(R[p], BIND[j] - BIND[p])
    return R, P

def two_bone(S, T, a, b, pole):
    d = T - S; L = np.linalg.norm(d); L = min(max(L, 1e-4), a + b - 1e-3)
    u = d / np.linalg.norm(d)
    cosA = np.clip((a * a + L * L - b * b) / (2 * a * L), -1, 1); sinA = np.sqrt(1 - cosA ** 2)
    w = np.asarray(pole, float) - np.dot(pole, u) * u
    w = w / np.linalg.norm(w) if np.linalg.norm(w) > 1e-6 else np.array([0, 0, 1.0])
    E = S + a * (cosA * u + sinA * w); Tc = S + u * L
    return norm(E - S), norm(Tc - E)

def solve(spec):
    """spec keys: root, hipR, spine, chest, neck, head (eulers), handL/handR (wrist pos), elbowL/R (pole), hdirL/hdirR,
       footL/footR (ankle pos), kneeL/R (pole), toeL/toeR (foot direction), bat=(wristR, dir) shortcut."""
    spec = {k: v for k, v in spec.items() if v is not None}
    if "bat" in spec:
        w, dr = spec.pop("bat"); dr = norm(dr); w = np.asarray(w, float)
        spec["handR"] = w; spec["hdirR"] = dr; spec["elbowR"] = spec.get("elbowRbat", (-0.3, -0.7, -0.4))
        if "handL" not in spec or "_batL" in spec:
            spec["handL"] = w - 0.10 * dr; spec["hdirL"] = dr; spec["elbowL"] = (0.2, -0.6, 0.6); spec["_batL"] = 1
    res = {k: spec.get(k, (0, 0, 0)) for k in ("hipR", "spine", "chest", "neck", "head", "clavL", "clavR")}
    local = {}
    root = np.asarray(spec.get("root", BIND["hips"]), float); res["root"] = root
    local["hips"] = qeuler(*spec.get("hipR", (0, 0, 0)))
    for j in ("spine", "chest", "neck", "head"): local[j] = qeuler(*spec.get(j, (0, 0, 0)))
    for sd in "LR":
        local["clav_" + sd] = qeuler(*spec.get("clav" + sd, (0, 0, 0)))
    R, P = fk(local, root)
    for sd in "LR":
        s = 1 if sd == "L" else -1
        ua, fa, ha = "upperarm_" + sd, "forearm_" + sd, "hand_" + sd
        S = P[ua]
        T = np.asarray(spec["hand" + sd], float) if "hand" + sd in spec else S + qrot(R["chest"], (0.06 * s, -0.53, 0.04))
        pole = spec.get("elbow" + sd, qrot(R["chest"], (0.3 * s, -0.2, -1.0)))
        u, f = two_bone(S, T, UA, FA, np.asarray(pole, float))
        res["hand" + sd] = T; res["elbow" + sd] = pole
        Rp = R["clav_" + sd]
        local[ua] = qarc(bind_dir(ua), qrot(qinv(Rp), u)); Rua = qmul(Rp, local[ua])
        local[fa] = qarc(bind_dir(fa), qrot(qinv(Rua), f)); Rfa = qmul(Rua, local[fa])
        hd = spec.get("hdir" + sd); res["hdir" + sd] = hd if hd is not None else f
        local[ha] = qarc(bind_dir(ha), qrot(qinv(Rfa), hd)) if hd is not None else np.array([0, 0, 0, 1.0])
        th, sh_, ft = "thigh_" + sd, "shin_" + sd, "foot_" + sd
        S = P[th]
        T = np.asarray(spec["foot" + sd], float) if "foot" + sd in spec else np.array([S[0], 0.08, S[2]])
        pole = spec.get("knee" + sd, (0.05 * s, 0, 1.0))
        u, f = two_bone(S, T, TH, SH, np.asarray(pole, float))
        res["foot" + sd] = T; res["knee" + sd] = pole
        Rh = R["hips"]
        local[th] = qarc(bind_dir(th), qrot(qinv(Rh), u)); Rth = qmul(Rh, local[th])
        local[sh_] = qarc(bind_dir(sh_), qrot(qinv(Rth), f)); Rsh = qmul(Rth, local[sh_])
        td = spec.get("toe" + sd, (0.08 * s, -0.25, 1.0)); res["toe" + sd] = td
        local[ft] = qarc(bind_dir(ft), qrot(qinv(Rsh), td))
    if "_batL" in spec: res["_batL"] = 1
    return local, root, res

def lerp_spec(a, b, t):
    out = {}
    for k in set(a) | set(b):
        if k in a and k in b:
            va, vb = a[k], b[k]
            if k == "_batL": continue
            out[k] = tuple(np.asarray(va, float) * (1 - t) + np.asarray(vb, float) * t)
        else: out[k] = a.get(k, b.get(k))
    return out
