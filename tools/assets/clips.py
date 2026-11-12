"""Keyframed cricket animation clips (character frame: +x = character's left, +z = chest forward).
Batting clips assume a right-hander whose bowler is at +x (front shoulder). Left-handers are mirrored at runtime.
Bowling / running / fielding clips move toward +z."""
def M(base, **kw):
    d = dict(base); d.update(kw); return d

IDLE = dict(root=(0, 0.955, 0), footL=(0.12, 0.08, 0.0), footR=(-0.12, 0.08, 0.0), toeL=(0.25, -0.25, 1), toeR=(-0.25, -0.25, 1),
            handL=(0.24, 0.93, 0.02), handR=(-0.24, 0.93, 0.02), elbowL=(0.3, 0, -1), elbowR=(-0.3, 0, -1))
IDLE2 = M(IDLE, root=(0, 0.951, 0.005), spine=(3, 0, 0), handL=(0.245, 0.925, 0.03), handR=(-0.245, 0.925, 0.03))

# ---------------------------------------------------------------- batting (RH)
BATLEGS = dict(root=(0.03, 0.91, 0.0), hipR=(0, 12, 0), footL=(0.30, 0.08, 0.03), footR=(-0.14, 0.08, -0.02),
               toeL=(0.3, -0.25, 1), toeR=(-0.05, -0.25, 1), kneeL=(0.3, 0, 1), kneeR=(-0.1, 0, 1))
STANCE = M(BATLEGS, spine=(14, 4, 0), chest=(6, 10, 0), neck=(0, 25, 0), head=(-6, 45, 0), bat=((-0.02, 0.80, 0.24), (-0.08, -0.75, -0.10)))
STANCE2 = M(STANCE, bat=((-0.03, 0.83, 0.23), (-0.10, -0.75, -0.06)))
BACKLIFT = M(STANCE, spine=(16, 2, 0), bat=((-0.12, 1.18, 0.12), (-0.35, 0.80, -0.45)))
FRONT = dict(root=(0.24, 0.83, 0.06), hipR=(0, 20, 0), footL=(0.62, 0.08, 0.10), toeL=(0.6, -0.2, 0.8), kneeL=(0.5, 0, 1), footR=(-0.14, 0.08, -0.02), toeR=(0, -0.3, 1))
BACK = dict(root=(-0.02, 0.90, 0.04), hipR=(0, 5, 0), footR=(-0.18, 0.08, 0.10), footL=(0.22, 0.08, 0.06), toeR=(0, -0.3, 1))

def shot(impact, follow, t_imp=0.55, t_end=1.25, prep=BACKLIFT):
    return [(0, STANCE), (0.28, prep), (t_imp, impact), (t_imp + 0.3, follow), (t_end, follow)]

COVER_IMP = M({**STANCE, **FRONT}, spine=(22, 6, 0), chest=(10, 14, 0), neck=(0, 20, 0), head=(10, 35, 0), bat=((0.38, 0.62, 0.30), (0.25, -0.92, 0.30)))
COVER_FOL = M(COVER_IMP, spine=(10, 10, 0), chest=(4, 30, 0), head=(5, 25, 0), bat=((0.30, 1.50, 0.18), (-0.45, 0.55, -0.70)))
STR_IMP = M(COVER_IMP, footL=(0.62, 0.08, 0.02), bat=((0.40, 0.60, 0.12), (0.15, -0.98, 0.0)))
STR_FOL = M(STR_IMP, spine=(8, 6, 0), chest=(2, 20, 0), bat=((0.32, 1.55, 0.05), (-0.25, 0.85, -0.45)))
DEF_IMP = M({**STANCE, **FRONT}, root=(0.18, 0.86, 0.04), footL=(0.50, 0.08, 0.06), spine=(24, 4, 0), head=(12, 40, 0), bat=((0.40, 0.58, 0.18), (0.05, -0.99, 0.08)))
PULL_IMP = M({**STANCE, **BACK}, hipR=(0, -20, 0), spine=(8, -5, 0), chest=(0, -25, 0), neck=(0, 30, 0), head=(0, 60, 0), bat=((0.05, 1.12, 0.30), (0.3, 0.05, 0.95)))
PULL_FOL = M(PULL_IMP, chest=(0, -55, 0), head=(0, 80, 0), bat=((-0.10, 1.25, -0.10), (-0.2, 0.4, -0.9)))
HOOK_IMP = M(PULL_IMP, bat=((0.04, 1.38, 0.28), (0.3, 0.25, 0.92)), spine=(-4, -5, 0))
HOOK_FOL = M(HOOK_IMP, chest=(0, -60, 0), bat=((-0.15, 1.45, -0.05), (-0.3, 0.7, -0.6)))
CUT_IMP = M({**STANCE, **BACK}, footR=(-0.04, 0.08, 0.20), root=(0.0, 0.90, 0.08), chest=(4, 0, 0), bat=((0.12, 1.02, 0.48), (0.30, -0.25, 0.92)))
CUT_FOL = M(CUT_IMP, bat=((0.18, 0.85, 0.45), (0.2, -0.9, 0.4)))
SWEEPLEGS = dict(root=(0.12, 0.55, 0.02), hipR=(0, 10, 0), spine=(30, 10, 0), footL=(0.62, 0.08, 0.18), kneeL=(0.4, 0.3, 1),
                 footR=(-0.40, 0.10, -0.10), kneeR=(-0.2, -1, 0.3), toeR=(-0.3, -0.9, 0.2))
