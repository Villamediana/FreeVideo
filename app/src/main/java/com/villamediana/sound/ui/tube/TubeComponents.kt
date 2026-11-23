package com.villamediana.sound.ui.tube

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.villamediana.sound.R
import com.villamediana.sound.tube.online.ChannelItem
import com.villamediana.sound.tube.online.PlaylistItem
import com.villamediana.sound.tube.online.VideoItem
import com.villamediana.sound.ui.components.PillButton
import com.villamediana.sound.ui.components.formatDuration
import com.villamediana.sound.ui.components.pressScale
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.sc

internal val ThumbShape = RoundedCornerShape(16.dp)

/** "AO VIVO" on a picture: one warm red that reads on any thumbnail, in both themes. */
private val LiveRed = Color(0xFFC94F43)
internal val SmallThumbShape = RoundedCornerShape(12.dp)

/** Where a card's thumbnail is (root coordinates): the watch page grows out of it when the card is tapped. */
class ThumbBounds {
    var rect: Rect? = null

    /** Just before the card opens its video. */
    fun note() {
        rect?.let(ThumbOrigin::note)
    }
}

/**
 * A 16:9 frame of the video with its length (or "AO VIVO") and how far it was watched. [origin]
 * keeps where it is on screen; [previewing]: the card waits in the middle of the screen, and the
 * thumbnail flips through a few frames of the video.
 */
@Composable
fun VideoThumb(
    video: VideoItem,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    shape: RoundedCornerShape = ThumbShape,
    compact: Boolean = false,
    origin: ThumbBounds? = null,
    previewing: Boolean = false,
) {
    var loaded by remember(video.thumbnailUrl) { mutableStateOf(false) }
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .then(if (origin != null) Modifier.onGloballyPositioned { origin.rect = it.boundsInRoot() } else Modifier)
            .clip(shape)
            .then(if (loaded) Modifier.background(sc.Surface2) else Modifier.shimmer()),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            modifier = Modifier.fillMaxSize(),
        )
        if (previewing && !video.live) Flipbook(video, Modifier.fillMaxSize())
        when {
            video.live -> Badge(stringResource(R.string.tube_live), LiveRed, Color.White, compact)
            video.durationMs > 0 -> Badge(formatDuration(video.durationMs), Color.Black.copy(alpha = 0.78f), Color.White, compact)
        }
        if (progress > 0.01f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.35f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(sc.Accent))
            }
        }
    }
}

@Composable
private fun BoxScope.Badge(text: String, bg: Color, fg: Color, compact: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
        color = fg,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(if (compact) 5.dp else 8.dp)
            .clip(RoundedCornerShape(if (compact) 5.dp else 7.dp))
            .background(bg)
            .padding(horizontal = if (compact) 5.dp else 6.dp, vertical = if (compact) 1.dp else 2.dp),
    )
}

/** The channel's picture in a circle (its initial while there is none). */
@Composable
fun ChannelAvatar(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(if (sc.isLight) sc.Surface3 else sc.Surface2), contentAlignment = Alignment.Center) {
        val initial = name.trim().removePrefix("@").firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        Text(initial, style = if (size >= 56.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium, color = sc.TextMuted)
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Channel name with the verified tick. */
@Composable
fun ChannelName(name: String, verified: Boolean, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium, color: Color = sc.TextMuted) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (verified) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.CheckCircle, null, tint = color, modifier = Modifier.size(13.dp))
        }
    }
}

/**
 * A feed card: the frame across the page, the channel's picture, the title and its facts. Tap
 * opens it, the channel picture opens the channel, ⋮ or a long press opens its menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    onChannel: (() -> Unit)? = null,
    /** False on the channel's own page: no picture, no name (it's theirs). */
    showChannel: Boolean = true,
    /** It waits in the middle of the screen: its thumbnail flips through the video. */
    previewing: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val res = resources()
    val origin = remember { ThumbBounds() }
    Column(
        modifier
            .fillMaxWidth()
            .pressable(
                onPress = { com.villamediana.sound.SoundApplication.graph.tube.player.warm(video.id) },
                onClick = {
                    origin.note()
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMore()
                },
            )
            .padding(horizontal = Dimens.Gutter)
            .padding(top = 6.dp, bottom = 18.dp),
    ) {
        VideoThumb(video, Modifier.fillMaxWidth(), progress = progress, origin = origin, previewing = previewing)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Top) {
            if (showChannel) {
                ChannelAvatar(
                    video.channelAvatar,
                    video.channelName,
                    38.dp,
                    Modifier.then(if (onChannel != null) Modifier.clip(CircleShape).clickable(onClick = onChannel) else Modifier),
                )
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.width(2.dp))
            }
            Column(Modifier.weight(1f).padding(top = 1.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, color = sc.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(TubeFormat.meta(res, video, withChannel = showChannel), style = MaterialTheme.typography.bodySmall, color = sc.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            MoreButton(onMore)
        }
    }
}

