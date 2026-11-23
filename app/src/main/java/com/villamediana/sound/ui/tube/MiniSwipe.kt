package com.villamediana.sound.ui.tube

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The mini player under the finger: pushed up it opens the video (it rises with the finger), pushed to
 * a side it goes away (it slides and fades, and lets go past a third of its width or with a fling).
 * Short of that it springs back. Taps still reach its buttons.
 */
internal fun Modifier.miniSwipe(onOpen: () -> Unit, onClose: () -> Unit): Modifier = composed {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val open by rememberUpdatedState(onOpen)
    val close by rememberUpdatedState(onClose)
    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    val upThreshold = with(LocalDensity.current) { 56.dp.toPx() }
    this
        .graphicsLayer {
            translationX = x.value
            // Up it follows the finger a little less than 1:1 (it's a pull, not a move).
            translationY = y.value * 0.6f
            val w = size.width.takeIf { it > 0 } ?: 1f
            alpha = 1f - (abs(x.value) / w).coerceIn(0f, 0.85f)
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var axis = 0 // 0 = not decided, 1 = sideways, 2 = up
                var dx = 0f
                var dy = 0f
                while (true) {
                    // Seen before the buttons (Initial pass): once it's a swipe, they see it consumed and don't click.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        if (axis != 0) change.consume()
                        break
                    }
                    val d = change.position - down.position
                    if (axis == 0) {
                        axis = when {
                            abs(d.x) > slop && abs(d.x) > abs(d.y) -> 1
                            d.y < -slop && abs(d.y) > abs(d.x) -> 2
                            else -> 0
                        }
                    }
                    if (axis != 0) {
                        change.consume()
                        dx = d.x
                        dy = d.y.coerceAtMost(0f)
                        scope.launch { if (axis == 1) x.snapTo(dx) else y.snapTo(dy) }
                    }
                }
                if (axis == 0) return@awaitEachGesture
                val v = tracker.calculateVelocity()
                val width = size.width.toFloat()
                if (axis == 1) {
                    val flung = abs(v.x) > 1400f && (v.x > 0) == (dx > 0)
                    if (abs(dx) > width / 3 || flung) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        scope.launch {
                            x.animateTo(if (dx > 0) width else -width, tween(160))
                            close()
                            x.snapTo(0f)
                        }
                    } else {
                        scope.launch { x.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
                    }
                } else {
                    val flung = v.y < -1200f
                    if (-dy > upThreshold || flung) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        open()
                    }
                    scope.launch { y.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                }
            }
        }
}
