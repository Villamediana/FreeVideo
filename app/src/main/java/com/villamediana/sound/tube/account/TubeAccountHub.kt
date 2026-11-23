package com.villamediana.sound.tube.account

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.villamediana.sound.BuildConfig
import com.villamediana.sound.social.AccountStatus
import com.villamediana.sound.social.Computer
import com.villamediana.sound.social.ComputerLink
import com.villamediana.sound.social.RelayApi
import com.villamediana.sound.social.RelayException
import com.villamediana.sound.social.SocialAccount
import com.villamediana.sound.tube.data.TubeDatabase
import com.villamediana.sound.tube.data.TubeLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * FreeVideo's profile, like FreeSong's but without the social part: made by itself on the first
 * launch (a random @name, a recovery code to bring it to another phone), with a vault — history,
 * subscriptions, saved videos, dismissals, searches, interests — sealed here and kept by FreeVideo's
 * relay ([BuildConfig.TUBE_RELAY_URL]); and the computers linked to it (FreeVideo on the web).
 *
 * The vault follows: when the app comes to the screen, a moment after something changes here (the
 * history, which changes all the time while watching, a minute after), and at once when a computer or
 * another phone changes it (a long wait on the relay while the app is on the screen).
 */
@OptIn(FlowPreview::class)
class TubeAccountHub(
    private val context: Context,
    private val scope: CoroutineScope,
    http: OkHttpClient,
    private val db: TubeDatabase,
    private val library: TubeLibrary,
) {
    // The long wait holds the request ~25 s: its own client, with room for it.
    private val longHttp = http.newBuilder().readTimeout(40, TimeUnit.SECONDS).build()

    val account = SocialAccount(context, scope) { token -> RelayApi(longHttp, BuildConfig.TUBE_RELAY_URL, token) }

    val vault = TubeVaultSync(
        dir = context.filesDir,
        api = account.api,
        local = PhoneTubeVault(db.dao()),
        profile = { account.me.value?.id?.let { it to account.recoveryCode.value } },
        loadKey = { account.vaultKey(it) },
        saveKey = { id, key -> account.setVaultKey(id, key) },
    )

    private val _computers = MutableStateFlow<List<Computer>>(emptyList())
    val computers: StateFlow<List<Computer>> = _computers

    private val _foreground = MutableStateFlow(false)
    private var live: Job? = null
    @Volatile private var started = false

    fun start() {
        if (started || BuildConfig.TUBE_RELAY_URL.isBlank()) return
        // Unit tests drive it themselves: no real relay from a test run.
        if (android.os.Build.FINGERPRINT == "robolectric") return
        started = true
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) { _foreground.value = true }
                override fun onStop(owner: LifecycleOwner) { _foreground.value = false }
            })
        }
        scope.launch {
            _foreground.collect { fg ->
                if (fg) {
                    quietly { bootstrap() }
                    live?.cancel()
                    live = scope.launch { listen() }
                } else {
                    live?.cancel()
                    live = null
                    // What was watched until now goes with the app leaving the screen.
                    if (account.ready) quietly { vault.sync() }
                }
            }
        }
        // Subscriptions, saved videos, dismissals, interests changed here: the vault follows a moment later.
        scope.launch {
            combine(
                library.subscriptions.map { l -> l.map { it.channelUrl } },
                library.liked.map { l -> l.map { it.videoId } },
                library.later.map { l -> l.map { it.videoId } },
                library.dismissals.map { l -> l.map { it.key } },
                library.interests.map { l -> l.map { it.topic } },
            ) { a, b, c, d, e -> listOf(a, b, c, d, e) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(SOON_MS)
                .collect { if (account.ready) quietly { vault.sync() } }
        }
        // The history changes every few seconds while watching: once a minute is enough.
        scope.launch {
            library.history.map { l -> l.size to (l.firstOrNull()?.lastWatchedAt ?: 0) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(HISTORY_MS)
                .collect { if (account.ready) quietly { vault.sync() } }
        }
    }

    /** The profile (made now if there is none), the vault, the computers. */
    suspend fun bootstrap() {
        account.ensure() ?: return
        vault.sync()
        refreshComputers()
    }

    /** While on the screen: a long wait on the relay, answered as soon as a computer or another phone writes. */
    private suspend fun listen() {
        var pause = 2_000L
        while (scope.isActive) {
            if (!account.ready) {
                delay(5_000)
                continue
            }
            try {
                val changed = withContext(Dispatchers.IO) { account.api.vaultChange(vault.rev, WAIT_S) }
                pause = 2_000L
                if (changed != null && changed.rev != vault.rev) vault.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(pause)
                pause = (pause * 2).coerceAtMost(60_000L)
            }
        }
    }

    // ------------------------------------------------------------------ profile

    /** Another phone's code: that profile comes here, with its vault (opened with the code). */
    suspend fun recover(code: String): Result<Unit> = runCatching {
        val old = account.me.value?.id
        account.recover(code).getOrThrow()
        if (old != null) vault.forget(old)
        vault.sync(code)
        refreshComputers()
    }

    suspend fun newRecoveryCode(): Result<String> = account.newRecoveryCode().onSuccess { code -> quietly { vault.rekey(code) } }

    suspend fun deleteProfile(): Result<Unit> {
        val id = account.me.value?.id
        return account.delete { id?.let { vault.forget(it) } }.onSuccess { _computers.value = emptyList() }
    }

    /** A profile made again after a deletion ("Criar perfil"). */
    suspend fun createAgain(): Boolean {
        val me = account.ensure(force = true) ?: return false
        quietly { vault.sync() }
        return me.id.isNotEmpty()
    }

    val status: StateFlow<AccountStatus> get() = account.status

    // ------------------------------------------------------------------ computers

    suspend fun refreshComputers() {
        if (!account.ready) return
        quietly { _computers.value = withContext(Dispatchers.IO) { account.api.computers() } }
    }

    suspend fun linkComputer(code: ComputerLink.Code): Result<String> = runCatching {
        val me = account.me.value ?: account.ensure() ?: throw IOException("no profile")
        var key = account.vaultKey(me.id)
        if (key == null) {
            vault.sync()
            key = account.vaultKey(me.id)
        }
        if (key == null) throw RelayException(409, "no_vault")
        val owner = listOfNotNull(android.os.Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }, android.os.Build.MODEL)
            .distinct().joinToString(" ").trim().take(60)
        val wrapped = withContext(Dispatchers.Default) { ComputerLink.wrap(code, me.id, key, owner) }
        val name = withContext(Dispatchers.IO) { account.api.approveLink(code.linkId, wrapped, owner) }
        refreshComputers()
        name
    }

    suspend fun unlinkComputer(id: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { account.api.unlinkComputer(id) }
        _computers.value = _computers.value.filterNot { it.id == id }
        refreshComputers()
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("TubeAccount", "account step failed", e)
        }
    }

    private companion object {
        const val SOON_MS = 2_000L
        const val HISTORY_MS = 60_000L
        const val WAIT_S = 25
    }
}
