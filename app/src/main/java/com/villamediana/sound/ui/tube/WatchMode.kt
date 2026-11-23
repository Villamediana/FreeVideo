package com.villamediana.sound.ui.tube

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Full screen, for every watch page at once (it lives in the shell, so going from one video to the
 * next — a suggestion, autoplay — stays full screen). A wide video goes sideways; a vertical one
 * (Shorts) fills the upright screen instead ([tall]). Leaving the watch pages puts everything back.
 */
@Stable
internal class WatchMode(activity: Activity?) {
    /** Upright full screen (a vertical video). */
    var tall by mutableStateOf(false)
        private set

    /** Full screen fills the whole screen (pinched open), for the next videos too, until pinched back. */
    var fill by mutableStateOf(false)

    val turn = WatchTurn(activity)

    /** Full screen for a video of this [ratio] (width / height). */
    fun enter(ratio: Float) {
        if (ratio > 0f && ratio < 0.9f) tall = true else turn.landscape()
    }

    fun exit() {
        if (tall) tall = false else turn.portrait()
    }

    fun toggle(ratio: Float, landscape: Boolean) {
        if (landscape || tall) exit() else enter(ratio)
    }

    /** Off the watch pages: upright full screen off, the phone decides the orientation again. */
    fun leave() {
        tall = false
        turn.release()
    }
}

internal val LocalWatchMode = staticCompositionLocalOf<WatchMode> { WatchMode(null) }

/**
 * The shell's side of full screen: follows the phone's tilt, hides the system bars while a watch page
 * is full screen (bars come back with a swipe from the edge), and lets go when no watch page is shown.
 */
@Composable
internal fun WatchModeEffects(activity: Activity?, mode: WatchMode, onWatch: Boolean, landscape: Boolean) {
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        mode.turn.start()
        onDispose {
            mode.turn.stop()
            if (activity?.isChangingConfigurations == true) return@onDispose
            controller?.show(WindowInsetsCompat.Type.systemBars())
            previousBehavior?.let { controller.systemBarsBehavior = it }
        }
    }
    LaunchedEffect(onWatch) { if (!onWatch) mode.leave() }
    val full = onWatch && (landscape || mode.tall)
    DisposableEffect(activity, view, full) {
        val window = activity?.window ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        if (full) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
}

/**
 * How the watch page turns. It follows the phone as the rest of the app does (and the system's
 * rotation lock); the full-screen button and a swipe up turn it sideways, and it stays sideways until
 * the person comes back (button, swipe down, back). Back upright, the phone decides again.
 */
internal class WatchTurn(private val activity: Activity?) {
    private var previous: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var listener: OrientationEventListener? = null

    fun start() {
        val host = activity ?: return
        previous = host.requestedOrientation
    }

    fun stop() {
        unwatch()
        activity?.let { if (!it.isChangingConfigurations) it.requestedOrientation = previous }
    }

    fun landscape() {
        unwatch()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    /** Upright now; once the phone itself is held upright, it decides again (the tilt is watched only until then). */
    fun portrait() {
        val host = activity ?: return
        host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (listener != null) return
        listener = object : OrientationEventListener(host) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                if (degrees <= 30 || degrees >= 330) {
                    unwatch()
                    host.requestedOrientation = previous
                }
            }
        }.also { if (it.canDetectOrientation()) it.enable() else host.requestedOrientation = previous }
    }

    /** The phone decides again (no watch page on screen). */
    fun release() {
        unwatch()
        val host = activity ?: return
        if (host.requestedOrientation != previous) host.requestedOrientation = previous
    }

    fun flip() {
        val host = activity ?: return
        if (host.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) portrait() else landscape()
    }

    private fun unwatch() {
        listener?.disable()
        listener = null
    }
}
