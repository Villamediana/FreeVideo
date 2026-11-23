package com.villamediana.sound.ui.tube

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.state.rememberPresentationState
import coil3.compose.AsyncImage
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.online.Storyboard
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
import com.villamediana.sound.ui.video.VideoTime
import com.villamediana.sound.ui.video.rememberVideoProgress
import com.villamediana.sound.ui.video.safeAspectRatio
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The video over the pages, in one piece from the mini player to the watch page ([WatchSheet]).
 *
 * As a page: the video on top (tap for the controls, double-tap a side to jump 10 s, hold for 2×;
 * drag it up for full screen, down to shrink it into the bar — it follows the finger, and so does a
 * pull down on the page under it), and below it the title, actions, channel, description, comments
 * and what comes next. Full screen: the video alone (sideways, or upright for a vertical video), with
 * two fingers to fill the screen or zoom in, a sideways slide to seek (the frame shows), and the
 * left / right edges for brightness / volume; drag the middle down to come back, up for more videos.
 *
 * As the bar: the same video, small, with the title and play / close; tap it or push it up to open
 * it, push it to a side to close it.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun WatchScreen(route: WatchRoute, sheet: WatchSheet, miniBottom: () -> Float) {
    val tube = SoundApplication.graph.tube
    val player = tube.player
    val nav = LocalTubeNav.current
    val mode = LocalWatchMode.current
    val vm: WatchViewModel = viewModel(key = "watch:" + route.id) { WatchViewModel(route.video()) }
    val pState by player.state.collectAsState()
    val exo by player.player.collectAsState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val activity = LocalActivity.current

    // Open (or carry on with) this video. When the player moves on by itself (autoplay), the page follows.
    var followed by rememberSaveable(route.id) { mutableStateOf(false) }
    LaunchedEffect(route.id) {
        player.open(route.video(), route.startMs.takeIf { it >= 0 }, route.src)
    }
    LaunchedEffect(pState.video?.id) {
        val now = pState.video ?: return@LaunchedEffect
        if (now.id == route.id) followed = true
        else if (followed) nav.followVideo(now)
    }

    // The page, the bar, or on the way between them.
    val atPage by remember(sheet) { derivedStateOf { sheet.expansion.value > 0.985f && sheet.appear.value > 0.985f } }
    val atBar by remember(sheet) { derivedStateOf { sheet.expansion.value < 0.015f && sheet.appear.value >= 1f } }
    val growing by remember(sheet) { derivedStateOf { sheet.origin != null && sheet.appear.value < 1f } }
    val toPage = sheet.open

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreen = toPage && (landscape || mode.tall)
    var panel by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val captions by vm.captions.collectAsState()
    val prefs by remember { tube.settings.prefs }.collectAsState(initial = com.villamediana.sound.tube.data.TubePrefs())
    val appLanguage = remember(context) { com.villamediana.sound.tube.ContentLanguage.of(context).language }
    LaunchedEffect(fullscreen) { if (!fullscreen) panel = false }
    LaunchedEffect(toPage) {
        if (!toPage) settingsOpen = false
    }

    // Back: shrinks the page into the bar, following the gesture (the page gets smaller as the finger goes).
    PredictiveBackHandler(enabled = toPage && !fullscreen && !settingsOpen) { progress ->
        try {
            progress.collect { e -> sheet.dragTo(1f - 0.25f * e.progress) }
            sheet.collapse()
        } catch (e: CancellationException) {
            sheet.expand()
            throw e
        }
    }
    BackHandler(enabled = fullscreen && !settingsOpen) { if (panel) panel = false else mode.exit() }

    // The status bar over the black top of the page: light icons while the page is there.
    val darkTop by remember(sheet) { derivedStateOf { sheet.expansion.value > 0.5f } }
    val view = LocalView.current
    DisposableEffect(darkTop, activity) {
        val window = activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = if (darkTop) false else SoundColors.isLight
        onDispose { bars?.isAppearanceLightStatusBars = SoundColors.isLight }
    }

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
    val pinned = scrubbing || loading || error != null || panel || settingsOpen

    // Full screen: the picture's zoom (1 = fits, [Zoom.fill] = fills the screen, more = a closer look).
    val zoom = remember { Zoom() }
    LaunchedEffect(fullscreen) { if (!fullscreen) zoom.reset() }
    var zoomHint by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(zoomHint) {
        if (zoomHint == null) return@LaunchedEffect
        delay(1_100)
        zoomHint = null
    }
    // Full screen: sliding sideways seeks (shown, then done on release); the edges set brightness and volume.
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var seekFrom by remember { mutableStateOf(0L) }
    var level by remember { mutableStateOf<Level?>(null) }
    val levels = remember(activity) { Levels(activity, context) }
    DisposableEffect(fullscreen) { onDispose { levels.resetBrightness() } }
    LaunchedEffect(level?.shownAt) {
        val l = level ?: return@LaunchedEffect
        if (l.active) return@LaunchedEffect
        delay(700)
        if (level?.shownAt == l.shownAt) level = null
    }

    // The drag on the video upwards / in full screen (px, up < 0): the stage follows it, and past [threshold] it acts.
    val drag = remember { Animatable(0f) }
    val threshold = with(density) { 76.dp.toPx() }
    val isFull by rememberUpdatedState(fullscreen)
    val isPanel by rememberUpdatedState(panel)
    val ratioNow by rememberUpdatedState(pState.aspectRatio)
    val isLandscape by rememberUpdatedState(landscape)
    val durationNow by rememberUpdatedState(progress.durationMs)

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
                    player.boost(false)
                    if (rewoundFromPlaying) player.play()
                    rewoundFromPlaying = false
                    return
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (side == SeekSide.FORWARD) {
                    player.boost(true)
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
                if (!isFull && dy > 0) {
                    // Down from the page: it shrinks toward the bar under the finger.
                    sheet.dragTo(1f - dy / sheet.travel.coerceAtLeast(1f))
                    return
                }
                val wasPast = abs(drag.value) >= threshold
                scope.launch { drag.snapTo(dy) }
                // A tick as the finger crosses the point where letting go acts.
                if (abs(dy) >= threshold && !wasPast) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }

            override fun release(dy: Float, velocity: Float) {
                val up = dy < 0
                if (!isFull && !up) {
                    val flung = velocity > FLING_PX_S
                    val toBar = flung || (velocity > -FLING_PX_S && dy > sheet.travel * 0.22f)
                    if (toBar) haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    sheet.release(towardsPage = !toBar)
                    return
                }
                val flung = abs(velocity) > FLING_PX_S && (velocity < 0) == up && abs(dy) > threshold / 3
                if (abs(dy) >= threshold || flung) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    when {
                        isFull && up -> if (!isPanel) panel = true
                        isFull -> if (isPanel) panel = false else mode.exit()
                        up -> mode.enter(ratioNow)
                    }
                }
                scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 520f)) }
            }

            override fun skip(forward: Boolean) {
                val p = exo ?: return
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                seekTo(VideoSeek.target(p.currentPosition, p.duration, if (forward) SeekSide.FORWARD else SeekSide.BACK))
            }

            override fun rotate() = mode.toggle(ratioNow, isLandscape)
            override fun retry() = player.retry()
            override fun minimize() = sheet.collapse()
            override fun playNext() = player.playUpNext()
            override fun cancelNext() = player.cancelCountdown()
            override fun showPanel(show: Boolean) {
                panel = show
                overlay = overlay.touch(now())
            }

            override fun openSettings() {
                settingsOpen = true
                overlay = overlay.touch(now())
            }

            override fun toggleCaptions() {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                vm.toggleCaptions()
                overlay = overlay.touch(now())
            }

            override fun zoomedIn(): Boolean = zoom.free != null

            override fun transform(zoomChange: Float, pan: Offset) {
                zoom.change(zoomChange, pan)
            }

            override fun transformEnd() {
                val before = zoom.fillWanted()
                val filled = zoom.settle()
                if (filled != null) {
                    if (filled != before || filled != mode.fill) zoomHint = filled
                    mode.fill = filled
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            }

            override fun seekDrag(dx: Float, width: Float) {
                val p = exo ?: return
                val total = durationNow.takeIf { it > 0 } ?: return
                if (seekPreview == null) {
                    seekFrom = p.currentPosition
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                // A whole slide across the screen covers the video, up to ten minutes of it.
                val span = total.coerceIn(60_000L, 600_000L).toFloat()
                seekPreview = (seekFrom + dx / width.coerceAtLeast(1f) * span).toLong().coerceIn(0L, total)
                overlay = overlay.touch(now())
            }

            override fun seekDragEnd() {
                seekPreview?.let { seekTo(it) }
                seekPreview = null
            }

            override fun level(brightness: Boolean, delta: Float) {
                val value = if (brightness) levels.brightnessBy(delta) else levels.volumeBy(delta)
                level = Level(brightness, value, active = true, shownAt = level?.shownAt ?: now())
            }

            override fun levelEnd() {
                level = level?.copy(active = false, shownAt = now())
            }
        }
    }

    val details by vm.state.collectAsState()
    val title = details.video?.title?.takeIf { it.isNotBlank() } ?: route.title
    val channel = details.video?.channelName?.takeIf { it.isNotBlank() } ?: route.channel
    val storyboard = details.details?.storyboard

    // Pulled down at the top of the page: the page shrinks toward the bar (as dragging the video does).
    val pullToBar = remember(sheet) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val f = sheet.expansion.value
                if (source != NestedScrollSource.UserInput || available.y >= 0f || f >= 1f) return Offset.Zero
                // Partway down and the finger goes back up: the page grows back before the list scrolls.
                val travel = sheet.travel.coerceAtLeast(1f)
                val used = min(-available.y / travel, 1f - f)
                sheet.dragBy(used)
                return Offset(0f, -used * travel)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f || isFull) return Offset.Zero
                sheet.dragBy(-available.y / sheet.travel.coerceAtLeast(1f))
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val f = sheet.expansion.value
                if (f >= 1f || sheet.dragging.not()) return Velocity.Zero
                val toBar = available.y > FLING_PX_S || (available.y > -FLING_PX_S && f < 0.8f)
                sheet.release(towardsPage = !toBar)
                return available
            }
        }
    }

    // The screen stays on while the video plays on the page (in the bar it's browsing: the phone decides).
    BoxWithConstraints(Modifier.fillMaxSize().then(if (pState.playing && toPage) Modifier.keepScreenOn() else Modifier)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val stageRatio by animateFloatAsState(safeAspectRatio(pState.aspectRatio).coerceAtLeast(1f), Motion.soft(), label = "ratio")
        val geo = remember(sheet) { WatchGeo(sheet, miniBottom) }
        geo.w = w
        geo.h = h
        geo.statusTop = statusTop
        geo.stageH = min(w / stageRatio, h * 0.42f)
        geo.full = fullscreen
        geo.miniH = with(density) { MINI_HEIGHT.toPx() }
        geo.hair = with(density) { 1.dp.toPx() }
        geo.line = with(density) { 2.dp.toPx() }
        // How far the video goes between page and bar (drags are measured against it), and how wide the bar leaves.
        SideEffect {
            sheet.travel = geo.travelNow()
            sheet.travelWidth = w
        }

        // The cut-out of the screen (camera) leaves room around the fitted video; zoomed, the video goes under it.
        val layoutDirection = LocalLayoutDirection.current
        val cutout = WindowInsets.displayCutout
        val safe = if (fullscreen) {
            Rect(
                cutout.getLeft(density, layoutDirection).toFloat(),
                cutout.getTop(density).toFloat(),
                cutout.getRight(density, layoutDirection).toFloat(),
                cutout.getBottom(density).toFloat(),
            )
        } else {
            Rect.Zero
        }
        val videoRatio = safeAspectRatio(pState.aspectRatio)
        zoom.fill = if (fullscreen) fillScale(videoRatio, w, h, safe) else 1f
        LaunchedEffect(fullscreen, zoom.fill) { zoom.applyFill(mode.fill) }

        val page = if (fullscreen) Color.Black else sc.Ink
        val barColor = sc.Surface2
        // 1. The page's ground: the bar's color in the bar, the page's color as it grows; black above the video.
        Box(
            Modifier
                .at(geo::panel)
                .graphicsLayer { alpha = geo.pageAlpha() }
                .drawBehind {
                    val f = geo.f()
                    drawRect(lerp(barColor, page, f.coerceIn(0f, 1f)))
                    if (!geo.full && f > 0f) drawRect(Color.Black.copy(alpha = f.coerceIn(0f, 1f)), size = Size(size.width, geo.statusTop))
                }
                // The pages under it don't get its touches.
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
        )

        // 2. Under the video: the details (fade in as the page opens; out of reach in the bar).
        if (!fullscreen) {
            val veil = animatedVeil(rememberThumbColor(route.thumb ?: details.video?.thumbnailUrl))
            val ink = sc.Ink
            val wash = SoundColors.palette.washAlpha
            val rise = with(density) { 56.dp.toPx() }
            Box(
                Modifier
                    .at(geo::details)
                    .graphicsLayer {
                        val t = geo.detailsShown()
                        alpha = t
                        translationY = (1f - t) * rise
                    }
                    .drawBehind {
                        drawRect(Brush.verticalGradient(listOf(lerp(ink, veil, wash * 0.7f), ink), endY = 320.dp.toPx()))
                    }
                    .nestedScroll(pullToBar),
            ) {
                WatchDetails(route = route, vm = vm, modifier = Modifier.fillMaxSize())
            }
        }

        // 3. The video, wherever it is between the bar and the page.
        Stage(
            player = exo,
            videoRatio = videoRatio,
            poster = route.thumb ?: details.video?.thumbnailUrl,
            posterAlpha = if (growing || atBar) 1f else 0.55f,
            fullscreen = fullscreen,
            landscape = landscape,
            atPage = atPage,
            controlsVisible = atPage && (overlay.visible || error != null),
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
            captions = captions,
            related = details.related,
            storyboard = storyboard,
            zoom = zoom,
            safe = safe,
            zoomHint = zoomHint,
            seekPreview = seekPreview,
            seekFrom = seekFrom,
            level = level,
            onOpen = { v -> nav.openVideo(v) },
            modifier = Modifier.at(geo::stage),
            // Up: the video grows a little toward full screen; in full screen, down shrinks it a little.
            layer = {
                val d = drag.value
                if (!fullscreen) {
                    if (d < 0) {
                        val s = 1f + 0.06f * (-d / threshold).coerceAtMost(1.6f)
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

        // 4. The bar: title, play and close next to the small video; it takes the touches over the video too.
        val showBar by remember(sheet) { derivedStateOf { (sheet.expansion.value < 0.6f || sheet.barHeld) && !(sheet.origin != null && sheet.appear.value < 1f) } }
        if (showBar && !fullscreen) {
            MiniBar(
                state = pState,
                player = player,
                progress = progress,
                atBar = atBar,
                sheet = sheet,
                videoWidth = { geo.stage().width },
                landed = { (1f - geo.f() / 0.15f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .at(geo::miniBar)
                    .graphicsLayer { alpha = (1f - geo.f() / 0.6f).coerceIn(0f, 1f) },
            )
        }
    }
    WatchSettings(settingsOpen, onClose = { settingsOpen = false }, pState, captions, appLanguage, prefs.maxHeight, vm, player)
}

private fun now(): Long = SystemClock.uptimeMillis()

/** The video's settings sheet (speed, captions, quality), opened by the gear or the speed pill. */
@Composable
private fun WatchSettings(open: Boolean, onClose: () -> Unit, state: TubePlayer.State, captions: WatchViewModel.CaptionsUi, language: String, quality: Int, vm: WatchViewModel, player: TubePlayer) {
    if (!open) return
    VideoSettingsSheet(
        speed = state.speed,
        onSpeed = { player.setSpeed(it) },
        captions = captions,
        language = language,
        onCaptions = { choice, off -> vm.pickCaptions(choice, off) },
        quality = quality,
        live = state.video?.live == true,
        onQuality = { if (it != quality) player.setQuality(it) },
        onClose = onClose,
    )
}

/** A fling faster than this (px/s) acts even short of the threshold. */
private const val FLING_PX_S = 1600f

internal val MINI_HEIGHT = 74.dp

// ------------------------------------------------------------------ geometry

/**
 * Where everything is for an expansion: the video's box between the bar's corner and the top of the
 * page (or out of a card's thumbnail while it grows), the page's ground, the details and the bar.
 * Read in layout and drawing only, so moving it relays and redraws without recomposing.
 */
@Stable
private class WatchGeo(private val sheet: WatchSheet, private val miniBottom: () -> Float) {
    var w = 0f
    var h = 0f
    var statusTop = 0f
    var stageH = 0f
    var full = false
    var miniH = 0f
    var hair = 0f
    var line = 0f

    fun f(): Float = sheet.expansion.value
    private fun growing(): Boolean = sheet.origin != null && sheet.appear.value < 1f
    private val barH get() = hair + miniH + line
    private fun barTop(): Float = h - miniBottom() - barH
    private fun shift(): Offset = Offset(sheet.barShift.value, 0f) * (1f - f()).coerceIn(0f, 1f)

    fun pageStage(): Rect = if (full) Rect(0f, 0f, w, h) else Rect(0f, statusTop, w, statusTop + stageH)
    private fun miniStage(): Rect {
        val top = barTop() + hair
        return Rect(0f, top, miniH * 16f / 9f, top + miniH)
    }

    fun travelNow(): Float = (barTop() + hair - pageStage().top).coerceAtLeast(1f)

    fun stage(): Rect {
        val o = sheet.origin
        if (o != null && growing()) return lerpRect(o, pageStage(), sheet.appear.value)
        val page = pageStage()
        val mini = miniStage()
        val t = (1f - f()).coerceIn(0f, 1f)
        // The top follows the finger; the size goes a little ahead of it (small well before it lands).
        val top = page.top + (mini.top - page.top) * t
        val k = 1f - (1f - t) * (1f - t)
        val width = page.width + (mini.width - page.width) * k
        val height = page.height + (mini.height - page.height) * k
        return Rect(0f, top, width, top + height).translate(shift())
    }

    /** The page's ground: a sheet from just above the video down to the bottom, sliding into the bar. */
    fun panel(): Rect {
        if (growing()) return Rect(0f, 0f, w, h)
        val t = (1f - f()).coerceIn(0f, 1f)
        val videoTop = stage().top
        val top = videoTop - (statusTop + (hair - statusTop) * t)
        val bottom = h + (barTop() + barH - h) * t
        // Pushed sideways from the bar, the bar's ground goes with it.
        return Rect(0f, top, w, bottom).translate(shift())
    }

    fun pageAlpha(): Float = if (growing()) (sheet.appear.value * 1.6f).coerceIn(0f, 1f) else (1f - abs(sheet.barShift.value) / w.coerceAtLeast(1f)).coerceIn(0f, 1f)

    /** 0..1: how much of the details shows (they go early on the way to the bar). */
    fun detailsShown(): Float = if (growing()) ((sheet.appear.value - 0.35f) / 0.65f).coerceIn(0f, 1f) else ((f() - 0.55f) / 0.45f).coerceIn(0f, 1f)

    /** Right under the video, moving with it (their size stays: nothing is laid out again while it moves). */
    fun details(): Rect {
        val height = h - pageStage().bottom
        if (growing()) return Rect(0f, pageStage().bottom, w, h)
        // In the bar the details wait below the screen (out of reach of the fingers).
        if (f() < 0.02f) return Rect(0f, h + 1f, w, h + 1f + height)
        val top = stage().bottom
        return Rect(0f, top, w, top + height)
    }

    /** Beside the video, riding with it on its way down (at the end, the bar's own place). */
    fun miniBar(): Rect {
        val top = stage().top - hair
        return Rect(0f, top, w, top + barH).translate(shift())
    }
}

private fun lerpRect(a: Rect, b: Rect, t: Float): Rect {
    val k = t.coerceIn(0f, 1f)
    fun l(x: Float, y: Float) = x + (y - x) * k
    return Rect(l(a.left, b.left), l(a.top, b.top), l(a.right, b.right), l(a.bottom, b.bottom))
}

/** Measures the content to [rect]'s size and places it there (the node itself takes the whole parent). */
private fun Modifier.at(rect: () -> Rect): Modifier = layout { measurable, constraints ->
    val r = rect()
    val placeable = measurable.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(0), r.height.roundToInt().coerceAtLeast(0)))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(r.left.roundToInt(), r.top.roundToInt()) }
}

// ------------------------------------------------------------------ zoom

/**
 * The picture's zoom in full screen, relative to the video fitted in the screen: 1 fits it,
 * [fill] covers the whole screen (cropping the sides or the top and bottom), more is a closer look
 * (moved around with a finger). Pinching between fit and fill snaps to the nearer; past fill it stays.
 */
@Stable
internal class Zoom {
    /** The scale that covers the screen (≥ 1). */
    var fill by mutableFloatStateOf(1f)
    /** Fills the screen (when not zoomed freely). */
    var filled by mutableStateOf(false)
    /** A free zoom (past fill), or the pinch going on; null = fit or fill. */
    var free by mutableStateOf<Float?>(null)
    var pan by mutableStateOf(Offset.Zero)

    val scale: Float get() = free ?: if (filled) fill else 1f

    fun reset() {
        free = null
        pan = Offset.Zero
    }

    fun applyFill(want: Boolean) {
        filled = want && fill > 1.01f
    }

    fun fillWanted(): Boolean = filled

    fun change(zoomChange: Float, panBy: Offset) {
        val next = (scale * zoomChange).coerceIn(0.85f, MAX_ZOOM * fill.coerceAtLeast(1f))
        free = next
        pan += panBy
    }

    /** The pinch ended: snapped to fit / fill (which one: true = fill), or null = it stays zoomed in. */
    fun settle(): Boolean? {
        val s = free ?: return null
        if (s > fill * 1.12f && fill >= 1f) return null
        // Nearer to fit or to fill, as the eye sees it (on a log scale).
        val toFill = fill > 1.01f && s > sqrt(fill)
        filled = toFill
        free = null
        pan = Offset.Zero
        return toFill
    }

    private companion object {
        const val MAX_ZOOM = 4f
    }
}

/** How much the fitted video grows to cover [w] × [h] (the cut-out's margins included). */
private fun fillScale(ratio: Float, w: Float, h: Float, safe: Rect): Float {
    val sw = (w - safe.left - safe.right).coerceAtLeast(1f)
    val sh = (h - safe.top - safe.bottom).coerceAtLeast(1f)
    val (fw, fh) = if (sw / sh > ratio) (sh * ratio) to sh else sw to (sw / ratio)
    return max(w / fw, h / fh).coerceAtLeast(1f)
}

/**
 * The video's box inside the stage: fitted in the safe part of the screen, then grown by [zoom]
 * around the middle of the screen (and moved by its pan, never so far that an edge shows).
 */
private fun Modifier.videoFrame(ratio: Float, zoom: Zoom?, safe: Rect): Modifier = layout { measurable, constraints ->
    val w = constraints.maxWidth.toFloat()
    val h = constraints.maxHeight.toFloat()
    val sw = (w - safe.left - safe.right).coerceAtLeast(1f)
    val sh = (h - safe.top - safe.bottom).coerceAtLeast(1f)
    val (fw, fh) = if (sw / sh > ratio) (sh * ratio) to sh else sw to (sw / ratio)
    val s = zoom?.scale ?: 1f
    val vw = (fw * s).coerceAtLeast(1f)
    val vh = (fh * s).coerceAtLeast(1f)
    val placeable = measurable.measure(Constraints.fixed(vw.roundToInt(), vh.roundToInt()))
    // Fitted: the middle of the safe part; as it grows, the middle of the screen.
    val t = if (zoom == null || zoom.fill <= 1.001f) (if (s > 1f) 1f else 0f) else ((s - 1f) / (zoom.fill - 1f)).coerceIn(0f, 1f)
    val cx = (safe.left + sw / 2f) * (1f - t) + (w / 2f) * t
    val cy = (safe.top + sh / 2f) * (1f - t) + (h / 2f) * t
    val maxX = ((vw - w) / 2f).coerceAtLeast(0f)
    val maxY = ((vh - h) / 2f).coerceAtLeast(0f)
    val pan = zoom?.pan ?: Offset.Zero
    val px = pan.x.coerceIn(-maxX, maxX)
    val py = pan.y.coerceIn(-maxY, maxY)
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place((cx - vw / 2f + px).roundToInt(), (cy - vh / 2f + py).roundToInt())
    }
}

// ------------------------------------------------------------------ brightness and volume

/** What the edge of the screen is setting: brightness or volume, 0..1, and whether the finger is on it. */
internal data class Level(val brightness: Boolean, val value: Float, val active: Boolean, val shownAt: Long)

/** The window's brightness (only while full screen; the phone's own comes back after) and the media volume. */
private class Levels(private val activity: Activity?, context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var volume: Float? = null
    private var brightness: Float? = null

    fun brightnessBy(delta: Float): Float {
        val window = activity?.window ?: return 0f
        val start = brightness ?: window.attributes.screenBrightness.takeIf { it >= 0f } ?: systemBrightness()
        val next = (start + delta).coerceIn(0.01f, 1f)
        brightness = next
        window.attributes = window.attributes.apply { screenBrightness = next }
        return next
    }

    fun resetBrightness() {
        if (brightness == null) return
        brightness = null
        val window = activity?.window ?: return
        window.attributes = window.attributes.apply { screenBrightness = -1f }
    }

    private fun systemBrightness(): Float =
        runCatching { Settings.System.getInt(activity?.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f).coerceIn(0f, 1f)

    fun volumeBy(delta: Float): Float {
        val a = audio ?: return 0f
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val start = volume ?: (a.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max)
        val next = (start + delta).coerceIn(0f, 1f)
        volume = next
        val index = (next * max).roundToInt()
        if (index != a.getStreamVolume(AudioManager.STREAM_MUSIC)) runCatching { a.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }
        return next
    }
}

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
    /** The gear: the video's settings (speed, captions, quality). */
    fun openSettings()
    /** CC: captions on (the automatic ones, or those picked for this video) / off. */
    fun toggleCaptions()
    /** Zoomed in past filling the screen: one finger moves the picture. */
    fun zoomedIn(): Boolean
    fun transform(zoomChange: Float, pan: Offset)
    fun transformEnd()
    /** Full screen: slid [dx] px sideways (of [width]) to seek. */
    fun seekDrag(dx: Float, width: Float)
    fun seekDragEnd()
    /** Full screen: an edge slid by [delta] (0..1 of the way, up > 0). */
    fun level(brightness: Boolean, delta: Float)
    fun levelEnd()
}

// ------------------------------------------------------------------ stage

@OptIn(UnstableApi::class)
@Composable
private fun Stage(
    player: Player?,
    videoRatio: Float,
    poster: String?,
    posterAlpha: Float,
    fullscreen: Boolean,
    landscape: Boolean,
    atPage: Boolean,
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
    captions: WatchViewModel.CaptionsUi,
    related: List<VideoItem>,
    storyboard: Storyboard?,
    zoom: Zoom,
    safe: Rect,
    zoomHint: Boolean?,
    seekPreview: Long?,
    seekFrom: Long,
    level: Level?,
    onOpen: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    layer: GraphicsLayerScope.() -> Unit = {},
) {
    val error = state.error
    val toggleLabel = stringResource(if (controlsVisible) R.string.video_hide_controls else R.string.video_show_controls)
    // Gestures are decided when the finger lands (on the page, or full screen) and then followed to the
    // end, even as the video moves and shrinks under the finger.
    val gesturesOn by rememberUpdatedState(atPage || fullscreen)
    val where = remember { RootSpot() }
    val live = state.video?.live == true
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val full by rememberUpdatedState(fullscreen)
    Box(
        modifier
            .semantics {
                onClick(label = toggleLabel) {
                    actions.toggleControls()
                    true
                }
            }
            .onGloballyPositioned { where.offset = it.positionInRoot() }
            .pointerInput(player != null) {
                stageGestures(canSeek = player != null, full = { full }, enabled = { gesturesOn }, where = where, actions = actions)
            },
    ) {
        Box(Modifier.matchParentSize().graphicsLayer(layer).background(Color.Black).clipToBounds()) {
            val coverSurface = player == null || rememberPresentationState(player).coverSurface
            Box(Modifier.videoFrame(videoRatio, if (fullscreen) zoom else null, safe)) {
                // In the floating window the window draws the video; this surface comes back after it.
                if (player != null && !LocalInPip.current) PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
                if (!poster.isNullOrBlank()) {
                    val alpha by animateFloatAsState(posterAlpha, tween(260), label = "poster")
                    // In the bar the thumbnail stands in for a video that can't play (never a black box).
                    AnimatedVisibility(coverSurface && (error == null || !atPage), enter = fadeIn(tween(200)), exit = fadeOut(tween(420)), modifier = Modifier.matchParentSize()) {
                        AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha })
                    }
                }
            }

            // Controls hidden: a thin line along the bottom says how far the video is.
            AnimatedVisibility(
                visible = atPage && !controlsVisible && !fullscreen && !live && error == null && state.countdown == null,
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                StageProgressLine(progress, pending)
            }

            AnimatedVisibility(
                visible = controlsVisible && state.countdown == null && !panel && seekPreview == null,
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
                            Spacer(Modifier.width(8.dp))
                            StageTopActions(state.speed, live, captions, actions)
                        }
                    } else {
                        StageButton(
                            Icons.Rounded.KeyboardArrowDown,
                            stringResource(R.string.tube_minimize),
                            actions::minimize,
                            Modifier.align(Alignment.TopStart).padding(4.dp),
                            iconSize = 30.dp,
                        )
                        StageTopActions(state.speed, live, captions, actions, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp))
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
                        StageBottom(
                            progress = progress,
                            pending = pending,
                            live = live,
                            fullscreen = fullscreen,
                            moreVideos = related.isNotEmpty(),
                            actions = actions,
                            dragFraction = dragFraction,
                            onDragFraction = { dragFraction = it },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }

            // Sliding sideways in full screen: the rest of the controls step aside, but the time and the
            // bar stay and go along with the finger (the frame of that moment shows in the middle).
            val sliding = seekPreview
            AnimatedVisibility(
                visible = sliding != null && state.countdown == null && !panel && error == null && player != null && !live,
                enter = fadeIn(tween(140)),
                exit = fadeOut(tween(320)),
                modifier = Modifier.matchParentSize(),
            ) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f)))) {
                    StageBottom(
                        progress = progress,
                        pending = pending,
                        live = false,
                        fullscreen = fullscreen,
                        moreVideos = false,
                        actions = actions,
                        dragFraction = null,
                        onDragFraction = {},
                        previewMs = sliding,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            // Sliding along the seek bar: the frame of that moment above the finger.
            val barFraction = dragFraction
            if (barFraction != null && atPage) {
                SeekBubble(
                    storyboard = storyboard,
                    positionMs = (barFraction * progress.durationMs.coerceAtLeast(0)).toLong(),
                    totalMs = progress.durationMs,
                    big = fullscreen,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .followX(barFraction, inset = with(LocalDensity.current) { (if (fullscreen) 20.dp else 10.dp).toPx() })
                        .padding(bottom = if (fullscreen) 92.dp else 60.dp),
                )
            }
            // Sliding sideways in full screen: the frame, the time and how far from where it was.
            seekPreview?.let { target ->
                SeekBubble(
                    storyboard = storyboard,
                    positionMs = target,
                    totalMs = progress.durationMs,
                    big = true,
                    delta = target - seekFrom,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // The line said now (captions on), above the controls when they show.
            if (captions.showing && captions.cues.isNotEmpty() && (atPage || fullscreen) && state.countdown == null) {
                val lift by animateFloatAsState(if (controlsVisible) 1f else 0f, tween(220), label = "ccLift")
                val low = if (fullscreen) 28.dp else 12.dp
                val high = if (fullscreen) 104.dp else 64.dp
                StageCaption(
                    captions.cues,
                    progress,
                    big = fullscreen,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = low + (high - low) * lift),
                )
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
            // Pinched to fill / to fit: says which, for a moment.
            AnimatedVisibility(
                visible = zoomHint != null,
                enter = fadeIn(tween(150)) + scaleIn(tween(200), initialScale = 0.85f),
                exit = fadeOut(tween(260)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            ) {
                val filled = zoomHint == true
                StageChip(
                    icon = if (filled) Icons.Rounded.ZoomIn else Icons.Rounded.FitScreen,
                    text = stringResource(if (filled) R.string.tube_zoom_fill else R.string.tube_zoom_fit),
                )
            }
            // The edges in full screen: brightness on the left, volume on the right.
            level?.let { l -> LevelMeter(l, Modifier.align(if (l.brightness) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 40.dp)) }
            if (atPage) DragHint(drag, threshold, fullscreen, panel, Modifier.align(Alignment.Center))
            if (atPage || fullscreen) StageSpinner(loading, Modifier.align(Alignment.Center))
            if (atPage) state.countdown?.let { seconds -> UpNextOverlay(seconds, state.upNext?.title.orEmpty(), state.upNext?.thumbnailUrl, actions, fullscreen) }
            if (error != null && atPage) StageError(error, compact = !fullscreen, onRetry = actions::retry, modifier = Modifier.align(Alignment.Center))
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

/**
 * Along the bottom of the video: the time (or LIVE), more videos and full screen, and the seek bar.
 * [previewMs]: a sideways slide is under way — just the time and the bar, both at where it is going.
 */
@Composable
private fun StageBottom(
    progress: VideoProgress,
    pending: PendingSeek?,
    live: Boolean,
    fullscreen: Boolean,
    moreVideos: Boolean,
    actions: WatchActions,
    dragFraction: Float?,
    onDragFraction: (Float?) -> Unit,
    modifier: Modifier = Modifier,
    previewMs: Long? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)) else Modifier)
            .padding(horizontal = if (fullscreen) 20.dp else 10.dp)
            .padding(bottom = if (fullscreen) 6.dp else 0.dp),
    ) {
        Row(Modifier.heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                Text(
                    stringResource(R.string.tube_live),
                    style = MaterialTheme.typography.labelSmall,
                    color = sc.OnAccent,
                    modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(5.dp)).background(sc.Accent).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            } else {
                StageTimes(progress, pending, Modifier.padding(start = 4.dp), dragFraction, previewMs)
            }
            Spacer(Modifier.weight(1f))
            if (previewMs == null) {
                if (fullscreen && moreVideos) {
                    StageButton(Icons.Rounded.VideoLibrary, stringResource(R.string.tube_more_videos), { actions.showPanel(true) }, size = 42.dp, iconSize = 22.dp)
                }
                StageButton(
                    if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    stringResource(if (fullscreen) R.string.video_exit_fullscreen else R.string.video_fullscreen),
                    actions::rotate,
                    size = 42.dp,
                )
            }
        }
        if (!live) {
            StageSeekBar(
                progress = progress,
                pending = pending,
                onSeek = actions::seekTo,
                onScrub = actions::scrub,
                onDragFraction = onDragFraction,
                modifier = Modifier.padding(bottom = if (fullscreen) 2.dp else 0.dp),
                previewMs = previewMs,
            )
        }
    }
}

