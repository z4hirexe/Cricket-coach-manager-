"""Higher-fidelity offline preview approximating the in-game Filament look: sun shadow map, Lambert+Blinn-Phong specular,
hemispheric ambient, ACES tone mapping, 2x supersampling, vertex colours, textures and emissive."""
import numpy as np
from PIL import Image

def _raster(tris, P, W, H, want_color, shadow=None, L=None, eye=None, amb=None, sun=None):
    Z = np.full((H, W), np.inf); img = np.zeros((H, W, 3)) if want_color else None
    for tri in tris:
        v, nr, f, col, met = tri[:5]; tex, uvs, vcol, emi = (tri[5:] + (None, None, None, np.zeros(3)))[:4] if len(tri) > 5 else (None, None, None, np.zeros(3))
        sx, sy, z = P(v)
        X = sx[f]; Y = sy[f]; ZZ = z[f]
        if want_color:
            nl = nr / np.maximum(np.linalg.norm(nr, axis=1, keepdims=True), 1e-9)
            base = col[None] * (vcol if vcol is not None else 1.0)
        for i in range(len(f)):
            if (ZZ[i] <= 0.05).any(): continue
            x0, x1 = int(max(0, np.floor(X[i].min()))), int(min(W - 1, np.ceil(X[i].max())))
            y0, y1 = int(max(0, np.floor(Y[i].min()))), int(min(H - 1, np.ceil(Y[i].max())))
            if x0 > x1 or y0 > y1: continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            (ax, bx, cx), (ay, by, cy) = X[i], Y[i]
            d = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
            if abs(d) < 1e-9: continue
            w1 = ((xs - ax) * (cy - ay) - (cx - ax) * (ys - ay)) / d; w2 = ((bx - ax) * (ys - ay) - (xs - ax) * (by - ay)) / d; w0 = 1 - w1 - w2
            m = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            if not m.any(): continue
            iz = 1 / ZZ[i]; q0, q1, q2 = w0 * iz[0], w1 * iz[1], w2 * iz[2]; qs = q0 + q1 + q2
            zz = 1.0 / qs
            sub = Z[y0:y1 + 1, x0:x1 + 1]; m &= zz < sub
            if not m.any(): continue
            sub[m] = zz[m]
            if not want_color: continue
            a0, a1, a2 = q0 / qs, q1 / qs, q2 / qs
            ii = f[i]
            pw = a0[..., None] * v[ii[0]] + a1[..., None] * v[ii[1]] + a2[..., None] * v[ii[2]]
            nw = a0[..., None] * nl[ii[0]] + a1[..., None] * nl[ii[1]] + a2[..., None] * nl[ii[2]]; nw /= np.maximum(np.linalg.norm(nw, axis=-1, keepdims=True), 1e-9)
            alb = a0[..., None] * base[ii[0] if base.shape[0] > 1 else 0] + a1[..., None] * base[ii[1] if base.shape[0] > 1 else 0] + a2[..., None] * base[ii[2] if base.shape[0] > 1 else 0]
            if tex is not None:
                uu = a0 * uvs[ii[0], 0] + a1 * uvs[ii[1], 0] + a2 * uvs[ii[2], 0]; vv = a0 * uvs[ii[0], 1] + a1 * uvs[ii[1], 1] + a2 * uvs[ii[2], 1]
                th, tw = tex.shape[:2]; alb = alb * tex[(np.mod(vv, 1) * th).astype(int) % th, (np.mod(uu, 1) * tw).astype(int) % tw] ** 2.2
            if np.any(emi > 0) and np.all(col > 0.95) and tex is None and vcol is not None:   # unlit sky
                c = alb
            else:
                ndl = np.clip(nw @ L, 0, 1)
                vis = shadow(pw) if shadow is not None else 1.0
                V = eye[None, None] - pw; V /= np.linalg.norm(V, axis=-1, keepdims=True); Hh = V + L; Hh /= np.linalg.norm(Hh, axis=-1, keepdims=True)
                rough = 0.55 if met < 0.5 else 0.3
                spec = (np.clip((nw * Hh).sum(-1), 0, 1) ** (2 / rough ** 2)) * (0.04 + 0.6 * met) * 1.2
                hemi = amb[0] * (0.5 + 0.5 * nw[..., 1:2]) + amb[1] * (0.5 - 0.5 * nw[..., 1:2])
                c = alb * (hemi + sun * (ndl * vis)[..., None]) + (spec * vis)[..., None] * sun + np.asarray(emi) * 3.0 * (alb if tex is not None else 1)
            img[y0:y1 + 1, x0:x1 + 1][m] = c[m]
    return Z, img

