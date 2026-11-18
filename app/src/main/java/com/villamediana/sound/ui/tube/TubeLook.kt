package com.villamediana.sound.ui.tube

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import com.villamediana.sound.ui.components.CoverColors
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.Motion
import com.villamediana.sound.ui.theme.sc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/*
 * FreeVideo's look and motion, on top of FreeSong's theme: the shimmer of what is still loading,
 * skeletons shaped like what will come, the press of a card, and the color a thumbnail lends its page.
 */

/** A soft light passing over a surface that is waiting for its content. */
fun Modifier.shimmer(): Modifier = composed {
    val base = sc.Surface1
    val light = sc.Surface3
    val transition = rememberInfiniteTransition(label = "shimmer")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_250, easing = LinearEasing), RepeatMode.Restart), label = "shimmer")
    drawBehind {
        val w = size.width
        val x = -w + t * 3 * w
        drawRect(Brush.linearGradient(listOf(base, light, base), start = Offset(x - w * 0.6f, 0f), end = Offset(x + w * 0.6f, size.height)))
    }
}

/** A card's press: it sinks a little under the finger (no ripple), springs back on release. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null, pressed: Float = 0.975f, onPress: (() -> Unit)? = null): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    // The finger is down: a head start (the video's streams resolve while the tap completes).
    LaunchedEffect(isPressed) { if (isPressed) onPress?.invoke() }
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "press")
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
}

/** A quick pop (1 → 1.25 → 1) each time [key] turns true: the like, the subscribe. */
@Composable
fun rememberPop(key: Boolean): Float {
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (key) {
            scale.animateTo(1.28f, tween(110))
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        }
    }
    return scale.value
}

/** The shape of a feed card while the feed is on its way. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter).padding(top = 6.dp, bottom = 18.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(ThumbShape).shimmer())
        Spacer(Modifier.height(12.dp))
        Row {
            Box(Modifier.size(38.dp).clip(CircleShape).shimmer())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(0.92f).height(15.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.6f).height(15.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(0.42f).height(11.dp).clip(RoundedCornerShape(5.dp)).shimmer())
            }
        }
    }
}

/** The shape of a compact row (suggestions, search, a channel's videos) while it loads. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = Dimens.Gutter, end = Dimens.Gutter, top = 7.dp, bottom = 7.dp)) {
        Box(Modifier.width(164.dp).aspectRatio(16f / 9f).clip(SmallThumbShape).shimmer())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 3.dp)) {
            Box(Modifier.fillMaxWidth().height(13.dp).clip(RoundedCornerShape(5.dp)).shimmer())
            Spacer(Modifier.height(7.dp))
            Box(Modifier.fillMaxWidth(0.7f).height(13.dp).clip(RoundedCornerShape(5.dp)).shimmer())
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(0.45f).height(10.dp).clip(RoundedCornerShape(5.dp)).shimmer())
        }
    }
}

/** A few lines of text on their way (a description, a comment). */
@Composable
fun SkeletonLines(lines: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        repeat(lines) { i ->
            Box(Modifier.fillMaxWidth(if (i == lines - 1) 0.55f else 1f).height(12.dp).clip(RoundedCornerShape(5.dp)).shimmer())
            if (i < lines - 1) Spacer(Modifier.height(8.dp))
        }
    }
}

private val thumbColors = ConcurrentHashMap<String, Int>()

/**
 * The color a thumbnail lends its page (the watch page's veil, a channel's header), tuned as
 * FreeSong tunes cover colors. Null until known (a tiny copy of the image is read once, then cached).
 */
@Composable
fun rememberThumbColor(url: String?): Color? {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf(url?.let { thumbColors[it] }?.let { Color(it) }) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || color != null) return@LaunchedEffect
        val argb = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context).data(url).size(Size(96, 54)).allowHardware(false).build()
                val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@runCatching null
                val bitmap = (result.image.asDrawable(context.resources) as? BitmapDrawable)?.bitmap ?: return@runCatching null
                val palette = Palette.from(bitmap).maximumColorCount(12).generate()
                val swatch = palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch ?: return@runCatching null
                CoverColors.tune(Color(swatch.rgb)).toArgb()
            }.getOrNull()
        } ?: return@LaunchedEffect
        thumbColors[url] = argb
        color = Color(argb)
    }
    return color
}

/** [color] arriving softly (from the page's own background). */
@Composable
fun animatedVeil(color: Color?): Color {
    val target = color ?: sc.Ink
    val c by animateColorAsState(target, tween(700), label = "veil")
    return c
}

/** Two states of a label trading places with a small scale and fade ("Inscrever-se" ↔ "Inscrito"). */
@Composable
fun <T> SwapContent(state: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = { (fadeIn(tween(180)) + scaleIn(tween(220, easing = Motion.Emphasized), initialScale = 0.85f)) togetherWith (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f)) },
        label = "swap",
    ) { content(it) }
}
