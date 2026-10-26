package com.strumwise.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.strumwise.app.ui.guitar.PlayedNote
import com.strumwise.app.ui.guitar.StringHit
import com.strumwise.app.ui.play.PlayContent
import com.strumwise.app.ui.play.PlayUiState
import com.strumwise.app.ui.theme.StrumwiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** On-device version of the play-screen test: real touch pipeline and rendering. */
@RunWith(AndroidJUnit4::class)
class PlayScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun swipeStrumsEveryString() {
        val hits = mutableListOf<StringHit>()
        compose.setContent {
            StrumwiseTheme {
                PlayContent(
                    state = PlayUiState(),
                    onBack = {},
                    onChordTapped = {},
                    onChordLongPressed = {},
                    onToggleFreeFret = {},
                    onStringHits = { h -> hits += h; h.map { PlayedNote(it.string, 0, it.velocity, it.delayMs, it.direction) } },
                    onFretDown = { _, s, f -> PlayedNote(s, f, 0.7f, 0f, null) },
                    onFretUp = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Chord G").assertIsDisplayed()
        compose.onNodeWithTag("guitar").performTouchInput { swipeDown(startY = 2f, endY = height - 2f, durationMillis = 120) }
        assertEquals(listOf(0, 1, 2, 3, 4, 5), hits.map { it.string }.distinct())
    }
}
