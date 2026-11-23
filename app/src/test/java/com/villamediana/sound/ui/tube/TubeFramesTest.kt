package com.villamediana.sound.ui.tube

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.data.ThemeMode
import com.villamediana.sound.tube.data.Subscription
import com.villamediana.sound.ui.nav.Inbound
import com.villamediana.sound.ui.theme.SoundTheme
import kotlinx.coroutines.flow.MutableStateFlow
import coil3.request.allowHardware
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Real frames of the app, to look at the design without a phone: with FRAMES_DIR set (and
 * SOUND_LIVE=1), the pages are drawn with real YouTube data and saved there as PNGs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TubeFramesTest {
    @get:Rule
    val rule = createComposeRule()

    private val dir: String = System.getenv("FRAMES_DIR").orEmpty()
    private fun str(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Lets the network and the images arrive (real time; the frame clock keeps running). */
    private fun settle(ms: Long) {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) {
            rule.mainClock.advanceTimeBy(100)
            rule.waitForIdle()
            Thread.sleep(100)
        }
    }

    @Test
    fun frames() {
        assumeTrue(System.getProperty("sound.live") == "1" && dir.isNotBlank())
        File(dir).mkdirs()
        val tube = SoundApplication.graph.tube
        runBlocking {
            // A person with some history, so the pages have something to show.
            val watched = tube.source.searchVideos("receita de pão caseiro").take(3) + tube.source.searchVideos("documentário astronomia").take(3)
            watched.forEachIndexed { i, v -> tube.library.recordWatch(v, v.durationMs * (if (i % 2 == 0) 9 else 4) / 10, v.durationMs, v.durationMs * (if (i % 2 == 0) 9 else 4) / 10, newVisit = true) }
            watched.take(2).forEach { tube.library.toggleLike(it) }
            watched.drop(2).take(2).forEach { tube.library.toggleLater(it) }
            watched.take(4).forEach { v -> tube.library.importSubscriptions(listOf(Subscription(v.channelUrl!!, v.channelName, v.channelAvatar, System.currentTimeMillis()))) }
            tube.library.addSearch("pão de fermentação natural")
            tube.settings.setInterestsAsked(true)
        }
        // Software bitmaps: the frame is drawn on a plain canvas, which can't paint hardware ones.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        coil3.SingletonImageLoader.setUnsafe(
            coil3.ImageLoader.Builder(context)
                .components { add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = { SoundApplication.graph.http })) }
                .allowHardware(false)
                .build(),
        )
        val theme = if (System.getenv("FRAMES_THEME") == "light") ThemeMode.LIGHT else ThemeMode.DARK
        rule.setContent { SoundTheme(mode = theme, accent = "ink") { TubeRoot(MutableStateFlow<Inbound?>(null)) } }
        settle(9_000)
        shot("01-home")
        rule.onRoot().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.25f, durationMillis = 400) }
        settle(2_500)
        shot("02-home-scrolled")

        rule.onAllNodesWithContentDescription(str(R.string.tube_tab_subs)).onFirst().performClick()
        settle(6_000)
        shot("03-subs")

        rule.onAllNodesWithContentDescription(str(R.string.tube_tab_library)).onFirst().performClick()
        settle(3_000)
        shot("04-library")

        rule.onAllNodesWithContentDescription(str(R.string.tube_settings)).onFirst().performClick()
        settle(1_500)
        shot("05-settings")
        rule.onRoot().performTouchInput { swipeUp(startY = height * 0.85f, endY = height * 0.15f, durationMillis = 300) }
        settle(800)
        shot("05b-settings-more")
        rule.onRoot().performTouchInput { swipeUp(startY = height * 0.85f, endY = height * 0.15f, durationMillis = 300) }
        settle(800)
        shot("05c-settings-end")
        rule.onAllNodesWithContentDescription(str(R.string.tube_back)).onFirst().performClick()
        settle(800)

        rule.onAllNodesWithContentDescription(str(R.string.tube_search)).onFirst().performClick()
        settle(1_500)
        shot("06-search")
        rule.onAllNodesWithText("pão de fermentação natural").onFirst().performClick()
        settle(6_000)
        shot("07-search-results")
        rule.onAllNodesWithContentDescription(str(R.string.tube_back)).onFirst().performClick()
        settle(800)

        // A channel, from the subscriptions list.
        rule.onAllNodesWithText(str(R.string.tube_your_subs)).onFirst().performClick()
        settle(1_500)
        val name = runBlocking { tube.library.snapshot().subscriptions.first().name }
        rule.onAllNodesWithText(name).onFirst().performClick()
        settle(7_000)
        shot("08-channel")
        rule.onRoot().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.2f, durationMillis = 400) }
        settle(2_500)
        shot("08b-channel-scrolled")
        rule.onAllNodesWithText(str(R.string.tube_channel_shorts)).fetchSemanticsNodes().firstOrNull()?.let {
            rule.onAllNodesWithText(str(R.string.tube_channel_shorts)).onFirst().performClick()
            settle(5_000)
            shot("08c-channel-shorts")
        }
        rule.onAllNodesWithContentDescription(str(R.string.tube_back)).onFirst().performClick()
        settle(600)
        rule.onAllNodesWithContentDescription(str(R.string.tube_back)).onFirst().performClick()
        settle(600)

        // The watch page, from the history shelf.
        val title = runBlocking { tube.library.snapshot().history.first().title }
        rule.onAllNodesWithText(title).onFirst().performClick()
        settle(7_000)
        shot("09-watch")
        // The speed, from its pill over the video (when the video plays here).
        rule.onAllNodesWithContentDescription(str(R.string.tube_speed), substring = true).fetchSemanticsNodes().firstOrNull()?.let {
            rule.onAllNodesWithContentDescription(str(R.string.tube_speed), substring = true).onFirst().performClick()
            settle(800)
            shot("09a-speed")
            rule.onRoot().performClick()
            settle(600)
        }
        rule.onRoot().performTouchInput { swipeUp(startY = height * 0.9f, endY = height * 0.55f, durationMillis = 300) }
        settle(1_000)
        shot("09b-watch-scrolled")
        rule.onAllNodesWithText(str(R.string.tube_comments)).fetchSemanticsNodes().firstOrNull()?.let {
            rule.onAllNodesWithText(str(R.string.tube_comments)).onFirst().performClick()
            settle(2_500)
            shot("09c-comments")
            if (rule.onAllNodesWithText("resposta", substring = true).fetchSemanticsNodes().isNotEmpty()) {
                rule.onAllNodesWithText("resposta", substring = true).onFirst().performClick()
                settle(3_000)
                shot("09d-replies")
            }
            // The sheet opens only when the comments arrived (live data): close it if it's there.
            if (rule.onAllNodesWithContentDescription(str(R.string.tube_close)).fetchSemanticsNodes().isNotEmpty()) {
                rule.onAllNodesWithContentDescription(str(R.string.tube_close)).onFirst().performClick()
                settle(800)
            }
        }
        rule.onAllNodesWithContentDescription(str(R.string.tube_minimize)).fetchSemanticsNodes().firstOrNull() ?: rule.onRoot().performClick()
        settle(600)
        rule.onAllNodesWithContentDescription(str(R.string.tube_minimize)).onFirst().performClick()
        settle(1_500)
        shot("10-mini-player")
    }
}
