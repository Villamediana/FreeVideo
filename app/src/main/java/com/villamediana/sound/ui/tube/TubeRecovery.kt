package com.villamediana.sound.ui.tube

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.social.RelayException
import com.villamediana.sound.ui.sheets.ConfirmDialog
import com.villamediana.sound.ui.theme.Pastel
import com.villamediana.sound.ui.theme.SoundColors as sc
import kotlinx.coroutines.launch

/**
 * Ajustes › Conta (1.6): "Código de recuperação" (opens the code: copy it, or make another, asked once
 * because the old one stops opening anything) and "Usar outro código". Not on the profile page: it is
 * about this phone, not what the profile shows.
 */
@Composable
internal fun AccountRows() {
    val account = SoundApplication.graph.tube.account.account
    val code by account.recoveryCode.collectAsState()
    var showing by rememberSaveable { mutableStateOf(false) }
    var recovering by rememberSaveable { mutableStateOf(false) }

    if (code != null) {
        AccountRow(Icons.Rounded.Key, Pastel.Honey, stringResource(R.string.tube_profile_code), maskedCode(code!!)) { showing = true }
        Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(sc.Hairline))
    }
    AccountRow(Icons.Rounded.SwapHoriz, Pastel.Sage, stringResource(R.string.tube_profile_use_code), null) { recovering = true }

    if (showing) code?.let { CodeShownDialog(it, onDismiss = { showing = false }) }
    if (recovering) CodeDialog(onDismiss = { recovering = false })
}

@Composable
private fun AccountRow(icon: ImageVector, tint: Color, title: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = sc.Text)
            if (value != null) Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = sc.TextMuted, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = sc.TextFaint)
    }
}

/** The code as the row says it: only its first group, the rest dotted (it is a key to the whole profile). */
internal fun maskedCode(code: String): String =
    code.split('-').mapIndexed { i, part -> if (i == 0) part else "•".repeat(part.length) }.joinToString("-")

/** The code itself, big, with "Copiar" and "Gerar outro". */
@Composable
private fun CodeShownDialog(code: String, onDismiss: () -> Unit) {
    val hub = SoundApplication.graph.tube.account
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var renewing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sc.Surface2,
        shape = RoundedCornerShape(26.dp),
        icon = { IconTile(Icons.Rounded.Key, Pastel.Honey, size = 44.dp) },
        title = { Text(stringResource(R.string.tube_profile_code), color = sc.Text, textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    code,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp),
                    color = sc.Text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TubeButton(stringResource(R.string.tube_profile_copy), icon = Icons.Rounded.ContentCopy, onClick = {
                        copySecret(context, code)
                        Toast.makeText(context, R.string.tube_profile_copied, Toast.LENGTH_SHORT).show()
                    })
                    TubeButton(stringResource(R.string.tube_profile_new_code), icon = Icons.Rounded.Refresh, kind = TubeButtonKind.Tonal, onClick = { renewing = true })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tube_close), color = sc.Accent) } },
    )
    if (renewing) {
        ConfirmDialog(
            title = stringResource(R.string.tube_code_new_title),
            body = stringResource(R.string.tube_code_new_body),
            confirm = stringResource(R.string.tube_profile_new_code),
            onConfirm = {
                renewing = false
                scope.launch { hub.newRecoveryCode().onFailure { Toast.makeText(context, R.string.tube_profile_error, Toast.LENGTH_SHORT).show() } }
            },
            onDismiss = { renewing = false },
        )
    }
}

/** The code on the clipboard, marked sensitive (Android 13+ doesn't show it in the copy preview). */
internal fun copySecret(context: Context, text: String) {
    val clip = ClipData.newPlainText("FreeVideo", text)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/** Another phone's code: that profile (and its history, subscriptions, saved videos) comes here. */
@Composable
internal fun CodeDialog(onDismiss: () -> Unit) {
    val hub = SoundApplication.graph.tube.account
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sc.Surface2,
        shape = RoundedCornerShape(26.dp),
        title = { Text(stringResource(R.string.tube_profile_use_code), color = sc.Text) },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(40); error = null },
                    singleLine = true,
                    placeholder = { Text("7K2Q-M9XD-4TFA-H3RB", color = sc.TextFaint) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = sc.Text, fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = sc.Accent, unfocusedBorderColor = sc.Hairline, cursorColor = sc.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(stringResource(it), color = sc.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && code.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    hub.recover(code)
                        .onSuccess { onDismiss() }
                        .onFailure { e ->
                            when ((e as? RelayException)?.status) {
                                404 -> error = R.string.tube_profile_code_wrong
                                429 -> error = R.string.tube_code_too_many
                                else -> Toast.makeText(context, R.string.tube_profile_error, Toast.LENGTH_SHORT).show()
                            }
                        }
                    busy = false
                }
            }) { Text(stringResource(R.string.tube_profile_use), color = sc.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tube_cancel), color = sc.TextMuted) } },
    )
}
