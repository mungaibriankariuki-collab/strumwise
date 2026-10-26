package com.strumwise.app.ui.guitar

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Visual vibration of the six strings. Plain arrays hold the per-string state; only [frameNanos]
 * (and [generation], which restarts the animation loop) are snapshot state. The canvas reads
 * [frameNanos] in the draw phase, so an animating string invalidates drawing only, never composition.
 */
@Stable
class StringVibrations {
    private val amplitude = FloatArray(6)
    private val startNanos = LongArray(6)
    private val fretted = IntArray(6)

    /** Current frame time; written by the animation loop, read during draw. */
    var frameNanos by mutableLongStateOf(0L)

    /** Bumped on every pluck so the animation loop (re)starts. */
    var generation by mutableIntStateOf(0)
        private set

    fun pluck(string: Int, fret: Int, velocity: Float, delayMs: Float, nowNanos: Long = System.nanoTime()) {
        amplitude[string] = 0.25f + 0.75f * velocity
        startNanos[string] = nowNanos + (delayMs * 1_000_000).toLong()
        fretted[string] = fret
        generation++
    }

    fun damp(string: Int) {
        amplitude[string] = 0f
    }

    fun dampAll() = amplitude.fill(0f)

    /** Fret the string is held at (vibration runs from that fret to the bridge). */
    fun fretOf(string: Int): Int = fretted[string]

    /** Envelope 0..1 at [now], 0 once the string has settled or before a delayed pluck starts. */
    fun envelope(string: Int, now: Long): Float {
        val a = amplitude[string]
        if (a == 0f) return 0f
        val t = (now - startNanos[string]) / 1e9f
        if (t < 0f) return 0f
        if (t > SETTLE_SECONDS) return 0f
        return a * exp(-t / DECAY_SECONDS)
    }

    /**
     * Displacement (in units of the max amplitude) at [position] 0..1 along the vibrating length.
     * A fundamental standing wave whose phase flips fast enough to read as a blur, plus a small
     * second harmonic so it doesn't look perfectly mechanical.
     */
    fun displacement(string: Int, position: Float, now: Long): Float {
        val env = envelope(string, now)
        if (env == 0f) return 0f
        val t = (now - startNanos[string]) / 1e9f
        val rate = VISUAL_HZ * (1f + string * 0.08f)
        val fundamental = sin(PI * position).toFloat() * sin(2f * PI.toFloat() * rate * t)
        val second = 0.25f * sin(2 * PI * position).toFloat() * sin(2f * PI.toFloat() * rate * 1.9f * t + 1f)
        return env * (fundamental + second)
    }

    fun anyActive(now: Long): Boolean = (0 until 6).any { amplitude[it] > 0f && (now - startNanos[it]) / 1e9f < SETTLE_SECONDS }

    companion object {
        const val DECAY_SECONDS = 0.55f
        const val SETTLE_SECONDS = 2.5f
        /** Deliberately slower than the real pitch: a 60 fps screen can only show a wobble. */
        const val VISUAL_HZ = 23f
    }
}
