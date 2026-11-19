package com.villamediana.sound.ui.tube

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.ThumbsUpDown
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.villamediana.sound.BuildConfig
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.data.ThemeMode
import com.villamediana.sound.tube.data.TubePrefs
import com.villamediana.sound.tube.data.TubeSettings
import com.villamediana.sound.tube.player.VideoCache
import com.villamediana.sound.ui.components.formatBytes
import com.villamediana.sound.ui.components.pressScale
import com.villamediana.sound.ui.sheets.ConfirmDialog
import com.villamediana.sound.ui.sheets.SoundSheet
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.Pastel
import com.villamediana.sound.ui.theme.SoundAccents
import com.villamediana.sound.ui.theme.SoundPalettes
import com.villamediana.sound.ui.theme.sc
import com.villamediana.sound.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Colors of the settings' icon tiles: each group its own pastel, so the page reads at a glance. */
private object Tiles {
    val Look: Color get() = Pastel.Lilac
    val Play: Color get() = Pastel.Coral
    val Store: Color get() = Pastel.Sky
    val Private: Color get() = Pastel.Sage
    val Danger: Color get() = Pastel.Clay
    val About: Color get() = Pastel.Honey
}

private enum class Confirm { RESET, CLEAR_HISTORY, CLEAR_CACHE }

