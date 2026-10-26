package com.strumwise.app.ui.guitar

import kotlin.math.abs

/** Down = toward the high E string (string index increasing), up = toward low E. */
enum class StrumDirection { DOWN, UP }

/**
 * One string being sounded by touch.
 * @param delayMs when to sound it relative to now (strum spacing); 0 for the first string.
 * @param direction null for a tap/pluck.
 */
data class StringHit(val string: Int, val velocity: Float, val delayMs: Float, val direction: StrumDirection?)

/**
 * Turns raw pointer positions into string hits. Pure Kotlin, so it is unit tested on the JVM.
 *
 * Each pointer is tracked separately (multi-touch). For every movement we take the line segment
 * from the previous position to the new one and find every string it crosses, so even a very fast
 * swipe that skips frames still hits every string in between, in the order it crossed them.
 *
 * Strings crossed within one segment get 6–12 ms spacing (faster swipe = tighter), like a real strum.
 * Velocity comes from the vertical swipe speed.
 */
class StrumDetector(
    private val minSpacingMs: Float = 6f,
    private val maxSpacingMs: Float = 12f,
) {
    /** Y coordinate of each string, index 0 = low E. Updated whenever the layout changes. */
    var stringYs: FloatArray = FloatArray(6)

    /** Pixels per dp, so speeds are device independent. */
    var density: Float = 1f

    /** [lastString] can't be re-triggered until the finger has moved clear of it ([armed]). */
    private class Track(var y: Float, var timeMs: Long, var speed: Float, var lastString: Int, var armed: Boolean)

    private val tracks = HashMap<Long, Track>()

    /** A finger lands. If it lands on a string, that string is plucked immediately. */
    fun down(pointerId: Long, y: Float, timeMs: Long): List<StringHit> {
        val string = stringAt(y)
        tracks[pointerId] = Track(y, timeMs, 0f, string ?: -1, armed = string == null)
        return if (string != null) listOf(StringHit(string, TAP_VELOCITY, 0f, null)) else emptyList()
    }

    fun move(pointerId: Long, y: Float, timeMs: Long): List<StringHit> {
        val track = tracks[pointerId] ?: return emptyList()
        val prevY = track.y
        val dy = y - prevY
        val dt = (timeMs - track.timeMs).coerceAtLeast(1L).toFloat()
        // Smooth the speed a little so one noisy event doesn't swing the velocity.
        val instant = abs(dy) / dt / density  // dp per ms
        track.speed = if (track.speed == 0f) instant else 0.5f * track.speed + 0.5f * instant
        track.y = y
        track.timeMs = timeMs
        // Re-arm only if the finger was already clear of the last string *before* this move, so a
        // finger that lands on a string and swipes through it doesn't sound it twice.
        if (!track.armed && track.lastString >= 0 && abs(prevY - stringYs[track.lastString]) > pluckRadius()) {
            track.armed = true
        }
        if (dy == 0f) return emptyList()

        // Strings whose line lies within (prevY, y]: the segment crossed (or just reached) them.
        val crossed = stringYs.indices.filter { s ->
            val a = prevY - stringYs[s]
            val b = y - stringYs[s]
            (a < 0f && b >= 0f) || (a > 0f && b <= 0f)
        }.filterNot { s ->
            // Hysteresis: a finger wobbling on the string it just hit mustn't re-trigger it.
            s == track.lastString && !track.armed
        }.sortedBy { s -> (stringYs[s] - prevY) / dy }  // order of crossing along the segment
        if (crossed.isEmpty()) return emptyList()

        val direction = if (dy > 0f) StrumDirection.DOWN else StrumDirection.UP
        val velocity = velocityFor(track.speed)
        val spacing = maxSpacingMs - (maxSpacingMs - minSpacingMs) * ((velocity - MIN_VELOCITY) / (1f - MIN_VELOCITY))
        track.lastString = crossed.last()
        track.armed = abs(y - stringYs[track.lastString]) > pluckRadius()
        return crossed.mapIndexed { i, s -> StringHit(s, velocity, i * spacing, direction) }
    }

    fun up(pointerId: Long) {
        tracks.remove(pointerId)
    }

    fun cancelAll() = tracks.clear()

    /** The string under [y], if it is within the pluck radius of one. */
    fun stringAt(y: Float): Int? {
        if (stringYs.size < 2) return null
        val nearest = stringYs.indices.minByOrNull { abs(stringYs[it] - y) } ?: return null
        return nearest.takeIf { abs(stringYs[it] - y) <= pluckRadius() }
    }

    private fun pluckRadius(): Float =
        if (stringYs.size < 2) 0f else abs(stringYs[1] - stringYs[0]) * PLUCK_RADIUS

    companion object {
        const val TAP_VELOCITY = 0.7f
        const val MIN_VELOCITY = 0.3f
        const val PLUCK_RADIUS = 0.3f

        /** Maps swipe speed (dp/ms) to velocity: gentle brush ≈ 0.3, hard strum = 1.0. */
        fun velocityFor(dpPerMs: Float): Float {
            val t = ((dpPerMs - 0.15f) / 2.0f).coerceIn(0f, 1f)
            return MIN_VELOCITY + (1f - MIN_VELOCITY) * t
        }
    }
}
