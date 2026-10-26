package com.strumwise.game

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Latency calibration: the player taps along to a click track; the median difference between each
 * tap and its nearest click is their input offset (touch + audio output + reaction habits).
 * A positive offset means taps register late, so it is subtracted from tap times when judging.
 */
object Calibration {
    const val MIN_TAPS = 6
    const val MAX_OFFSET_MS = 300

    /**
     * @param clickTimesMs when each click was heard, on the audio clock
     * @param tapTimesMs when each tap happened, on the same clock
     * @return the offset in ms, or null if there aren't enough usable taps
     */
    fun computeOffsetMs(clickTimesMs: List<Double>, tapTimesMs: List<Double>): Int? {
        if (clickTimesMs.size < 2) return null
        val interval = clickTimesMs.zipWithNext { a, b -> b - a }.average()
        val deltas = tapTimesMs.mapNotNull { tap ->
            val nearest = clickTimesMs.minByOrNull { abs(it - tap) } ?: return@mapNotNull null
            (tap - nearest).takeIf { abs(it) < interval / 2 }
        }
        if (deltas.size < MIN_TAPS) return null
        val median = deltas.sorted().let { s ->
            if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
        }
        return median.roundToInt().coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
    }

    /** Spread of the taps (median absolute deviation) — a rough "how steady were you". */
    fun consistencyMs(clickTimesMs: List<Double>, tapTimesMs: List<Double>): Double? {
        val offset = computeOffsetMs(clickTimesMs, tapTimesMs) ?: return null
        val deviations = tapTimesMs.map { tap ->
            val nearest = clickTimesMs.minBy { abs(it - tap) }
            abs(tap - nearest - offset)
        }.sorted()
        return deviations[deviations.size / 2]
    }
}
