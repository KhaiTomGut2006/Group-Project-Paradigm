"""Generates the original retro SFX + looping BGM for Last Camp Survivor.
16-bit PCM mono 22050 Hz. stdlib only. Usage: python3 gen_sounds.py <outdir>"""
import math
import random
import struct
import sys
import wave

SR = 22050
OUT = sys.argv[1]
rng = random.Random(213)


def write(name, samples, gain=1.0, loop=False):
    samples = list(samples)
    if not loop:
        # 2 ms fade-in / 5 ms fade-out so no SFX starts or ends with a DC click
        fi, fo = 44, 110
        for i in range(min(fi, len(samples))):
            samples[i] *= i / fi
        for i in range(min(fo, len(samples))):
            samples[-1 - i] *= i / fo
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = gain * 0.89 / peak
    with wave.open(f"{OUT}/{name}.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for s in samples:
            v = int(max(-1.0, min(1.0, s * scale)) * 32767)
            frames += struct.pack("<h", v)
        w.writeframes(bytes(frames))


def n(sec):
    return int(sec * SR)


def env_adsr(i, total, a, d, s_level, r):
    """Envelope with attack/decay/sustain and a release over the last r seconds."""
    t = i / SR
    T = total / SR
    if t < a:
        e = t / a if a > 0 else 1.0
    elif t < a + d:
        e = 1.0 - (1.0 - s_level) * (t - a) / d
    else:
        e = s_level
    if t > T - r:
        e *= max(0.0, (T - t) / r)
    return e


class LowPass:
    def __init__(self, cutoff):
        self.y = 0.0
        self.set(cutoff)

    def set(self, cutoff):
        x = math.exp(-2.0 * math.pi * max(20.0, cutoff) / SR)
        self.a = 1.0 - x

    def __call__(self, v):
        self.y += self.a * (v - self.y)
        return self.y


class HighPass:
    def __init__(self, cutoff):
        self.lp = LowPass(cutoff)

    def __call__(self, v):
        return v - self.lp(v)


def noise():
    return rng.uniform(-1.0, 1.0)


def square(phase, duty=0.5):
    return 1.0 if (phase % 1.0) < duty else -1.0


def tri(phase):
    p = phase % 1.0
    return 4.0 * p - 1.0 if p < 0.5 else 3.0 - 4.0 * p


def gunshot(length, f0, f1, noise_cut0, noise_cut1, body=0.6, tail=0.5, crack=0.0):
    """Noise burst through a falling low-pass + a pitch-dropping thump."""
    total = n(length)
    lp = LowPass(noise_cut0)
    hp = HighPass(60)
    out = []
    phase = 0.0
    for i in range(total):
        t = i / total
        lp.set(noise_cut0 * (noise_cut1 / noise_cut0) ** t)
        e = math.exp(-t * (6.0 / tail))
        nz = lp(noise()) * e
        f = f0 * (f1 / f0) ** min(1.0, t * 3)
        phase += f / SR
        thump = math.sin(2 * math.pi * phase) * math.exp(-t * 14) * body
        c = noise() * math.exp(-i / (SR * 0.004)) * crack
        out.append(hp(nz + thump + c))
    return out


def sweep_tone(length, f0, f1, wave_fn, a=0.005, d=0.05, s=0.6, r=0.05, vib=0.0, cut=6000):
    total = n(length)
    lp = LowPass(cut)
    out = []
    phase = 0.0
    for i in range(total):
        t = i / total
        f = f0 * (f1 / f0) ** t
        f *= 1.0 + vib * math.sin(2 * math.pi * 7 * i / SR)
        phase += f / SR
        out.append(lp(wave_fn(phase)) * env_adsr(i, total, a, d, s, r))
    return out


def mix(*tracks):
    length = max(len(t) for t in tracks)
    out = [0.0] * length
    for tr in tracks:
        for i, v in enumerate(tr):
            out[i] += v
    return out


def concat_notes(notes, wave_fn, note_len, gap=0.0, cut=5000, duty=None):
    out = []
    for f in notes:
        total = n(note_len)
        lp = LowPass(cut)
        phase = 0.0
        for i in range(total):
            phase += f / SR
            v = square(phase, duty) if duty else wave_fn(phase)
            out.append(lp(v) * env_adsr(i, total, 0.004, 0.04, 0.55, note_len * 0.35))
        out += [0.0] * n(gap)
    return out


# ---------------------------------------------------------------- SFX
write("shoot_pistol", gunshot(0.16, 420, 90, 7000, 900, body=0.5, tail=0.35, crack=0.6))
write("shoot_rifle", gunshot(0.11, 520, 140, 8000, 1500, body=0.35, tail=0.25, crack=0.7))
write("shoot_heavy", mix(gunshot(0.38, 300, 55, 6000, 400, body=0.9, tail=0.6, crack=0.9),
                         [0.0] * n(0.05) + gunshot(0.3, 200, 60, 1500, 300, body=0.2, tail=0.8)))
write("shotgun", mix(gunshot(0.42, 260, 45, 5000, 350, body=1.0, tail=0.7, crack=1.0),
                     [0.0] * n(0.22) + gunshot(0.08, 900, 500, 4000, 2500, body=0.1, tail=0.2)))


def rocket():
    total = n(0.7)
    lp = LowPass(800)
    out = []
    for i in range(total):
        t = i / total
        lp.set(300 + 2500 * t)
        e = min(1.0, t * 12) * (1 - t) ** 1.5
        out.append(lp(noise()) * e + math.sin(2 * math.pi * (90 + 160 * t) * i / SR) * e * 0.3)
    return out


write("rocket", rocket())


def explosion():
    total = n(1.3)
    lp = LowPass(3000)
    out = []
    phase = 0.0
    for i in range(total):
        t = i / total
        lp.set(3200 * (0.04 / 1.0) ** t + 120)
        e = math.exp(-t * 4.0)
        crackle = noise() * (1.0 if rng.random() < 0.02 * (1 - t) else 0.0) * 0.6
        phase += (70 * (1 - 0.6 * t)) / SR
        boom = math.sin(2 * math.pi * phase) * math.exp(-t * 6) * 0.9
        out.append(lp(noise()) * e * 1.2 + boom + crackle)
    return out


write("explosion", explosion())


def hit():
    total = n(0.12)
    lp = LowPass(2500)
    out = []
    phase = 0.0
    for i in range(total):
        t = i / total
        phase += (220 * (1 - 0.6 * t)) / SR
        e = math.exp(-t * 7)
        out.append((lp(noise()) * 0.7 + tri(phase) * 0.6) * e)
    return out


write("hit", hit())


def hurt():
    total = n(0.3)
    out = []
    phase = 0.0
    lp = LowPass(1800)
    for i in range(total):
        t = i / total
        f = 340 * (0.45 ** t) * (1 + 0.04 * math.sin(2 * math.pi * 30 * i / SR))
        phase += f / SR
        out.append(lp(square(phase, 0.3)) * env_adsr(i, total, 0.002, 0.08, 0.5, 0.12) + lp(noise()) * 0.2 * math.exp(-t * 10))
    return out


write("hurt", hurt())
write("pickup", concat_notes([988, 1319], None, 0.06, cut=7000, duty=0.25), gain=0.75)
write("craft", mix(concat_notes([523, 659, 784, 1047], None, 0.075, cut=6000, duty=0.5),
                   [0.0] * n(0.0) + gunshot(0.1, 600, 300, 6000, 3000, body=0.0, tail=0.3, crack=0.3)), gain=0.8)
write("click", concat_notes([1400], None, 0.03, cut=8000, duty=0.5), gain=0.5)


def swing():
    total = n(0.18)
    out = []
    for i in range(total):
        t = i / total
        bp_center = 800 + 2600 * math.sin(math.pi * t)
        lp = swing.lp
        lp.set(bp_center)
        e = math.sin(math.pi * t) ** 2
        out.append(swing.hp(lp(noise())) * e)
    return out


swing.lp = LowPass(1000)
swing.hp = HighPass(400)
write("swing", swing(), gain=0.7)
write("enemy_shoot", gunshot(0.18, 700, 200, 5000, 1200, body=0.4, tail=0.3, crack=0.4), gain=0.8)


def spit():
    total = n(0.28)
    out = []
    lp = LowPass(1200)
    phase = 0.0
    for i in range(total):
        t = i / total
        phase += (180 + 260 * t) / SR
        gurgle = math.sin(2 * math.pi * phase + 3 * math.sin(2 * math.pi * 23 * i / SR))
        out.append((gurgle * 0.6 + lp(noise()) * 0.5) * env_adsr(i, total, 0.01, 0.05, 0.6, 0.1))
    return out


write("toxic_spit", spit(), gain=0.8)


def groan(length, base, gain_noise=0.25):
    total = n(length)
    out = []
    lp = LowPass(900)
    phase = 0.0
    for i in range(total):
        t = i / total
        f = base * (1 + 0.15 * math.sin(math.pi * t)) * (1 - 0.35 * t) * (1 + 0.03 * math.sin(2 * math.pi * 9 * i / SR))
        phase += f / SR
        v = square(phase, 0.35) * 0.6 + math.sin(2 * math.pi * phase * 2.01) * 0.3 + noise() * gain_noise
        out.append(lp(v) * env_adsr(i, total, 0.03, 0.1, 0.8, length * 0.4))
    return out


write("monster_death", groan(0.45, 140), gain=0.8)
write("boss_roar", mix(groan(1.2, 70, 0.5), groan(1.2, 105, 0.3)), gain=1.0)


def horn():
    out = []
    for f in (220, 220, 330):
        total = n(0.28 if f != 330 else 0.6)
        lp = LowPass(1600)
        phase = 0.0
        for i in range(total):
            phase += f / SR
            v = square(phase, 0.5) * 0.5 + square(phase * 1.005, 0.5) * 0.5
            out.append(lp(v) * env_adsr(i, total, 0.02, 0.05, 0.8, 0.08))
        out += [0.0] * n(0.05)
    return out


write("wave", horn(), gain=0.7)


def chest():
    creak = sweep_tone(0.25, 180, 320, lambda p: square(p, 0.2), cut=1500, vib=0.08)
    sparkle = [0.0] * n(0.2) + concat_notes([1047, 1319, 1568, 2093], None, 0.07, cut=8000, duty=0.25)
    return mix(creak, sparkle)


write("chest_open", chest(), gain=0.8)
write("gameover", concat_notes([392, 370, 349, 262], tri, 0.32, gap=0.04, cut=2500), gain=0.9)
write("win", concat_notes([523, 659, 784, 1047, 784, 1047], None, 0.13, cut=6000, duty=0.5), gain=0.8)


def perfect_hit():
    thump = gunshot(0.14, 240, 55, 4000, 600, body=0.9, tail=0.35, crack=0.4)
    chime = concat_notes([1047, 1319, 1568], None, 0.045, cut=8500, duty=0.25)
    return mix(thump, chime)


write("perfect_hit", perfect_hit(), gain=0.9)


def fever_activate():
    rise = sweep_tone(0.45, 220, 880, lambda p: square(p, 0.35), a=0.01, d=0.08, s=0.8, r=0.1, cut=7000)
    chord = mix(
        sweep_tone(0.65, 440, 440, tri, a=0.02, d=0.1, s=0.7, r=0.2, cut=6000),
        sweep_tone(0.65, 554.37, 554.37, tri, a=0.02, d=0.1, s=0.7, r=0.2, cut=6000),
        sweep_tone(0.65, 659.25, 659.25, tri, a=0.02, d=0.1, s=0.7, r=0.2, cut=6000),
    )
    return mix(rise, [0.0] * n(0.12) + chord, explosion()[:n(0.55)])


write("fever", fever_activate(), gain=0.95)
write("off_beat", concat_notes([155, 130], tri, 0.055, cut=900), gain=0.55)


def double_beat():
    # Twin bright synth-pluck chord (523Hz -> 784Hz) + crisp transient click for 8th-note Double-Beat hits
    click = gunshot(0.08, 420, 120, 6500, 1600, body=0.45, tail=0.22, crack=0.6)
    n1 = mix(sweep_tone(0.09, 523.25, 523.25, lambda p: square(p, 0.3), a=0.002, d=0.03, s=0.4, r=0.03, cut=7500),
             sweep_tone(0.09, 659.25, 659.25, tri, a=0.002, d=0.03, s=0.4, r=0.03, cut=7500))
    n2 = mix(sweep_tone(0.14, 783.99, 783.99, lambda p: square(p, 0.25), a=0.002, d=0.04, s=0.5, r=0.06, cut=8500),
             sweep_tone(0.14, 1046.5, 1046.5, tri, a=0.002, d=0.04, s=0.5, r=0.06, cut=8500))
    return mix(click, n1, [0.0] * n(0.055) + n2)


def finisher_boom():
    # Heavy sub-drop + metallic shatter + power chord for 3-2-1 STRIKE! Finisher explosions
    drop = sweep_tone(0.45, 160, 42, math.sin if False else tri, a=0.003, d=0.12, s=0.6, r=0.2, cut=600)
    shatter = gunshot(0.32, 900, 180, 9000, 1200, body=0.7, tail=0.5, crack=1.0)
    chord = mix(
        sweep_tone(0.50, 440, 440, lambda p: square(p, 0.4), a=0.005, d=0.08, s=0.65, r=0.2, cut=6500),
        sweep_tone(0.50, 659.25, 659.25, lambda p: square(p, 0.4), a=0.005, d=0.08, s=0.65, r=0.2, cut=6500),
        sweep_tone(0.50, 880, 880, tri, a=0.005, d=0.08, s=0.65, r=0.2, cut=6500),
    )
    return mix(drop, shatter, [0.0] * n(0.02) + chord)


def beat_dash():
    # Electric whoosh + rising zap for Beat Dash Parry
    zap = sweep_tone(0.18, 320, 1280, lambda p: square(p, 0.25), a=0.002, d=0.04, s=0.55, r=0.08, cut=7500)
    whoosh = gunshot(0.16, 1200, 450, 7500, 1800, body=0.2, tail=0.45, crack=0.2)
    return mix(zap, whoosh)


write("double_beat", double_beat(), gain=0.88)
write("finisher_boom", finisher_boom(), gain=0.95)
write("beat_dash", beat_dash(), gain=0.82)


# ---------------------------------------------------------------- BGM (seamless loop)
BPM = 125
BEAT = 60.0 / BPM                # 0.48 s per beat = exact 30 simulation ticks at 16 ms/tick
BEATS = 32                       # 8 bars of 4/4 = 15.36 s
N = int(round(BEATS * BEAT * SR))
buf = [0.0] * N


def add_circular(start, samples, gain):
    """Adds a note into the loop buffer; anything running past the end wraps to the start."""
    for k, v in enumerate(samples):
        buf[(start + k) % N] += v * gain


def note_samples(freq, length, kind, cutoff, a=0.01, d=0.1, s=0.6, r=0.15):
    total = n(length)
    lp = LowPass(cutoff)
    phase = 0.0
    out = []
    for i in range(total):
        phase += freq / SR
        if kind == "saw":
            v = 2.0 * (phase % 1.0) - 1.0
        elif kind == "sq":
            v = square(phase, 0.5)
        elif kind == "pulse":
            v = square(phase, 0.125)
        else:
            v = tri(phase)
        out.append(lp(v) * env_adsr(i, total, a, d, s, r))
    return out


def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


def at(beat):
    return int(round(beat * BEAT * SR))


# driving darksynth / electro-rock progression (A minor: Am Am F G Am Am Dm E)
roots = [45, 45, 41, 43, 45, 45, 38, 40]
pads = [[57, 60, 64], [57, 60, 64], [53, 57, 60], [55, 59, 62], [57, 60, 64], [57, 60, 64], [50, 53, 57], [52, 56, 59]]
for bar, root in enumerate(roots):
    base = bar * 4
    # bass: driving eighths with an octave pop on the off-beat of beat 3
    pattern = [0, 0, 12, 0, 0, 12, 0, 7]
    for k, step in enumerate(pattern):
        add_circular(at(base + k * 0.5), note_samples(midi(root + step - 12), BEAT * 0.44, "saw", 620, a=0.003, d=0.07, s=0.55, r=0.05), 0.45)
    # pad: soft triangle chord
    for m in pads[bar]:
        add_circular(at(base), note_samples(midi(m), BEAT * 4.1, "tri", 1500, a=0.25, d=0.4, s=0.65, r=0.6), 0.08)

# lead arpeggio across all 8 bars + rising 4-beat end-of-bar fill cuing 3-2-1 STRIKE! finishers
arp = [69, 72, 76, 72, 69, 72, 76, 79]
fill_notes = [76, 79, 81, 84]
for bar in range(8):
    shift = pads[bar][0] - 57
    gain_arp = 0.085 if bar < 4 else 0.13
    cut_arp = 2600 if bar < 4 else 3800
    for k, m in enumerate(arp):
        add_circular(at(bar * 4 + k * 0.5), note_samples(midi(m + shift + 12), BEAT * 0.28, "pulse", cut_arp, a=0.002, d=0.04, s=0.45, r=0.06), gain_arp)
    # rising synth fill on beats 3.0, 3.25, 3.5, 3.75 of every bar
    for fk, fm in enumerate(fill_notes):
        add_circular(at(bar * 4 + 3.0 + fk * 0.25), note_samples(midi(fm + shift), BEAT * 0.22, "saw", 3400, a=0.002, d=0.03, s=0.4, r=0.04), 0.11)

# drums: punchy four-on-the-floor kick + sub-bass every beat, crisp 8th-note accents for Double-Beats
for beat in range(BEATS):
    total = n(0.25)
    k_out = []
    phase = 0.0
    for i in range(total):
        t = i / total
        phase += (135 * (0.30 ** t)) / SR
        click = noise() * math.exp(-i / (SR * 0.004)) * 0.42
        k_out.append(math.sin(2 * math.pi * phase) * math.exp(-t * 6.0) + click)
    add_circular(at(beat), k_out, 0.72)
    # snare on 2 and 4 (beat % 2 == 1)
    if beat % 2 == 1:
        total = n(0.18)
        lp = LowPass(5200)
        s_out = [lp(noise()) * math.exp(-(i / total) * 6.0) + math.sin(2 * math.pi * 210 * i / SR) * math.exp(-(i / total) * 14) * 0.50 for i in range(total)]
        add_circular(at(beat), s_out, 0.34)
    # crisp eighth-note hi-hats + off-beat snare ghost click on +0.5 so Double-Beat windows pop clearly
    for half in (0.0, 0.5):
        total = n(0.055)
        hp = HighPass(5500)
        h_out = [hp(noise()) * math.exp(-(i / total) * 7.5) for i in range(total)]
        add_circular(at(beat + half), h_out, 0.17 if half else 0.09)
    off_total = n(0.06)
    off_lp = LowPass(3600)
    off_out = [off_lp(noise()) * math.exp(-(i / off_total) * 10.0) * 0.35 + math.sin(2 * math.pi * 320 * i / SR) * math.exp(-(i / off_total) * 16) * 0.25 for i in range(off_total)]
    add_circular(at(beat + 0.5), off_out, 0.22)

write("bgm", buf, gain=0.88, loop=True)
print("ok")
