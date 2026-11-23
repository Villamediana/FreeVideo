package com.villamediana.sound.ui.tube

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The mini player pushed off to a side: it slides away and is gone. Before, once off the screen the bar
 * snapped back to its place while the video was still there, so the video showed at its old spot for a
 * moment before closing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BarSwipeAwayTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun swipedAwayItNeverComesBackToItsPlace() {
        lateinit var sheet: WatchSheet
        // Every state the screen could draw: the video on screen and how far the bar is pushed.
        val seen = mutableListOf<Pair<Boolean, Float>>()
        rule.setContent {
            val scope = rememberCoroutineScope()
            sheet = androidx.compose.runtime.remember { WatchSheet(scope).also { it.travelWidth = 1000f } }
            LaunchedEffect(sheet) {
                snapshotFlow { (sheet.route != null) to sheet.barShift.value }.collect { seen += it }
            }
        }
        rule.runOnIdle { sheet.open(WatchRoute(id = "abc", title = "Um vídeo"), from = null) }
        rule.waitForIdle()
        rule.runOnIdle { sheet.collapse() }
        rule.waitForIdle()

        // The finger pushes the bar to the right, past a third, and lets go.
        rule.runOnIdle { sheet.shiftTo(420f) }
        rule.waitForIdle()
        var routeWhenClosed: WatchRoute? = WatchRoute(id = "not called")
        rule.runOnIdle { sheet.shiftAway(right = true) { routeWhenClosed = sheet.route } }
        rule.waitForIdle()

        // The page was already gone when the player was told to close…
        assertNull(routeWhenClosed)
        assertNull(sheet.route)
        // …and from the push on, the video was never on screen back at its place.
        val pushed = seen.indexOfFirst { it.second > 0f }
        assertTrue("the bar moved", pushed >= 0)
        val back = seen.drop(pushed).filter { (onScreen, shift) -> onScreen && shift == 0f }
        assertEquals("on screen at its old place after the push: $seen", emptyList<Pair<Boolean, Float>>(), back)
        // At rest for the next video.
        assertEquals(0f, sheet.barShift.value)
        assertEquals(0f, sheet.expansion.value)
    }
}
