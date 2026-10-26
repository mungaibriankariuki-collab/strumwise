package com.strumwise.game

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class CalibrationAndInputTest {
    private val clicks = (0 until 12).map { it * 500.0 }

    @Test fun offsetIsMedianOfTapDeltas() {
        val taps = clicks.mapIndexed { i, c -> c + 40 + (if (i % 2 == 0) 5 else -5) }
        assertThat(Calibration.computeOffsetMs(clicks, taps)).isEqualTo(40)
    }

    @Test fun earlyTapperGetsNegativeOffset() {
        val taps = clicks.map { it - 25 }
        assertThat(Calibration.computeOffsetMs(clicks, taps)).isEqualTo(-25)
    }

    @Test fun outliersAreIgnored() {
        val taps = clicks.map { it + 30 } + listOf(1240.0, 3260.0) // stray taps half a beat off
        assertThat(Calibration.computeOffsetMs(clicks, taps)).isEqualTo(30)
    }

    @Test fun tooFewTaps() {
        assertThat(Calibration.computeOffsetMs(clicks, clicks.take(5))).isNull()
        assertThat(Calibration.computeOffsetMs(listOf(0.0), clicks)).isNull()
    }

    @Test fun offsetIsClamped() {
        val slowClicks = (0 until 10).map { it * 2000.0 }
        assertThat(Calibration.computeOffsetMs(slowClicks, slowClicks.map { it + 900 })).isEqualTo(300)
    }

    @Test fun consistency() {
        val taps = clicks.mapIndexed { i, c -> c + 50 + (if (i % 2 == 0) 10 else -10) }
        assertThat(Calibration.consistencyMs(clicks, taps)).isWithin(1e-9).of(10.0)
    }

    @Test fun slowStrumBatchesAreOneInput() {
        val g = InputGrouper(windowMs = 70)
        assertThat(g.isNewInput(1000, isStrum = true)).isTrue()
        assertThat(g.isNewInput(1016, isStrum = true)).isFalse()
        assertThat(g.isNewInput(1032, isStrum = true)).isFalse()
        assertThat(g.isNewInput(1200, isStrum = true)).isTrue()
    }

    @Test fun tapsAreAlwaysNewInputs() {
        val g = InputGrouper()
        assertThat(g.isNewInput(1000, isStrum = false)).isTrue()
        assertThat(g.isNewInput(1001, isStrum = false)).isTrue()
    }

    @Test fun practiceOptions() {
        assertThat(PracticeOptions().isPractice).isFalse()
        assertThat(PracticeOptions(speed = 0.75f).isPractice).isTrue()
        assertThat(PracticeOptions(loop = LoopRange(0, 10)).isPractice).isTrue()
        assertThrows(IllegalArgumentException::class.java) { PracticeOptions(speed = 0.6f) }
        assertThrows(IllegalArgumentException::class.java) { LoopRange(10, 10) }
    }

    @Test fun loopBars() {
        val chart = SongChart("x", "X", bpm = 120f, beatsPerBar = 4, offsetMs = 1000, difficulty = Difficulty.EASY,
            events = listOf(ChartEvent(0, EventType.STRUM, "G")))
        val loop = PracticeOptions.loopBars(chart, 2, 4)
        assertThat(loop.startMs).isEqualTo(1000 + 4000)
        assertThat(loop.endMs).isEqualTo(1000 + 8000 - 1)
    }
}
