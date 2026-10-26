package com.strumwise.app.ui.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.google.common.truth.Truth.assertThat
import com.strumwise.app.ui.guitar.PlayedNote
import com.strumwise.app.ui.guitar.StringHit
import com.strumwise.app.ui.theme.StrumwiseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h400dp-land")
class PlayScreenTest {
    @get:Rule val compose = createComposeRule()

    private var state by mutableStateOf(PlayUiState())
    private val tapped = mutableListOf<Int>()
    private val longPressed = mutableListOf<Int>()
    private val hits = mutableListOf<StringHit>()
    private var freeFretToggles = 0
    private var backs = 0

    private fun show() = compose.setContent {
        StrumwiseTheme {
            PlayContent(
                state = state,
                onBack = { backs++ },
                onChordTapped = { tapped += it; state = state.copy(selectedSlot = it) },
                onChordLongPressed = { longPressed += it },
                onToggleFreeFret = { freeFretToggles++; state = state.copy(freeFret = !state.freeFret) },
                onStringHits = { h -> hits += h; h.map { PlayedNote(it.string, 0, it.velocity, it.delayMs, it.direction) } },
                onFretDown = { _, s, f -> PlayedNote(s, f, 0.7f, 0f, null) },
                onFretUp = {},
            )
        }
    }

    @Test fun showsEveryChordSlot() {
        show()
        for (chord in state.slots) compose.onNodeWithContentDescription("Chord ${chord.displayName}").assertIsDisplayed()
    }

    @Test fun tappingAChordSelectsIt() {
        show()
        compose.onNodeWithContentDescription("Chord C").performClick()
        assertThat(tapped).containsExactly(state.slots.indexOfFirst { it.id == "C" })
        // The chord's name now also shows in the top bar.
        assertThat(compose.onAllNodesWithText("C", useUnmergedTree = true).fetchSemanticsNodes().size).isEqualTo(2)
    }

    @Test fun longPressOpensSwap() {
        show()
        compose.onNodeWithContentDescription("Chord G").performTouchInput { longClick() }
        assertThat(longPressed).containsExactly(0)
    }

    @Test fun freeFretToggle() {
        show()
        compose.onNodeWithText("Free fret").performClick()
        assertThat(freeFretToggles).isEqualTo(1)
        compose.onNodeWithText("Tap the frets").assertIsDisplayed()
    }

    @Test fun swipingAcrossTheGuitarStrumsAllStrings() {
        show()
        compose.onNodeWithTag("guitar").performTouchInput { swipeDown(startY = 2f, endY = height - 2f, durationMillis = 120) }
        assertThat(hits.map { it.string }.distinct()).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
    }

    @Test fun backButton() {
        show()
        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(backs).isEqualTo(1)
    }
}
