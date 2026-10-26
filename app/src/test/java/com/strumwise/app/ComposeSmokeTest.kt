package com.strumwise.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComposeSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun composeRendersUnderRobolectric() {
        compose.setContent { Text("Strumwise") }
        compose.onNodeWithText("Strumwise").assertIsDisplayed()
    }
}
