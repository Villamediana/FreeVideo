package com.villamediana.sound.ui.tube

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.tube.online.Captions
import com.villamediana.sound.tube.online.Section
import com.villamediana.sound.tube.online.Storyboard
import com.villamediana.sound.ui.sheets.SoundSheet
import com.villamediana.sound.ui.theme.sc
import com.villamediana.sound.ui.video.VideoTime
import com.villamediana.sound.ui.video.rememberVideoProgress
import java.text.Normalizer

/*
 * Reading a video: its transcript (the captions as paragraphs, following the video, searchable, a tap
 * goes to that moment) and its visual summary (a part per chapter — or per stretch of the video — with
 * the picture of that moment and what is said in it). Both come from the video's captions; nothing
 * leaves the phone but the request for them.
 */

/** Where the transcript and summary stand: loading, ready, or nothing to read ([none]) / failed ([error]). */
@Composable
private fun ReadingState(captions: WatchViewModel.CaptionsUi, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
        when {
            captions.loading -> SkeletonLines(6)
            captions.tracks.isEmpty() || captions.none -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SubtitlesOff, null, tint = sc.TextMuted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.tube_captions_none), style = MaterialTheme.typography.bodyLarge, color = sc.TextMuted)
            }
            captions.error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tube_captions_error), style = MaterialTheme.typography.bodyLarge, color = sc.TextMuted, modifier = Modifier.weight(1f))
                RetryPill(onRetry)
            }
        }
    }
}

/** Letters without accents, lower case: "Transcrição" finds "transcricao". */
private fun plain(text: String): String = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/**
 * The transcript: paragraphs with their time; the one being said is lit and kept in sight while the
 * video plays (unless the person is scrolling); a search narrows it to the paragraphs that say it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TranscriptSheet(captions: WatchViewModel.CaptionsUi, onSeek: (Long) -> Unit, onRetry: () -> Unit, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val exo by SoundApplication.graph.tube.player.player.collectAsState()
    val progress = rememberVideoProgress(exo, tickMs = 500)
    val paragraphs = remember(captions.cues) { Captions.paragraphs(captions.cues) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(paragraphs, query) {
        val q = plain(query.trim())
        paragraphs.withIndex().filter { q.isEmpty() || plain(it.value.text).contains(q) }
    }
    val current by remember(paragraphs, progress) { derivedStateOf { Captions.indexAt(paragraphs, progress.positionMs) } }
    val list = rememberLazyListState()
    // The person scrolled lately: the list stays where they put it for a while.
    var touchedAt by remember { mutableLongStateOf(0L) }
    var following by remember { mutableStateOf(false) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { scrolling -> if (scrolling && !following) touchedAt = SystemClock.uptimeMillis() }
    }
    LaunchedEffect(current, query) {
        if (query.isNotBlank() || current < 0 || SystemClock.uptimeMillis() - touchedAt < 4_000) return@LaunchedEffect
        following = true
        runCatching { list.animateScrollToItem((current - 1).coerceAtLeast(0)) }
        following = false
    }
    SoundSheet(onDismissRequest = onClose, sheetState = sheetState) {
        SheetTop(stringResource(R.string.tube_transcript), count = null, onClose = onClose)
        if (captions.cues.isEmpty()) {
            ReadingState(captions, onRetry)
            return@SoundSheet
        }
        SearchField(query, onChange = { query = it }, hint = stringResource(R.string.tube_transcript_search))
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.8f), state = list, contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp)) {
            if (shown.isEmpty()) {
                item { Text(stringResource(R.string.tube_search_empty), style = MaterialTheme.typography.bodyLarge, color = sc.TextMuted, modifier = Modifier.padding(16.dp)) }
            }
            itemsIndexed(shown, key = { _, it -> it.index }) { _, (index, p) ->
                val now = index == current
                val bg by animateColorAsState(if (now) sc.AccentSoft else Color.Transparent, tween(260), label = "para")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .clickable { onSeek(p.startMs) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        VideoTime.format(p.startMs, progress.durationMs),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = sc.Accent,
                        modifier = Modifier.width(56.dp).padding(top = 2.dp),
                    )
                    Text(p.text, style = MaterialTheme.typography.bodyLarge, color = if (now) sc.Text else sc.TextMuted)
                }
            }
        }
    }
}

/** A quiet search pill: the glass, the icon, the words, and × to clear. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(sc.Surface1)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = sc.TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = sc.TextFaint, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = sc.Text),
                cursorBrush = SolidColor(sc.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) CircleIconButton(Icons.Rounded.Close, stringResource(R.string.tube_clear), { onChange("") }, size = 36.dp, iconSize = 18.dp)
    }
}

/**
 * The visual summary: down a timeline, a card per part of the video — its picture (the chapter's,
 * or YouTube's frame of that moment), the time, the chapter's name and what is said there. A tap goes
 * to that moment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SummarySheet(
    captions: WatchViewModel.CaptionsUi,
    sections: List<Section>,
    storyboard: Storyboard?,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parts = sections.filter { it.title != null || it.text.isNotBlank() || it.thumbnailUrl != null || storyboard != null }
    SoundSheet(onDismissRequest = onClose, sheetState = sheetState) {
        SheetTop(stringResource(R.string.tube_summary), count = parts.size.takeIf { it > 0 }?.toString(), onClose = onClose)
        if (parts.isEmpty()) {
            ReadingState(captions, onRetry)
            return@SoundSheet
        }
        val line = sc.Hairline
        val dot = sc.Accent
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.85f), contentPadding = PaddingValues(start = 12.dp, end = 18.dp, top = 12.dp, bottom = 28.dp)) {
            itemsIndexed(parts, key = { _, it -> it.startMs }) { i, part ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    // The timeline: a dot per part, joined by a hairline.
                    Box(
                        Modifier
                            .width(28.dp)
                            .fillMaxHeight()
                            .drawBehind {
                                val x = size.width / 2
                                val top = if (i == 0) 18.dp.toPx() else 0f
                                val bottom = if (i == parts.lastIndex) 18.dp.toPx() else size.height
                                drawLine(line, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
                                drawCircle(dot, radius = 5.dp.toPx(), center = Offset(x, 18.dp.toPx()))
                            },
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(bottom = 18.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(sc.Surface1)
                            .clickable { onSeek(part.startMs) },
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                            val thumb = part.thumbnailUrl
                            if (thumb != null) {
                                AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                // A moment a little into the part says more than its first frame.
                                StoryboardFrame(storyboard?.frameAt(part.startMs + (part.endMs - part.startMs) / 4), Modifier.fillMaxSize())
                            }
                            Text(
                                VideoTime.format(part.startMs, durationMs),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            part.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = sc.Text) }
                            if (part.text.isNotBlank()) Text(part.text, style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted)
                        }
                    }
                }
            }
        }
    }
}
