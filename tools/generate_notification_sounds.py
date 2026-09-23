from pathlib import Path
import math
import random
import struct
import wave

OUT = Path('ACAI_DA_7_PAINEL_ANDROID_FCM_PROJETO/AcaiDa7PainelApp/app/src/main/res/raw')
OUT.mkdir(parents=True, exist_ok=True)
SR = 44100

# 50 genuinely different original notification compositions.
# Each preset has its own melody, rhythm, timbre and envelope instead of being
# the same ringtone with a different pitch.
ROOTS = [220.00, 246.94, 261.63, 277.18, 293.66, 329.63, 349.23, 392.00,
         415.30, 440.00, 493.88, 523.25, 587.33, 659.25]
SCALES = {
    'major': [1, 9/8, 5/4, 4/3, 3/2, 5/3, 15/8, 2],
    'pent': [1, 9/8, 5/4, 3/2, 5/3, 2],
    'minor': [1, 9/8, 6/5, 4/3, 3/2, 8/5, 9/5, 2],
    'blues': [1, 6/5, 4/3, 7/5, 3/2, 9/5, 2],
    'dorian': [1, 9/8, 6/5, 4/3, 3/2, 5/3, 9/5, 2],
}

# name, oscillator, melody degrees, note lengths, gap, scale, octave, attack,
# release, decay, harmonic mix, tempo multiplier, echo amount, bass amount
PRESETS = [
('01 Entrega Sino', 'bell',       [0,2,4,7],       [0.32,0.30,0.55,0.72], .07,'major',1,.008,.30,2.2,.42,1.00,.00,.00),
('02 Campainha Dupla', 'bell',    [4,2,0,4],       [0.20,0.20,0.34,0.65], .06,'major',1,.006,.25,2.8,.55,1.15,.00,.00),
('03 Chime Cristal', 'chime',     [0,4,7,12],      [0.22,0.22,0.30,0.75], .04,'major',2,.004,.34,3.2,.65,.92,.16,.00),
('04 Marimba Alegre', 'marimba',  [0,2,4,2,7],     [0.18]*5,              .025,'major',1,.003,.12,4.5,.30,1.18,.00,.08),
('05 Piano Pop', 'piano',         [0,4,2,5,7],     [0.20,0.22,0.20,0.25,0.60],.035,'major',1,.006,.22,1.2,.24,1.00,.10,.10),
('06 Piano Acorde', 'piano',      [0,4,7,12],      [0.30,0.28,0.34,0.85], .08,'major',1,.010,.32,.8,.30,.86,.18,.22),
('07 Pluck Rapido', 'pluck',      [0,2,4,7,4,2],   [0.11]*6,              .025,'pent',2,.002,.07,6.0,.18,1.32,.00,.00),
('08 Pluck Groove', 'pluck',      [0,4,7,4,9,7],   [0.14,0.11,0.16,0.11,0.18,0.30],.03,'minor',1,.002,.08,5.0,.25,1.08,.00,.18),
('09 Synth Neon', 'saw',          [0,4,7,12],      [0.16,0.16,0.18,0.45], .035,'minor',2,.006,.14,1.6,.18,1.05,.10,.18),
('10 Synth Arcade', 'square',     [0,7,12,7,4],    [0.12,0.12,0.16,0.12,0.42],.025,'major',2,.002,.09,2.8,.08,1.25,.00,.12),
('11 Arpejo Relampago', 'triangle',[0,2,4,5,7,9,12],[0.10]*7,             .018,'major',2,.002,.08,4.0,.16,1.42,.00,.00),
('12 Arpejo Futuro', 'digital',   [0,4,7,9,12,9,7],[0.09,0.09,0.09,0.12,0.18,0.09,0.35],.02,'minor',2,.001,.06,5.5,.30,1.50,.12,.00),
('13 Alerta Curto', 'square',     [0,0,7,0],       [0.13,0.13,0.20,0.40], .08,'major',1,.001,.06,3.5,.04,.92,.00,.00),
('14 Alerta Triplo', 'square',    [7,7,0,7],       [0.10,0.10,0.10,0.42], .045,'blues',2,.001,.05,4.0,.10,1.20,.00,.00),
('15 Radar', 'sine',              [0,7,0,12],       [0.18,0.18,0.18,0.65], .07,'minor',2,.010,.18,1.4,.10,.82,.20,.00),
('16 Whistle', 'whistle',        [7,9,12,9],       [0.20,0.20,0.28,0.65], .05,'major',2,.008,.22,.5,.06,1.00,.18,.00),
('17 Assobio Descendente', 'whistle',[12,9,7,4,0],[0.15,0.14,0.16,0.18,0.55],.03,'pent',2,.006,.18,.8,.08,1.05,.08,.00),
('18 Vibrafone', 'vibra',         [0,4,7,9,12],    [0.24,0.24,0.26,0.25,0.70],.05,'major',1,.006,.35,1.5,.45,.90,.20,.00),
('19 Kalimba', 'kalimba',         [0,2,5,7,9],     [0.18,0.16,0.18,0.16,0.55],.035,'pent',2,.003,.16,3.8,.38,1.04,.05,.00),
('20 Caixa Musical', 'musicbox',  [0,4,2,7,4,9],   [0.14,0.14,0.14,0.16,0.16,0.55],.025,'major',2,.002,.20,5.0,.52,1.00,.16,.00),
('21 Glockenspiel', 'glock',      [7,4,2,0,4],     [0.17,0.17,0.20,0.22,0.65],.035,'major',2,.003,.30,4.0,.70,1.00,.20,.00),
('22 Xilofone', 'xylophone',      [0,2,4,7,4,2],   [0.13,0.13,0.13,0.16,0.13,0.45],.025,'major',2,.002,.10,5.0,.24,1.18,.00,.06),
('23 Sino Grave', 'gong',         [0,7,0],         [0.35,0.32,0.90],      .10,'minor',1,.004,.65,.6,.90,.72,.24,.20),
('24 Bronze', 'gong',             [0,4,7],          [0.30,0.25,1.05],      .08,'major',1,.005,.75,.5,.80,.68,.20,.28),
('25 Organ', 'organ',             [0,4,7,12],      [0.24,0.22,0.25,0.75], .06,'major',1,.018,.25,.4,.58,.92,.08,.22),
('26 Brass Fanfare', 'brass',      [0,4,7,12],      [0.18,0.16,0.20,0.62], .045,'major',1,.025,.20,.7,.45,.98,.05,.28),
('27 Trompete Pop', 'brass',      [7,7,9,12],      [0.13,0.13,0.18,0.58], .035,'major',2,.018,.16,.8,.35,1.10,.05,.12),
('28 Baixo Chicote', 'bass',      [0,0,7,12],      [0.16,0.16,0.22,0.55], .045,'minor',1,.004,.10,2.0,.18,1.06,.00,.65),
('29 Bass Bounce', 'bass',        [0,7,4,7,12],    [0.13,0.13,0.13,0.16,0.45],.025,'blues',1,.003,.08,2.5,.20,1.18,.00,.72),
('30 Drum Fill', 'drum',          [0,0,7,4,0],      [0.08,0.08,0.08,0.08,0.40],.025,'minor',1,.001,.05,8.0,.05,1.15,.00,.35),
('31 Kick Digital', 'kick',       [0,0,0,12],      [0.11,0.11,0.11,0.40], .04,'minor',1,.001,.07,6.0,.03,1.02,.00,.85),
('32 Clave', 'clave',             [0,4,0,7,0],      [0.09,0.09,0.09,0.09,0.35],.05,'pent',1,.001,.04,9.0,.02,1.00,.00,.10),
('33 Futurista Laser', 'laser',   [12,7,14,4],     [0.16,0.12,0.18,0.55], .06,'minor',3,.001,.15,2.0,.20,1.12,.30,.00),
('34 Game Coin', 'coin',          [12,16,19,24],    [0.08,0.08,0.10,0.45], .02,'major',2,.001,.08,7.0,.32,1.35,.05,.00),
('35 Arcade Victory', 'arcade',   [0,4,7,12,16],   [0.12,0.12,0.12,0.14,0.60],.025,'major',2,.002,.10,4.5,.28,1.30,.08,.00),
('36 Retro 8bit', 'chip',         [0,7,12,7,4,2],  [0.10]*6,              .02,'major',2,.001,.06,6.0,.06,1.45,.00,.00),
('37 SciFi Door', 'scifi',        [0,1,6,12],      [0.24,0.18,0.24,0.75], .08,'dorian',1,.012,.32,.9,.38,.82,.28,.00),
('38 Portal', 'scifi',            [12,9,6,4,0],     [0.18,0.16,0.15,0.18,0.80],.045,'minor',2,.008,.28,1.2,.55,.80,.35,.00),
('39 Soft Cloud', 'soft',         [0,4,7],          [0.34,0.32,0.85],      .12,'major',1,.045,.42,.25,.20,.72,.32,.00),
('40 Soft Piano', 'softpiano',    [0,2,4,7],       [0.28,0.26,0.30,0.80], .08,'major',1,.025,.40,.45,.24,.80,.20,.00),
('41 Happy Ukulele', 'ukulele',   [0,4,7,4,9],     [0.14,0.13,0.15,0.13,0.55],.025,'major',1,.003,.12,2.8,.30,1.08,.05,.15),
('42 Guitar Pluck', 'guitar',     [0,2,4,7,9],     [0.16,0.14,0.16,0.14,0.60],.035,'major',1,.004,.18,2.4,.28,1.02,.08,.12),
('43 Buzzer Elegante', 'buzzer',  [0,7,0,12],      [0.12,0.12,0.18,0.55], .06,'blues',1,.001,.08,3.0,.20,.94,.00,.25),
('44 Bell Corporativo', 'bell2',  [0,5,7,12],      [0.24,0.20,0.25,0.80], .07,'major',1,.010,.35,2.0,.35,.90,.18,.00),
('45 Delivery Dois Tons', 'delivery',[0,7,12,7],   [0.16,0.16,0.20,0.70], .055,'major',2,.004,.18,2.2,.35,1.06,.10,.15),
('46 Delivery Chegando', 'delivery',[4,7,12,9,7],[0.11,0.11,0.13,0.11,0.55],.025,'major',2,.003,.12,3.0,.42,1.20,.08,.12),
('47 Delivery Urgente', 'urgent',  [0,0,7,12,0],   [0.09,0.09,0.12,0.16,0.45],.035,'minor',2,.001,.06,4.5,.15,1.28,.00,.28),
('48 Festa Açaí', 'festive',     [0,2,4,7,9,12],  [0.11,0.11,0.11,0.13,0.13,0.55],.025,'major',2,.003,.13,3.5,.52,1.22,.12,.05),
('49 Pedido VIP', 'vip',           [7,4,0,4,7,12],  [0.15,0.15,0.20,0.15,0.20,0.75],.045,'major',1,.006,.30,1.8,.65,.88,.22,.10),
('50 Açaí da 7 Original', 'signature',[0,4,7,9,12,9,7,4],[0.13,0.13,0.16,0.13,0.18,0.13,0.16,0.75],.035,'major',2,.004,.22,2.8,.48,1.16,.16,.12),
]


