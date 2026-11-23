package com.villamediana.sound.ui.tube

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.villamediana.sound.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The video over the pages, as one thing that changes shape: the watch page ([expansion] = 1) and the
 * mini player ([expansion] = 0) are the same video and the same surface — it shrinks into the bar and
 * grows back out of it, following the finger in between. Opened from a card, it grows out of that
 * card's thumbnail ([origin], [appear]).
 */
@Stable
internal class WatchSheet(private val scope: CoroutineScope) {
    /** The video on the page (null = nothing open). */
    var route by mutableStateOf<WatchRoute?>(null)
        private set

    /** 0 = the mini player, 1 = the watch page. */
    val expansion = Animatable(0f)

    /** Opening from a card: the thumbnail's box (root coordinates) the video grows out of. */
    var origin by mutableStateOf<Rect?>(null)
        private set

    /** 0 → 1 while the video grows out of [origin]. */
    val appear = Animatable(1f)

    /** How far (px) the video travels between the page and the bar (set by the layout; drags are measured against it). */
    var travel by mutableFloatStateOf(1f)

    /** The bar pushed sideways (px), on its way to closing or back. */
    val barShift = Animatable(0f)

    /** Where the finger has the page now (while a drag goes on; null = no drag). */
    private var dragged: Float? = null

    /** A finger is moving the page. */
    val dragging: Boolean get() = dragged != null

    /** The page is (or is going to be) open, not the bar. */
    val open: Boolean get() = route != null && expansion.targetValue > 0.5f

    private var job: Job? = null

    /** Shows [next]: out of [from] (a card) when nothing was open on the page, else the page as it is. */
    fun open(next: WatchRoute, from: Rect?) {
        val wasPage = route != null && open
        dragged = null
        job?.cancel()
        if (wasPage) {
            // Another video from the page itself (a suggestion): the page stays, the video changes.
            route = next
            job = scope.launch {
                origin = null
                appear.snapTo(1f)
                expansion.animateTo(1f, SETTLE)
            }
            return
        }
        job = scope.launch {
            // Everything in place before the new video is shown, so the bar never flashes first.
            barShift.snapTo(0f)
            if (from != null && !from.isEmpty) {
                expansion.snapTo(1f)
                appear.snapTo(0f)
                origin = from
                route = next
                appear.animateTo(1f, GROW)
                origin = null
            } else {
                origin = null
                appear.snapTo(1f)
                if (route == null) expansion.snapTo(0f)
                route = next
                expansion.animateTo(1f, SETTLE)
            }
        }
    }

    /** The player moved on by itself (autoplay): the page follows, as open or small as it is. */
    fun follow(next: WatchRoute) {
        if (route?.id != next.id) route = next
    }

    fun expand() = settle(1f)

    fun collapse() {
        if (route == null || expansion.targetValue == 0f && !expansion.isRunning) return
        settle(0f)
    }

    /** The finger moved the page to [fraction] (0 = bar, 1 = page). */
    fun dragTo(fraction: Float) {
        if (route == null) return
        val f = fraction.coerceIn(0f, 1f)
        dragged = f
        job?.cancel()
        job = scope.launch {
            if (appear.value < 1f) appear.snapTo(1f)
            origin = null
            expansion.snapTo(f)
        }
    }

    /** …by [delta] from where it is. */
    fun dragBy(delta: Float) = dragTo((dragged ?: expansion.value) + delta)

    /** The bar follows a sideways finger. */
    fun shiftTo(x: Float) {
        scope.launch { barShift.snapTo(x) }
    }

    /** Let go far enough: the bar slides off that side and the video closes. */
    fun shiftAway(right: Boolean, onClose: () -> Unit) {
        scope.launch {
            barShift.animateTo(if (right) travelWidth else -travelWidth, tween(160))
            onClose()
            barShift.snapTo(0f)
        }
    }

    fun shiftBack() {
        scope.launch { barShift.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
    }

    /** The screen's width (px), for the bar to leave by. */
    var travelWidth by mutableFloatStateOf(1080f)

    /** Let go: to the page or to the bar, whichever it was heading to. */
    fun release(towardsPage: Boolean) = settle(if (towardsPage) 1f else 0f)

    private fun settle(target: Float, spec: AnimationSpec<Float> = SETTLE) {
        if (route == null) return
        dragged = null
        job?.cancel()
        job = scope.launch {
            if (appear.value < 1f) appear.snapTo(1f)
            origin = null
            expansion.animateTo(target, spec)
        }
    }

    /** Nothing open anymore (closed from the bar). */
    fun close() {
        job?.cancel()
        dragged = null
        route = null
        origin = null
        scope.launch {
            expansion.snapTo(0f)
            appear.snapTo(1f)
            barShift.snapTo(0f)
        }
    }

    companion object {
        val SETTLE: AnimationSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 420f)
        val GROW: AnimationSpec<Float> = tween(380, easing = Motion.Emphasized)
    }
}

/**
 * The thumbnail last tapped (root coordinates), for the watch page to grow out of. A card notes it
 * just before it opens its video; it is good for a moment only.
 */
internal object ThumbOrigin {
    private var rect: Rect? = null
    private var at = 0L

    fun note(r: Rect) {
        rect = r
        at = SystemClock.uptimeMillis()
    }

    fun take(): Rect? {
        val r = rect?.takeIf { SystemClock.uptimeMillis() - at < 800 }
        rect = null
        return r
    }
}
