package com.villamediana.sound.ui.tube

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.villamediana.sound.ui.components.EmptyState
import com.villamediana.sound.ui.components.PillButton
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.LocalBottomInset
import com.villamediana.sound.ui.theme.SoundColors
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

@Composable
fun ChannelScreen(route: ChannelRoute) {
    val nav = LocalTubeNav.current
    val tube = SoundApplication.graph.tube
    val context = LocalContext.current
    val vm: ChannelViewModel = viewModel(key = "channel:" + route.url) { ChannelViewModel(route.url) }
    val state by vm.state.collectAsState()
    val name = state.channel?.name ?: route.name
    val avatar = state.channel?.avatarUrl ?: route.avatar
    val subscribed by remember(state.channel?.url, name) { tube.library.isSubscribed(state.channel?.url ?: route.url, name) }.collectAsState(initial = false)
    val watched by remember { tube.library.history.map { list -> list.associate { it.videoId to it.progress } } }.collectAsState(initial = emptyMap())
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var menu by remember { mutableStateOf<VideoMenuTarget?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val res = resources()
    val tab = state.current
    LoadMoreWhenNearEnd(list, tab.items.size to state.selected, vm::more)

    // The channel's picture lends its color to the top of the page.
    val veil = animatedVeil(rememberThumbColor(avatar))
    val ink = sc.Ink
    val wash = SoundColors.palette.washAlpha
    Box(Modifier.fillMaxSize().drawBehind { drawRect(Brush.verticalGradient(listOf(lerp(ink, veil, wash), ink), endY = 420.dp.toPx())) }) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
            item(key = "bar") {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.tube_back), tint = sc.Text) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { shareLink(context, name, state.channel?.url ?: route.url) }) { Icon(Icons.Rounded.Share, stringResource(R.string.tube_share), tint = sc.Text) }
                }
            }
            item(key = "head") {
                Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter)) {
                    val banner = state.banner
                    if (banner != null) {
                        AsyncImage(
                            model = banner,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(4.2f).clip(RoundedCornerShape(18.dp)).background(sc.Surface1),
                        )
                    }
                    Row(Modifier.offset(y = if (banner != null) (-26).dp else 0.dp).padding(start = if (banner != null) 12.dp else 0.dp), verticalAlignment = Alignment.Bottom) {
                        ChannelAvatar(avatar, name, 88.dp, Modifier.border(4.dp, lerp(ink, veil, wash * 0.5f), CircleShape).padding(4.dp))
                    }
                    Column(Modifier.offset(y = if (banner != null) (-16).dp else 12.dp)) {
                        ChannelName(name, state.channel?.verified == true, style = MaterialTheme.typography.headlineMedium, color = sc.Text)
                        TubeFormat.subscribers(res, state.channel?.subscribers ?: -1)?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted)
                        }
                        state.channel?.description?.let { d ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                d,
                                style = MaterialTheme.typography.bodyMedium,
                                color = sc.TextMuted,
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded },
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        SubscribeButton(subscribed, onClick = {
                            scope.launch { tube.library.toggleSubscription(state.channel?.url ?: route.url, name, avatar) }
                        }, modifier = Modifier.fillMaxWidth().height(48.dp))
                    }
                }
            }
            if (state.tabs.size > 1) {
                item(key = "tabs") {
                    LazyRow(Modifier.padding(bottom = 8.dp), contentPadding = PaddingValues(horizontal = Dimens.Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.tabs, key = { it.kind.name }) { t ->
                            TubeChip(stringResource(tabLabel(t.kind)), selected = state.selected == t.kind, onClick = { vm.select(t.kind) })
                        }
                    }
                }
            }
            items(tab.items, key = { state.selected.name + it.key }) { r ->
                when (r) {
                    is SearchResult.Video -> VideoRow(
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
            when {
                state.error -> item(key = "error") {
                    EmptyState(
                        icon = Icons.Rounded.WifiOff,
                        title = stringResource(R.string.tube_channel_error),
                        body = stringResource(R.string.tube_feed_error_body),
                        action = { PillButton(stringResource(R.string.tube_retry), onClick = vm::load, filled = true) },
                    )
                }
                state.loading || (tab.loading && tab.items.isEmpty()) -> items(6, key = { "sk$it" }) { SkeletonRow() }
                tab.loading -> items(2, key = { "more$it" }) { SkeletonRow() }
                tab.error -> item(key = "tabError") {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { RetryPill(vm::more) }
                }
                tab.loaded && tab.items.isEmpty() -> item(key = "empty") {
                    Text(stringResource(R.string.tube_channel_empty), style = MaterialTheme.typography.bodyMedium, color = sc.TextFaint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
            }
        }
    }
    menu?.let { target -> VideoActionsSheet(target, onClose = { menu = null }) }
}

private fun tabLabel(kind: ChannelTabKind): Int = when (kind) {
    ChannelTabKind.VIDEOS -> R.string.tube_channel_videos
    ChannelTabKind.SHORTS -> R.string.tube_channel_shorts
    ChannelTabKind.LIVE -> R.string.tube_chip_live
    ChannelTabKind.PLAYLISTS -> R.string.tube_filter_playlists
}
