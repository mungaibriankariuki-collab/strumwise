# Strumwise progress

## Done
- **Phase 0 — project setup**: Gradle Kotlin DSL + version catalog, modules `app`, `core-audio`
  (NDK/CMake + Oboe via Prefab), `core-music`, `core-game`, `core-data`; Hilt app shell;
  CI workflow builds debug + signed release APKs and runs unit tests.

- **Phase 1 — audio engine** (`core-audio`): Oboe low-latency exclusive float stereo stream at the
  native rate, reopened on disconnect; 16-voice mixer with oldest-first stealing (damped voices
  first); extended Karplus-Strong voice (fractional-delay allpass tuning, loop lowpass, per-pitch T60,
  velocity-shaped noise+triangle excitation, pick-position comb); WAV sample voice behind the same
  `Voice` interface; same-string damping; body resonance (4 bandpass modes) + soft limiter;
  sample-accurate scheduled notes for strums; metronome click track; audio clock from Oboe
  timestamps (seqlock, monotonic across stream reopen); lock-free SPSC event queue; process-lifecycle
  pause/resume. `tools/prepare_samples.py` prepares recorded samples.

## Next
- Phase 2: music theory (core-music).

## Decisions
- **Builds run on GitHub Actions.** The dev machine is a phone (Termux/proot, no Android SDK),
  so CI is the build of record. `STRUMWISE_JVM_ONLY=1 gradle :core-music:test :core-game:test`
  runs the pure-Kotlin modules locally without the SDK.
- Stack: AGP 9.4.1 (built-in Kotlin), Kotlin 2.3.21, KSP 2.3.12, Hilt 2.59.2, compileSdk/targetSdk 37,
  Gradle 9.6.1, Oboe 1.10.0. These are pinned to a combination already proven on CI.
- Compose UI tests run under **Robolectric** in `testDebugUnitTest` so CI needs no emulator;
  instrumented tests in `androidTest` are compiled in CI (`assembleDebugAndroidTest`).
- Release signing reads `strumwise.storeFile/storePassword/keyAlias/keyPassword` from
  `local.properties`; CI writes that file from the `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` secrets.
- Oboe's prefab AAR ships a shared library, so the native build uses `ANDROID_STL=c++_shared`.
- `GuitarEngine` is a Kotlin interface (`OboeGuitarEngine` is the JNI implementation) so ViewModels
  and UI tests can use a fake engine without loading native code.
- `noteOn` takes an optional `delayMs`; strum spacing is scheduled on the audio thread to the exact
  sample instead of with Kotlin timers.
- The engine also has a metronome (`setMetronome`) so the calibration screen and game count-in clicks
  are sample-accurately aligned with the audio clock.
- Background = stream closed (releases the exclusive device for other apps); the clock freezes and
  resumes from the same value.
- No recorded samples ship (none are licensed); "Samples" mode falls back to the synth until
  `assets/samples/<midi>.wav` files are added.
