"""Procedurally synthesises all game audio (original, royalty-free) into app/src/main/res/raw/.

Short SFX -> 16-bit mono WAV (lowest SoundPool latency). Long loops (music, ambience layers) -> OGG Vorbis (small APK).
Audio 2.0 (Phase 8): stadium reverb on every crowd/ball sound, layered ambience (bed / tension / chant),
per-event sounds (edge, pad, catch, near-miss "ooh", dhol, bugle), auction-room SFX and an auction music loop.
Run: python3 tools/make_sounds.py   (needs numpy + soundfile)
"""
import os
import numpy as np
import wave
import soundfile as sf

SR = 22050
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res", "raw")
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(11)


def t(d): return np.arange(int(SR * d)) / SR


def band(x, lo, hi):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    X[(f < lo) | (f > hi)] = 0
    return np.fft.irfft(X, len(x))


def env(n, a, d):
    tt = np.arange(n) / SR; e = np.minimum(1, tt / max(a, 1e-4)); return e * np.exp(-np.maximum(0, tt - a) / d)


def norm(x, peak=0.9): return x / (np.max(np.abs(x)) + 1e-9) * peak


def fade(x, a=0.0003, r=0.02):
    n = len(x); ia = min(n, int(a * SR)); ir = min(n, int(r * SR))
    if ia: x[:ia] *= np.linspace(0, 1, ia)
    if ir: x[-ir:] *= np.linspace(1, 0, ir)
    return x


def conv(x, h):
    n = len(x) + len(h) - 1; N = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, N) * np.fft.rfft(h, N), N)[:n]


def reverb(x, size=1.2, wet=0.25, lo=200, hi=6000, tail=True):
    """Open-air stadium reverb: exponentially decaying band-limited noise impulse + early reflections."""
    L = int(SR * size); h = band(rng.standard_normal(L), lo, hi) * np.exp(-np.arange(L) / (SR * size / 6.5))
    for d, g in ((0.031, .5), (0.047, .35), (0.083, .25), (0.12, .18)): h[int(d * SR)] += g * 8
    h /= np.sqrt(np.sum(h ** 2)) + 1e-9
    y = conv(x, h) * wet
    out = np.zeros(len(y)); out[:len(x)] += x * (1 - wet * 0.4); out += y
    return out if tail else out[:len(x)]


def save(name, x, peak=0.9):
    x = norm(fade(np.asarray(x, dtype=np.float64)), peak); d = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    for ext in (".ogg", ".wav"):
        p = os.path.join(OUT, name + ext)
        if os.path.exists(p): os.remove(p)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(d.tobytes())


def save_loop(name, x, peak=0.6, q=0.35):
    x = norm(np.asarray(x, dtype=np.float64), peak)
    for ext in (".ogg", ".wav"):
        p = os.path.join(OUT, name + ext)
        if os.path.exists(p): os.remove(p)
    sf.write(os.path.join(OUT, name + ".ogg"), x.astype(np.float32), SR, format="OGG", subtype="VORBIS")


def seamless(a, L):
    """Make a seamless loop of L seconds from a buffer at least L+1 s long (crossfade tail into head)."""
    n = int(SR * L); fd = int(SR * 1); loop = a[:n].copy(); r = np.linspace(0, 1, fd)
    loop[:fd] = loop[:fd] * r + a[n:n + fd] * (1 - r)
    return loop


def put(buf, x, at):
    p = int(at * SR)
    if p >= len(buf): return
    m = min(len(x), len(buf) - p); buf[p:p + m] += x[:m]


# ----------------------------------------------------------------- building blocks
def knock(d, freqs, decay, noise=0.6, nlo=1500, nhi=8000):
    tt = t(d); x = sum(np.sin(2 * np.pi * f * tt + rng.random() * 6) * a for f, a in freqs)
    x = x * env(len(tt), 0.001, decay); n = band(rng.standard_normal(len(tt)), nlo, nhi) * env(len(tt), 0.0005, 0.012) * noise
    return x + n * 3


