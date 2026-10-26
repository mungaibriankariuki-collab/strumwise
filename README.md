# Strumwise

A realistic virtual acoustic guitar for Android phones, played in landscape. Swipe across six
strings to strum, hold chords from the column beside the neck, or tap frets directly. A rhythm-game
mode scrolls note markers along the strings toward a hit line by the sound hole.

- **Package:** `com.strumwise.app` · **minSdk** 26 · **target/compile SDK** 37
- **Stack:** Kotlin, Jetpack Compose (Material 3), MVVM + StateFlow, Hilt, Room, DataStore,
  kotlinx.serialization, C++17 + [Oboe](https://github.com/google/oboe) via Prefab, CMake/NDK.

## Architecture

```
app/          Compose UI, navigation, ViewModels, Hilt wiring
 ├─ ui/guitar   Canvas guitar (GuitarSurface), StrumDetector, GuitarPlayer, string vibration
 ├─ ui/play     Free-play screen          ui/game     Game screen, note markers
 ├─ ui/songs    Song list + practice      ui/results  Results, stars, confetti
 ├─ ui/settings Settings                  ui/calibration  Latency calibration
 ├─ ui/dev      Hidden dev menu + debug overlay (fps, stream stats, round-trip latency)
 └─ monetization  TODO hooks only (ads, billing) — not implemented
core-audio/   Native engine (C++/Oboe) + JNI bridge + Kotlin GuitarEngine API
core-music/   Pure Kotlin music theory: pitches, tunings, chord library, fret maths
core-game/    Pure Kotlin game logic: chart model/parser, clock, judgment, scoring, sessions
core-data/    Room (high scores, plays) + DataStore (settings, chord slots)
tools/        generate_charts.py (song charts), prepare_samples.py (guitar samples)
```

`core-music` and `core-game` have no Android dependencies and are fully unit tested on the JVM.

### Audio engine (`core-audio`)

```
 Kotlin (any thread)                       Audio thread (Oboe callback)
 GuitarEngine.noteOn(...) ──► lock-free ──► drain events ─► schedule notes to the exact sample
 muteString / volume / ...    SPSC queue                    16 voices (oldest-first stealing)
                                                             │  KarplusStrongVoice | SampleVoice
                                                             ▼
                                            pan ─► body resonance ─► volume ─► soft limiter ─► out
                                            Oboe timestamps ─► seqlock ─► getAudioTimeNanos()
```

- **Stream:** low-latency, exclusive (falls back to shared), float, stereo, device-native rate.
  On disconnect (headphones, Bluetooth) the stream is reopened and the clock carries on.
  When the app goes to the background the stream is closed; it reopens on return.
- **Synthesis:** extended Karplus-Strong. Each voice is a delay line tuned with a first-order
  allpass for the fractional part of the period, a two-point loop lowpass, and a per-pitch decay
  gain (low strings ring longer). The excitation is a triangle "pluck shape" blended with a
  velocity-darkened noise burst, run through a pick-position comb filter. Four bandpass modes
  (air ≈98 Hz, top ≈196 Hz, ≈390 Hz, presence ≈2.8 kHz) add the body; a lowpass tames the fizz.
- **Real-guitar behaviour:** a new note on a string damps the previous one; strumming over a muted
  string of the chord damps it; strums are spaced 6–12 ms per string, scheduled sample-accurately.
- **Real-time safety:** no allocation, locks, logging or JNI calls on the audio thread; Kotlin calls
  only push to a lock-free single-consumer queue.
- **Clock:** `getAudioTimeNanos()` is derived from Oboe presentation timestamps (what is being heard
  *now*), extrapolated with `CLOCK_MONOTONIC` and kept monotonic. The game never reads the wall clock.
- **Metronome:** sample-accurate click track for the game count-in/timing and calibration.

### Game (`core-game`)

- **Judgment:** Perfect ±45 ms, Good ±90 ms, otherwise Miss — in real time, so practice speed
  doesn't widen the windows. Wrong chord = Miss. Notes that pass unplayed = Miss.
- **Score:** Perfect 100, Good 50; multiplier x2 at 10 combo, x3 at 25, x4 at 50; a miss resets it.
- **Stars:** 3 at ≥ 90 % accuracy, 2 at ≥ 70 %, otherwise 1 (accuracy = (Perfect + ½ Good) / notes).
- **Calibration:** tap along to clicks; the median offset is subtracted from every tap.
- **Practice:** 50 / 75 / 100 % speed and section loops. Practice runs never set high scores.

## Building

Requirements: JDK 17, Android SDK (platform 37), NDK and CMake 3.22.1 (Gradle installs the NDK/CMake
if missing), Gradle 9.6.1.

```bash
gradle :app:assembleDebug                      # debug APK
gradle :core-music:test :core-game:test        # pure-Kotlin unit tests
gradle :core-data:testDebugUnitTest :app:testDebugUnitTest   # Robolectric + Compose UI tests
```

On a machine without the Android SDK you can still run the pure modules:
`STRUMWISE_JVM_ONLY=1 gradle :core-music:test :core-game:test`.

**CI:** `.github/workflows/build.yml` runs every test suite, builds debug and signed release APKs
and publishes them as a GitHub release on each push to `main`.

### Release signing

Signing is read from the git-ignored `local.properties` — keys are never committed:

```properties
strumwise.storeFile=strumwise-release.jks
strumwise.storePassword=…
strumwise.keyAlias=strumwise
strumwise.keyPassword=…
```

CI writes this file from the `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` repository secrets. Without
it, the release build falls back to the debug key. Release builds use R8 (minify + resource
shrinking); the JNI keep rules live in `core-audio/consumer-rules.pro`.

## Adding songs

Charts live in `app/src/main/assets/songs/<id>.json`; every file there is loaded automatically.

```json
{
  "id": "my_song", "title": "My Song", "artist": "Traditional",
  "bpm": 90, "beatsPerBar": 4, "offsetMs": 2000, "difficulty": "easy",
  "events": [
    {"timeMs": 0,    "type": "strum", "chord": "G", "direction": "down"},
    {"timeMs": 667,  "type": "strum", "chord": "G", "direction": "up"},
    {"timeMs": 1333, "type": "pluck", "string": 0, "chord": "G"}
  ]
}
```

- `timeMs` is relative to `offsetMs`; events must be in time order.
- `strum` needs a `chord` (an id from `ChordLibrary`, e.g. `Am`, `F#m`, `Fbarre`); `direction` is a hint.
- `pluck` needs `string` (0 = low E … 5 = high E); with `chord`, that shape must be held and must
  not mute the string.
- `difficulty`: `easy` | `medium` | `hard`.

The easiest route is to describe the song as bars of chords plus a pattern in
`tools/generate_charts.py` and run `python3 tools/generate_charts.py`. `ChartParserTest` validates
every bundled chart, so a broken chart fails the build. **Only use public-domain melodies or
original progressions.**

## Adding real guitar samples

The engine has a sample-playback voice behind the same interface as the synth. No recordings ship
with the app (none are licensed), so **Settings → Sound type → Samples** falls back to the synth
until you add some:

1. Record single notes of an acoustic guitar (dry, close-miked, let each ring out), ideally every
   open string and a few frets on each — the engine pitch-shifts the nearest sample to each fret,
   so more roots = more natural. Name the files after their note: `E2.wav`, `A2.wav`, `C#3.wav`, …
2. Run `python3 tools/prepare_samples.py recordings/ app/src/main/assets/samples/`. It trims
   leading silence, normalises to −1 dBFS, fades out the tail, converts to 16-bit mono and names
   each file by MIDI number (`E2.wav` → `40.wav`).
3. Rebuild. At startup every `assets/samples/<midi>.wav` is loaded; the debug overlay/Logcat
   (`OboeGuitarEngine`) reports how many were accepted.

## Developer menu

Tap the **Strumwise** title on the home screen seven times. The debug overlay shows fps and janky
frames, sample rate, Oboe's output latency and underrun count. In debug builds it can also measure
the **round-trip latency** (speaker → microphone) with test pulses; results are logged under the
`StrumwiseLatency` tag. Only debug builds request `RECORD_AUDIO`.

## Monetization hooks

`app/.../monetization/Monetization.kt` holds clearly marked `TODO(ads)` / `TODO(billing)` stubs for
an AdMob banner (song list) and interstitial (after results), and Play Billing "remove ads" /
"unlock songs". Nothing is implemented; the app is ad-free with every song unlocked.

## Known limitations

- The Karplus-Strong synth is convincing for strums and single notes but is not a sampled guitar;
  no recorded samples are bundled.
- Only frets 0–7 are drawn/playable in free-fret mode; chord shapes use frets up to 4.
- No backing track: the metronome is the timing reference in game mode.
- Strum direction in charts is shown but not judged.
- The round-trip latency test needs the phone speaker and a quiet room; headphones block it.
- Audio latency varies widely between devices; use calibration with your usual headphones/speaker.
- Compose UI tests run under Robolectric; the instrumented test source set is compiled but not run
  on CI (no emulator).
