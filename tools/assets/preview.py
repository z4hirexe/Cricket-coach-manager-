"""Independent GLB validator + software renderer (reads file with pygltflib, does its own skinning/animation sampling)."""
import numpy as np, pygltflib, sys
from PIL import Image

def qmat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])

class Model:
    def __init__(self, path):
        self.g = g = pygltflib.GLTF2().load(path); self.blob = g.binary_blob()
    def acc(self, i):
        g = self.g; a = g.accessors[i]; bv = g.bufferViews[a.bufferView]
        dt = {5126: np.float32, 5123: np.uint16, 5125: np.uint32, 5121: np.uint8}[a.componentType]
        w = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}[a.type]
        arr = np.frombuffer(self.blob, dt, a.count * w, bv.byteOffset + (a.byteOffset or 0))
        return arr.reshape(a.count, w) if w > 1 else arr
    def local(self, ni, anim=None, t=0.0):
        n = self.g.nodes[ni]
        T = np.array(n.translation or [0, 0, 0], float); R = np.array(n.rotation or [0, 0, 0, 1], float); S = np.array(n.scale or [1, 1, 1], float)
        if anim is not None:
            a = self.g.animations[anim]
            for ch in a.channels:
                if ch.target.node != ni: continue
                s = a.samplers[ch.sampler]; ti = self.acc(s.input); vo = self.acc(s.output)
                k = np.clip(np.searchsorted(ti, t) - 1, 0, len(ti) - 2); u = np.clip((t - ti[k]) / max(ti[k + 1] - ti[k], 1e-6), 0, 1)
                v = vo[k] * (1 - u) + vo[k + 1] * u
                if ch.target.path == "rotation": R = v / np.linalg.norm(v)
                elif ch.target.path == "translation": T = v
        M = np.eye(4); M[:3, :3] = qmat(R) * S; M[:3, 3] = T; return M
    def world(self, anim=None, t=0.0):
        W = {}
        def rec(ni, P):
            W[ni] = P @ self.local(ni, anim, t)
            for c in self.g.nodes[ni].children or []: rec(c, W[ni])
        for r in self.g.scenes[0].nodes: rec(r, np.eye(4))
        return W
    def image(self, ti):
        import io
        if not hasattr(self, "_img"): self._img = {}
        if ti not in self._img:
            im = self.g.images[self.g.textures[ti].source]; bv = self.g.bufferViews[im.bufferView]
            self._img[ti] = np.asarray(Image.open(io.BytesIO(self.blob[bv.byteOffset:bv.byteOffset + bv.byteLength])).convert("RGB"), float) / 255
        return self._img[ti]
    def anim_index(self, name):
        for i, a in enumerate(self.g.animations):
            if a.name == name: return i
        raise KeyError(name)
    def triangles(self, include, anim=None, t=0.0):
        """returns list of (verts Nx3, normals, faces, rgb)"""
        W = self.world(anim, t); out = []
        for ni, n in enumerate(self.g.nodes):
            if n.mesh is None or not include(n.name): continue
            for p in self.g.meshes[n.mesh].primitives:
                v = self.acc(p.attributes.POSITION).astype(float); nr = self.acc(p.attributes.NORMAL).astype(float)
                f = self.acc(p.indices).reshape(-1, 3)
                if n.skin is not None:
                    sk = self.g.skins[n.skin]; ibm = self.acc(sk.inverseBindMatrices).reshape(-1, 4, 4).transpose(0, 2, 1)
                    J = np.stack([W[j] @ ibm[k] for k, j in enumerate(sk.joints)])
                    jo = self.acc(p.attributes.JOINTS_0).astype(int); we = self.acc(p.attributes.WEIGHTS_0).astype(float)
                    M = np.einsum("nk,nkij->nij", we, J[jo])
                    v = np.einsum("nij,nj->ni", M[:, :3, :3], v) + M[:, :3, 3]
                    nr = np.einsum("nij,nj->ni", M[:, :3, :3], nr)
                else:
                    M = W[ni]; v = v @ M[:3, :3].T + M[:3, 3]; nr = nr @ M[:3, :3].T
                mat = self.g.materials[p.material]; col = np.array(mat.pbrMetallicRoughness.baseColorFactor[:3])
                tex = uv = vcol = None
                bt = mat.pbrMetallicRoughness.baseColorTexture
                if bt is not None and p.attributes.TEXCOORD_0 is not None:
                    tex = self.image(bt.index); uv = self.acc(p.attributes.TEXCOORD_0).astype(float)
                if p.attributes.COLOR_0 is not None: vcol = self.acc(p.attributes.COLOR_0)[:, :3].astype(float)
                emi = np.array(mat.emissiveFactor or [0, 0, 0])
                out.append((v, nr, f, col, mat.pbrMetallicRoughness.metallicFactor or 0, tex, uv, vcol, emi))
        return out

def look(eye, tgt):
    f = tgt - eye; f /= np.linalg.norm(f); r = np.cross(f, (0, 1, 0)); r /= np.linalg.norm(r); u = np.cross(r, f)
    return np.stack([r, u, -f]), eye

