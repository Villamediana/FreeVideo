package com.villamediana.sound.ui.tube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.villamediana.sound.R
import com.villamediana.sound.tube.data.TubeSettings
import com.villamediana.sound.tube.online.CaptionChoice
import com.villamediana.sound.tube.online.Captions
import com.villamediana.sound.ui.sheets.SoundSheet
import com.villamediana.sound.ui.theme.Pastel
import com.villamediana.sound.ui.theme.sc

/**
 * The video's settings, from the gear over the video: the speed, the captions (off, the automatic ones
 * in the app's language, or a language picked for this video) and the quality. Each is a row of the
 * app's chips — one tap and it's set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoSettingsSheet(
    speed: Float,
    onSpeed: (Float) -> Unit,
    captions: WatchViewModel.CaptionsUi,
    language: String,
    onCaptions: (CaptionChoice?, Boolean) -> Unit,
    quality: Int,
    live: Boolean,
    onQuality: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    SoundSheet(onDismissRequest = onClose, sheetState = sheetState) {
        SheetTop(stringResource(R.string.tube_video_settings), count = null, onClose = onClose)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 14.dp)) {
            if (!live) {
                Part(Icons.Rounded.Speed, Pastel.Coral, stringResource(R.string.tube_speed)) {
                    TubeSettings.SPEEDS.forEach { s -> TubeChip(speedLabel(s), selected = s == speed, onClick = { onSpeed(s) }) }
                }
            }
            if (captions.tracks.isNotEmpty()) {
                Part(Icons.Rounded.ClosedCaption, Pastel.Sky, stringResource(R.string.tube_captions)) {
                    TubeChip(stringResource(R.string.tube_captions_off), selected = !captions.showing, onClick = { onCaptions(null, true) })
                    val automatic = Captions.choose(captions.tracks, language)
                    TubeChip(
                        stringResource(R.string.tube_captions_default, languageName(automatic?.language ?: language)),
                        selected = captions.showing && !captions.forced,
                        onClick = { onCaptions(null, false) },
                    )
                    captions.tracks.forEach { t ->
                        val choice = CaptionChoice(t)
                        TubeChip(
                            captionLabel(choice),
                            selected = captions.showing && captions.forced && captions.choice == choice,
                            onClick = { onCaptions(choice, false) },
                        )
                    }
                }
            }
            if (!live) {
                Part(Icons.Rounded.HighQuality, Pastel.Sage, stringResource(R.string.tube_quality)) {
                    TubeSettings.QUALITIES.forEach { q -> TubeChip("${q}p", selected = q == quality, onClick = { onQuality(q) }) }
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** One setting: its tile and name, then its choices as chips that wrap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Part(icon: ImageVector, tint: Color, title: String, chips: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = sc.Text)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}
