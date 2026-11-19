package com.villamediana.sound.ui.tube

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.villamediana.sound.R
import com.villamediana.sound.ui.components.pressScale
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.Motion
import com.villamediana.sound.ui.theme.sc

/*
 * FreeVideo's own kit (its look apart from FreeSong's): the mark, headers, buttons, tabs, switches
 * and the small colored tiles that stand in for words.
 */

/** The app's mark: the ember tile with the winged play button. */
@Composable
fun TubeMark(modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Image(
        painterResource(R.drawable.freevideo_mark),
        contentDescription = null,
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.24f)),
    )
}

/** Mark + "FreeVideo", in the page's ink: the home header. */
@Composable
fun TubeWordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TubeMark(size = 30.dp)
        Spacer(Modifier.width(9.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em),
            color = sc.Text,
        )
    }
}

/** A section's title, with an optional "see all" on the right. */
@Composable
fun TubeSectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = Dimens.Gutter, end = 10.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = sc.Text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Row(
                Modifier.clip(CircleShape).clickable(onClick = onAction).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = sc.TextMuted)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = sc.TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Nothing (yet) to show: a soft ember disc with the icon, a title, a line and maybe a button. */
@Composable
fun TubeEmpty(icon: ImageVector, title: String, body: String?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(sc.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = sc.Accent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = sc.Text, textAlign = TextAlign.Center)
        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
        }
        if (action != null) {
            Spacer(Modifier.height(22.dp))
            action()
        }
    }
}

enum class TubeButtonKind { Filled, Inverse, Tonal, Ghost }

/** A pill button: ember (Filled), the page's ink turned over (Inverse), quiet grey (Tonal) or bare (Ghost). */
@Composable
fun TubeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: TubeButtonKind = TubeButtonKind.Filled,
    enabled: Boolean = true,
    big: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val (bg, fg) = when (kind) {
        TubeButtonKind.Filled -> sc.Accent to sc.OnAccent
        TubeButtonKind.Inverse -> sc.Text to sc.Ink
        TubeButtonKind.Tonal -> sc.Surface1 to sc.Text
        TubeButtonKind.Ghost -> Color.Transparent to sc.Text
    }
    Row(
        modifier
            .height(if (big) 50.dp else 42.dp)
            .pressScale(interaction, 0.96f)
            .clip(CircleShape)
            .background(if (enabled) bg else sc.Surface1)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (big) 22.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val color = if (enabled) fg else sc.TextFaint
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(if (big) 22.dp else 18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = if (big) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

/** A round icon-only button (top bars, actions): the icon says it, [description] tells TalkBack. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Color.Transparent,
    tint: Color = sc.Text,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(interaction, 0.88f)
            .clip(CircleShape)
            .background(container)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** A small rounded tile with an icon in its color (settings rows, library rows). */
@Composable
fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = if (sc.isLight) 0.13f else 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** The app's switch: ember track when on, white thumb. */
@Composable
fun TubeSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = sc.Accent,
            checkedThumbColor = if (sc.isLight) Color.White else sc.Ink,
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = sc.Surface3,
            uncheckedThumbColor = if (sc.isLight) Color.White else sc.TextMuted,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/**
 * Text tabs over a hairline, the chosen one in the page's ink with an ember bar under it that
 * slides from tab to tab (a channel's Vídeos / Shorts / Ao vivo / Playlists).
 */
@Composable
fun TubeTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val bounds = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() }
    val target = bounds[selected]
    val x by animateDpAsState(target?.first ?: 0.dp, Motion.bouncy(), label = "tabX")
    val w by animateDpAsState(target?.second ?: 0.dp, Motion.bouncy(), label = "tabW")
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).selectableGroup()) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val color by animateColorAsState(if (on) sc.Text else sc.TextMuted, tween(200), label = "tab")
                Box(
                    Modifier
                        .onPlaced { c ->
                            with(density) { bounds[i] = c.positionInParent().x.toDp() to c.size.width.toDp() }
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) })
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold), color = color, maxLines = 1)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (target != null) {
                Box(
                    Modifier
                        .offset(x = 8.dp + x + 14.dp)
                        .width((w - 28.dp).coerceAtLeast(12.dp))
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(sc.Accent),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sc.Hairline))
    }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.positionInParent() =
    parentLayoutCoordinates?.localPositionOf(this, androidx.compose.ui.geometry.Offset.Zero) ?: androidx.compose.ui.geometry.Offset.Zero
