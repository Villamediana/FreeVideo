package com.villamediana.sound.tube

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.villamediana.sound.download.ROBOLECTRIC_SDK
import com.villamediana.sound.online.NewPipeHttp
import com.villamediana.sound.tube.data.Subscription
import com.villamediana.sound.tube.data.TubeDatabase
import com.villamediana.sound.tube.data.TubeLibrary
import com.villamediana.sound.tube.feed.FeedLoader
import com.villamediana.sound.tube.feed.TopicChip
import com.villamediana.sound.tube.online.Kiosks
import com.villamediana.sound.tube.online.SearchKind
import com.villamediana.sound.tube.online.SearchResult
import com.villamediana.sound.tube.online.TubeLink
import com.villamediana.sound.tube.online.TubeSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * FreeVideo against the real YouTube (SOUND_LIVE=1 only): search, the watch page, the stream,
 * comments, a channel, its RSS, the kiosks, links — and a whole feed made from a small history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class TubeLiveTest {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private fun source(): TubeSource {
        NewPipeHttp.init(http, Locale.forLanguageTag("pt-BR"))
        return TubeSource(http)
    }

    @Test
    fun theVideoSideOfYouTubeAnswers() = runBlocking {
        assumeTrue(System.getProperty("sound.live") == "1")
        val source = source()

        val search = source.search("receita bolo de cenoura", SearchKind.ALL)
        val videos = search.items.filterIsInstance<SearchResult.Video>().map { it.video }
        println("search: ${search.items.size} results, ${videos.size} videos, more=${search.hasMore}")
        videos.take(3).forEach { println("  ${it.id} ${it.title} · ${it.channelName} · ${it.views} views · ${it.uploadedText} · ${it.durationMs / 1000}s") }
        assertTrue(videos.isNotEmpty())
        val more = source.moreSearch(search)
        println("search page 2: ${more.items.size}")

        val pick = videos.first { !it.live && it.durationMs > 0 }
        // From a datacenter IP YouTube may ask to "confirm you're not a bot" on the player endpoint
        // (details/stream). Phones on home or mobile networks don't get it; here it is only reported.
        val detailsTry = runCatching { source.details(pick.id) }
        val blocked = detailsTry.exceptionOrNull()?.let { it is com.villamediana.sound.online.OnlineException && it.kind == com.villamediana.sound.online.OnlineException.Kind.RATE_LIMITED } == true
        if (blocked) println("details/stream: BLOCKED from this IP (bot check) — ${detailsTry.exceptionOrNull()?.message?.take(120)}")
        val details = detailsTry.getOrNull()
        if (details != null) {
            println("details: ${details.video.title} · ${details.video.channelName} (${details.video.channelUrl}) · likes ${details.likes} · subs ${details.channelSubscribers} · related ${details.related.size}")
            println("  description: ${details.description.take(120).replace('\n', ' ')}")
            assertTrue(details.related.isNotEmpty())
            val stream = source.stream(pick.id, 720)
            println("stream: ${stream.width}x${stream.height} ${stream.videoMimeType} + ${stream.audioMimeType}")
            assertTrue(stream.videoUrl.startsWith("http"))
            // The googlevideo url really serves bytes (a bounded range, as the player asks).
            val code = http.newCall(Request.Builder().url(stream.videoUrl).header("Range", "bytes=0-65535").header("User-Agent", NewPipeHttp.USER_AGENT).build())
                .execute().use { it.code }
            println("  first range: HTTP $code")
            assertTrue(code == 200 || code == 206)
        } else if (!blocked) {
            throw detailsTry.exceptionOrNull()!!
        }

        val comments = runCatching { source.comments(pick.id) }
        println("comments: ${comments.getOrNull()?.items?.size} (${comments.exceptionOrNull()?.message?.take(100)})")
        comments.getOrNull()?.items?.take(2)?.forEach { println("  ${it.author}: ${it.text.take(80)}") }

        val channelUrl = details?.video?.channelUrl ?: pick.channelUrl!!
        val channel = source.channel(channelUrl)
        println("channel: ${channel.channel.name} · ${channel.channel.subscribers} subs · ${channel.videos.items.size} videos · banner=${channel.bannerUrl != null}")
        assertTrue(channel.videos.items.isNotEmpty())

        val latest = source.latest(channelUrl, channel.channel.name)
        println("latest (rss): ${latest.size}, dated=${latest.count { it.uploadedAt != null }}, views=${latest.count { it.views >= 0 }}")
        assertTrue(latest.isNotEmpty())

        println("kiosks available: ${source.kiosks()}")
        for (k in listOf(Kiosks.TRENDING, Kiosks.MUSIC, Kiosks.GAMING, Kiosks.LIVE, Kiosks.PODCASTS, Kiosks.MOVIES)) {
            val r = runCatching { source.kiosk(k) }
            println("  kiosk $k: ${r.getOrNull()?.size} ${r.exceptionOrNull()?.let { "(" + it.javaClass.simpleName + ": " + it.message?.take(80) + ")" }.orEmpty()}")
        }

        println("suggestions: ${source.suggestions("bolo de")}")
        val link = source.resolve("olha esse https://youtu.be/${pick.id}?t=90")
        println("link: $link")
        assertEquals(TubeLink.Video(pick.id, 90_000), link)
        val chLink = source.resolve(channelUrl)
        println("channel link: $chLink")
        assertTrue(chLink is TubeLink.Channel)

        if (blocked) return@runBlocking
        val live = source.search("ao vivo agora", SearchKind.VIDEOS).items.filterIsInstance<SearchResult.Video>().map { it.video }.firstOrNull { it.live }
        if (live != null) {
            val ls = runCatching { source.stream(live.id, 720) }
            println("live ${live.id}: hls=${ls.getOrNull()?.hlsUrl != null} ${ls.exceptionOrNull()?.message.orEmpty()}")
        }
    }

    @Test
    fun aFeedFromASmallHistory() = runBlocking {
        assumeTrue(System.getProperty("sound.live") == "1")
        val source = source()
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, TubeDatabase::class.java).allowMainThreadQueries().build()
        val library = TubeLibrary(db)

        // Someone who watched cooking and a bit of astronomy, and follows one channel.
        val watched = source.searchVideos("receita de pão caseiro").take(3) + source.searchVideos("documentário astronomia").take(2)
        for (v in watched) library.recordWatch(v, v.durationMs * 9 / 10, v.durationMs, v.durationMs * 9 / 10, newVisit = true)
        val follow = watched.first()
        library.importSubscriptions(listOf(Subscription(follow.channelUrl!!, follow.channelName, null, System.currentTimeMillis())))
        library.addSearch("pão de fermentação natural")

        val loader = FeedLoader(source, library, starterTopics = { listOf("em alta") })
        val session = loader.newSession(seed = 7)
        val t0 = System.currentTimeMillis()
        val first = loader.first(session)
        println("feed page 1: ${first.size} videos in ${System.currentTimeMillis() - t0} ms")
        first.forEach { println("  [${it.reason}] ${it.video.title.take(60)} · ${it.video.channelName} · ${"%.2f".format(it.score)}") }
        assertTrue(first.size >= 10)
        val channels = first.groupBy { it.video.channelName }.mapValues { it.value.size }
        assertTrue("one channel took over: $channels", channels.values.max() <= 3)
        assertTrue(first.none { e -> watched.any { it.id == e.video.id } })

        // The scroll never ends: page after page, nothing twice, no channel taking over a page.
        val all = first.map { it.video.id }.toMutableSet()
        repeat(6) { n ->
            val t1 = System.currentTimeMillis()
            val page = loader.more(session)
            val reasons = page.groupingBy { it.reason.source }.eachCount()
            println("feed page ${n + 2}: ${page.size} videos in ${System.currentTimeMillis() - t1} ms $reasons")
            assertTrue("page ${n + 2} is short: ${page.size}", page.size >= 8)
            assertTrue(page.none { it.video.id in all })
            assertTrue(page.groupBy { it.video.channelName }.values.maxOf { it.size } <= 3)
            all += page.map { it.video.id }
        }

        val topicSession = loader.newTopic(TopicChip("Games", "games", Kiosks.GAMING))
        val topic = loader.topicFirst(topicSession)
        val topicMore = loader.topicMore(topicSession)
        println("topic Games: ${topic.size} + ${topicMore.size}")
        assertTrue(topic.isNotEmpty())
        assertTrue(topicMore.none { m -> topic.any { it.video.id == m.video.id } })

        // A channel's page goes on to all of its videos.
        val channel = source.channel(follow.channelUrl!!)
        var page = channel.videos
        var count = page.items.size
        var pages = 1
        while (page.hasMore && pages < 4) {
            page = source.moreChannelVideos(page, channel.channel)
            count += page.items.size
            pages++
        }
        println("channel ${channel.channel.name}: $count videos over $pages pages, tabs=${channel.tabs.map { it.kind }}")
        channel.tabs.forEach { tab -> println("  tab ${tab.kind}: ${runCatching { source.channelTab(tab, channel.channel).items.size }.getOrElse { it.message }}") }

        val subs = loader.subscriptionsFeed()
        println("subscriptions feed: ${subs.size}")
        db.close()
    }
}
