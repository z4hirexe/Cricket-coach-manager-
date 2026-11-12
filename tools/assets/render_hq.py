"""Offline preview renderer approximating the in-game Filament setup (Arena3D.kt):
directional key light with shadow map, 2-band SH ambient (sky/ground), Lambert + GGX-ish specular, emissive (+textures), unlit sky,
alpha-blended decals, two-sided lighting, *mipmapped* texture sampling (like the GPU), near-plane clipping, height fog, ACES tone map.
It is a validation tool: the APK renders the same GLB with Filament on the phone GPU."""
import io
import numpy as np
from PIL import Image
from preview import Model

# ---------------------------------------------------------------- textures with mip pyramid
class Tex:
    def __init__(self, arr, repeat=True):
        self.repeat = repeat; self.levels = [arr]
        while min(arr.shape[:2]) > 2:
            h, w = arr.shape[0] // 2 * 2, arr.shape[1] // 2 * 2; a = arr[:h, :w]
            arr = 0.25 * (a[0::2, 0::2] + a[1::2, 0::2] + a[0::2, 1::2] + a[1::2, 1::2]); self.levels.append(arr)
        self.size = np.array(self.levels[0].shape[:2][::-1], float)  # (w, h)

    def sample(self, u, v, lod):
        L = np.clip(np.floor(lod + 0.5), 0, len(self.levels) - 1).astype(int)
        C = self.levels[0].shape[2]; out = np.empty(u.shape + (C,))
        for l in np.unique(L):
            m = L == l; lev = self.levels[l]; h, w = lev.shape[:2]
            if self.repeat: uu = np.mod(u[m], 1.0); vv = np.mod(v[m], 1.0)
            else: uu = np.clip(u[m], 0, 0.9999); vv = np.clip(v[m], 0, 0.9999)
            out[m] = lev[(vv * h).astype(int) % h, (uu * w).astype(int) % w]
        return out


class Scene:
    def __init__(self): self.items = []

    def add_model(self, model, include, anim=None, t=0.0, xform=None, tint=None, cast=True):
        g = model.g; W = model.world(anim, t)
        if not hasattr(model, "_tex"): model._tex = {}
        def texture(ti, srgb=True):
            key = (ti, srgb)
            if key not in model._tex:
                im = g.images[g.textures[ti].source]; bv = g.bufferViews[im.bufferView]
                img = Image.open(io.BytesIO(model.blob[bv.byteOffset:bv.byteOffset + bv.byteLength])).convert("RGBA")
                a = np.asarray(img, float) / 255
                if srgb: a[..., :3] = a[..., :3] ** 2.2
                smp = g.samplers[g.textures[ti].sampler] if g.textures[ti].sampler is not None else None
                rep = smp is None or smp.wrapS != 33071
                model._tex[key] = Tex(a, rep)
            return model._tex[key]
        for ni, n in enumerate(g.nodes):
            if n.mesh is None or not include(n.name): continue
            for p in g.meshes[n.mesh].primitives:
                v = model.acc(p.attributes.POSITION).astype(float); nr = model.acc(p.attributes.NORMAL).astype(float)
                f = model.acc(p.indices).reshape(-1, 3).astype(int)
                if n.skin is not None:
                    sk = g.skins[n.skin]; ibm = model.acc(sk.inverseBindMatrices).reshape(-1, 4, 4).transpose(0, 2, 1)
                    J = np.stack([W[j] @ ibm[k] for k, j in enumerate(sk.joints)])
                    jo = model.acc(p.attributes.JOINTS_0).astype(int); we = model.acc(p.attributes.WEIGHTS_0).astype(float)
                    Mx = np.einsum("nk,nkij->nij", we, J[jo]); v = np.einsum("nij,nj->ni", Mx[:, :3, :3], v) + Mx[:, :3, 3]
                    nr = np.einsum("nij,nj->ni", Mx[:, :3, :3], nr)
                else:
                    Mx = W[ni]; v = v @ Mx[:3, :3].T + Mx[:3, 3]; nr = nr @ np.linalg.inv(Mx[:3, :3])
                if xform is not None: v, nr = xform(v, nr)
                mt = g.materials[p.material]; pbr = mt.pbrMetallicRoughness
                base = np.array(pbr.baseColorFactor or [1, 1, 1, 1], float)
                if tint is not None and tint[0](mt.name): base[:3] = tint[1]
                it = dict(P=v, N=nr, F=f, name=n.name, mname=mt.name, base=base, metal=float(pbr.metallicFactor if pbr.metallicFactor is not None else 1),
                          rough=float(pbr.roughnessFactor if pbr.roughnessFactor is not None else 1), cast=cast,
                          unlit=bool(mt.extensions and "KHR_materials_unlit" in mt.extensions), blend=(mt.alphaMode == "BLEND"), double=bool(mt.doubleSided),
                          emi=np.array(mt.emissiveFactor or [0, 0, 0], float), tex=None, etex=None, UV=None, C=None)
                if p.attributes.TEXCOORD_0 is not None: it["UV"] = model.acc(p.attributes.TEXCOORD_0).astype(float)
                if pbr.baseColorTexture is not None and it["UV"] is not None: it["tex"] = texture(pbr.baseColorTexture.index)
                if mt.emissiveTexture is not None and it["UV"] is not None: it["etex"] = texture(mt.emissiveTexture.index)
                if p.attributes.COLOR_0 is not None:
                    c = model.acc(p.attributes.COLOR_0).astype(float); it["C"] = c if c.shape[1] == 4 else np.c_[c, np.ones(len(c))]
                self.items.append(it)


