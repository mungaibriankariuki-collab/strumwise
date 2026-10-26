package com.strumwise.data.scores

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SongScoreEntity::class], version = 1, exportSchema = true)
abstract class StrumwiseDatabase : RoomDatabase() {
    abstract fun songScores(): SongScoreDao
}
