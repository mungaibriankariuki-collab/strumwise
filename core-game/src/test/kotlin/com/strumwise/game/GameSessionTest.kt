package com.strumwise.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GameSessionTest {
    private var nanos = 0L
    private val clock = GameClock { nanos }
    private fun setSongMs(session: GameSession, ms: Double) {
        nanos = clock.sourceTimeAt(ms)
        assertThat(session.nowMs()).isWithin(1e-3).of(ms)
    }

    // 120 bpm, 4/4: beat = 500 ms. Notes at 1000, 1500, 2000, 2500 (offset 1000).
    private val chart = SongChart(
        id = "t", title = "T", bpm = 120f, offsetMs = 1000, difficulty = Difficulty.EASY,
        events = listOf(0L, 500L, 1000L, 1500L).map { ChartEvent(it, EventType.STRUM, "G", direction = Direction.DOWN) },
    )
    private val all = setOf(0, 1, 2, 3, 4, 5)

    @Test fun countInStartsFourBeatsBeforeFirstNote() {
        val s = GameSession(chart, clock)
        s.start()
        assertThat(s.startMs).isWithin(1e-9).of(1000.0 - 4 * 500.0)
        assertThat(s.countdown()).isEqualTo(4)
        setSongMs(s, 600.0)
        assertThat(s.countdown()).isEqualTo(1)
        setSongMs(s, 1000.0)
        assertThat(s.countdown()).isNull()
    }

    @Test fun fullRunScoresAndFinishes() {
        val s = GameSession(chart, clock)
        s.start()
        for (t in listOf(1000.0, 1500.0, 2000.0, 2500.0)) {
            setSongMs(s, t)
            val f = s.onInput(nanos, "G", all, isStrum = true)!!
            assertThat(f.result.judgment).isEqualTo(Judgment.PERFECT)
        }
        assertThat(s.update().finished).isFalse()
        setSongMs(s, 2500.0 + GameSession.END_PADDING_MS + 1)
        assertThat(s.update().finished).isTrue()
        val r = s.result(previousBest = null)
        assertThat(r.score).isEqualTo(400)
        assertThat(r.stars).isEqualTo(3)
        assertThat(r.isNewHighScore).isTrue()
    }

    @Test fun unplayedNotesAreMissedAndShownAsSuch() {
        val s = GameSession(chart, clock)
        s.start()
        // At 1550 the first note (1000) is long gone; the second (1500) is only 50 ms late — still playable.
        setSongMs(s, 1550.0)
        val u = s.update()
        assertThat(u.feedback.map { it.result.judgment }).containsExactly(Judgment.MISS)
        assertThat(s.resultOf(s.notes[0])).isEqualTo(Judgment.MISS)
        assertThat(s.resultOf(s.notes[1])).isNull()
    }

    @Test fun calibrationOffsetShiftsTaps() {
        // The player habitually taps 60 ms late; with calibration that is a Perfect.
        val s = GameSession(chart, clock, calibrationOffsetMs = 60)
        s.start()
        setSongMs(s, 1060.0)
        assertThat(s.onInput(nanos, "G", all, true)!!.result.judgment).isEqualTo(Judgment.PERFECT)
    }

    @Test fun lateTapWithoutCalibrationIsOnlyGood() {
        val s = GameSession(chart, clock)
        s.start()
        setSongMs(s, 1060.0)
        assertThat(s.onInput(nanos, "G", all, true)!!.result.judgment).isEqualTo(Judgment.GOOD)
    }

    @Test fun beatenHighScoreOnly() {
        val s = GameSession(chart, clock)
        s.start()
        setSongMs(s, 1000.0)
        s.onInput(nanos, "G", all, true)
        assertThat(s.result(previousBest = 100).isNewHighScore).isFalse()
        assertThat(s.result(previousBest = 99).isNewHighScore).isTrue()
    }

    @Test fun practiceRunsNeverSetHighScores() {
        val s = GameSession(chart, clock, PracticeOptions(speed = 0.5f))
        s.start()
        setSongMs(s, 1000.0)
        s.onInput(nanos, "G", all, true)
        assertThat(s.result(previousBest = null).isNewHighScore).isFalse()
    }

    @Test fun halfSpeedSlowsTheSong() {
        val s = GameSession(chart, clock, PracticeOptions(speed = 0.5f))
        s.start()
        val start = s.nowMs()
        nanos += 1_000_000_000
        assertThat(s.nowMs() - start).isWithin(1e-6).of(500.0)
    }

    @Test fun loopRepeatsTheSection() {
        val loop = LoopRange(1500, 2000)
        val s = GameSession(chart, clock, PracticeOptions(loop = loop))
        s.start()
        // Count-in leads up to the loop's first note, not the song's.
        assertThat(s.startMs).isWithin(1e-9).of(1500.0 - 2000.0)
        setSongMs(s, 1500.0)
        s.onInput(nanos, "G", all, true)
        setSongMs(s, 2000.0)
        s.onInput(nanos, "G", all, true)
        setSongMs(s, 2100.0)
        val u = s.update()
        assertThat(u.loopedBack).isTrue()
        assertThat(u.finished).isFalse()
        assertThat(s.nowMs()).isWithin(1e-3).of(s.startMs)
        assertThat(s.resultOf(s.notes[1])).isNull() // re-armed
        setSongMs(s, 1500.0)
        assertThat(s.onInput(nanos, "G", all, true)!!.result.judgment).isEqualTo(Judgment.PERFECT)
        assertThat(s.score.combo).isEqualTo(3)
        // Notes outside the loop are never shown or judged.
        assertThat(s.visibleNotes(0.0, aheadMs = 5000.0).map { it.timeMs }).containsExactly(1500L, 2000L)
    }

    @Test fun upcomingChordFollowsTheChart() {
        val c = chart.copy(events = listOf(
            ChartEvent(0, EventType.STRUM, "G"), ChartEvent(500, EventType.STRUM, "C"),
        ))
        val s = GameSession(c, clock)
        s.start()
        assertThat(s.upcomingChord(0.0)).isEqualTo("G")
        setSongMs(s, 1000.0)
        s.onInput(nanos, "G", all, true)
        assertThat(s.upcomingChord(1000.0)).isEqualTo("C")
    }

    @Test fun visibleNotesWindow() {
        val s = GameSession(chart, clock)
        assertThat(s.visibleNotes(1000.0, aheadMs = 600.0).map { it.timeMs }).containsExactly(1000L, 1500L)
        assertThat(s.visibleNotes(1300.0, aheadMs = 300.0, behindMs = 300.0).map { it.timeMs }).containsExactly(1000L, 1500L)
    }

    @Test fun quittingEndsTheSession() {
        val s = GameSession(chart, clock)
        s.start()
        s.finishNow()
        assertThat(s.finished).isTrue()
        assertThat(s.onInput(nanos, "G", all, true)).isNull()
    }
}
