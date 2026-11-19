package com.villamediana.sound.ui.tube

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.online.SearchKind
import com.villamediana.sound.tube.online.SearchResult
import com.villamediana.sound.tube.online.TubeLink
import com.villamediana.sound.tube.online.TubePage
import com.villamediana.sound.tube.online.VideoItem
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.LocalBottomInset
import com.villamediana.sound.ui.theme.sc
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class TubeSearchViewModel : ViewModel() {
    private val tube = SoundApplication.graph.tube

    data class UiState(
        val query: String = "",
        /** The query the results are for (null = still typing). */
        val submitted: String? = null,
        val kind: SearchKind = SearchKind.ALL,
        val results: List<SearchResult> = emptyList(),
        val page: TubePage<SearchResult>? = null,
        val loading: Boolean = false,
        val error: Boolean = false,
        val suggestions: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val recent = tube.library.searches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set when a link was typed: the page opens it. */
    val opened = MutableStateFlow<TubeLink?>(null)

    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    init {
        SearchInbox.query?.let { q ->
            SearchInbox.query = null
            submit(q)
        }
    }

    fun type(q: String) {
        _state.value = _state.value.copy(query = q, submitted = if (q == _state.value.submitted) q else null)
        suggestJob?.cancel()
        if (q.isBlank()) {
            _state.value = _state.value.copy(suggestions = emptyList())
            return
        }
        suggestJob = viewModelScope.launch {
            delay(180)
            val list = tube.source.suggestions(q)
            if (_state.value.query == q) _state.value = _state.value.copy(suggestions = list)
        }
    }

    fun submit(q: String = _state.value.query, kind: SearchKind = _state.value.kind) {
        val query = q.trim()
        if (query.isEmpty()) return
        suggestJob?.cancel()
        searchJob?.cancel()
        _state.value = _state.value.copy(query = query, submitted = query, kind = kind, loading = true, error = false, results = emptyList(), page = null)
        searchJob = viewModelScope.launch {
            // A pasted link opens what it points to.
            if (query.contains("youtu", ignoreCase = true) || query.startsWith("http")) {
                tube.source.resolve(query)?.let {
                    _state.value = _state.value.copy(loading = false)
                    opened.value = it
                    return@launch
                }
            }
            tube.library.addSearch(query)
            try {
                val page = tube.source.search(query, kind)
                _state.value = _state.value.copy(results = page.items, page = page, loading = false)
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
        searchJob = viewModelScope.launch {
            try {
                val next = tube.source.moreSearch(page)
                val known = _state.value.results.map { it.key }.toSet()
                _state.value = _state.value.copy(results = _state.value.results + next.items.filter { it.key !in known }, page = next, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun removeRecent(q: String) {
        viewModelScope.launch { tube.library.removeSearch(q) }
    }

    fun clearRecent() {
        viewModelScope.launch { tube.library.clearSearches() }
    }
}

/** Busca: suggestions while typing, recent searches, then results with filters. Pasted links open directly. */
@Composable
fun TubeSearchScreen(vm: TubeSearchViewModel = viewModel()) {
    val nav = LocalTubeNav.current
    val tube = SoundApplication.graph.tube
    val state by vm.state.collectAsState()
    val recent by vm.recent.collectAsState()
    val opened by vm.opened.collectAsState()
    val subscribed by tube.library.subscribedKeys.collectAsState(initial = emptySet())
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf<VideoMenuTarget?>(null) }
    val list = rememberLazyListState()
    var fieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (state.submitted == null) runCatching { focus.requestFocus() } }
    LaunchedEffect(opened) {
        val link = opened ?: return@LaunchedEffect
        vm.opened.value = null
        when (link) {
            is TubeLink.Video -> nav.openVideo(VideoItem(link.id, "", ""), startMs = link.startMs.takeIf { it > 0 } ?: -1)
            is TubeLink.Channel -> nav.openChannel(link.url)
            is TubeLink.Playlist -> nav.openPlaylist(link.url)
        }
    }
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 4 } == true } }
    LaunchedEffect(nearEnd, state.results.size) { if (nearEnd && state.submitted != null) vm.more() }

    Column(Modifier.fillMaxSize()) {
        // Field.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 4.dp, end = Dimens.Gutter, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.tube_back), nav::back)
            Spacer(Modifier.width(4.dp))
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(sc.Surface1)
                    .border(1.5.dp, if (fieldFocused) sc.Accent.copy(alpha = 0.7f) else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = if (fieldFocused) sc.Accent else sc.TextMuted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (state.query.isEmpty()) Text(stringResource(R.string.tube_search_hint), style = MaterialTheme.typography.bodyLarge, color = sc.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicTextField(
                        value = state.query,
                        onValueChange = vm::type,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = sc.Text),
                        cursorBrush = SolidColor(sc.Text),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            vm.submit()
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { fieldFocused = it.isFocused },
                    )
                }
                if (state.query.isNotEmpty()) {
                    CircleIconButton(Icons.Rounded.Close, stringResource(R.string.tube_clear), {
                        vm.type("")
                        runCatching { focus.requestFocus() }
                    }, tint = sc.TextMuted, size = 40.dp, iconSize = 20.dp)
                }
            }
        }

        if (state.submitted == null) {
            // Typing: suggestions, or the recent searches when the field is empty.
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
                if (state.query.isBlank()) {
                    if (recent.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(start = Dimens.Gutter, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.tube_search_recent), style = MaterialTheme.typography.headlineSmall, color = sc.Text, modifier = Modifier.weight(1f))
                                TubeButton(stringResource(R.string.tube_clear), onClick = vm::clearRecent, kind = TubeButtonKind.Ghost)
                            }
                        }
                        items(recent, key = { "r:" + it.query }) { r ->
                            QueryRow(r.query, Icons.Rounded.History, onClick = {
                                focusManager.clearFocus()
                                vm.submit(r.query)
                            }, onTrailing = { vm.removeRecent(r.query) }, trailingIcon = Icons.Rounded.Close)
                        }
                    }
                } else {
                    items(state.suggestions, key = { "s:$it" }) { s ->
                        QueryRow(s, Icons.Rounded.Search, onClick = {
                            focusManager.clearFocus()
                            vm.submit(s)
                        }, onTrailing = { vm.type("$s ") }, trailingIcon = Icons.Rounded.NorthWest)
                    }
                }
            }
        } else {
            LazyRow(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                contentPadding = PaddingValues(horizontal = Dimens.Gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val kinds = listOf(
                    SearchKind.ALL to R.string.tube_filter_all,
                    SearchKind.VIDEOS to R.string.tube_filter_videos,
                    SearchKind.CHANNELS to R.string.tube_filter_channels,
                    SearchKind.PLAYLISTS to R.string.tube_filter_playlists,
                )
                items(kinds, key = { it.first.name }) { (kind, label) ->
                    TubeChip(stringResource(label), selected = state.kind == kind, onClick = { if (state.kind != kind) vm.submit(kind = kind) })
                }
            }
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
                items(state.results, key = { it.key }) { r ->
                    when (r) {
                        is SearchResult.Video -> VideoRow(r.video, onClick = { nav.openVideo(r.video) }, onMore = { menu = VideoMenuTarget(r.video) })
                        is SearchResult.Channel -> {
                            val key = com.villamediana.sound.tube.feed.FeedEngine.channelKey(r.channel.url, r.channel.name)
                            ChannelRow(r.channel, subscribed = key in subscribed, onClick = { nav.openChannel(r.channel) }, onSubscribe = {
                                scope.launch { tube.library.toggleSubscription(r.channel.url, r.channel.name, r.channel.avatarUrl) }
                            })
                        }
                        is SearchResult.Playlist -> PlaylistRow(r.playlist, onClick = { nav.openPlaylist(r.playlist) })
                    }
                }
                when {
                    state.loading -> items(if (state.results.isEmpty()) 7 else 2, key = { "sk$it" }) { SkeletonRow() }
                    state.error -> item {
                        TubeEmpty(
                            icon = Icons.Rounded.WifiOff,
                            title = stringResource(R.string.tube_search_error),
                            body = stringResource(R.string.tube_feed_error_body),
                            action = { TubeButton(stringResource(R.string.tube_retry), onClick = { vm.submit() }, icon = Icons.Rounded.Refresh) },
                        )
                    }
                    state.results.isEmpty() -> item {
                        TubeEmpty(icon = Icons.Rounded.SearchOff, title = stringResource(R.string.tube_search_empty), body = stringResource(R.string.tube_search_empty_body))
                    }
                }
            }
        }
    }

    menu?.let { target -> VideoActionsSheet(target, onClose = { menu = null }) }
}

@Composable
private fun QueryRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, onTrailing: () -> Unit, trailingIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = Dimens.Gutter, end = 8.dp).height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(sc.Surface1), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = sc.TextMuted, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = sc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        CircleIconButton(trailingIcon, null, onTrailing, tint = sc.TextFaint, size = 40.dp, iconSize = 20.dp)
    }
}
