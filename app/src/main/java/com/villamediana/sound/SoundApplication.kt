package com.villamediana.sound

import kotlinx.coroutines.launch
import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

class SoundApplication : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // If the app ever closes by a crash, its first lines are kept (the update sheet shows why).
        runCatching { com.villamediana.sound.update.ExitReport.catchCrashes(this) }
        graph = AppGraph(this)
        instance = this
        Notifications.createChannels(this)
        // FreeVideo: none of FreeSong's music work (downloads, profile/friends on the relay). The video
        // library's housekeeping, and new versions from FreeVideo's own GitHub releases.
        graph.scope.launch { runCatching { graph.tube.library.prune() } }
        // What 1.1.0 cached could mix two files of a video (blocks in the picture): dropped once, off the main thread.
        graph.scope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { com.villamediana.sound.tube.player.VideoCache.purgeLegacy(this@SoundApplication) } }
        if (graph.updates.enabled) runCatching { com.villamediana.sound.update.UpdateCheckWorker.schedule(this) }
        // Android 12–13: a version ready installs itself once the app leaves the screen (14+ waits by itself).
        runCatching { com.villamediana.sound.update.UpdateIdleWorker.watch(this, graph.updates) }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.http })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(128L * 1024 * 1024).build() }
            .crossfade(180)
            .build()

    companion object {
        lateinit var instance: SoundApplication
            private set
        val graph: AppGraph get() = instance.graph
    }
}
