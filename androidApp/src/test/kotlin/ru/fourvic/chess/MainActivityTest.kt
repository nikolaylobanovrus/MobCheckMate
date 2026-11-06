package ru.fourvic.chess

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The real activity starts with its AppCompat theme and, on a phone set to Russian, picks English as the app
 * language on the first launch. (The system then recreates the activity in English; Robolectric does not, so the
 * screen is checked by the language names, which look the same in every language.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru")
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchShowsTheWelcomeScreenAndChoosesEnglish() {
        compose.onNodeWithText("English").assertExists()
        compose.onNodeWithText("Русский").assertExists()
        compose.onNodeWithText("中文").assertExists()
        assertEquals("en", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }
}
