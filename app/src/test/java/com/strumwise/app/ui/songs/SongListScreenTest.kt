package com.strumwise.app.ui.songs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.strumwise.app.nav.GameRoute
import com.strumwise.app.ui.theme.StrumwiseTheme
import com.strumwise.data.scores.SongBest
import com.strumwise.game.ChartEvent
import com.strumwise.game.Difficulty
import com.strumwise.game.EventType
import com.strumwise.game.SongChart
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h400dp-land")
class SongListScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun chart(id: String, title: String, difficulty: Difficulty) = SongChart(
        id = id, title = title, bpm = 90f, difficulty = difficulty,
        events = (0 until 16).map { ChartEvent(it * 667L, EventType.STRUM, if (it % 2 == 0) "G" else "C") },
    )

    private val state = SongListState(
        loading = false,
        songs = listOf(
            SongItem(chart("a", "Amazing Grace", Difficulty.EASY), SongBest(4200, 3, 95.0, 40, 3)),
            SongItem(chart("b", "Greensleeves", Difficulty.HARD), null),
        ),
    )

    @Test fun listsSongsWithDifficultyAndBest() {
        compose.setContent { StrumwiseTheme { SongListContent(state, {}, {}) } }
        compose.onNodeWithText("Amazing Grace").assertIsDisplayed()
        compose.onNodeWithText("Easy").assertIsDisplayed()
        compose.onNodeWithText("Hard").assertIsDisplayed()
        compose.onNodeWithText("Best 4,200").assertIsDisplayed()
        compose.onNodeWithText("Not played").assertIsDisplayed()
        compose.onNodeWithContentDescription("Best: 3 of 3 stars").assertIsDisplayed()
    }

    @Test fun choosingASongAndPlayingAtFullSpeed() {
        var launched: GameRoute? = null
        compose.setContent { StrumwiseTheme { SongListContent(state, {}, { launched = it }) } }
        compose.onNodeWithText("Greensleeves").performClick()
        compose.onNodeWithText("Play").performClick()
        assertThat(launched).isEqualTo(GameRoute("b", 1f, -1, -1))
    }

    @Test fun practiceAtHalfSpeed() {
        var launched: GameRoute? = null
        compose.setContent { StrumwiseTheme { SongListContent(state, {}, { launched = it }) } }
        compose.onNodeWithText("Amazing Grace").performClick()
        compose.onNodeWithText("50%").performClick()
        compose.onNodeWithText("Practice runs don't count toward high scores.").assertIsDisplayed()
        compose.onNodeWithText("Play").performClick()
        assertThat(launched?.speed).isEqualTo(0.5f)
    }
}
