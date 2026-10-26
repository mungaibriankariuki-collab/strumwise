package com.strumwise.audio

/** Which voice type the engine uses for new notes. */
enum class SoundType(internal val nativeValue: Int) {
    /** Extended Karplus-Strong plucked-string synthesis. Always available. */
    SYNTH(0),

    /** Recorded 16-bit WAV samples from `assets/samples/`. Falls back to [SYNTH] if none are installed. */
    SAMPLES(1),
}

/** Diagnostic numbers for the debug overlay. */
data class EngineStats(
    val running: Boolean,
    val sampleRate: Int,
    val outputLatencyMillis: Double,
    val underruns: Int,
)

/**
 * The guitar's sound engine. Strings are numbered 0 (low E) to 5 (high E).
 *
 * Every call is non-blocking: control calls are queued for the audio thread, which applies them
 * at the start of its next buffer (or, for delayed notes, on the exact sample).
 */
interface GuitarEngine {
    val isRunning: Boolean

    /** Recorded samples available to [SoundType.SAMPLES]; 0 means that mode falls back to the synth. */
    val loadedSampleCount: Int

    /** Opens and starts the audio stream. Returns false if no output stream could be opened. */
    fun start(): Boolean

    /** Stops and closes the stream. The audio clock freezes until the next [start]. */
    fun stop()

    /**
     * Plays [fret] on [string]. A new note damps the previous note on the same string.
     * [velocity] is 0..1; [delayMs] schedules the note sample-accurately (used for strum spacing).
     */
    fun noteOn(string: Int, fret: Int, velocity: Float, delayMs: Float = 0f)

    fun muteString(string: Int)

    fun muteAll()

    /** 0..1, smoothed on the audio thread so changes never click. */
    fun setMasterVolume(volume: Float)

    /** Open-string frequencies in Hz, low E first. Must have 6 entries. */
    fun setTuning(openStringHz: FloatArray)

    fun setSoundType(type: SoundType)

    /**
     * Starts a click track at [bpm] whose first click (a downbeat) sounds at [firstClickNanos] on the
     * audio clock, accenting every [beatsPerBar]th click; bpm <= 0 stops it. A start time in the past
     * joins the beat grid with the right accent phase.
     */
    fun setMetronome(bpm: Float, firstClickNanos: Long, beatsPerBar: Int = 4)

    /**
     * Monotonic game clock in nanoseconds: how much audio has been *heard* since the engine started,
     * derived from Oboe's presentation timestamps. Frozen while the stream is stopped.
     */
    fun getAudioTimeNanos(): Long

    fun stats(): EngineStats

    /** Called when the app goes to the background: releases the stream but remembers it was started. */
    fun pauseForBackground()

    /** Called when the app returns: restarts the stream if it was running before [pauseForBackground]. */
    fun resumeFromForeground()
}
