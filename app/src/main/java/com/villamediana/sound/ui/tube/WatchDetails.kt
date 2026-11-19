package com.villamediana.sound.ui.tube

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.online.CommentItem
import com.villamediana.sound.tube.online.VideoItem
import com.villamediana.sound.ui.components.pressScale
import com.villamediana.sound.ui.sheets.SoundSheet
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.sc
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Under the video: title and facts, the channel, actions, description, comments and what's next. */
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
    var showDescription by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<VideoMenuTarget?>(null) }

    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        // Title and facts; "…mais" opens the description.
        item(key = "title") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { showDescription = true }
                    .padding(horizontal = Dimens.Gutter)
                    .padding(top = 14.dp, bottom = 6.dp),
            ) {
                Text(video.title, style = MaterialTheme.typography.titleLarge, color = sc.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                val facts = TubeFormat.meta(res, video, withChannel = false)
                val more = stringResource(R.string.tube_more_short)
                val strong = sc.Text
                Text(
                    buildAnnotatedString {
                        if (facts.isNotBlank()) append("$facts  ")
                        withStyle(SpanStyle(color = strong, fontWeight = FontWeight.Bold)) { append(more) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = sc.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Channel + follow.
        item(key = "channel") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = video.channelUrl != null) { video.channelUrl?.let { nav.openChannel(it, video.channelName, video.channelAvatar) } }
                    .padding(horizontal = Dimens.Gutter, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChannelAvatar(video.channelAvatar, video.channelName, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    ChannelName(video.channelName, video.verified, style = MaterialTheme.typography.titleSmall, color = sc.Text)
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
        // Actions: icons only (TalkBack reads each name).
        item(key = "actions") {
            LazyRow(
                Modifier.padding(top = 4.dp, bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = Dimens.Gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    LikePill(
                        liked = liked,
                        disliked = disliked,
                        likes = state.details?.likes?.takeIf { it > 0 }?.let { it + if (liked) 1 else 0 },
                        onLike = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            disliked = false
                            scope.launch { tube.library.toggleLike(video) }
                        },
                        onDislike = {
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
                    ActionIcon(
                        icon = if (later) Icons.Rounded.WatchLater else Icons.Rounded.Schedule,
                        description = stringResource(if (later) R.string.tube_saved else R.string.tube_save),
                        selected = later,
                        onClick = { scope.launch { tube.library.toggleLater(video) } },
                    )
                }
                item {
                    ActionIcon(icon = Icons.Rounded.Share, description = stringResource(R.string.tube_share), selected = false, onClick = { shareVideo(context, video) })
                }
            }
        }
        // Description preview.
        item(key = "description") {
            val text = state.details?.description.orEmpty()
            val tags = state.details?.tags.orEmpty()
            when {
                text.isNotBlank() || tags.isNotEmpty() -> Card(onClick = { showDescription = true }) {
                    if (tags.isNotEmpty()) {
                        Text(tags.take(4).joinToString("  ") { "#" + it.replace(" ", "") }, style = MaterialTheme.typography.labelLarge, color = sc.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyMedium, color = sc.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                state.details == null && !state.detailsError -> Card(onClick = null) { SkeletonLines(3) }
                state.detailsError -> Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tube_details_error), style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted, modifier = Modifier.weight(1f))
                    RetryPill(vm::loadDetails)
                }
            }
        }
        // Comments (the first one; the sheet has them all).
        item(key = "comments") {
            CommentsCard(state, onOpen = { showComments = true }, onRetry = vm::loadComments)
        }
        // What's next.
        if (state.related.isNotEmpty()) {
            item(key = "upnext") {
                val autoplayLabel = stringResource(R.string.tube_autoplay)
                Row(
                    Modifier.fillMaxWidth().padding(start = Dimens.Gutter, end = 14.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.tube_up_next), style = MaterialTheme.typography.headlineSmall, color = sc.Text, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Autorenew, null, tint = if (autoplay) sc.Accent else sc.TextFaint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    TubeSwitch(autoplay, onChange = { on -> scope.launch { tube.settings.setAutoplay(on) } }, modifier = Modifier.semantics { contentDescription = autoplayLabel })
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

    if (showDescription) DescriptionSheet(video, state, onClose = { showDescription = false })
    if (showComments) {
        CommentsSheet(state, vm, onClose = { showComments = false })
    }
    menu?.let { target ->
        VideoActionsSheet(target, onClose = { menu = null }, onDismissed = { v, _ -> vm.drop(v) })
    }
}

/** A soft rounded card on the page, tappable when [onClick] is given. */
@Composable
private fun Card(onClick: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = Dimens.Gutter, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(sc.Surface1)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content,
    )
}

/** Like (with its count) and dislike sharing one pill, split by a hairline. */
@Composable
private fun LikePill(liked: Boolean, disliked: Boolean, likes: Long?, onLike: () -> Unit, onDislike: () -> Unit) {
    val likeTint by animateColorAsState(if (liked) sc.Accent else sc.Text, tween(220), label = "like")
    val dislikeTint by animateColorAsState(if (disliked) sc.Accent else sc.Text, tween(220), label = "dislike")
    val pop = rememberPop(liked)
    val dislikePop = rememberPop(disliked)
    val likeLabel = stringResource(if (liked) R.string.tube_liked else R.string.tube_like)
    val dislikeLabel = stringResource(R.string.tube_not_interested)
    Row(Modifier.height(38.dp).clip(CircleShape).background(sc.Surface1), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .fillMaxHeight()
                .clickable(onClickLabel = likeLabel, role = Role.Button, onClick = onLike)
                .semantics { contentDescription = likeLabel }
                .padding(start = 14.dp, end = 12.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, null, tint = likeTint, modifier = Modifier.size(19.dp).graphicsLayer {
                scaleX = pop
                scaleY = pop
            })
            if (likes != null) {
                Spacer(Modifier.width(7.dp))
                Text(TubeFormat.compact(likes), style = MaterialTheme.typography.labelLarge, color = likeTint, maxLines = 1)
            }
        }
        Box(Modifier.width(1.dp).height(20.dp).background(sc.Hairline))
        Box(
            Modifier
                .fillMaxHeight()
                .clickable(onClickLabel = dislikeLabel, role = Role.Button, onClick = onDislike)
                .semantics { contentDescription = dislikeLabel }
                .padding(start = 12.dp, end = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (disliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown, null, tint = dislikeTint, modifier = Modifier.size(19.dp).graphicsLayer {
                scaleX = dislikePop
                scaleY = dislikePop
            })
        }
    }
}

/** An icon-only action in a round pill, the ember when it's on. */
@Composable
private fun ActionIcon(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) sc.AccentSoft else sc.Surface1, tween(220), label = "actBg")
    val fg by animateColorAsState(if (selected) sc.Accent else sc.Text, tween(220), label = "actFg")
    val pop = rememberPop(selected)
    Box(
        Modifier
            .height(38.dp)
            .width(52.dp)
            .pressScale(interaction, 0.92f)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp).graphicsLayer {
            scaleX = pop
            scaleY = pop
        })
    }
}

/** "Comentários 1,2 mil" and the top comment; opens the sheet with all of them. */
@Composable
private fun CommentsCard(state: WatchViewModel.UiState, onOpen: () -> Unit, onRetry: () -> Unit) {
    val first = state.comments.firstOrNull()
    Card(onClick = if (first != null) onOpen else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tube_comments), style = MaterialTheme.typography.titleSmall, color = sc.Text)
            if (state.commentsTotal > 0) {
                Spacer(Modifier.width(8.dp))
                Text(TubeFormat.compact(state.commentsTotal), style = MaterialTheme.typography.bodySmall, color = sc.TextMuted)
            }
            Spacer(Modifier.weight(1f))
            if (first != null) Icon(Icons.Rounded.UnfoldMore, null, tint = sc.TextMuted, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        when {
            first != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelAvatar(first.authorAvatar, first.author, 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(first.text, style = MaterialTheme.typography.bodyMedium, color = sc.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            state.commentsLoading -> SkeletonLines(2)
            state.commentsError -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tube_comments_error), style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted, modifier = Modifier.weight(1f))
                TubeButton(stringResource(R.string.tube_retry), onClick = onRetry, kind = TubeButtonKind.Ghost)
            }
            state.commentsPage != null -> Text(stringResource(R.string.tube_comments_none), style = MaterialTheme.typography.bodyMedium, color = sc.TextFaint)
        }
    }
}

// ------------------------------------------------------------------ description

/** The whole description: the numbers as big figures, the tags and the text (selectable). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DescriptionSheet(video: VideoItem, state: WatchViewModel.UiState, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val details = state.details
    SoundSheet(onDismissRequest = onClose, sheetState = sheetState) {
        SheetTop(stringResource(R.string.tube_description), count = null, onClose = onClose)
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.85f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
            item {
                Text(video.title, style = MaterialTheme.typography.titleLarge, color = sc.Text)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    details?.likes?.takeIf { it > 0 }?.let { Figure(TubeFormat.compact(it), stringResource(R.string.tube_figure_likes)) }
                    if (video.views >= 0) Figure(TubeFormat.compact(video.views), stringResource(if (video.live) R.string.tube_figure_watching else R.string.tube_figure_views))
                    TubeFormat.age(video)?.let { Figure(it, stringResource(R.string.tube_figure_posted)) }
                }
                Spacer(Modifier.height(18.dp))
            }
            val tags = details?.tags.orEmpty()
            if (tags.isNotEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.take(12).forEach { t ->
                            Text(
                                "#" + t.replace(" ", ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = sc.Accent,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(sc.AccentSoft).padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            item {
                val text = details?.description.orEmpty()
                if (text.isNotBlank()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(sc.Surface1).padding(16.dp)) {
                        SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium, color = sc.Text) }
                    }
                } else if (details == null) {
                    SkeletonLines(5)
                }
            }
        }
    }
}

@Composable
private fun Figure(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = sc.Text, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted, maxLines = 1)
    }
}

/** A sheet's head: title, a count, and × — over a hairline. */
@Composable
private fun SheetTop(title: String, count: String?, onClose: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = sc.Text)
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                Text(count, style = MaterialTheme.typography.titleSmall, color = sc.TextMuted)
            }
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Rounded.Close, stringResource(R.string.tube_close), onClose, size = 40.dp, iconSize = 22.dp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sc.Hairline))
    }
}

