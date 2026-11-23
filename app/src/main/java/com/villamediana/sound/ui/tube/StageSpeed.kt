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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.villamediana.sound.R
import com.villamediana.sound.tube.data.TubeSettings
import com.villamediana.sound.ui.components.pressScale
import java.text.DecimalFormat

/** "1×", "1,5×" (the phone's own decimal mark). */
internal fun speedLabel(speed: Float): String = DecimalFormat("0.##").format(speed.toDouble()) + "×"

/** The speed over the video: a small glass pill ("1×", "1,5×"); the chosen speed lights it in white. */
@Composable
internal fun StageSpeedPill(speed: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(R.string.tube_speed)
    val changed = speed != 1f
    Box(
        modifier
            .height(30.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(if (changed) Color.White else Color.White.copy(alpha = 0.16f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                contentDescription = label + " " + speedLabel(speed)
                role = Role.Button
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(speedLabel(speed), style = MaterialTheme.typography.labelLarge, color = if (changed) Color.Black else Color.White)
    }
}

/**
 * The speeds, in a row of pills over the dimmed video. A tap picks one (it stays for the next videos)
 * and closes; a tap anywhere else closes too.
 */
@Composable
internal fun StageSpeedPicker(visible: Boolean, speed: Float, onPick: (Float) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(160)), modifier = modifier) {
        Box(
            Modifier
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(visible, enter = scaleIn(tween(220), initialScale = 0.9f), exit = scaleOut(tween(160), targetScale = 0.95f)) {
                Row(
                    Modifier
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .horizontalScroll(rememberScrollState())
                        .selectableGroup()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TubeSettings.SPEEDS.forEach { s -> SpeedChoice(s, selected = s == speed) { onPick(s) } }
                }
            }
        }
    }
}

@Composable
private fun SpeedChoice(speed: Float, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, tween(180), label = "speedBg")
    val fg by animateColorAsState(if (selected) Color.Black else Color.White, tween(180), label = "speedFg")
    Box(
        Modifier
            .height(40.dp)
            .widthIn(min = 52.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(bg)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(speedLabel(speed), style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
