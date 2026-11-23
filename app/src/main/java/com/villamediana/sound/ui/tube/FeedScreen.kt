package com.villamediana.sound.ui.tube

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.villamediana.sound.ui.components.pressScale
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.villamediana.sound.R
import com.villamediana.sound.tube.feed.FeedEngine
import com.villamediana.sound.tube.feed.TopicChip
import com.villamediana.sound.tube.online.Kiosks
import com.villamediana.sound.tube.online.VideoItem
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.LocalBottomInset
import com.villamediana.sound.ui.theme.sc

/** Início: the feed, made on the phone from what the person watches ([FeedEngine]). It never ends. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(vm: FeedViewModel = viewModel()) {
    val nav = LocalTubeNav.current
    val state by vm.state.collectAsState()
    val watched by vm.watched.collectAsState()
    val continueList by vm.continueWatching.collectAsState()
    val askInterests by vm.askInterests.collectAsState()
    val ownTopics by vm.ownTopics.collectAsState()
    val chips = rememberChips(ownTopics)
    var menu by remember { mutableStateOf<VideoMenuTarget?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // The end of the scroll is near: more of the feed (and again, after a pause, when nothing came).
    val shownCount = if (state.chip != null) state.chipEntries.size else state.entries.size
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 5 } == true } }
    LaunchedEffect(nearEnd, shownCount, state.stalled) { if (nearEnd && !state.stalled) vm.loadMore() }

    // "Início" tapped again: up to the top; already there: a new feed.
    LaunchedEffect(Unit) {
        nav.homeAgain.collect {
            if (list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < 40) vm.refresh()
            else {
                if (list.firstVisibleItemIndex > 12) list.scrollToItem(6)
                list.animateScrollToItem(0)
            }
        }
    }
    // Back from another app after a while, at the top: a fresh feed.
    LifecycleResumeEffect(Unit) {
        if (list.firstVisibleItemIndex == 0) vm.refreshIfStale()
        onPauseOrDispose { }
    }
    // A chip chosen: its page starts at the top.
    LaunchedEffect(state.chip) { if (list.firstVisibleItemIndex > 1) list.scrollToItem(1) }

    // The card waiting in the middle of the screen (the list at rest): its thumbnail flips through the video.
    // Worked out off the composition (the list's layout changes every frame it moves): only a new card recomposes.
    var centered by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(list) {
        snapshotFlow { if (list.isScrollInProgress) null else centerKey(list.layoutInfo) }
            .distinctUntilChanged()
            .collect { centered = it }
    }
    // Cards rise into place the first time they show (the first screenful one after the other).
    val risen = remember { HashSet<String>() }

    // Sliding the list sideways: the next / previous topic (the cards follow the finger, then the new ones come in).
    val order = remember(chips) { listOf<TopicChip?>(null) + chips }
    val swipe = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val chipNow by rememberUpdatedState(state.chip)
    val swipeTopics = Modifier.pointerInput(order) {
        var dx = 0f
        detectHorizontalDragGestures(
            onDragStart = { dx = 0f },
            onDragCancel = { scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) } },
            onDragEnd = {
                val width = size.width.toFloat()
                val i = order.indexOf(chipNow).coerceAtLeast(0)
                val next = if (dx < 0) i + 1 else i - 1
                if (kotlin.math.abs(dx) > width * 0.2f && next in order.indices) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    scope.launch {
                        swipe.animateTo(if (dx < 0) -width * 0.45f else width * 0.45f, tween(140))
                        vm.selectChip(order[next])
                        swipe.snapTo(if (dx < 0) width * 0.3f else -width * 0.3f)
                        swipe.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f))
                    }
                } else {
                    scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                }
            },
            onHorizontalDrag = { change, amount ->
                change.consume()
                dx += amount
                // At the ends there is nowhere to go: it resists.
                val i = order.indexOf(chipNow).coerceAtLeast(0)
                val edge = (dx > 0 && i == 0) || (dx < 0 && i == order.lastIndex)
                scope.launch { swipe.snapTo(if (edge) dx * 0.25f else dx * 0.6f) }
            },
        )
    }
    val slide: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = remember(swipe) {
        {
            translationX = swipe.value
            alpha = 1f - (kotlin.math.abs(swipe.value) / size.width.coerceAtLeast(1f) * 1.4f).coerceIn(0f, 0.8f)
        }
    }

    val pull = rememberPullToRefreshState()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val refreshing = if (state.chip != null) state.chipLoading && state.chipEntries.isNotEmpty() else state.refreshing && state.entries.isNotEmpty()
    // The chips sit under the status bar once the header has scrolled away: a hairline says so.
    val stuck by remember { derivedStateOf { list.firstVisibleItemIndex >= 1 } }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = vm::refresh,
        state = pull,
        modifier = Modifier.fillMaxSize().background(sc.Ink),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pull,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = top),
                containerColor = sc.Surface2,
                color = sc.Accent,
            )
        },
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().then(swipeTopics),
            contentPadding = PaddingValues(bottom = LocalBottomInset.current),
        ) {
            item(key = "header") { FeedHeader(onSearch = nav::openSearch, onSettings = nav::openSettings) }
            stickyHeader(key = "chips") {
                ChipRow(chips, selected = state.chip, stuck = stuck, onSelect = vm::selectChip)
            }
            if (askInterests && state.chip == null) {
                item(key = "interests") { InterestPicker(onDone = vm::saveInterests) }
            }
            if (state.chip == null) {
                if (continueList.isNotEmpty()) {
                    item(key = "continue") {
                        Column(Modifier.padding(top = 6.dp, bottom = 8.dp).animateItem(fadeInSpec = tween(260), fadeOutSpec = null).graphicsLayer(slide)) {
                            TubeSectionHeader(title = stringResource(R.string.tube_continue), modifier = Modifier.padding(top = 0.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = Dimens.Gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(continueList, key = { it.videoId }) { w ->
                                    val v = VideoItem(w.videoId, w.title, w.channelName, w.channelUrl, null, w.thumbnailUrl, w.durationMs)
                                    ShelfVideo(
                                        v,
                                        onClick = { nav.openVideo(v) },
                                        onMore = { menu = VideoMenuTarget(v, list = VideoLists.HISTORY, feedback = false) },
                                        progress = w.progress,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                    if (state.entries.isNotEmpty()) item(key = "foryou") { Spacer(Modifier.height(8.dp)) }
                }
                feedItems("v:", state.entries, watched, nav, onMenu = { menu = it }, onSeen = vm::seen, centered = centered, risen = risen, slide = slide)
                when {
                    state.loading && state.entries.isEmpty() -> items(4, key = { "sk$it" }) { SkeletonCard() }
                    state.error && state.entries.isEmpty() -> item(key = "error") {
                        TubeEmpty(
                            icon = Icons.Rounded.WifiOff,
                            title = stringResource(R.string.tube_feed_error_title),
                            body = stringResource(R.string.tube_feed_error_body),
                            action = { TubeButton(stringResource(R.string.tube_retry), onClick = vm::refresh, icon = Icons.Rounded.Refresh) },
                        )
                    }
                    // The next page is always on its way: its shape waits at the end of the list.
                    state.entries.isNotEmpty() -> items(2, key = { "more$it" }) { SkeletonCard() }
                }
            } else {
                feedItems("c:" + state.chip?.label + ":", state.chipEntries, watched, nav, onMenu = { menu = it }, onSeen = {}, centered = centered, risen = risen, slide = slide)
                when {
                    state.chipLoading && state.chipEntries.isEmpty() -> items(4, key = { "csk$it" }) { SkeletonCard() }
                    state.chipError && state.chipEntries.isEmpty() -> item(key = "chipError") {
                        TubeEmpty(
                            icon = Icons.Rounded.WifiOff,
                            title = stringResource(R.string.tube_feed_error_title),
                            body = stringResource(R.string.tube_topic_empty),
                            action = { TubeButton(stringResource(R.string.tube_retry), onClick = { vm.selectChip(state.chip) }, icon = Icons.Rounded.Refresh) },
                        )
                    }
                    state.chipLoadingMore -> items(2, key = { "cmore$it" }) { SkeletonCard() }
                }
            }
        }
    }

    menu?.let { target ->
        VideoActionsSheet(target, onClose = { menu = null }, onDismissed = { v, channel -> vm.drop(v, channel) })
    }
}

/**
 * The cards of one list (the home's, or a topic's: [keyPrefix] keeps them apart). Cards that leave go
 * at once — fading them out drew the old topic over the new one for a moment.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.feedItems(
    keyPrefix: String,
    entries: List<FeedEngine.Entry>,
    watched: Map<String, com.villamediana.sound.tube.data.WatchEntry>,
    nav: TubeNavigator,
    onMenu: (VideoMenuTarget) -> Unit,
    onSeen: (FeedEngine.Entry) -> Unit,
    centered: String?,
    risen: MutableSet<String>,
    slide: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit,
) {
    itemsIndexed(entries, key = { _, it -> keyPrefix + it.video.id }) { index, e ->
        val key = keyPrefix + e.video.id
        // On screen for a moment: the feed notes it was shown.
        LaunchedEffect(e.video.id) {
            delay(900)
            onSeen(e)
        }
        VideoCard(
            video = e.video,
            onClick = { nav.openVideo(e.video, source = e.reason.source) },
            onMore = { onMenu(VideoMenuTarget(e.video, reason = e.reason)) },
            progress = watched[e.video.id]?.progress ?: 0f,
            onChannel = e.video.channelUrl?.let { url -> { nav.openChannel(url, e.video.channelName, e.video.channelAvatar) } },
            previewing = centered == key,
            modifier = Modifier
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
                .rise(key, if (index < 6) index else 0, risen)
                .graphicsLayer(slide),
        )
    }
}

@Composable
private fun FeedHeader(onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = Dimens.Gutter, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TubeWordmark()
        Spacer(Modifier.weight(1f))
        CircleIconButton(Icons.Rounded.Search, stringResource(R.string.tube_search), onSearch, iconSize = 26.dp)
        CircleIconButton(Icons.Outlined.Settings, stringResource(R.string.tube_settings), onSettings, iconSize = 24.dp)
    }
}

/** The fixed topics, after the person's own; some read a kiosk as well as a search. */
@Composable
private fun rememberChips(own: List<String>): List<TopicChip> {
    val fixed = listOf(
        TopicChip(stringResource(R.string.tube_chip_music), stringResource(R.string.tube_q_music), Kiosks.MUSIC),
        TopicChip(stringResource(R.string.tube_chip_games), stringResource(R.string.tube_q_games), Kiosks.GAMING),
        TopicChip(stringResource(R.string.tube_chip_live), stringResource(R.string.tube_q_live), Kiosks.LIVE),
        TopicChip(stringResource(R.string.tube_chip_podcasts), stringResource(R.string.tube_q_podcasts), Kiosks.PODCASTS),
        TopicChip(stringResource(R.string.tube_chip_news), stringResource(R.string.tube_q_news)),
        TopicChip(stringResource(R.string.tube_chip_sports), stringResource(R.string.tube_q_sports)),
        TopicChip(stringResource(R.string.tube_chip_comedy), stringResource(R.string.tube_q_comedy)),
        TopicChip(stringResource(R.string.tube_chip_tech), stringResource(R.string.tube_q_tech)),
        TopicChip(stringResource(R.string.tube_chip_cooking), stringResource(R.string.tube_q_cooking)),
        TopicChip(stringResource(R.string.tube_chip_science), stringResource(R.string.tube_q_science)),
        TopicChip(stringResource(R.string.tube_chip_movies), stringResource(R.string.tube_q_movies)),
    )
    return remember(own, fixed) {
        val mine = own.map { TopicChip(it.replaceFirstChar { c -> c.uppercase() }, it) }
        (mine + fixed).distinctBy { it.label.lowercase() }
    }
}

