package com.villamediana.sound.ui.tube

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.villamediana.sound.R
import com.villamediana.sound.SoundApplication
import com.villamediana.sound.social.AccountStatus
import com.villamediana.sound.social.Computer
import com.villamediana.sound.social.ComputerLink
import com.villamediana.sound.social.RelayException
import com.villamediana.sound.ui.theme.LocalBottomInset
import com.villamediana.sound.ui.theme.Pastel
import com.villamediana.sound.ui.theme.SoundColors as sc
import kotlinx.coroutines.launch

/**
 * The profile: photo, name and @ (the @ is the identifier and stays), the recovery code (it brings the
 * profile and everything in its vault to another phone), another phone's code, the computers linked
 * (FreeVideo on the web) and deleting the profile. Short labels, no paragraphs.
 */
@Composable
fun TubeProfileScreen() {
    val nav = LocalTubeNav.current
    val hub = SoundApplication.graph.tube.account
    val account = hub.account
    val me by account.me.collectAsState()
    val code by account.recoveryCode.collectAsState()
    val status by account.status.collectAsState()
    val computers by hub.computers.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    var framing by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var photoBusy by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var recovering by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf<ComputerLink.Code?>(null) }
    LaunchedEffect(Unit) {
        account.refresh()
        hub.refreshComputers()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val photo = account.loadPhoto(uri)
            if (photo == null) toast(context.getString(R.string.tube_profile_error)) else framing = photo
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageTopBar(stringResource(R.string.tube_profile), onBack = nav::back)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Avatar(me?.avatar?.let { account.api.absolute(it) }, me?.name, size = 104, onClick = if (me != null) ({ gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) else null)
                        if (me != null) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(sc.Accent), contentAlignment = Alignment.Center) {
                                if (photoBusy) CircularProgressIndicator(Modifier.size(16.dp), color = sc.OnAccent, strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.PhotoCamera, null, tint = sc.OnAccent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(me?.name ?: "…", style = MaterialTheme.typography.headlineSmall, color = sc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (me != null) {
                            Spacer(Modifier.width(4.dp))
                            CircleIconButton(Icons.Rounded.Edit, stringResource(R.string.tube_profile_name), { naming = true }, iconSize = 18.dp)
                        }
                    }
                    me?.let { Text("@${it.username}", style = MaterialTheme.typography.bodyMedium, color = sc.TextMuted) }
                    if (me == null && (status == AccountStatus.NONE || status == AccountStatus.OPTED_OUT)) {
                        Spacer(Modifier.height(16.dp))
                        TubeButton(stringResource(R.string.tube_profile_create), onClick = {
                            scope.launch { if (!hub.createAgain()) toast(context.getString(R.string.tube_profile_error)) }
                        })
                    }
                }
            }
            if (me != null) {
                item(key = "code") {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconTile(Icons.Rounded.Key, Pastel.Honey, size = 30.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.tube_profile_code), style = MaterialTheme.typography.titleMedium, color = sc.Text)
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                code ?: "····-····-····-····",
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp),
                                color = sc.Text,
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (code != null) {
                                    TubeButton(stringResource(R.string.tube_profile_copy), icon = Icons.Rounded.ContentCopy, onClick = {
                                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("FreeVideo", code))
                                        toast(context.getString(R.string.tube_profile_copied))
                                    })
                                }
                                TubeButton(stringResource(R.string.tube_profile_new_code), icon = Icons.Rounded.Refresh, kind = TubeButtonKind.Tonal, onClick = {
                                    scope.launch { hub.newRecoveryCode().onFailure { toast(context.getString(R.string.tube_profile_error)) } }
                                })
                            }
                        }
                    }
                }
                item(key = "computers") {
                    Card {
                        Row(Modifier.fillMaxWidth().clickable { scanComputerCode(context) { linking = it } }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Rounded.Computer, Pastel.Sky, size = 38.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.tube_computer), style = MaterialTheme.typography.titleMedium, color = sc.Text)
                                Text("freevideo.simplyonegoal.com/web", style = MaterialTheme.typography.bodySmall, color = sc.TextMuted, maxLines = 1)
                            }
                            Icon(Icons.Rounded.QrCodeScanner, stringResource(R.string.tube_computer_link), tint = sc.Accent)
                        }
                        for (c in computers) ComputerRow(c, onUnlink = {
                            scope.launch {
                                hub.unlinkComputer(c.id)
                                    .onSuccess { toast(context.getString(R.string.tube_computer_unlinked)) }
                                    .onFailure { toast(context.getString(R.string.tube_profile_error)) }
                            }
                        })
                    }
                }
                item(key = "more") {
                    Card {
                        ProfileRow(Icons.Rounded.SwapHoriz, Pastel.Sage, stringResource(R.string.tube_profile_use_code)) { recovering = true }
                        Box(Modifier.fillMaxWidth().padding(start = 68.dp).height(1.dp).background(sc.Hairline))
                        ProfileRow(Icons.Rounded.DeleteOutline, Pastel.Clay, stringResource(R.string.tube_profile_delete), danger = true) { deleting = true }
                    }
                }
            } else if (status == AccountStatus.READY || status == AccountStatus.LOADING) {
                item(key = "wait") { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = sc.Accent) } }
            } else {
                item(key = "use") { Card { ProfileRow(Icons.Rounded.SwapHoriz, Pastel.Sage, stringResource(R.string.tube_profile_use_code)) { recovering = true } } }
            }
            item(key = "end") { Spacer(Modifier.height(LocalBottomInset.current + 24.dp)) }
        }
    }

    framing?.let { photo ->
        com.villamediana.sound.ui.social.PhotoCropDialog(
            photo = remember(photo) { photo.asImageBitmap() },
            onCancel = { framing = null },
            onApply = { cut ->
                framing = null
                scope.launch {
                    photoBusy = true
                    account.setPhoto(photo, android.graphics.Rect(cut.left.toInt(), cut.top.toInt(), cut.right.toInt(), cut.bottom.toInt()))
                        .onFailure { toast(context.getString(R.string.tube_profile_error)) }
                    photoBusy = false
                }
            },
        )
    }
    if (naming) me?.let { NameDialog(it.name, onDismiss = { naming = false }) }
    if (recovering) CodeDialog(onDismiss = { recovering = false })
    linking?.let { LinkComputerDialog(it, onDismiss = { linking = null }) }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = sc.Surface2,
            shape = RoundedCornerShape(26.dp),
            title = { Text(stringResource(R.string.tube_profile_delete_q), color = sc.Text) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch { hub.deleteProfile().onFailure { toast(context.getString(R.string.tube_profile_error)) } }
                }) { Text(stringResource(R.string.tube_profile_delete), color = sc.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.tube_cancel), color = sc.TextMuted) } },
        )
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(22.dp)).background(sc.Surface1), content = content)
}