SWEEP_IMP = M({**STANCE, **SWEEPLEGS}, bat=((0.34, 0.42, 0.30), (0.75, -0.30, -0.55)))
SWEEP_FOL = M(SWEEP_IMP, bat=((0.15, 0.55, 0.0), (-0.2, -0.2, -0.95)))
REV_IMP = M({**STANCE, **SWEEPLEGS}, bat=((0.34, 0.42, 0.30), (0.70, -0.30, 0.65)))
REV_FOL = M(REV_IMP, bat=((0.30, 0.60, 0.45), (-0.1, -0.2, 0.97)))
LOFT_IMP = M(COVER_IMP, root=(0.26, 0.84, 0.04), bat=((0.36, 0.70, 0.20), (0.35, -0.88, 0.30)))
LOFT_FOL = M(LOFT_IMP, spine=(5, 15, 0), chest=(0, 40, 0), head=(-15, 35, 0), bat=((0.12, 1.62, -0.05), (-0.55, 0.65, -0.5)))
LEAVE = M(STANCE, bat=((-0.02, 1.42, 0.05), (-0.25, 0.95, -0.15)))
MISS_IMP = M(DEF_IMP, bat=((0.36, 0.72, 0.42), (0.25, -0.90, 0.35)))
MISS_FOL = M(MISS_IMP, head=(0, 20, 0))

# ---------------------------------------------------------------- running / walking (toward +z)
def run_keys(amp=1.0, lean=14, bat=False, h=0.93, period=0.6):
    def pose(ph):
        s = 1 if ph == 0 else -1
        p = dict(root=(0, h, 0), spine=(lean, 0, 0),
                 footL=(0.10, 0.10 + (0 if s > 0 else 0.12 * amp), 0.35 * amp * s), footR=(-0.10, 0.10 + (0.12 * amp if s > 0 else 0), -0.35 * amp * s),
                 toeL=(0, -0.3 if s > 0 else -0.8, 1), toeR=(0, -0.8 if s > 0 else -0.3, 1),
                 handL=(0.20, 0.98, -0.22 * amp * s), handR=(-0.20, 1.10 if bat else 0.98, 0.22 * amp * s), elbowL=(0.2, 0, -1), elbowR=(-0.2, 0, -1))
        if bat: p["hdirR"] = (0, -0.5, 1)
        return p
    def mid(ph):
        p = pose(ph); p = dict(p); p["root"] = (0, h + 0.035 * amp, 0)
        p["footL"] = (0.10, 0.08 if ph == 0 else 0.30 * amp, 0.0); p["footR"] = (-0.10, 0.30 * amp if ph == 0 else 0.08, 0.0)
        p["handL"] = (0.21, 0.98, 0.0); p["handR"] = (-0.21, 1.10 if bat else 0.98, 0.0)
        return p
    q = period / 4
    return [(0, pose(0)), (q, mid(0)), (2 * q, pose(1)), (3 * q, mid(1)), (period, pose(0))]

RUN = run_keys(); WALK = run_keys(amp=0.55, lean=4, h=0.95, period=1.0); BAT_RUN = run_keys(bat=True)

