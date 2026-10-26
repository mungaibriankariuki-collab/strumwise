# Strumwise progress

## Done
- **Phase 0 — project setup**: Gradle Kotlin DSL + version catalog, modules `app`, `core-audio`
  (NDK/CMake + Oboe via Prefab), `core-music`, `core-game`, `core-data`; Hilt app shell;
  CI workflow builds debug + signed release APKs and runs unit tests.

## Next
- Phase 1: audio engine.

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
