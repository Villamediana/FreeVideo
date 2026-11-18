package com.villamediana.sound.tube.feed

import com.villamediana.sound.tube.data.TubeLibrary
import com.villamediana.sound.tube.feed.FeedEngine.Candidate
import com.villamediana.sound.tube.feed.FeedEngine.Reason
import com.villamediana.sound.tube.online.ChannelItem
import com.villamediana.sound.tube.online.Kiosks
import com.villamediana.sound.tube.online.SearchKind
import com.villamediana.sound.tube.online.SearchResult
import com.villamediana.sound.tube.online.TubePage
import com.villamediana.sound.tube.online.TubeSource
import com.villamediana.sound.tube.online.VideoItem
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * Goes out and gathers the feed's candidates, as [FeedEngine.Taste] asks, and keeps the home endless.
 *
 * A [Session] is one scroll of the home. Every page brings fresh candidates into its pool and
 * [FeedEngine.rank] picks the next ones from all of it:
 * - **suggestions** after the videos that say most about the person (other seeds on every page);
 * - once those run out, **one step further**: suggestions after videos the feed itself showed and
 *   that scored well — this is what makes the scroll endless, and the score (channels, words, the
 *   session) is what keeps it from drifting away from the person;
 * - new uploads of **subscriptions** and of channels watched often (RSS: light), then page after
 *   page of their **catalog**;
 * - **topics**: a new search each page, and when they run out, the next page of the ones made;
 * - **kiosks**, a little, for what's up now.
 *
 * Requests run a few at a time, each with a timeout; a source that fails just brings nothing. A
 * page is retried with more sources (up to [MAX_ROUNDS]) while it comes back short.
 */
