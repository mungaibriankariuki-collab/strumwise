package com.strumwise.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GameClockTest {
    private var nanos = 5_000_000_000L
    private val clock = GameClock { nanos }
    private fun advanceMs(ms: Long) { nanos += ms * 1_000_000 }

    @Test fun followsTheTimeSourceNotTheWallClock() {
        clock.start(-1000.0)
        assertThat(clock.nowMs()).isEqualTo(-1000.0)
        advanceMs(250)
        assertThat(clock.nowMs()).isWithin(1e-6).of(-750.0)
        Thread.sleep(20) // wall time passing changes nothing
        assertThat(clock.nowMs()).isWithin(1e-6).of(-750.0)
    }

    @Test fun pauseFreezesAndResumeContinues() {
        clock.start(0.0)
        advanceMs(100)
        clock.pause()
        advanceMs(5000)
        assertThat(clock.nowMs()).isWithin(1e-6).of(100.0)
        clock.resume()
        advanceMs(50)
        assertThat(clock.nowMs()).isWithin(1e-6).of(150.0)
    }

    @Test fun speedScalesSongTime() {
        clock.start(0.0)
        clock.setSpeed(0.5f)
        advanceMs(1000)
        assertThat(clock.nowMs()).isWithin(1e-6).of(500.0)
        clock.setSpeed(0.75f)
        advanceMs(1000)
        assertThat(clock.nowMs()).isWithin(1e-6).of(1250.0)
    }

    @Test fun seek() {
        clock.start(0.0)
        advanceMs(3000)
        clock.seek(1000.0)
        assertThat(clock.nowMs()).isWithin(1e-6).of(1000.0)
        advanceMs(10)
        assertThat(clock.nowMs()).isWithin(1e-6).of(1010.0)
    }

    @Test fun songTimeOfAPastInput() {
        clock.start(0.0)
        advanceMs(1000)
        val inputAt = nanos - 30_000_000 // touch happened 30 ms ago
        assertThat(clock.songTimeAt(inputAt)).isWithin(1e-6).of(970.0)
        assertThat(clock.sourceTimeAt(970.0)).isEqualTo(inputAt)
    }
}