@Composable
private fun ChipRow(chips: List<TopicChip>, selected: TopicChip?, stuck: Boolean, onSelect: (TopicChip?) -> Unit) {
    val line by animateColorAsState(if (stuck) sc.Hairline else Color.Transparent, tween(200), label = "chipLine")
    // The chosen topic stays in sight (a swipe on the list may pick one further along).
    val row = rememberLazyListState()
    LaunchedEffect(selected) {
        if (selected == null) return@LaunchedEffect
        // Once the row is laid out: only when the chip is out of sight or cut at the edge.
        snapshotFlow { row.layoutInfo.totalItemsCount }.first { it > 0 }
        val index = chips.indexOf(selected) + 1
        val info = row.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null || item.offset < info.viewportStartOffset || item.offset + item.size > info.viewportEndOffset) {
            row.animateScrollToItem((index - 1).coerceAtLeast(0))
        }
    }
    Column(Modifier.fillMaxWidth().background(sc.Ink).then(if (stuck) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)) {
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            state = row,
            contentPadding = PaddingValues(horizontal = Dimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all") { TubeChip(stringResource(R.string.tube_chip_all), selected = selected == null, onClick = { onSelect(null) }) }
            items(chips, key = { "chip:" + it.label }) { chip ->
                TubeChip(chip.label, selected = selected == chip, onClick = { onSelect(if (selected == chip) null else chip) })
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(line))
    }
}

