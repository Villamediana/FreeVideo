package com.villamediana.sound.tube.player

import android.content.Context
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.villamediana.sound.playback.stream.AudioCache
import com.villamediana.sound.playback.stream.ChunkedHttpDataSource
import com.villamediana.sound.playback.stream.StreamDataSources
import com.villamediana.sound.playback.stream.StreamUris
import com.villamediana.sound.tube.online.StreamTag
import com.villamediana.sound.tube.online.TubeStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * The videos played lately, on disk ([BYTES], least recently played go first). Keyed by video, part
 * and the exact file ([StreamTag]: format, version, size), so it outlives the expiring googlevideo
 * urls and bytes of two different files never end up under one key.
 */
@OptIn(UnstableApi::class)
internal object VideoCache {
    const val BYTES = 1_500L * 1024 * 1024

    @Volatile private var shared: SimpleCache? = null

    fun get(context: Context): SimpleCache = shared ?: synchronized(this) {
        shared ?: SimpleCache(
            File(context.cacheDir, "video"),
            LeastRecentlyUsedCacheEvictor(BYTES),
            // One index database for every media cache of the app (two helpers on one file can lock it).
            AudioCache.databaseProvider(context),
        ).also { shared = it }
    }

    /** Query parameter of a part's placeholder uri: the [StreamTag] of the file it must read. */
    const val FILE = "f"

    /** Keys before 1.2.0 ("tube:…") named only codec and height: two files could share one. */
    private const val LEGACY_PREFIX = "tube:"
    private const val PREFIX = "tv2:"

    fun tag(part: String, stream: TubeStream): String? = if (part == StreamUris.AUDIO_PART) stream.audioTag else stream.videoTag

    fun key(id: String, part: String, stream: TubeStream): String {
        val file = tag(part, stream)
            ?: if (part == StreamUris.AUDIO_PART) stream.audioMimeType.orEmpty() else "${stream.videoMimeType}:${stream.height}"
        return "$PREFIX$id:$part:$file"
    }

    /** The placeholder uri of one part of [stream]: the resolver only ever answers it with that same file. */
    fun uri(id: String, part: String, stream: TubeStream): Uri {
        val base = StreamUris.video(id, part)
        val file = tag(part, stream) ?: return base
        return base.buildUpon().appendQueryParameter(FILE, file).build()
    }

    /** The file a placeholder uri asks for (null: any). */
    fun wanted(uri: Uri): String? = uri.getQueryParameter(FILE)?.takeIf { it.isNotBlank() }

    /** Drops what 1.1.0 stored: its keys could mix two files (blocks in the picture). Blocking. */
    fun purgeLegacy(context: Context) {
        val cache = get(context)
        runCatching { cache.keys.filter { it.startsWith(LEGACY_PREFIX) }.forEach { cache.removeResource(it) } }
    }

    /** Bytes the cache holds now. */
    fun size(context: Context): Long = runCatching { get(context).cacheSpace }.getOrDefault(0L)

    fun clear(context: Context) {
        val cache = get(context)
        runCatching { cache.keys.toList().forEach { cache.removeResource(it) } }
    }
}

/**
 * Fetches the video playing into [VideoCache] ahead of the player, from the start (what's already
 * there is skipped at once): on Wi-Fi the whole video, on mobile data a stretch ([METERED_BYTES]).
 * The player then reads from disk: the end of the video plays, and seeking works, with no network.
 * One video at a time; a new one (or closing) stops the previous fetch.
 */
@OptIn(UnstableApi::class)
internal class Prefetcher(private val context: Context, private val resolver: ChunkedHttpDataSource.UriResolver) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var writers: List<CacheWriter> = emptyList()

    fun start(id: String, stream: TubeStream) {
        stop()
        val unmetered = isUnmetered()
        job = scope.launch {
            // Let the player have the connection while it starts; then fetch the rest.
            kotlinx.coroutines.delay(START_DELAY_MS)
            val cache = VideoCache.get(context)
            val parts = buildList {
                add(StreamUris.VIDEO_PART)
                if (stream.audioUrl != null) add(StreamUris.AUDIO_PART)
            }
            // Audio first: it is small, and with it the video can at least be heard to the end.
            for (part in parts.reversed()) {
                val source = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(ChunkedHttpDataSource.Factory(StreamDataSources.http(), PREFETCH_CHUNK, resolver))
                    .createDataSource()
                val spec = DataSpec.Builder()
                    .setUri(VideoCache.uri(id, part, stream))
                    .setKey(VideoCache.key(id, part, stream))
                    .apply { if (!unmetered && part == StreamUris.VIDEO_PART) setLength(METERED_BYTES) }
                    .build()
                val writer = CacheWriter(source, spec, null, null)
                writers = writers + writer
                runCatching { writer.cache() }
                if (job?.isCancelled == true) return@launch
            }
        }
    }

    fun stop() {
        writers.forEach { runCatching { it.cancel() } }
        writers = emptyList()
        job?.cancel()
        job = null
    }

    private fun isUnmetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private companion object {
        /** ~8 minutes of 720p: enough to ride out a tunnel or a dead zone. */
        const val METERED_BYTES = 40L * 1024 * 1024
        const val PREFETCH_CHUNK = 4L * 1024 * 1024
        const val START_DELAY_MS = 6_000L
    }
}

/**
 * The urls of a video were renewed and the new ones serve another file (YouTube re-encoded it, or
 * answered with other formats). Reading on would splice two files: the player starts over from the
 * same second with the new file instead.
 */
class FileChangedException(id: String) : java.io.IOException("The stream of $id now serves another file")

/** As the app's policy, but a [FileChangedException] is final at once (retrying reads the same answer). */
@OptIn(UnstableApi::class)
internal class TubeLoadErrorPolicy(
    private val base: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy = com.villamediana.sound.playback.stream.StreamLoadErrorPolicy(),
) : androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy by base {
    override fun getRetryDelayMsFor(loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if (fileChanged(loadErrorInfo.exception)) androidx.media3.common.C.TIME_UNSET else base.getRetryDelayMsFor(loadErrorInfo)
}

/** Whether [t] (or what caused it) is a [FileChangedException]. */
internal fun fileChanged(t: Throwable?): Boolean = generateSequence(t) { it.cause }.take(10).any { it is FileChangedException }
