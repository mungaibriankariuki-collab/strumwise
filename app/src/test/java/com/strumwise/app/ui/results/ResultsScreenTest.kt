package com.strumwise.app.ui.results

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.strumwise.app.nav.ResultsRoute
import com.strumwise.app.ui.theme.StrumwiseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h400dp-land")
class ResultsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun route(newHigh: Boolean, practice: Boolean = false) = ResultsRoute(
        songId = "amazing_grace", title = "Amazing Grace", score = 12_340, maxCombo = 31, perfect = 40, good = 6,
        miss = 2, accuracy = 89.6f, stars = 2, newHighScore = newHigh, practice = practice, speed = if (practice) 0.5f else 1f,
    )

    @Test fun showsStatsAndStars() {
        compose.setContent { StrumwiseTheme { ResultsScreen(route(newHigh = false), "Amazing Grace", {}, {}) } }
        compose.onNodeWithText("Amazing Grace").assertIsDisplayed()
        compose.onNodeWithContentDescription("2 of 3 stars").assertIsDisplayed()
        compose.onNodeWithContentDescription("Score 12340").assertIsDisplayed()
        compose.onNodeWithText("89.6%").assertIsDisplayed()
        compose.onNodeWithText("31").assertIsDisplayed()
        compose.onNodeWithText("New high score!").assertDoesNotExist()
    }

    @Test fun celebratesNewHighScore() {
        compose.setContent { StrumwiseTheme { ResultsScreen(route(newHigh = true), "Amazing Grace", {}, {}) } }
        compose.onNodeWithText("New high score!").assertIsDisplayed()
    }

    @Test fun practiceRunsAreLabelled() {
        compose.setContent { StrumwiseTheme { ResultsScreen(route(newHigh = false, practice = true), "Amazing Grace", {}, {}) } }
        compose.onNodeWithText("Practice run · 50% — not saved as a best").assertIsDisplayed()
    }

    @Test fun buttons() {
        var retries = 0
        var songs = 0
        compose.setContent { StrumwiseTheme { ResultsScreen(route(false), "Amazing Grace", { retries++ }, { songs++ }) } }
        compose.onNodeWithText("Play again").performClick()
        compose.onNodeWithText("Song list").performClick()
        assertThat(retries).isEqualTo(1)
        assertThat(songs).isEqualTo(1)
    }
}
