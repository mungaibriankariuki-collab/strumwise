#!/usr/bin/env python3
"""
Prepare recorded guitar notes for Strumwise's sample-playback voice.

Usage:
    python3 tools/prepare_samples.py recordings/ app/src/main/assets/samples/

Input: WAV files (8/16/24/32-bit PCM, mono or stereo, any sample rate) named after the note they
contain, e.g. E2.wav, A2.wav, D3.wav, G3.wav, B3.wav, E4.wav (sharps as C#3 or Cs3).

Output: one 16-bit mono WAV per note named by MIDI number (E2 -> 40.wav), with leading silence
trimmed, peak-normalised to -1 dBFS and a short fade-out. The engine loads every
assets/samples/<midi>.wav at startup and pitch-shifts the nearest one to each fret.

Only the Python standard library is needed.
"""
import array
import os
import re
import sys
import wave

NOTE_INDEX = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
NAME = re.compile(r"^([A-Ga-g])(#|s|b)?(-?\d)$")


def note_to_midi(stem):
    m = NAME.match(stem)
    if not m:
        return None
    letter, accidental, octave = m.groups()
    semitone = NOTE_INDEX[letter.upper()] + {"#": 1, "s": 1, "b": -1}.get(accidental, 0)
    return 12 * (int(octave) + 1) + semitone


def read_mono(path):
    with wave.open(path, "rb") as w:
        channels, width, rate, frames = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        raw = w.readframes(frames)
    scale = float(1 << (8 * width - 1))
    samples = []
    for i in range(0, len(raw), width):
        chunk = raw[i:i + width]
        if width == 1:
            value = (chunk[0] - 128) / 128.0
        else:
            value = int.from_bytes(chunk, "little", signed=True) / scale
        samples.append(value)
    mono = [sum(samples[i:i + channels]) / channels for i in range(0, len(samples), channels)]
    return mono, rate


def process(mono, rate):
    threshold = 0.02 * max(abs(s) for s in mono)
    start = next(i for i, s in enumerate(mono) if abs(s) > threshold)
    start = max(0, start - int(0.002 * rate))  # keep 2 ms of pre-attack
    mono = mono[start:]
    peak = max(abs(s) for s in mono) or 1.0
    gain = 0.891 / peak  # -1 dBFS
    fade = min(len(mono), int(0.05 * rate))
    out = array.array("h")
    for i, s in enumerate(mono):
        tail = len(mono) - i
        env = tail / fade if tail < fade else 1.0
        out.append(int(max(-1.0, min(1.0, s * gain * env)) * 32767))
    return out


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    src, dst = sys.argv[1], sys.argv[2]
    os.makedirs(dst, exist_ok=True)
    for name in sorted(os.listdir(src)):
        stem, ext = os.path.splitext(name)
        midi = note_to_midi(stem)
        if ext.lower() != ".wav" or midi is None:
            print(f"skip {name} (name it like E2.wav)")
            continue
        mono, rate = read_mono(os.path.join(src, name))
        pcm = process(mono, rate)
        target = os.path.join(dst, f"{midi}.wav")
        with wave.open(target, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(rate)
            w.writeframes(pcm.tobytes())
        print(f"{name} -> {target} ({len(pcm) / rate:.2f}s)")


if __name__ == "__main__":
    main()