@Composable
private fun ProfileRow(icon: ImageVector, tint: Color, title: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint, size = 38.dp)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (danger) sc.Danger else sc.Text, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = sc.TextFaint)
    }
}

@Composable
private fun ComputerRow(c: Computer, onUnlink: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 68.dp, end = 8.dp, top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.bodyLarge, color = sc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.shared) Text(stringResource(R.string.tube_computer_shared), style = MaterialTheme.typography.bodySmall, color = sc.TextFaint)
        }
        TextButton(onClick = onUnlink) { Text(stringResource(R.string.tube_computer_unlink), color = sc.Danger, style = MaterialTheme.typography.labelLarge) }
    }
}

/** The person's photo (or the first letter of the name). */
@Composable
internal fun Avatar(url: String?, name: String?, size: Int, onClick: (() -> Unit)? = null) {
    val m = Modifier.size(size.dp).clip(CircleShape).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = m.background(sc.Surface2))
    } else {
        Box(m.background(Pastel.Lilac.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            val letter = name?.firstOrNull()?.uppercase()
            if (letter != null) Text(letter, style = MaterialTheme.typography.headlineMedium.copy(fontSize = (size * 0.42f).sp), color = sc.Text)
            else Icon(Icons.Rounded.Person, null, tint = sc.TextMuted, modifier = Modifier.size((size * 0.5f).dp))
        }
    }
}

