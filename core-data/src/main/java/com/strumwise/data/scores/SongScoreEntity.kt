package com.strumwise.data.scores

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Best results and progress for one song. */
@Entity(tableName = "song_scores")
data class SongScoreEntity(
    @PrimaryKey val songId: String,
    val highScore: Long,
    val bestStars: Int,
    val bestAccuracy: Double,
    val bestCombo: Int,
    /** Every finished run counts, practice included. */
    val plays: Int,
    val lastPlayedAt: Long,
)
