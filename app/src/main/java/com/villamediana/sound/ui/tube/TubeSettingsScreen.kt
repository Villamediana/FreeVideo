package com.villamediana.sound.ui.tube

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.villamediana.sound.BuildConfig
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.data.ThemeMode
import com.villamediana.sound.tube.data.TubePrefs
import com.villamediana.sound.tube.data.TubeSettings
import com.villamediana.sound.ui.components.FilterPill
import com.villamediana.sound.ui.sheets.ConfirmDialog
import com.villamediana.sound.ui.theme.Dimens
import com.villamediana.sound.ui.theme.SoundAccents
import com.villamediana.sound.ui.theme.sc
import kotlinx.coroutines.launch

/** Ajustes: look, playback, privacy and how the feed works. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TubeSettingsScreen() {
    val nav = LocalTubeNav.current
    val graph = SoundApplication.graph
    val tube = graph.tube
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val look by graph.settings.settings.collectAsState(initial = null)
    val prefs by tube.settings.prefs.collectAsState(initial = TubePrefs())
    var confirmClear by remember { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun toast(id: Int) = Toast.makeText(context, context.getString(id), Toast.LENGTH_SHORT).show()

    Column(Modifier.fillMaxSize()) {
        PageTopBar(stringResource(R.string.tube_settings), onBack = nav::back)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Section(stringResource(R.string.tube_set_section_look)) }
            item {
                Setting(stringResource(R.string.tube_set_theme)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ThemeMode.LIGHT to R.string.tube_theme_light, ThemeMode.DARK to R.string.tube_theme_dark, ThemeMode.SYSTEM to R.string.tube_theme_system).forEach { (mode, label) ->
                            FilterPill(stringResource(label), selected = look?.theme == mode, onClick = { scope.launch { graph.settings.setTheme(mode) } })
                        }
                    }
                }
            }
            item {
                Setting(stringResource(R.string.tube_set_accent)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SoundAccents.all.forEach { a ->
                            val color = if (sc.isLight) a.light else a.dark
                            val selected = look?.accent == a.id
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(if (selected) Modifier.border(3.dp, sc.Text, CircleShape) else Modifier)
                                    .clickable { scope.launch { graph.settings.setAccent(a.id) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Rounded.Check, null, tint = sc.Ink, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            item { Section(stringResource(R.string.tube_set_section_playback)) }
            item {
                Setting(stringResource(R.string.tube_set_quality), stringResource(R.string.tube_set_quality_body)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TubeSettings.QUALITIES.forEach { q ->
                            FilterPill("${q}p", selected = prefs.maxHeight == q, onClick = { scope.launch { tube.settings.setMaxHeight(q) } })
                        }
                    }
                }
            }
            item {
                Toggle(stringResource(R.string.tube_set_autoplay), stringResource(R.string.tube_set_autoplay_body), prefs.autoplay) { on ->
                    scope.launch { tube.settings.setAutoplay(on) }
                }
            }
            item {
                Toggle(stringResource(R.string.tube_set_background), stringResource(R.string.tube_set_background_body), prefs.keepPlayingInBackground) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    scope.launch { tube.settings.setKeepPlayingInBackground(on) }
                }
            }

            item {
                Toggle(stringResource(R.string.tube_set_pip), stringResource(R.string.tube_set_pip_body), prefs.pictureInPicture) { on ->
                    scope.launch { tube.settings.setPictureInPicture(on) }
                }
            }
            item {
                var cached by remember { mutableStateOf(com.villamediana.sound.tube.player.VideoCache.size(context)) }
                Action(stringResource(R.string.tube_set_cache), stringResource(R.string.tube_set_cache_body, com.villamediana.sound.ui.components.formatBytes(cached))) {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        com.villamediana.sound.tube.player.VideoCache.clear(context)
                        cached = com.villamediana.sound.tube.player.VideoCache.size(context)
                    }
                }
            }

            item { Section(stringResource(R.string.tube_set_section_privacy)) }
            item {
                Toggle(stringResource(R.string.tube_set_history_paused), stringResource(R.string.tube_set_history_paused_body), prefs.historyPaused) { on ->
                    scope.launch { tube.settings.setHistoryPaused(on) }
                }
            }
            item {
                Action(stringResource(R.string.tube_set_reset), stringResource(R.string.tube_set_reset_body)) {
                    scope.launch {
                        tube.library.resetRecommendations()
                        tube.feedCache.clear()
                        toast(R.string.tube_set_reset_done)
                    }
                }
            }
            item {
                Action(stringResource(R.string.tube_set_clear_searches), null) {
                    scope.launch {
                        tube.library.clearSearches()
                        toast(R.string.tube_set_clear_searches_done)
                    }
                }
            }
            item {
                Action(stringResource(R.string.tube_clear_history), stringResource(R.string.tube_clear_history_body), danger = true) { confirmClear = true }
            }

            item { Section(stringResource(R.string.tube_set_how)) }
            item { Paragraph(stringResource(R.string.tube_set_how_body)) }
            item { Section(stringResource(R.string.tube_set_about)) }
            item { com.villamediana.sound.ui.update.UpdateRow() }
            item { Paragraph(stringResource(R.string.tube_set_about_body, BuildConfig.VERSION_NAME)) }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.tube_clear_history_title),
            body = stringResource(R.string.tube_clear_history_body),
            confirm = stringResource(R.string.tube_clear),
            onConfirm = {
                confirmClear = false
                scope.launch { tube.library.clearHistory() }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = sc.TextFaint,
        modifier = Modifier.padding(horizontal = Dimens.Gutter).padding(top = 26.dp, bottom = 8.dp),
    )
}

@Composable
private fun Setting(title: String, body: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = sc.Text)
        if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Toggle(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = Dimens.Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = sc.Text)
            Text(body, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = sc.Accent, checkedThumbColor = sc.OnAccent, uncheckedTrackColor = sc.Surface3, uncheckedBorderColor = sc.Hairline),
        )
    }
}

@Composable
private fun Action(title: String, body: String?, danger: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Dimens.Gutter, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (danger) sc.Danger else sc.Text)
        if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = sc.TextMuted)
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = sc.TextMuted,
        modifier = Modifier.padding(horizontal = Dimens.Gutter).clip(RoundedCornerShape(16.dp)).background(sc.Surface1).padding(14.dp).fillMaxWidth(),
    )
}