/** A topic chip: a soft rounded tab; chosen, it turns into the page's ink (white on dark, black on light). */
@Composable
internal fun TubeChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) sc.Text else sc.Surface1, tween(220), label = "chipBg")
    val fg by animateColorAsState(if (selected) sc.Ink else sc.Text, tween(220), label = "chipFg")
    Box(
        modifier
            .height(34.dp)
            .pressScale(interaction, 0.94f)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** "Do que você gosta?": a few topics to start the feed for someone it doesn't know yet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestPicker(onDone: (List<String>) -> Unit) {
    val topics = stringArrayResource(R.array.tube_interest_topics)
    val picked = remember { mutableStateListOf<String>() }
    Column(
        Modifier
            .padding(horizontal = Dimens.Gutter, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(sc.SoftTop, sc.Surface1)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.AutoAwesome, sc.Accent, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.tube_interests_title), style = MaterialTheme.typography.headlineSmall, color = sc.Text)
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            topics.forEach { t ->
                TubeChip(t, selected = t in picked, onClick = { if (t in picked) picked.remove(t) else picked.add(t) })
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TubeButton(stringResource(R.string.tube_interests_done), onClick = { onDone(picked.toList()) }, enabled = picked.isNotEmpty())
            Spacer(Modifier.width(6.dp))
            TubeButton(stringResource(R.string.tube_interests_skip), onClick = { onDone(emptyList()) }, kind = TubeButtonKind.Ghost)
        }
    }
}

@Composable
internal fun FadeIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible) { Box { content() } }
}

/**
 * The first time a card shows it rises into place (a short slide up and fade); the first ones of a
 * new list one after the other ([order]). Scrolled away and back, it is just there.
 */
@Composable
private fun Modifier.rise(key: String, order: Int, risen: MutableSet<String>): Modifier {
    val fresh = remember(key) { risen.add(key) }
    val t = remember(key) { Animatable(if (fresh) 0f else 1f) }
    LaunchedEffect(key) {
        if (t.value >= 1f) return@LaunchedEffect
        delay(order * 55L)
        t.animateTo(1f, tween(420, easing = com.villamediana.sound.ui.theme.Motion.Emphasized))
    }
    val distance = with(androidx.compose.ui.platform.LocalDensity.current) { 26.dp.toPx() }
    return graphicsLayer {
        alpha = t.value
        translationY = (1f - t.value) * distance
    }
}

/** The feed card nearest the middle of the list's window (its key), if any. */
private fun centerKey(info: androidx.compose.foundation.lazy.LazyListLayoutInfo): String? {
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo
        .filter { (it.key as? String)?.let { k -> k.startsWith("v:") || k.startsWith("c:") } == true }
        .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - middle) }
        ?.key as? String
}
