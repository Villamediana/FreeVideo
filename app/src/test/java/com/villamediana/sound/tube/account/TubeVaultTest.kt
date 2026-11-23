package com.villamediana.sound.tube.account

import com.villamediana.sound.social.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FreeVideo's vault: how two devices of one profile are put together, and its locks. */
class TubeVaultTest {
    private fun watch(id: String, last: Long, pos: Long = 0, watched: Long = 60_000, plays: Int = 1) =
        TvWatch(id, "Video $id", "Canal", "https://www.youtube.com/channel/UC$id", null, 600_000, pos, watched, plays, last - 1000, last)
    private fun sub(url: String, at: Long = 1) = TvSub(url, "Canal $url", null, at)
    private fun saved(list: String, id: String, at: Long = 1) = TvSaved(list, id, "Video $id", "Canal", null, null, 0, at)

    @Test
    fun subscriptionsAndSavedVideosFollowEveryDevice() {
        val base = TubeVaultData(subscriptions = listOf(sub("a"), sub("b")), saved = listOf(saved("liked", "x")))
        val here = TubeVaultData(subscriptions = listOf(sub("a"), sub("b"), sub("c")), saved = listOf(saved("liked", "x"), saved("later", "y")))
        val there = TubeVaultData(subscriptions = listOf(sub("a"), sub("d")), saved = listOf())
        val m = TubeVaultMerge.merge(base, here, there)
        assertEquals(listOf("a", "c", "d"), m.subscriptions.map { it.channelUrl })
        // Unliked there (x), "later" added here (y).
        assertEquals(listOf("later:y"), m.saved.map { it.key })
    }

    @Test
    fun theSameVideoWatchedOnBothKeepsTheNewestPlaceAndTheMostTime() {
        val here = TubeVaultData(history = listOf(watch("v", last = 100, pos = 30_000, watched = 90_000, plays = 2)))
        val there = TubeVaultData(history = listOf(watch("v", last = 200, pos = 50_000, watched = 40_000, plays = 1), watch("w", last = 150)))
        val m = TubeVaultMerge.merge(null, here, there)
        assertEquals(listOf("v", "w"), m.history.map { it.videoId })
        val v = m.history.first()
        assertEquals(50_000L, v.positionMs)
        assertEquals(90_000L, v.watchedMs)
        assertEquals(2, v.plays)
        assertEquals(200L, v.lastAt)
        // Removed from the history on one device: gone from the other too.
        val gone = TubeVaultMerge.merge(m, m.copy(history = m.history.filter { it.videoId != "w" }), m)
        assertEquals(listOf("v"), gone.history.map { it.videoId })
        // A device just linked loses nothing.
        val fresh = TubeVaultMerge.merge(null, TubeVaultData(), m)
        assertEquals(m, fresh)
    }

    @Test
    fun searchesAddUpAndNothingNewComparesEqual() {
        val here = TubeVaultData(searches = listOf(TvSearch("pão caseiro", 2, 10)), interests = listOf(TvInterest("culinária", 5)))
        val there = TubeVaultData(searches = listOf(TvSearch("pão caseiro", 5, 3), TvSearch("drone", 1, 20)), dismissals = listOf(TvDismissal("v:abc", "x", 1)))
        val m = TubeVaultMerge.merge(null, here, there)
        assertEquals(listOf("drone", "pão caseiro"), m.searches.map { it.query })
        assertEquals(5, m.searches.single { it.query == "pão caseiro" }.count)
        assertEquals(10L, m.searches.single { it.query == "pão caseiro" }.at)
        assertEquals(m, TubeVaultMerge.merge(m, m, m))
        // The JSON the page writes (short names, defaults left out) reads back the same.
        val json = TubeVaultJson.encodeToString(TubeVaultData.serializer(), m)
        assertTrue(json.contains("\"q\":\"drone\""))
        assertEquals(m, TubeVaultJson.decodeFromString(TubeVaultData.serializer(), json))
    }

    @Test
    fun theCodeOpensTheKeyWithFreeVideosOwnSalt() {
        val key = VaultCrypto.newDataKey()
        val wrapped = VaultCrypto.wrap(key, VaultCrypto.codeKey("7K2Q-M9XD-4TFA-H3RB", "uabc"))
        assertArrayEquals(key, VaultCrypto.unwrap(wrapped, VaultCrypto.codeKey("7k2q m9xd 4tfa h3rb", "uabc")))
        assertNull(VaultCrypto.unwrap(wrapped, VaultCrypto.codeKey("7K2Q-M9XD-4TFA-H3RB", "uother")))
    }
}