/** Places the content centred on [fraction] of the parent's width (minus [inset] each side), kept inside it. */
private fun Modifier.followX(fraction: Float, inset: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val width = constraints.maxWidth
    layout(placeable.width, placeable.height) {
        val track = (width - 2 * inset).coerceAtLeast(0f)
        val center = inset + track * fraction.coerceIn(0f, 1f)
        val x = (center - placeable.width / 2f).coerceIn(0f, (width - placeable.width).toFloat().coerceAtLeast(0f))
        placeable.place(x.roundToInt(), 0)
    }
}

/** The frame at [positionMs] (when YouTube has frames for it) with the time under it. */
@Composable
private fun SeekBubble(storyboard: Storyboard?, positionMs: Long, totalMs: Long, big: Boolean, modifier: Modifier = Modifier, delta: Long? = null) {
    val frame = storyboard?.frameAt(positionMs)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (frame != null) {
            StoryboardFrame(
                frame,
                Modifier
                    .width(if (big) 208.dp else 160.dp)
                    .height(if (big) 117.dp else 90.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .background(Color.Black),
            )
            Spacer(Modifier.height(6.dp))
        }
        val time = VideoTime.format(positionMs, totalMs)
        val text = delta?.let { d -> (if (d >= 0) "+" else "−") + VideoTime.format(abs(d), totalMs) + "  ·  " + time } ?: time
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = Color.White,
            modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Top right over the video: the speed while it isn't 1×, CC (on / off in the app's language — the
 * video has captions) and the gear for the video's settings.
 */
@Composable
private fun StageTopActions(speed: Float, live: Boolean, captions: WatchViewModel.CaptionsUi, actions: WatchActions, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!live && speed != 1f) {
            StageSpeedPill(speed, actions::openSettings, Modifier.padding(end = 4.dp))
        }
        if (captions.tracks.isNotEmpty()) {
            StageButton(
                if (captions.showing) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                stringResource(R.string.tube_captions),
                actions::toggleCaptions,
                size = 44.dp,
                iconSize = 24.dp,
            )
        }
        StageButton(Icons.Rounded.Settings, stringResource(R.string.tube_video_settings), actions::openSettings, size = 44.dp, iconSize = 23.dp)
    }
}

