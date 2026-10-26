package com.strumwise.game

/** A section of the song (on the song clock) to repeat. */
data class LoopRange(val startMs: Long, val endMs: Long) {
    init {
        require(endMs > startMs) { "loop must end after it starts" }
    }
}

data class PracticeOptions(val speed: Float = 1f, val loop: LoopRange? = null) {
    init {
        require(speed in SPEEDS) { "speed must be one of $SPEEDS" }
    }

    /** Practice runs are never saved as high scores. */
    val isPractice: Boolean get() = speed != 1f || loop != null

    companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f)

        /** Loop bars [fromBar, toBar) of a chart (0-based), measured from the first event. */
        fun loopBars(chart: SongChart, fromBar: Int, toBar: Int): LoopRange {
            val barMs = chart.beatMs * chart.beatsPerBar
            val start = chart.offsetMs + (fromBar * barMs).toLong()
            val end = chart.offsetMs + (toBar * barMs).toLong() - 1
            return LoopRange(start, end)
        }
    }
}

/**
 * Collapses the many touch batches of one swipe into a single strum input. A slow strum crosses
 * one string per touch event; without this, each string would try to judge a separate note.
 */
class InputGrouper(private val windowMs: Long = 70) {
    private var lastStrumMs = Long.MIN_VALUE / 2

    /** Returns true if this batch starts a new input, false if it continues the previous strum. */
    fun isNewInput(timeMs: Long, isStrum: Boolean): Boolean {
        if (!isStrum) return true
        val continues = timeMs - lastStrumMs < windowMs
        lastStrumMs = timeMs
        return !continues
    }

    fun reset() {
        lastStrumMs = Long.MIN_VALUE / 2
    }
}