def look(eye, tgt):
    f = tgt - eye; f /= np.linalg.norm(f); r = np.cross(f, (0, 1, 0)); r /= np.linalg.norm(r); u = np.cross(r, f)
    return np.stack([r, u, -f])

def render(tris, eye, tgt, W=1280, H=720, fov=40, sun_dir=(0.35, -0.82, 0.45), sun=(3.0, 2.85, 2.6), amb=((0.55, 0.66, 0.85), (0.22, 0.26, 0.18)),
           shadow_center=(0, 0, 8), shadow_extent=40.0, ss=2, exposure=0.9, bg=(0.1, 0.15, 0.25)):
    eye = np.asarray(eye, float); tgt = np.asarray(tgt, float)
    L = -np.asarray(sun_dir, float); L /= np.linalg.norm(L)
    # orthographic shadow map
    SM = 1400; Rl = look(np.asarray(shadow_center) + L * 200, np.asarray(shadow_center, float)); cen = np.asarray(shadow_center, float)
    def Pl(v):
        c = (v - cen) @ Rl.T; return (c[:, 0] / shadow_extent * 0.5 + 0.5) * SM, (0.5 - c[:, 1] / shadow_extent * 0.5) * SM, 300 - c[:, 2]
    occ = [t for t in tris if not (len(t) > 8 and t[8] is not None and np.any(np.asarray(t[8]) > 0) and t[6] is None) and not (np.ptp(t[0][:, 1]) < 0.02 and abs(t[0][:, 1].mean()) < 0.05)]
    Zs, _ = _raster(occ, Pl, SM, SM, False)
    def shadow(pw):
        c = (pw - cen) @ Rl.T; u = ((c[..., 0] / shadow_extent * 0.5 + 0.5) * SM).astype(int); v = ((0.5 - c[..., 1] / shadow_extent * 0.5) * SM).astype(int)
        z = 300 - c[..., 2]; ok = (u >= 0) & (u < SM) & (v >= 0) & (v < SM)
        zs = np.where(ok, Zs[np.clip(v, 0, SM - 1), np.clip(u, 0, SM - 1)], np.inf)
        return np.where(z - 0.03 > zs, 0.0, 1.0)
    R = look(eye, tgt); Wb, Hb = W * ss, H * ss; fl = 0.5 * Hb / np.tan(np.radians(fov) / 2)
    def P(v):
        c = (v - eye) @ R.T; z = -c[:, 2]; return Wb / 2 + fl * c[:, 0] / np.maximum(z, 1e-3), Hb / 2 - fl * c[:, 1] / np.maximum(z, 1e-3), z
    Z, img = _raster(tris, P, Wb, Hb, True, shadow, L, eye, np.asarray(amb), np.asarray(sun))
    img[np.isinf(Z)] = bg
    x = img * exposure; a, b, c2, d, e = 2.51, 0.03, 2.43, 0.59, 0.14
    x = np.clip((x * (a * x + b)) / (x * (c2 * x + d) + e), 0, 1) ** (1 / 2.2)
    im = Image.fromarray((x * 255).astype(np.uint8))
    return im.resize((W, H), Image.LANCZOS)
