from pathlib import Path
import math
import random
import struct
import wave

OUT = Path('ACAI_DA_7_PAINEL_ANDROID_FCM_PROJETO/AcaiDa7PainelApp/app/src/main/res/raw')
OUT.mkdir(parents=True, exist_ok=True)
SR = 44100

# 50 deliberately different notification styles.
# They are generated locally during GitHub Actions, so no external audio
# files or copyrighted ringtones are required.
ROOTS = [261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25, 587.33, 659.25]
SCALES = {
    'major': [1, 9/8, 5/4, 4/3, 3/2, 5/3, 15/8, 2],
    'pent': [1, 9/8, 5/4, 3/2, 5/3, 2],
    'minor': [1, 9/8, 6/5, 4/3, 3/2, 8/5, 9/5, 2],
    'blues': [1, 6/5, 4/3, 7/5, 3/2, 9/5, 2],
}


def envelope(t, duration, attack=0.012, release=0.10, decay=0.0):
    attack = min(attack, max(0.001, duration * 0.25))
    release = min(release, max(0.005, duration * 0.45))
    if t < attack:
        return t / attack
    if decay > 0:
        sustain = max(0.0, 1.0 - decay * (t - attack) / max(0.001, duration - attack))
    else:
        sustain = 1.0
    if t > duration - release:
        sustain *= max(0.0, (duration - t) / release)
    return sustain


def osc(freq, t, kind):
    phase = 2 * math.pi * freq * t
    if kind == 'sine':
        return math.sin(phase)
    if kind == 'triangle':
        return 2 * abs(2 * ((freq * t) % 1) - 1) - 1
    if kind == 'square':
        return 1.0 if math.sin(phase) >= 0 else -1.0
    if kind == 'soft_square':
        return math.tanh(2.5 * math.sin(phase))
    if kind == 'saw':
        return 2 * ((freq * t) % 1) - 1
    if kind == 'bell':
        return (
            0.90 * math.sin(phase)
            + 0.45 * math.sin(phase * 2.01)
            + 0.25 * math.sin(phase * 3.99)
            + 0.12 * math.sin(phase * 6.97)
        )
    if kind == 'marimba':
        return (
            0.92 * math.sin(phase)
            + 0.30 * math.sin(phase * 4.01)
            + 0.12 * math.sin(phase * 10.0)
        )
    if kind == 'piano':
        return (
            0.72 * math.sin(phase)
            + 0.22 * math.sin(phase * 2.0)
            + 0.10 * math.sin(phase * 3.0)
        )
    return math.sin(phase)


def make_sound(index, family, variant):
    root = ROOTS[(index * 3 + variant) % len(ROOTS)]
    scale_name = list(SCALES)[(index + variant) % len(SCALES)]
    scale = SCALES[scale_name]

    patterns = {
        'bell': [0, 2, 4, 7],
        'chime': [4, 2, 0, 7, 4],
        'marimba': [0, 2, 4, 2, 7],
        'piano': [0, 4, 2, 5],
        'pluck': [0, 2, 4, 7, 4, 2],
        'synth': [0, 4, 7, 12],
        'arpeggio': [0, 2, 4, 5, 7, 9, 12],
        'alert': [0, 0, 7, 0],
        'soft': [0, 4, 7],
        'festive': [0, 2, 4, 7, 9, 12],
    }
    pattern = patterns[family]
    pattern = pattern[variant % 3:] + pattern[:variant % 3]

    # Every variant changes tempo, pitch, oscillator and articulation.
    bpm = 190 - ((index * 17 + variant * 11) % 70)
    gap = 60.0 / bpm * (0.22 + 0.08 * ((variant + index) % 4))
    duration = {
        'bell': 0.52,
        'chime': 0.34,
        'marimba': 0.22,
        'piano': 0.40,
        'pluck': 0.18,
        'synth': 0.28,
        'arpeggio': 0.16,
        'alert': 0.20,
        'soft': 0.55,
        'festive': 0.18,
    }[family] * (0.88 + 0.06 * (variant % 4))

    kinds = {
        'bell': 'bell',
        'chime': 'sine',
        'marimba': 'marimba',
        'piano': 'piano',
        'pluck': 'triangle',
        'synth': ['saw', 'soft_square', 'triangle', 'sine'][variant % 4],
        'arpeggio': ['sine', 'triangle', 'soft_square'][variant % 3],
        'alert': ['square', 'soft_square', 'saw', 'sine'][variant % 4],
        'soft': 'sine',
        'festive': ['bell', 'piano', 'marimba'][variant % 3],
    }
    kind = kinds[family]
    if isinstance(kind, list):
        kind = kind[variant % len(kind)]

    samples = []
    rng = random.Random(index * 997 + variant * 37)

    for pos, degree in enumerate(pattern):
        multiplier = scale[degree % len(scale)]
        if degree >= len(scale):
            multiplier *= 2 ** (degree // len(scale))
        freq = root * multiplier
        note_dur = duration
        # Some styles add a short octave accent for extra character.
        accents = family in ('bell', 'festive') and pos == len(pattern) - 1
        total = int(note_dur * SR)
        for k in range(total):
            t = k / SR
            env = envelope(
                t,
                note_dur,
                attack=0.004 if family in ('pluck', 'marimba', 'alert') else 0.018,
                release=0.055 if family in ('pluck', 'alert') else 0.12,
                decay=2.0 if family in ('marimba', 'pluck', 'bell') else 0.7,
            )
            value = osc(freq, t, kind)
            # Small harmonic/brightness differences between every variant.
            value += 0.08 * math.sin(2 * math.pi * freq * (2 + (variant % 3)) * t)
            if accents:
                value += 0.12 * osc(freq * 2, t, 'sine')
            if family == 'alert' and k < int(0.025 * SR):
                value += 0.05 * (rng.random() * 2 - 1) * (1 - k / (0.025 * SR))
            samples.append(value * env * 0.30)
        samples.extend([0.0] * int(gap * SR))

    pcm = b''.join(
        struct.pack('<h', int(max(-1.0, min(1.0, x)) * 32767))
        for x in samples
    )
    path = OUT / f'toque_{index:02d}.wav'
    with wave.open(str(path), 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm)


families = [
    'bell', 'chime', 'marimba', 'piano', 'pluck',
    'synth', 'arpeggio', 'alert', 'soft', 'festive'
]

for index in range(1, 51):
    family = families[(index - 1) // 5]
    variant = (index - 1) % 5
    make_sound(index, family, variant)

# Default notification sound points to the first bell preset.
(OUT / 'novo_pedido.wav').write_bytes((OUT / 'toque_01.wav').read_bytes())
print('50 toques variados gerados com sucesso: 10 estilos x 5 variações.')
