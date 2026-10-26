package com.strumwise.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HitJudgeTest {
    private fun strum(t: Long, chord: String = "G") = ChartEvent(t, EventType.STRUM, chord, direction = Direction.DOWN)
    private fun pluck(t: Long, string: Int, chord: String? = null) = ChartEvent(t, EventType.PLUCK, chord, string)
    private fun judge(vararg events: ChartEvent) = HitJudge(events.mapIndexed { i, e -> Note(i, e.timeMs, e) })
    private fun strumAt(t: Double, chord: String? = "G") = PlayerInput(t, chord, setOf(0, 1, 2, 3, 4, 5), isStrum = true)

    @Test fun windowsBoundaries() {
        assertThat(Windows.judge(0.0)).isEqualTo(Judgment.PERFECT)
        assertThat(Windows.judge(45.0)).isEqualTo(Judgment.PERFECT)
        assertThat(Windows.judge(-45.0)).isEqualTo(Judgment.PERFECT)
        assertThat(Windows.judge(45.1)).isEqualTo(Judgment.GOOD)
        assertThat(Windows.judge(-90.0)).isEqualTo(Judgment.GOOD)
        assertThat(Windows.judge(90.1)).isEqualTo(Judgment.MISS)
        assertThat(Windows.judge(-200.0)).isEqualTo(Judgment.MISS)
    }

    @Test fun perfectGoodAndSignedError() {
        val j = judge(strum(1000), strum(2000), strum(3000))
        assertThat(j.onInput(strumAt(1010.0))!!.judgment).isEqualTo(Judgment.PERFECT)
        val good = j.onInput(strumAt(1930.0))!!
        assertThat(good.judgment).isEqualTo(Judgment.GOOD)
        assertThat(good.errorMs).isWithin(1e-9).of(-70.0)
        assertThat(j.onInput(strumAt(3089.0))!!.judgment).isEqualTo(Judgment.GOOD)
        assertThat(j.isComplete).isTrue()
    }

    @Test fun inputOutsideAnyWindowIsIgnored() {
        val j = judge(strum(1000))
        assertThat(j.onInput(strumAt(500.0))).isNull()
        assertThat(j.onInput(strumAt(1095.0))).isNull()
        assertThat(j.remaining).isEqualTo(1)
    }

    @Test fun wrongChordIsMiss() {
        val j = judge(strum(1000, "G"))
        val r = j.onInput(strumAt(1000.0, "C"))!!
        assertThat(r.judgment).isEqualTo(Judgment.MISS)
        assertThat(r.wrongChord).isTrue()
    }

    @Test fun noChordHeldOnChordNoteIsMiss() {
        val j = judge(strum(1000, "G"))
        assertThat(j.onInput(strumAt(1000.0, null))!!.judgment).isEqualTo(Judgment.MISS)
    }

    @Test fun pluckMustHitTheRightString() {
        val j = judge(pluck(1000, 3), pluck(2000, 4))
        assertThat(j.onInput(PlayerInput(1000.0, null, setOf(3), isStrum = false))!!.judgment).isEqualTo(Judgment.PERFECT)
        assertThat(j.onInput(PlayerInput(2000.0, null, setOf(2), isStrum = false))!!.judgment).isEqualTo(Judgment.MISS)
    }

    @Test fun pluckWithChordNeedsTheShape() {
        val j = judge(pluck(1000, 1, "Am"), pluck(2000, 1, "Am"))
        assertThat(j.onInput(PlayerInput(1000.0, "Am", setOf(1), false))!!.judgment).isEqualTo(Judgment.PERFECT)
        assertThat(j.onInput(PlayerInput(2000.0, "C", setOf(1), false))!!.judgment).isEqualTo(Judgment.MISS)
    }

    @Test fun tapOnStrumNoteIsMiss() {
        val j = judge(strum(1000))
        assertThat(j.onInput(PlayerInput(1000.0, "G", setOf(2), isStrum = false))!!.judgment).isEqualTo(Judgment.MISS)
    }

    @Test fun passedNotesBecomeMisses() {
        val j = judge(strum(1000), strum(2000), strum(3000))
        assertThat(j.update(1090.0)).isEmpty()
        val missed = j.update(2100.0)
        assertThat(missed.map { it.note.timeMs }).containsExactly(1000L, 2000L).inOrder()
        assertThat(missed.all { it.judgment == Judgment.MISS && it.errorMs == null }).isTrue()
        assertThat(j.update(2100.0)).isEmpty() // not reported twice
        assertThat(j.onInput(strumAt(3000.0))!!.judgment).isEqualTo(Judgment.PERFECT)
    }

    @Test fun eachNoteIsJudgedOnce() {
        val j = judge(strum(1000))
        assertThat(j.onInput(strumAt(1000.0))).isNotNull()
        assertThat(j.onInput(strumAt(1010.0))).isNull()
        assertThat(j.update(5000.0)).isEmpty()
    }

    @Test fun earliestNoteInWindowIsTakenFirst() {
        // Two notes 100 ms apart: an input between them hits the earlier one first.
        val j = judge(strum(1000), strum(1100))
        assertThat(j.onInput(strumAt(1060.0))!!.note.timeMs).isEqualTo(1000)
        assertThat(j.onInput(strumAt(1070.0))!!.note.timeMs).isEqualTo(1100)
    }

    @Test fun windowsAreInRealTimeAtPracticeSpeed() {
        // At half speed, 60 song-ms equals 120 real ms: outside Good.
        val j = judge(strum(1000))
        j.speed = 0.5f
        assertThat(j.onInput(strumAt(1060.0))).isNull()
        val r = j.onInput(strumAt(1020.0))!!
        assertThat(r.errorMs).isWithin(1e-9).of(40.0)
        assertThat(r.judgment).isEqualTo(Judgment.PERFECT)
    }

    @Test fun rearmAllowsReplayingALoop() {
        val j = judge(strum(1000), strum(2000))
        j.update(5000.0)
        assertThat(j.isComplete).isTrue()
        j.rearm(900, 2100)
        assertThat(j.remaining).isEqualTo(2)
        assertThat(j.onInput(strumAt(1000.0))!!.judgment).isEqualTo(Judgment.PERFECT)
    }
}
