package com.villamediana.sound.ui.tube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.villamediana.sound.tube.online.PlaylistItem
import com.villamediana.sound.tube.online.TubePage
import com.villamediana.sound.tube.online.VideoItem
import com.villamediana.sound.ui.components.EmptyState
import com.villamediana.sound.ui.components.PillButton
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.LocalBottomInset
import com.villamediana.sound.ui.theme.sc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** A page's top bar: back, title, and actions on the right. */
@Composable
fun PageTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(60.dp).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.tube_back), tint = sc.Text) }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = sc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

/** Loads the next page when the list's end is near. */
@Composable
internal fun LoadMoreWhenNearEnd(list: androidx.compose.foundation.lazy.LazyListState, key: Any, onMore: () -> Unit) {
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 4 } == true } }
    LaunchedEffect(nearEnd, key) { if (nearEnd) onMore() }
}

// ------------------------------------------------------------------ playlist

class PlaylistViewModel(private val url: String) : ViewModel() {
    private val tube = SoundApplication.graph.tube

    data class UiState(
        val playlist: PlaylistItem? = null,
        val videos: List<VideoItem> = emptyList(),
        val page: TubePage<VideoItem>? = null,
        val loading: Boolean = true,
        val error: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            try {
                val page = tube.source.playlist(url)
                _state.value = UiState(page.playlist, page.videos.items, page.videos, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _state.value = _state.value.copy(loading = false, error = true)
            }
        }
    }

    fun more() {
        val s = _state.value
        val page = s.page ?: return
        if (!page.hasMore || s.loading) return
        _state.value = s.copy(loading = true)
        viewModelScope.launch {
            try {
                val next = tube.source.morePlaylist(page)
                val known = _state.value.videos.map { it.id }.toSet()
                _state.value = _state.value.copy(videos = _state.value.videos + next.items.filter { it.id !in known }, page = next, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }
}

@Composable
fun PlaylistScreen(route: TubePlaylistRoute) {
    val nav = LocalTubeNav.current
    val context = LocalContext.current
    val vm: PlaylistViewModel = viewModel(key = "playlist:" + route.url) { PlaylistViewModel(route.url) }
    val state by vm.state.collectAsState()
    val list = rememberLazyListState()
    var menu by remember { mutableStateOf<VideoMenuTarget?>(null) }
    val res = resources()
    val title = state.playlist?.name ?: route.name
    LoadMoreWhenNearEnd(list, state.videos.size, vm::more)

    Column(Modifier.fillMaxSize()) {
        PageTopBar(title.ifBlank { stringResource(R.string.tube_playlist) }, onBack = nav::back) {
            IconButton(onClick = { shareLink(context, title, state.playlist?.url ?: route.url) }) { Icon(Icons.Rounded.Share, stringResource(R.string.tube_share), tint = sc.Text) }
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
            state.playlist?.let { p ->
                item(key = "head") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter).padding(bottom = 12.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(ThumbShape).background(sc.Surface2)) {
                            AsyncImage(model = p.thumbnailUrl ?: state.videos.firstOrNull()?.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(p.name, style = MaterialTheme.typography.headlineSmall, color = sc.Text)
                        val facts = listOfNotNull(p.uploader, TubeFormat.videoCount(res, p.count)).joinToString(" · ")
                        if (facts.isNotBlank()) Text(facts, style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted)
                        Spacer(Modifier.height(14.dp))
                        state.videos.firstOrNull()?.let { first ->
                            PillButton(stringResource(R.string.tube_play_all), onClick = { nav.openVideo(first) }, icon = Icons.Rounded.PlayArrow, filled = true, big = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            itemsIndexed(state.videos, key = { _, v -> v.id }) { i, v ->
                VideoRow(
                    v,
                    onClick = { nav.openVideo(v) },
                    onMore = { menu = VideoMenuTarget(v) },
                    leading = {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = sc.TextFaint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(26.dp).padding(top = 30.dp),
                        )
                    },
                )
            }
            when {
                state.loading -> item { LoadingRow(Modifier.padding(top = if (state.videos.isEmpty()) 40.dp else 0.dp)) }
                state.error -> item {
                    EmptyState(
                        icon = Icons.Rounded.WifiOff,
                        title = stringResource(R.string.tube_playlist_error),
                        body = stringResource(R.string.tube_feed_error_body),
                        action = { PillButton(stringResource(R.string.tube_retry), onClick = vm::load, filled = true) },
                    )
                }
            }
        }
    }
    menu?.let { target -> VideoActionsSheet(target, onClose = { menu = null }) }
}

// ------------------------------------------------------------------ personal lists

@Composable
fun VideoListScreen(route: VideoListRoute) {
    val nav = LocalTubeNav.current
    val tube = SoundApplication.graph.tube
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf<VideoMenuTarget?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val title = stringResource(
        when (route.list) {
            VideoLists.HISTORY -> R.string.tube_history
            VideoLists.LIKED -> R.string.tube_liked_videos
            else -> R.string.tube_later
        },
    )
    val emptyBody = stringResource(
        when (route.list) {
            VideoLists.HISTORY -> R.string.tube_history_empty
            VideoLists.LIKED -> R.string.tube_liked_empty
            else -> R.string.tube_later_empty
        },
    )
    // Each list as (video, progress).
    val rows by remember(route.list) {
        when (route.list) {
            VideoLists.HISTORY -> tube.library.history.map { list ->
                list.map { w -> VideoItem(w.videoId, w.title, w.channelName, w.channelUrl, null, w.thumbnailUrl, w.durationMs) to w.progress }
            }
            else -> (if (route.list == VideoLists.LIKED) tube.library.liked else tube.library.later).map { list ->
                list.map { s -> VideoItem(s.videoId, s.title, s.channelName, s.channelUrl, null, s.thumbnailUrl, s.durationMs) to 0f }
            }
        }
    }.collectAsState(initial = null)

    Column(Modifier.fillMaxSize()) {
        PageTopBar(title, onBack = nav::back) {
            if (route.list == VideoLists.HISTORY && rows?.isNotEmpty() == true) {
                Text(
                    stringResource(R.string.tube_clear),
                    style = MaterialTheme.typography.labelLarge,
                    color = sc.TextMuted,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { confirmClear = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        val items = rows
        if (items != null && items.isEmpty()) {
            EmptyState(icon = Icons.Rounded.VideoLibrary, title = stringResource(R.string.tube_list_empty_title), body = emptyBody)
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
                items(items.orEmpty(), key = { it.first.id }) { (v, progress) ->
                    VideoRow(
                        v,
                        onClick = { nav.openVideo(v) },
                        onMore = { menu = VideoMenuTarget(v, list = route.list, feedback = route.list != VideoLists.LIKED) },
                        progress = progress,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
    menu?.let { target -> VideoActionsSheet(target, onClose = { menu = null }) }
    if (confirmClear) {
        com.villamediana.sound.ui.sheets.ConfirmDialog(
            title = stringResource(R.string.tube_clear_history_title),
            body = stringResource(R.string.tube_clear_history_body),
            confirm = stringResource(R.string.tube_clear),
            onConfirm = {
                confirmClear = false
                scope.launch { tube.library.clearHistory() }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
internal fun EmptyRow(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = sc.TextFaint, modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 6.dp))
}
