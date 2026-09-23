from pathlib import Path
import math
import wave
import struct

OUT = Path('ACAI_DA_7_PAINEL_ANDROID_FCM_PROJETO/AcaiDa7PainelApp/app/src/main/res/raw')
OUT.mkdir(parents=True, exist_ok=True)
SR = 44100

presets = []
for i in range(50):
    root = 330 * (2 ** ((i % 12) / 12))
    mode = i % 10
    if mode == 0: seq = [(1.0,.12),(1.25,.12),(1.5,.22)]
    elif mode == 1: seq = [(1.0,.16),(1.0,.16),(1.4,.22)]
    elif mode == 2: seq = [(1.0,.10),(1.35,.18),(1.0,.10),(1.6,.20)]
    elif mode == 3: seq = [(1.0,.20),(1.5,.28)]
    elif mode == 4: seq = [(1.0,.08),(1.0,.08),(1.0,.08),(1.8,.25)]
    elif mode == 5: seq = [(1.0,.18),(.8,.18),(1.2,.18)]
    elif mode == 6: seq = [(1.0,.10),(1.25,.10),(1.5,.10),(2.0,.24)]
    elif mode == 7: seq = [(1.0,.24),(1.3,.10),(1.6,.24)]
    elif mode == 8: seq = [(1.0,.12),(1.6,.16),(1.3,.12),(2.0,.26)]
    else: seq = [(1.0,.12),(1.0,.12),(1.5,.12),(1.8,.28)]
    presets.append((root, seq))

def tone(freq, duration, k, variant):
    t = k / SR
    attack = min(.015, duration*.2)
    release = min(.08, duration*.35)
    if t < attack: env = t/attack
    elif t > duration-release: env = max(0.0,(duration-t)/release)
    else: env = 1.0
    if variant % 4 == 0:
        value = math.sin(2*math.pi*freq*t) + .28*math.sin(2*math.pi*freq*2*t)
    elif variant % 4 == 1:
        value = .8*math.sin(2*math.pi*freq*t) + .35*math.sin(2*math.pi*freq*3*t)
    elif variant % 4 == 2:
        value = math.sin(2*math.pi*freq*t) * (.75 + .25*math.sin(2*math.pi*5*t))
    else:
        value = .7*math.sin(2*math.pi*freq*t) + .25*math.sin(2*math.pi*freq*4*t)
    return value * env * .34

for idx, (root, seq) in enumerate(presets, start=1):
    samples=[]
    for mult, dur in seq:
        for k in range(int(dur*SR)):
            samples.append(tone(root*mult,dur,k,idx))
        samples.extend([0.0]*int(.045*SR))
    pcm=b''.join(struct.pack('<h',int(max(-1,min(1,x))*32767)) for x in samples)
    with wave.open(str(OUT/f'toque_{idx:02d}.wav'),'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm)

(OUT/'novo_pedido.wav').write_bytes((OUT/'toque_01.wav').read_bytes())
print('50 toques gerados com sucesso.')
