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

/** The speed over the video while it isn't 1×: a small white pill ("1,5×"); a tap opens the video's settings. */
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
