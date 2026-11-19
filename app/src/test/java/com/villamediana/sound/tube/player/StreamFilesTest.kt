package com.villamediana.sound.tube.player

import com.villamediana.sound.playback.stream.StreamUris
import com.villamediana.sound.tube.online.StreamTag
import com.villamediana.sound.tube.online.TubeStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Two files of one video are never mixed in the cache (that showed as blocks in the picture): the
 * key and the placeholder uri name the exact file, not just its codec and height.
 */
@RunWith(RobolectricTestRunner::class)
class StreamFilesTest {
    private fun url(itag: Int, lmt: String, clen: String, expire: Long = 1_900_000_000, host: String = "rr1---sn-a.googlevideo.com", xtags: String? = null) =
        "https://$host/videoplayback?expire=$expire&itag=$itag&mime=video%2Fmp4&clen=$clen&lmt=$lmt&ip=1.2.3.4" +
            (xtags?.let { "&xtags=$it" } ?: "")

    private fun stream(video: String, audio: String? = url(140, "1700000000000001", "3400000")) =
        TubeStream(video, audio, "video/mp4", "audio/mp4", 1280, 720)

    @Test
    fun theSameFileHasTheSameTagWhateverTheUrlsExpiryOrServer() {
        val a = url(136, "1700000000000000", "52000000")
        val b = url(136, "1700000000000000", "52000000", expire = 1_900_009_999, host = "rr5---sn-b.googlevideo.com")
        assertEquals(StreamTag.of(a), StreamTag.of(b))
    }

    @Test
    fun anotherFormatVersionOrSizeIsAnotherFile() {
        val base = StreamTag.of(url(136, "1700000000000000", "52000000"))
        // 720p60 instead of 720p30: same codec, same height.
        assertNotEquals(base, StreamTag.of(url(298, "1700000000000000", "52000000")))
        // Re-encoded by YouTube.
        assertNotEquals(base, StreamTag.of(url(136, "1711111111111111", "52000000")))
        assertNotEquals(base, StreamTag.of(url(136, "1700000000000000", "51999999")))
        // A dubbed audio track of the same format.
        assertNotEquals(StreamTag.of(url(140, "1", "2", xtags = "acont=original:lang=pt")), StreamTag.of(url(140, "1", "2", xtags = "acont=dubbed:lang=en")))
    }

    @Test
    fun notAGooglevideoUrlHasNoTag() {
        assertNull(StreamTag.of("https://example.com/video.mp4"))
        assertNull(StreamTag.of("not a url"))
    }

    @Test
    fun cacheKeysFollowTheFileNotTheResolution() {
        val p30 = stream(url(136, "1700000000000000", "52000000"))
        val p60 = stream(url(298, "1700000000000000", "61000000"))
        assertEquals(p30.height, p60.height)
        assertNotEquals(VideoCache.key("abc", StreamUris.VIDEO_PART, p30), VideoCache.key("abc", StreamUris.VIDEO_PART, p60))
        // Renewed urls of the same file: same key, the cache keeps serving it.
        val renewed = stream(url(136, "1700000000000000", "52000000", expire = 1_900_005_000))
        assertEquals(VideoCache.key("abc", StreamUris.VIDEO_PART, p30), VideoCache.key("abc", StreamUris.VIDEO_PART, renewed))
        assertTrue(VideoCache.key("abc", StreamUris.AUDIO_PART, p30).contains(":a:"))
    }

    @Test
    fun thePlaceholderUriNamesItsFile() {
        val s = stream(url(136, "1700000000000000", "52000000"))
        val uri = VideoCache.uri("abc", StreamUris.VIDEO_PART, s)
        assertEquals("abc" to StreamUris.VIDEO_PART, StreamUris.videoPart(uri))
        assertEquals(s.videoTag, VideoCache.wanted(uri))
        assertEquals(s.audioTag, VideoCache.wanted(VideoCache.uri("abc", StreamUris.AUDIO_PART, s)))
    }

    @Test
    fun aChangedFileIsFinalAtOnce() {
        val wrapped = java.io.IOException("load", FileChangedException("abc"))
        assertTrue(fileChanged(wrapped))
        assertTrue(!fileChanged(java.io.IOException("other")))
    }

    @Test
    fun theFetchAheadStartsNearWhereItPlaysAndHasABudget() {
        val url = "https://rr1---sn-a.googlevideo.com/videoplayback?itag=136&clen=100000000&lmt=1"
        val size = PrefetchPlan.sizeOf(url)
        assertEquals(100_000_000L, size)
        // From the start: byte 0. Halfway through: half the file, a little before.
        assertEquals(0L, PrefetchPlan.startByte(size, 0, 600_000))
        assertEquals(50_000_000L - PrefetchPlan.BEHIND, PrefetchPlan.startByte(size, 300_000, 600_000))
        // Unknown length or size: from the start.
        assertEquals(0L, PrefetchPlan.startByte(-1, 300_000, 600_000))
        assertEquals(0L, PrefetchPlan.startByte(size, 300_000, 0))
        // Never past the end, never more than the budget.
        assertEquals(10_000_000L, PrefetchPlan.length(size, 90_000_000, 40_000_000))
        assertEquals(40_000_000L, PrefetchPlan.length(size, 0, 40_000_000))
        assertEquals(40_000_000L, PrefetchPlan.length(-1, 0, 40_000_000))
        assertEquals(-1L, PrefetchPlan.sizeOf("https://example.com/v.mp4"))
    }

    @Test
    fun withoutH264Vp9ComesBeforeAv1() {
        fun v(codec: String, mime: String) = com.villamediana.sound.online.VideoStreams.Video("https://v/$codec", 1280, 720, 30, codec, mime, 1_000_000)
        val audio = com.villamediana.sound.online.AudioStream("https://a", "audio/mp4", "m4a", 128_000, null, "mp4a.40.2")
        val picked = com.villamediana.sound.online.VideoStreams.pick(listOf(v("av01.0.05M.08", "video/mp4"), v("vp9", "video/webm")), emptyList(), audio, 720)
        assertEquals("https://v/vp9", picked!!.videoUrl)
    }
}
