package com.strumwise.audio

import android.content.res.AssetManager
import android.util.Log
import kotlin.math.pow

/**
 * [GuitarEngine] backed by the native Oboe engine. Create one per process (the app provides it as a
 * Hilt singleton); the native engine lives as long as this object.
 */
class OboeGuitarEngine : GuitarEngine {
    private val handle: Long = NativeBridge.create()

    @Volatile private var started = false
    @Volatile private var pausedInBackground = false

    override val isRunning: Boolean get() = started && !pausedInBackground

    @Volatile override var loadedSampleCount: Int = 0
        private set

    @Synchronized
    override fun start(): Boolean {
        started = true
        if (pausedInBackground) return true  // resumeFromForeground() will open the stream
        return NativeBridge.start(handle)
    }

    @Synchronized
    override fun stop() {
        started = false
        NativeBridge.stop(handle)
    }

    @Synchronized
    override fun pauseForBackground() {
        pausedInBackground = true
        if (started) NativeBridge.stop(handle)
    }

    @Synchronized
    override fun resumeFromForeground() {
        pausedInBackground = false
        if (started) NativeBridge.start(handle)
    }

    override fun noteOn(string: Int, fret: Int, velocity: Float, delayMs: Float) =
        NativeBridge.noteOn(handle, string, fret, velocity.coerceIn(0f, 1f), delayMs)

    override fun muteString(string: Int) = NativeBridge.muteString(handle, string)

    override fun muteAll() = NativeBridge.muteAll(handle)

    override fun setMasterVolume(volume: Float) = NativeBridge.setMasterVolume(handle, volume.coerceIn(0f, 1f))

    override fun setTuning(openStringHz: FloatArray) {
        require(openStringHz.size == 6) { "Tuning needs 6 strings, got ${openStringHz.size}" }
        NativeBridge.setTuning(handle, openStringHz)
    }

    override fun setSoundType(type: SoundType) = NativeBridge.setSoundType(handle, type.nativeValue)

    override fun setMetronome(bpm: Float, firstClickNanos: Long, beatsPerBar: Int) =
        NativeBridge.setMetronome(handle, bpm, firstClickNanos, beatsPerBar)

    override fun getAudioTimeNanos(): Long = NativeBridge.audioTimeNanos(handle, System.nanoTime())

    override fun stats() = EngineStats(
        running = isRunning,
        sampleRate = NativeBridge.sampleRate(handle),
        outputLatencyMillis = NativeBridge.outputLatencyMillis(handle),
        underruns = NativeBridge.underrunCount(handle),
    )

    /**
     * Developer tool: measures app-to-app round-trip latency by playing short pulses and timing their
     * return through the microphone. Requires RECORD_AUDIO and a quiet room; blocking, so call it from
     * a background dispatcher. Returns the median of [pulses] measurements in ms, or null if none
     * came back (e.g. headphones, or the mic couldn't hear the speaker).
     */
    fun measureRoundTripMillis(pulses: Int = 5): Double? {
        if (!NativeBridge.startLatencyProbe(handle)) return null
        try {
            Thread.sleep(300) // let the input stream settle and learn the noise floor
            val results = ArrayList<Double>()
            repeat(pulses) {
                NativeBridge.firePulse(handle)
                val deadline = System.nanoTime() + 1_000_000_000L
                var ms = -1.0
                while (System.nanoTime() < deadline) {
                    Thread.sleep(10)
                    ms = NativeBridge.probeResultMillis(handle)
                    if (ms >= 0) break
                }
                if (ms >= 0) results += ms
                Thread.sleep(250) // let the room go quiet before the next pulse
            }
            val median = results.sorted().getOrNull(results.size / 2)
            Log.i(TAG, "Round-trip latency: ${results.joinToString { "%.1f".format(it) }} ms -> median $median")
            return median
        } finally {
            NativeBridge.stopLatencyProbe(handle)
        }
    }

    /**
     * Loads every `assets/samples/<midi>.wav` (16-bit PCM) into the sample voice bank. The file name is
     * the MIDI note the sample was recorded at, e.g. `40.wav` = E2. Blocking: call off the main thread.
     * Returns how many samples were accepted (0 means [SoundType.SAMPLES] will fall back to synthesis).
     */
    fun loadSamplesFromAssets(assets: AssetManager): Int {
        val files = assets.list(SAMPLE_DIR).orEmpty()
            .mapNotNull { name -> name.removeSuffix(".wav").toIntOrNull()?.let { midi -> name to midi } }
        if (files.isEmpty()) return 0
        val bytes = files.map { (name, _) -> assets.open("$SAMPLE_DIR/$name").use { it.readBytes() } }
        val roots = FloatArray(files.size) { midiToHz(files[it].second) }
        val loaded = NativeBridge.loadSamples(handle, bytes.toTypedArray(), roots)
        loadedSampleCount = loaded
        Log.i(TAG, "Loaded $loaded/${files.size} guitar samples")
        return loaded
    }

    companion object {
        private const val TAG = "OboeGuitarEngine"
        const val SAMPLE_DIR = "samples"

        fun oboeVersion(): String = NativeBridge.oboeVersion()

        internal fun midiToHz(midi: Int): Float = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
    }
}