def env(t, duration, attack, release, decay):
    attack = min(attack, max(0.001, duration * .30))
    release = min(release, max(.006, duration * .55))
    if t < attack:
        return t / attack
    level = math.exp(-decay * max(0.0, t - attack))
    if t > duration - release:
        level *= max(0.0, (duration - t) / release)
    return level


def osc(freq, t, kind):
    phase = 2 * math.pi * freq * t
    x = (freq * t) % 1.0
    if kind in ('sine', 'soft'):
        return math.sin(phase)
    if kind in ('triangle',):
        return 2 * abs(2 * x - 1) - 1
    if kind in ('square', 'chip'):
        return 1.0 if math.sin(phase) >= 0 else -1.0
    if kind in ('saw',):
        return 2 * x - 1
    if kind in ('bell', 'bell2'):
        return .82*math.sin(phase) + .42*math.sin(phase*2.01) + .22*math.sin(phase*3.97) + .10*math.sin(phase*6.91)
    if kind == 'chime':
        return .70*math.sin(phase) + .38*math.sin(phase*2.73) + .20*math.sin(phase*5.41) + .08*math.sin(phase*8.17)
    if kind == 'marimba':
        return .88*math.sin(phase) + .27*math.sin(phase*4.02) + .10*math.sin(phase*10.01)
    if kind == 'piano':
        return .70*math.sin(phase) + .22*math.sin(phase*2) + .12*math.sin(phase*3) + .06*math.sin(phase*5)
    if kind in ('pluck','guitar','ukulele'):
        return .72*math.sin(phase) + .18*math.sin(phase*2.01) + .08*math.sin(phase*3.99)
    if kind in ('vibra','glock','xylophone','kalimba','musicbox'):
        return .72*math.sin(phase) + .32*math.sin(phase*3.01) + .18*math.sin(phase*5.97) + .08*math.sin(phase*8.98)
    if kind == 'organ':
        return .50*math.sin(phase) + .25*math.sin(phase*2) + .16*math.sin(phase*3) + .10*math.sin(phase*4)
    if kind == 'brass':
        return .55*math.sin(phase) + .28*math.sin(phase*2) + .18*math.sin(phase*3) + .12*math.sin(phase*4)
    if kind == 'bass':
        return .82*math.sin(phase) + .18*math.sin(phase*2)
    if kind in ('drum','kick','clave'):
        return math.sin(phase) + .20*math.sin(phase*3.7)
    if kind == 'laser':
        return math.sin(2*math.pi*(freq*(1+3*t))*t)
    if kind == 'coin':
        return .65*math.sin(phase) + .25*math.sin(phase*2.6) + .10*math.sin(phase*5.2)
    if kind == 'arcade':
        return .70*math.sin(phase) + .20*math.sin(phase*2) + .12*math.sin(phase*4)
    if kind == 'scifi':
        return .55*math.sin(phase) + .30*math.sin(phase*1.5) + .15*math.sin(phase*3.5)
    if kind == 'softpiano':
        return .78*math.sin(phase) + .15*math.sin(phase*2) + .07*math.sin(phase*4)
    if kind == 'buzzer':
        return math.tanh(2.2*math.sin(phase))
    if kind == 'delivery':
        return .62*math.sin(phase) + .28*math.sin(phase*2) + .10*math.sin(phase*4)
    if kind == 'urgent':
        return .75*math.sin(phase) + .25*math.sin(phase*2.03)
    if kind == 'festive':
        return .58*math.sin(phase) + .28*math.sin(phase*2.01) + .16*math.sin(phase*4.02)
    if kind == 'vip':
        return .60*math.sin(phase) + .24*math.sin(phase*2) + .12*math.sin(phase*3) + .06*math.sin(phase*6)
    if kind == 'signature':
        return .64*math.sin(phase) + .25*math.sin(phase*2.01) + .11*math.sin(phase*3.99)
    if kind == 'whistle':
        return math.sin(phase) * (0.7 + 0.3*math.sin(2*math.pi*4*t))
    if kind == 'digital':
        return .60*math.sin(phase) + .25*math.sin(phase*3.01) + .15*math.sin(phase*7.03)
    return math.sin(phase)


