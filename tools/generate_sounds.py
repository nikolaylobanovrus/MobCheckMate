"""Generate the app's sound effects (move, capture, check, game end) as small mono WAV files.

The sounds are synthesised from scratch, so they carry no third-party licence.
Usage: python3 tools/generate_sounds.py androidApp/src/main/res/raw
"""
import math
import random
import struct
import sys
import wave

RATE = 44100


def write(path, samples):
    with wave.open(path, "w") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32000)) for s in samples))


def click(duration, tone_hz, noise, decay, gain):
    """Wooden 'tock': a short low tone plus filtered noise, both decaying fast."""
    rnd = random.Random(42)
    out, lp = [], 0.0
    for i in range(int(RATE * duration)):
        t = i / RATE
        env = math.exp(-t * decay)
        lp = lp * 0.75 + rnd.uniform(-1, 1) * 0.25  # one-pole low-pass on white noise
        out.append(gain * env * (math.sin(2 * math.pi * tone_hz * t) * (1 - noise) + lp * noise * 3))
    return out


def tones(notes, note_len, gain):
    out = []
    for hz in notes:
        for i in range(int(RATE * note_len)):
            t = i / RATE
            env = min(1.0, t * 200) * math.exp(-t * 6)
            out.append(gain * env * (math.sin(2 * math.pi * hz * t) + 0.3 * math.sin(4 * math.pi * hz * t)))
    return out


def silence(seconds):
    return [0.0] * int(RATE * seconds)


if __name__ == "__main__":
    target = sys.argv[1]
    write(f"{target}/move.wav", click(0.09, 180, 0.55, 60, 0.8))
    write(f"{target}/capture.wav", click(0.05, 240, 0.7, 70, 0.9) + silence(0.025) + click(0.08, 160, 0.6, 55, 0.8))
    write(f"{target}/check.wav", tones([880, 1175], 0.12, 0.45))
    write(f"{target}/game_end.wav", tones([523, 659, 784], 0.16, 0.45))
