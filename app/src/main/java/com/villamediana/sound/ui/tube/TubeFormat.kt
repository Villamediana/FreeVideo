package com.villamediana.sound.ui.tube

import android.content.res.Resources
import android.icu.text.CompactDecimalFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.villamediana.sound.R
import com.villamediana.sound.tube.online.VideoItem
import java.util.Locale

/** How FreeVideo writes numbers and times ("1,2 mi de visualizações", "há 3 dias"). */
object TubeFormat {
    /** 1.234.567 → "1,2 mi" (the phone's language). */
    fun compact(n: Long): String = runCatching {
        CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT).format(n)
    }.getOrDefault(n.toString())

    fun views(res: Resources, video: VideoItem): String? {
        if (video.views < 0) return null
        return if (video.live) {
            res.getQuantityString(R.plurals.tube_watching, video.views.toQuantity(), compact(video.views))
        } else {
            res.getQuantityString(R.plurals.tube_views, video.views.toQuantity(), compact(video.views))
        }
    }

    fun subscribers(res: Resources, n: Long): String? =
        if (n < 0) null else res.getQuantityString(R.plurals.tube_subscribers, n.toQuantity(), compact(n))

    fun videoCount(res: Resources, n: Long): String? =
        if (n < 0) null else res.getQuantityString(R.plurals.tube_videos_count, n.toQuantity(), compact(n))

    /** "há 3 dias": YouTube's own text when it gave one, else from the upload time. */
    fun age(video: VideoItem, now: Long = System.currentTimeMillis()): String? {
        if (video.live) return null
        video.uploadedText?.takeIf { it.isNotBlank() }?.let { return it }
        val at = video.uploadedAt ?: return null
        return DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }

    /** "Canal · 1,2 mi de visualizações · há 3 dias", leaving out what isn't known. */
    fun meta(res: Resources, video: VideoItem, withChannel: Boolean = true): String =
        listOfNotNull(
            video.channelName.takeIf { withChannel && it.isNotBlank() },
            views(res, video),
            age(video),
        ).joinToString(" · ")

    private fun Long.toQuantity(): Int = if (this > Int.MAX_VALUE) Int.MAX_VALUE else toInt()
}

@Composable
@ReadOnlyComposable
internal fun resources(): Resources = LocalContext.current.resources
