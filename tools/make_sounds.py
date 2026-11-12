"""Procedurally synthesises all game audio (original, royalty-free) into app/src/main/res/raw/*.wav"""
import numpy as np, wave, os
SR = 22050
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res", "raw")
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)
def t(d): return np.arange(int(SR * d)) / SR
def band(x, lo, hi):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    X[(f < lo) | (f > hi)] = 0; return np.fft.irfft(X, len(x))
def env(n, a, d):  # attack/decay seconds, exp decay
    tt = np.arange(n) / SR; e = np.minimum(1, tt / max(a, 1e-4)); return e * np.exp(-np.maximum(0, tt - a) / d)
def norm(x, peak=0.9): return x / (np.max(np.abs(x)) + 1e-9) * peak
def save(name, x, peak=0.9):
    x = norm(x, peak); d = (x * 32767).astype(np.int16)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(d.tobytes())
def knock(d, freqs, decay, noise=0.6):
    tt = t(d); x = sum(np.sin(2 * np.pi * f * tt + rng.random() * 6) * a for f, a in freqs)
    x = x * env(len(tt), 0.001, decay); n = band(rng.standard_normal(len(tt)), 1500, 8000) * env(len(tt), 0.0005, 0.012) * noise
    return x + n * 3
def crowd(d, swell=0.4, decay=1.2, density=40, lo=250, hi=3200):
    n = int(SR * d); x = np.zeros(n)
    for _ in range(density):  # many "voices": band-limited noise with own envelope + vibrato-ish AM
        c = rng.uniform(lo, hi); bw = c * 0.25
        v = band(rng.standard_normal(n), c - bw, c + bw)
        lfo = 0.6 + 0.4 * np.sin(2 * np.pi * rng.uniform(2, 7) * t(d) + rng.random() * 6)
        st = rng.uniform(0, swell)
        e = np.clip((t(d) - st) / 0.15, 0, 1) * np.exp(-np.maximum(0, t(d) - st - 0.3) / decay)
        x += v * lfo * e
    return x
def claps(d, rate=60):
    n = int(SR * d); x = np.zeros(n); burst = band(rng.standard_normal(int(SR * 0.03)), 800, 6000) * env(int(SR * 0.03), 0.001, 0.006)
    for _ in range(int(rate * d)):
        p = int(rng.uniform(0, n - len(burst))); g = np.exp(-p / SR / (d * 0.7)); x[p:p + len(burst)] += burst * g * rng.uniform(0.4, 1)
    return x
save("sfx_bat", knock(0.30, [(820, 1), (1650, .6), (2480, .35), (430, .5)], 0.05))
save("sfx_bat_big", np.concatenate([knock(0.35, [(760, 1), (1520, .7), (2300, .4), (380, .7)], 0.08), np.zeros(1)]) )
st = np.zeros(int(SR * 0.7))
for k, off in enumerate([0, 0.05, 0.11, 0.2, 0.32]):
    h = knock(0.25, [(1200 + 300 * k, 1), (2600 - 200 * k, .5)], 0.03, 0.4) * (1 - k * 0.15); p = int(off * SR); st[p:p + len(h)] += h