/** A dark glass chip over the video: an icon and a word or two. */
@Composable
private fun StageChip(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** Brightness or volume while an edge is slid: the icon over a tall glass meter that fills from below. */
@Composable
private fun LevelMeter(level: Level, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(level.value, spring(dampingRatio = 0.9f, stiffness = 900f), label = "level")
    val accent = Color.White
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(if (level.brightness) Icons.Rounded.BrightnessMedium else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(6.dp)
                .height(120.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(Color.White.copy(alpha = 0.22f))
                    val filled = size.height * shown.coerceIn(0f, 1f)
                    drawRect(accent, topLeft = Offset(0f, size.height - filled), size = Size(size.width, filled))
                },
        )
    }
}

/** While the video is dragged: what letting go will do (full screen, back upright, more videos), brighter as it gets closer. */
@Composable
private fun DragHint(drag: () -> Float, threshold: Float, fullscreen: Boolean, panel: Boolean, modifier: Modifier = Modifier) {
    val direction by remember { derivedStateOf { drag().let { if (it < -1f) -1 else if (it > 1f) 1 else 0 } } }
    if (direction == 0) return
    val icon: ImageVector = when {
        fullscreen && direction < 0 -> if (panel) return else Icons.Rounded.KeyboardArrowUp
        fullscreen -> if (panel) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.FullscreenExit
        direction < 0 -> Icons.Rounded.Fullscreen
        else -> return
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

// ------------------------------------------------------------------ the bar

/**
 * The video going on while browsing: next to the small video (drawn by the stage underneath), the
 * title and channel, a big play/pause and close, and a line along the bottom for how far it is. Tap
 * it or push it up to open the page (it grows with the finger); push it to a side to close it.
 */
@OptIn(UnstableApi::class)
@Composable
private fun MiniBar(
    state: TubePlayer.State,
    player: TubePlayer,
    progress: VideoProgress,
    atBar: Boolean,
    sheet: WatchSheet,
    /** How wide the video beside it is right now (px): the title starts where the video ends. */
    videoWidth: () -> Float,
    /** 0..1: how close it is to being the bar (its lines show only then: mid-way they'd cross the video). */
    landed: () -> Float,
    modifier: Modifier = Modifier,
) {
    val video = state.video ?: return
    val accent = sc.Accent
    val track = sc.Hairline
    val playLabel = stringResource(if (state.playing) R.string.video_pause else R.string.video_play)
    val openLabel = stringResource(R.string.tube_open_video)
    val where = remember { RootSpot() }
    Column(
        modifier
            .onGloballyPositioned { where.offset = it.positionInRoot() }
            .barGestures(sheet, where, onClose = player::close)
            .semantics { onClick(label = openLabel) { sheet.expand(); true } },
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).graphicsLayer { alpha = landed() }.background(sc.Hairline))
        Row(
            Modifier.fillMaxWidth().height(MINI_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Over the small video: a play mark while it's paused.
            Box(Modifier.videoGap(videoWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (!state.playing && state.error == null) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, color = sc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(video.channelName, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CircleIconButton(
                if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                playLabel,
                player::togglePlayPause,
                size = 52.dp,
                iconSize = 34.dp,
            )
            CircleIconButton(Icons.Rounded.Close, stringResource(R.string.tube_close), player::close, tint = sc.TextMuted, size = 48.dp, iconSize = 24.dp)
            Spacer(Modifier.width(4.dp))
        }
        Box(
            Modifier.fillMaxWidth().height(2.dp).graphicsLayer { alpha = landed() }.drawBehind {
                drawRect(track)
                val total = progress.durationMs
                if (total > 0) drawRect(accent, size = size.copy(width = size.width * (progress.positionMs.toFloat() / total).coerceIn(0f, 1f)))
            },
        )
    }
}

/** As wide as the video beside it is at this moment (read while laying out: it follows the drag). */
private fun Modifier.videoGap(width: () -> Float): Modifier = layout { measurable, constraints ->
    val px = width().roundToInt().coerceIn(0, constraints.maxWidth)
    val placeable = measurable.measure(constraints.copy(minWidth = px, maxWidth = px))
    layout(px, placeable.height) { placeable.place(0, 0) }
}

/**
 * The bar under the finger: a tap opens the page; pushed up the page grows with the finger (and opens
 * past a little of the way or with a flick); pushed to a side the bar slides away and closes past a
 * third of its width (or with a flick). Short of that it all springs back. The buttons keep their taps.
 */
private fun Modifier.barGestures(sheet: WatchSheet, where: RootSpot, onClose: () -> Unit): Modifier = pointerInput(sheet) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Only from the bar itself (not while it is still on its way down).
        if (sheet.expansion.value > 0.05f) return@awaitEachGesture
        val downAt = down.position + where.offset
        val slop = viewConfiguration.touchSlop
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position + where.offset)
        var axis = 0 // 0 = not decided (a tap so far), 1 = sideways, 2 = up
        var d = Offset.Zero
        var tapped = true
        while (true) {
            // Seen before the buttons (Initial pass): once it's a swipe, they see it consumed and don't click.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            tracker.addPosition(change.uptimeMillis, change.position + where.offset)
            if (!change.pressed) {
                if (axis != 0) change.consume()
                break
            }
            d = change.position + where.offset - downAt
            if (axis == 0) {
                axis = when {
                    abs(d.x) > slop && abs(d.x) > abs(d.y) -> 1
                    -d.y > slop && abs(d.y) > abs(d.x) -> 2
                    else -> 0
                }
                if (abs(d.x) > slop || abs(d.y) > slop) tapped = false
                // The bar stays on screen (and holds the gesture) while the finger moves the page.
                if (axis != 0) sheet.barHeld = true
            }
            when (axis) {
                1 -> {
                    change.consume()
                    sheet.shiftTo(d.x)
                }
                2 -> {
                    change.consume()
                    sheet.dragTo((-d.y - slop).coerceAtLeast(0f) / sheet.travel.coerceAtLeast(1f))
                }
            }
        }
        val v = tracker.calculateVelocity()
        sheet.barHeld = false
        when (axis) {
            0 -> if (tapped && !down.isConsumed) {
                // A tap on the bar (not on its buttons, which took theirs): the page.
                sheet.expand()
            }
            1 -> {
                val width = size.width.toFloat()
                val flung = abs(v.x) > 1400f && (v.x > 0) == (d.x > 0)
                if (abs(d.x) > width / 3 || flung) sheet.shiftAway(d.x > 0, onClose) else sheet.shiftBack()
            }
            2 -> sheet.release(towardsPage = v.y < -900f || (v.y < 900f && sheet.expansion.value > 0.18f))
        }
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
 * Taps, double taps, holds and drags on the video, told apart by hand so a single tap answers at
 * once: tap = controls, second tap on a side = jump 10 s, held = 2× until lifted, a vertical drag is
 * reported as it goes ([WatchActions.drag]) and when let go ([WatchActions.release]). In full screen
 * also: two fingers zoom (and move the zoomed picture), a sideways slide seeks, and a vertical slide
 * on the left / right third sets the brightness / the volume.
 */
private suspend fun PointerInputScope.stageGestures(canSeek: Boolean, full: () -> Boolean, enabled: () -> Boolean, where: RootSpot, actions: WatchActions) {
    awaitEachGesture {
        // Also when it lands on a button over the video (play, ±10 s…): a drag still moves the video;
        // the button keeps its tap (and a tap there isn't also a tap on the video).
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!enabled()) return@awaitEachGesture
        val onButton = down.isConsumed
        // Where the finger is on the screen (the video itself moves while it is dragged).
        val downAt = down.position + where.offset
        val slop = viewConfiguration.touchSlop
        val deadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position + where.offset)
        val fullNow = full()
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        var last = down.uptimeMillis
        var kind = Kind.NONE
        var held = false
        var dy = 0f
        var lastPos = down.position
        var upAt: Offset? = null
        while (true) {
            val event = if (kind == Kind.NONE && !held) {
                withTimeoutOrNull((deadline - last).coerceAtLeast(1)) { awaitPointerEvent() }
            } else {
                // Once it's a drag, seen before the buttons (Initial pass): they see it taken and don't click.
                awaitPointerEvent(if (kind != Kind.NONE) PointerEventPass.Initial else PointerEventPass.Main)
            }
            if (event == null) {
                held = true
                if (canSeek && !onButton) actions.hold(true, VideoSeek.sideOf(down.position.x, width))
                continue
            }
            // A second finger in full screen: zoom, until every finger is up.
            if (fullNow && !held && (kind == Kind.NONE || kind == Kind.PAN) && event.changes.count { it.pressed } >= 2) {
                kind = Kind.PINCH
                while (true) {
                    val e = awaitPointerEvent()
                    if (e.changes.none { it.pressed }) break
                    val z = e.calculateZoom()
                    val p = e.calculatePan()
                    if (z != 1f || p != Offset.Zero) actions.transform(z, p)
                    e.changes.forEach { if (it.positionChanged()) it.consume() }
                }
                actions.transformEnd()
                return@awaitEachGesture
            }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            last = change.uptimeMillis
            tracker.addPosition(change.uptimeMillis, change.position + where.offset)
            if (!change.pressed) {
                upAt = change.position
                if (kind != Kind.NONE) change.consume()
                break
            }
            val d = change.position + where.offset - downAt
            if (kind == Kind.NONE && !held) {
                kind = when {
                    fullNow && actions.zoomedIn() && (abs(d.x) > slop || abs(d.y) > slop) -> Kind.PAN
                    abs(d.y) > slop && abs(d.y) > abs(d.x) -> when {
                        fullNow && down.position.x < width * EDGE -> Kind.BRIGHTNESS
                        fullNow && down.position.x > width * (1 - EDGE) -> Kind.VOLUME
                        else -> Kind.VERTICAL
                    }
                    fullNow && canSeek && abs(d.x) > slop && abs(d.x) > abs(d.y) -> Kind.SEEK
                    else -> Kind.NONE
                }
                lastPos = change.position
            }
            when (kind) {
                Kind.VERTICAL -> {
                    // From where the drag was recognized: no jump of a slop's length.
                    dy = if (d.y > 0) d.y - slop else d.y + slop
                    actions.drag(dy)
                    change.consume()
                }
                Kind.SEEK -> {
                    actions.seekDrag(if (d.x > 0) d.x - slop else d.x + slop, width)
                    change.consume()
                }
                Kind.BRIGHTNESS, Kind.VOLUME -> {
                    val step = (lastPos.y - change.position.y) / (height * 0.7f).coerceAtLeast(1f)
                    if (step != 0f) actions.level(kind == Kind.BRIGHTNESS, step)
                    lastPos = change.position
                    change.consume()
                }
                Kind.PAN -> {
                    actions.transform(1f, change.position - lastPos)
                    lastPos = change.position
                    change.consume()
                }
                else -> Unit
            }
        }
        when {
            held -> if (canSeek && !onButton) actions.hold(false)
            kind == Kind.VERTICAL -> actions.release(dy, tracker.calculateVelocity().y)
            kind == Kind.SEEK -> actions.seekDragEnd()
            kind == Kind.BRIGHTNESS || kind == Kind.VOLUME -> actions.levelEnd()
            kind == Kind.PAN -> actions.transformEnd()
            onButton -> Unit
            upAt != null -> {
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

private enum class Kind { NONE, VERTICAL, SEEK, BRIGHTNESS, VOLUME, PAN, PINCH }

/** Where a moving node is on the screen (updated as it is laid out): drags are measured on the screen. */
internal class RootSpot {
    var offset: Offset = Offset.Zero
}

/** The share of the screen's width, at each side, that sets brightness / volume in full screen. */
private const val EDGE = 0.3f

@Composable
internal fun RetryPill(onClick: () -> Unit) {
    TubeButton(stringResource(R.string.tube_retry), onClick = onClick, icon = Icons.Rounded.Refresh, kind = TubeButtonKind.Tonal)
}