def look(eye, tgt):
    f = tgt - eye; f = f / np.linalg.norm(f); r = np.cross(f, (0, 1, 0)); r /= np.linalg.norm(r); u = np.cross(r, f)
    return np.stack([r, u, -f])


def aces(x):
    a, b, c, d, e = 2.51, 0.03, 2.43, 0.59, 0.14
    return np.clip((x * (a * x + b)) / (x * (c * x + d) + e), 0, 1)


def _clip_poly(recs, near):
    """recs: list of dict with 'c' (camera xyz) -> clipped polygon against -c.z >= near"""
    out = []
    for i in range(len(recs)):
        A = recs[i]; B = recs[(i + 1) % len(recs)]
        za, zb = -A["c"][2], -B["c"][2]
        if za >= near: out.append(A)
        if (za >= near) != (zb >= near):
            t = (near - za) / (zb - za)
            out.append({k: A[k] + (B[k] - A[k]) * t for k in A})
    return out


def render(scene, eye, tgt, W=1280, H=720, fov=40, ss=2, light=None, shadow_center=(0, 0, 10), shadow_extent=60.0, shadow_res=2048,
           fog=None, emissive_scale=None, near=0.1, wear_alpha=None, sky_names=("sky_day", "sky_dusk", "sky_night")):
    L = light or {}
    sun_dir = np.asarray(L.get("dir", (0.35, -0.82, 0.45)), float); Ld = -sun_dir / np.linalg.norm(sun_dir)
    sun = np.asarray(L.get("sun", (1.0, 0.96, 0.88)), float) * L.get("key", 3.2)
    skyC = np.asarray(L.get("sky", (0.55, 0.66, 0.85)), float); gndC = np.asarray(L.get("gnd", (0.22, 0.28, 0.16)), float)
    exposure = L.get("exposure", 1.0)
    emissive_scale = emissive_scale or {}
    eye = np.asarray(eye, float); tgt = np.asarray(tgt, float)
    # ---------------- shadow map (orthographic, depth only)
    cen = np.asarray(shadow_center, float); Rl = look(cen + Ld * 300, cen); SM = shadow_res
    Zs = np.full((SM, SM), np.inf)
    for it in scene.items:
        if not it["cast"] or it["blend"] or it["unlit"] or np.ptp(it["P"][:, 1]) < 0.08: continue
        c = (it["P"] - cen) @ Rl.T; X = (c[:, 0] / shadow_extent * 0.5 + 0.5) * SM; Y = (0.5 - c[:, 1] / shadow_extent * 0.5) * SM; Z = 400 - c[:, 2]
        for tri in it["F"]:
            xs, ys, zs = X[tri], Y[tri], Z[tri]
            x0, x1 = int(max(0, np.floor(xs.min()))), int(min(SM - 1, np.ceil(xs.max()))); y0, y1 = int(max(0, np.floor(ys.min()))), int(min(SM - 1, np.ceil(ys.max())))
            if x0 > x1 or y0 > y1: continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            (ax, bx, cx), (ay, by, cy) = xs, ys; d = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
            if abs(d) < 1e-12: continue
            w1 = ((gx - ax) * (cy - ay) - (cx - ax) * (gy - ay)) / d; w2 = ((bx - ax) * (gy - ay) - (gx - ax) * (by - ay)) / d; w0 = 1 - w1 - w2
            m = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            if not m.any(): continue
            zz = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]; sub = Zs[y0:y1 + 1, x0:x1 + 1]; m &= zz < sub; sub[m] = zz[m]
    def shadow(pw):
        c = (pw - cen) @ Rl.T; u = ((c[..., 0] / shadow_extent * 0.5 + 0.5) * SM); v = ((0.5 - c[..., 1] / shadow_extent * 0.5) * SM); z = 400 - c[..., 2]
        acc = np.zeros(z.shape)
        for du, dv in ((-0.6, -0.6), (0.6, -0.6), (-0.6, 0.6), (0.6, 0.6)):   # 4-tap PCF
            ui = (u + du).astype(int); vi = (v + dv).astype(int); ok = (ui >= 0) & (ui < SM) & (vi >= 0) & (vi < SM)
            zs = np.where(ok, Zs[np.clip(vi, 0, SM - 1), np.clip(ui, 0, SM - 1)], np.inf); acc += np.where(z - 0.04 > zs, 0.0, 1.0)
        return acc / 4
    # ---------------- main pass
    R = look(eye, tgt); Wb, Hb = W * ss, H * ss; fl = 0.5 * Hb / np.tan(np.radians(fov) / 2)
    Z = np.full((Hb, Wb), np.inf); img = np.zeros((Hb, Wb, 3)); isSky = np.zeros((Hb, Wb), bool)
    order = [it for it in scene.items if not it["blend"]] + sorted([it for it in scene.items if it["blend"]], key=lambda it: -np.linalg.norm(it["P"].mean(0) - eye))
    for it in order:
        P = it["P"]; cam = (P - eye) @ R.T; nrm = it["N"] / np.maximum(np.linalg.norm(it["N"], axis=1, keepdims=True), 1e-9)
        UV = it["UV"] if it["UV"] is not None else np.zeros((len(P), 2)); C = it["C"] if it["C"] is not None else np.ones((len(P), 4))
        sky = it["name"] in sky_names
        es = emissive_scale.get(it["mname"], emissive_scale.get("*", 1.0))
        base = it["base"].copy()
        if it["mname"] == "pitch_wear" and wear_alpha is not None: base[3] = wear_alpha
        zc = -cam[:, 2]
        for tri in it["F"]:
            zt = zc[tri]
            if (zt < near).all(): continue
            recs = [dict(c=cam[k], p=P[k], n=nrm[k], uv=UV[k], col=C[k]) for k in tri]
            if (zt < near).any():
                recs = _clip_poly(recs, near)
                if len(recs) < 3: continue
            for k in range(1, len(recs) - 1):
                _raster(recs[0], recs[k], recs[k + 1], it, base, es, sky, Z, img, isSky, Wb, Hb, fl, eye, Ld, sun, skyC, gndC, shadow)
    # ---------------- fog + tone map
    if fog is not None:
        fc = np.asarray(fog["color"], float); dist = np.where(np.isinf(Z), 1e4, Z)
        fa = np.clip(1 - np.exp(-np.maximum(dist - fog.get("start", 30), 0) * fog["density"]), 0, fog.get("max", 0.6))
        fa[isSky] *= fog.get("sky", 0.0)
        img = img * (1 - fa[..., None]) + fc * fa[..., None]
    bg = np.asarray(L.get("bg", (0.4, 0.55, 0.8)), float); img[np.isinf(Z)] = bg
    out = aces(img * exposure) ** (1 / 2.2)
    return Image.fromarray((out * 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)


def _raster(A, B, Cc, it, base, es, sky, Z, img, isSky, Wb, Hb, fl, eye, Ld, sun, skyC, gndC, shadow):
    zs = np.array([-A["c"][2], -B["c"][2], -Cc["c"][2]])
    X = Wb / 2 + fl * np.array([A["c"][0], B["c"][0], Cc["c"][0]]) / zs; Y = Hb / 2 - fl * np.array([A["c"][1], B["c"][1], Cc["c"][1]]) / zs
    x0, x1 = int(max(0, np.floor(X.min()))), int(min(Wb - 1, np.ceil(X.max()))); y0, y1 = int(max(0, np.floor(Y.min()))), int(min(Hb - 1, np.ceil(Y.max())))
    if x0 > x1 or y0 > y1: return
    (ax, bx, cx), (ay, by, cy) = X, Y; d = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
    if abs(d) < 1e-12: return
    # facing (world winding vs eye)
    gn = np.cross(B["p"] - A["p"], Cc["p"] - A["p"]); back = np.dot(gn, eye - A["p"]) < 0
    if back and not it["double"] and not sky: return
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    w1 = ((gx - ax) * (cy - ay) - (cx - ax) * (gy - ay)) / d; w2 = ((bx - ax) * (gy - ay) - (gx - ax) * (by - ay)) / d; w0 = 1 - w1 - w2
    m = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
    if not m.any(): return
    iz = 1 / zs; q0, q1, q2 = w0 * iz[0], w1 * iz[1], w2 * iz[2]; qs = q0 + q1 + q2; zz = 1 / qs
    sub = Z[y0:y1 + 1, x0:x1 + 1]; m &= zz < sub
    if not m.any(): return
    a0, a1, a2 = q0 / qs, q1 / qs, q2 / qs
    def I(key): return a0[..., None] * A[key] + a1[..., None] * B[key] + a2[..., None] * Cc[key]
    uv = I("uv"); col = I("col")
    alb = base[None, None, :3] * col[..., :3]; alpha = base[3] * col[..., 3]
    def uv_at(px, py):
        b1 = ((px - ax) * (cy - ay) - (cx - ax) * (py - ay)) / d; b2 = ((bx - ax) * (py - ay) - (px - ax) * (by - ay)) / d; b0 = 1 - b1 - b2
        r0, r1, r2 = b0 * iz[0], b1 * iz[1], b2 * iz[2]; rs = r0 + r1 + r2
        return (r0[..., None] * A["uv"] + r1[..., None] * B["uv"] + r2[..., None] * Cc["uv"]) / rs[..., None]
    duvx = None
    def lod_of(tex):
        nonlocal duvx
        if duvx is None: duvx = (uv_at(gx + 1, gy) - uv, uv_at(gx, gy + 1) - uv)
        dx = duvx[0] * tex.size; dy = duvx[1] * tex.size
        rho = np.maximum(np.hypot(dx[..., 0], dx[..., 1]), np.hypot(dy[..., 0], dy[..., 1])); return np.log2(np.maximum(rho, 1e-6))
    if it["tex"] is not None:
        tx = it["tex"].sample(uv[..., 0], uv[..., 1], lod_of(it["tex"])); alb = alb * tx[..., :3]; alpha = alpha * tx[..., 3]
    if sky or it["unlit"]:
        c = alb
    else:
        pw = I("p"); nw = I("n"); nw /= np.maximum(np.linalg.norm(nw, axis=-1, keepdims=True), 1e-9)
        if back: nw = -nw
        V = eye[None, None] - pw; V /= np.linalg.norm(V, axis=-1, keepdims=True)
        ndl = np.clip(nw @ Ld, 0, 1); vis = shadow(pw)
        Hh = V + Ld; Hh /= np.linalg.norm(Hh, axis=-1, keepdims=True); ndh = np.clip((nw * Hh).sum(-1), 0, 1)
        rough = max(it["rough"], 0.08); a2_ = rough ** 4; dd = ndh * ndh * (a2_ - 1) + 1; D = a2_ / (np.pi * dd * dd)
        metal = it["metal"]; F0 = 0.04 * (1 - metal) + alb * metal
        spec = (D * 0.25)[..., None] * F0
        irr = (skyC + gndC) * 0.5 + (skyC - gndC) * 0.5 * nw[..., 1:2]
        diff = alb * (1 - metal)
        c = diff * irr + (diff / np.pi + spec) * (sun * (ndl * vis)[..., None])
        if metal > 0.5: c = c + alb * irr * 0.35
    if np.any(it["emi"] > 0):
        e = it["emi"][None, None] * es
        if it["etex"] is not None: e = e * it["etex"].sample(uv[..., 0], uv[..., 1], lod_of(it["etex"]))[..., :3]
        c = c + e
    tgt = img[y0:y1 + 1, x0:x1 + 1]
    if it["blend"]:
        a = np.clip(alpha, 0, 1)[..., None]; tgt[m] = (tgt * (1 - a) + c * a)[m]
    else:
        tgt[m] = c[m]; sub[m] = zz[m]
        isSky[y0:y1 + 1, x0:x1 + 1][m] = sky


LIGHTS = {
    "day": dict(dir=(0.62, -0.58, 0.52), sun=(1, 0.96, 0.88), key=3.2, sky=(0.55, 0.66, 0.85), gnd=(0.22, 0.28, 0.16), exposure=1.0, bg=(0.5, 0.65, 0.85)),
    "dusk": dict(dir=(-0.75, -0.35, 0.55), sun=(1, 0.70, 0.45), key=2.2, sky=(0.40, 0.36, 0.45), gnd=(0.20, 0.17, 0.12), exposure=1.1, bg=(0.3, 0.25, 0.3)),
    "night": dict(dir=(0.15, -0.9, 0.4), sun=(0.95, 0.97, 1.0), key=2.6, sky=(0.16, 0.18, 0.24), gnd=(0.10, 0.13, 0.09), exposure=1.2, bg=(0.02, 0.03, 0.06)),
}
FOG = {"day": dict(color=(0.62, 0.72, 0.85), density=0.0011, start=80, max=0.32), "dusk": dict(color=(0.55, 0.40, 0.38), density=0.0012, start=80, max=0.35),
       "night": dict(color=(0.03, 0.04, 0.07), density=0.0015, start=80, max=0.4)}
# emissive multipliers mirroring Arena3D.setLighting
EMI = {"day": {"*": 0.6, "flood_lamp": 0.15, "facade": 0.0, "city": 0.0, "pavilion_front": 0.2, "vip_glass": 0.35, "roof_led": 0.2},
       "dusk": {"*": 1.0, "flood_lamp": 1.5, "facade": 0.6, "city": 0.5, "pavilion_front": 0.8, "vip_glass": 0.8, "roof_led": 1.2},
       "night": {"*": 1.3, "flood_lamp": 6.0, "facade": 1.2, "city": 1.0, "pavilion_front": 1.4, "vip_glass": 1.3, "roof_led": 3.0, "ad_boards": 1.6, "ribbon": 1.6}}