save("sfx_stumps", st)
save("sfx_cheer", crowd(2.6, 0.25, 0.9) + claps(2.6, 40) * 0.6)
save("sfx_roar", crowd(3.6, 0.15, 1.6, 60) + claps(3.6, 70) * 0.5)
w = crowd(3.0, 0.1, 1.2, 55, 300, 2600); save("sfx_wicket", w)
save("sfx_applause", claps(3.0, 90) + crowd(3.0, 0.2, 1.5, 12) * 0.15)
save("sfx_groan", band(crowd(1.6, 0.05, 0.5, 30, 150, 900), 120, 1200))
# ambient crowd loop (seamless: crossfade tail into head)
L = 6.0; a = crowd(L + 1, 0, 1e9, 50, 200, 2500); a *= 0.5 + 0.1 * np.sin(2 * np.pi * 0.2 * t(L + 1))
n = int(SR * L); fade = int(SR * 1); loop = a[:n].copy(); r = np.linspace(0, 1, fade); loop[:fade] = loop[:fade] * r + a[n:n + fade] * (1 - r)
save("amb_crowd", loop, 0.6)
tt = t(0.045); save("sfx_click", np.sin(2 * np.pi * 1800 * tt) * env(len(tt), 0.001, 0.008) + band(rng.standard_normal(len(tt)), 2000, 7000) * env(len(tt), 0.0005, 0.003) * .5, 0.6)
tt = t(0.5); thud = np.sin(2 * np.pi * (90 + 60 * np.exp(-tt * 30)) * tt) * env(len(tt), 0.001, 0.09)
save("sfx_hammer", thud * 1.2 + knock(0.5, [(620, 1), (1240, .5), (1900, .3)], 0.06, 0.8))
tt = t(0.9); save("sfx_coin", sum(np.sin(2 * np.pi * f * tt) * a for f, a in [(2093, 1), (3150, .6), (5240, .35), (6900, .2)]) * env(len(tt), 0.001, 0.25))
tt = t(1.4); horn = sum(np.sign(np.sin(2 * np.pi * f * tt)) * 0.3 + np.sin(2 * np.pi * f * tt) for f in (233, 294, 349))
save("sfx_horn", band(horn, 100, 2500) * np.clip(tt / 0.05, 0, 1) * np.clip((1.4 - tt) / 0.3, 0, 1), 0.7)
tt = t(0.35); save("sfx_whoosh", band(rng.standard_normal(len(tt)), 300, 3000) * np.sin(np.pi * tt / 0.35) ** 2, 0.5)
tt = t(1.2); save("sfx_success", sum(np.sin(2 * np.pi * f * tt) * env(len(tt), 0.01, 0.4) * (tt >= o) for f, o in [(523, 0), (659, .12), (784, .24), (1046, .36)]), 0.6)
# ---------------- background music loop: 120 bpm, 8 bars (16 s), Am-F-C-G ----------------
bpm = 120; beat = 60 / bpm; bars = 8; Ltot = bars * 4 * beat; N = int(SR * Ltot); mus = np.zeros(N)
def note(f, dur, kind="pad", amp=1.0):
    tt = t(dur)
    if kind == "pad": x = sum(np.sin(2 * np.pi * f * m * tt + 0.3 * np.sin(2 * np.pi * 5 * tt)) / m for m in (1, 2, 3)); e = np.clip(tt / 0.3, 0, 1) * np.clip((dur - tt) / 0.4, 0, 1)
    elif kind == "bass": x = np.sin(2 * np.pi * f * tt) + 0.4 * np.sin(4 * np.pi * f * tt); e = env(len(tt), 0.005, 0.25)
    else: x = sum(np.sin(2 * np.pi * f * m * tt) * 0.6 ** m for m in (1, 2, 3, 4)); e = env(len(tt), 0.002, 0.18)
    return x * e * amp
def put(x, at):
    p = int(at * SR); m = min(len(x), N - p); mus[p:p + m] += x[:m]
mf = lambda midi: 440 * 2 ** ((midi - 69) / 12)
chords = [[57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62]]
for b in range(bars):
    ch = chords[(b // 2) % 4]; t0 = b * 4 * beat
    for m in ch: put(note(mf(m), 4 * beat, "pad", 0.16), t0)
    for q in range(8): put(note(mf(ch[0] - 24 + (12 if q % 4 == 2 else 0)), beat / 2, "bass", 0.5), t0 + q * beat / 2)
    arp = [ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[1] + 24]
    for q in range(8): put(note(mf(arp[q % 4]), beat / 2, "pluck", 0.22 if q % 2 == 0 else 0.15), t0 + q * beat / 2)
    for q in range(4):  # drums
        kt = t(0.25); put(np.sin(2 * np.pi * (55 + 90 * np.exp(-kt * 35)) * kt) * env(len(kt), 0.001, 0.08) * 0.9, t0 + q * beat)
        if q % 2 == 1: st_ = t(0.2); put(band(rng.standard_normal(len(st_)), 900, 6000) * env(len(st_), 0.001, 0.05) * 0.35, t0 + q * beat)
        for h in range(2): ht = t(0.05); put(band(rng.standard_normal(len(ht)), 6000, 10000) * env(len(ht), 0.001, 0.012) * 0.18, t0 + q * beat + h * beat / 2)
save("music_theme", mus, 0.55)
print("ok", sorted(os.listdir(OUT)))