// ------------------------------------------------------------------ comments

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(state: WatchViewModel.UiState, vm: WatchViewModel, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } == true } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.moreComments() }
    SoundSheet(onDismissRequest = onClose, sheetState = sheetState) {
        SheetTop(stringResource(R.string.tube_comments), count = state.commentsTotal.takeIf { it > 0 }?.let { TubeFormat.compact(it) }, onClose = onClose)
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f), contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)) {
            state.comments.forEach { c ->
                item(key = c.id) {
                    CommentRow(c, avatar = 36.dp, onReplies = if (c.repliesCursor != null && c.replies != 0) ({ vm.toggleReplies(c) }) else null, repliesOpen = state.replies[c.id]?.open == true)
                }
                val replies = state.replies[c.id]
                if (replies != null && replies.open) {
                    replies.items.forEach { r ->
                        item(key = c.id + "/" + r.id) { CommentRow(r, avatar = 26.dp, indent = 46.dp, onReplies = null, repliesOpen = false) }
                    }
                    when {
                        replies.loading -> item(key = c.id + "/loading") { Box(Modifier.padding(start = 92.dp, end = 22.dp, top = 6.dp, bottom = 10.dp)) { SkeletonLines(2) } }
                        replies.error -> item(key = c.id + "/error") {
                            Box(Modifier.padding(start = 82.dp, bottom = 6.dp)) { TubeButton(stringResource(R.string.tube_retry), onClick = { vm.toggleReplies(c) }, kind = TubeButtonKind.Ghost) }
                        }
                        replies.page?.hasMore == true -> item(key = c.id + "/more") {
                            Row(
                                Modifier.padding(start = 76.dp, bottom = 6.dp).clip(CircleShape).clickable { vm.moreReplies(c) }.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.SubdirectoryArrowRight, null, tint = sc.Accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.tube_more_replies), style = MaterialTheme.typography.labelLarge, color = sc.Accent)
                            }
                        }
                    }
                }
            }
            if (state.commentsLoading) items(3, key = { "csk$it" }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).shimmer())
                    Spacer(Modifier.width(12.dp))
                    SkeletonLines(3, Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * One comment: picture, author (a pill when it's the channel itself) and when, the text (long ones
 * fold after a few lines; tap to read all), likes, the channel's heart, and its replies to unfold.
 */
@Composable
private fun CommentRow(c: CommentItem, avatar: Dp, onReplies: (() -> Unit)?, repliesOpen: Boolean, indent: Dp = 0.dp) {
    val res = resources()
    var open by rememberSaveable(c.id) { mutableStateOf(false) }
    var long by remember(c.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 22.dp + indent, end = 22.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        ChannelAvatar(c.authorAvatar, c.author, avatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (c.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PushPin, null, tint = sc.TextMuted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.tube_pinned), style = MaterialTheme.typography.labelMedium, color = sc.TextMuted)
                }
                Spacer(Modifier.height(3.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.byUploader) {
                    Text(
                        c.author,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = sc.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clip(CircleShape).background(sc.TextMuted).padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                } else {
                    Text(c.author, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = sc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (c.verified) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Rounded.CheckCircle, null, tint = sc.TextMuted, modifier = Modifier.size(12.dp))
                }
                c.published?.let {
                    Text("  ·  $it", style = MaterialTheme.typography.labelMedium, color = sc.TextFaint, maxLines = 1)
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(Modifier.animateContentSize().clickable(enabled = long || open, indication = null, interactionSource = remember { MutableInteractionSource() }) { open = !open }) {
                SelectionContainer {
                    Text(
                        c.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = sc.Text,
                        maxLines = if (open) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!open) long = it.hasVisualOverflow },
                    )
                }
                if (long || open) {
                    Text(
                        stringResource(if (open) R.string.tube_show_less else R.string.tube_read_more),
                        style = MaterialTheme.typography.labelLarge,
                        color = sc.TextMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ThumbUp, null, tint = sc.TextMuted, modifier = Modifier.size(15.dp))
                if (c.likes > 0) {
                    Spacer(Modifier.width(5.dp))
                    Text(TubeFormat.compact(c.likes), style = MaterialTheme.typography.labelMedium, color = sc.TextMuted)
                }
                if (c.hearted) {
                    Spacer(Modifier.width(14.dp))
                    Icon(Icons.Rounded.Favorite, stringResource(R.string.tube_hearted), tint = sc.Accent, modifier = Modifier.size(15.dp))
                }
            }
            if (onReplies != null) {
                val turn by animateFloatAsState(if (repliesOpen) 180f else 0f, tween(220), label = "replies")
                Row(
                    Modifier.padding(top = 4.dp).offset(x = (-10).dp).clip(CircleShape).clickable(onClick = onReplies).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = sc.Accent, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn })
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (c.replies > 0) res.getQuantityString(R.plurals.tube_replies, c.replies, c.replies) else stringResource(R.string.tube_replies_some),
                        style = MaterialTheme.typography.labelLarge,
                        color = sc.Accent,
                    )
                }
            }
        }
    }
}
