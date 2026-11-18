package com.villamediana.sound.tube.feed

import com.villamediana.sound.tube.online.VideoItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The last home feed on disk: opening the app shows it at once while a fresh one is gathered
 * (and it is what's there without network).
 */
class FeedCache(private val file: File) {
    @Serializable
    private data class Saved(val savedAt: Long, val entries: List<SavedEntry>)

    @Serializable
    private data class SavedEntry(val video: VideoItem, val reason: String, val detail: String = "")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun read(): List<FeedEngine.Entry> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString<Saved>(file.readText()).entries.map { e ->
            val reason = when (e.reason) {
                "related" -> FeedEngine.Reason.Related(e.detail)
                "similar" -> FeedEngine.Reason.Similar(e.detail)
                "subscription" -> FeedEngine.Reason.Subscription
                "channel" -> FeedEngine.Reason.Channel(e.detail)
                "topic" -> FeedEngine.Reason.Topic(e.detail)
                else -> FeedEngine.Reason.Trending
            }
            FeedEngine.Entry(e.video, 0.0, reason, setOf(reason.source))
        }
    }.getOrDefault(emptyList())

    fun write(entries: List<FeedEngine.Entry>, now: Long = System.currentTimeMillis()) {
        runCatching {
            val saved = Saved(
                now,
                entries.take(MAX).map { e ->
                    when (val r = e.reason) {
                        is FeedEngine.Reason.Related -> SavedEntry(e.video, "related", r.seedTitle)
                        is FeedEngine.Reason.Similar -> SavedEntry(e.video, "similar", r.seedTitle)
                        FeedEngine.Reason.Subscription -> SavedEntry(e.video, "subscription")
                        is FeedEngine.Reason.Channel -> SavedEntry(e.video, "channel", r.name)
                        is FeedEngine.Reason.Topic -> SavedEntry(e.video, "topic", r.query)
                        FeedEngine.Reason.Trending -> SavedEntry(e.video, "trending")
                    }
                },
            )
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Saved.serializer(), saved))
            tmp.renameTo(file)
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    private companion object {
        const val MAX = 60
    }
}