# ---------------------------------------------------------------- bowling (RH, running toward +z)
def bowl(fast=True):
    k = []
    rk = run_keys(amp=1.0 if fast else 0.6, lean=12, period=0.6 if fast else 0.7)
    n = 2 if fast else 1
    for c in range(n):
        for t, p in rk[:-1]: k.append((c * rk[-1][0] + t, p))
    t0 = n * rk[-1][0]
    air = dict(root=(0, 1.08 if fast else 1.0, 0.05), hipR=(0, -75, 0), spine=(-4, -10, 0), chest=(0, -10, 0), head=(0, 75, 0),
               handL=(0.0, 1.95, 0.42), hdirL=(0, 1, 0.3), handR=(-0.05, 1.00, -0.45), elbowR=(0, -0.5, -1),
               footL=(0.05, 0.40 if fast else 0.25, 0.25), kneeL=(0, 0, 1), footR=(-0.05, 0.30, -0.25), toeL=(0, -0.5, 1), toeR=(0, -0.8, 0.5))
    land = dict(root=(0, 0.90, 0.12), hipR=(0, -60, 0), spine=(-10, -10, 0), chest=(0, -20, 0), head=(0, 70, 0),
                handL=(0.05, 1.85, 0.45), hdirL=(0, 1, 0.3), handR=(-0.10, 1.25, -0.60), elbowR=(0, -0.3, -1),
                footR=(-0.05, 0.08, -0.35), footL=(0.0, 0.08, 0.55), kneeL=(0, 0, 1), toeL=(0, -0.25, 1), toeR=(-1, -0.25, 0.3))
    rel = M(land, root=(0, 0.93, 0.30), hipR=(0, -10, 0), spine=(15, 0, 0), chest=(10, 10, 0), head=(0, 10, 0),
            handR=(-0.05, 2.08, 0.25), hdirR=(0, 1, 0.3), elbowR=(-1, 0, 0), handL=(0.25, 1.05, -0.15), hdirL=None)
    fol = M(rel, root=(0, 0.85, 0.55), hipR=(0, 15, 0), spine=(35, 10, 0), chest=(15, 15, 0), head=(-10, 0, 0),
            handR=(0.30, 0.85, 0.75), hdirR=(0.3, -0.5, 0.8), handL=(0.30, 0.90, -0.20),
            footR=(-0.05, 0.25 if fast else 0.12, 0.65), footL=(0.0, 0.08, 0.55), toeR=(0, -0.6, 1))
    end = M(IDLE, root=(0, 0.95, 0.70), footR=(-0.12, 0.08, 0.75), footL=(0.12, 0.08, 0.62), spine=(8, 0, 0))
    for p in (air, land, rel, fol):
        if p.get("hdirL") is None: p.pop("hdirL", None)
    sp = 1.0 if fast else 1.25
    k += [(t0 + 0.20 * sp, air), (t0 + 0.38 * sp, land), (t0 + 0.48 * sp, rel), (t0 + 0.75 * sp, fol), (t0 + 1.15 * sp, end)]
    return k

APPEAL = [(0, IDLE), (0.3, M(IDLE, handL=(0.35, 1.95, 0.15), handR=(-0.35, 1.95, 0.15), head=(-25, 0, 0), spine=(-8, 0, 0))), (1.2, M(IDLE, handL=(0.36, 1.97, 0.12), handR=(-0.36, 1.97, 0.12), head=(-25, 0, 0), spine=(-8, 0, 0)))]
CROUCH = M(IDLE, root=(0, 0.85, 0), spine=(10, 0, 0))
JUMP = M(IDLE, root=(0, 1.12, 0), handR=(-0.15, 2.05, 0.1), handL=(0.12, 1.30, 0.25), footL=(0.12, 0.28, -0.05), footR=(-0.12, 0.30, -0.05), head=(-15, 0, 0))
PUMP = M(IDLE, root=(0, 0.93, 0), handR=(-0.25, 1.50, 0.22), elbowR=(-1, -0.5, 0), handL=(0.15, 1.25, 0.25))
CELEB = [(0, IDLE), (0.25, CROUCH), (0.45, JUMP), (0.7, M(PUMP, root=(0, 0.90, 0))), (1.0, PUMP), (1.25, M(PUMP, handR=(-0.18, 2.0, 0.15))), (1.6, PUMP)]
BAT_RAISE = [(0, STANCE), (0.6, M(IDLE, bat=((-0.22, 1.90, 0.08), (-0.1, 1, 0.05)), handL=(0.28, 1.85, 0.05), hdirL=None, head=(-20, 0, 0))),
             (2.0, M(IDLE, bat=((-0.24, 1.92, 0.06), (-0.12, 1, 0.0)), handL=(0.30, 1.88, 0.0), head=(-20, 0, 0)))]

