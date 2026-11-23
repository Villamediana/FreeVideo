package com.villamediana.sound.tube.feed

import com.villamediana.sound.tube.data.Dismissal
import com.villamediana.sound.tube.data.Impression
import com.villamediana.sound.tube.data.SourceStat
import com.villamediana.sound.tube.data.VideoFacts
import com.villamediana.sound.tube.data.Interest
import com.villamediana.sound.tube.data.SavedVideo
import com.villamediana.sound.tube.data.SearchEntry
import com.villamediana.sound.tube.data.Subscription
import com.villamediana.sound.tube.data.WatchEntry
import com.villamediana.sound.tube.feed.FeedEngine.Candidate
import com.villamediana.sound.tube.feed.FeedEngine.Reason
import com.villamediana.sound.tube.online.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FeedEngineTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun ch(n: Int) = "https://www.youtube.com/channel/UC" + n.toString().padStart(22, '0')

    private fun video(id: String, channel: Int, title: String = "Video $id", views: Long = 10_000, ageDays: Long? = 2, durationMs: Long = 600_000, short: Boolean = false) =
        VideoItem(
            id = id.padEnd(11, '_').take(11),
            title = title,
            channelName = "Channel $channel",
            channelUrl = ch(channel),
            durationMs = durationMs,
            views = views,
            uploadedAt = ageDays?.let { now - it * day },
            short = short,
        )

    private fun watch(v: VideoItem, watchedMs: Long, positionMs: Long = watchedMs, ageDays: Long = 1, plays: Int = 1) = WatchEntry(
        videoId = v.id,
        title = v.title,
        channelName = v.channelName,
        channelUrl = v.channelUrl,
        thumbnailUrl = null,
        durationMs = v.durationMs,
        positionMs = positionMs,
        watchedMs = watchedMs,
        plays = plays,
        firstWatchedAt = now - ageDays * day,
        lastWatchedAt = now - ageDays * day,
    )

    @Test
    fun aVideoWatchedToTheEndSaysMoreThanABounceAndFadesWithTime() {
        val v = video("a", 1)
        val full = FeedEngine.engagement(watch(v, 600_000), now)
        val bounce = FeedEngine.engagement(watch(v, 5_000), now)
        val old = FeedEngine.engagement(watch(v, 600_000, ageDays = 28), now)
        assertTrue(full > bounce * 10)
        // Two half-lives later: a quarter.
        assertEquals(full / 4, old, full * 0.02)
        val replayed = FeedEngine.engagement(watch(v, 600_000, plays = 4), now)
        assertTrue(replayed > full)
    }

    @Test
    fun seedsAreTheVideosReallyWatchedNotTheBounces() {
        val watched = (1..5).map { video("w$it", it) }
        val bounced = video("bounce", 9)
        val input = FeedEngine.Input(
            history = watched.map { watch(it, 500_000) } + watch(bounced, 4_000),
            now = now,
        )
        val taste = FeedEngine.buildTaste(input, seed = 1, seedCount = 10)
        val seedIds = taste.seeds.map { it.videoId }.toSet()
        assertEquals(watched.map { it.id }.toSet(), seedIds)
        assertTrue(taste.seeds.all { it.weight in 0.0..1.0 })
        // Already used seeds are skipped on the next page.
        val next = FeedEngine.buildTaste(input, seed = 2, seedCount = 10, exclude = seedIds.take(3).toSet())
        assertEquals(2, next.seeds.size)
    }

    @Test
    fun watchedDismissedAndShortsStayOutOfTheFeed() {
        val seen = video("seen", 1)
        val started = video("started", 2)
        val dismissed = video("dismissed", 3)
        val mutedChannel = video("muted", 4)
        val short = video("short", 5, short = true, durationMs = 30_000)
        val fresh = video("fresh", 6)
        val input = FeedEngine.Input(
            history = listOf(watch(seen, 590_000), watch(started, 120_000, positionMs = 120_000)),
            dismissals = listOf(Dismissal(Dismissal.video(dismissed.id), "", now), Dismissal(Dismissal.channel(ch(4)), "", now)),
            now = now,
        )
        val taste = FeedEngine.buildTaste(input)
        val candidates = listOf(seen, started, dismissed, mutedChannel, short, fresh).map { Candidate(it, 1.0, Reason.Trending) }
        val ids = FeedEngine.rank(candidates, taste, now).map { it.video.id }
        assertEquals(listOf(fresh.id), ids)
        // A topic page may keep shorts.
        assertTrue(FeedEngine.rank(candidates, taste, now, allowShorts = true).any { it.video.id == short.id })
    }

    @Test
    fun oneChannelNeverFloodsThePage() {
        val many = (1..12).map { video("c1v$it", 1, views = 5_000_000) }
        val others = (2..8).map { video("o$it", it, views = 1_000) }
        val taste = FeedEngine.buildTaste(FeedEngine.Input(now = now))
        val ranked = FeedEngine.rank((many + others).map { Candidate(it, 1.0, Reason.Topic("x")) }, taste, now, limit = 15)
        val fromOne = ranked.count { it.video.channelUrl == ch(1) }
        assertEquals(3, fromOne)
        // Variety at the top: the first two aren't both from the big channel.
        assertFalse(ranked.take(2).all { it.video.channelUrl == ch(1) })
    }

    @Test
    fun whatThePersonLikesRisesFollowedChannelsAndWordsInCommon() {
        val liked = video("liked", 1, title = "Receita de bolo de cenoura fácil")
        val input = FeedEngine.Input(
            liked = listOf(SavedVideo(SavedVideo.LIST_LIKED, liked.id, liked.title, liked.channelName, liked.channelUrl, null, liked.durationMs, now - day)),
            subscriptions = listOf(Subscription(ch(2), "Channel 2", null, now - 10 * day)),
            searches = listOf(SearchEntry("bolo de chocolate", 3, now - day)),
            now = now,
        )
        val taste = FeedEngine.buildTaste(input)
        val cake = video("cake", 7, title = "Bolo de chocolate fofinho em 10 minutos", views = 1_000)
        val fromSub = video("sub", 2, title = "Unboxing", views = 1_000)
        val random = video("random", 8, title = "Top 10 carros de corrida", views = 1_000)
        val ranked = FeedEngine.rank(listOf(random, cake, fromSub).map { Candidate(it, 0.5, Reason.Trending) }, taste, now, seed = 3)
        assertEquals(random.id, ranked.last().video.id)
        assertTrue(taste.topics.contains("bolo de chocolate"))
    }

    @Test
    fun oneSearchDoesntTakeThePageWhenThereIsMore() {
        val a = (1..12).map { Candidate(video("a$it", 100 + it, views = 9_000_000), 1.0, Reason.Topic("bolo")) }
        val b = (1..12).map { Candidate(video("b$it", 200 + it, views = 1_000), 0.6, Reason.Topic("astronomia")) }
        val taste = FeedEngine.buildTaste(FeedEngine.Input(now = now))
        val page = FeedEngine.rank(a + b, taste, now, limit = 12)
        assertEquals(6, page.count { (it.reason as Reason.Topic).query == "bolo" })
        // With nothing else left, the cap gives way instead of cutting the page short.
        assertEquals(12, FeedEngine.rank(a, taste, now, limit = 12).size)
    }

    @Test
    fun aChannelThePersonKeepsLeavingSinksAndSoDoDismissedWords() {
        val left = (1..3).map { watch(video("left$it", 5, title = "Fofoca dos famosos $it"), 4_000) }
        val input = FeedEngine.Input(
            history = left + watch(video("ok", 6, title = "Astronomia para iniciantes"), 500_000),
            dismissals = listOf(Dismissal(Dismissal.video("zzzzzzzzzzz"), "Pegadinha engraçada com susto", now)),
            now = now,
        )
        val taste = FeedEngine.buildTaste(input)
        assertTrue(taste.affinity(ch(5), "") < 0)
        assertTrue((taste.words["pegadinha"] ?: 0.0) < 0)
        val fromLeft = video("new5", 5, title = "Outro assunto qualquer", views = 1_000)
        val neutral = video("new7", 7, title = "Mais um tema diferente", views = 1_000)
        val prank = video("new8", 8, title = "Pegadinha engraçada do susto", views = 1_000)
        val ranked = FeedEngine.rank(listOf(fromLeft, prank, neutral).map { Candidate(it, 0.8, Reason.Topic(it.id)) }, taste, now, seed = 5)
        assertEquals(neutral.id, ranked.first().video.id)
    }

    @Test
    fun aCardShownAgainAndAgainWithoutATapTiresAndLeaves() {
        val tired = video("tired", 1)
        val seenTwice = video("twice", 2)
        val fresh = video("fresh", 3)
        val opened = video("opened", 4)
        val input = FeedEngine.Input(
            impressions = mapOf(
                tired.id to Impression(tired.id, 4, 0, now - day),
                seenTwice.id to Impression(seenTwice.id, 2, 0, now - day),
                opened.id to Impression(opened.id, 6, 1, now - day),
            ),
            now = now,
        )
        val ranked = FeedEngine.rank(listOf(tired, seenTwice, fresh, opened).map { Candidate(it, 1.0, Reason.Topic(it.id)) }, FeedEngine.buildTaste(input), now, seed = 2)
        val ids = ranked.map { it.video.id }
        assertFalse(tired.id in ids)
        assertTrue(opened.id in ids)
        assertEquals(seenTwice.id, ids.last())
    }

    @Test
    fun whatIsBeingWatchedNowPullsHarderThanLastWeeks() {
        val nowWatching = watch(video("bread", 1, title = "Pão caseiro de fermentação lenta"), 550_000, ageDays = 0)
        val lastWeek = (1..2).map { watch(video("space$it", 2, title = "Documentário astronomia buracos negros $it"), 550_000, ageDays = 8) }
        val taste = FeedEngine.buildTaste(FeedEngine.Input(history = listOf(nowWatching) + lastWeek, now = now), seedCount = 3)
        assertTrue(taste.seeds.first { it.videoId == nowWatching.videoId }.session)
        assertEquals(nowWatching.videoId, taste.seeds.maxBy { it.weight }.videoId)
        val bread = video("b2", 8, title = "Pão de fermentação lenta sem sovar", views = 1_000)
        val space = video("s2", 9, title = "Buracos negros: documentário de astronomia", views = 1_000)
        val ranked = FeedEngine.rank(listOf(space, bread).map { Candidate(it, 0.7, Reason.Topic(it.id)) }, taste, now, seed = 1)
        assertEquals(bread.id, ranked.first().video.id)
    }

    @Test
    fun theSameHeadlineTwiceShowsOnce() {
        val a = video("a", 1, title = "Final da Copa: melhores momentos e gols")
        val b = video("b", 2, title = "Melhores momentos e gols da final da Copa")
        val c = video("c", 3, title = "Receita de bolo de fubá cremoso")
        val ranked = FeedEngine.rank(listOf(a, b, c).map { Candidate(it, 1.0, Reason.Topic(it.id)) }, FeedEngine.buildTaste(FeedEngine.Input(now = now)), now)
        assertEquals(2, ranked.size)
        assertTrue(ranked.any { it.video.id == c.id })
    }

    @Test
    fun aSourceThePersonOpensIsTrustedMoreAndOneIgnoredLess() {
        val boost = FeedEngine.sourceBoost(
            mapOf(
                "RELATED" to SourceStat("RELATED", 200, 40, 0),
                "TOPIC" to SourceStat("TOPIC", 200, 2, 0),
                "TRENDING" to SourceStat("TRENDING", 5, 0, 0),
            ),
        )
        assertTrue(boost.getValue(FeedEngine.Source.RELATED) > 1.0)
        assertTrue(boost.getValue(FeedEngine.Source.TOPIC) < 1.0)
        // Too few cards shown to judge.
        assertFalse(FeedEngine.Source.TRENDING in boost)
        assertTrue(FeedEngine.sourceBoost(emptyMap()).isEmpty())
    }

    @Test
    fun tagsThatComeBackInWhatIsWatchedBecomeTopicsAndWords() {
        val a = video("a", 1, title = "Episódio 12")
        val b = video("b", 2, title = "Ao vivo de sábado")
        val input = FeedEngine.Input(
            history = listOf(watch(a, 500_000), watch(b, 500_000)),
            facts = mapOf(
                a.id to VideoFacts(a.id, "marcenaria\nmóveis planejados", null),
                b.id to VideoFacts(b.id, "Marcenaria\nferramentas", null),
            ),
            now = now,
        )
        val taste = FeedEngine.buildTaste(input)
        assertTrue("marcenaria" in taste.topics)
        assertFalse("ferramentas" in taste.topics)
        assertTrue((taste.words["marcenaria"] ?: 0.0) > 0.5)
    }

    @Test
    fun aFittingLengthScoresBetterOnceTheHabitIsKnown() {
        val long = (1..6).map { watch(video("l$it", it, durationMs = 2_400_000), 2_300_000) }
        val taste = FeedEngine.buildTaste(FeedEngine.Input(history = long, now = now))
        assertTrue(FeedEngine.durationFit(2_400_000, taste.duration) > 0.9)
        assertTrue(FeedEngine.durationFit(60_000, taste.duration) < 0.2)
        assertEquals(0.5, FeedEngine.durationFit(60_000, null), 0.0)
    }

    @Test
    fun severalSourcesAgreeingScoreHigherThanOne() {
        val both = video("both", 1, views = 1_000)
        val one = video("one", 2, views = 1_000)
        val taste = FeedEngine.buildTaste(FeedEngine.Input(now = now))
        val ranked = FeedEngine.rank(
            listOf(
                Candidate(both, 0.8, Reason.Related("x")),
                Candidate(both, 0.8, Reason.Topic("y")),
                Candidate(one, 0.8, Reason.Related("x")),
            ),
            taste, now, seed = 1,
        )
        assertEquals(both.id, ranked.first().video.id)
        assertEquals(setOf(FeedEngine.Source.RELATED, FeedEngine.Source.TOPIC), ranked.first().sources)
    }

    @Test
    fun theSameSeedGivesTheSameFeedAndAnotherShufflesNearTies() {
        val candidates = (1..30).map { Candidate(video("v$it", it, views = 10_000), 0.5, Reason.Trending) }
        val taste = FeedEngine.buildTaste(FeedEngine.Input(now = now))
        val a = FeedEngine.rank(candidates, taste, now, seed = 42).map { it.video.id }
        val b = FeedEngine.rank(candidates, taste, now, seed = 42).map { it.video.id }
        val c = FeedEngine.rank(candidates, taste, now, seed = 43).map { it.video.id }
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun alreadyShownVideosDontComeBackOnTheNextPage() {
        val candidates = (1..10).map { Candidate(video("v$it", it), 1.0, Reason.Trending) }
        val taste = FeedEngine.buildTaste(FeedEngine.Input(now = now))
        val first = FeedEngine.rank(candidates, taste, now, limit = 5)
        val second = FeedEngine.rank(candidates, taste, now, limit = 5, shown = first.map { it.video.id }.toSet())
        assertTrue(first.map { it.video.id }.intersect(second.map { it.video.id }.toSet()).isEmpty())
        assertEquals(5, second.size)
    }

    @Test
    fun aNewPersonIsNewUntilTheyGiveAnything() {
        assertTrue(FeedEngine.Input(now = now).isNew)
        assertFalse(FeedEngine.Input(interests = listOf(Interest("Games", now)), now = now).isNew)
    }

    @Test
    fun continueWatchingIsWhatWasStartedLatelyAndNotFinished() {
        val started = watch(video("s", 1), 200_000, positionMs = 200_000)
        val finished = watch(video("f", 2), 600_000, positionMs = 598_000)
        val barely = watch(video("b", 3), 5_000, positionMs = 5_000)
        val old = watch(video("o", 4), 200_000, positionMs = 200_000, ageDays = 40)
        val list = FeedEngine.continueWatching(listOf(started, finished, barely, old), now)
        assertEquals(listOf(started.videoId), list.map { it.videoId })
    }

    @Test
    fun wordsLeaveOutAccentsAndNoise() {
        assertEquals(listOf("receita", "pao", "queijo"), FeedEngine.tokens("Receita de PÃO de queijo (Official Video) 4K"))
    }

    @Test
    fun channelUrlsOfOneChannelShareAKey() {
        val id = "UC" + "a".repeat(22)
        assertEquals(FeedEngine.channelKey("https://www.youtube.com/channel/$id", "x"), FeedEngine.channelKey("https://youtube.com/channel/$id/videos", "y"))
        assertEquals("name:canal", FeedEngine.channelKey(null, " Canal "))
    }

    @Test
    fun theFeedOnDiskComesBackAsItWasSaved() {
        val dir = Files.createTempDirectory("feed").toFile()
        val cache = FeedCache(File(dir, "feed.json"))
        val entries = listOf(
            FeedEngine.Entry(video("a", 1), 1.0, Reason.Related("Seed title"), setOf(FeedEngine.Source.RELATED)),
            FeedEngine.Entry(video("b", 2), 1.0, Reason.Subscription, setOf(FeedEngine.Source.SUBSCRIPTION)),
            FeedEngine.Entry(video("c", 3), 1.0, Reason.Topic("bolo"), setOf(FeedEngine.Source.TOPIC)),
        )
        cache.write(entries, now)
        val back = cache.read()
        assertEquals(entries.map { it.video }, back.map { it.video })
        assertEquals(entries.map { it.reason }, back.map { it.reason })
        dir.deleteRecursively()
    }

    @Test
    fun newVideosComeBeforeOldOnesOfTheSameKind() {
        // Same source, same strength, same words: only the age differs.
        val ages = listOf(1_100L, 500L, 30L, 3L)
        val titles = listOf("Bolo de cenoura fofinho", "Pizza napolitana em casa", "Lasanha de berinjela leve", "Torta de limão gelada")
        val videos = ages.mapIndexed { i, age -> video("age$age", 10 + i, title = titles[i], ageDays = age) }
        val taste = FeedEngine.buildTaste(FeedEngine.Input(now = now))
        for (seed in 1L..5L) {
            val ranked = FeedEngine.rank(videos.map { Candidate(it, 1.0, Reason.Topic("receitas")) }, taste, now, seed = seed)
            assertEquals(listOf("age3", "age30", "age500", "age1100"), ranked.map { it.video.id.trimEnd('_') })
        }
        // Last month still beats last year by a clear margin (not a coin toss between refreshes).
        assertTrue(FeedEngine.freshness(30 * day) > FeedEngine.freshness(365 * day) + 0.15)
    }

    @Test
    fun anOldVideoVeryMuchAboutWhatThePersonLikesKeepsMoreOfItsScore() {
        assertEquals(1.0, FeedEngine.agedFactor(200 * day, fit = 0.0), 1e-9)
        assertEquals(0.5, FeedEngine.agedFactor(4 * 365 * day, fit = 0.0), 1e-9)
        assertEquals(0.75, FeedEngine.agedFactor(4 * 365 * day, fit = 1.0), 1e-9)
        assertEquals(1.0, FeedEngine.agedFactor(null, fit = 0.0), 1e-9)
    }
}
