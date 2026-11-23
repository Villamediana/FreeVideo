package com.villamediana.sound.tube.account

import com.villamediana.sound.social.RelayApi
import com.villamediana.sound.social.RelayException
import com.villamediana.sound.social.VaultCrypto
import com.villamediana.sound.tube.data.Dismissal
import com.villamediana.sound.tube.data.Interest
import com.villamediana.sound.tube.data.SavedVideo
import com.villamediana.sound.tube.data.SearchEntry
import com.villamediana.sound.tube.data.Subscription
import com.villamediana.sound.tube.data.TubeDao
import com.villamediana.sound.tube.data.WatchEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * FreeVideo's vault: what this phone knows of the person — history (the latest [TubeVaultMerge.HISTORY]
 * videos, with where each was left), subscriptions, liked and "assistir mais tarde", "não tenho
 * interesse", searches and the interests picked — sealed on the phone ([VaultCrypto]) and kept by
 * the relay as bytes it can't read, so the profile's other phones and computers (FreeVideo on the
 * web, web/src/core/vault.js: the same names and the same rules) stay the same. Short names: the vault
 * travels whole.
 */
@Serializable
data class TvWatch(
    @SerialName("i") val videoId: String,
    @SerialName("t") val title: String,
    @SerialName("cn") val channelName: String = "",
    @SerialName("cu") val channelUrl: String? = null,
    @SerialName("th") val thumbnailUrl: String? = null,
    @SerialName("d") val durationMs: Long = 0,
    @SerialName("p") val positionMs: Long = 0,
    @SerialName("w") val watchedMs: Long = 0,
    @SerialName("n") val plays: Int = 1,
    @SerialName("f") val firstAt: Long = 0,
    @SerialName("l") val lastAt: Long = 0,
)

@Serializable
data class TvSub(
    @SerialName("u") val channelUrl: String,
    @SerialName("n") val name: String,
    @SerialName("a") val avatarUrl: String? = null,
    @SerialName("at") val at: Long = 0,
)

/** A video in "liked" or "later" ([list]). */
@Serializable
data class TvSaved(
    @SerialName("li") val list: String,
    @SerialName("i") val videoId: String,
    @SerialName("t") val title: String,
    @SerialName("cn") val channelName: String = "",
    @SerialName("cu") val channelUrl: String? = null,
    @SerialName("th") val thumbnailUrl: String? = null,
    @SerialName("d") val durationMs: Long = 0,
    @SerialName("at") val at: Long = 0,
) {
    val key: String get() = "$list:$videoId"
}

/** "Não tenho interesse" ("v:<id>") or "Não recomendar o canal" ("c:<url>"). */
@Serializable
data class TvDismissal(@SerialName("k") val key: String, @SerialName("t") val title: String = "", @SerialName("at") val at: Long = 0)

@Serializable
data class TvSearch(@SerialName("q") val query: String, @SerialName("n") val count: Int = 1, @SerialName("at") val at: Long = 0)

@Serializable
data class TvInterest(@SerialName("t") val topic: String, @SerialName("at") val at: Long = 0)

@Serializable
data class TubeVaultData(
    @SerialName("v") val version: Int = 1,
    @SerialName("h") val history: List<TvWatch> = emptyList(),
    @SerialName("s") val subscriptions: List<TvSub> = emptyList(),
    @SerialName("sv") val saved: List<TvSaved> = emptyList(),
    @SerialName("x") val dismissals: List<TvDismissal> = emptyList(),
    @SerialName("q") val searches: List<TvSearch> = emptyList(),
    @SerialName("in") val interests: List<TvInterest> = emptyList(),
)

internal val TubeVaultJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    coerceInputValues = true
}

/**
 * Two devices of one profile put together: what this one has ([local]), what the vault has
 * ([remote]) and what both had at the last sync ([base]). Added on either side stays, removed on
 * either side since the last sync goes; the same video watched on both keeps the newest place and
 * the most time. With no [base] (just linked, just recovered) nothing is removed. Pure; the page's
 * vault.js is the same.
 */
object TubeVaultMerge {
    /** The history the vault carries (the newest). */
    const val HISTORY = 1500
    const val SEARCHES = 200

    fun merge(base: TubeVaultData?, local: TubeVaultData, remote: TubeVaultData): TubeVaultData = TubeVaultData(
        history = threeWay(base?.history, local.history, remote.history, { it.videoId }) { l, r ->
            val newer = if (r.lastAt > l.lastAt) r else l
            newer.copy(
                watchedMs = maxOf(l.watchedMs, r.watchedMs),
                plays = maxOf(l.plays, r.plays),
                firstAt = listOf(l.firstAt, r.firstAt).filter { it > 0 }.minOrNull() ?: 0,
                durationMs = maxOf(l.durationMs, r.durationMs),
            )
        }.sortedWith(compareByDescending<TvWatch> { it.lastAt }.thenBy { it.videoId }).take(HISTORY),
        subscriptions = threeWay(base?.subscriptions, local.subscriptions, remote.subscriptions, { it.channelUrl }) { l, r ->
            if (r.at > l.at) r else l
        }.sortedBy { it.channelUrl },
        saved = threeWay(base?.saved, local.saved, remote.saved, { it.key }) { l, r -> if (r.at > l.at) r else l }.sortedBy { it.key },
        dismissals = threeWay(base?.dismissals, local.dismissals, remote.dismissals, { it.key }) { l, r -> if (r.at > l.at) r else l }.sortedBy { it.key },
        searches = threeWay(base?.searches, local.searches, remote.searches, { it.query }) { l, r ->
            TvSearch(l.query, maxOf(l.count, r.count), maxOf(l.at, r.at))
        }.sortedWith(compareByDescending<TvSearch> { it.at }.thenBy { it.query }).take(SEARCHES),
        interests = threeWay(base?.interests, local.interests, remote.interests, { it.topic }) { l, r -> if (r.at > l.at) r else l }
            .sortedBy { it.topic },
    )

