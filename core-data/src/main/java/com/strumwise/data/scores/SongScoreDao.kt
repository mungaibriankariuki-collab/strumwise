package com.strumwise.data.scores

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SongScoreDao {
    @Query("SELECT * FROM song_scores")
    fun observeAll(): Flow<List<SongScoreEntity>>

    @Query("SELECT * FROM song_scores WHERE songId = :songId")
    suspend fun get(songId: String): SongScoreEntity?

    @Upsert
    suspend fun upsert(score: SongScoreEntity)

    @Query("DELETE FROM song_scores")
    suspend fun clear()
}
