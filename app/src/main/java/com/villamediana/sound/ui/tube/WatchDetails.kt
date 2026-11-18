package com.villamediana.sound.ui.tube

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.online.CommentItem
import com.villamediana.sound.ui.components.pressScale
import com.villamediana.sound.ui.sheets.SheetTitle
import com.villamediana.sound.ui.sheets.SoundSheet
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.sc
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Under the video: title and facts, actions, the channel, description, comments and what's next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchDetails(route: WatchRoute, vm: WatchViewModel, modifier: Modifier = Modifier) {
    val tube = SoundApplication.graph.tube
    val nav = LocalTubeNav.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    val video = state.video ?: vm.initial
    val res = resources()
    val liked by remember(route.id) { tube.library.isLiked(route.id) }.collectAsState(initial = false)
    val later by remember(route.id) { tube.library.isInLater(route.id) }.collectAsState(initial = false)
    val subscribed by remember(video.channelUrl, video.channelName) { tube.library.isSubscribed(video.channelUrl, video.channelName) }
        .collectAsState(initial = false)
    val autoplay by remember { tube.settings.prefs.map { it.autoplay } }.collectAsState(initial = true)
    val watched by remember { tube.library.history.map { list -> list.associate { it.videoId to it.progress } } }.collectAsState(initial = emptyMap())
    var disliked by rememberSaveable(route.id) { mutableStateOf(false) }
    var expanded by rememberSaveable(route.id) { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<VideoMenuTarget?>(null) }

    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        // Title and facts (tap: the description opens).
        item(key = "title") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Dimens.Gutter)
                    .padding(top = 12.dp, bottom = 10.dp),
            ) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = sc.Text,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val facts = TubeFormat.meta(res, video, withChannel = false)
                if (facts.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(facts, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Channel + follow.
        item(key = "channel") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = video.channelUrl != null) { video.channelUrl?.let { nav.openChannel(it, video.channelName, video.channelAvatar) } }
                    .padding(horizontal = Dimens.Gutter, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChannelAvatar(video.channelAvatar, video.channelName, 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    ChannelName(video.channelName, video.verified, style = MaterialTheme.typography.titleMedium, color = sc.Text)
                    TubeFormat.subscribers(res, state.details?.channelSubscribers ?: -1)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted, maxLines = 1)
                    }
                }
                if (video.channelUrl != null) {
                    SubscribeButton(subscribed, onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { tube.library.toggleSubscription(video.channelUrl, video.channelName, video.channelAvatar) }
                    })
                }
            }
        }
        // Actions.
        item(key = "actions") {
            LazyRow(
                Modifier.padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = Dimens.Gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    val likes = state.details?.likes?.takeIf { it > 0 }
                    ActionPill(
                        icon = if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                        // Just the count (the thumb says the rest); no count known → the icon alone.
                        text = likes?.let { TubeFormat.compact(it + if (liked) 1 else 0) },
                        description = stringResource(if (liked) R.string.tube_liked else R.string.tube_like),
                        selected = liked,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            disliked = false
                            scope.launch { tube.library.toggleLike(video) }
                        },
                    )
                }
                item {
                    ActionPill(
                        icon = if (disliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                        text = null,
                        description = stringResource(R.string.tube_not_interested),
                        selected = disliked,
                        onClick = {
                            disliked = !disliked
                            scope.launch {
                                if (disliked) {
                                    if (liked) tube.library.toggleLike(video)
                                    tube.library.dismissVideo(video)
                                } else {
                                    tube.library.undoDismissal("v:${video.id}")
                                }
                            }
                        },
                    )
                }
                item {
                    ActionPill(
                        icon = if (later) Icons.Rounded.WatchLater else Icons.Rounded.Schedule,
                        text = null,
                        description = stringResource(if (later) R.string.tube_saved else R.string.tube_save),
                        selected = later,
                        onClick = { scope.launch { tube.library.toggleLater(video) } },
                    )
                }
                item {
                    ActionPill(icon = Icons.Rounded.Share, text = null, description = stringResource(R.string.tube_share), selected = false, onClick = { shareVideo(context, video) })
                }
            }
        }
        // Description.
        item(key = "description") {
            val text = state.details?.description.orEmpty()
            if (text.isNotBlank() || state.details?.tags?.isNotEmpty() == true) {
                Column(
                    Modifier
                        .padding(horizontal = Dimens.Gutter, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(sc.Surface1)
                        .clickable { expanded = !expanded }
                        .padding(14.dp)
                        .animateContentSize(),
                ) {
                    val tags = state.details?.tags.orEmpty().take(4).joinToString("  ") { "#" + it.replace(" ", "") }
                    if (tags.isNotBlank()) {
                        Text(tags, style = MaterialTheme.typography.labelMedium, color = sc.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (expanded) {
                        SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium, color = sc.Text) }
                    } else {
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = sc.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(if (expanded) R.string.tube_show_less else R.string.tube_show_more),
                        style = MaterialTheme.typography.labelLarge,
                        color = sc.TextMuted,
                    )
                }
            } else if (state.details == null && !state.detailsError) {
                Column(
                    Modifier.padding(horizontal = Dimens.Gutter, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(sc.Surface1).padding(14.dp),
                ) { SkeletonLines(3) }
            } else if (state.detailsError) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tube_details_error), style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted, modifier = Modifier.weight(1f))
                    RetryPill(vm::loadDetails)
                }
            }
        }
        // Comments (a preview; the sheet has them all).
        item(key = "comments") {
            CommentsCard(state, onOpen = { showComments = true }, onRetry = vm::loadComments)
        }
        // What's next.
        if (state.related.isNotEmpty()) {
            item(key = "upnext") {
                Row(
                    Modifier.fillMaxWidth().padding(start = Dimens.Gutter, end = 12.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.tube_up_next), style = MaterialTheme.typography.headlineSmall, color = sc.Text, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Autorenew, stringResource(R.string.tube_autoplay), tint = if (autoplay) sc.Accent else sc.TextFaint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = autoplay,
                        onCheckedChange = { on -> scope.launch { tube.settings.setAutoplay(on) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = sc.Accent, checkedThumbColor = sc.OnAccent, uncheckedTrackColor = sc.Surface3, uncheckedBorderColor = sc.Hairline),
                    )
                }
            }
            items(state.related, key = { "r:" + it.id }) { v ->
                VideoRow(
                    video = v,
                    onClick = { nav.openVideo(v) },
                    onMore = { menu = VideoMenuTarget(v) },
                    progress = watched[v.id] ?: 0f,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (state.details == null && !state.detailsError) {
            items(5, key = { "rsk$it" }) { SkeletonRow() }
        }
        item(key = "bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }

    if (showComments) {
        CommentsSheet(state, onMore = vm::moreComments, onClose = { showComments = false })
    }
    menu?.let { target ->
        VideoActionsSheet(target, onClose = { menu = null }, onDismissed = { v, _ -> vm.drop(v) })
    }
}

@Composable
private fun ActionPill(icon: ImageVector, text: String?, description: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) sc.AccentSoft else sc.Surface1, tween(220), label = "pillBg")
    val fg by animateColorAsState(if (selected) sc.Accent else sc.Text, tween(220), label = "pillFg")
    val pop = rememberPop(selected)
    Row(
        Modifier
            .height(40.dp)
            .pressScale(interaction, 0.94f)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = if (text == null) 12.dp else 14.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp).graphicsLayer {
            scaleX = pop
            scaleY = pop
        })
        if (text != null) {
            Spacer(Modifier.width(7.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun CommentsCard(state: WatchViewModel.UiState, onOpen: () -> Unit, onRetry: () -> Unit) {
    val first = state.comments.firstOrNull()
    Column(
        Modifier
            .padding(horizontal = Dimens.Gutter, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(sc.Surface1)
            .clickable(enabled = first != null, onClick = onOpen)
            .padding(14.dp),
    ) {
        Text(stringResource(R.string.tube_comments), style = MaterialTheme.typography.titleMedium, color = sc.Text)
        Spacer(Modifier.height(8.dp))
        when {
            first != null -> Row(verticalAlignment = Alignment.Top) {
                ChannelAvatar(first.authorAvatar, first.author, 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(first.text, style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            state.commentsLoading -> SkeletonLines(2)
            state.commentsError -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tube_comments_error), style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.tube_retry),
                    style = MaterialTheme.typography.labelLarge,
                    color = sc.Accent,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onRetry).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            state.commentsPage != null -> Text(stringResource(R.string.tube_comments_none), style = MaterialTheme.typography.bodyMedium, color = sc.TextFaint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(state: WatchViewModel.UiState, onMore: () -> Unit, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } == true } }
    LaunchedEffect(nearEnd) { if (nearEnd) onMore() }
    SoundSheet(onDismissRequest = onClose, sheetState = sheetState) {
        SheetTitle(stringResource(R.string.tube_comments))
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            items(state.comments, key = { it.id }) { CommentRow(it) }
            if (state.commentsLoading) item { LoadingRow() }
        }
    }
}

@Composable
private fun CommentRow(c: CommentItem) {
    val res = resources()
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        ChannelAvatar(c.authorAvatar, c.author, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (c.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PushPin, null, tint = sc.TextFaint, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.tube_pinned), style = MaterialTheme.typography.labelSmall, color = sc.TextFaint)
                }
                Spacer(Modifier.height(2.dp))
            }
            Text(
                listOfNotNull(c.author, c.published).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = if (c.byUploader) sc.Accent else sc.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            SelectionContainer { Text(c.text, style = MaterialTheme.typography.bodyMedium, color = sc.Text) }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ThumbUp, null, tint = sc.TextFaint, modifier = Modifier.size(14.dp))
                if (c.likes > 0) {
                    Spacer(Modifier.width(5.dp))
                    Text(TubeFormat.compact(c.likes), style = MaterialTheme.typography.labelMedium, color = sc.TextFaint)
                }
                if (c.hearted) {
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Rounded.Favorite, null, tint = sc.Danger, modifier = Modifier.size(14.dp))
                }
                if (c.replies > 0) {
                    Spacer(Modifier.width(12.dp))
                    Text(res.getQuantityString(R.plurals.tube_replies, c.replies, c.replies), style = MaterialTheme.typography.labelMedium, color = sc.TextFaint)
                }
            }
        }
    }
}
