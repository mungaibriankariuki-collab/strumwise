package com.strumwise.data.scores

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScoreRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrumwiseDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repo = ScoreRepository(db)

    @After fun close() = db.close()

    private fun run(score: Long, stars: Int = 2, acc: Double = 80.0, combo: Int = 10, best: Boolean = true) =
        RunRecord(score, stars, acc, combo, best)

    @Test fun firstRunIsANewHighScore() = runTest {
        assertThat(repo.record("song", run(1000))).isTrue()
        assertThat(repo.best("song")).isEqualTo(SongBest(1000, 2, 80.0, 10, 1))
    }

    @Test fun onlyHigherScoresAreNewHighs() = runTest {
        repo.record("song", run(1000))
        assertThat(repo.record("song", run(900, stars = 3, acc = 95.0, combo = 30))).isFalse()
        // Each "best" field keeps its own maximum.
        assertThat(repo.best("song")).isEqualTo(SongBest(1000, 3, 95.0, 30, 2))
        assertThat(repo.record("song", run(1001))).isTrue()
        assertThat(repo.best("song")!!.highScore).isEqualTo(1001)
    }

    @Test fun practiceRunsCountPlaysOnly() = runTest {
        assertThat(repo.record("song", run(5000, best = false))).isFalse()
        assertThat(repo.best("song")).isEqualTo(SongBest(0, 0, 0.0, 0, 1))
        repo.record("song", run(100))
        assertThat(repo.record("song", run(9999, best = false))).isFalse()
        assertThat(repo.best("song")!!.highScore).isEqualTo(100)
        assertThat(repo.best("song")!!.plays).isEqualTo(3)
    }

    @Test fun zeroScoreIsNeverAHighScore() = runTest {
        assertThat(repo.record("song", run(0, stars = 1, acc = 0.0, combo = 0))).isFalse()
    }

    @Test fun bestsFlowPerSong() = runTest {
        repo.record("a", run(10))
        repo.record("b", run(20))
        val bests = repo.bests.first()
        assertThat(bests.keys).containsExactly("a", "b")
        assertThat(bests.getValue("b").highScore).isEqualTo(20)
        assertThat(repo.best("missing")).isNull()
    }
}