def voices(d, n_voices=40, lo=250, hi=3000, start=(0, 0.3), rise=0.15, decay=1.2, glide=0.0, vowel=None):
    """A crowd: many 'voices' of band-limited noise with their own onset, vibrato-like AM and optional pitch glide."""
    n = int(SR * d); x = np.zeros(n); tt = t(d)
    for _ in range(n_voices):
        c = rng.uniform(lo, hi); bw = c * rng.uniform(0.15, 0.3)
        v = band(rng.standard_normal(n), c - bw, c + bw)
        if glide:
            # cheap pitch glide: modulate with a slow chirp
            v *= 1 + 0.25 * np.sin(2 * np.pi * (c * 0.01) * tt * (1 + glide * tt))
        lfo = 0.6 + 0.4 * np.sin(2 * np.pi * rng.uniform(2, 7) * tt + rng.random() * 6)
        st = rng.uniform(*start)
        e = np.clip((tt - st) / rise, 0, 1) * np.exp(-np.maximum(0, tt - st - 0.3) / decay)
        x += v * lfo * e
    if vowel:  # formant emphasis (e.g. 'oo' = 300/870 Hz, 'ah' = 700/1220 Hz)
        x = x * 0.35 + sum(band(x, f * 0.8, f * 1.2) * g for f, g in vowel)
    return x


def claps(d, rate=60, decay_frac=0.7, steady=False):
    n = int(SR * d); x = np.zeros(n); L = int(SR * 0.03)
    for _ in range(int(rate * d)):
        burst = band(rng.standard_normal(L), rng.uniform(700, 1200), rng.uniform(4500, 7000)) * env(L, 0.001, rng.uniform(0.004, 0.008))
        p = int(rng.uniform(0, n - L)); g = 1.0 if steady else np.exp(-p / SR / (d * decay_frac))
        x[p:p + L] += burst * g * rng.uniform(0.3, 1)
    return x


def whistle(d=0.7, f0=2600):
    tt = t(d); f = f0 + 500 * np.sin(np.pi * tt / d) ** 2
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.clip(tt / 0.05, 0, 1) * np.clip((d - tt) / 0.15, 0, 1) * 0.3


def drum(f0, d, decay, click=0.4, bend=60):
    tt = t(d); ph = 2 * np.pi * np.cumsum(f0 + bend * np.exp(-tt * 40)) / SR
    return np.sin(ph) * env(len(tt), 0.001, decay) + band(rng.standard_normal(len(tt)), 1500, 6000) * env(len(tt), 0.0005, 0.006) * click


def dhol_pattern(d, bpm=150, intensity=1.0):
    """Bhangra-style dhol groove: low 'dagga' on the bass side, high 'tilli' cracks in between (original pattern)."""
    x = np.zeros(int(SR * d)); beat = 60 / bpm
    low = [0, 0.75, 1.5, 2.0, 2.75, 3.5]           # in beats, per 4-beat bar
    high = [0.5, 1.0, 1.25, 2.5, 3.0, 3.25, 3.75]
    bar = 4 * beat; b = 0.0
    while b < d:
        for o in low: put(x, drum(78, 0.5, 0.18, 0.3, 45) * 1.0 * intensity, b + o * beat)
        for o in high: put(x, drum(420, 0.15, 0.035, 1.0, 120) * 0.45 * intensity, b + o * beat)
        b += bar
    return x


def brass(freqs_durs, bpm=132, amp=0.8):
    """Simple brass-synth phrase (stadium bugle 'charge'-style, original melody)."""
    beat = 60 / bpm; total = sum(dd for _, dd in freqs_durs) * beat + 0.4; x = np.zeros(int(SR * total)); at = 0.0
    for f, dd in freqs_durs:
        dur = dd * beat; tt = t(dur * 0.95)
        vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * tt)
        ph = 2 * np.pi * np.cumsum(f * vib) / SR
        tone = sum(np.sin(ph * k) * (0.8 ** k) for k in range(1, 8))
        e = np.clip(tt / 0.03, 0, 1) * np.clip((dur * 0.95 - tt) / 0.05, 0, 1)
        put(x, tone * e * amp, at); at += dur
    return band(x, 150, 5000)


