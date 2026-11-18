package com.villamediana.sound.ui.tube

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import com.villamediana.sound.tube.player.TubePlayer

/**
 * "Janela flutuante": leaving the app (home, another app) with a video playing turns it into a small
 * window over everything, which can be moved, resized or closed. Closing it stops the video.
 */
object Pip {
    fun supported(activity: Activity): Boolean =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** The window's shape and whether leaving the app enters it by itself (Android 12+). */
    fun params(state: TubePlayer.State, enabled: Boolean): PictureInPictureParams {
        val ratio = state.aspectRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
        // Android refuses ratios beyond 2.39:1 either way.
        val clamped = ratio.coerceIn(1 / 2.39f, 2.39f)
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational((clamped * 1000).toInt(), 1000))
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(enabled && wanted(state))
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    /** A video is playing (not paused, not ended, not an error): worth a floating window. */
    fun wanted(state: TubePlayer.State): Boolean = state.active && state.playing && state.error == null

    /** Android 8–11: entered by hand when the person leaves the app ([ComponentActivity.onUserLeaveHint]). */
    fun enterIfWanted(activity: Activity, state: TubePlayer.State, enabled: Boolean) {
        if (!enabled || !wanted(state) || !supported(activity)) return
        runCatching { activity.enterPictureInPictureMode(params(state, enabled)) }
    }
}

/** Whether the app is a floating window now (the pages hide, only the video shows). */
val LocalInPip = staticCompositionLocalOf { false }

/** Follows the window mode, and stops the video when the window is closed (swiped away / ×). */
@Composable
fun rememberPipMode(activity: ComponentActivity?, player: TubePlayer): Boolean {
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val host = activity ?: return@DisposableEffect onDispose { }
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
            // Out of the window without coming back to the app: it was closed.
            if (!info.isInPictureInPictureMode && !host.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) player.pause()
        }
        host.addOnPictureInPictureModeChangedListener(listener)
        onDispose { host.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/** Keeps the window's shape and auto-enter in step with what plays. */
@Composable
fun PipParams(activity: Activity?, state: TubePlayer.State, enabled: Boolean) {
    LaunchedEffect(activity, state.aspectRatio, state.playing, state.active, state.error, enabled) {
        val host = activity ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT < 26 || !Pip.supported(host)) return@LaunchedEffect
        runCatching { host.setPictureInPictureParams(Pip.params(state, enabled)) }
    }
}

/** What the floating window shows: the video alone, letterboxed on black. */
@OptIn(UnstableApi::class)
@Composable
fun PipStage(player: TubePlayer) {
    val exo by player.player.collectAsState()
    val state by player.state.collectAsState()
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        exo?.let { p ->
            Box(Modifier.aspectRatio(state.aspectRatio.takeIf { it > 0f } ?: (16f / 9f))) {
                PlayerSurface(player = p, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
