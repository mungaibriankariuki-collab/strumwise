package com.strumwise.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScoreKeeperTest {
    @Test fun basePoints() {
        val s = ScoreKeeper()
        assertThat(s.record(Judgment.PERFECT).points).isEqualTo(100)
        assertThat(s.record(Judgment.GOOD).points).isEqualTo(50)
        assertThat(s.record(Judgment.MISS).points).isEqualTo(0)
        assertThat(s.score).isEqualTo(150)
    }

    @Test fun multiplierThresholds() {
        assertThat(ScoreKeeper.multiplierFor(0)).isEqualTo(1)
        assertThat(ScoreKeeper.multiplierFor(9)).isEqualTo(1)
        assertThat(ScoreKeeper.multiplierFor(10)).isEqualTo(2)
        assertThat(ScoreKeeper.multiplierFor(24)).isEqualTo(2)
        assertThat(ScoreKeeper.multiplierFor(25)).isEqualTo(3)
        assertThat(ScoreKeeper.multiplierFor(49)).isEqualTo(3)
        assertThat(ScoreKeeper.multiplierFor(50)).isEqualTo(4)
        assertThat(ScoreKeeper.multiplierFor(500)).isEqualTo(4)
    }

    @Test fun tenthHitScoresDoubleAndAnnouncesIt() {
        val s = ScoreKeeper()
        repeat(9) { assertThat(s.record(Judgment.PERFECT).multiplierChanged).isFalse() }
        val tenth = s.record(Judgment.PERFECT)
        assertThat(tenth.multiplier).isEqualTo(2)
        assertThat(tenth.points).isEqualTo(200)
        assertThat(tenth.multiplierChanged).isTrue()
        assertThat(s.score).isEqualTo(900 + 200)
    }

    @Test fun fullComboScoreFor60Perfects() {
        val s = ScoreKeeper()
        repeat(60) { s.record(Judgment.PERFECT) }
        // 9 at x1, 15 at x2, 25 at x3, 11 at x4
        assertThat(s.score).isEqualTo(9 * 100L + 15 * 200 + 25 * 300 + 11 * 400)
        assertThat(s.maxCombo).isEqualTo(60)
        assertThat(s.multiplier).isEqualTo(4)
    }

    @Test fun missResetsComboAndMultiplier() {
        val s = ScoreKeeper()
        repeat(12) { s.record(Judgment.GOOD) }
        assertThat(s.multiplier).isEqualTo(2)
        val miss = s.record(Judgment.MISS)
        assertThat(miss.comboBroken).isTrue()
        assertThat(miss.multiplierChanged).isTrue()
        assertThat(s.combo).isEqualTo(0)
        assertThat(s.multiplier).isEqualTo(1)
        assertThat(s.maxCombo).isEqualTo(12)
        assertThat(s.record(Judgment.PERFECT).points).isEqualTo(100)
    }

    @Test fun missWithoutComboIsNotABreak() {
        assertThat(ScoreKeeper().record(Judgment.MISS).comboBroken).isFalse()
    }

    @Test fun goodsBuildComboToo() {
        val s = ScoreKeeper()
        repeat(10) { s.record(Judgment.GOOD) }
        assertThat(s.combo).isEqualTo(10)
        assertThat(s.score).isEqualTo(9 * 50L + 100)
    }

    @Test fun accuracy() {
        val s = ScoreKeeper()
        assertThat(s.accuracyPercent).isEqualTo(100.0)
        s.record(Judgment.PERFECT)
        s.record(Judgment.GOOD)
        s.record(Judgment.MISS)
        s.record(Judgment.PERFECT)
        assertThat(s.accuracyPercent).isWithin(1e-9).of(62.5)
        assertThat(s.judgedCount).isEqualTo(4)
    }

    @Test fun stars() {
        assertThat(ScoreKeeper.starsFor(100.0)).isEqualTo(3)
        assertThat(ScoreKeeper.starsFor(90.0)).isEqualTo(3)
        assertThat(ScoreKeeper.starsFor(89.9)).isEqualTo(2)
        assertThat(ScoreKeeper.starsFor(70.0)).isEqualTo(2)
        assertThat(ScoreKeeper.starsFor(69.9)).isEqualTo(1)
        assertThat(ScoreKeeper.starsFor(0.0)).isEqualTo(1)
    }

    @Test fun result() {
        val s = ScoreKeeper()
        repeat(9) { s.record(Judgment.PERFECT) }
        s.record(Judgment.MISS)
        val r = s.result(isNewHighScore = true)
        assertThat(r).isEqualTo(GameResult(900, 9, 9, 0, 1, 90.0, 3, true))
    }
}