# ----------------------------------------------------------------- bat & ball (dry close-mic + a touch of stadium)
def willow(d, body, crack, decay):
    tt = t(d)
    modes = sum(np.sin(2 * np.pi * f * tt + rng.random() * 6) * a * np.exp(-tt / (decay * k)) for f, a, k in body)
    tr = band(rng.standard_normal(len(tt)), 2000, 9500) * env(len(tt), 0.0003, 0.004) * crack
    thump = np.sin(2 * np.pi * 180 * tt) * env(len(tt), 0.001, 0.02) * 0.6
    return modes + tr + thump


save("sfx_bat", reverb(willow(0.32, [(980, 1, 1), (1720, .6, .7), (2650, .35, .5), (520, .5, 1.4)], 3.0, 0.045), 0.6, 0.12))
save("sfx_bat_big", reverb(willow(0.45, [(900, 1, 1), (1580, .75, .8), (2480, .45, .5), (460, .8, 1.5)], 4.5, 0.075), 0.9, 0.22))
save("sfx_edge", reverb(knock(0.18, [(2400, 1), (3900, .6), (5200, .3)], 0.012, 0.9, 3000, 9500), 0.5, 0.1), 0.7)
tt = t(0.3); pad = np.sin(2 * np.pi * (140 + 80 * np.exp(-tt * 50)) * tt) * env(len(tt), 0.002, 0.05) + band(rng.standard_normal(len(tt)), 300, 1800) * env(len(tt), 0.001, 0.025) * 0.8
save("sfx_pad", reverb(pad, 0.5, 0.1), 0.75)
tt = t(0.25); pop = band(rng.standard_normal(len(tt)), 400, 3500) * env(len(tt), 0.0008, 0.018) + np.sin(2 * np.pi * 320 * tt) * env(len(tt), 0.001, 0.03) * 0.8
save("sfx_glove", reverb(pop, 0.5, 0.12), 0.75)

st = np.zeros(int(SR * 0.8))
for k, off in enumerate([0, 0.04, 0.09, 0.17, 0.3]):
    h = knock(0.3, [(1150 + 330 * k, 1), (2550 - 180 * k, .5), (3900, .2)], 0.035, 0.5) * (1 - k * 0.15); put(st, h, off)
save("sfx_stumps", reverb(st, 0.8, 0.2))

# ----------------------------------------------------------------- crowd reactions (all with stadium reverb)
def crowd_fx(x, size=1.6, wet=0.32): return reverb(x, size, wet, 150, 5000)


