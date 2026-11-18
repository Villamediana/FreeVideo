package com.villamediana.sound.ui.tube

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.data.Subscription
import com.villamediana.sound.tube.online.VideoItem
import com.villamediana.sound.ui.components.EmptyState
import com.villamediana.sound.ui.components.PillButton
import com.villamediana.sound.ui.components.SectionHeader
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.LocalBottomInset
import com.villamediana.sound.ui.theme.sc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

// ------------------------------------------------------------------ Inscrições

class SubsViewModel : ViewModel() {
    private val tube = SoundApplication.graph.tube

    data class UiState(val videos: List<VideoItem> = emptyList(), val loading: Boolean = false, val loaded: Boolean = false)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Reload when the subscriptions change (a channel followed or left elsewhere).
        viewModelScope.launch {
            tube.library.subscriptions.map { list -> list.map { it.channelUrl }.toSet() }.distinctUntilChanged().collect { refresh() }
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            try {
                val videos = tube.feed.subscriptionsFeed()
                _state.value = UiState(videos, loading = false, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _state.value = _state.value.copy(loading = false, loaded = true)
            }
        }
    }
}

/** Inscrições: the channels followed (no account, on the phone) and their new videos, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubsScreen(vm: SubsViewModel = viewModel()) {
    val nav = LocalTubeNav.current
    val tube = SoundApplication.graph.tube
    val state by vm.state.collectAsState()
    val subs by tube.library.subscriptions.collectAsState(initial = null)
    val watched by remember { tube.library.history.map { list -> list.associate { it.videoId to it.progress } } }.collectAsState(initial = emptyMap())
    var menu by remember { mutableStateOf<VideoMenuTarget?>(null) }
    val importer = rememberSubscriptionImport()
    val pull = rememberPullToRefreshState()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    PullToRefreshBox(
        isRefreshing = state.loading && state.loaded,
        onRefresh = vm::refresh,
        state = pull,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pull,
                isRefreshing = state.loading && state.loaded,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = top),
                containerColor = sc.Surface2,
                color = sc.Accent,
            )
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
            item(key = "head") {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = Dimens.Gutter, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.tube_tab_subs), style = MaterialTheme.typography.headlineLarge, color = sc.Text, modifier = Modifier.weight(1f))
                    IconButton(onClick = nav::openSearch) { Icon(Icons.Rounded.Search, stringResource(R.string.tube_search), tint = sc.Text) }
                    if (subs?.isNotEmpty() == true) {
                        IconButton(onClick = nav::openSubscriptionList) { Icon(Icons.Rounded.Tune, stringResource(R.string.tube_subs_manage), tint = sc.Text) }
                    }
                }
            }
            val list = subs
            if (list != null && list.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Subscriptions,
                        title = stringResource(R.string.tube_subs_empty_title),
                        body = stringResource(R.string.tube_subs_empty_body),
                        action = { PillButton(stringResource(R.string.tube_import), onClick = importer, icon = Icons.Rounded.FileUpload) },
                    )
                }
            } else if (list != null) {
                item(key = "channels") {
                    LazyRow(
                        Modifier.padding(vertical = 10.dp),
                        contentPadding = PaddingValues(horizontal = Dimens.Gutter),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(list, key = { it.channelUrl }) { s ->
                            Column(
                                Modifier.width(66.dp).clip(RoundedCornerShape(12.dp)).clickable { nav.openChannel(s.channelUrl, s.name, s.avatarUrl) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                ChannelAvatar(s.avatarUrl, s.name, 58.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(s.name, style = MaterialTheme.typography.labelMedium, color = sc.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                items(state.videos, key = { "s:" + it.id }) { v ->
                    VideoCard(
                        v,
                        onClick = { nav.openVideo(v) },
                        onMore = { menu = VideoMenuTarget(v, feedback = false) },
                        progress = watched[v.id] ?: 0f,
                        onChannel = v.channelUrl?.let { url -> { nav.openChannel(url, v.channelName, v.channelAvatar) } },
                    )
                }
                if (state.loading && state.videos.isEmpty()) item(key = "loading") { LoadingRow(Modifier.padding(top = 40.dp)) }
            }
        }
    }
    menu?.let { target -> VideoActionsSheet(target, onClose = { menu = null }) }
}

/** Opens the file picker for a Google Takeout export and imports its subscriptions. */
@Composable
internal fun rememberSubscriptionImport(): () -> Unit {
    val context = LocalContext.current
    val tube = SoundApplication.graph.tube
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val count = runCatching {
                val name = withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }
                val pairs = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input -> tube.source.readSubscriptions(input, name) }
                }.orEmpty()
                if (pairs.isEmpty()) error("empty")
                val now = System.currentTimeMillis()
                tube.library.importSubscriptions(pairs.map { (url, n) -> Subscription(url, n, null, now) })
            }.getOrNull()
            val message = if (count == null) context.getString(R.string.tube_import_failed)
            else context.resources.getQuantityString(R.plurals.tube_import_done, count, count)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    return { runCatching { launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/zip", "application/json", "text/plain", "*/*")) } }
}

/** All the channels followed, to open or leave, and the import. */
@Composable
fun SubscriptionListScreen() {
    val nav = LocalTubeNav.current
    val tube = SoundApplication.graph.tube
    val subs by tube.library.subscriptions.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val importer = rememberSubscriptionImport()
    Column(Modifier.fillMaxSize()) {
        PageTopBar(stringResource(R.string.tube_your_subs), onBack = nav::back)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
            item(key = "import") {
                LibraryRow(Icons.Rounded.FileUpload, stringResource(R.string.tube_import), stringResource(R.string.tube_import_body), onClick = importer)
            }
            items(subs, key = { it.channelUrl }) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { nav.openChannel(s.channelUrl, s.name, s.avatarUrl) }.padding(horizontal = Dimens.Gutter, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelAvatar(s.avatarUrl, s.name, 46.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(s.name, style = MaterialTheme.typography.titleMedium, color = sc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    SubscribeButton(true, onClick = { scope.launch { tube.library.toggleSubscription(s.channelUrl, s.name, s.avatarUrl) } })
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Biblioteca

/** Biblioteca: history (a shelf), the personal lists, subscriptions and the settings. */
@Composable
fun LibraryScreen() {
    val nav = LocalTubeNav.current
    val tube = SoundApplication.graph.tube
    val history by tube.library.history.collectAsState(initial = emptyList())
    val liked by tube.library.liked.collectAsState(initial = emptyList())
    val later by tube.library.later.collectAsState(initial = emptyList())
    val subs by tube.library.subscriptions.collectAsState(initial = emptyList())
    var menu by remember { mutableStateOf<VideoMenuTarget?>(null) }
    val res = resources()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
        item(key = "head") {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = Dimens.Gutter, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.tube_library_title), style = MaterialTheme.typography.headlineLarge, color = sc.Text, modifier = Modifier.weight(1f))
                IconButton(onClick = nav::openSearch) { Icon(Icons.Rounded.Search, stringResource(R.string.tube_search), tint = sc.Text) }
                IconButton(onClick = nav::openSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.tube_settings), tint = sc.Text) }
            }
        }
        if (history.isNotEmpty()) {
            item(key = "history") {
                Column(Modifier.padding(bottom = 12.dp)) {
                    SectionHeader(title = stringResource(R.string.tube_history), action = stringResource(R.string.tube_see_all), onAction = { nav.openList(VideoLists.HISTORY) })
                    LazyRow(contentPadding = PaddingValues(horizontal = Dimens.Gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(history.take(20), key = { it.videoId }) { w ->
                            val v = VideoItem(w.videoId, w.title, w.channelName, w.channelUrl, null, w.thumbnailUrl, w.durationMs)
                            ShelfVideo(v, onClick = { nav.openVideo(v) }, onMore = { menu = VideoMenuTarget(v, list = VideoLists.HISTORY, feedback = false) }, progress = w.progress)
                        }
                    }
                }
            }
        }
        item(key = "rows") {
            Column {
                LibraryRow(Icons.Rounded.History, stringResource(R.string.tube_history), TubeFormat.videoCount(res, history.size.toLong()), onClick = { nav.openList(VideoLists.HISTORY) })
                LibraryRow(Icons.Rounded.Schedule, stringResource(R.string.tube_later), TubeFormat.videoCount(res, later.size.toLong()), onClick = { nav.openList(VideoLists.LATER) })
                LibraryRow(Icons.Rounded.ThumbUp, stringResource(R.string.tube_liked_videos), TubeFormat.videoCount(res, liked.size.toLong()), onClick = { nav.openList(VideoLists.LIKED) })
                LibraryRow(Icons.Rounded.Subscriptions, stringResource(R.string.tube_your_subs), subs.size.toString(), onClick = nav::openSubscriptionList)
            }
        }
    }
    menu?.let { target -> VideoActionsSheet(target, onClose = { menu = null }) }
}

@Composable
internal fun LibraryRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Dimens.Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(sc.Surface1),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = sc.Accent, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = sc.Text, maxLines = 1)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = sc.TextFaint)
    }
}

@Composable
internal fun OnFirst(block: suspend () -> Unit) {
    LaunchedEffect(Unit) { block() }
}
