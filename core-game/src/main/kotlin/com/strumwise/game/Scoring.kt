package com.strumwise.game

/** Points, combo and multiplier bookkeeping. */
class ScoreKeeper {
    var score = 0L
        private set
    var combo = 0
        private set
    var maxCombo = 0
        private set
    var perfect = 0
        private set
    var good = 0
        private set
    var miss = 0
        private set

    val multiplier: Int get() = multiplierFor(combo)

    val judgedCount: Int get() = perfect + good + miss

    /** Accuracy 0..100: Perfect counts fully, Good half. 100 before anything is judged. */
    val accuracyPercent: Double
        get() = if (judgedCount == 0) 100.0 else (perfect + good * 0.5) * 100.0 / judgedCount

    /**
     * Records a judgment. The hit that reaches a threshold already scores at the new multiplier
     * (the 10th consecutive hit is worth x2).
     */
    fun record(judgment: Judgment): ScoreEvent {
        val before = multiplier
        return when (judgment) {
            Judgment.MISS -> {
                val hadCombo = combo > 0
                miss++
                combo = 0
                ScoreEvent(judgment, 0, 1, multiplierChanged = before != 1, comboBroken = hadCombo)
            }
            Judgment.PERFECT, Judgment.GOOD -> {
                if (judgment == Judgment.PERFECT) perfect++ else good++
                combo++
                maxCombo = maxOf(maxCombo, combo)
                val m = multiplier
                val points = basePoints(judgment) * m
                score += points
                ScoreEvent(judgment, points, m, multiplierChanged = m != before, comboBroken = false)
            }
        }
    }

    fun result(isNewHighScore: Boolean = false) = GameResult(
        score = score,
        maxCombo = maxCombo,
        perfect = perfect,
        good = good,
        miss = miss,
        accuracyPercent = accuracyPercent,
        stars = starsFor(accuracyPercent),
        isNewHighScore = isNewHighScore,
    )

    companion object {
        const val PERFECT_POINTS = 100
        const val GOOD_POINTS = 50

        fun basePoints(j: Judgment): Int = when (j) {
            Judgment.PERFECT -> PERFECT_POINTS
            Judgment.GOOD -> GOOD_POINTS
            Judgment.MISS -> 0
        }

        fun multiplierFor(combo: Int): Int = when {
            combo >= 50 -> 4
            combo >= 25 -> 3
            combo >= 10 -> 2
            else -> 1
        }

        /** 3 stars from 90% accuracy, 2 from 70%, otherwise 1 for finishing. */
        fun starsFor(accuracyPercent: Double): Int = when {
            accuracyPercent >= 90.0 -> 3
            accuracyPercent >= 70.0 -> 2
            else -> 1
        }
    }
}

/** Result of one judgment, for the floating feedback text ("Perfect", "x2"). */
data class ScoreEvent(
    val judgment: Judgment,
    val points: Int,
    val multiplier: Int,
    val multiplierChanged: Boolean,
    val comboBroken: Boolean,
)

data class GameResult(
    val score: Long,
    val maxCombo: Int,
    val perfect: Int,
    val good: Int,
    val miss: Int,
    val accuracyPercent: Double,
    val stars: Int,
    val isNewHighScore: Boolean,
)