@Composable
private fun NameDialog(current: String, onDismiss: () -> Unit) {
    val hub = SoundApplication.graph.tube.account
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf(current) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sc.Surface2,
        shape = RoundedCornerShape(26.dp),
        title = { Text(stringResource(R.string.tube_profile_name), color = sc.Text) },
        text = { Field(name, { name = it }) },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && name.trim() != current, onClick = {
                busy = true
                scope.launch {
                    hub.account.rename(name.trim(), null)
                        .onSuccess { onDismiss() }
                        .onFailure { Toast.makeText(context, R.string.tube_profile_error, Toast.LENGTH_SHORT).show() }
                    busy = false
                }
            }) { Text(stringResource(R.string.save), color = sc.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tube_cancel), color = sc.TextMuted) } },
    )
}

/** Another phone's code: that profile (and its history, subscriptions, saved videos) comes here. */
@Composable
private fun CodeDialog(onDismiss: () -> Unit) {
    val hub = SoundApplication.graph.tube.account
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sc.Surface2,
        shape = RoundedCornerShape(26.dp),
        title = { Text(stringResource(R.string.tube_profile_use_code), color = sc.Text) },
        text = {
            Column {
                Field(code, { code = it.uppercase(); error = false }, placeholder = "7K2Q-M9XD-4TFA-H3RB", mono = true)
                if (error) Text(stringResource(R.string.tube_profile_code_wrong), color = sc.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && code.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    hub.recover(code)
                        .onSuccess { onDismiss() }
                        .onFailure { e ->
                            if ((e as? RelayException)?.status == 404) error = true
                            else Toast.makeText(context, R.string.tube_profile_error, Toast.LENGTH_SHORT).show()
                        }
                    busy = false
                }
            }) { Text(stringResource(R.string.tube_profile_use), color = sc.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tube_cancel), color = sc.TextMuted) } },
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String? = null, mono: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = placeholder?.let { { Text(it, color = sc.TextFaint) } },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = sc.Text, fontFamily = if (mono) FontFamily.Monospace else null),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = sc.Accent, unfocusedBorderColor = sc.Hairline, cursorColor = sc.Accent),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Google's code scanner (no camera permission here); a FreeVideo computer code goes to [onCode]. */
internal fun scanComputerCode(context: android.content.Context, onCode: (ComputerLink.Code) -> Unit) {
    val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
        .build()
    runCatching {
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val code = ComputerLink.code(barcode.rawValue)
                if (code != null) onCode(code) else Toast.makeText(context, R.string.tube_computer_not_code, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { Toast.makeText(context, R.string.tube_computer_no_scanner, Toast.LENGTH_LONG).show() }
    }.onFailure { Toast.makeText(context, R.string.tube_computer_no_scanner, Toast.LENGTH_LONG).show() }
}

/** "Ligar este computador?" (from the scanner, or the phone's camera opening the code's address). */
@Composable
internal fun LinkComputerDialog(code: ComputerLink.Code, onDismiss: () -> Unit) {
    val hub = SoundApplication.graph.tube.account
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = sc.Surface2,
        shape = RoundedCornerShape(26.dp),
        icon = { Icon(Icons.Rounded.Computer, null, tint = sc.Accent) },
        title = { Text(stringResource(R.string.tube_computer_link_q), color = sc.Text) },
        text = { Text(stringResource(R.string.tube_computer_link_safe), color = sc.TextMuted, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    hub.linkComputer(code)
                        .onSuccess { name -> Toast.makeText(context, context.getString(R.string.tube_computer_linked, name), Toast.LENGTH_LONG).show() }
                        .onFailure { e ->
                            val msg = when ((e as? RelayException)?.code) {
                                "expired", "delivered", "no_link" -> R.string.tube_computer_expired
                                "too_many" -> R.string.tube_computer_too_many
                                else -> R.string.tube_profile_error
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    busy = false
                    onDismiss()
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = sc.Accent, strokeWidth = 2.dp)
                else Text(stringResource(R.string.tube_computer_link), color = sc.Accent)
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.tube_cancel), color = sc.TextMuted) } },
    )
}
