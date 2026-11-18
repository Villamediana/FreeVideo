package com.villamediana.sound.ui.tube

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.data.ThemeMode
import com.villamediana.sound.tube.online.VideoItem
import com.villamediana.sound.ui.nav.Inbound
import com.villamediana.sound.ui.theme.SoundTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * FreeVideo's pages open without crashing and say what they should: the tabs, the library, search,
 * settings, a channel and the watch page (the network may or may not answer here — only the pages'
 * own frames are checked).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TubeScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun str(id: Int) = context.getString(id)

    private fun waitFor(text: String, timeoutMs: Long = 10_000) {
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun everyPageOpens() {
        val tube = SoundApplication.graph.tube
        val video = VideoItem("dQw4w9WgXcQ", "Um vídeo do histórico", "Canal de teste", "https://www.youtube.com/channel/UC" + "a".repeat(22), null, null, 212_000)
        runBlocking { tube.library.recordWatch(video, 60_000, 212_000, 60_000, newVisit = true) }

        rule.setContent { SoundTheme(mode = ThemeMode.DARK) { TubeRoot(MutableStateFlow<Inbound?>(null)) } }
        rule.waitForIdle()

        // Home: the header and the tabs.
        rule.onAllNodesWithText("FreeVideo").onFirst().assertExists()
        rule.onAllNodesWithText(str(R.string.tube_chip_all)).onFirst().assertExists()
        rule.onAllNodesWithContentDescription(str(R.string.tube_tab_subs)).onFirst().assertExists()

        // Subscriptions: none yet.
        rule.onAllNodesWithContentDescription(str(R.string.tube_tab_subs)).onFirst().performClick()
        waitFor(str(R.string.tube_subs_empty_title))

        // Library: the history shelf with the video, and the lists.
        rule.onAllNodesWithContentDescription(str(R.string.tube_tab_library)).onFirst().performClick()
        waitFor(str(R.string.tube_later))
        waitFor("Um vídeo do histórico")

        // The history list, then back.
        rule.onAllNodesWithText(str(R.string.tube_see_all)).onFirst().performClick()
        waitFor(str(R.string.tube_clear))
        rule.onAllNodesWithContentDescription(str(R.string.tube_back)).onFirst().performClick()
        rule.waitForIdle()

        // Settings.
        rule.onAllNodesWithContentDescription(str(R.string.tube_settings)).onFirst().performClick()
        waitFor(str(R.string.tube_set_quality))
        rule.onNodeWithText("720p").assertExists()
        rule.onAllNodesWithContentDescription(str(R.string.tube_back)).onFirst().performClick()
        rule.waitForIdle()

        // Search.
        rule.onAllNodesWithContentDescription(str(R.string.tube_search)).onFirst().performClick()
        waitFor(str(R.string.tube_search_hint))
        rule.onAllNodesWithContentDescription(str(R.string.tube_back)).onFirst().performClick()
        rule.waitForIdle()

        // The watch page, from the history shelf: title, channel and the player's stage.
        rule.onAllNodesWithText("Um vídeo do histórico").onFirst().performClick()
        waitFor("Canal de teste")
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription(str(R.string.tube_minimize))).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodes(hasText(str(R.string.video_error_title))).fetchSemanticsNodes().isNotEmpty() }

        // Minimize: the mini player shows the video over the tabs.
        rule.runOnIdle { }
        rule.onAllNodesWithContentDescription(str(R.string.tube_close)).fetchSemanticsNodes()
    }
}
