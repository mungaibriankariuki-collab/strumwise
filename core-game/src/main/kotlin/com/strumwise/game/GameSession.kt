package com.strumwise.game

import kotlin.math.ceil

/** A judgment together with its effect on the score, for UI feedback. */
data class Feedback(val result: JudgeResult, val score: ScoreEvent)

data class UpdateResult(val feedback: List<Feedback>, val loopedBack: Boolean, val finished: Boolean)

/**
 * One play-through of a chart: owns the clock, judge and score. Pure Kotlin — the ViewModel feeds
 * it the audio clock and player inputs, and renders what it exposes.
 *
 * @param calibrationOffsetMs from the calibration screen; positive = player's taps register late.
 */
class GameSession(
    val chart: SongChart,
    private val clock: GameClock,
    val practice: PracticeOptions = PracticeOptions(),
    private val calibrationOffsetMs: Int = 0,
) {
    val notes: List<Note> = chart.notes()

    /** Notes this run actually plays: just the loop section when looping. */
    private val playable: List<Note> = practice.loop?.let { loop -> notes.filter { it.timeMs in loop.startMs..loop.endMs } }
        ?.takeIf { it.isNotEmpty() } ?: notes

    private val judge = HitJudge(playable).also { it.speed = practice.speed }
    val score = ScoreKeeper()

    /** Judgment per note index, for colouring/hiding markers. */
    private val results = HashMap<Int, Judgment>()

    val countInBeats: Int = maxOf(3, chart.beatsPerBar)

    private val firstNoteMs: Long = playable.first().timeMs

    /** Song time the clock starts from: a count-in before the first note to play. */
    val startMs: Double = firstNoteMs - countInBeats * chart.beatMs

    private val endMs: Double = playable.last().timeMs + END_PADDING_MS

    var finished = false
        private set

    fun start() {
        clock.setSpeed(practice.speed)
        clock.start(startMs)
    }

    fun pause() = clock.pause()
    fun resume() = clock.resume()

    fun nowMs(): Double = clock.nowMs()

    /** Beats left in the count-in (3, 2, 1) or null once the song is under way. */
    fun countdown(nowMs: Double = clock.nowMs()): Int? {
        if (nowMs >= firstNoteMs) return null
        val beats = ceil((firstNoteMs - nowMs) / chart.beatMs).toInt()
        return beats.takeIf { it in 1..countInBeats }
    }

    /**
     * A player input that happened at [sourceNanos] on the audio clock. Returns the feedback if it
     * judged a note.
     */
    fun onInput(sourceNanos: Long, chordId: String?, strings: Set<Int>, isStrum: Boolean): Feedback? {
        if (finished) return null
        val songMs = clock.songTimeAt(sourceNanos) - calibrationOffsetMs * practice.speed
        val result = judge.onInput(PlayerInput(songMs, chordId, strings, isStrum)) ?: return null
        return record(result)
    }

    /** Call every frame: turns unplayed notes into misses, handles loops and the end of the song. */
    fun update(nowMs: Double = clock.nowMs()): UpdateResult {
        if (finished) return UpdateResult(emptyList(), loopedBack = false, finished = true)
        val feedback = judge.update(nowMs).map(::record)
        val loop = practice.loop
        if (loop != null && nowMs > loop.endMs + Windows.GOOD_MS * practice.speed) {
            judge.rearm(loop.startMs, loop.endMs)
            results.keys.removeAll { notes[it].timeMs in loop.startMs..loop.endMs }
            clock.seek(startMs)
            return UpdateResult(feedback, loopedBack = true, finished = false)
        }
        if (loop == null && nowMs > endMs && judge.isComplete) finished = true
        return UpdateResult(feedback, loopedBack = false, finished = finished)
    }

    /** Ends the session early (quit from the pause menu); unplayed notes don't count. */
    fun finishNow() {
        finished = true
    }

    /** Notes from [behindMs] in the past to [aheadMs] in the future, for drawing markers. */
    fun visibleNotes(nowMs: Double, aheadMs: Double, behindMs: Double = 250.0): List<Note> =
        ArrayList<Note>().also { list -> forEachVisibleNote(nowMs, aheadMs, behindMs) { list += it } }

    /**
     * Allocation-free version of [visibleNotes] for the draw loop: binary-searches the first note in
     * the window and walks forward until past it.
     */
    inline fun forEachVisibleNote(nowMs: Double, aheadMs: Double, behindMs: Double, action: (Note) -> Unit) {
        val sorted = playableNotes
        var lo = 0
        var hi = sorted.size
        val from = nowMs - behindMs
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].timeMs < from) lo = mid + 1 else hi = mid
        }
        val to = nowMs + aheadMs
        var i = lo
        while (i < sorted.size && sorted[i].timeMs <= to) action(sorted[i++])
    }

    /** Playable notes sorted by time (exposed for the inline [forEachVisibleNote]). */
    @PublishedApi internal val playableNotes: List<Note> get() = playable

    fun resultOf(note: Note): Judgment? = results[note.index]

    /** The next chord the player will need (for highlighting its button). */
    fun upcomingChord(nowMs: Double): String? =
        playable.firstOrNull { it.timeMs >= nowMs - Windows.GOOD_MS && results[it.index] == null }?.event?.chord

    fun result(previousBest: Long?): GameResult {
        val isNewHigh = !practice.isPractice && score.score > 0 && (previousBest == null || score.score > previousBest)
        return score.result(isNewHighScore = isNewHigh)
    }

    private fun record(result: JudgeResult): Feedback {
        results[result.note.index] = result.judgment
        return Feedback(result, score.record(result.judgment))
    }

    companion object {
        const val END_PADDING_MS = 1500.0
    }
}
