package com.strumwise.data.scores

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** What one finished run achieved. */
data class RunRecord(
    val score: Long,
    val stars: Int,
    val accuracyPercent: Double,
    val maxCombo: Int,
    /** Practice runs (slowed down or looped) count as plays but never set bests. */
    val countsForBest: Boolean,
)

data class SongBest(val highScore: Long, val stars: Int, val accuracyPercent: Double, val bestCombo: Int, val plays: Int)

@Singleton
class ScoreRepository @Inject constructor(private val db: StrumwiseDatabase) {
    private val dao = db.songScores()

    /** Best result per song id. Songs never played are absent. */
    val bests: Flow<Map<String, SongBest>> = dao.observeAll().map { rows -> rows.associate { it.songId to it.toBest() } }

    suspend fun best(songId: String): SongBest? = dao.get(songId)?.toBest()

    /** Saves a run and returns true if it set a new high score. */
    suspend fun record(songId: String, run: RunRecord, now: Long = System.currentTimeMillis()): Boolean =
        db.withTransaction {
            val old = dao.get(songId)
            val newHigh = run.countsForBest && run.score > 0 && (old == null || run.score > old.highScore)
            val updated = if (old == null) {
                SongScoreEntity(
                    songId = songId,
                    highScore = if (run.countsForBest) run.score else 0,
                    bestStars = if (run.countsForBest) run.stars else 0,
                    bestAccuracy = if (run.countsForBest) run.accuracyPercent else 0.0,
                    bestCombo = if (run.countsForBest) run.maxCombo else 0,
                    plays = 1,
                    lastPlayedAt = now,
                )
            } else if (run.countsForBest) {
                old.copy(
                    highScore = maxOf(old.highScore, run.score),
                    bestStars = maxOf(old.bestStars, run.stars),
                    bestAccuracy = maxOf(old.bestAccuracy, run.accuracyPercent),
                    bestCombo = maxOf(old.bestCombo, run.maxCombo),
                    plays = old.plays + 1,
                    lastPlayedAt = now,
                )
            } else {
                old.copy(plays = old.plays + 1, lastPlayedAt = now)
            }
            dao.upsert(updated)
            newHigh
        }

    private fun SongScoreEntity.toBest() = SongBest(highScore, bestStars, bestAccuracy, bestCombo, plays)
}
