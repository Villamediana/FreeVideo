package com.villamediana.sound.ui.tube

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.villamediana.sound.R
import com.villamediana.sound.ui.components.pressScale
import com.villamediana.sound.ui.theme.sc
import com.villamediana.sound.ui.video.PendingSeek
import com.villamediana.sound.ui.video.VideoProgress
import com.villamediana.sound.ui.video.VideoTime

/*
 * The controls drawn over the picture: always white on the video (whatever the app's theme), with
 * the ember for the part already watched.
 */

/** A white icon button over the video. */
@Composable
internal fun StageButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 46.dp, iconSize: Dp = 26.dp) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(interaction, 0.86f)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

/** The center button: a dark glass disc, the white glyph morphing play ↔ pause ("watch again" at the end). */
@Composable
internal fun StagePlayPause(playing: Boolean, ended: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val t by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.82f, stiffness = 650f), label = "morph")
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(
        when {
            ended -> R.string.video_replay
            playing -> R.string.video_pause
            else -> R.string.video_play
        },
    )
    Box(
        modifier
            .size(size)
            .pressScale(interaction, 0.88f)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(ended, animationSpec = tween(220), label = "ended") { done ->
            if (done) {
                Icon(Icons.Rounded.Replay, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
            } else {
                Canvas(Modifier.size(size * 0.44f)) { drawPlayPause(t, Color.White) }
            }
        }
    }
}

private val PLAY_LEFT = floatArrayOf(0.27f, 0.10f, 0.59f, 0.30f, 0.59f, 0.70f, 0.27f, 0.90f)
private val PLAY_RIGHT = floatArrayOf(0.59f, 0.30f, 0.91f, 0.50f, 0.91f, 0.50f, 0.59f, 0.70f)
private val PAUSE_LEFT = floatArrayOf(0.18f, 0.12f, 0.40f, 0.12f, 0.40f, 0.88f, 0.18f, 0.88f)
private val PAUSE_RIGHT = floatArrayOf(0.60f, 0.12f, 0.82f, 0.12f, 0.82f, 0.88f, 0.60f, 0.88f)

private fun DrawScope.drawPlayPause(t: Float, color: Color) {
    val w = size.width
    val h = size.height
    for ((from, to) in arrayOf(PLAY_LEFT to PAUSE_LEFT, PLAY_RIGHT to PAUSE_RIGHT)) {
        fun x(i: Int) = (from[i] + (to[i] - from[i]) * t) * w
        fun y(i: Int) = (from[i] + (to[i] - from[i]) * t) * h
        val path = Path().apply {
            moveTo(x(0), y(1))
            lineTo(x(2), y(3))
            lineTo(x(4), y(5))
            lineTo(x(6), y(7))
            close()
        }
        drawPath(path, color)
    }
}

/** Where the video is, as the bar shows it: the finger's spot while dragging, the target right after a seek. */
private fun shownMs(progress: VideoProgress, drag: Float?, pending: PendingSeek?): Long {
    val total = progress.durationMs
    drag?.let { return (it * total.coerceAtLeast(0)).toLong() }
    val reported = progress.positionMs
    return pending?.shown(reported, SystemClock.uptimeMillis()) ?: reported
}

/**
 * The seek bar over the bottom edge of the video: white tracks, the ember for what was watched and a
 * thumb that grows under the finger. Tap or drag to seek.
 */
@Composable
internal fun StageSeekBar(
    progress: VideoProgress,
    pending: PendingSeek?,
    onSeek: (Long) -> Unit,
    onScrub: (Boolean) -> Unit,
    onDragFraction: (Float?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val drag = remember { mutableStateOf<Float?>(null) }
    val dragging by remember { derivedStateOf { drag.value != null } }
    val grow by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 700f), label = "thumb")
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrub)
    val fraction by rememberUpdatedState(onDragFraction)
    val hold by rememberUpdatedState(pending)
    val accent = sc.Accent
    val label = stringResource(R.string.video_position)
    Box(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(VideoTime.fraction(progress.positionMs, progress.durationMs), 0f..1f)
                setProgress { f ->
                    val total = progress.durationMs
                    if (total > 0) seek((f.coerceIn(0f, 1f) * total).toLong())
                    total > 0
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val total = progress.durationMs
                    if (total > 0 && size.width > 0) seek(((o.x / size.width).coerceIn(0f, 1f) * total).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        if (progress.durationMs > 0 && size.width > 0) {
                            drag.value = (o.x / size.width).coerceIn(0f, 1f)
                            fraction(drag.value)
                            scrub(true)
                        }
                    },
                    onDragEnd = {
                        drag.value?.let { seek((it * progress.durationMs).toLong()) }
                        drag.value = null
                        fraction(null)
                        scrub(false)
                    },
                    onDragCancel = {
                        drag.value = null
                        fraction(null)
                        scrub(false)
                    },
                    onHorizontalDrag = { change, dx ->
                        val f = drag.value ?: return@detectHorizontalDragGestures
                        change.consume()
                        drag.value = (f + dx / size.width).coerceIn(0f, 1f)
                        fraction(drag.value)
                    },
                )
            }
            .drawBehind {
                val total = progress.durationMs
                val f = VideoTime.fraction(shownMs(progress, drag.value, hold), total)
                val buffered = VideoTime.fraction(progress.bufferedMs, total)
                val cy = size.height / 2f
                val stroke = (3.dp + 2.dp * grow).toPx()
                drawLine(Color.White.copy(alpha = 0.28f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = stroke, cap = StrokeCap.Round)
                if (buffered > f) drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, cy), Offset(size.width * buffered, cy), strokeWidth = stroke, cap = StrokeCap.Round)
                if (f > 0f) drawLine(accent, Offset(0f, cy), Offset(size.width * f, cy), strokeWidth = stroke, cap = StrokeCap.Round)
                if (total > 0) drawCircle(accent, radius = (6.dp + 4.dp * grow).toPx(), center = Offset(size.width * f, cy))
            },
    )
}

/** "1:23 / 10:45" over the video. */
@Composable
internal fun StageTimes(progress: VideoProgress, pending: PendingSeek?, modifier: Modifier = Modifier, dragFraction: Float? = null) {
    val second by remember(progress, pending, dragFraction) { derivedStateOf { shownMs(progress, dragFraction, pending) / 1000 } }
    val total by remember(progress) { derivedStateOf { progress.durationMs } }
    val style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    Text(
        "${VideoTime.format(second * 1000, total)} / ${VideoTime.formatTotal(total)}",
        style = style,
        color = Color.White,
        modifier = modifier,
    )
}

/** The thin line along the bottom of the video while the controls are hidden: watched (ember) and buffered. */
@Composable
internal fun StageProgressLine(progress: VideoProgress, pending: PendingSeek?, modifier: Modifier = Modifier) {
    val accent = sc.Accent
    Box(
        modifier.fillMaxWidth().height(3.dp).drawBehind {
            val total = progress.durationMs
            if (total <= 0) return@drawBehind
            val f = VideoTime.fraction(shownMs(progress, null, pending), total)
            val buffered = VideoTime.fraction(progress.bufferedMs, total)
            drawRect(Color.White.copy(alpha = 0.22f))
            drawRect(Color.White.copy(alpha = 0.4f), size = size.copy(width = size.width * buffered))
            drawRect(accent, size = size.copy(width = size.width * f))
        },
    )
}
