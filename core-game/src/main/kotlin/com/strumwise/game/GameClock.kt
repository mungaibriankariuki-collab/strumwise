package com.strumwise.game

/**
 * Song clock in milliseconds, driven by an external monotonic time source in nanoseconds — in the
 * app that is GuitarEngine.getAudioTimeNanos(), never the wall clock, so the game stays locked to
 * what the player hears.
 *
 * Supports pause/resume, seeking (loops) and playback speed (practice at 50% / 75%).
 */
class GameClock(private val timeSourceNanos: () -> Long) {
    var speed: Float = 1f
        private set
    var isRunning: Boolean = false
        private set

    private var anchorSourceNanos = 0L
    private var anchorSongMs = 0.0

    /** Starts (or restarts) the clock so that "now" is [songMs]. */
    fun start(songMs: Double) {
        anchorSongMs = songMs
        anchorSourceNanos = timeSourceNanos()
        isRunning = true
    }

    fun pause() {
        if (!isRunning) return
        anchorSongMs = nowMs()
        isRunning = false
    }

    fun resume() {
        if (isRunning) return
        anchorSourceNanos = timeSourceNanos()
        isRunning = true
    }

    fun seek(songMs: Double) {
        anchorSongMs = songMs
        anchorSourceNanos = timeSourceNanos()
    }

    fun setSpeed(newSpeed: Float) {
        require(newSpeed > 0f) { "speed must be positive" }
        anchorSongMs = nowMs()
        anchorSourceNanos = timeSourceNanos()
        speed = newSpeed
    }

    fun nowMs(): Double = songTimeAt(timeSourceNanos())

    /** Song time corresponding to an earlier (or later) source time, e.g. when a touch happened. */
    fun songTimeAt(sourceNanos: Long): Double =
        if (!isRunning) anchorSongMs else anchorSongMs + (sourceNanos - anchorSourceNanos) / 1e6 * speed

    /** Source-clock time at which the song reaches [songMs] (at the current speed). */
    fun sourceTimeAt(songMs: Double): Long =
        anchorSourceNanos + ((songMs - anchorSongMs) / speed * 1e6).toLong()
}