class FeedLoader(
    private val source: TubeSource,
    private val library: TubeLibrary,
    /** Searches for someone the feed knows nothing about yet ("em alta", "música"…), in the phone's language. */
    private val starterTopics: () -> List<String>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val gate = Semaphore(PARALLEL)

    class Session internal constructor(val seed: Long) {
        internal val pool = ArrayList<Candidate>()
        internal val shown = LinkedHashSet<String>()
        internal val usedSeeds = HashSet<String>()
        /** Entries shown that can still be asked "what comes after you?", best first. */
        internal val hops = ArrayList<FeedEngine.Entry>()
        /** Topic → the last page read of its search (null = it ended). */
        internal val topics = LinkedHashMap<String, TubePage<SearchResult>?>()
        /** Channel url → its videos tab, as far as it was read (null page = it ended). */
        internal val catalogs = LinkedHashMap<String, Catalog>()
        internal val usedKiosks = HashSet<String>()
        internal var page = 0
        val shownCount: Int get() = shown.size
    }

    internal class Catalog(val name: String, val subscribed: Boolean, var channel: ChannelItem? = null, var page: TubePage<VideoItem>? = null, var started: Boolean = false)

    fun newSession(seed: Long = clock()): Session = Session(seed)

    /** The first page of a new home. */
    suspend fun first(session: Session): List<FeedEngine.Entry> = page(session)

    /** The next page (the end of the scroll was reached). Empty only when nothing answers (offline). */
    suspend fun more(session: Session): List<FeedEngine.Entry> = page(session)

    private suspend fun page(session: Session): List<FeedEngine.Entry> {
        val input = library.snapshot()
        val out = ArrayList<FeedEngine.Entry>(PAGE_SIZE)
        var round = 0
        while (out.size < MIN_PAGE && round < MAX_ROUNDS) {
            val first = session.page == 0 && round == 0
            val roundSeed = session.seed + session.page * 7919L + round * 104_729L
            val taste = FeedEngine.buildTaste(input, seed = roundSeed, seedCount = if (first) 6 else 4, exclude = session.usedSeeds)
            val fresh = gather(session, input, taste, first, Random(roundSeed))
            session.pool += fresh
            val ranked = FeedEngine.rank(session.pool, taste, input.now, seed = roundSeed, limit = PAGE_SIZE - out.size, shown = session.shown)
            ranked.forEach { session.shown += it.video.id }
            out += ranked
            session.hops += ranked
            round++
            // Nothing new came and nothing was left to rank: the network isn't answering.
            if (fresh.isEmpty() && ranked.isEmpty()) break
        }
        session.page++
        // What was shown leaves the pool; the rest waits for the next pages (the weakest go when it grows too much).
        session.pool.removeAll { it.video.id in session.shown }
        if (session.pool.size > POOL_MAX) {
            session.pool.sortByDescending { it.weight * it.reason.source.strength }
            session.pool.subList(POOL_MAX, session.pool.size).clear()
        }
        return out
    }

    private suspend fun gather(
        session: Session,
        input: FeedEngine.Input,
        taste: FeedEngine.Taste,
        first: Boolean,
        rnd: Random,
    ): List<Candidate> = coroutineScope {
        val jobs = ArrayList<Deferred<List<Candidate>>>()
        val wanted = if (first) 6 else 4

        // 1. What YouTube suggests after the videos that say most about the person.
        for (s in taste.seeds) {
            session.usedSeeds += s.videoId
            jobs += async { fetch { source.related(s.videoId) }.mapIndexed { i, v -> Candidate(v, s.weight * positionWeight(i), Reason.Related(s.title)) } }
        }
        // 2. Out of those: one step further, from the best videos the feed already showed.
        if (taste.seeds.size < wanted) {
            for (hop in nextHops(session, wanted - taste.seeds.size)) {
                session.usedSeeds += hop.video.id
                jobs += async {
                    fetch { source.related(hop.video.id) }.mapIndexed { i, v -> Candidate(v, 0.9 * positionWeight(i), Reason.Similar(hop.video.title)) }
                }
            }
        }
        if (first) {
            // 3. New uploads of the subscriptions and of channels watched often but not followed.
            for (sub in input.subscriptions.shuffled(rnd).take(MAX_SUBSCRIPTIONS)) {
                session.catalogs.getOrPut(sub.channelUrl) { Catalog(sub.name, subscribed = true) }
                jobs += async { fetch { source.latest(sub.channelUrl, sub.name, sub.avatarUrl) }.take(8).map { Candidate(it, 1.0, Reason.Subscription) } }
            }
            for (ch in taste.frequentChannels.take(5)) {
                session.catalogs.getOrPut(ch.url) { Catalog(ch.name, subscribed = false) }
                jobs += async { fetch { source.latest(ch.url, ch.name) }.take(6).map { Candidate(it, ch.weight, Reason.Channel(ch.name)) } }
            }
        } else {
            // 4. Further into the catalog of one of those channels (another one each time).
            nextCatalog(session)?.let { (url, catalog) ->
                jobs += async {
                    val reason = if (catalog.subscribed) Reason.Subscription else Reason.Channel(catalog.name)
                    fetch { readCatalog(url, catalog) }.mapIndexed { i, v -> Candidate(v, 0.7 * positionWeight(i), reason) }
                }
            }
        }
        // 5. Topics: new ones while there are, then the next page of the ones already searched.
        val starters = if (taste.isNew || (taste.seeds.size < 2 && session.hops.isEmpty())) starterTopics().shuffled(rnd) else emptyList()
        val fresh = (taste.topics + starters).filter { it !in session.topics }.take(if (taste.isNew) 3 else if (first) 2 else 1)
        for (t in fresh) {
            session.topics[t] = null
            jobs += async {
                fetch { listOf(source.search(t, SearchKind.VIDEOS)) }.firstOrNull()?.let { page ->
                    session.topics[t] = page.takeIf { it.hasMore }
                    videosOf(page).mapIndexed { i, v -> Candidate(v, positionWeight(i), Reason.Topic(t)) }
                }.orEmpty()
            }
        }
        if (fresh.isEmpty()) {
            session.topics.entries.filter { it.value != null }.let { open ->
                open.getOrNull(session.page % open.size.coerceAtLeast(1))?.let { (t, page) ->
                    jobs += async {
                        fetch { listOf(source.moreSearch(page!!)) }.firstOrNull()?.let { next ->
                            session.topics[t] = next.takeIf { it.hasMore }
                            // Deeper pages of a search are weaker matches.
                            videosOf(next).mapIndexed { i, v -> Candidate(v, 0.7 * positionWeight(i), Reason.Topic(t)) }
                        }.orEmpty()
                    }
                }
            }
        }
        // 6. Kiosks: a little of what's up now (more of it while the feed is still learning).
        val kiosks = KIOSK_ORDER.filter { it !in session.usedKiosks }.take(if (taste.isNew && first) 3 else if (first || session.page % 3 == 2) 1 else 0)
        for (k in kiosks) {
            session.usedKiosks += k
            jobs += async { fetch { source.kiosk(k) }.take(30).mapIndexed { i, v -> Candidate(v, positionWeight(i), Reason.Trending) } }
        }
        jobs.awaitAll().flatten()
    }

    /** The next entries to expand: the best shown ones not used yet, one per channel. */
    private fun nextHops(session: Session, n: Int): List<FeedEngine.Entry> {
        if (n <= 0) return emptyList()
        val channels = HashSet<String>()
        val picked = session.hops
            .asSequence()
            .filter { it.video.id !in session.usedSeeds && !it.video.live && !it.video.short }
            // Trending cards say little about the person: last resort.
            .sortedByDescending { it.score * if (it.reason.source == FeedEngine.Source.TRENDING) 0.4 else 1.0 }
            .filter { channels.add(FeedEngine.channelKey(it.video.channelUrl, it.video.channelName)) }
            .take(n)
            .toList()
        session.hops.removeAll { h -> picked.any { it.video.id == h.video.id } }
        if (session.hops.size > HOPS_MAX) {
            session.hops.sortByDescending { it.score }
            session.hops.subList(HOPS_MAX, session.hops.size).clear()
        }
        return picked
    }

    /** The channel whose catalog is read next: round-robin over the ones that haven't ended. */
    private fun nextCatalog(session: Session): Pair<String, Catalog>? {
        val open = session.catalogs.entries.filter { !it.value.started || it.value.page?.hasMore == true }
        if (open.isEmpty()) return null
        return open[session.page % open.size].toPair()
    }

    private suspend fun readCatalog(url: String, catalog: Catalog): List<VideoItem> {
        if (!catalog.started) {
            catalog.started = true
            val page = source.channel(url)
            catalog.channel = page.channel
            catalog.page = page.videos
            return page.videos.items
        }
        val page = catalog.page ?: return emptyList()
        val channel = catalog.channel ?: return emptyList()
        val next = source.moreChannelVideos(page, channel)
        catalog.page = next
        return next.items
    }

    // ------------------------------------------------------------------ topic pages

    /** One scroll of a topic chip ("Games", "Música", a search the person makes often…). */
    class TopicSession internal constructor(val chip: TopicChip, val seed: Long) {
        internal var page: TubePage<SearchResult>? = null
        internal val shown = HashSet<String>()
        internal var ended = false
    }

    fun newTopic(chip: TopicChip, seed: Long = clock()): TopicSession = TopicSession(chip, seed)

    suspend fun topicFirst(session: TopicSession): List<FeedEngine.Entry> {
        val chip = session.chip
        val input = library.snapshot()
        val taste = FeedEngine.buildTaste(input, seed = session.seed, seedCount = 0)
        val candidates = coroutineScope {
            val jobs = ArrayList<Deferred<List<Candidate>>>()
            chip.kiosk?.let { k -> jobs += async { fetch { source.kiosk(k) }.mapIndexed { i, v -> Candidate(v, positionWeight(i), Reason.Trending) } } }
            chip.query?.let { q ->
                jobs += async {
                    fetch { listOf(source.search(q, SearchKind.VIDEOS)) }.firstOrNull()?.let { page ->
                        session.page = page.takeIf { it.hasMore }
                        videosOf(page).mapIndexed { i, v -> Candidate(v, positionWeight(i), Reason.Topic(q)) }
                    }.orEmpty()
                }
            }
            jobs.awaitAll().flatten()
        }
        if (session.page == null) session.ended = true
        return FeedEngine.rank(candidates, taste, input.now, seed = session.seed, limit = 60).also { list -> list.forEach { session.shown += it.video.id } }
    }

    /** The next page of the topic's search; empty when it ended. */
    suspend fun topicMore(session: TopicSession): List<FeedEngine.Entry> {
        val q = session.chip.query ?: return emptyList()
        val page = session.page ?: return emptyList()
        val next = fetch { listOf(source.moreSearch(page)) }.firstOrNull() ?: return emptyList()
        session.page = next.takeIf { it.hasMore }
        if (session.page == null) session.ended = true
        val input = library.snapshot()
        val taste = FeedEngine.buildTaste(input, seed = session.seed, seedCount = 0)
        // Keep the search's own order here (it is one topic): only filter and de-duplicate.
        val ranked = FeedEngine.rank(videosOf(next).mapIndexed { i, v -> Candidate(v, positionWeight(i), Reason.Topic(q)) }, taste, input.now, seed = session.seed, limit = 40, shown = session.shown)
        ranked.forEach { session.shown += it.video.id }
        return ranked
    }

    fun topicEnded(session: TopicSession): Boolean = session.ended

    /** New uploads of every subscription, newest first (the Inscrições tab). */
    suspend fun subscriptionsFeed(): List<VideoItem> {
        val subs = library.snapshot().subscriptions
        val all = coroutineScope {
            subs.map { sub -> async { fetch { source.latest(sub.channelUrl, sub.name, sub.avatarUrl) } } }.awaitAll().flatten()
        }
        return all.distinctBy { it.id }.filter { !it.short }.sortedByDescending { it.uploadedAt ?: 0 }
    }

    private fun videosOf(page: TubePage<SearchResult>): List<VideoItem> = page.items.filterIsInstance<SearchResult.Video>().map { it.video }

    private suspend fun <T> fetch(block: suspend () -> List<T>): List<T> = try {
        gate.withPermit { withTimeoutOrNull(TIMEOUT_MS) { block() } }.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        emptyList()
    }

    private fun positionWeight(i: Int): Double = 1.0 / (1.0 + i * 0.06)

    companion object {
        const val PAGE_SIZE = 20
        /** A page shorter than this asks more sources before it is handed over. */
        private const val MIN_PAGE = 12
        private const val MAX_ROUNDS = 3
        private const val POOL_MAX = 700
        private const val HOPS_MAX = 80
        private const val PARALLEL = 6
        private const val TIMEOUT_MS = 15_000L
        private const val MAX_SUBSCRIPTIONS = 40
        // "Trending" (YouTube took that page down in 2025) and the movies chart answer nothing now.
        private val KIOSK_ORDER = listOf(Kiosks.MUSIC, Kiosks.GAMING, Kiosks.LIVE, Kiosks.PODCASTS)
    }
}

/** A chip over the home: the person's own topics and a few fixed ones (some backed by a kiosk). */
data class TopicChip(val label: String, val query: String?, val kiosk: String? = null)