    internal fun <T> threeWay(base: List<T>?, local: List<T>, remote: List<T>, key: (T) -> String, pick: (T, T) -> T): List<T> {
        val b = base?.mapTo(HashSet(), key)
        val l = local.associateBy(key)
        val r = remote.associateBy(key)
        val out = LinkedHashMap<String, T>()
        for ((k, lv) in l) {
            val rv = r[k]
            when {
                rv != null -> out[k] = pick(lv, rv)
                b == null || k !in b -> out[k] = lv
                else -> Unit
            }
        }
        for ((k, rv) in r) if (k !in l && (b == null || k !in b)) out[k] = rv
        return out.values.toList()
    }
}

/** This phone's side: read out of the database for the vault, and made to match it. */
class PhoneTubeVault(private val dao: TubeDao) {
    suspend fun export(): TubeVaultData = TubeVaultData(
        history = dao.historyNow(TubeVaultMerge.HISTORY).map {
            TvWatch(it.videoId, it.title, it.channelName, it.channelUrl, it.thumbnailUrl, it.durationMs, it.positionMs, it.watchedMs, it.plays, it.firstWatchedAt, it.lastWatchedAt)
        }.sortedWith(compareByDescending<TvWatch> { it.lastAt }.thenBy { it.videoId }),
        subscriptions = dao.subscriptionsNow().map { TvSub(it.channelUrl, it.name, it.avatarUrl, it.subscribedAt) }.sortedBy { it.channelUrl },
        saved = (dao.savedNow(SavedVideo.LIST_LIKED) + dao.savedNow(SavedVideo.LIST_LATER)).map {
            TvSaved(it.list, it.videoId, it.title, it.channelName, it.channelUrl, it.thumbnailUrl, it.durationMs, it.savedAt)
        }.sortedBy { it.key },
        dismissals = dao.dismissalsNow().map { TvDismissal(it.key, it.title, it.createdAt) }.sortedBy { it.key },
        searches = dao.searchesNow(TubeVaultMerge.SEARCHES).map { TvSearch(it.query, it.count, it.lastAt) }
            .sortedWith(compareByDescending<TvSearch> { it.at }.thenBy { it.query }),
        interests = dao.interestsNow().map { TvInterest(it.topic, it.createdAt) }.sortedBy { it.topic },
    )

    /** Makes this phone match [merged] ([local] was what it had a moment ago: only that is ever removed). */
    suspend fun apply(merged: TubeVaultData, local: TubeVaultData) {
        val mineW = local.history.associateBy { it.videoId }
        val theirW = merged.history.associateBy { it.videoId }
        for ((id, w) in theirW) {
            val m = mineW[id]
            if (m == null || m != w) {
                dao.putWatch(WatchEntry(w.videoId, w.title, w.channelName, w.channelUrl, w.thumbnailUrl, w.durationMs, w.positionMs, w.watchedMs, w.plays, w.firstAt, w.lastAt))
            }
        }
        for (id in mineW.keys) if (id !in theirW) dao.deleteWatch(id)

        val mineS = local.subscriptions.associateBy { it.channelUrl }
        val theirS = merged.subscriptions.associateBy { it.channelUrl }
        for ((url, s) in theirS) if (mineS[url] != s) dao.putSubscription(Subscription(s.channelUrl, s.name, s.avatarUrl, s.at))
        for (url in mineS.keys) if (url !in theirS) dao.deleteSubscription(url)

        val mineV = local.saved.associateBy { it.key }
        val theirV = merged.saved.associateBy { it.key }
        for ((k, v) in theirV) if (mineV[k] != v) dao.putSaved(SavedVideo(v.list, v.videoId, v.title, v.channelName, v.channelUrl, v.thumbnailUrl, v.durationMs, v.at))
        for ((k, v) in mineV) if (k !in theirV) dao.deleteSaved(v.list, v.videoId)

        val mineX = local.dismissals.associateBy { it.key }
        val theirX = merged.dismissals.associateBy { it.key }
        for ((k, x) in theirX) if (mineX[k] != x) dao.putDismissal(Dismissal(x.key, x.title, x.at))
        for (k in mineX.keys) if (k !in theirX) dao.deleteDismissal(k)

        val mineQ = local.searches.associateBy { it.query }
        val theirQ = merged.searches.associateBy { it.query }
        for ((q, s) in theirQ) if (mineQ[q] != s) dao.putSearch(SearchEntry(s.query, s.count, s.at))
        for (q in mineQ.keys) if (q !in theirQ) dao.deleteSearch(q)

        val mineI = local.interests.associateBy { it.topic }
        val theirI = merged.interests.associateBy { it.topic }
        val add = theirI.values.filter { mineI[it.topic] != it }
        if (add.isNotEmpty()) dao.putInterests(add.map { Interest(it.topic, it.at) })
        for (t in mineI.keys) if (t !in theirI) dao.deleteInterest(t)
    }
}