# ---------------------------------------------------------------- fielding / keeping / umpiring
READY = M(IDLE, root=(0, 0.80, 0.05), spine=(30, 0, 0), footL=(0.24, 0.08, 0.05), footR=(-0.24, 0.08, 0.05), kneeL=(0.3, 0, 1), kneeR=(-0.3, 0, 1),
          handL=(0.13, 0.64, 0.40), handR=(-0.13, 0.64, 0.40), elbowL=(0.5, -0.3, -0.3), elbowR=(-0.5, -0.3, -0.3), head=(-18, 0, 0))
FIELD_READY = [(0, READY), (0.5, M(READY, root=(0, 0.78, 0.07))), (1.0, READY)]
DIVE = [(0, READY), (0.22, M(READY, root=(0.35, 0.70, 0.0), hipR=(0, 0, -30), handL=(0.85, 0.85, 0.25), handR=(0.70, 0.90, 0.35))),
        (0.45, M(READY, root=(0.85, 0.42, 0.0), hipR=(0, 0, -80), spine=(5, 0, 0), handL=(1.55, 0.48, 0.15), handR=(1.50, 0.58, 0.28), footL=(0.05, 0.35, 0.0), footR=(0.02, 0.30, 0.18), head=(0, 0, 0))),
        (0.7, M(READY, root=(1.05, 0.17, 0.0), hipR=(0, 0, -86), spine=(0, 0, 0), handL=(1.80, 0.10, 0.18), handR=(1.75, 0.14, 0.30), footL=(0.20, 0.10, 0.0), footR=(0.22, 0.12, 0.16), head=(0, 0, 0))),
        (1.2, M(READY, root=(1.05, 0.17, 0.0), hipR=(0, 0, -86), spine=(0, 0, 0), handL=(1.80, 0.10, 0.18), handR=(1.75, 0.14, 0.30), footL=(0.20, 0.10, 0.0), footR=(0.22, 0.12, 0.16), head=(0, 0, 0)))]
CATCH = [(0, READY), (0.45, M(IDLE, handL=(0.08, 1.85, 0.32), handR=(-0.08, 1.85, 0.32), hdirL=(0, 1, 0.2), hdirR=(0, 1, 0.2), head=(-35, 0, 0), elbowL=(1, 0, 0), elbowR=(-1, 0, 0))),
         (0.75, M(IDLE, handL=(0.07, 1.30, 0.26), handR=(-0.07, 1.30, 0.26), elbowL=(1, -1, 0), elbowR=(-1, -1, 0))), (1.2, M(IDLE, handL=(0.07, 1.28, 0.25), handR=(-0.07, 1.28, 0.25), elbowL=(1, -1, 0), elbowR=(-1, -1, 0)))]
THROWW = M(IDLE, hipR=(0, -60, 0), handR=(-0.15, 1.55, -0.45), handL=(0.15, 1.45, 0.40), footL=(0.05, 0.08, 0.40), footR=(-0.10, 0.08, -0.30), head=(0, 60, 0))
THROWR = M(THROWW, hipR=(0, 0, 0), spine=(15, 0, 0), handR=(-0.05, 1.75, 0.45), handL=(0.25, 1.0, -0.1), head=(0, 0, 0))
THROW = [(0, IDLE), (0.3, THROWW), (0.48, THROWR), (0.72, M(THROWR, handR=(0.2, 0.95, 0.55), spine=(30, 10, 0), footR=(-0.05, 0.10, 0.35))), (1.0, M(IDLE, root=(0, 0.955, 0.2), footL=(0.12, 0.08, 0.35), footR=(-0.12, 0.08, 0.2)))]
KCROUCH = M(IDLE, root=(0, 0.52, 0.0), spine=(32, 0, 0), footL=(0.26, 0.08, 0.05), footR=(-0.26, 0.08, 0.05), kneeL=(0.6, 0, 1), kneeR=(-0.6, 0, 1),
            toeL=(0.5, -0.2, 1), toeR=(-0.5, -0.2, 1), handL=(0.07, 0.40, 0.36), handR=(-0.07, 0.40, 0.36), elbowL=(1, -0.5, 0), elbowR=(-1, -0.5, 0), head=(-25, 0, 0))