/** A compact row: small frame on the left, title and facts on the right (suggestions, search, lists). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoRow(
    video: VideoItem,
    onClick: () -> Unit,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    showChannel: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val res = resources()
    val origin = remember { ThumbBounds() }
    Row(
        modifier
            .fillMaxWidth()
            .pressable(
                onPress = { com.villamediana.sound.SoundApplication.graph.tube.player.warm(video.id) },
                onClick = {
                    origin.note()
                    onClick()
                },
                onLongClick = onMore?.let { more ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        more()
                    }
                },
                pressed = 0.985f,
            )
            .padding(start = Dimens.Gutter, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        leading?.invoke()
        VideoThumb(video, Modifier.width(164.dp), progress = progress, shape = SmallThumbShape, compact = true, origin = origin)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 1.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, color = sc.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            if (showChannel && video.channelName.isNotBlank()) {
                ChannelName(video.channelName, video.verified, style = MaterialTheme.typography.bodySmall)
            }
            val facts = TubeFormat.meta(res, video, withChannel = false)
            if (facts.isNotBlank()) Text(facts, style = MaterialTheme.typography.bodySmall, color = sc.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onMore != null) MoreButton(onMore)
    }
}

/** A narrow card for horizontal shelves (Continuar assistindo, the library's rows). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfVideo(video: VideoItem, onClick: () -> Unit, onMore: (() -> Unit)?, modifier: Modifier = Modifier, progress: Float = 0f) {
    val haptic = LocalHapticFeedback.current
    val origin = remember { ThumbBounds() }
    Column(
        modifier
            .width(212.dp)
            .pressable(
                onPress = { com.villamediana.sound.SoundApplication.graph.tube.player.warm(video.id) },
                onClick = {
                    origin.note()
                    onClick()
                },
                onLongClick = onMore?.let { more ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        more()
                    }
                },
            ),
    ) {
        VideoThumb(video, Modifier.fillMaxWidth(), progress = progress, shape = SmallThumbShape, compact = true, origin = origin)
        Spacer(Modifier.height(8.dp))
        Text(video.title, style = MaterialTheme.typography.titleSmall, color = sc.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (video.channelName.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(video.channelName, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MoreButton(onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = sc.TextMuted) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(40.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MoreVert, stringResource(R.string.tube_more), tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** A channel in a list: picture, name, subscribers and the follow button. */
@Composable
fun ChannelRow(channel: ChannelItem, subscribed: Boolean, onClick: () -> Unit, onSubscribe: () -> Unit, modifier: Modifier = Modifier) {
    val res = resources()
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Dimens.Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(channel.avatarUrl, channel.name, 64.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            ChannelName(channel.name, channel.verified, style = MaterialTheme.typography.titleMedium, color = sc.Text)
            val facts = listOfNotNull(TubeFormat.subscribers(res, channel.subscribers), TubeFormat.videoCount(res, channel.videos)).joinToString(" · ")
            if (facts.isNotBlank()) Text(facts, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        SubscribeButton(subscribed, onSubscribe)
    }
}

/**
 * "Inscrever-se" (the page's ink turned over: white on dark, black on light) ↔ "Inscrito" (quiet
 * grey, with a tick): the colors glide and the label swaps. [big]: the channel page's wide button.
 */
@Composable
fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, big: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (subscribed) sc.Surface1 else sc.Text, tween(260), label = "subBg")
    val fg by animateColorAsState(if (subscribed) sc.Text else sc.Ink, tween(260), label = "subFg")
    val pop = rememberPop(subscribed)
    Box(
        modifier
            .height(if (big) 46.dp else 38.dp)
            .graphicsLayer {
                scaleX = pop
                scaleY = pop
            }
            .pressScale(interaction, 0.95f)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (big) 22.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        SwapContent(subscribed) { on ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (on) {
                    Icon(Icons.Rounded.Check, null, tint = fg, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    stringResource(if (on) R.string.tube_subscribed else R.string.tube_subscribe),
                    style = if (big) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelLarge,
                    color = fg,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A playlist in a list. */
@Composable
fun PlaylistRow(playlist: PlaylistItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val res = resources()
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = Dimens.Gutter, end = Dimens.Gutter, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(164.dp).aspectRatio(16f / 9f).clip(SmallThumbShape).background(sc.Surface2)) {
            AsyncImage(model = playlist.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Row(
                Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 5.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(Icons.Rounded.PlaylistPlay, null, tint = Color.White, modifier = Modifier.size(14.dp))
                if (playlist.count >= 0) Text(TubeFormat.compact(playlist.count), style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, color = sc.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val facts = listOfNotNull(playlist.uploader, stringResource(R.string.tube_playlist), TubeFormat.videoCount(res, playlist.count)).joinToString(" · ")
            Text(facts, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A quiet spinner row at the end of a list that's loading more. */
@Composable
fun LoadingRow(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = sc.Accent, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
    }
}
