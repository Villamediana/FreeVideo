package com.villamediana.sound.tube.player

import android.content.Context
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
import com.villamediana.sound.tube.online.TubeStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * The videos played lately, on disk ([BYTES], least recently played go first). Keyed by video, part
 * and format, so it outlives the expiring googlevideo urls and a quality change never mixes formats.
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

    fun key(id: String, part: String, stream: TubeStream): String {
        val format = if (part == StreamUris.AUDIO_PART) stream.audioMimeType.orEmpty() else "${stream.videoMimeType}:${stream.height}"
        return "tube:$id:$part:$format"
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
                    .setUri(StreamUris.video(id, part))
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
