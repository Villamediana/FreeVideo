package com.villamediana.sound

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.villamediana.sound.ui.nav.ExternalRequest
import com.villamediana.sound.ui.nav.Inbound
import com.villamediana.sound.ui.theme.SoundTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** YouTube links shared to FreeVideo or opened from the browser, waiting for the shell to open them. */
    private val incoming = MutableStateFlow<Inbound?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Only called when the system showed the splash: the logo stays a moment over the ready
        // app (it used to vanish with the first frame), then grows and fades away.
        splash.setOnExitAnimationListener { provider ->
            runCatching { provider.iconView }.getOrNull()?.animate()
                ?.scaleX(1.18f)?.scaleY(1.18f)?.alpha(0f)
                ?.setStartDelay(SPLASH_HOLD_MS)?.setDuration(SPLASH_EXIT_MS)?.setInterpolator(AccelerateInterpolator(1.4f))?.start()
            provider.view.animate().alpha(0f).setStartDelay(SPLASH_HOLD_MS + SPLASH_EXIT_MS / 3).setDuration(SPLASH_EXIT_MS)
                .setInterpolator(DecelerateInterpolator()).withEndAction { provider.remove() }.start()
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        // First frame already in the saved theme (no flash of the other one).
        val settingsStore = SoundApplication.graph.settings
        val initialSettings = kotlinx.coroutines.runBlocking { settingsStore.current() }
        val initialTheme = initialSettings.theme
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        com.villamediana.sound.ui.theme.SoundColors.palette = com.villamediana.sound.ui.theme.paletteFor(initialTheme, night, initialSettings.accent)
        setContent {
            val settings by settingsStore.settings.collectAsState(initial = null)
            SoundTheme(mode = settings?.theme ?: initialTheme, accent = settings?.accent ?: initialSettings.accent) {
                com.villamediana.sound.ui.tube.TubeRoot(incoming = incoming)
            }
        }
    }

    /** Android 8–11: home pressed with a video playing → the floating window (12+ enters it by itself). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT in 26..30) {
            val tube = SoundApplication.graph.tube
            val enabled = kotlinx.coroutines.runBlocking { tube.settings.current().pictureInPicture }
            com.villamediana.sound.ui.tube.Pip.enterIfWanted(this, tube.player.state.value, enabled)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        // "Nova versão do FreeVideo" notification: the update sheet.
        if (intent?.getBooleanExtra(com.villamediana.sound.update.UpdateNotifier.EXTRA_OPEN, false) == true) {
            SoundApplication.graph.updates.openSheet()
            return
        }
        when (val request = ExternalRequest.of(intent)) {
            is ExternalRequest.OpenLink -> incoming.value = Inbound.Link(request.text)
            is ExternalRequest.PlayFromSearch -> if (request.query.isNotBlank()) incoming.value = Inbound.Query(request.query)
            null -> Unit
        }
    }
}

private const val SPLASH_HOLD_MS = 700L
private const val SPLASH_EXIT_MS = 480L
