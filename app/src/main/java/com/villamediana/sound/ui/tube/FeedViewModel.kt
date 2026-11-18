package com.villamediana.sound.ui.tube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.data.WatchEntry
import com.villamediana.sound.tube.feed.FeedEngine
import com.villamediana.sound.tube.feed.FeedLoader
import com.villamediana.sound.tube.feed.TopicChip
import com.villamediana.sound.tube.online.VideoItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The home: the last feed from disk at once, then a fresh one ([FeedLoader]); more at the end of
 * the scroll; pull to refresh draws a new one. Topic chips swap the list for that topic's.
 */
class FeedViewModel : ViewModel() {
    private val tube = SoundApplication.graph.tube

    data class UiState(
        val entries: List<FeedEngine.Entry> = emptyList(),
        /** Nothing on screen yet and the first page is on its way. */
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        /** The last load failed (offline…) and there is nothing to show. */
        val error: Boolean = false,
        val chip: TopicChip? = null,
        val chipEntries: List<FeedEngine.Entry> = emptyList(),
        val chipLoading: Boolean = false,
        val chipError: Boolean = false,
        val chipLoadingMore: Boolean = false,
        /** The last "more" brought nothing (offline): the list waits and tries again. */
        val stalled: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Watched videos by id (progress lines on the thumbnails). */
    val watched: StateFlow<Map<String, WatchEntry>> = tube.library.history
        .map { list -> list.associateBy { it.videoId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val continueWatching: StateFlow<List<WatchEntry>> = tube.library.history
        .map { FeedEngine.continueWatching(it, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "Do que você gosta?": a person the feed knows nothing about, who wasn't asked yet. */
    val askInterests: StateFlow<Boolean> = combine(
        tube.library.history,
        tube.library.subscriptions,
        tube.library.interests,
        tube.settings.prefs,
    ) { history, subs, interests, prefs ->
        history.isEmpty() && subs.isEmpty() && interests.isEmpty() && !prefs.interestsAsked
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The person's own topics for the chips: picked interests, then frequent searches. */
    val ownTopics: StateFlow<List<String>> = combine(tube.library.interests, tube.library.searches) { interests, searches ->
        (interests.map { it.topic } + searches.sortedByDescending { it.count * 2 + if (System.currentTimeMillis() - it.lastAt < 7 * 86_400_000L) 1 else 0 }.map { it.query })
            .map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var session: FeedLoader.Session = tube.feed.newSession()
    private var loadedAt = 0L

    /** Cards seen and not yet written down: video id → the source that brought it. */
    private val seen = HashMap<String, String>()
    private val noted = HashSet<String>()

    /** A feed card stayed on screen: the feed learns what it shows and isn't opened. */
    fun seen(entry: FeedEngine.Entry) {
        if (!noted.add(entry.video.id)) return
        seen[entry.video.id] = entry.reason.source.name
        if (seen.size == 1) {
            viewModelScope.launch {
                delay(IMPRESSION_FLUSH_MS)
                flushSeen()
            }
        }
    }

    private fun flushSeen() {
        if (seen.isEmpty()) return
        val batch = HashMap(seen)
        seen.clear()
        SoundApplication.graph.scope.launch { runCatching { tube.library.recordImpressions(batch) } }
    }

    override fun onCleared() {
        flushSeen()
    }

    /** Back on the home after a good while (and at the top): a fresh feed, quietly. */
    fun refreshIfStale() {
        val s = _state.value
        if (loadedAt > 0 && System.currentTimeMillis() - loadedAt > STALE_MS && !s.refreshing && !s.loading && s.chip == null) refresh()
    }
    private var job: Job? = null
    private var chipJob: Job? = null

    init {
        val cached = tube.feedCache.read()
        if (cached.isNotEmpty()) _state.value = UiState(entries = cached, loading = false, refreshing = true)
        load(fresh = true)
    }

    fun refresh() {
        if (_state.value.chip != null) {
            selectChip(_state.value.chip)
            return
        }
        _state.value = _state.value.copy(refreshing = true, error = false)
        load(fresh = true)
    }

    private fun load(fresh: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            val newSession = tube.feed.newSession()
            try {
                val first = tube.feed.first(newSession)
                session = newSession
                if (first.isEmpty() && _state.value.entries.isNotEmpty()) {
                    // Nothing came back (offline): keep what's there.
                    _state.value = _state.value.copy(loading = false, refreshing = false, error = false)
                    return@launch
                }
                _state.value = _state.value.copy(entries = first, loading = false, refreshing = false, error = first.isEmpty(), stalled = false)
                loadedAt = System.currentTimeMillis()
                noted.clear()
                if (first.isNotEmpty()) tube.feedCache.write(first)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _state.value = _state.value.copy(loading = false, refreshing = false, error = _state.value.entries.isEmpty())
            }
        }
    }

    /** The end of the scroll is near: the next page (of the home, or of the topic chosen). */
    fun loadMore() {
        val s = _state.value
        if (s.chip != null) return moreOfChip()
        if (s.loading || s.refreshing || s.loadingMore || s.entries.isEmpty()) return
        if (job?.isActive == true) return
        _state.value = s.copy(loadingMore = true, stalled = false)
        job = viewModelScope.launch {
            val more = try {
                tube.feed.more(session)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                emptyList()
            }
            val known = _state.value.entries.map { it.video.id }.toSet()
            val fresh = more.filter { it.video.id !in known }
            _state.value = _state.value.copy(entries = _state.value.entries + fresh, loadingMore = false, stalled = fresh.isEmpty())
            if (fresh.isEmpty()) {
                // Nothing answered: wait a little and let the scroll ask again.
                delay(STALL_RETRY_MS)
                _state.value = _state.value.copy(stalled = false)
            }
        }
    }

    private fun moreOfChip() {
        val s = _state.value
        val topic = topicSession ?: return
        if (s.chipLoading || s.chipLoadingMore || s.chipEntries.isEmpty() || tube.feed.topicEnded(topic)) return
        _state.value = s.copy(chipLoadingMore = true)
        chipJob = viewModelScope.launch {
            val more = try {
                tube.feed.topicMore(topic)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                emptyList()
            }
            if (topicSession !== topic) return@launch
            val known = _state.value.chipEntries.map { it.video.id }.toSet()
            _state.value = _state.value.copy(chipEntries = _state.value.chipEntries + more.filter { it.video.id !in known }, chipLoadingMore = false)
        }
    }

    private var topicSession: FeedLoader.TopicSession? = null

    fun selectChip(chip: TopicChip?) {
        chipJob?.cancel()
        if (chip == null) {
            topicSession = null
            _state.value = _state.value.copy(chip = null, chipEntries = emptyList(), chipLoading = false, chipError = false, chipLoadingMore = false)
            return
        }
        _state.value = _state.value.copy(chip = chip, chipEntries = if (_state.value.chip == chip) _state.value.chipEntries else emptyList(), chipLoading = true, chipError = false)
        val topic = tube.feed.newTopic(chip)
        topicSession = topic
        chipJob = viewModelScope.launch {
            try {
                val list = tube.feed.topicFirst(topic)
                if (topicSession !== topic) return@launch
                _state.value = _state.value.copy(chipEntries = list, chipLoading = false, chipError = list.isEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _state.value = _state.value.copy(chipLoading = false, chipError = true)
            }
        }
    }

    /** Takes [video] (or every video of its channel) off the lists at once. */
    fun drop(video: VideoItem, channel: Boolean) {
        val key = FeedEngine.channelKey(video.channelUrl, video.channelName)
        fun keep(e: FeedEngine.Entry) =
            if (channel) FeedEngine.channelKey(e.video.channelUrl, e.video.channelName) != key else e.video.id != video.id
        val s = _state.value
        _state.value = s.copy(entries = s.entries.filter(::keep), chipEntries = s.chipEntries.filter(::keep))
        tube.feedCache.write(_state.value.entries)
    }

    fun saveInterests(topics: List<String>) {
        viewModelScope.launch {
            tube.settings.setInterestsAsked(true)
            if (topics.isNotEmpty()) {
                tube.library.setInterests(topics)
                _state.value = _state.value.copy(refreshing = true)
                load(fresh = true)
            }
        }
    }

    private companion object {
        const val STALL_RETRY_MS = 4_000L
        const val IMPRESSION_FLUSH_MS = 4_000L
        const val STALE_MS = 30 * 60_000L
    }
}
