package com.strumwise.app.songs

import android.content.Context
import android.util.Log
import com.strumwise.game.ChartException
import com.strumwise.game.ChartParser
import com.strumwise.game.SongChart
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Loads every chart in assets/songs/ once. Invalid charts are logged and skipped. */
@Singleton
class SongCatalog @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    private var cache: List<SongChart>? = null

    suspend fun all(): List<SongChart> = mutex.withLock {
        cache ?: withContext(Dispatchers.IO) { load() }.also { cache = it }
    }

    suspend fun byId(id: String): SongChart? = all().firstOrNull { it.id == id }

    private fun load(): List<SongChart> {
        val assets = context.assets
        return assets.list(DIR).orEmpty().filter { it.endsWith(".json") }.mapNotNull { name ->
            try {
                ChartParser.parse(assets.open("$DIR/$name").bufferedReader().use { it.readText() })
            } catch (e: ChartException) {
                Log.e(TAG, "Skipping $name: ${e.message}")
                null
            }
        }.sortedWith(compareBy({ it.difficulty }, { it.title }))
    }

    private companion object {
        const val DIR = "songs"
        const val TAG = "SongCatalog"
    }
}
