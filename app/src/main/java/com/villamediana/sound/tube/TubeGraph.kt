package com.villamediana.sound.tube

import android.content.Context
import com.villamediana.sound.R
import com.villamediana.sound.online.NewPipeHttp
import com.villamediana.sound.tube.data.TubeDatabase
import com.villamediana.sound.tube.data.TubeLibrary
import com.villamediana.sound.tube.data.TubeSettings
import com.villamediana.sound.tube.feed.FeedCache
import com.villamediana.sound.tube.feed.FeedLoader
import com.villamediana.sound.tube.online.TubeSource
import com.villamediana.sound.tube.player.TubePlayer
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import java.io.File

/** FreeVideo's services (one of each per process), hung off [com.villamediana.sound.AppGraph.tube]. */
class TubeGraph(private val context: Context, private val http: OkHttpClient, private val scope: CoroutineScope) {
    val db: TubeDatabase by lazy { TubeDatabase.build(context) }
    val library: TubeLibrary by lazy { TubeLibrary(db) }
    val settings: TubeSettings by lazy { TubeSettings(context) }
    val source: TubeSource by lazy {
        NewPipeHttp.init(http)
        TubeSource(http)
    }
    val feed: FeedLoader by lazy {
        FeedLoader(source, library, starterTopics = { context.resources.getStringArray(R.array.tube_starter_topics).toList() })
    }
    val feedCache: FeedCache by lazy { FeedCache(File(context.filesDir, "feed.json")) }
    val player: TubePlayer by lazy { TubePlayer(context, scope, source, library, settings) }
}
