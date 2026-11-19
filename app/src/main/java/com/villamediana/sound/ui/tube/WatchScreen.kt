package com.villamediana.sound.ui.tube

import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.state.rememberPresentationState
import coil3.compose.AsyncImage
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.online.VideoItem
import com.villamediana.sound.tube.player.TubePlayer
import com.villamediana.sound.ui.components.pressScale
import com.villamediana.sound.ui.theme.Motion
import com.villamediana.sound.ui.theme.SoundColors
import com.villamediana.sound.ui.theme.sc
import com.villamediana.sound.ui.video.OverlayState
import com.villamediana.sound.ui.video.PendingSeek
import com.villamediana.sound.ui.video.SeekHint
import com.villamediana.sound.ui.video.SeekSide
import com.villamediana.sound.ui.video.SeekStreak
import com.villamediana.sound.ui.video.StageSpinner
import com.villamediana.sound.ui.video.VideoProgress
import com.villamediana.sound.ui.video.VideoSeek
import com.villamediana.sound.ui.video.rememberVideoProgress
import com.villamediana.sound.ui.video.safeAspectRatio
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * The watch page. Upright: the video on top (tap for the controls, double-tap a side to jump 10 s,
 * hold for 2×; drag it up for full screen, down to minimize — it follows the finger), and below it
 * the title, actions, channel, description, comments and what comes next. Full screen: the video
 * alone (sideways, or upright for a vertical video); drag down to come back, up for more videos.
 * Leaving keeps the video going in the mini player.
 */
