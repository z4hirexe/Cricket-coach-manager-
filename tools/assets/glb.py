"""Minimal glTF 2.0 binary (GLB) writer: meshes, PBR materials, textures, skins, animations."""
import json, struct, io
import numpy as np

FLOAT, USHORT, UINT, UBYTE = 5126, 5123, 5125, 5121
ARRAY_BUFFER, ELEMENT_ARRAY_BUFFER = 34962, 34963
TYPES = {1: "SCALAR", 2: "VEC2", 3: "VEC3", 4: "VEC4", 16: "MAT4"}


class GLB:
    def __init__(self):
        self.g = {"asset": {"version": "2.0", "generator": "CricketCoachManager asset builder"}, "scenes": [{"nodes": []}], "scene": 0,
                  "nodes": [], "meshes": [], "materials": [], "accessors": [], "bufferViews": [], "buffers": [{"byteLength": 0}]}
        self.bin = io.BytesIO()
        self.extensions = set()

    def _view(self, data: bytes, target=None):
        pad = (-self.bin.tell()) % 4
        self.bin.write(b"\0" * pad)
        off = self.bin.tell(); self.bin.write(data)
        v = {"buffer": 0, "byteOffset": off, "byteLength": len(data)}
        if target: v["target"] = target
        self.g["bufferViews"].append(v)
        return len(self.g["bufferViews"]) - 1

    def accessor(self, arr, comp=FLOAT, target=None, normalized=False, minmax=False):
        arr = np.ascontiguousarray(arr)
        n = arr.shape[0]; w = 1 if arr.ndim == 1 else int(np.prod(arr.shape[1:]))
        dt = {FLOAT: np.float32, USHORT: np.uint16, UINT: np.uint32, UBYTE: np.uint8}[comp]
        a = arr.astype(dt).reshape(n, w) if w > 1 else arr.astype(dt)
        bv = self._view(a.tobytes(), target)
        acc = {"bufferView": bv, "componentType": comp, "count": n, "type": TYPES[w]}
        if normalized: acc["normalized"] = True
        if minmax or (target == ARRAY_BUFFER and w == 3 and comp == FLOAT):
            acc["min"] = a.reshape(n, w).min(0).tolist(); acc["max"] = a.reshape(n, w).max(0).tolist()
        self.g["accessors"].append(acc)
        return len(self.g["accessors"]) - 1

    def texture(self, png: bytes, repeat=True):
        bv = self._view(png)
        self.g.setdefault("images", []).append({"bufferView": bv, "mimeType": "image/png"})
        self.g.setdefault("samplers", []).append({"magFilter": 9729, "minFilter": 9987, "wrapS": 10497 if repeat else 33071, "wrapT": 10497 if repeat else 33071})
        self.g.setdefault("textures", []).append({"source": len(self.g["images"]) - 1, "sampler": len(self.g["samplers"]) - 1})
        return len(self.g["textures"]) - 1

    def material(self, name, color=(1, 1, 1, 1), metallic=0.0, roughness=0.6, tex=None, normal=None, emissive=None, double=False, unlit=False, blend=False):
        pbr = {"baseColorFactor": list(color), "metallicFactor": metallic, "roughnessFactor": roughness}
        if tex is not None: pbr["baseColorTexture"] = {"index": tex}
        m = {"name": name, "pbrMetallicRoughness": pbr}
        if normal is not None: m["normalTexture"] = {"index": normal, "scale": 1.0}
        if emissive is not None: m["emissiveFactor"] = list(emissive[:3])
        if double: m["doubleSided"] = True
        if blend: m["alphaMode"] = "BLEND"
        if unlit:
            m["extensions"] = {"KHR_materials_unlit": {}}; self.extensions.add("KHR_materials_unlit")
        self.g["materials"].append(m)
        return len(self.g["materials"]) - 1

    def primitive(self, pos, nrm, idx, mat, uv=None, joints=None, weights=None, color=None):
        attrs = {"POSITION": self.accessor(pos, FLOAT, ARRAY_BUFFER), "NORMAL": self.accessor(nrm, FLOAT, ARRAY_BUFFER)}
        if uv is not None: attrs["TEXCOORD_0"] = self.accessor(uv, FLOAT, ARRAY_BUFFER)
        if color is not None: attrs["COLOR_0"] = self.accessor(color, FLOAT, ARRAY_BUFFER)
        if joints is not None:
            attrs["JOINTS_0"] = self.accessor(joints, USHORT, ARRAY_BUFFER)
            attrs["WEIGHTS_0"] = self.accessor(weights, FLOAT, ARRAY_BUFFER)
        comp = USHORT if len(pos) < 65535 else UINT
        return {"attributes": attrs, "indices": self.accessor(np.asarray(idx).reshape(-1), comp, ELEMENT_ARRAY_BUFFER), "material": mat}

    def mesh(self, name, prims):
        self.g["meshes"].append({"name": name, "primitives": prims})
        return len(self.g["meshes"]) - 1

    def node(self, name, mesh=None, skin=None, t=None, r=None, s=None, children=None, root=False):
        n = {"name": name}
        if mesh is not None: n["mesh"] = mesh
        if skin is not None: n["skin"] = skin
        if t is not None: n["translation"] = [float(x) for x in t]
        if r is not None: n["rotation"] = [float(x) for x in r]
        if s is not None: n["scale"] = [float(x) for x in s]
        if children: n["children"] = list(children)
        self.g["nodes"].append(n)
        i = len(self.g["nodes"]) - 1
        if root: self.g["scenes"][0]["nodes"].append(i)
        return i

    def skin(self, joints, ibm, skeleton=None):
        s = {"joints": list(joints), "inverseBindMatrices": self.accessor(np.asarray(ibm, np.float32).reshape(len(joints), 16))}
        if skeleton is not None: s["skeleton"] = skeleton
        self.g.setdefault("skins", []).append(s)
        return len(self.g["skins"]) - 1

    def animation(self, name, channels):
        """channels: list of (node, path, times[N], values[N,k])"""
        samplers, chans = [], []
        for node, path, times, values in channels:
            ti = self.accessor(np.asarray(times, np.float32), FLOAT, minmax=True)
            vi = self.accessor(np.asarray(values, np.float32), FLOAT)
            samplers.append({"input": ti, "output": vi, "interpolation": "LINEAR"})
            chans.append({"sampler": len(samplers) - 1, "target": {"node": node, "path": path}})
        self.g.setdefault("animations", []).append({"name": name, "samplers": samplers, "channels": chans})

    def save(self, path):
        if self.extensions: self.g["extensionsUsed"] = sorted(self.extensions)
        b = self.bin.getvalue(); b += b"\0" * ((-len(b)) % 4)
        self.g["buffers"][0]["byteLength"] = len(b)
        j = json.dumps(self.g, separators=(",", ":")).encode(); j += b" " * ((-len(j)) % 4)
        out = struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(j) + 8 + len(b)) + struct.pack("<II", len(j), 0x4E4F534A) + j + struct.pack("<II", len(b), 0x004E4942) + b
        open(path, "wb").write(out)
        return len(out)


def smooth_normals(v, f):
    n = np.zeros_like(v, dtype=np.float64)
    fn = np.cross(v[f[:, 1]] - v[f[:, 0]], v[f[:, 2]] - v[f[:, 0]])
    for k in range(3): np.add.at(n, f[:, k], fn)
    l = np.linalg.norm(n, axis=1, keepdims=True); l[l == 0] = 1
    return (n / l).astype(np.float32)


def png_bytes(img):
    b = io.BytesIO(); img.save(b, "PNG", optimize=True); return b.getvalue()