cheer = voices(2.8, 45, 250, 3200, (0, 0.25), 0.12, 0.9) + claps(2.8, 45) * 0.55; put(cheer, whistle(0.6) * 0.6, 0.3)
save("sfx_cheer", crowd_fx(cheer))
save("sfx_cheer2", crowd_fx(voices(2.6, 45, 300, 3000, (0, 0.2), 0.1, 0.8, vowel=[(700, .8), (1220, .5)]) + claps(2.6, 55) * 0.5))
roar = voices(4.0, 70, 200, 3200, (0, 0.15), 0.1, 1.7, vowel=[(700, .7), (1220, .5)]) + claps(4.0, 80) * 0.45
put(roar, whistle(0.8, 2800), 0.4); put(roar, whistle(0.6, 3100), 1.1)
save("sfx_roar", crowd_fx(roar, 2.0, 0.35))
roar2 = voices(4.2, 70, 220, 3000, (0, 0.12), 0.08, 1.9) + claps(4.2, 90) * 0.5
put(roar2, whistle(0.7, 2500), 0.6)
save("sfx_roar2", crowd_fx(roar2, 2.0, 0.35))
save("sfx_wicket", crowd_fx(voices(3.2, 60, 300, 2600, (0, 0.1), 0.08, 1.2, vowel=[(700, .7), (1100, .5)]) + claps(3.2, 40, 0.8) * 0.35))
save("sfx_applause", crowd_fx(claps(3.4, 95) + voices(3.4, 14, 300, 2400, (0, 0.3), 0.3, 1.5) * 0.15, 1.4, 0.3))
save("sfx_groan", crowd_fx(band(voices(1.8, 35, 150, 1000, (0, 0.08), 0.12, 0.55, vowel=[(500, .8), (900, .4)]), 120, 1300)))
# near miss / beaten bat: rising then falling "ooooh"
ooh = voices(1.9, 45, 220, 1200, (0, 0.1), 0.25, 0.6, glide=0.6, vowel=[(300, 1.0), (870, .6)])
save("sfx_ooh", crowd_fx(band(ooh, 150, 1600)))
# appeal: whole ground shouts ("howzat"-like burst, no words)
app = voices(1.6, 55, 300, 2200, (0, 0.06), 0.05, 0.35, vowel=[(700, .9), (1200, .6)])
save("sfx_appeal", crowd_fx(app, 1.4, 0.3))
# catch taken: glove pop + crowd jump
c = np.zeros(int(SR * 2.4)); put(c, pop * 2.5, 0); put(c, voices(2.3, 55, 300, 2800, (0.05, 0.15), 0.06, 0.9) * 0.9, 0.05)
save("sfx_catch", crowd_fx(c))
save("sfx_dhol", reverb(dhol_pattern(3.2, 152), 1.0, 0.18), 0.85)
bugle_notes = [(392, .5), (523, .5), (659, .5), (784, 1.0), (659, .5), (784, 1.5)]
save("sfx_bugle", reverb(brass(bugle_notes), 1.6, 0.35), 0.7)

# ----------------------------------------------------------------- UI & management
tt = t(0.045); save("sfx_click", np.sin(2 * np.pi * 1800 * tt) * env(len(tt), 0.001, 0.008) + band(rng.standard_normal(len(tt)), 2000, 7000) * env(len(tt), 0.0005, 0.003) * .5, 0.6)
tt = t(0.6); thud = np.sin(2 * np.pi * (90 + 60 * np.exp(-tt * 30)) * tt) * env(len(tt), 0.001, 0.1)
hammer = thud * 1.3 + knock(0.6, [(620, 1), (1240, .5), (1900, .3)], 0.06, 0.9)
save("sfx_hammer", reverb(hammer, 1.0, 0.25))
tt = t(0.3); tap = knock(0.3, [(700, 1), (1400, .45), (2100, .25)], 0.035, 0.5) + np.sin(2 * np.pi * 120 * tt) * env(len(tt), 0.001, 0.04) * 0.6
save("sfx_gavel_tap", reverb(tap, 0.7, 0.2), 0.6)
# SOLD: big gavel bang + room applause
sold = np.zeros(int(SR * 3.0)); put(sold, hammer, 0); put(sold, claps(2.6, 70) * 0.5 + voices(2.6, 25, 300, 2400, (0.05, 0.2), 0.1, 0.9) * 0.35, 0.12)
save("sfx_sold", reverb(sold, 1.1, 0.25))
tt = t(0.9); unsold = sum(np.sin(2 * np.pi * f * tt) * env(len(tt), 0.01, 0.25) * (tt >= o) for f, o in [(311, 0), (233, 0.22)])
save("sfx_unsold", reverb(unsold, 0.8, 0.2), 0.5)
# paddle raise (whoosh + soft clack) and a rival bid (short murmur tick)
tt = t(0.32); sw = band(rng.standard_normal(len(tt)), 600, 4000) * np.sin(np.pi * np.minimum(tt / 0.22, 1)) ** 2
pad_clack = np.zeros(len(tt)); put(pad_clack, knock(0.1, [(1500, 1), (2600, .4)], 0.01, 0.4), 0.2)
save("sfx_paddle", sw * 0.6 + pad_clack, 0.6)
rb = np.zeros(int(SR * 0.9)); put(rb, knock(0.1, [(1300, 1), (2300, .4)], 0.01, 0.3), 0); put(rb, voices(0.85, 12, 250, 1400, (0, 0.05), 0.08, 0.35) * 0.25, 0.02)
save("sfx_bid_rival", reverb(rb, 0.7, 0.2), 0.45)
tt = t(0.9); save("sfx_coin", sum(np.sin(2 * np.pi * f * tt) * a for f, a in [(2093, 1), (3150, .6), (5240, .35), (6900, .2)]) * env(len(tt), 0.001, 0.25))
tt = t(1.4); horn = sum(np.sign(np.sin(2 * np.pi * f * tt)) * 0.3 + np.sin(2 * np.pi * f * tt) for f in (233, 294, 349))
save("sfx_horn", reverb(band(horn, 100, 2500) * np.clip(tt / 0.05, 0, 1) * np.clip((1.4 - tt) / 0.3, 0, 1), 1.8, 0.3), 0.7)
tt = t(0.35); save("sfx_whoosh", band(rng.standard_normal(len(tt)), 300, 3000) * np.sin(np.pi * tt / 0.35) ** 2, 0.5)
tt = t(1.2); save("sfx_success", reverb(sum(np.sin(2 * np.pi * f * tt) * env(len(tt), 0.01, 0.4) * (tt >= o) for f, o in [(523, 0), (659, .12), (784, .24), (1046, .36)]), 0.9, 0.2), 0.6)
tt = t(0.35); save("sfx_notify", sum(np.sin(2 * np.pi * f * tt) * env(len(tt), 0.003, 0.09) * (tt >= o) for f, o in [(880, 0), (1320, 0.08)]), 0.5)
tt = t(0.35); save("sfx_error", sum(np.sign(np.sin(2 * np.pi * f * tt)) * 0.2 * env(len(tt), 0.003, 0.08) * (tt >= o) for f, o in [(300, 0), (220, 0.1)]), 0.45)

