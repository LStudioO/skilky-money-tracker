package com.vstorchevyi.skilky

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppTest {
    @Test
    fun `renders the click-me button`() =
        runComposeUiTest {
            setContent { App() }
            onNodeWithText("Click me!").assertIsDisplayed()
        }

    @Test
    fun `clicking the button reveals the compose greeting`() =
        runComposeUiTest {
            setContent { App() }
            onNodeWithText("Click me!").performClick()
            waitForIdle()
            onNodeWithText("Compose: ", substring = true).assertIsDisplayed()
        }
}
