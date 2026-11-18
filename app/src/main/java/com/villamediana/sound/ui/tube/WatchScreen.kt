package com.villamediana.sound.ui.tube

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.OrientationEventListener
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import com.villamediana.sound.ui.theme.SoundColors
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.state.rememberPresentationState
import coil3.compose.AsyncImage
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.player.TubePlayer
import com.villamediana.sound.ui.components.PillButton
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.Motion
import com.villamediana.sound.ui.theme.sc
import com.villamediana.sound.ui.video.OverlayState
import com.villamediana.sound.ui.video.PendingSeek
import com.villamediana.sound.ui.video.SeekHint
import com.villamediana.sound.ui.video.SeekSide
import com.villamediana.sound.ui.video.SeekStreak
import com.villamediana.sound.ui.video.StagePill
import com.villamediana.sound.ui.video.StageSpinner
import com.villamediana.sound.ui.video.VideoIconButton
import com.villamediana.sound.ui.video.VideoPlayPause
import com.villamediana.sound.ui.video.VideoProgress
import com.villamediana.sound.ui.video.VideoSeek
import com.villamediana.sound.ui.video.VideoSeekBar
import com.villamediana.sound.ui.video.rememberVideoProgress
import com.villamediana.sound.ui.video.safeAspectRatio
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * The watch page. Upright: the video on top (FreeSong's stage: tap for the controls, double-tap a
 * side to jump 10 s, hold for 2×, swipe up for full screen and down to minimize), the seek bar, and
 * below it the title, actions, channel, description, comments and what comes next. Sideways: the
 * video alone, full screen. Leaving keeps the video going in the mini player.
 */
