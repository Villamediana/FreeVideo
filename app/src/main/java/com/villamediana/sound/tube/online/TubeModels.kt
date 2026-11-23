package com.villamediana.sound.tube.online

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** One video as lists show it (feed, search, related, channel). Unknown numbers are 0 / -1 / null. */
@Serializable
data class VideoItem(
    val id: String,
    val title: String,
    val channelName: String,
    val channelUrl: String? = null,
    val channelAvatar: String? = null,
    val thumbnailUrl: String? = null,
    /** 0 = unknown (lives, RSS entries). */
    val durationMs: Long = 0,
    /** -1 = unknown. */
    val views: Long = -1,
    /** Upload time (epoch ms; YouTube's "há 3 dias" is approximate). */
    val uploadedAt: Long? = null,
    /** YouTube's own "há 3 dias", in the phone's language. */
    val uploadedText: String? = null,
    val live: Boolean = false,
    val short: Boolean = false,
    val verified: Boolean = false,
)

@Serializable
data class ChannelItem(
    val url: String,
    val name: String,
    val avatarUrl: String? = null,
    /** -1 = hidden / unknown. */
    val subscribers: Long = -1,
    val videos: Long = -1,
    val description: String? = null,
    val verified: Boolean = false,
)

@Serializable
data class PlaylistItem(
    val url: String,
    val name: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val count: Long = -1,
)

/** A search result. */
sealed interface SearchResult {
    val key: String

    data class Video(val video: VideoItem) : SearchResult {
        override val key get() = "v:" + video.id
    }

    data class Channel(val channel: ChannelItem) : SearchResult {
        override val key get() = "c:" + channel.url
    }

    data class Playlist(val playlist: PlaylistItem) : SearchResult {
        override val key get() = "p:" + playlist.url
    }
}

enum class SearchKind { ALL, VIDEOS, CHANNELS, PLAYLISTS }

/** A page of results and what continues it (null = the end). */
data class TubePage<T>(val items: List<T>, val cursor: Any? = null, /** All there is (comments), -1 = unknown. */ val total: Long = -1) {
    val hasMore: Boolean get() = cursor != null
}

/** The watch page: the video's facts, its description and what YouTube suggests next. */
data class VideoDetails(
    val video: VideoItem,
    val description: String,
    val likes: Long,
    val channelSubscribers: Long,
    val tags: List<String>,
    val category: String?,
    val related: List<VideoItem>,
    val ageLimit: Int = 0,
    /** YouTube's preview frames (the seek bar's pictures); null when it has none. */
    val storyboard: Storyboard? = null,
    /** The captions the video has (the channel's and YouTube's automatic ones). */
    val captions: List<CaptionTrack> = emptyList(),
    /** Its chapters, when the channel marked them. */
    val chapters: List<Chapter> = emptyList(),
)

data class CommentItem(
    val id: String,
    val author: String,
    val authorAvatar: String?,
    val authorUrl: String?,
    val text: String,
    val likes: Long,
    val published: String?,
    val replies: Int,
    val pinned: Boolean,
    val hearted: Boolean,
    val byUploader: Boolean,
    val verified: Boolean = false,
    /** Where its replies are read from (the source's own business); null = none. */
    val repliesCursor: Any? = null,
)

enum class ChannelTabKind { VIDEOS, SHORTS, LIVE, PLAYLISTS }

/** One tab of a channel's page (what continues it is the source's own business). */
class ChannelTab internal constructor(val kind: ChannelTabKind, internal val handler: Any)

/** A channel's page: who they are, their latest uploads and the tabs it has. */
data class ChannelPage(
    val channel: ChannelItem,
    val bannerUrl: String?,
    val videos: TubePage<VideoItem>,
    val tabs: List<ChannelTab> = emptyList(),
)

data class PlaylistPage(
    val playlist: PlaylistItem,
    val videos: TubePage<VideoItem>,
)

/** What the player plays: video (+ separate audio) or, for a live, an HLS manifest. */
data class TubeStream(
    val videoUrl: String,
    val audioUrl: String?,
    val videoMimeType: String,
    val audioMimeType: String?,
    val width: Int,
    val height: Int,
    val hlsUrl: String? = null,
) {
    /** Which exact file the video url serves (format, version, size): see [StreamTag]. */
    val videoTag: String? get() = StreamTag.of(videoUrl)

    /** Which exact file the audio url serves; null when the video carries its own sound. */
    val audioTag: String? get() = audioUrl?.let(StreamTag::of)
}

/**
 * The identity of the bytes a googlevideo url serves: format (`itag`), file version (`lmt`), size
 * (`clen`) and variant (`xtags`: a dubbed audio track, for one). Two urls with the same tag serve the
 * same file byte for byte, so ranges of one can follow ranges of the other; with different tags they
 * can't, even at the same resolution and codec (mixing them shows as blocks in the picture).
 * Null for urls that don't say (not googlevideo).
 */
object StreamTag {
    fun of(url: String?): String? {
        val u = url?.toHttpUrlOrNull() ?: return null
        val itag = u.queryParameter("itag")?.takeIf { it.isNotBlank() } ?: return null
        val parts = listOfNotNull(
            itag,
            u.queryParameter("lmt")?.takeIf { it.isNotBlank() },
            u.queryParameter("clen")?.takeIf { it.isNotBlank() },
            u.queryParameter("xtags")?.takeIf { it.isNotBlank() }?.let { Integer.toHexString(it.hashCode()) },
        )
        return parts.joinToString("-")
    }
}

/** A YouTube link pasted or shared. */
sealed interface TubeLink {
    data class Video(val id: String, val startMs: Long = 0) : TubeLink
    data class Channel(val url: String) : TubeLink
    data class Playlist(val url: String) : TubeLink
}

/** Kiosks the feed and the topic chips use (ids as NewPipe names them). */
object Kiosks {
    const val TRENDING = "Trending"
    const val LIVE = "live"
    const val MUSIC = "trending_music"
    const val GAMING = "trending_gaming"
    const val MOVIES = "trending_movies_and_shows"
    const val PODCASTS = "trending_podcasts_episodes"
}
