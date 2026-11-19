package com.villamediana.sound.ui.tube

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import com.villamediana.sound.ui.sheets.SoundSheet
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.online.ChannelItem
import com.villamediana.sound.tube.online.ChannelTab
import com.villamediana.sound.tube.online.ChannelTabKind
import com.villamediana.sound.tube.online.SearchResult
import com.villamediana.sound.tube.online.TubePage
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.LocalBottomInset
import com.villamediana.sound.ui.theme.sc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** A channel: who they are and each of its tabs (videos, shorts, lives, playlists), read to the end as the list scrolls. */
class ChannelViewModel(private val url: String) : ViewModel() {
    private val tube = SoundApplication.graph.tube

    data class TabState(
        val items: List<SearchResult> = emptyList(),
        val page: TubePage<SearchResult>? = null,
        val loading: Boolean = false,
        val loaded: Boolean = false,
        val error: Boolean = false,
    ) {
        val ended: Boolean get() = loaded && page?.hasMore != true
    }

    data class UiState(
        val channel: ChannelItem? = null,
        val banner: String? = null,
        val tabs: List<ChannelTab> = emptyList(),
        val selected: ChannelTabKind = ChannelTabKind.VIDEOS,
        val content: Map<ChannelTabKind, TabState> = emptyMap(),
        val loading: Boolean = true,
        val error: Boolean = false,
    ) {
        val current: TabState get() = content[selected] ?: TabState()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            try {
                val page = tube.source.channel(url)
                val first = page.tabs.firstOrNull()?.kind ?: ChannelTabKind.VIDEOS
                _state.value = UiState(channel = page.channel, banner = page.bannerUrl, tabs = page.tabs, selected = first, loading = false)
                runCatching { tube.library.refreshSubscription(page.channel.url, page.channel.name, page.channel.avatarUrl) }
                select(first)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _state.value = _state.value.copy(loading = false, error = true)
            }
        }
    }

    fun select(kind: ChannelTabKind) {
        _state.value = _state.value.copy(selected = kind)
        if (_state.value.content[kind]?.loaded != true) more()
    }

    private fun put(kind: ChannelTabKind, change: (TabState) -> TabState) {
        val s = _state.value
        _state.value = s.copy(content = s.content + (kind to change(s.content[kind] ?: TabState())))
    }

    /** The next page of the tab on screen (its first, when it wasn't opened yet). */
    fun more() {
        val s = _state.value
        val kind = s.selected
        val channel = s.channel ?: return
        val tab = s.tabs.firstOrNull { it.kind == kind } ?: return
        val now = s.content[kind] ?: TabState()
        if (now.loading || now.ended) return
        put(kind) { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            try {
                val next = if (now.page == null) tube.source.channelTab(tab, channel) else tube.source.moreChannelTab(now.page, channel)
                put(kind) { old ->
                    val known = old.items.map { it.key }.toSet()
                    old.copy(items = old.items + next.items.filter { it.key !in known }, page = next, loading = false, loaded = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                put(kind) { it.copy(loading = false, error = true, loaded = it.items.isNotEmpty()) }
            }
        }
    }
}

/**
 * A channel's page: its banner edge to edge, the picture over it, name and numbers, follow and share;
 * then its tabs (they stay under the top bar while scrolling) and everything in the tab, read to the
 * end as the list goes: videos as big cards, Shorts in a grid, playlists as rows.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(route: ChannelRoute) {
    val nav = LocalTubeNav.current
    val tube = SoundApplication.graph.tube
    val context = LocalContext.current
    val vm: ChannelViewModel = viewModel(key = "channel:" + route.url) { ChannelViewModel(route.url) }
    val state by vm.state.collectAsState()
    val name = state.channel?.name?.takeIf { it.isNotBlank() } ?: route.name
    val avatar = state.channel?.avatarUrl ?: route.avatar
    val subscribed by remember(state.channel?.url, name) { tube.library.isSubscribed(state.channel?.url ?: route.url, name) }.collectAsState(initial = false)
    val watched by remember { tube.library.history.map { list -> list.associate { it.videoId to it.progress } } }.collectAsState(initial = emptyMap())
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var menu by remember { mutableStateOf<VideoMenuTarget?>(null) }
    var about by remember { mutableStateOf(false) }
    val res = resources()
    val tab = state.current
    LoadMoreWhenNearEnd(list, tab.items.size to state.selected, vm::more)

    val color = animatedVeil(rememberThumbColor(avatar))
    val ink = sc.Ink
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barHeight = statusTop + TOP_BAR
    // The top bar turns solid (and says the name) once the header has scrolled under it.
    val solid by remember {
        derivedStateOf {
            if (list.firstVisibleItemIndex > 0) 1f
            else (list.firstVisibleItemScrollOffset / with(density) { 150.dp.toPx() }).coerceIn(0f, 1f)
        }
    }
    val stuck by remember { derivedStateOf { list.firstVisibleItemIndex >= 1 } }

    Box(Modifier.fillMaxSize().background(ink)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
            item(key = "head") {
                ChannelHeader(
                    name = name,
                    avatar = avatar,
                    banner = state.banner,
                    color = color,
                    channel = state.channel,
                    loading = state.loading && state.channel == null,
                    subscribed = subscribed,
                    statusTop = statusTop,
                    onSubscribe = { scope.launch { tube.library.toggleSubscription(state.channel?.url ?: route.url, name, avatar) } },
                    onShare = { shareLink(context, name, state.channel?.url ?: route.url) },
                    onAbout = { about = true },
                )
            }
            if (state.tabs.size > 1) {
                stickyHeader(key = "tabs") {
                    Column(Modifier.fillMaxWidth().background(ink)) {
                        if (stuck) Spacer(Modifier.height(barHeight))
                        TubeTabs(
                            labels = state.tabs.map { stringResource(tabLabel(it.kind)) },
                            selected = state.tabs.indexOfFirst { it.kind == state.selected }.coerceAtLeast(0),
                            onSelect = { i -> vm.select(state.tabs[i].kind) },
                        )
                    }
                }
            }
            item(key = "gap") { Spacer(Modifier.height(10.dp)) }
            when (state.selected) {
                ChannelTabKind.SHORTS -> {
                    val shorts = tab.items.filterIsInstance<SearchResult.Video>()
                    items(shorts.chunked(2), key = { row -> "sh:" + row.first().key }) { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { r ->
                                ShortCard(r.video, onClick = { nav.openVideo(r.video) }, onMore = { menu = VideoMenuTarget(r.video) }, modifier = Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                else -> items(tab.items, key = { state.selected.name + it.key }) { r ->
                    when (r) {
                        is SearchResult.Video -> VideoCard(
                            r.video,
                            onClick = { nav.openVideo(r.video) },
                            onMore = { menu = VideoMenuTarget(r.video) },
                            progress = watched[r.video.id] ?: 0f,
                            showChannel = false,
                        )
                        is SearchResult.Playlist -> PlaylistRow(r.playlist, onClick = { nav.openPlaylist(r.playlist) })
                        is SearchResult.Channel -> Unit
                    }
                }
            }
            when {
                state.error -> item(key = "error") {
                    TubeEmpty(
                        icon = Icons.Rounded.WifiOff,
                        title = stringResource(R.string.tube_channel_error),
                        body = stringResource(R.string.tube_feed_error_body),
                        action = { TubeButton(stringResource(R.string.tube_retry), onClick = vm::load, icon = Icons.Rounded.Refresh) },
                    )
                }
                state.loading || (tab.loading && tab.items.isEmpty()) -> items(3, key = { "sk$it" }) { SkeletonCard() }
                tab.loading -> items(1, key = { "more$it" }) { SkeletonCard() }
                tab.error -> item(key = "tabError") {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { RetryPill(vm::more) }
                }
                tab.loaded && tab.items.isEmpty() -> item(key = "empty") {
                    TubeEmpty(icon = Icons.Rounded.VideoLibrary, title = stringResource(R.string.tube_channel_empty), body = null)
                }
            }
        }

        // The top bar over it all: round glass buttons on the banner, solid with the name once scrolled.
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(ink.copy(alpha = solid)),
        ) {
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(TOP_BAR).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val glass = Color.Black.copy(alpha = 0.32f * (1f - solid))
                val tint = lerp(Color.White, sc.Text, solid)
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.tube_back), nav::back, container = glass, tint = tint, size = 40.dp, iconSize = 22.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    color = sc.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).graphicsLayer { alpha = ((solid - 0.6f) / 0.4f).coerceIn(0f, 1f) },
                )
                CircleIconButton(Icons.Rounded.Share, stringResource(R.string.tube_share), { shareLink(context, name, state.channel?.url ?: route.url) }, container = glass, tint = tint, size = 40.dp, iconSize = 20.dp)
            }
            if (solid >= 1f && !stuck) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(sc.Hairline))
        }
    }
    menu?.let { target -> VideoActionsSheet(target, onClose = { menu = null }) }
    if (about) ChannelAbout(name, avatar, state.channel, onClose = { about = false })
}

private val TOP_BAR = 56.dp

@Composable
private fun ChannelHeader(
    name: String,
    avatar: String?,
    banner: String?,
    color: Color,
    channel: ChannelItem?,
    loading: Boolean,
    subscribed: Boolean,
    statusTop: androidx.compose.ui.unit.Dp,
    onSubscribe: () -> Unit,
    onShare: () -> Unit,
    onAbout: () -> Unit,
) {
    val ink = sc.Ink
    val res = resources()
    Column(Modifier.fillMaxWidth()) {
        // Banner (or the picture's color) under the status bar, melting into the page.
        Box(Modifier.fillMaxWidth().height(statusTop + 132.dp)) {
            Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(lerp(color, ink, 0.15f), lerp(color, ink, 0.6f)))))
            if (banner != null) {
                AsyncImage(model = banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
            // A little shade at the top for the buttons on a picture; the bottom melts into the page.
            val top = if (banner != null) Color.Black.copy(alpha = 0.35f) else Color.Transparent
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to top, 0.35f to Color.Transparent, 0.7f to Color.Transparent, 1f to ink.copy(alpha = 0.92f))))
        }
        // The picture rides up over the banner's edge (and the space it leaves is taken back).
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Gutter)
                .layout { measurable, constraints ->
                    val lift = 44.dp.roundToPx()
                    val p = measurable.measure(constraints)
                    layout(p.width, (p.height - lift).coerceAtLeast(0)) { p.place(0, -lift) }
                },
        ) {
            ChannelAvatar(avatar, name, 88.dp, Modifier.border(4.dp, ink, CircleShape))
            Spacer(Modifier.height(12.dp))
            if (name.isNotBlank()) {
                ChannelName(name, channel?.verified == true, style = MaterialTheme.typography.headlineMedium, color = sc.Text)
            } else {
                Box(Modifier.fillMaxWidth(0.6f).height(26.dp).clip(RoundedCornerShape(8.dp)).shimmer())
            }
            val facts = listOfNotNull(TubeFormat.subscribers(res, channel?.subscribers ?: -1), TubeFormat.videoCount(res, channel?.videos ?: -1)).joinToString("  ·  ")
            if (facts.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(facts, style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted)
            } else if (loading) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.4f).height(13.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            }
            channel?.description?.takeIf { it.isNotBlank() }?.let { d ->
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onAbout),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(d.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.tube_channel_about), tint = sc.TextMuted)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubscribeButton(subscribed, onClick = onSubscribe, modifier = Modifier.weight(1f), big = true)
                Spacer(Modifier.width(10.dp))
                CircleIconButton(Icons.Rounded.Share, stringResource(R.string.tube_share), onShare, container = sc.Surface1, size = 46.dp, iconSize = 20.dp)
            }
        }
    }
}

/** Shorts: a tall frame with its title and views over the bottom of the picture. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortCard(video: com.villamediana.sound.tube.online.VideoItem, onClick: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val res = resources()
    var loaded by remember(video.thumbnailUrl) { mutableStateOf(false) }
    Box(
        modifier
            .aspectRatio(9f / 16f)
            .pressable(onClick = onClick, onLongClick = onMore, onPress = { SoundApplication.graph.tube.player.warm(video.id) })
            .clip(RoundedCornerShape(14.dp))
            .then(if (loaded) Modifier.background(sc.Surface2) else Modifier.shimmer()),
    ) {
        AsyncImage(model = video.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, onSuccess = { loaded = true }, modifier = Modifier.fillMaxSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TubeFormat.views(res, video)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1) }
        }
    }
}

/** "Sobre": the whole description, picture and numbers, in a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelAbout(name: String, avatar: String?, channel: ChannelItem?, onClose: () -> Unit) {
    val res = resources()
    SoundSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelAvatar(avatar, name, 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    ChannelName(name, channel?.verified == true, style = MaterialTheme.typography.titleLarge, color = sc.Text)
                    val facts = listOfNotNull(TubeFormat.subscribers(res, channel?.subscribers ?: -1), TubeFormat.videoCount(res, channel?.videos ?: -1)).joinToString("  ·  ")
                    if (facts.isNotBlank()) Text(facts, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted)
                }
            }
            channel?.description?.takeIf { it.isNotBlank() }?.let { d ->
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.tube_channel_about), style = MaterialTheme.typography.titleSmall, color = sc.TextMuted)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    item { SelectionContainer { Text(d, style = MaterialTheme.typography.bodyMedium, color = sc.Text) } }
                }
            }
        }
    }
}

private fun tabLabel(kind: ChannelTabKind): Int = when (kind) {
    ChannelTabKind.VIDEOS -> R.string.tube_channel_videos
    ChannelTabKind.SHORTS -> R.string.tube_channel_shorts
    ChannelTabKind.LIVE -> R.string.tube_chip_live
    ChannelTabKind.PLAYLISTS -> R.string.tube_filter_playlists
}
