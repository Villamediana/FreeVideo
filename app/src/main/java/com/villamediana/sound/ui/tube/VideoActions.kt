package com.villamediana.sound.ui.tube

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.feed.FeedEngine
import com.villamediana.sound.tube.online.VideoItem
import com.villamediana.sound.ui.sheets.SheetAction
import com.villamediana.sound.ui.sheets.SheetHeader
import com.villamediana.sound.ui.sheets.SoundSheet
import com.villamediana.sound.ui.theme.sc
import kotlinx.coroutines.launch

/** A video's menu: what was asked for, and what the menu can also offer in that place. */
data class VideoMenuTarget(
    val video: VideoItem,
    /** Why the feed showed it (the "por que" line); null outside the feed. */
    val reason: FeedEngine.Reason? = null,
    /** Shown in a personal list (history / liked / later): "remove from this list". */
    val list: String? = null,
    /** Feedback options ("não tenho interesse", "não recomendar o canal"). */
    val feedback: Boolean = true,
)

/**
 * The ⋮ sheet of a video. [onDismissed] is told when the video should leave the list it is in
 * (not interested, channel muted, removed from a list), so the list can drop it at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionsSheet(target: VideoMenuTarget, onClose: () -> Unit, onDismissed: (VideoItem, channel: Boolean) -> Unit = { _, _ -> }) {
    val tube = SoundApplication.graph.tube
    val nav = LocalTubeNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val video = target.video
    val inLater by remember(video.id) { tube.library.isInLater(video.id) }.collectAsState(initial = false)

    SoundSheet(onDismissRequest = onClose) {
        SheetHeader(
            title = video.title,
            subtitle = video.channelName,
            art = { VideoThumb(video, compact = true, shape = SmallThumbShape) },
        )
        target.reason?.let { reason ->
            SheetAction(Icons.Rounded.Info, reasonText(reason), onClick = {}, tint = sc.TextFaint, labelColor = sc.TextMuted)
        }
        SheetAction(
            Icons.Rounded.Schedule,
            stringResource(if (inLater) R.string.tube_menu_later_remove else R.string.tube_menu_later),
            onClick = {
                scope.launch { tube.library.toggleLater(video) }
                onClose()
            },
        )
        SheetAction(Icons.Rounded.Share, stringResource(R.string.tube_menu_share), onClick = {
            shareVideo(context, video)
            onClose()
        })
        if (!video.channelUrl.isNullOrBlank()) {
            SheetAction(Icons.Rounded.Person, stringResource(R.string.tube_menu_channel), onClick = {
                onClose()
                nav.openChannel(video.channelUrl, video.channelName, video.channelAvatar)
            })
        }
        when (target.list) {
            VideoLists.HISTORY -> SheetAction(Icons.Rounded.DeleteOutline, stringResource(R.string.tube_menu_remove_history), onClick = {
                scope.launch { tube.library.removeFromHistory(video.id) }
                onDismissed(video, false)
                onClose()
            })
            VideoLists.LIKED, VideoLists.LATER -> SheetAction(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.tube_menu_remove_list), onClick = {
                scope.launch { tube.library.removeSaved(target.list, video.id) }
                onDismissed(video, false)
                onClose()
            })
        }
        if (target.feedback) {
            SheetAction(Icons.Rounded.DoNotDisturbOn, stringResource(R.string.tube_menu_not_interested), onClick = {
                scope.launch { tube.library.dismissVideo(video) }
                onDismissed(video, false)
                onClose()
            })
            if (!video.channelUrl.isNullOrBlank()) {
                SheetAction(Icons.Rounded.Block, stringResource(R.string.tube_menu_no_channel), onClick = {
                    scope.launch { tube.library.dismissChannel(video.channelUrl, video.channelName) }
                    onDismissed(video, true)
                    onClose()
                })
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun reasonText(reason: FeedEngine.Reason): String = when (reason) {
    is FeedEngine.Reason.Related -> stringResource(R.string.tube_why_related, reason.seedTitle)
    is FeedEngine.Reason.Similar -> stringResource(R.string.tube_why_similar, reason.seedTitle)
    FeedEngine.Reason.Subscription -> stringResource(R.string.tube_why_subscription)
    is FeedEngine.Reason.Channel -> stringResource(R.string.tube_why_channel, reason.name)
    is FeedEngine.Reason.Topic -> stringResource(R.string.tube_why_topic, reason.query)
    FeedEngine.Reason.Trending -> stringResource(R.string.tube_why_trending)
}

/** Shares the video's YouTube link (opens in any app, FreeVideo included). */
fun shareVideo(context: Context, video: VideoItem) {
    val text = "${video.title}\nhttps://youtu.be/${video.id}"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.tube_menu_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun shareLink(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.tube_menu_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
