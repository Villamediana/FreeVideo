package com.villamediana.sound.tube.data

import com.villamediana.sound.tube.feed.FeedEngine
import com.villamediana.sound.tube.online.VideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The person's video library, all on the phone: history (with how much of each video was seen),
 * subscriptions, liked videos, "assistir mais tarde", dismissals, searches and picked interests.
 * It is also what the feed learns from ([snapshot]).
 */
class TubeLibrary(db: TubeDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.dao()

    val history: Flow<List<WatchEntry>> = dao.history()
    val subscriptions: Flow<List<Subscription>> = dao.subscriptions()
    val liked: Flow<List<SavedVideo>> = dao.saved(SavedVideo.LIST_LIKED)
    val later: Flow<List<SavedVideo>> = dao.saved(SavedVideo.LIST_LATER)
    val dismissals: Flow<List<Dismissal>> = dao.dismissals()
    val searches: Flow<List<SearchEntry>> = dao.searches()
    val interests: Flow<List<Interest>> = dao.interests()

    /** Channel keys followed (any url form of a channel matches its key). */
    val subscribedKeys: Flow<Set<String>> = subscriptions.map { list -> list.map { FeedEngine.channelKey(it.channelUrl, it.name) }.toSet() }
        .distinctUntilChanged()

    fun isSubscribed(channelUrl: String?, name: String): Flow<Boolean> {
        val key = FeedEngine.channelKey(channelUrl, name)
        return subscribedKeys.map { key in it }.distinctUntilChanged()
    }

    fun isLiked(id: String): Flow<Boolean> = dao.isSaved(SavedVideo.LIST_LIKED, id).map { it > 0 }.distinctUntilChanged()
    fun isInLater(id: String): Flow<Boolean> = dao.isSaved(SavedVideo.LIST_LATER, id).map { it > 0 }.distinctUntilChanged()

    // ------------------------------------------------------------------ history

    /**
     * Notes time spent on [video]: [addWatchedMs] really played since the last note, left at
     * [positionMs]. [newVisit] = the video was just opened (one more play).
     */
    suspend fun recordWatch(video: VideoItem, positionMs: Long, durationMs: Long, addWatchedMs: Long, newVisit: Boolean) {
        val now = clock()
        val old = dao.watch(video.id)
        val length = durationMs.takeIf { it > 0 } ?: video.durationMs.takeIf { it > 0 } ?: old?.durationMs ?: 0
        dao.putWatch(
            WatchEntry(
                videoId = video.id,
                title = video.title.ifBlank { old?.title.orEmpty() },
                channelName = video.channelName.ifBlank { old?.channelName.orEmpty() },
                channelUrl = video.channelUrl ?: old?.channelUrl,
                thumbnailUrl = video.thumbnailUrl ?: old?.thumbnailUrl,
                durationMs = length,
                positionMs = positionMs.coerceAtLeast(0),
                watchedMs = ((old?.watchedMs ?: 0) + addWatchedMs.coerceAtLeast(0)).let { if (length > 0) it.coerceAtMost(length * 4) else it },
                plays = (old?.plays ?: 0) + if (newVisit || old == null) 1 else 0,
                firstWatchedAt = old?.firstWatchedAt ?: now,
                lastWatchedAt = now,
            ),
        )
    }

    suspend fun watch(id: String): WatchEntry? = dao.watch(id)
    suspend fun watches(ids: List<String>): Map<String, WatchEntry> =
        if (ids.isEmpty()) emptyMap() else ids.chunked(500).flatMap { dao.watches(it) }.associateBy { it.videoId }

    suspend fun removeFromHistory(id: String) = dao.deleteWatch(id)
    suspend fun clearHistory() = dao.clearHistory()

    /** The history keeps the last [HISTORY_KEEP] videos (no table growing forever). */
    suspend fun prune() {
        dao.pruneHistory(HISTORY_KEEP)
        dao.pruneImpressions(clock() - IMPRESSIONS_KEEP_MS)
        dao.pruneFacts()
    }

    // ------------------------------------------------------------------ lists

    private fun saved(list: String, v: VideoItem) = SavedVideo(
        list = list,
        videoId = v.id,
        title = v.title,
        channelName = v.channelName,
        channelUrl = v.channelUrl,
        thumbnailUrl = v.thumbnailUrl,
        durationMs = v.durationMs,
        savedAt = clock(),
    )

    /** Likes or unlikes [video]; true = liked now. */
    suspend fun toggleLike(video: VideoItem): Boolean = toggle(SavedVideo.LIST_LIKED, video)

    /** Adds to / takes out of "assistir mais tarde"; true = in the list now. */
    suspend fun toggleLater(video: VideoItem): Boolean = toggle(SavedVideo.LIST_LATER, video)

    private suspend fun toggle(list: String, video: VideoItem): Boolean {
        val there = dao.savedNow(list).any { it.videoId == video.id }
        if (there) dao.deleteSaved(list, video.id) else dao.putSaved(saved(list, video))
        return !there
    }

    suspend fun removeSaved(list: String, id: String) = dao.deleteSaved(list, id)

    // ------------------------------------------------------------------ subscriptions

    /** Follows or unfollows the channel; true = followed now. */
    suspend fun toggleSubscription(channelUrl: String, name: String, avatar: String?): Boolean {
        val key = FeedEngine.channelKey(channelUrl, name)
        val existing = dao.subscriptionsNow().filter { FeedEngine.channelKey(it.channelUrl, it.name) == key }
        if (existing.isNotEmpty()) {
            existing.forEach { dao.deleteSubscription(it.channelUrl) }
            return false
        }
        dao.putSubscription(Subscription(channelUrl, name, avatar, clock()))
        // Following a channel undoes "não recomendar".
        dao.deleteDismissal(Dismissal.channel(channelUrl))
        return true
    }

    /** A followed channel's page was read: keep its picture and name up to date (imports come without one). */
    suspend fun refreshSubscription(channelUrl: String, name: String, avatar: String?) {
        if (avatar.isNullOrBlank()) return
        val key = FeedEngine.channelKey(channelUrl, name)
        dao.subscriptionsNow().filter { FeedEngine.channelKey(it.channelUrl, it.name) == key && (it.avatarUrl != avatar || it.name.isBlank()) }
            .forEach { dao.putSubscription(it.copy(avatarUrl = avatar, name = it.name.ifBlank { name })) }
    }

    /** Subscriptions imported at once (a Google Takeout file); returns how many were new. */
    suspend fun importSubscriptions(list: List<Subscription>): Int {
        val known = dao.subscriptionsNow().map { FeedEngine.channelKey(it.channelUrl, it.name) }.toMutableSet()
        val fresh = list.filter { known.add(FeedEngine.channelKey(it.channelUrl, it.name)) }
        if (fresh.isNotEmpty()) dao.addSubscriptions(fresh)
        return fresh.size
    }

    // ------------------------------------------------------------------ feedback

    suspend fun dismissVideo(video: VideoItem) = dao.putDismissal(Dismissal(Dismissal.video(video.id), video.title, clock()))

    suspend fun dismissChannel(channelUrl: String, name: String) = dao.putDismissal(Dismissal(Dismissal.channel(channelUrl), name, clock()))

    suspend fun undoDismissal(key: String) = dao.deleteDismissal(key)

    /** "Redefinir recomendações": dismissals, picked interests and what the feed learnt from itself go (history stays). */
    suspend fun resetRecommendations() {
        dao.clearDismissals()
        dao.clearInterests()
        dao.clearImpressions()
        dao.clearSourceStats()
    }

    // ------------------------------------------------------------------ what the feed learns from itself

    /** Cards the feed had on screen: [shown] is video id → the source that brought it. */
    suspend fun recordImpressions(shown: Map<String, String>) {
        if (shown.isEmpty()) return
        val now = clock()
        val old = shown.keys.chunked(500).flatMap { dao.impressions(it) }.associateBy { it.videoId }
        dao.putImpressions(shown.keys.map { id -> old[id]?.let { it.copy(shown = it.shown + 1, lastShownAt = now) } ?: Impression(id, 1, 0, now) })
        for ((source, n) in shown.values.groupingBy { it }.eachCount()) bumpSource(source, shown = n.toLong())
    }

    /** A feed card was opened. */
    suspend fun recordClick(videoId: String, source: String) {
        val now = clock()
        val old = dao.impressions(listOf(videoId)).firstOrNull()
        dao.putImpressions(listOf(old?.copy(clicked = old.clicked + 1) ?: Impression(videoId, 1, 1, now)))
        bumpSource(source, clicked = 1)
    }

    /** Time watched on a video that was opened from the feed, credited to the source that brought it. */
    suspend fun creditSource(source: String, watchedMs: Long) {
        if (watchedMs > 0) bumpSource(source, watchedMs = watchedMs)
    }

    private suspend fun bumpSource(source: String, shown: Long = 0, clicked: Long = 0, watchedMs: Long = 0) {
        val old = dao.sourceStat(source) ?: SourceStat(source, 0, 0, 0)
        dao.putSourceStat(old.copy(shown = old.shown + shown, clicked = old.clicked + clicked, watchedMs = old.watchedMs + watchedMs))
    }

    /** What a video is about (its uploader's tags), kept for the videos watched. */
    suspend fun saveFacts(videoId: String, tags: List<String>, category: String?) {
        if (tags.isEmpty() && category.isNullOrBlank()) return
        dao.putFacts(VideoFacts(videoId, tags.map { it.trim() }.filter { it.isNotEmpty() }.take(25).joinToString("\n"), category))
    }

    // ------------------------------------------------------------------ searches & interests

    suspend fun addSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val old = dao.search(q)
        dao.putSearch(SearchEntry(q, (old?.count ?: 0) + 1, clock()))
    }

    suspend fun removeSearch(query: String) = dao.deleteSearch(query)
    suspend fun clearSearches() = dao.clearSearches()

    suspend fun setInterests(topics: List<String>) {
        dao.clearInterests()
        val now = clock()
        dao.putInterests(topics.distinct().mapIndexed { i, t -> Interest(t, now + i) })
    }

    // ------------------------------------------------------------------ feed

    /** Everything the feed learns from, read now. */
    suspend fun snapshot(): FeedEngine.Input {
        val now = clock()
        val history = dao.historyNow(HISTORY_FOR_TASTE)
        val liked = dao.savedNow(SavedVideo.LIST_LIKED)
        val factIds = (history.map { it.videoId } + liked.map { it.videoId }).distinct()
        return FeedEngine.Input(
            history = history,
            liked = liked,
            later = dao.savedNow(SavedVideo.LIST_LATER),
            subscriptions = dao.subscriptionsNow(),
            dismissals = dao.dismissalsNow(),
            searches = dao.searchesNow(),
            interests = dao.interestsNow(),
            impressions = dao.impressionsSince(now - 30 * 86_400_000L).associateBy { it.videoId },
            facts = factIds.chunked(500).flatMap { dao.facts(it) }.associateBy { it.videoId },
            sourceStats = dao.sourceStats().associateBy { it.source },
            now = now,
        )
    }

    companion object {
        const val HISTORY_KEEP = 3000
        const val HISTORY_FOR_TASTE = 400
        const val IMPRESSIONS_KEEP_MS = 45 * 86_400_000L
    }
}
