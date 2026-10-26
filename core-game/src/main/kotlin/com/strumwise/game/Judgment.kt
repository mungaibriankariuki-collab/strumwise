package com.strumwise.game

import kotlin.math.abs

enum class Judgment { PERFECT, GOOD, MISS }

/** Timing windows, in real (not song) milliseconds either side of the note. */
object Windows {
    const val PERFECT_MS = 45.0
    const val GOOD_MS = 90.0

    fun judge(realErrorMs: Double): Judgment = when {
        abs(realErrorMs) <= PERFECT_MS -> Judgment.PERFECT
        abs(realErrorMs) <= GOOD_MS -> Judgment.GOOD
        else -> Judgment.MISS
    }
}

/** What the player did, already mapped to song time (with calibration offset applied). */
data class PlayerInput(
    val songTimeMs: Double,
    /** Chord shape held when playing; null = open strings / free fret. */
    val chordId: String?,
    /** Strings that sounded. */
    val strings: Set<Int>,
    /** True for a swipe across strings, false for a tap on one string. */
    val isStrum: Boolean,
)

data class JudgeResult(
    val note: Note,
    val judgment: Judgment,
    /** Signed timing error in real ms (negative = early). Null for notes that passed unplayed. */
    val errorMs: Double?,
    val wrongChord: Boolean = false,
)

/**
 * Matches player inputs to chart notes.
 *
 * - An input judges the earliest unjudged note within the Good window (in real time, so practice
 *   speed doesn't widen the windows).
 * - The wrong chord (or wrong string) on a note within the window is a Miss.
 * - Notes that pass the Good window unplayed become Misses in [update].
 * - Inputs with no note nearby are ignored (no penalty for strumming along).
 */
class HitJudge(notes: List<Note>) {
    private val notes = notes.sortedBy { it.timeMs }
    private val judged = BooleanArray(this.notes.size)

    /** First note that may still be unjudged; everything before it is done. */
    private var cursor = 0

    var speed: Float = 1f

    fun onInput(input: PlayerInput): JudgeResult? {
        var best: Int? = null
        var i = cursor
        while (i < notes.size) {
            val dtSong = input.songTimeMs - notes[i].timeMs
            if (dtSong / speed < -Windows.GOOD_MS) break  // later notes are further away
            if (!judged[i] && abs(dtSong / speed) <= Windows.GOOD_MS) {
                best = i
                break
            }
            i++
        }
        val index = best ?: return null
        val note = notes[index]
        judged[index] = true
        advanceCursor()
        val error = (input.songTimeMs - note.timeMs) / speed
        val matches = matches(note.event, input)
        return JudgeResult(note, if (matches) Windows.judge(error) else Judgment.MISS, error, wrongChord = !matches)
    }

    /** Misses for every note that has slipped past the window by [songTimeMs]. */
    fun update(songTimeMs: Double): List<JudgeResult> {
        val missed = ArrayList<JudgeResult>()
        var i = cursor
        while (i < notes.size && (songTimeMs - notes[i].timeMs) / speed > Windows.GOOD_MS) {
            if (!judged[i]) {
                judged[i] = true
                missed += JudgeResult(notes[i], Judgment.MISS, null)
            }
            i++
        }
        advanceCursor()
        return missed
    }

    /** Un-judges notes in a time range so a practice loop can play them again. */
    fun rearm(fromMs: Long, toMs: Long) {
        for (i in notes.indices) if (notes[i].timeMs in fromMs..toMs) judged[i] = false
        cursor = judged.indexOfFirst { !it }.let { if (it < 0) notes.size else it }
    }

    val remaining: Int get() = judged.count { !it }

    val isComplete: Boolean get() = cursor >= notes.size

    private fun advanceCursor() {
        while (cursor < notes.size && judged[cursor]) cursor++
    }

    companion object {
        fun matches(event: ChartEvent, input: PlayerInput): Boolean {
            if (event.chord != null && event.chord != input.chordId) return false
            return when (event.type) {
                EventType.STRUM -> input.isStrum
                EventType.PLUCK -> event.string in input.strings
            }
        }
    }
}