@OptIn(UnstableApi::class)
@Composable
fun WatchScreen(route: WatchRoute) {
    val tube = SoundApplication.graph.tube
    val player = tube.player
    val nav = LocalTubeNav.current
    val vm: WatchViewModel = viewModel(key = "watch:" + route.id) { WatchViewModel(route.video()) }
    val pState by player.state.collectAsState()
    val exo by player.player.collectAsState()
    val activity = LocalActivity.current
    val haptic = LocalHapticFeedback.current

    // Open (or carry on with) this video. When the player moves on by itself (autoplay), the page follows.
    var followed by rememberSaveable(route.id) { mutableStateOf(false) }
    LaunchedEffect(route.id) {
        player.open(route.video(), route.startMs.takeIf { it >= 0 }, route.src)
    }
    LaunchedEffect(pState.video?.id) {
        val now = pState.video ?: return@LaunchedEffect
        if (now.id == route.id) followed = true
        else if (followed) nav.openVideo(now)
    }

    val turn = remember(activity) { WatchTurn(activity) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    WatchWindow(activity, turn, landscape)
    BackHandler(enabled = landscape) { turn.portrait() }

    val flags = rememberFlags(exo)
    val progress = rememberVideoProgress(exo)
    val error = pState.error
    val loading = error == null && (pState.loading || flags.buffering) && pState.countdown == null

    var overlay by remember { mutableStateOf(OverlayState(visible = true, touchedAtMs = now())) }
    var scrubbing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingSeek?>(null) }
    var streak by remember { mutableStateOf<SeekStreak?>(null) }
    var tapPoint by remember { mutableStateOf<Offset?>(null) }
    var holding by remember { mutableStateOf<SeekSide?>(null) }
    var rewindJob by remember { mutableStateOf<Job?>(null) }
    var rewoundFromPlaying by remember { mutableStateOf(false) }
    var beforeTap by remember { mutableStateOf<OverlayState?>(null) }
    val scope = rememberCoroutineScope()
    val pinned = scrubbing || loading || error != null

    LaunchedEffect(overlay, pState.playing, pinned) {
        val at = overlay.hideAtMs(pState.playing, pinned) ?: return@LaunchedEffect
        delay((at - now()).coerceAtLeast(0))
        overlay = overlay.hide()
    }
    LaunchedEffect(streak) {
        if (streak == null) return@LaunchedEffect
        delay(VideoSeek.STREAK_WINDOW_MS + 120)
        streak = null
    }
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        delay(PendingSeek.HOLD_MS + 100)
        pending = null
    }

    val actions = remember(player) {
        object : WatchActions {
            override fun toggleControls() {
                overlay = overlay.toggle(now())
            }

            override fun playPause() {
                overlay = overlay.touch(now())
                if (flags.ended) {
                    player.seekTo(0)
                    player.play()
                } else {
                    player.togglePlayPause()
                }
            }

            override fun seekTo(positionMs: Long) {
                player.seekTo(positionMs)
                pending = PendingSeek(positionMs, now())
                overlay = overlay.touch(now())
            }

            override fun scrub(active: Boolean) {
                scrubbing = active
                overlay = overlay.touch(now())
            }

            override fun seekTap(side: SeekSide, at: Offset) {
                val p = exo ?: return
                val t = now()
                val next = VideoSeek.tap(streak, side, t, p.currentPosition, p.duration)
                streak = next
                tapPoint = at
                player.seekTo(next.targetMs)
                pending = PendingSeek(next.targetMs, t)
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                if (overlay.visible) overlay = overlay.touch(t)
            }

            override fun stageTap(side: SeekSide, at: Offset) {
                if (VideoSeek.continues(streak, side, now())) {
                    seekTap(side, at)
                    return
                }
                beforeTap = overlay
                toggleControls()
            }

            override fun doubleTap(side: SeekSide, at: Offset) {
                beforeTap?.let { overlay = it }
                beforeTap = null
                seekTap(side, at)
            }

            override fun hold(active: Boolean, side: SeekSide) {
                if (active == (holding != null)) return
                holding = if (active) side else null
                rewindJob?.cancel()
                rewindJob = null
                if (!active) {
                    player.setSpeed(1f)
                    if (rewoundFromPlaying) player.play()
                    rewoundFromPlaying = false
                    return
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (side == SeekSide.FORWARD) {
                    player.setSpeed(2f)
                    return
                }
                val p = exo ?: return
                rewoundFromPlaying = pState.playing
                if (pState.playing) player.pause()
                rewindJob = scope.launch {
                    var at = p.currentPosition
                    while (at > 0) {
                        at = (at - 500).coerceAtLeast(0)
                        player.seekTo(at)
                        pending = PendingSeek(at, now())
                        delay(250)
                    }
                }
            }

            override fun swipe(up: Boolean) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                val sideways = activity?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
                when {
                    up -> turn.landscape()
                    sideways -> turn.portrait()
                    else -> nav.minimize()
                }
            }

            override fun skip(forward: Boolean) {
                val p = exo ?: return
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                seekTo(VideoSeek.target(p.currentPosition, p.duration, if (forward) SeekSide.FORWARD else SeekSide.BACK))
            }

            override fun rotate() = turn.flip()
            override fun retry() = player.retry()
            override fun minimize() = nav.minimize()
            override fun playNext() = player.playUpNext()
            override fun cancelNext() = player.cancelCountdown()
        }
    }

    val details by vm.state.collectAsState()
    val title = details.video?.title?.takeIf { it.isNotBlank() } ?: route.title

    BoxWithConstraints(Modifier.fillMaxSize().background(sc.Ink).then(if (pState.playing) Modifier.keepScreenOn() else Modifier)) {
        val ratio by animateFloatAsState(safeAspectRatio(pState.aspectRatio).coerceAtLeast(1f), Motion.soft(), label = "ratio")
        val stageHeight = min(maxWidth / ratio, maxHeight * 0.42f)
        Column(Modifier.fillMaxSize()) {
            if (!landscape) Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.Black))
            Stage(
                player = exo,
                ratio = ratio,
                poster = route.thumb ?: details.video?.thumbnailUrl,
                landscape = landscape,
                controlsVisible = overlay.visible || error != null,
                state = pState,
                ended = flags.ended,
                loading = loading,
                title = title,
                progress = progress,
                pending = pending,
                streak = streak,
                tapPoint = tapPoint,
                holding = holding,
                actions = actions,
                modifier = if (landscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(stageHeight),
            )
            if (!landscape) {
                // The video lends its color to the page: a soft veil under the stage that fades into the background.
                val veil = animatedVeil(rememberThumbColor(route.thumb ?: details.video?.thumbnailUrl))
                val ink = sc.Ink
                val wash = SoundColors.palette.washAlpha
                Column(
                    Modifier.weight(1f).fillMaxWidth().drawBehind {
                        drawRect(Brush.verticalGradient(listOf(lerp(ink, veil, wash * 0.75f), ink), endY = 300.dp.toPx()))
                    },
                ) {
                    if (!pState.video.let { it?.live == true }) {
                        VideoSeekBar(
                            progress = progress,
                            pending = pending,
                            onSeek = actions::seekTo,
                            onScrub = actions::scrub,
                            modifier = Modifier.padding(horizontal = Dimens.Gutter).padding(top = 10.dp),
                        )
                    }
                    WatchDetails(
                        route = route,
                        vm = vm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun now(): Long = SystemClock.uptimeMillis()

/** Everything the stage can ask for. */
internal interface WatchActions {
    fun toggleControls()
    fun playPause()
    fun seekTo(positionMs: Long)
    fun scrub(active: Boolean)
    fun seekTap(side: SeekSide, at: Offset)
    fun stageTap(side: SeekSide, at: Offset)
    fun doubleTap(side: SeekSide, at: Offset)
    fun hold(active: Boolean, side: SeekSide = SeekSide.FORWARD)
    fun swipe(up: Boolean)
    fun skip(forward: Boolean)
    fun rotate()
    fun retry()
    fun minimize()
    fun playNext()
    fun cancelNext()
}

// ------------------------------------------------------------------ stage

@OptIn(UnstableApi::class)
@Composable
private fun Stage(
    player: Player?,
    ratio: Float,
    poster: String?,
    landscape: Boolean,
    controlsVisible: Boolean,
    state: TubePlayer.State,
    ended: Boolean,
    loading: Boolean,
    title: String,
    progress: VideoProgress,
    pending: PendingSeek?,
    streak: SeekStreak?,
    tapPoint: Offset?,
    holding: SeekSide?,
    actions: WatchActions,
    modifier: Modifier = Modifier,
) {
    val error = state.error
    val toggleLabel = stringResource(if (controlsVisible) R.string.video_hide_controls else R.string.video_show_controls)
    val interactive = error == null && state.countdown == null
    Box(
        modifier
            .background(Color.Black)
            .clipToBounds()
            .semantics {
                onClick(label = toggleLabel) {
                    actions.toggleControls()
                    true
                }
            }
            .pointerInput(interactive, player != null) {
                if (!interactive) return@pointerInput
                stageGestures(canSeek = player != null, actions = actions)
            },
    ) {
        val coverSurface = player == null || rememberPresentationState(player).coverSurface
        Box(
            Modifier
                .matchParentSize()
                .then(if (landscape) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.aspectRatio(ratio)) {
                // In the floating window the window draws the video; this surface comes back after it.
                if (player != null && !LocalInPip.current) PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
                if (!poster.isNullOrBlank()) {
                    AnimatedVisibility(coverSurface && error == null, enter = fadeIn(tween(200)), exit = fadeOut(tween(420)), modifier = Modifier.matchParentSize()) {
                        AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.5f })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && state.countdown == null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(320)),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                if (landscape) {
                    Row(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(start = 8.dp, end = Dimens.Gutter, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VideoIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.tube_back), actions::rotate)
                        Spacer(Modifier.width(6.dp))
                        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    if (error == null && state.video?.live != true) {
                        VideoSeekBar(
                            progress = progress,
                            pending = pending,
                            onSeek = actions::seekTo,
                            onScrub = actions::scrub,
                            timeColor = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                                .padding(start = Dimens.Gutter, end = 56.dp)
                                .padding(bottom = 10.dp),
                        )
                    }
                } else {
                    VideoIconButton(
                        Icons.Rounded.KeyboardArrowDown,
                        stringResource(R.string.tube_minimize),
                        actions::minimize,
                        Modifier.align(Alignment.TopStart).padding(4.dp),
                    )
                    if (state.height > 0) {
                        Text(
                            stringResource(R.string.tube_quality_now, state.height),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                        )
                    }
                }
                if (error == null && player != null) {
                    Row(
                        Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (landscape) 48.dp else 32.dp),
                    ) {
                        VideoIconButton(Icons.Rounded.Replay10, stringResource(R.string.video_back_10), { actions.skip(false) })
                        Spacer(Modifier.size(if (landscape) 80.dp else 64.dp))
                        if (ended && state.upNext != null) {
                            VideoIconButton(Icons.Rounded.SkipNext, stringResource(R.string.tube_play_now), actions::playNext)
                        } else {
                            VideoIconButton(Icons.Rounded.Forward10, stringResource(R.string.video_forward_10), { actions.skip(true) })
                        }
                    }
                    VideoIconButton(
                        if (landscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        stringResource(if (landscape) R.string.video_exit_fullscreen else R.string.video_fullscreen),
                        actions::rotate,
                        Modifier
                            .align(Alignment.BottomEnd)
                            .then(if (landscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)) else Modifier)
                            .padding(end = 4.dp, bottom = if (landscape) 2.dp else 0.dp),
                    )
                }
                if (error == null && !loading) {
                    VideoPlayPause(
                        playing = state.playing,
                        ended = ended,
                        onClick = actions::playPause,
                        modifier = Modifier.align(Alignment.Center),
                        size = if (landscape) 80.dp else 64.dp,
                    )
                }
            }
        }

        SeekHint(streak, tapPoint)
        AnimatedVisibility(
            visible = holding != null,
            enter = fadeIn(tween(150)) + scaleIn(tween(200), initialScale = 0.85f),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
        ) {
            val back = holding == SeekSide.BACK
            Row(
                Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (back) {
                    Icon(Icons.Rounded.FastRewind, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text("2×", style = MaterialTheme.typography.labelLarge, color = Color.White)
                if (!back) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.FastForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        StageSpinner(loading, Modifier.align(Alignment.Center))
        state.countdown?.let { seconds -> UpNextOverlay(seconds, state.upNext?.title.orEmpty(), state.upNext?.thumbnailUrl, actions, landscape) }
        if (error != null) StageError(error, compact = !landscape, onRetry = actions::retry, modifier = Modifier.align(Alignment.Center))
    }
}

/** The end of a video with autoplay on: what's next and when, with "cancel" and "play now". */
@Composable
private fun BoxScope.UpNextOverlay(seconds: Int, title: String, thumb: String?, actions: WatchActions, landscape: Boolean) {
    Box(Modifier.matchParentSize()) {
        if (!thumb.isNullOrBlank()) {
            AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().graphicsLayer { alpha = 0.35f })
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.tube_next_in, seconds), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = if (landscape) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StagePill(stringResource(R.string.tube_cancel), Icons.Rounded.Refresh, actions::cancelNext)
                StagePill(stringResource(R.string.tube_play_now), Icons.Rounded.SkipNext, actions::playNext)
            }
        }
    }
}

@Composable
private fun StageError(message: String, compact: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 28.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!compact) {
            Icon(Icons.Rounded.VideocamOff, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(12.dp))
        }
        Text(stringResource(R.string.video_error_title), style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
        if (message.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
        StagePill(stringResource(R.string.video_retry), Icons.Rounded.Refresh, onRetry)
    }
}

// ------------------------------------------------------------------ player flags

private class Flags {
    var buffering by mutableStateOf(false)
    var ended by mutableStateOf(false)

    fun read(player: Player?) {
        val s = player?.playbackState
        buffering = s == Player.STATE_BUFFERING
        ended = s == Player.STATE_ENDED
    }
}

@Composable
private fun rememberFlags(player: Player?): Flags {
    val flags = remember { Flags() }
    DisposableEffect(player) {
        flags.read(player)
        val p = player ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = flags.read(p)
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }
    return flags
}

// ------------------------------------------------------------------ window

/** Sideways = full screen without system bars; everything is put back on the way out. */
@Composable
private fun WatchWindow(activity: Activity?, turn: WatchTurn, sideways: Boolean) {
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val window = activity?.window ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val previousBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        turn.start()
        onDispose {
            turn.stop()
            if (activity.isChangingConfigurations) return@onDispose
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previousBehavior
        }
    }
    DisposableEffect(activity, view, sideways) {
        val window = activity?.window ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        if (sideways) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
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
    private var releaseWhenUpright = false

    fun start() {
        val host = activity ?: return
        previous = host.requestedOrientation
        listener = object : OrientationEventListener(host) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN || !releaseWhenUpright) return
                if (degrees <= 30 || degrees >= 330) {
                    releaseWhenUpright = false
                    host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }.also { if (it.canDetectOrientation()) it.enable() }
    }

    fun stop() {
        listener?.disable()
        listener = null
        activity?.let { if (!it.isChangingConfigurations) it.requestedOrientation = previous }
    }

    fun landscape() {
        releaseWhenUpright = false
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun portrait() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        releaseWhenUpright = true
    }

    fun flip() {
        val host = activity ?: return
        if (host.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) portrait() else landscape()
    }
}

/**
 * Taps, double taps, holds and vertical swipes on the video, told apart by hand so a single tap
 * answers at once: tap = controls, second tap on a side = jump 10 s, held = 2× until lifted,
 * swipe up = full screen, swipe down = upright / minimize.
 */
private suspend fun PointerInputScope.stageGestures(canSeek: Boolean, actions: WatchActions) {
    val swipeMin = 56.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        val slop = viewConfiguration.touchSlop
        val deadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
        var last = down.uptimeMillis
        var dragging = false
        var held = false
        var dy = 0f
        var upAt: Offset? = null
        while (true) {
            val event = if (!dragging && !held) {
                withTimeoutOrNull((deadline - last).coerceAtLeast(1)) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }
            if (event == null) {
                held = true
                if (canSeek) actions.hold(true, VideoSeek.sideOf(down.position.x, size.width.toFloat()))
                continue
            }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            last = change.uptimeMillis
            if (!change.pressed) {
                upAt = change.position
                break
            }
            val d = change.position - down.position
            if (!dragging && !held && abs(d.y) > slop && abs(d.y) > abs(d.x)) dragging = true
            if (dragging) {
                dy = d.y
                change.consume()
            }
        }
        when {
            held -> if (canSeek) actions.hold(false)
            dragging -> if (abs(dy) >= swipeMin) actions.swipe(up = dy < 0)
            upAt != null -> {
                val width = size.width.toFloat()
                actions.stageTap(VideoSeek.sideOf(upAt.x, width), upAt)
                if (canSeek) {
                    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
                    if (second != null) {
                        actions.doubleTap(VideoSeek.sideOf(second.position.x, width), second.position)
                        waitForUpOrCancellation()
                    }
                }
            }
        }
    }
}

@Composable
internal fun RetryPill(onClick: () -> Unit) {
    PillButton(stringResource(R.string.tube_retry), onClick = onClick, icon = Icons.Rounded.Refresh)
}
