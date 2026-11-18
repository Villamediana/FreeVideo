package com.villamediana.sound.ui.tube

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.villamediana.sound.tube.online.ChannelItem
import com.villamediana.sound.tube.online.PlaylistItem
import com.villamediana.sound.tube.online.VideoItem
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** FreeVideo's pages. */
@Serializable data object FeedRoute
@Serializable data object SubsRoute
@Serializable data object YouRoute
@Serializable data object TubeSearchRoute
@Serializable data object TubeSettingsRoute
@Serializable data object SubscriptionListRoute

/** The watch page. Carries what the list already showed, so the page has it before the network answers. */
@Serializable
data class WatchRoute(
    val id: String,
    val title: String = "",
    val channel: String = "",
    val channelUrl: String? = null,
    val avatar: String? = null,
    val thumb: String? = null,
    val durationMs: Long = 0,
    val views: Long = -1,
    val uploadedText: String? = null,
    val live: Boolean = false,
    /** Where to start (a shared link with t=…); -1 = where it was left. */
    val startMs: Long = -1,
    /** The feed source whose card was tapped ([com.villamediana.sound.tube.feed.FeedEngine.Source] name). */
    val src: String? = null,
) {
    fun video() = VideoItem(
        id = id,
        title = title,
        channelName = channel,
        channelUrl = channelUrl,
        channelAvatar = avatar,
        thumbnailUrl = thumb,
        durationMs = durationMs,
        views = views,
        uploadedText = uploadedText,
        live = live,
    )
}

@Serializable data class ChannelRoute(val url: String, val name: String = "", val avatar: String? = null)
@Serializable data class TubePlaylistRoute(val url: String, val name: String = "")

/** One of the personal lists: [list] = "history", "liked" or "later". */
@Serializable data class VideoListRoute(val list: String)

/** Thin wrapper over the NavController so screens just say where to go. */
@Stable
class TubeNavigator(val controller: NavHostController) {

    /** [source]: the feed source whose card this was (the feed learns which sources the person opens). */
    fun openVideo(video: VideoItem, startMs: Long = -1, source: com.villamediana.sound.tube.feed.FeedEngine.Source? = null) {
        if (source != null) {
            val graph = com.villamediana.sound.SoundApplication.graph
            graph.scope.launch { runCatching { graph.tube.library.recordClick(video.id, source.name) } }
        }
        val route = WatchRoute(
            id = video.id,
            title = video.title,
            channel = video.channelName,
            channelUrl = video.channelUrl,
            avatar = video.channelAvatar,
            thumb = video.thumbnailUrl,
            durationMs = video.durationMs,
            views = video.views,
            uploadedText = video.uploadedText,
            live = video.live,
            startMs = startMs,
            src = source?.name,
        )
        // One watch page at a time: a suggestion replaces the video (back goes to where it was opened from).
        val onWatch = controller.currentBackStackEntry?.destination?.hasRoute(WatchRoute::class) == true
        controller.navigate(route) {
            if (onWatch) popUpTo<WatchRoute> { inclusive = true }
            launchSingleTop = false
        }
    }

    fun openChannel(url: String, name: String = "", avatar: String? = null) = controller.navigate(ChannelRoute(url, name, avatar))
    fun openChannel(c: ChannelItem) = openChannel(c.url, c.name, c.avatarUrl)
    fun openPlaylist(p: PlaylistItem) = controller.navigate(TubePlaylistRoute(p.url, p.name))
    fun openPlaylist(url: String) = controller.navigate(TubePlaylistRoute(url))
    fun openSearch() = controller.navigate(TubeSearchRoute) { launchSingleTop = true }
    fun openSettings() = controller.navigate(TubeSettingsRoute) { launchSingleTop = true }
    fun openList(list: String) = controller.navigate(VideoListRoute(list))
    fun openSubscriptionList() = controller.navigate(SubscriptionListRoute)

    /** Bottom bar: a tab keeps its own back stack and state. */
    fun switchTab(route: Any) {
        controller.navigate(route) {
            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** "Início" tapped while already there: the feed goes to its top (and refreshes when it is there). */
    val homeAgain = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun back() {
        controller.popBackStack()
    }

    /** Leaves the watch page (the video goes on in the mini player). */
    fun minimize() {
        if (controller.currentBackStackEntry?.destination?.hasRoute(WatchRoute::class) == true) controller.popBackStack()
    }
}

val LocalTubeNav = staticCompositionLocalOf<TubeNavigator> { error("TubeNavigator not provided") }

internal fun NavDestination.isWatch(): Boolean = hasRoute(WatchRoute::class)
internal fun NavDestination.isTab(): Boolean = hasRoute(FeedRoute::class) || hasRoute(SubsRoute::class) || hasRoute(YouRoute::class)
internal fun NavDestination.isFull(): Boolean = isWatch() || hasRoute(TubeSettingsRoute::class)

object VideoLists {
    const val HISTORY = "history"
    const val LIKED = "liked"
    const val LATER = "later"
}
