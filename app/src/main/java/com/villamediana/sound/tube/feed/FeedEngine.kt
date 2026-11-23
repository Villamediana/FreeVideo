package com.villamediana.sound.tube.feed

import com.villamediana.sound.tube.data.Dismissal
import com.villamediana.sound.tube.data.Impression
import com.villamediana.sound.tube.data.Interest
import com.villamediana.sound.tube.data.SavedVideo
import com.villamediana.sound.tube.data.SearchEntry
import com.villamediana.sound.tube.data.SourceStat
import com.villamediana.sound.tube.data.Subscription
import com.villamediana.sound.tube.data.VideoFacts
import com.villamediana.sound.tube.data.WatchEntry
import com.villamediana.sound.tube.online.VideoItem
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The feed's brain, pure Kotlin (no network, no Android): what the person seems to like ([Taste],
 * learnt from what stays on the phone) and how a pile of candidate videos becomes a feed ([rank]).
 *
 * 1. **Taste.** Every video watched counts by how much of it was seen, how often and how lately
 *    (half-life of two weeks); a like counts a lot, "assistir mais tarde" a little. A video left after
 *    a few seconds, or dismissed, counts *against* its channel and its words. From that come:
 *    - the channels and the words the person likes (and the ones they don't) — words from titles and,
 *      better, from the tags of the videos watched;
 *    - the **session**: what is being watched right now (the last hours) weighs extra, so the feed
 *      follows the mood of the moment without forgetting the long run;
 *    - the lengths the person usually watches to the end;
 *    - the **seeds** (videos whose "a seguir" feeds the home) and the **topics** worth a search:
 *      searches made, interests picked, and tags that keep coming back in what is watched.
 * 2. **The feed learns from itself.** A card shown several times and never opened tires and leaves;
 *    and each source (suggestions, subscriptions, topics…) is trusted more or less by how often
 *    its cards get opened by this person.
 * 3. **Score.** Source strength + channel affinity + words in common + the session + freshness +
 *    a pinch of popularity + a fitting length, minus what was watched, dismissed or tired.
 * 4. **Order.** Greedy, with variety: a channel loses strength each time it is picked, look-alike
 *    titles don't sit together (and near-copies are dropped), one search or one seed can't take the
 *    page, and every few slots goes to something new (exploration).
 *
 * Deterministic for a given [seed]: the same refresh gives the same feed (and tests can check it);
 * a new refresh shuffles seeds, topics and near-ties.
 */
object FeedEngine {

    // ------------------------------------------------------------------ input

    /** Everything known about the person — all of it local. */
    data class Input(
        /** Newest first. */
        val history: List<WatchEntry> = emptyList(),
        val liked: List<SavedVideo> = emptyList(),
        val later: List<SavedVideo> = emptyList(),
        val subscriptions: List<Subscription> = emptyList(),
        val dismissals: List<Dismissal> = emptyList(),
        val searches: List<SearchEntry> = emptyList(),
        val interests: List<Interest> = emptyList(),
        /** Cards the feed showed lately, by video. */
        val impressions: Map<String, Impression> = emptyMap(),
        /** Tags of the videos watched, by video. */
        val facts: Map<String, VideoFacts> = emptyMap(),
        /** How each source does, by [Source.name]. */
        val sourceStats: Map<String, SourceStat> = emptyMap(),
        val now: Long,
    ) {
        val isNew: Boolean get() = history.isEmpty() && liked.isEmpty() && subscriptions.isEmpty() && interests.isEmpty() && searches.isEmpty()
    }

    /** A video to pull YouTube's suggestions from, with how much it says about the person. */
    data class Seed(val videoId: String, val title: String, val weight: Double, val session: Boolean = false)

    /** The lengths the person watches to the end: mean and spread of ln(seconds). */
    data class DurationTaste(val meanLn: Double, val spreadLn: Double)

    /** What the person seems to like. Weights are −1..1 (1 = the strongest like, negative = turned away from). */
    class Taste(
        val channels: Map<String, Double>,
        val words: Map<String, Double>,
        /** What is being watched right now (0..1). */
        val sessionWords: Map<String, Double>,
        val sessionChannels: Set<String>,
        val seeds: List<Seed>,
        /** Searches, interests and recurring tags worth a topic shelf, strongest first. */
        val topics: List<String>,
        val subscribed: Set<String>,
        val dismissedVideos: Set<String>,
        val dismissedChannels: Set<String>,
        val watched: Map<String, WatchEntry>,
        /** Channels watched a lot but not followed (their uploads are worth a look), strongest first. */
        val frequentChannels: List<FrequentChannel>,
        val duration: DurationTaste?,
        /** How much each source is trusted with this person (1 = as designed). */
        val sourceBoost: Map<Source, Double>,
        val impressions: Map<String, Impression>,
        val isNew: Boolean,
    ) {
        fun affinity(channelUrl: String?, channelName: String): Double = channels[channelKey(channelUrl, channelName)] ?: 0.0
    }

    data class FrequentChannel(val url: String, val name: String, val weight: Double)

    // ------------------------------------------------------------------ taste

    private const val DAY = 86_400_000.0
    /** "Right now": what was watched in the last hours steers the feed. */
    const val SESSION_MS = 3 * 3_600_000L

    private fun seenShare(e: WatchEntry): Double =
        if (e.durationMs > 0) e.completion.toDouble() else (e.watchedMs / 300_000.0).coerceAtMost(1.0)

    /** Opened and left almost at once: not for them. */
    fun isBounce(e: WatchEntry): Boolean = e.watchedMs < 20_000 && seenShare(e) < 0.1

    /** How much one watch says about the person (0..~3). */
    fun engagement(e: WatchEntry, now: Long): Double {
        val seen = seenShare(e)
        val base = if (isBounce(e)) 0.05 else 0.25 + seen
        val replays = 1 + 0.35 * ln(e.plays.coerceAtLeast(1).toDouble())
        return base * replays * decay(now - e.lastWatchedAt, halfLifeDays = 14.0)
    }

    fun buildTaste(input: Input, seed: Long = 0, seedCount: Int = 6, exclude: Set<String> = emptySet()): Taste {
        val now = input.now
        val channels = HashMap<String, Double>()
        val words = HashMap<String, Double>()
        val sessionWords = HashMap<String, Double>()
        val sessionChannels = HashSet<String>()
        val seedWeights = LinkedHashMap<String, Seed>()
        val names = HashMap<String, Pair<String, String>>()
        val tagScores = HashMap<String, Double>()
        val tagVideos = HashMap<String, Int>()
        var lnSum = 0.0
        var lnSq = 0.0
        var lnWeight = 0.0
        var lnCount = 0

        fun addChannel(url: String?, name: String, w: Double) {
            if (name.isBlank() && url.isNullOrBlank()) return
            val key = channelKey(url, name)
            channels[key] = (channels[key] ?: 0.0) + w
            if (!url.isNullOrBlank()) names[key] = url to name
        }
        fun addWords(into: HashMap<String, Double>, text: String, w: Double) {
            val tokens = tokens(text)
            if (tokens.isEmpty()) return
            val each = w / sqrt(tokens.size.toDouble())
            tokens.forEach { into[it] = (into[it] ?: 0.0) + each }
        }
        fun tagsOf(id: String): List<String> =
            input.facts[id]?.tags?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

        for (e in input.history) {
            val tags = tagsOf(e.videoId).take(12)
            if (isBounce(e)) {
                // Left at once (and never came back to it): a little less of that channel and those words.
                if (e.plays <= 1) {
                    val neg = 0.3 * decay(now - e.lastWatchedAt, halfLifeDays = 14.0)
                    addChannel(e.channelUrl, e.channelName, -neg)
                    addWords(words, e.title, -neg * 0.5)
                }
                continue
            }
            val w = engagement(e, now)
            addChannel(e.channelUrl, e.channelName, w)
            addWords(words, e.title, w)
            if (tags.isNotEmpty()) addWords(words, tags.joinToString(" "), w * 0.8)
            for (tag in tags) {
                val t = tag.lowercase()
                if (t.length < 4 || t.length > 40) continue
                tagScores[t] = (tagScores[t] ?: 0.0) + w
                tagVideos[t] = (tagVideos[t] ?: 0) + 1
            }
            val inSession = now - e.lastWatchedAt < SESSION_MS
            if (inSession) {
                addWords(sessionWords, e.title + " " + tags.joinToString(" "), w)
                sessionChannels += channelKey(e.channelUrl, e.channelName)
            }
            val seen = seenShare(e)
            if (seen >= 0.3 || e.watchedMs >= 120_000) {
                // What is being watched now pulls twice as hard.
                seedWeights[e.videoId] = Seed(e.videoId, e.title, if (inSession) w * 2 else w, inSession)
            }
            if (seen >= 0.5 && e.durationMs > 20_000) {
                val l = ln(e.durationMs / 1000.0)
                lnSum += l * w
                lnSq += l * l * w
                lnWeight += w
                lnCount++
            }
        }
        for (v in input.liked) {
            val w = 1.5 * decay(now - v.savedAt, halfLifeDays = 60.0)
            addChannel(v.channelUrl, v.channelName, w)
            addWords(words, v.title, w)
            tagsOf(v.videoId).take(12).takeIf { it.isNotEmpty() }?.let { addWords(words, it.joinToString(" "), w * 0.8) }
            val prev = seedWeights[v.videoId]
            seedWeights[v.videoId] = Seed(v.videoId, v.title, (prev?.weight ?: 0.0) + w, prev?.session == true)
        }
        for (v in input.later) {
            val w = 0.4 * decay(now - v.savedAt, halfLifeDays = 30.0)
            addChannel(v.channelUrl, v.channelName, w)
            addWords(words, v.title, w)
        }
        for (s in input.subscriptions) addChannel(s.channelUrl, s.name, 1.0)
        for (s in input.searches) addWords(words, s.query, 0.8 * sqrt(s.count.toDouble()) * decay(now - s.lastAt, halfLifeDays = 21.0))
        for (i in input.interests) addWords(words, i.topic, 0.6)

        // "Não tenho interesse": less of those words. "Não recomendar o canal": never again.
        val dismissedVideos = HashSet<String>()
        val dismissedChannels = HashSet<String>()
        for (d in input.dismissals) {
            if (d.key.startsWith("v:")) {
                dismissedVideos += d.key.removePrefix("v:")
                addWords(words, d.title, -0.5 * decay(now - d.createdAt, halfLifeDays = 45.0))
            } else if (d.key.startsWith("c:")) {
                dismissedChannels += channelKey(d.key.removePrefix("c:"), "")
            }
        }
        dismissedChannels.forEach { channels.remove(it) }

        val subscribed = input.subscriptions.map { channelKey(it.channelUrl, it.name) }.toSet()
        val rnd = Random(seed)
        val seedPool = seedWeights.values.filter { it.videoId !in exclude && it.videoId !in dismissedVideos }
        val pickedSeeds = weightedSample(seedPool, seedCount, rnd) { it.weight }
        val maxSeed = pickedSeeds.maxOfOrNull { it.weight }?.takeIf { it > 0 } ?: 1.0

        // Topics: searches made, interests picked, and tags that come back in several videos watched.
        val topicScores = HashMap<String, Double>()
        for (s in input.searches) {
            val q = s.query.trim()
            if (q.length >= 2) topicScores[q] = (topicScores[q] ?: 0.0) + sqrt(s.count.toDouble()) * decay(now - s.lastAt, halfLifeDays = 21.0)
        }
        for (i in input.interests) topicScores[i.topic] = (topicScores[i.topic] ?: 0.0) + 0.7
        val maxTag = tagScores.values.maxOrNull() ?: 0.0
        if (maxTag > 0) {
            tagScores.entries.filter { (tagVideos[it.key] ?: 0) >= 2 }.sortedByDescending { it.value }.take(8).forEach { (tag, score) ->
                if (topicScores.keys.none { it.equals(tag, ignoreCase = true) }) topicScores[tag] = 0.6 * score / maxTag
            }
        }
        val topics = weightedSample(topicScores.entries.toList(), topicScores.size, rnd) { it.value }.map { it.key }

        val frequent = channels.entries
            .filter { it.key !in subscribed && it.key in names && it.value >= 0.6 }
            .sortedByDescending { it.value }
            .take(10)
            .map { FrequentChannel(names.getValue(it.key).first, names.getValue(it.key).second, it.value) }
        val maxFrequent = frequent.maxOfOrNull { it.weight }?.takeIf { it > 0 } ?: 1.0

        val duration = if (lnCount >= 5 && lnWeight > 0) {
            val mean = lnSum / lnWeight
            DurationTaste(mean, sqrt((lnSq / lnWeight - mean * mean).coerceAtLeast(0.0)).coerceAtLeast(0.5))
        } else null

        return Taste(
            channels = normalize(channels),
            words = normalize(words),
            sessionWords = normalize(sessionWords),
            sessionChannels = sessionChannels,
            seeds = pickedSeeds.map { it.copy(weight = (it.weight / maxSeed).coerceIn(0.0, 1.0)) },
            topics = topics,
            subscribed = subscribed,
            dismissedVideos = dismissedVideos,
            dismissedChannels = dismissedChannels,
            watched = input.history.associateBy { it.videoId },
            frequentChannels = frequent.map { it.copy(weight = (it.weight / maxFrequent).coerceIn(0.0, 1.0)) },
            duration = duration,
            sourceBoost = sourceBoost(input.sourceStats),
            impressions = input.impressions,
            isNew = input.isNew,
        )
    }

    /**
     * How much to trust each source with this person: its open rate against everyone's, smoothed
     * (a source needs [BOOST_MIN_SHOWN] cards shown before it moves) and kept within 0.75..1.35.
     */
    fun sourceBoost(stats: Map<String, SourceStat>): Map<Source, Double> {
        val known = Source.entries.mapNotNull { src -> stats[src.name]?.let { src to it } }
        val shown = known.sumOf { it.second.shown }
        if (shown < BOOST_MIN_SHOWN) return emptyMap()
        val overall = (known.sumOf { it.second.clicked } + 1.0) / (shown + 12.0)
        return known.filter { it.second.shown >= BOOST_MIN_SHOWN }.associate { (src, s) ->
            val rate = (s.clicked + 12.0 * overall) / (s.shown + 12.0)
            src to (rate / overall).coerceIn(0.75, 1.35)
        }
    }

    private const val BOOST_MIN_SHOWN = 20

    // ------------------------------------------------------------------ candidates

    /** Why a video is in the feed (shown in its menu: "Por que estou vendo isso?"). */
    sealed interface Reason {
        val source: Source

        /** YouTube suggests it after a video the person watched. */
        data class Related(val seedTitle: String) : Reason {
            override val source get() = Source.RELATED
        }

        /** One step further: suggested after a video the feed itself showed (keeps the feed endless). */
        data class Similar(val seedTitle: String) : Reason {
            override val source get() = Source.SIMILAR
        }

        data object Subscription : Reason {
            override val source get() = Source.SUBSCRIPTION
        }

        data class Channel(val name: String) : Reason {
            override val source get() = Source.CHANNEL
        }

        data class Topic(val query: String) : Reason {
            override val source get() = Source.TOPIC
        }

        data object Trending : Reason {
            override val source get() = Source.TRENDING
        }
    }

    enum class Source(val strength: Double) {
        RELATED(1.0),
        SUBSCRIPTION(0.95),
        CHANNEL(0.75),
        TOPIC(0.7),
        SIMILAR(0.6),
        TRENDING(0.4),
    }

    /** A video one source proposed, with how strongly ([weight], 0..1) and why. */
    data class Candidate(val video: VideoItem, val weight: Double, val reason: Reason)

    /** One feed entry. */
    data class Entry(val video: VideoItem, val score: Double, val reason: Reason, val sources: Set<Source>)

    // ------------------------------------------------------------------ scoring

    /**
     * Ranks [candidates] into at most [limit] entries, leaving out [shown] (already in the feed).
     * Shorts are left out (the feed is for videos); [allowShorts] keeps them (a topic page).
     */
    fun rank(
        candidates: List<Candidate>,
        taste: Taste,
        now: Long,
        seed: Long = 0,
        limit: Int = 40,
        shown: Set<String> = emptySet(),
        allowShorts: Boolean = false,
    ): List<Entry> {
        val rnd = Random(seed xor 0x5DEECE66DL)
        val merged = LinkedHashMap<String, MutableList<Candidate>>()
        for (c in candidates) {
            if (c.video.id in shown) continue
            merged.getOrPut(c.video.id) { mutableListOf() } += c
        }
        val scored = ArrayList<Entry>(merged.size)
        for ((id, list) in merged) {
            score(id, list, taste, now, allowShorts)?.let { e ->
                // Near-ties move a little between refreshes.
                scored += e.copy(score = e.score * (0.92 + 0.16 * rnd.nextDouble()))
            }
        }
        return diversify(scored, taste, limit)
    }

    /** One video's score from everything that proposed it; null = it has no place in the feed. */
    private fun score(id: String, list: List<Candidate>, taste: Taste, now: Long, allowShorts: Boolean): Entry? {
        fun strength(c: Candidate) = c.weight * c.reason.source.strength * (taste.sourceBoost[c.reason.source] ?: 1.0)
        val best = list.maxBy(::strength)
        val v = list.maxBy { richness(it.video) }.video
        if (!allowShorts && v.short) return null
        if (id in taste.dismissedVideos) return null
        val ch = channelKey(v.channelUrl, v.channelName)
        if (ch in taste.dismissedChannels) return null
        var factor = 1.0
        taste.watched[id]?.let { w ->
            // Seen (or most of it): out of the feed. Started: it lives in "Continuar assistindo".
            if (w.completion >= 0.8 || w.progress >= 0.1) return null
            factor *= if (w.watchedMs < 30_000) 0.4 else 0.6
        }
        taste.impressions[id]?.let { imp ->
            // Shown and never opened: it tires, then leaves for a while.
            if (imp.clicked == 0) {
                if (imp.shown >= TIRED_AFTER && now - imp.lastShownAt < 14 * DAY) return null
                factor *= 0.8.pow(imp.shown)
            }
        }
        // The strongest proposal counts whole, every other source half of its own.
        val strengths = list.groupBy { it.reason.source }.map { (_, cs) -> cs.maxOf(::strength) }.sortedDescending()
        val rel = strengths.first() + strengths.drop(1).sumOf { it * 0.5 }
        val aff = taste.channels[ch] ?: 0.0
        val sub = if (ch in taste.subscribed) 1.0 else 0.0
        val kw = wordMatch(v.title, taste.words)
        val session = maxOf(wordMatch(v.title, taste.sessionWords), if (ch in taste.sessionChannels) 0.6 else 0.0)
        val age = v.uploadedAt?.let { (now - it).coerceAtLeast(0) }
        val fresh = freshness(age)
        val pop = if (v.views > 0) ((log10(v.views + 1.0) - 3) / 4).coerceIn(0.0, 1.0) else 0.3
        val length = durationFit(v.durationMs, taste.duration)
        var score = 1.0 * rel + 0.5 * aff + 0.3 * sub + 0.55 * kw + 0.4 * session + 0.6 * fresh + 0.12 * pop + 0.15 * length
        // Old videos weigh less — unless they are very much the person's thing (then age matters less).
        factor *= agedFactor(age, fit = maxOf(kw, aff, session).coerceIn(0.0, 1.0))
        if (best.reason.source == Source.SUBSCRIPTION) {
            // A new upload of a followed channel is news; an old one is just catalog.
            score += 0.25 * fresh
            if (age != null && age > 21 * DAY) score *= 0.5
        }
        if (v.live && sub == 0.0) score *= 0.8
        // A channel the person keeps leaving: much less of it.
        if (aff < -0.1) factor *= (1 + aff * 1.5).coerceAtLeast(0.15)
        return Entry(v, (score * factor).coerceAtLeast(0.0), best.reason, list.map { it.reason.source }.toSet())
    }

    /**
     * 0..1: how new a video is. The first days count most (a week's half-life), and the last months
     * still count a little (a quarter's), so a video of last month beats one of last year.
     */
    fun freshness(ageMs: Long?): Double {
        if (ageMs == null) return 0.3
        return 0.65 * decay(ageMs, halfLifeDays = 7.0) + 0.35 * decay(ageMs, halfLifeDays = 90.0)
    }

    /**
     * What an old video keeps of its score: all of it up to a year, then less and less (down to 0.5 at
     * three years). [fit] (0..1: how much it is about what the person likes) gives back up to half of it.
     */
    fun agedFactor(ageMs: Long?, fit: Double): Double {
        if (ageMs == null) return 1.0
        val years = ageMs / (365 * DAY)
        val base = when {
            years <= 1.0 -> 1.0
            years >= 3.0 -> 0.5
            else -> 1.0 - 0.25 * (years - 1.0)
        }
        return 1.0 - (1.0 - base) * (1.0 - 0.5 * fit)
    }

    /** 0..1: how well a length fits what the person watches to the end (0.5 while unknown). */
    fun durationFit(durationMs: Long, taste: DurationTaste?): Double {
        if (taste == null || durationMs <= 0) return 0.5
        val z = (ln(durationMs / 1000.0) - taste.meanLn) / taste.spreadLn
        return exp(-0.5 * z * z)
    }

    /**
     * Greedy pick with variety: a channel already picked weighs 0.55× per time (and three per page at
     * most), a title much like the last ones weighs less (and a near-copy of one already picked is
     * dropped), one search / one seed / the kiosks give [MAX_PER_REASON] videos per page at most, a
     * fourth video in a row from one source weighs less, and every [EXPLORE_EVERY]th slot goes to the
     * best video from a channel the person doesn't know yet, if there is one. When the caps leave
     * nothing, the reason cap gives way (a short page is worse than a repetitive one).
     */
    private fun diversify(entries: List<Entry>, taste: Taste, limit: Int): List<Entry> {
        val pool = entries.sortedByDescending { it.score }.toMutableList()
        val words = HashMap<String, Set<String>>(pool.size)
        fun wordsOf(e: Entry) = words.getOrPut(e.video.id) { tokens(e.video.title).toSet() }
        val out = ArrayList<Entry>(limit)
        val perChannel = HashMap<String, Int>()
        val perReason = HashMap<String, Int>()

        fun pick(explore: Boolean, capReasons: Boolean): Int {
            var bestIndex = -1
            var bestScore = Double.NEGATIVE_INFINITY
            val recent = out.takeLast(4)
            for ((i, e) in pool.withIndex()) {
                // Nothing further down can win: the pool is sorted and every adjustment only lowers.
                if (e.score <= bestScore) break
                val ch = channelKey(e.video.channelUrl, e.video.channelName)
                val n = perChannel[ch] ?: 0
                if (n >= MAX_PER_CHANNEL) continue
                val reason = reasonKey(e.reason)
                // What's up in general is a pinch once the person is known; a whole helping only while they aren't.
                val cap = if (reason == "k" && !taste.isNew) MAX_TRENDING else MAX_PER_REASON
                if (capReasons && reason != null && (perReason[reason] ?: 0) >= cap) continue
                if (explore && abs(taste.channels[ch] ?: 0.0) > 0.05) continue
                var s = e.score * 0.55.pow(n)
                if (recent.size >= 3 && recent.takeLast(3).all { it.reason.source == e.reason.source }) s *= 0.85
                val mine = wordsOf(e)
                val alike = recent.maxOfOrNull { jaccard(mine, wordsOf(it)) } ?: 0.0
                s *= 1 - 0.5 * alike
                if (s > bestScore) {
                    bestScore = s
                    bestIndex = i
                }
            }
            return bestIndex
        }

        while (out.size < limit && pool.isNotEmpty()) {
            val slot = out.size
            val explore = !taste.isNew && slot > 0 && slot % EXPLORE_EVERY == EXPLORE_EVERY - 1
            val index = pick(explore, capReasons = true).takeIf { it >= 0 }
                ?: (if (explore) pick(false, capReasons = true).takeIf { it >= 0 } else null)
                ?: pick(false, capReasons = false).takeIf { it >= 0 }
                ?: break
            val picked = pool.removeAt(index)
            // The same video uploaded twice (or the same headline from two channels): one is enough.
            val mine = wordsOf(picked)
            if (mine.size >= 3 && out.any { jaccard(mine, wordsOf(it)) >= NEAR_COPY }) continue
            val ch = channelKey(picked.video.channelUrl, picked.video.channelName)
            perChannel[ch] = (perChannel[ch] ?: 0) + 1
            reasonKey(picked.reason)?.let { perReason[it] = (perReason[it] ?: 0) + 1 }
            out += picked
        }
        return out
    }

    /** What one reason is, for its cap (subscriptions and channels are capped per channel instead). */
    private fun reasonKey(r: Reason): String? = when (r) {
        is Reason.Related -> "r:" + r.seedTitle
        is Reason.Similar -> "s:" + r.seedTitle
        is Reason.Topic -> "t:" + r.query
        Reason.Trending -> "k"
        Reason.Subscription, is Reason.Channel -> null
    }

    private const val MAX_PER_REASON = 6
    private const val MAX_TRENDING = 2
    private const val EXPLORE_EVERY = 6
    private const val MAX_PER_CHANNEL = 3
    private const val TIRED_AFTER = 4
    private const val NEAR_COPY = 0.75

    // ------------------------------------------------------------------ helpers

    /** "Continuar assistindo": started, not finished, lately. */
    fun continueWatching(history: List<WatchEntry>, now: Long, limit: Int = 12): List<WatchEntry> =
        history.filter {
            it.durationMs > 60_000 && it.progress in 0.03f..0.92f && now - it.lastWatchedAt < 21 * DAY
        }.sortedByDescending { it.lastWatchedAt }.take(limit)

    /** One key per channel: its UC… id when the url has it, else the url, else the name. */
    fun channelKey(url: String?, name: String): String {
        if (!url.isNullOrBlank()) {
            Regex("(UC[A-Za-z0-9_-]{22})").find(url)?.let { return it.value }
            return url.trim().lowercase().removeSuffix("/")
        }
        return "name:" + name.trim().lowercase()
    }

    fun decay(ageMs: Long, halfLifeDays: Double): Double =
        0.5.pow(ageMs.coerceAtLeast(0) / (halfLifeDays * DAY))

    /** How much of [title] is about what the person likes (−1..1; negative = about what they turn away from). */
    fun wordMatch(title: String, words: Map<String, Double>): Double {
        if (words.isEmpty()) return 0.0
        val t = tokens(title)
        if (t.isEmpty()) return 0.0
        return (t.sumOf { words[it] ?: 0.0 } / sqrt(t.size.toDouble())).coerceIn(-1.0, 1.0)
    }

    /** Lowercase words without accents, minus the little ones and YouTube's title noise. */
    fun tokens(text: String): List<String> {
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && !it.all(Char::isDigit) && it !in STOP }
            .distinct()
    }

    /** Share of words two titles have in common (0..1). */
    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val common = a.count { it in b }
        return common.toDouble() / (a.size + b.size - common)
    }

    /** Likes scaled to 0..1 by the strongest; dislikes by the same measure, down to −1. */
    private fun normalize(map: Map<String, Double>): Map<String, Double> {
        val max = map.values.maxOrNull()?.takeIf { it > 0 } ?: map.values.minOrNull()?.let { -it }?.takeIf { it > 0 } ?: return emptyMap()
        return map.mapValues { (it.value / max).coerceIn(-1.0, 1.0) }
    }

    /** Fuller rows win when two sources bring the same video (one has the length, the other the views…). */
    private fun richness(v: VideoItem): Int =
        (if (v.durationMs > 0) 2 else 0) + (if (v.views >= 0) 1 else 0) + (if (v.uploadedAt != null) 1 else 0) + (if (v.channelAvatar != null) 1 else 0)

    /** [n] items drawn without replacement, each with probability ∝ its weight (Efraimidis–Spirakis). */
    fun <T> weightedSample(items: List<T>, n: Int, rnd: Random, weight: (T) -> Double): List<T> {
        if (n <= 0 || items.isEmpty()) return emptyList()
        return items
            .map { it to (weight(it).coerceAtLeast(1e-9)) }
            .map { (item, w) -> item to rnd.nextDouble().coerceAtLeast(1e-12).pow(1.0 / w) }
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }
    }

    private val STOP = setOf(
        // pt
        "que", "com", "para", "por", "uma", "uns", "umas", "dos", "das", "nos", "nas", "pelo", "pela", "pelos", "pelas",
        "mais", "muito", "muita", "isso", "esse", "essa", "este", "esta", "isto", "sobre", "porque", "quando", "como",
        "voce", "voces", "meu", "minha", "seu", "sua", "nao", "sim", "tudo", "todo", "toda", "todos", "quem", "qual",
        "onde", "ate", "entre", "depois", "antes", "aqui", "ali", "ser", "foi", "sao", "tem", "ter", "vai", "vou",
        "fiz", "fez", "ele", "ela", "eles", "elas", "nosso", "nossa", "agora", "hoje", "novo", "nova", "novos",
        "parte", "episodio", "video", "videos", "canal", "oficial", "clipe", "completo", "completa", "vivo",
        // en
        "the", "and", "for", "you", "your", "with", "this", "that", "from", "how", "why", "what", "who", "are",
        "was", "were", "has", "have", "not", "but", "all", "out", "about", "into", "its", "his", "her", "they",
        "official", "music", "full", "part", "episode", "new", "best", "live", "feat", "lyrics", "lyric",
        // es
        "los", "las", "del", "una", "por", "con", "para", "como", "mas", "pero", "sus", "este", "esta", "esto",
        "todo", "nuevo", "nueva", "parte", "capitulo",
        // noise
        "hd", "4k", "1080p", "720p", "ft", "vs", "shorts", "short",
    )
}