/** Ajustes: grouped cards (look, playback, storage, privacy, about), few words, the switches at hand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TubeSettingsScreen() {
    val nav = LocalTubeNav.current
    val graph = SoundApplication.graph
    val tube = graph.tube
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val look by graph.settings.settings.collectAsState(initial = null)
    val prefs by tube.settings.prefs.collectAsState(initial = TubePrefs())
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var cached by remember { mutableLongStateOf(-1L) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun toast(id: Int) = Toast.makeText(context, context.getString(id), Toast.LENGTH_SHORT).show()
    LaunchedEffect(Unit) { cached = withContext(Dispatchers.IO) { VideoCache.size(context) } }

    Column(Modifier.fillMaxSize()) {
        PageTopBar(stringResource(R.string.tube_settings), onBack = nav::back)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item(key = "look") {
                Group(stringResource(R.string.tube_set_section_look)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(ThemeMode.LIGHT to R.string.tube_theme_light, ThemeMode.DARK to R.string.tube_theme_dark, ThemeMode.SYSTEM to R.string.tube_theme_system).forEach { (mode, label) ->
                                ThemeTile(mode, stringResource(label), selected = look?.theme == mode, onClick = { scope.launch { graph.settings.setTheme(mode) } }, modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        AccentRow(selected = look?.accent, onPick = { id -> scope.launch { graph.settings.setAccent(id) } })
                    }
                }
            }

            item(key = "play") {
                Group(stringResource(R.string.tube_set_section_playback)) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Rounded.HighQuality, Tiles.Play)
                            Spacer(Modifier.width(14.dp))
                            Text(stringResource(R.string.tube_set_quality), style = MaterialTheme.typography.titleMedium, color = sc.Text, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        Segmented(
                            options = TubeSettings.QUALITIES.map { "${it}p" },
                            selected = TubeSettings.QUALITIES.indexOf(prefs.maxHeight).coerceAtLeast(0),
                            onSelect = { i -> scope.launch { tube.settings.setMaxHeight(TubeSettings.QUALITIES[i]) } },
                        )
                    }
                    Divider()
                    ToggleRow(Icons.Rounded.Autorenew, Tiles.Play, stringResource(R.string.tube_set_autoplay), null, prefs.autoplay) { on ->
                        scope.launch { tube.settings.setAutoplay(on) }
                    }
                    Divider()
                    ToggleRow(Icons.Rounded.Headphones, Tiles.Play, stringResource(R.string.tube_set_background), null, prefs.keepPlayingInBackground) { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        scope.launch { tube.settings.setKeepPlayingInBackground(on) }
                    }
                    Divider()
                    ToggleRow(Icons.Rounded.PictureInPictureAlt, Tiles.Play, stringResource(R.string.tube_set_pip), null, prefs.pictureInPicture) { on ->
                        scope.launch { tube.settings.setPictureInPicture(on) }
                    }
                }
            }

            item(key = "store") {
                Group(stringResource(R.string.tube_set_section_storage)) {
                    StorageRow(cached, onClear = { confirm = Confirm.CLEAR_CACHE })
                }
            }

            item(key = "privacy") {
                Group(stringResource(R.string.tube_set_section_privacy)) {
                    ToggleRow(Icons.Rounded.HistoryToggleOff, Tiles.Private, stringResource(R.string.tube_set_history_paused), null, prefs.historyPaused) { on ->
                        scope.launch { tube.settings.setHistoryPaused(on) }
                    }
                    Divider()
                    ActionRow(Icons.Rounded.RestartAlt, Tiles.Private, stringResource(R.string.tube_set_reset), onClick = { confirm = Confirm.RESET })
                    Divider()
                    ActionRow(Icons.Rounded.SearchOff, Tiles.Private, stringResource(R.string.tube_set_clear_searches), chevron = false, onClick = {
                        scope.launch {
                            tube.library.clearSearches()
                            toast(R.string.tube_set_clear_searches_done)
                        }
                    })
                    Divider()
                    ActionRow(Icons.Rounded.DeleteSweep, Tiles.Danger, stringResource(R.string.tube_clear_history), danger = true, chevron = false, onClick = { confirm = Confirm.CLEAR_HISTORY })
                }
            }

            item(key = "about") {
                Group(stringResource(R.string.tube_set_about)) {
                    UpdateSettingRow()
                    Divider()
                    InfoRow(Icons.Rounded.PhoneAndroid, Tiles.About, stringResource(R.string.tube_set_version), BuildConfig.VERSION_NAME)
                }
                Spacer(Modifier.height(18.dp))
            }
            item(key = "bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }

    when (confirm) {
        Confirm.RESET -> ConfirmDialog(
            title = stringResource(R.string.tube_set_reset_title),
            body = stringResource(R.string.tube_set_reset_body),
            confirm = stringResource(R.string.tube_set_reset_confirm),
            onConfirm = {
                confirm = null
                scope.launch {
                    tube.library.resetRecommendations()
                    tube.feedCache.clear()
                    toast(R.string.tube_set_reset_done)
                }
            },
            onDismiss = { confirm = null },
        )
        Confirm.CLEAR_HISTORY -> ConfirmDialog(
            title = stringResource(R.string.tube_clear_history_title),
            body = stringResource(R.string.tube_clear_history_body),
            confirm = stringResource(R.string.tube_clear),
            onConfirm = {
                confirm = null
                scope.launch { tube.library.clearHistory() }
            },
            onDismiss = { confirm = null },
        )
        Confirm.CLEAR_CACHE -> ConfirmDialog(
            title = stringResource(R.string.tube_set_cache_title),
            body = stringResource(R.string.tube_set_cache_body),
            confirm = stringResource(R.string.tube_clear),
            onConfirm = {
                confirm = null
                scope.launch {
                    cached = withContext(Dispatchers.IO) {
                        VideoCache.clear(context)
                        VideoCache.size(context)
                    }
                }
            },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

// ------------------------------------------------------------------ pieces

/** A group: its name, then its rows on one rounded card. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = sc.TextMuted,
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp),
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(sc.Surface1), content = content)
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(sc.Hairline))
}

@Composable
private fun RowShell(onClick: (() -> Unit)?, role: Role? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowTexts(title: String, subtitle: String?, modifier: Modifier, titleColor: Color = sc.Text) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, tint: Color, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowShell(onClick = { onChange(!checked) }, role = Role.Switch) {
        IconTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        RowTexts(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        TubeSwitch(checked, onChange = null)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, tint: Color, title: String, subtitle: String? = null, danger: Boolean = false, chevron: Boolean = true, onClick: () -> Unit) {
    RowShell(onClick = onClick, role = Role.Button) {
        IconTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        RowTexts(title, subtitle, Modifier.weight(1f), titleColor = if (danger) sc.Danger else sc.Text)
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = sc.TextFaint)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, tint: Color, title: String, value: String) {
    RowShell(onClick = null) {
        IconTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = sc.Text, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted)
    }
}

/** The disk cache: how much it holds out of its room, as a bar, and a button to empty it. */
@Composable
private fun StorageRow(bytes: Long, onClear: () -> Unit) {
    val used = bytes.coerceAtLeast(0)
    val fraction by animateFloatAsState((used.toFloat() / VideoCache.BYTES).coerceIn(0f, 1f), tween(600), label = "cache")
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.SdStorage, Tiles.Store)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tube_set_cache), style = MaterialTheme.typography.titleMedium, color = sc.Text)
                Text(
                    if (bytes < 0) "…" else stringResource(R.string.tube_set_cache_used, formatBytes(used), formatBytes(VideoCache.BYTES)),
                    style = MaterialTheme.typography.bodySmall,
                    color = sc.TextMuted,
                )
            }
            if (used > 0) TubeButton(stringResource(R.string.tube_clear), onClick = onClear, kind = TubeButtonKind.Inverse)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(sc.Surface3)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(CircleShape).background(Tiles.Store))
        }
    }
}

