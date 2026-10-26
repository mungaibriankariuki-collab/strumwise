#!/usr/bin/env python3
"""
Generates the built-in song charts in app/src/main/assets/songs/.

Every song here is either a public-domain traditional melody's chord progression or an original
practice progression. Charts are written as data (bars of chords + a strum/pick pattern) and
expanded into the timed event list the game reads, so they are easy to edit and always consistent.

    python3 tools/generate_charts.py

Chart format (see README "Adding songs"):
    { id, title, artist, bpm, beatsPerBar, offsetMs, difficulty,
      events: [ {timeMs, type: "strum"|"pluck", chord?, string?, direction?} ] }
timeMs is relative to offsetMs; the game adds them.
"""
import json
import os

OUT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "songs"))

# Patterns: list of (beat offset within bar, kind, arg). kind "D"/"U" = strum down/up, "P" = pluck string arg.
PATTERNS = {
    "whole4": [(0, "D")],
    "quarters4": [(0, "D"), (1, "D"), (2, "D"), (3, "D")],
    "half4": [(0, "D"), (2, "D")],
    "folk4": [(0, "D"), (1, "D"), (1.5, "U"), (2.5, "U"), (3, "D")],
    "waltz3": [(0, "D"), (1, "D"), (2, "D")],
    "waltzBass3": [(0, ("P", "bass")), (1, "D"), (2, "D")],
    "boomChick4": [(0, ("P", "bass")), (1, "D"), (2, ("P", "alt")), (3, "D")],
    "arp6": [(0, ("P", "bass")), (1, ("P", 2)), (2, ("P", 3)), (3, ("P", 4)), (4, ("P", 3)), (5, ("P", 2))],
}

# Lowest sounding string of each shape, and the next string up (for alternating bass).
BASS = {"C": 1, "D": 2, "E": 0, "F": 2, "G": 0, "A": 1, "Am": 1, "Dm": 2, "Em": 0, "A7": 1, "D7": 2,
        "E7": 0, "G7": 0, "C7": 1, "B7": 1, "Fmaj7": 2}


def expand(bars, pattern, bpm, beats_per_bar):
    beat_ms = 60000.0 / bpm
    events = []
    for bar_index, chord in enumerate(bars):
        bar_start = bar_index * beats_per_bar
        for beat, kind in PATTERNS[pattern]:
            t = round((bar_start + beat) * beat_ms)
            if kind in ("D", "U"):
                events.append({"timeMs": t, "type": "strum", "chord": chord,
                               "direction": "down" if kind == "D" else "up"})
            else:
                target = kind[1]
                if target == "bass":
                    string = BASS[chord]
                elif target == "alt":
                    string = min(BASS[chord] + 1, 3)
                else:
                    string = target
                events.append({"timeMs": t, "type": "pluck", "chord": chord, "string": string})
    return events


def song(id, title, artist, bpm, beats_per_bar, difficulty, sections, offset_ms=2000):
    """sections: list of (bars, pattern)."""
    events = []
    bar_offset = 0
    for bars, pattern in sections:
        section_events = expand(bars, pattern, bpm, beats_per_bar)
        shift = round(bar_offset * beats_per_bar * 60000.0 / bpm)
        for e in section_events:
            e["timeMs"] += shift
        events += section_events
        bar_offset += len(bars)
    return {"id": id, "title": title, "artist": artist, "bpm": bpm, "beatsPerBar": beats_per_bar,
            "offsetMs": offset_ms, "difficulty": difficulty, "events": events}


SONGS = [
    song("practice_g_c_d", "First Strums: G–C–D", "Strumwise practice", 72, 4, "easy", [
        (["G", "G", "C", "C", "D", "D", "G", "G"] * 2, "half4"),
    ]),
    song("practice_am_f_c_g", "Four-Chord Loop: Am–F–C–G", "Strumwise practice", 80, 4, "easy", [
        (["Am", "F", "C", "G"] * 4, "quarters4"),
    ]),
    song("practice_em_c_g_d", "Campfire Groove: Em–C–G–D", "Strumwise practice", 88, 4, "medium", [
        (["Em", "C", "G", "D"] * 2, "quarters4"),
        (["Em", "C", "G", "D"] * 2, "folk4"),
    ]),
    song("amazing_grace", "Amazing Grace", "Traditional", 90, 3, "easy", [
        (["G", "G", "C", "G", "G", "G", "D", "D",
          "G", "G7", "C", "G", "G", "D", "G", "G"], "waltz3"),
    ]),
    song("when_the_saints", "When the Saints Go Marching In", "Traditional", 96, 4, "easy", [
        (["G", "G", "G", "G", "G", "G", "D", "D",
          "G", "G7", "C", "C", "G", "D", "G", "G"], "half4"),
    ]),
    song("greensleeves", "Greensleeves", "Traditional", 96, 3, "medium", [
        (["Am", "G", "F", "E", "Am", "G", "E", "E"], "waltz3"),
        (["C", "G", "Am", "E", "C", "G", "Am", "Am"], "waltzBass3"),
    ]),
    song("oh_susanna", "Oh! Susanna", "Stephen Foster (1848)", 100, 4, "medium", [
        (["C", "C", "C", "G7", "C", "C", "G7", "C",
          "F", "F", "C", "G7", "C", "C", "G7", "C"], "boomChick4"),
    ]),
    song("auld_lang_syne", "Auld Lang Syne", "Traditional", 84, 4, "medium", [
        (["G", "D", "G", "C", "G", "D", "Em", "C", "G", "D", "G"], "folk4"),
    ]),
    song("house_of_the_rising_sun", "House of the Rising Sun", "Traditional", 180, 6, "hard", [
        (["Am", "C", "D", "F", "Am", "C", "E", "E",
          "Am", "C", "D", "F", "Am", "E", "Am", "E"], "arp6"),
    ]),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    for s in SONGS:
        path = os.path.join(OUT, f"{s['id']}.json")
        with open(path, "w") as f:
            json.dump(s, f, indent=1)
            f.write("\n")
        print(f"{path}: {len(s['events'])} events")


if __name__ == "__main__":
    main()