def make_preset(index, preset):
    name, kind, pattern, lengths, gap, scale_name, octave, attack, release, decay, harmonics, tempo, echo, bass = preset
    root = ROOTS[(index * 3 + index // 7) % len(ROOTS)]
    scale = SCALES[scale_name]
    rng = random.Random(index * 991)
    samples = []

    for pos, degree in enumerate(pattern):
        multiplier = scale[degree % len(scale)]
        if degree >= len(scale):
            multiplier *= 2 ** (degree // len(scale))
        freq = root * multiplier * octave
        duration = lengths[pos % len(lengths)] / max(.75, tempo)
        total = int(duration * SR)
        for k in range(total):
            t = k / SR
            e = env(t, duration, attack, release, decay)
            value = osc(freq, t, kind)
            value += harmonics * math.sin(2*math.pi*freq*(2 + ((index + pos) % 4))*t)
            if bass:
                value += bass * osc(root * .5, t, 'sine')
            # Tiny per-preset air/noise only for selected electronic alerts.
            if kind in ('laser','urgent','buzzer','digital') and k < int(.018*SR):
                value += .035*(rng.random()*2-1)*(1-k/(.018*SR))
            samples.append(value * e * .27)
        samples.extend([0.0] * int(gap * SR))

    # Very short echo/repeat makes the presets feel like notification sounds,
    # but the amount differs per preset and never reuses the same composition.
    if echo > 0:
        delay = int((0.055 + (index % 4)*.018) * SR)
        wet = samples[:]
        for i in range(delay, len(wet)):
            wet[i] += samples[i-delay] * echo * .22
        samples = wet

    # Normalize each file independently so quiet presets remain audible.
    peak = max(1e-9, max(abs(x) for x in samples))
    gain = min(1.0, .88 / peak)
    pcm = b''.join(struct.pack('<h', int(max(-1, min(1, x*gain))*32767)) for x in samples)
    path = OUT / f'toque_{index:02d}.wav'
    with wave.open(str(path), 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm)
    print(f'{index:02d}: {name}')


for index, preset in enumerate(PRESETS, start=1):
    make_preset(index, preset)

# Backward-compatible default resource.
(OUT / 'novo_pedido.wav').write_bytes((OUT / 'toque_01.wav').read_bytes())
print(f'{len(PRESETS)} toques originais gerados.')