/** Light / Dark / System as little drawings of the app, the chosen one ringed in the ember. */
@Composable
private fun ThemeTile(mode: ThemeMode, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val ring by animateColorAsState(if (selected) sc.Accent else sc.Hairline, tween(220), label = "themeRing")
    val width by animateDpAsState(if (selected) 2.5.dp else 1.dp, tween(220), label = "themeRingW")
    Column(
        modifier
            .pressScale(interaction, 0.96f)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .clip(RoundedCornerShape(16.dp))
                .border(width, ring, RoundedCornerShape(16.dp)),
        ) {
            when (mode) {
                ThemeMode.LIGHT -> MiniApp(light = true, Modifier.fillMaxSize())
                ThemeMode.DARK -> MiniApp(light = false, Modifier.fillMaxSize())
                ThemeMode.SYSTEM -> Box(Modifier.fillMaxSize()) {
                    MiniApp(light = true, Modifier.fillMaxSize())
                    MiniApp(light = false, Modifier.fillMaxSize().clip(RightHalf))
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(sc.Accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = sc.OnAccent, modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) sc.Text else sc.TextMuted)
    }
}

/** The right half of a box (the "Sistema" tile: light on the left, dark on the right). */
private val RightHalf = androidx.compose.foundation.shape.GenericShape { size, _ ->
    addRect(androidx.compose.ui.geometry.Rect(size.width / 2f, 0f, size.width, size.height))
}

/** A tiny sketch of a feed: a picture block and two lines, in the light or dark palette. */
@Composable
private fun MiniApp(light: Boolean, modifier: Modifier) {
    val p = if (light) SoundPalettes.Light else SoundPalettes.Dark
    val accent = sc.Accent
    Column(modifier.background(p.ink).padding(9.dp)) {
        Box(Modifier.fillMaxWidth(0.42f).height(5.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(p.surface3))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.9f).height(5.dp).clip(CircleShape).background(p.textMuted.copy(alpha = 0.6f)))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(0.55f).height(5.dp).clip(CircleShape).background(p.textFaint.copy(alpha = 0.5f)))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(p.surface3))
    }
}

/** The highlight colors as dots; the chosen one ringed, with a tick. */
@Composable
private fun AccentRow(selected: String?, onPick: (String) -> Unit) {
    val chosen = SoundAccents.byId(selected).id
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.selectableGroup()) {
        items(SoundAccents.all, key = { it.id }) { a ->
            val color = if (sc.isLight) a.light else a.dark
            val on = chosen == a.id
            val ring by animateDpAsState(if (on) 3.dp else 0.dp, tween(200), label = "accentRing")
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(42.dp)
                    .pressScale(interaction, 0.88f)
                    .clip(CircleShape)
                    .border(ring, sc.Text, CircleShape)
                    .padding(if (on) 5.dp else 0.dp)
                    .clip(CircleShape)
                    .background(color)
                    .selectable(selected = on, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = { onPick(a.id) })
                    .semantics { contentDescription = a.id },
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(Icons.Rounded.Check, null, tint = if (sc.isLight) Color.White else sc.Ink, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A row of choices sharing one track; the chosen one is a raised pill that slides there. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(14.dp)).background(sc.Surface3).padding(3.dp),
    ) {
        val slot = maxWidth / options.size
        val x by animateDpAsState(slot * selected, com.villamediana.sound.ui.theme.Motion.bouncy(), label = "segX")
        Box(Modifier.offset(x = x).width(slot).fillMaxHeight().clip(RoundedCornerShape(11.dp)).background(if (sc.isLight) Color.White else sc.Surface1))
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val color by animateColorAsState(if (on) sc.Text else sc.TextMuted, tween(200), label = "seg")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(11.dp)).selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(i) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold), color = color)
                }
            }
        }
    }
}

/** New versions from GitHub: check, see the new one, its download and install, as one row. */
@Composable
private fun UpdateSettingRow() {
    val updates = SoundApplication.graph.updates
    if (!updates.enabled) {
        InfoRow(Icons.Rounded.SystemUpdate, Tiles.About, stringResource(R.string.settings_update_check), "—")
        return
    }
    val state by updates.state.collectAsStateWithLifecycle()
    val downloading = state as? UpdateState.Downloading
    val release = state.release
    val title = when (state) {
        is UpdateState.Downloading, is UpdateState.Installing -> stringResource(R.string.settings_update_going, release!!.version)
        else -> if (release != null) stringResource(R.string.settings_update_available, release.version) else stringResource(R.string.settings_update_check)
    }
    val subtitle: String? = when (state) {
        UpdateState.Checking -> stringResource(R.string.settings_update_checking)
        UpdateState.UpToDate -> stringResource(R.string.settings_update_latest)
        UpdateState.CheckFailed -> stringResource(R.string.settings_update_failed)
        is UpdateState.Downloading -> java.text.NumberFormat.getPercentInstance().format((downloading?.fraction ?: 0f).toDouble())
        is UpdateState.Installing -> stringResource(R.string.update_installing)
        else -> null
    }
    RowShell(onClick = { if (state != UpdateState.Checking) { if (release != null) updates.openSheet() else updates.checkNow() } }, role = Role.Button) {
        IconTile(Icons.Rounded.SystemUpdate, Tiles.About)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (release != null) sc.Accent else sc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (state == UpdateState.UpToDate) sc.Success else sc.TextMuted)
        }
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                state == UpdateState.Checking || state is UpdateState.Installing -> CircularProgressIndicator(Modifier.size(18.dp), color = sc.Accent, strokeWidth = 2.dp)
                downloading != null -> CircularProgressIndicator(progress = { downloading.fraction }, modifier = Modifier.size(18.dp), color = sc.Accent, trackColor = sc.Surface3, strokeWidth = 2.dp)
                release != null -> Box(Modifier.size(10.dp).clip(CircleShape).background(sc.Accent))
                else -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = sc.TextFaint)
            }
        }
    }
}