def render(tris, eye, tgt, W=480, H=640, fov=35, bg=(18, 26, 22), light=(0.4, 0.8, 0.5), sky=None):
    R, e = look(np.asarray(eye, float), np.asarray(tgt, float)); fl = 0.5 * H / np.tan(np.radians(fov) / 2)
    img = np.zeros((H, W, 3)); img[:] = bg; Z = np.full((H, W), np.inf); L = np.asarray(light, float); L /= np.linalg.norm(L)
    for tri in tris:
        v, nr, f, col, met = tri[:5]; tex, uvs, vcol, emi = (tri[5:] + (None, None, None, np.zeros(3)))[:4] if len(tri) > 5 else (None, None, None, np.zeros(3))
        c = (v - e) @ R.T; z = -c[:, 2]
        sx = W / 2 + fl * c[:, 0] / np.maximum(z, 1e-3); sy = H / 2 - fl * c[:, 1] / np.maximum(z, 1e-3)
        nl = nr / np.maximum(np.linalg.norm(nr, axis=1, keepdims=True), 1e-9)
        shade = 0.28 + 0.62 * np.clip(nl @ L, 0, 1) + 0.18 * np.clip(nl[:, 1], 0, 1)
        base = col[None] * (vcol if vcol is not None else 1.0)
        vc = np.clip(base * shade[:, None] * 255 * 1.15 + np.asarray(emi)[None] * 255, 0, 255)
        lit = np.clip(shade[:, None] * 1.15 * (vcol if vcol is not None else 1.0) * col[None], 0, 4)
        for a, b, cc in (f[:, [0, 1, 2]].T,):
            pass
        X = sx[f]; Y = sy[f]; ZZ = z[f]
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
            m = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
            if not m.any(): continue
            zz = 1.0 / (w0 / ZZ[i, 0] + w1 / ZZ[i, 1] + w2 / ZZ[i, 2])
            sub = Z[y0:y1 + 1, x0:x1 + 1]; m &= zz < sub
            if not m.any(): continue
            sub[m] = zz[m]
            if tex is not None:
                iz = 1 / ZZ[i]; q0, q1, q2 = w0 * iz[0], w1 * iz[1], w2 * iz[2]; qs = q0 + q1 + q2
                uu = (q0 * uvs[f[i, 0], 0] + q1 * uvs[f[i, 1], 0] + q2 * uvs[f[i, 2], 0]) / qs
                vv = (q0 * uvs[f[i, 0], 1] + q1 * uvs[f[i, 1], 1] + q2 * uvs[f[i, 2], 1]) / qs
                th, tw = tex.shape[:2]
                tc = tex[(np.mod(vv, 1) * th).astype(int) % th, (np.mod(uu, 1) * tw).astype(int) % tw]
                L3 = w0[..., None] * lit[f[i, 0]] + w1[..., None] * lit[f[i, 1]] + w2[..., None] * lit[f[i, 2]]
                cc = np.clip(tc * L3 * 255 + np.asarray(emi) * 255 * tc, 0, 255)
            else:
                cc = w0[..., None] * vc[f[i, 0]] + w1[..., None] * vc[f[i, 1]] + w2[..., None] * vc[f[i, 2]]
            img[y0:y1 + 1, x0:x1 + 1][m] = cc[m]
    return Image.fromarray(img.astype(np.uint8))

KIT = lambda n: n.startswith("body_athletic_lod0") or n in ("hair_short", "beard", "eyes", "bat")
BAT = lambda n: n.startswith("body_athletic_lod0") or n in ("helmet", "grille", "pad_L", "pad_R", "glove_L", "glove_R", "eyes", "bat")

def stadium_preview(path, out):
    m = Model(path); tr = m.triangles(lambda n: True)
    views = [((0, 22, -95), (0, 0, 30)), ((-60, 9, 2), (0, 0, 15)), ((0, 2.2, -9), (0, 1, 20.12)), ((70, 60, 10), (0, 0, 10))]
    sheet = Image.new("RGB", (640 * 2, 360 * 2))
    for i, (e, t) in enumerate(views):
        im = render(tr, np.array(e, float), np.array(t, float), 640, 360, fov=50, bg=(70, 110, 160)); sheet.paste(im, ((i % 2) * 640, (i // 2) * 360))
    sheet.save(out)

if __name__ == "__main__" and len(sys.argv) > 3:
    stadium_preview(sys.argv[1], sys.argv[2]); sys.exit()
if __name__ == "__main__":
    m = Model(sys.argv[1]); out = sys.argv[2]
    shots = [("bind", None, 0, KIT, (1.2, 1.3, 3.2)), ("bat_stance", "bat_stance", 0.0, BAT, (2.6, 1.2, 1.6)),
             ("cover", "bat_cover_drive", 0.55, BAT, (2.6, 1.2, 1.8)), ("cover_follow", "bat_cover_drive", 0.9, BAT, (2.6, 1.2, 1.8)),
             ("pull", "bat_pull", 0.55, BAT, (2.6, 1.2, 1.8)), ("sweep", "bat_sweep", 0.55, BAT, (2.6, 1.0, 1.8)),
             ("bowl_release", "bowl_fast", 1.75, KIT, (3.2, 1.3, 0.8)), ("dive", "field_dive", 0.6, KIT, (1.5, 1.3, 3.2)),
             ("keeper", "keep_crouch", 0.0, KIT, (2.2, 1.0, 2.6)), ("ump_out", "ump_out", 1.0, KIT, (1.2, 1.4, 3.2))]
    imgs = []
    for name, an, t, inc, eye in shots:
        ai = m.anim_index(an) if an else None
        tr = m.triangles(inc, ai, t)
        allv = np.concatenate([x[0] for x in tr]); ctr = (allv.min(0) + allv.max(0)) / 2
        img = render(tr, ctr + np.array(eye) * 0.9, ctr, 300, 400, fov=40)
        imgs.append(img); print(name, "ok", allv.min(0).round(2), allv.max(0).round(2))
    sheet = Image.new("RGB", (300 * 5, 400 * 2))
    for i, im in enumerate(imgs): sheet.paste(im, ((i % 5) * 300, (i // 5) * 400))
    sheet.save(out)
