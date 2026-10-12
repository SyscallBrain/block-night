#!/usr/bin/env python3
"""Gera os sons curtos do jogo (DESIGN §7). Originais, sintetizados aqui; sem material de terceiros.

Uso: python3 tools/generate_sounds.py  (escreve app/src/main/res/raw/*.wav)
"""
import math
import pathlib
import random
import struct
import wave

RATE = 22050
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/raw"


def tone(freq_start, freq_end, seconds, volume=0.5, wave_kind="sine", noise=0.0, seed=1):
    rng = random.Random(seed)
    n = int(RATE * seconds)
    samples = []
    phase = 0.0
    for i in range(n):
        t = i / max(1, n - 1)
        freq = freq_start + (freq_end - freq_start) * t
        phase += 2 * math.pi * freq / RATE
        if wave_kind == "triangle":
            osc = 2 / math.pi * math.asin(math.sin(phase))
        else:
            osc = math.sin(phase)
        env = min(1.0, i / (0.004 * RATE)) * (1 - t) ** 2
        value = osc * env * volume + noise * (rng.random() * 2 - 1) * env * volume
        samples.append(max(-1.0, min(1.0, value)))
    return samples


def chain(*parts, gap=0.0):
    out = []
    silence = [0.0] * int(RATE * gap)
    for index, part in enumerate(parts):
        if index:
            out.extend(silence)
        out.extend(part)
    return out


def write(name, samples):
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{name}.wav"
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(b"".join(struct.pack("<h", int(s * 32767)) for s in samples))
    print(path.relative_to(OUT.parent.parent), len(samples) / RATE, "s")


SOUNDS = {
    "sfx_move": lambda: tone(1800, 1600, 0.03, volume=0.12, wave_kind="triangle"),
    "sfx_rotate": lambda: tone(900, 1300, 0.05, volume=0.25, wave_kind="triangle"),
    "sfx_lock": lambda: tone(220, 120, 0.09, volume=0.45, noise=0.15, seed=3),
    "sfx_drop": lambda: tone(300, 90, 0.12, volume=0.5, noise=0.2, seed=4),
    "sfx_hold": lambda: tone(600, 900, 0.08, volume=0.3, wave_kind="triangle"),
    "sfx_clear1": lambda: tone(660, 880, 0.12, volume=0.35, wave_kind="triangle"),
    "sfx_clear2": lambda: chain(tone(660, 880, 0.08, 0.35, "triangle"), tone(880, 1100, 0.1, 0.35, "triangle")),
    "sfx_clear3": lambda: chain(tone(660, 880, 0.07, 0.35, "triangle"), tone(880, 1100, 0.07, 0.35, "triangle"), tone(1100, 1320, 0.1, 0.35, "triangle")),
    "sfx_quad": lambda: chain(tone(660, 880, 0.07, 0.4, "triangle"), tone(880, 1100, 0.07, 0.4, "triangle"), tone(1100, 1320, 0.07, 0.4, "triangle"), tone(1320, 1760, 0.18, 0.45, "triangle")),
    "sfx_spin": lambda: chain(tone(440, 660, 0.08, 0.35, "triangle"), tone(660, 990, 0.12, 0.35, "triangle")),
    "sfx_combo": lambda: tone(500, 1000, 0.14, volume=0.3, wave_kind="triangle"),
    "sfx_win": lambda: chain(tone(523, 523, 0.12, 0.4, "triangle"), tone(659, 659, 0.12, 0.4, "triangle"), tone(784, 784, 0.12, 0.4, "triangle"), tone(1047, 1047, 0.3, 0.45, "triangle"), gap=0.0),
    "sfx_lose": lambda: tone(330, 165, 0.5, volume=0.3, wave_kind="triangle"),
}

if __name__ == "__main__":
    for name, make in SOUNDS.items():
        write(name, make())