# ----------------------------------------------------------------- ambience layers (seamless loops, OGG)
L = 12.0
bed = voices(L + 1, 60, 180, 2400, (0, 0), 0.01, 1e9) * (0.55 + 0.1 * np.sin(2 * np.pi * 0.11 * t(L + 1)))
bed += band(rng.standard_normal(len(bed)), 60, 300) * 0.15                       # low rumble of a full ground
bed += claps(L + 1, 6, steady=True) * 0.25                                         # scattered claps
for at in rng.uniform(0.5, L - 1, 3): put(bed, whistle(0.5, rng.uniform(2300, 3200)) * 0.25, at)
save_loop("amb_crowd", seamless(reverb(bed, 1.6, 0.3, tail=False), L), 0.6)
# tension: hushed, nervous murmur + slow heartbeat-like low drum (tight finishes)
Lt = 8.0
ten = voices(Lt + 1, 40, 200, 1400, (0, 0), 0.01, 1e9) * 0.45
for k in range(int((Lt + 1) / 0.8)):
    put(ten, drum(52, 0.4, 0.12, 0.05, 20) * 0.9, k * 0.8); put(ten, drum(52, 0.4, 0.1, 0.05, 20) * 0.55, k * 0.8 + 0.22)
save_loop("amb_tension", seamless(reverb(ten, 1.4, 0.3, tail=False), Lt), 0.55)
# chant: rhythmic clap-clap-clap + crowd swells + dhol (no words, original rhythm)
Lc = 8.0
ch = np.zeros(int(SR * (Lc + 1))); beat = 60 / 120
for b in range(int((Lc + 1) / beat)):
    if b % 4 in (0, 1, 2):
        put(ch, claps(0.12, 400, steady=True) * 0.9, b * beat)
    if b % 4 == 3:
        put(ch, voices(0.5, 30, 300, 1800, (0, 0.02), 0.04, 0.2, vowel=[(700, .8), (1200, .5)]) * 0.6, b * beat)
ch += dhol_pattern(Lc + 1, 120, 0.45)
ch += voices(Lc + 1, 30, 200, 2000, (0, 0), 0.01, 1e9) * 0.25
save_loop("amb_chant", seamless(reverb(ch, 1.6, 0.3, tail=False), Lc), 0.55)
# auction room murmur
La = 10.0
room = voices(La + 1, 25, 200, 1600, (0, 0), 0.01, 1e9) * 0.35 + band(rng.standard_normal(int(SR * (La + 1))), 80, 400) * 0.05
save_loop("amb_auction", seamless(reverb(room, 0.9, 0.25, tail=False), La), 0.45)


