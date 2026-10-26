package com.strumwise.audio

/** Raw JNI entry points; see core-audio/src/main/cpp/jni_bridge.cpp. Use [OboeGuitarEngine] instead. */
internal object NativeBridge {
    init {
        System.loadLibrary("strumwise_audio")
    }

    external fun oboeVersion(): String
    external fun create(): Long
    external fun destroy(handle: Long)
    external fun start(handle: Long): Boolean
    external fun stop(handle: Long)
    external fun noteOn(handle: Long, string: Int, fret: Int, velocity: Float, delayMs: Float)
    external fun muteString(handle: Long, string: Int)
    external fun muteAll(handle: Long)
    external fun setMasterVolume(handle: Long, volume: Float)
    external fun setTuning(handle: Long, hz: FloatArray)
    external fun setSoundType(handle: Long, type: Int)
    external fun setMetronome(handle: Long, bpm: Float, firstClickNanos: Long)
    external fun audioTimeNanos(handle: Long, monotonicNowNanos: Long): Long
    external fun sampleRate(handle: Long): Int
    external fun outputLatencyMillis(handle: Long): Double
    external fun underrunCount(handle: Long): Int
    external fun loadSamples(handle: Long, wavFiles: Array<ByteArray>, rootHz: FloatArray): Int
}
