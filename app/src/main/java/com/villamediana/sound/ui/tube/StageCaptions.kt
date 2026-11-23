package com.villamediana.sound.ui.tube

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.villamediana.sound.R
import com.villamediana.sound.tube.online.CaptionChoice
import com.villamediana.sound.tube.online.CaptionTrack
import com.villamediana.sound.tube.online.Captions
import com.villamediana.sound.tube.online.Cue
import com.villamediana.sound.ui.video.VideoProgress
import java.util.Locale

/** The line said now, over the bottom of the video: white on a dark glass, two lines at most. */
@Composable
internal fun StageCaption(cues: List<Cue>, progress: VideoProgress, big: Boolean, modifier: Modifier = Modifier) {
    val cue by remember(cues, progress) { derivedStateOf { Captions.at(cues, progress.positionMs) } }
    val line = cue?.text ?: return
    Text(
        line,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (big) 20.sp else 15.sp, lineHeight = if (big) 26.sp else 20.sp),
        color = Color.White,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = if (big) 720.dp else 520.dp)
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** How a captions choice is called: "English", "English (automáticas)", "Português (traduzidas)". */
@Composable
internal fun captionLabel(choice: CaptionChoice): String {
    if (choice.translateTo != null) return stringResource(R.string.tube_captions_translated, languageName(choice.translateTo))
    val name = choice.track.name.ifBlank { languageName(choice.track.language) }
    return if (choice.track.auto) stringResource(R.string.tube_captions_auto, name) else name
}

/** "Português", "English": a language in its own words. */
internal fun languageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }.ifBlank { tag }
}

/**
 * The captions to pick, in a row of pills over the dimmed video: off, each track the video has,
 * and — when none is in the person's language — YouTube's translation into it.
 */
@Composable
internal fun StageCaptionPicker(
    visible: Boolean,
    tracks: List<CaptionTrack>,
    language: String,
    selected: CaptionChoice?,
    onPick: (CaptionChoice?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(160)), modifier = modifier) {
        Box(
            Modifier
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            val choices = remember(tracks, language) {
                val own = tracks.map { CaptionChoice(it) }
                val translated = Captions.choose(tracks, language)?.takeIf { it.translateTo != null }
                own + listOfNotNull(translated)
            }
            AnimatedVisibility(visible, enter = scaleIn(tween(220), initialScale = 0.9f), exit = scaleOut(tween(160), targetScale = 0.95f)) {
                Row(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .horizontalScroll(rememberScrollState())
                        .selectableGroup()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CaptionPill(stringResource(R.string.tube_captions_off), selected == null) { onPick(null) }
                    choices.forEach { c ->
                        CaptionPill(captionLabel(c), selected?.track?.url == c.track.url && selected.translateTo == c.translateTo) { onPick(c) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, tween(180), label = "ccBg")
    val fg by animateColorAsState(if (selected) Color.Black else Color.White, tween(180), label = "ccFg")
    Box(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(bg)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}
