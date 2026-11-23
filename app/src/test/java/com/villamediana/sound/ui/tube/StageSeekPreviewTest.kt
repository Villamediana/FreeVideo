package com.villamediana.sound.ui.tube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.villamediana.sound.data.ThemeMode
import com.villamediana.sound.ui.theme.SoundTheme
import com.villamediana.sound.ui.video.VideoProgress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sliding sideways over the video in full screen: the frame shows in the middle, and the time and the
 * seek bar go along with the finger (before, the bar stayed at the old spot until the finger let go).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StageSeekPreviewTest {
    @get:Rule
    val rule = createComposeRule()

    private val tenMinutes = object : VideoProgress {
        override val positionMs = 60_000L
        override val durationMs = 600_000L
        override val bufferedMs = 90_000L
    }

    @Test
    fun theBarAndTheTimeFollowTheSlide() {
        var preview by mutableStateOf<Long?>(null)
        rule.setContent {
            SoundTheme(mode = ThemeMode.DARK) {
                StageTimes(tenMinutes, pending = null, previewMs = preview)
                StageSeekBar(tenMinutes, pending = null, onSeek = {}, onScrub = {}, previewMs = preview)
            }
        }
        // Not sliding: where the video is.
        rule.onNodeWithText("1:00 / 10:00").assertExists()
        rule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.1f, 0f..1f))).assertExists()

        // Sliding: the time and the bar are at the finger's spot, and follow it as it moves.
        preview = 300_000L
        rule.waitForIdle()
        rule.onNodeWithText("5:00 / 10:00").assertExists()
        rule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f))).assertExists()

        preview = 450_000L
        rule.waitForIdle()
        rule.onNodeWithText("7:30 / 10:00").assertExists()
        rule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.75f, 0f..1f))).assertExists()

        // Let go: back to the video's own position (the seek itself is the screen's job).
        preview = null
        rule.waitForIdle()
        rule.onNodeWithText("1:00 / 10:00").assertExists()
    }
}