# ----------------------------------------------------------------- music
def make_music(bpm, bars, chords, arp_shape, pad_amp, bass_pat, drums, name, peak, key_shift=0):
    beat = 60 / bpm; Ltot = bars * 4 * beat; N = int(SR * Ltot); mus = np.zeros(N)

    def note(f, dur, kind="pad", amp=1.0):
        tt = t(dur)
        if kind == "pad": x = sum(np.sin(2 * np.pi * f * m * tt + 0.3 * np.sin(2 * np.pi * 5 * tt)) / m for m in (1, 2, 3)); e = np.clip(tt / 0.3, 0, 1) * np.clip((dur - tt) / 0.4, 0, 1)
        elif kind == "bass": x = np.sin(2 * np.pi * f * tt) + 0.4 * np.sin(4 * np.pi * f * tt); e = env(len(tt), 0.005, 0.25)
        else: x = sum(np.sin(2 * np.pi * f * m * tt) * 0.6 ** m for m in (1, 2, 3, 4)); e = env(len(tt), 0.002, 0.18)
        return x * e * amp
    mf = lambda midi: 440 * 2 ** ((midi + key_shift - 69) / 12)
    for b in range(bars):
        ch = chords[(b // 2) % len(chords)]; t0 = b * 4 * beat
        for m in ch: put(mus, note(mf(m), 4 * beat, "pad", pad_amp), t0)
        for q, step in enumerate(bass_pat):
            if step is not None: put(mus, note(mf(ch[0] - 24 + step), beat / 2, "bass", 0.5), t0 + q * beat / 2)
        arp = [ch[i % 3] + 12 * (1 + i // 3) for i in arp_shape]
        for q in range(8): put(mus, note(mf(arp[q % len(arp)]), beat / 2, "pluck", 0.22 if q % 2 == 0 else 0.15), t0 + q * beat / 2)
        drums(mus, t0, beat)
    save_loop(name, mus, peak)


def pop_drums(mus, t0, beat):
    for q in range(4):
        put(mus, drum(55, 0.25, 0.08, 0.2, 90) * 0.9, t0 + q * beat)
        if q % 2 == 1: st_ = t(0.2); put(mus, band(rng.standard_normal(len(st_)), 900, 6000) * env(len(st_), 0.001, 0.05) * 0.35, t0 + q * beat)
        for h in range(2): ht = t(0.05); put(mus, band(rng.standard_normal(len(ht)), 6000, 10000) * env(len(ht), 0.001, 0.012) * 0.18, t0 + q * beat + h * beat / 2)


def tense_drums(mus, t0, beat):
    for q in range(4):
        put(mus, drum(48, 0.35, 0.12, 0.1, 40) * (1.0 if q == 0 else 0.6), t0 + q * beat)
        for h in range(4): ht = t(0.04); put(mus, band(rng.standard_normal(len(ht)), 7000, 10500) * env(len(ht), 0.001, 0.01) * (0.16 if h % 2 == 0 else 0.08), t0 + q * beat + h * beat / 4)
    put(mus, drum(200, 0.3, 0.06, 0.6, 80) * 0.5, t0 + 3.5 * beat)


# menu theme: 120 bpm, Am-F-C-G, uplifting
make_music(120, 8, [[57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62]], [0, 1, 2, 4], 0.16, [0, 0, 12, 0, 0, 0, 12, 0], pop_drums, "music_theme", 0.55)
# auction theme: 104 bpm, Dm-Bb-Gm-A, ticking hats, pulsing bass (tension of the bidding room)
make_music(104, 8, [[50, 53, 57], [46, 50, 53], [43, 46, 50], [45, 49, 52]], [0, 2, 1, 2], 0.12, [0, 0, 0, 0, 0, 0, 0, 0], tense_drums, "music_auction", 0.5)

print("ok", sorted(os.listdir(OUT)))