KEEP_CROUCH = [(0, KCROUCH), (1.0, M(KCROUCH, root=(0, 0.53, 0.0))), (2.0, KCROUCH)]
KUP = M(IDLE, root=(0, 0.85, 0), spine=(15, 0, 0), handL=(0.07, 1.05, 0.38), handR=(-0.07, 1.05, 0.38), elbowL=(1, -0.5, 0), elbowR=(-1, -0.5, 0))
KEEP_COLLECT = [(0, KCROUCH), (0.25, KUP), (0.5, M(KUP, handL=(0.07, 1.20, 0.22), handR=(-0.07, 1.20, 0.22))), (0.9, M(KUP, handL=(0.07, 1.20, 0.22), handR=(-0.07, 1.20, 0.22)))]
KEEP_STUMP = [(0, KUP), (0.3, M(KUP, root=(0, 0.75, 0.10), spine=(40, 0, 0), handL=(0.03, 0.58, 0.55), handR=(-0.03, 0.58, 0.55))), (0.8, APPEAL[1][1]), (1.4, APPEAL[2][1])]
UIDLE = M(IDLE, handL=(0.05, 0.98, 0.17), handR=(-0.05, 0.98, 0.17), elbowL=(1, -0.3, 0), elbowR=(-1, -0.3, 0))
UMP_IDLE = [(0, UIDLE), (1.5, M(UIDLE, root=(0.01, 0.952, 0))), (3.0, UIDLE)]
UOUT = M(UIDLE, handR=(-0.10, 2.12, 0.12), hdirR=(0, 1, 0), elbowR=(-1, 0, 0))
UMP_OUT = [(0, UIDLE), (0.45, UOUT), (2.0, UOUT)]
UFOUR_A = M(UIDLE, handR=(-0.55, 1.30, 0.28), elbowR=(-1, -1, 0)); UFOUR_B = M(UIDLE, handR=(0.30, 1.30, 0.38), elbowR=(-1, -1, 0))
UMP_FOUR = [(0, UIDLE)] + [(0.3 + i * 0.35, UFOUR_A if i % 2 == 0 else UFOUR_B) for i in range(5)] + [(2.2, UFOUR_A)]
USIX = M(UIDLE, handL=(0.12, 2.12, 0.05), handR=(-0.12, 2.12, 0.05), elbowL=(1, 0, 0), elbowR=(-1, 0, 0), hdirL=(0, 1, 0), hdirR=(0, 1, 0))
UMP_SIX = [(0, UIDLE), (0.5, USIX), (2.0, USIX)]
UWIDE = M(UIDLE, handL=(0.72, 1.40, 0.0), handR=(-0.72, 1.40, 0.0), elbowL=(0, 1, -1), elbowR=(0, 1, -1), hdirL=(1, 0, 0), hdirR=(-1, 0, 0))
UMP_WIDE = [(0, UIDLE), (0.45, UWIDE), (2.0, UWIDE)]

CLIPS = {
    "idle": [(0, IDLE), (1.5, IDLE2), (3.0, IDLE)],
    "walk": WALK, "run": RUN,
    "bat_stance": [(0, STANCE), (1.0, STANCE2), (2.0, STANCE)],
    "bat_defend": shot(DEF_IMP, DEF_IMP, prep=M(BACKLIFT, bat=((-0.06, 1.05, 0.16), (-0.2, 0.9, -0.3)))),
    "bat_cover_drive": shot(COVER_IMP, COVER_FOL), "bat_straight_drive": shot(STR_IMP, STR_FOL),
    "bat_pull": shot(PULL_IMP, PULL_FOL, t_imp=0.5), "bat_hook": shot(HOOK_IMP, HOOK_FOL, t_imp=0.5),
    "bat_cut": shot(CUT_IMP, CUT_FOL, t_imp=0.5), "bat_sweep": shot(SWEEP_IMP, SWEEP_FOL, t_imp=0.6),
    "bat_reverse_sweep": shot(REV_IMP, REV_FOL, t_imp=0.6), "bat_loft": shot(LOFT_IMP, LOFT_FOL),
    "bat_leave": [(0, STANCE), (0.35, LEAVE), (1.2, LEAVE)], "bat_miss": shot(MISS_IMP, MISS_FOL),
    "bat_run": BAT_RUN, "bat_celebrate": BAT_RAISE,
    "bowl_fast": bowl(True), "bowl_spin": bowl(False),
    "appeal": APPEAL, "celebrate": CELEB,
    "field_ready": FIELD_READY, "field_dive": DIVE, "field_catch": CATCH, "field_throw": THROW,
    "keep_crouch": KEEP_CROUCH, "keep_collect": KEEP_COLLECT, "keep_stump": KEEP_STUMP,
    "ump_idle": UMP_IDLE, "ump_out": UMP_OUT, "ump_four": UMP_FOUR, "ump_six": UMP_SIX, "ump_wide": UMP_WIDE,
}