@OptIn(UnstableApi::class)
@Composable
fun WatchScreen(route: WatchRoute) {
    val tube = SoundApplication.graph.tube
    val player = tube.player
    val nav = LocalTubeNav.current
    val mode = LocalWatchMode.current
    val vm: WatchViewModel = viewModel(key = "watch:" + route.id) { WatchViewModel(route.video()) }
    val pState by player.state.collectAsState()
    val exo by player.player.collectAsState()
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

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreen = landscape || mode.tall
    var panel by remember { mutableStateOf(false) }
    LaunchedEffect(fullscreen) { if (!fullscreen) panel = false }
    BackHandler(enabled = fullscreen) { if (panel) panel = false else mode.exit() }

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
    val pinned = scrubbing || loading || error != null || panel

    // The drag on the video (px, up < 0): the stage follows it, and past [threshold] it acts.
    val drag = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 76.dp.toPx() }
    val isFull by rememberUpdatedState(fullscreen)
    val isPanel by rememberUpdatedState(panel)
    val ratioNow by rememberUpdatedState(pState.aspectRatio)
    val isLandscape by rememberUpdatedState(landscape)

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
                if (isPanel) {
                    panel = false
                    return
                }
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

            override fun drag(dy: Float) {
                val wasPast = abs(drag.value) >= threshold
                scope.launch { drag.snapTo(dy) }
                // A tick as the finger crosses the point where letting go acts.
                if (abs(dy) >= threshold && !wasPast) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }

            override fun release(dy: Float, velocity: Float) {
                val up = dy < 0
                val flung = abs(velocity) > FLING_PX_S && (velocity < 0) == up && abs(dy) > threshold / 3
                var minimized = false
                if (abs(dy) >= threshold || flung) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    when {
                        isFull && up -> if (!isPanel) panel = true
                        isFull -> if (isPanel) panel = false else mode.exit()
                        up -> mode.enter(ratioNow)
                        else -> {
                            minimized = true
                            nav.minimize()
                        }
                    }
                }
                // Leaving downwards: the page goes as it is; otherwise the video springs back into place.
                if (!minimized) scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 520f)) }
            }

            override fun skip(forward: Boolean) {
                val p = exo ?: return
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                seekTo(VideoSeek.target(p.currentPosition, p.duration, if (forward) SeekSide.FORWARD else SeekSide.BACK))
            }

            override fun rotate() = mode.toggle(ratioNow, isLandscape)
            override fun retry() = player.retry()
            override fun minimize() = nav.minimize()
            override fun playNext() = player.playUpNext()
            override fun cancelNext() = player.cancelCountdown()
            override fun showPanel(show: Boolean) {
                panel = show
                overlay = overlay.touch(now())
            }
        }
    }

    val details by vm.state.collectAsState()
    val title = details.video?.title?.takeIf { it.isNotBlank() } ?: route.title
    val channel = details.video?.channelName?.takeIf { it.isNotBlank() } ?: route.channel

    BoxWithConstraints(Modifier.fillMaxSize().background(if (fullscreen) Color.Black else sc.Ink).then(if (pState.playing) Modifier.keepScreenOn() else Modifier)) {
        val ratio by animateFloatAsState(safeAspectRatio(pState.aspectRatio).coerceAtLeast(1f), Motion.soft(), label = "ratio")
        val stageHeight = min(maxWidth / ratio, maxHeight * 0.42f)
        Column(Modifier.fillMaxSize()) {
            if (!fullscreen) Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.Black))
            Stage(
                player = exo,
                ratio = if (fullscreen) safeAspectRatio(pState.aspectRatio) else ratio,
                poster = route.thumb ?: details.video?.thumbnailUrl,
                fullscreen = fullscreen,
                landscape = landscape,
                controlsVisible = overlay.visible || error != null,
                state = pState,
                ended = flags.ended,
                loading = loading,
                title = title,
                channel = channel,
                progress = progress,
                pending = pending,
                streak = streak,
                tapPoint = tapPoint,
                holding = holding,
                actions = actions,
                drag = { drag.value },
                threshold = threshold,
                panel = panel,
                related = details.related,
                onOpen = { v -> nav.openVideo(v) },
                modifier = (if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(stageHeight)).zIndex(1f),
                // The picture follows the finger (the touch area stays put, so the drag reads true).
                layer = {
                        val d = drag.value
                        if (!fullscreen) {
                            if (d < 0) {
                                // Up: the video grows a little toward full screen.
                                val s = 1f + 0.06f * (-d / threshold).coerceAtMost(1.6f)
                                scaleX = s
                                scaleY = s
                            } else if (d > 0) {
                                // Down: it sinks and shrinks toward the mini player.
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                translationY = d * 0.6f
                                val s = 1f - 0.12f * (d / threshold).coerceAtMost(1.6f)
                                scaleX = s
                                scaleY = s
                            }
                        } else if (d > 0 && !panel) {
                            val s = 1f - 0.07f * (d / threshold).coerceAtMost(1.6f)
                            scaleX = s
                            scaleY = s
                        }
                    },
            )
            if (!fullscreen) {
                // The video lends its color to the page: a soft veil under the stage that fades into the background.
                val veil = animatedVeil(rememberThumbColor(route.thumb ?: details.video?.thumbnailUrl))
                val ink = sc.Ink
                val wash = SoundColors.palette.washAlpha
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer {
                            // Dragging the video down: the page fades as it goes.
                            val d = drag.value
                            if (d > 0) alpha = 1f - 0.75f * (d / threshold).coerceIn(0f, 1f)
                        }
                        .drawBehind {
                            drawRect(Brush.verticalGradient(listOf(lerp(ink, veil, wash * 0.7f), ink), endY = 320.dp.toPx()))
                        },
                ) {
                    WatchDetails(route = route, vm = vm, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun now(): Long = SystemClock.uptimeMillis()

/** A fling faster than this (px/s) acts even short of the threshold. */
private const val FLING_PX_S = 1600f

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
    /** The finger dragged the video [dy] px (up < 0) and is still down. */
    fun drag(dy: Float)
    /** …and let go after [dy] px, at [velocity] px/s. */
    fun release(dy: Float, velocity: Float)
    fun skip(forward: Boolean)
    fun rotate()
    fun retry()
    fun minimize()
    fun playNext()
    fun cancelNext()
    fun showPanel(show: Boolean)
}

// ------------------------------------------------------------------ stage

@OptIn(UnstableApi::class)
@Composable
private fun Stage(
    player: Player?,
    ratio: Float,
    poster: String?,
    fullscreen: Boolean,
    landscape: Boolean,
    controlsVisible: Boolean,
    state: TubePlayer.State,
    ended: Boolean,
    loading: Boolean,
    title: String,
    channel: String,
    progress: VideoProgress,
    pending: PendingSeek?,
    streak: SeekStreak?,
    tapPoint: Offset?,
    holding: SeekSide?,
    actions: WatchActions,
    drag: () -> Float,
    threshold: Float,
    panel: Boolean,
    related: List<VideoItem>,
    onOpen: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    layer: GraphicsLayerScope.() -> Unit = {},
) {
    val error = state.error
    val toggleLabel = stringResource(if (controlsVisible) R.string.video_hide_controls else R.string.video_show_controls)
    val interactive = error == null && state.countdown == null
    val live = state.video?.live == true
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    Box(
        modifier
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
        Box(Modifier.matchParentSize().graphicsLayer(layer).background(Color.Black).clipToBounds()) {
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
                            AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f })
                        }
                    }
                }
            }

            // Controls hidden: a thin line along the bottom says how far the video is.
            AnimatedVisibility(
                visible = !controlsVisible && !fullscreen && !live && error == null && state.countdown == null,
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                StageProgressLine(progress, pending)
            }

            AnimatedVisibility(
                visible = controlsVisible && state.countdown == null && !panel,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(320)),
                modifier = Modifier.matchParentSize(),
            ) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.3f to Color.Black.copy(alpha = 0.28f),
                            0.65f to Color.Black.copy(alpha = 0.28f),
                            1f to Color.Black.copy(alpha = 0.62f),
                        ),
                    ),
                ) {
                    // Top: minimize (upright) or back + title (full screen).
                    if (fullscreen) {
                        Row(
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                                .padding(start = 6.dp, end = 20.dp, top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StageButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.video_exit_fullscreen), actions::rotate)
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (channel.isNotBlank()) Text(channel, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        StageButton(
                            Icons.Rounded.KeyboardArrowDown,
                            stringResource(R.string.tube_minimize),
                            actions::minimize,
                            Modifier.align(Alignment.TopStart).padding(4.dp),
                            iconSize = 30.dp,
                        )
                        if (state.height > 0) {
                            Text(
                                stringResource(R.string.tube_quality_now, state.height),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(14.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    // Center: back 10 s, play/pause, forward 10 s.
                    if (error == null && player != null) {
                        Row(
                            Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 56.dp else 36.dp),
                        ) {
                            StageButton(Icons.Rounded.Replay10, stringResource(R.string.video_back_10), { actions.skip(false) }, iconSize = 30.dp)
                            Spacer(Modifier.size(if (fullscreen) 76.dp else 62.dp))
                            if (ended && state.upNext != null) {
                                StageButton(Icons.Rounded.SkipNext, stringResource(R.string.tube_play_now), actions::playNext, iconSize = 32.dp)
                            } else {
                                StageButton(Icons.Rounded.Forward10, stringResource(R.string.video_forward_10), { actions.skip(true) }, iconSize = 30.dp)
                            }
                        }
                    }
                    if (error == null && !loading) {
                        StagePlayPause(
                            playing = state.playing,
                            ended = ended,
                            onClick = actions::playPause,
                            modifier = Modifier.align(Alignment.Center),
                            size = if (fullscreen) 76.dp else 62.dp,
                        )
                    }
                    // Bottom: time, full screen, and the seek bar along the edge.
                    if (error == null && player != null) {
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)) else Modifier)
                                .padding(horizontal = if (fullscreen) 20.dp else 10.dp)
                                .padding(bottom = if (fullscreen) 6.dp else 0.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (live) {
                                    Text(
                                        stringResource(R.string.tube_live),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(5.dp)).background(sc.Accent).padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                } else {
                                    StageTimes(progress, pending, Modifier.padding(start = 4.dp), dragFraction)
                                }
                                Spacer(Modifier.weight(1f))
                                if (fullscreen && related.isNotEmpty()) {
                                    StageButton(Icons.Rounded.VideoLibrary, stringResource(R.string.tube_more_videos), { actions.showPanel(true) }, size = 42.dp, iconSize = 22.dp)
                                }
                                StageButton(
                                    if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    stringResource(if (fullscreen) R.string.video_exit_fullscreen else R.string.video_fullscreen),
                                    actions::rotate,
                                    size = 42.dp,
                                )
                            }
                            if (!live) {
                                StageSeekBar(
                                    progress = progress,
                                    pending = pending,
                                    onSeek = actions::seekTo,
                                    onScrub = actions::scrub,
                                    onDragFraction = { dragFraction = it },
                                    modifier = Modifier.padding(bottom = if (fullscreen) 2.dp else 0.dp),
                                )
                            }
                        }
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
            DragHint(drag, threshold, fullscreen, panel, Modifier.align(Alignment.Center))
            StageSpinner(loading, Modifier.align(Alignment.Center))
            state.countdown?.let { seconds -> UpNextOverlay(seconds, state.upNext?.title.orEmpty(), state.upNext?.thumbnailUrl, actions, fullscreen) }
            if (error != null) StageError(error, compact = !fullscreen, onRetry = actions::retry, modifier = Modifier.align(Alignment.Center))
        }
        if (fullscreen) {
            MoreVideosPanel(
                visible = panel && related.isNotEmpty(),
                videos = related,
                onOpen = onOpen,
                onClose = { actions.showPanel(false) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** While the video is dragged: what letting go will do (full screen, minimize, back upright), brighter as it gets closer. */
@Composable
private fun DragHint(drag: () -> Float, threshold: Float, fullscreen: Boolean, panel: Boolean, modifier: Modifier = Modifier) {
    val direction by remember { derivedStateOf { drag().let { if (it < -1f) -1 else if (it > 1f) 1 else 0 } } }
    if (direction == 0) return
    val icon: ImageVector = when {
        fullscreen && direction < 0 -> if (panel) return else Icons.Rounded.KeyboardArrowUp
        fullscreen -> if (panel) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.FullscreenExit
        direction < 0 -> Icons.Rounded.Fullscreen
        else -> Icons.Rounded.KeyboardArrowDown
    }
    Box(
        modifier
            .graphicsLayer {
                val p = (abs(drag()) / threshold).coerceIn(0f, 1f)
                alpha = p
                val s = 0.7f + 0.3f * p
                scaleX = s
                scaleY = s
            }
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

/** Full screen, swiped up: what comes next, in a row over the bottom of the video. */
@Composable
private fun MoreVideosPanel(visible: Boolean, videos: List<VideoItem>, onOpen: (VideoItem) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(320, easing = Motion.Emphasized)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.92f))))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(top = 36.dp, bottom = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tube_up_next), style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                StageButton(Icons.Rounded.Close, stringResource(R.string.tube_close), onClose, size = 40.dp, iconSize = 22.dp)
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(videos, key = { "p:" + it.id }) { v ->
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        Modifier
                            .width(212.dp)
                            .pressScale(interaction, 0.96f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(interactionSource = interaction, indication = null) { onOpen(v) },
                    ) {
                        VideoThumb(v, Modifier.fillMaxWidth(), shape = SmallThumbShape, compact = true)
                        Spacer(Modifier.height(6.dp))
                        Text(v.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (v.channelName.isNotBlank()) Text(v.channelName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.66f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The end of a video with autoplay on: what's next and when, with "cancel" and "play now". */
@Composable
private fun BoxScope.UpNextOverlay(seconds: Int, title: String, thumb: String?, actions: WatchActions, big: Boolean) {
    Box(Modifier.matchParentSize().background(Color.Black)) {
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
                style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WhitePill(stringResource(R.string.tube_cancel), Icons.Rounded.Close, actions::cancelNext, filled = false)
                WhitePill(stringResource(R.string.tube_play_now), Icons.Rounded.SkipNext, actions::playNext, filled = true)
            }
        }
    }
}

/** A pill over the video: white (filled) or glass with a white hairline. */
@Composable
private fun WhitePill(text: String, icon: ImageVector, onClick: () -> Unit, filled: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .height(40.dp)
            .pressScale(interaction, 0.96f)
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.14f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (filled) Color.Black else Color.White
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
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
        WhitePill(stringResource(R.string.video_retry), Icons.Rounded.Refresh, onRetry, filled = true)
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

/**
 * Taps, double taps, holds and vertical drags on the video, told apart by hand so a single tap
 * answers at once: tap = controls, second tap on a side = jump 10 s, held = 2× until lifted, a
 * vertical drag is reported as it goes ([WatchActions.drag]) and when let go ([WatchActions.release]).
 */
private suspend fun PointerInputScope.stageGestures(canSeek: Boolean, actions: WatchActions) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val slop = viewConfiguration.touchSlop
        val deadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
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
            tracker.addPosition(change.uptimeMillis, change.position)
            if (!change.pressed) {
                upAt = change.position
                break
            }
            val d = change.position - down.position
            if (!dragging && !held && abs(d.y) > slop && abs(d.y) > abs(d.x)) dragging = true
            if (dragging) {
                // From where the drag was recognized: no jump of a slop's length.
                dy = if (d.y > 0) d.y - slop else d.y + slop
                actions.drag(dy)
                change.consume()
            }
        }
        when {
            held -> if (canSeek) actions.hold(false)
            dragging -> actions.release(dy, tracker.calculateVelocity().y)
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
    TubeButton(stringResource(R.string.tube_retry), onClick = onClick, icon = Icons.Rounded.Refresh, kind = TubeButtonKind.Tonal)
}