/**
 * The profile's vault on this phone (the same steps as FreeSong's VaultSync): read the vault, put it
 * together with this phone ([TubeVaultMerge]), make this phone match and write the result back,
 * sealed here. The first device to sync makes the key and wraps it with the code it shows.
 */
class TubeVaultSync(
    private val dir: File,
    private val api: RelayApi,
    private val local: PhoneTubeVault,
    private val profile: () -> Pair<String, String?>?,
    private val loadKey: suspend (userId: String) -> ByteArray?,
    private val saveKey: suspend (userId: String, key: ByteArray?) -> Unit,
) {
    enum class Outcome { SYNCED, NO_PROFILE, OFFLINE, LOCKED }

    private val lock = Mutex()

    /** The vault's version after the last sync (a long wait asks for anything newer). */
    @Volatile var rev: Long = 0
        private set

    suspend fun sync(code: String? = null): Outcome = lock.withLock { withContext(Dispatchers.IO) { run(code, rekey = null) } }

    suspend fun rekey(newCode: String): Outcome = lock.withLock { withContext(Dispatchers.IO) { run(null, rekey = newCode) } }

    suspend fun forget(userId: String) = lock.withLock {
        withContext(Dispatchers.IO) { baseFile(userId).delete() }
        saveKey(userId, null)
        rev = 0
    }

    private suspend fun run(typed: String?, rekey: String?): Outcome {
        val (userId, shown) = profile() ?: return Outcome.NO_PROFILE
        repeat(3) {
            val remote = try {
                api.vault()
            } catch (e: IOException) {
                return Outcome.OFFLINE
            }
            val saved = loadKey(userId)
            val key: ByteArray
            var wrapped: String? = null
            var remoteData = TubeVaultData()
            if (remote == null) {
                val code = rekey ?: shown ?: typed ?: return Outcome.LOCKED
                key = saved ?: VaultCrypto.newDataKey()
                wrapped = VaultCrypto.wrap(key, VaultCrypto.codeKey(code, userId))
            } else {
                val withSaved = saved?.let { VaultCrypto.openData(remote.data, it) }
                val opened: ByteArray
                if (saved != null && withSaved != null) {
                    key = saved
                    opened = withSaved
                } else {
                    val code = typed ?: shown ?: return Outcome.LOCKED
                    key = VaultCrypto.unwrap(remote.key, VaultCrypto.codeKey(code, userId)) ?: return Outcome.LOCKED
                    opened = VaultCrypto.openData(remote.data, key) ?: return Outcome.LOCKED
                }
                remoteData = runCatching { TubeVaultJson.decodeFromString(TubeVaultData.serializer(), opened.decodeToString()) }.getOrNull()
                    ?: return Outcome.LOCKED
                if (rekey != null) wrapped = VaultCrypto.wrap(key, VaultCrypto.codeKey(rekey, userId))
            }
            val base = loadBase(userId)
            val mine = local.export()
            val merged = TubeVaultMerge.merge(base, mine, remoteData)
            local.apply(merged, mine)
            var newRev = remote?.rev ?: 0
            if (remote == null || merged != remoteData || wrapped != null) {
                val sealed = VaultCrypto.sealData(TubeVaultJson.encodeToString(TubeVaultData.serializer(), merged).encodeToByteArray(), key)
                try {
                    newRev = api.putVault(remote?.rev ?: 0, sealed, wrapped)
                } catch (e: RelayException) {
                    if (e.status == 409) return@repeat
                    return Outcome.OFFLINE
                } catch (e: IOException) {
                    return Outcome.OFFLINE
                }
            }
            saveKey(userId, key)
            saveBase(userId, merged)
            rev = newRev
            return Outcome.SYNCED
        }
        return Outcome.OFFLINE
    }

    private fun baseFile(userId: String) = File(dir, "tube-vault-base-${userId.filter(Char::isLetterOrDigit)}.json")

    private fun loadBase(userId: String): TubeVaultData? =
        runCatching { TubeVaultJson.decodeFromString(TubeVaultData.serializer(), baseFile(userId).readText()) }.getOrNull()

    private fun saveBase(userId: String, data: TubeVaultData) {
        runCatching {
            dir.mkdirs()
            val f = baseFile(userId)
            val tmp = File(f.path + ".tmp")
            tmp.writeText(TubeVaultJson.encodeToString(TubeVaultData.serializer(), data))
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
    }
}
