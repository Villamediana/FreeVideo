package com.villamediana.sound.tube.account

import com.villamediana.sound.social.ComputerLink
import com.villamediana.sound.social.VaultCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * The phone and FreeVideo on the web (web/src/core/crypto.js, vault.js) lock things the same way, both ways:
 * - what the page sealed (web/tools/fixtures.mjs → test resources/web-fixtures.json) opens here: the
 *   vault's contents, its key wrapped by the recovery code, a computer's QR code;
 * - what this phone makes for the page (a vault, the key wrapped by the code, the answer to the QR
 *   code) goes to web/test/fixtures/phone.json, which the page's own test opens (npm test in web/).
 */
class TubeWebParityTest {
    private val fixture: JsonObject = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("web-fixtures.json")!!.readText(),
    ).jsonObject

    private fun str(key: String) = fixture[key]!!.jsonPrimitive.content

    @Test
    fun whatThePageSealedOpensOnThePhone() {
        val raw = VaultCrypto.unb64(str("rawKey"))
        // The key the page wrapped with the code: the code (typed any way) opens it.
        assertArrayEquals(raw, VaultCrypto.unwrap(str("wrappedKey"), VaultCrypto.codeKey(str("code").lowercase(), str("user"))))
        assertNull(VaultCrypto.unwrap(str("wrappedKey"), VaultCrypto.codeKey("AAAA-BBBB-CCCC-DDDD", str("user"))))
        // The contents the page sealed (gzip + AES-GCM in the browser) are the vault the phone reads.
        val opened = VaultCrypto.openData(str("vault"), raw)!!.decodeToString()
        val data = TubeVaultJson.decodeFromString(TubeVaultData.serializer(), opened)
        assertEquals("Pão caseiro ção", data.history.single().title)
        assertEquals(120_000L, data.history.single().positionMs)
        assertEquals(2, data.history.single().plays)
        assertEquals("Amo Pão", data.subscriptions.single().name)
        assertEquals("later:abcdefghijk", data.saved.single().key)
        assertEquals("v:zzzzzzzzzzz", data.dismissals.single().key)
        assertEquals(3, data.searches.single().count)
        assertEquals("culinária", data.interests.single().topic)
        assertEquals(data, TubeVaultMerge.merge(data, data, data))
    }

    @Test
    fun theComputersCodeIsReadAndTheAnswerGoesToThePage() {
        val link = fixture["link"]!!.jsonObject
        val url = link["url"]!!.jsonPrimitive.content
        val code = ComputerLink.code(url)
        assertNotNull(code)
        assertEquals(link["id"]!!.jsonPrimitive.content, code!!.linkId)
        // Only FreeSong's own address, whole and well formed.
        assertNull(ComputerLink.code(url.replace("freevideo.simplyonegoal.com", "freesong.simplyonegoal.com")))
        assertNull(ComputerLink.code(url + "x"))
        assertNull(ComputerLink.code(url.substringBefore('#')))

        val raw = VaultCrypto.newDataKey()
        val content = TubeVaultData(subscriptions = listOf(TvSub("https://www.youtube.com/channel/UCkotlin1234567890123456", "Do celular", null, 7)))
        val sealed = VaultCrypto.sealData(TubeVaultJson.encodeToString(TubeVaultData.serializer(), content).encodeToByteArray(), raw)
        val wrappedKey = VaultCrypto.wrap(raw, VaultCrypto.codeKey(str("code"), str("user")))
        val answer = ComputerLink.wrap(code, str("user"), raw, "Pixel 8")
        val out = buildJsonObject {
            put("rawKey", JsonPrimitive(VaultCrypto.b64(raw)))
            put("vault", JsonPrimitive(sealed))
            put("wrappedKey", JsonPrimitive(wrappedKey))
            put("answer", buildJsonObject {
                put("pub", JsonPrimitive(answer.pub))
                put("iv", JsonPrimitive(answer.iv))
                put("ct", JsonPrimitive(answer.ct))
            })
        }
        val file = File("../web/test/fixtures/phone.json")
        file.parentFile.mkdirs()
        file.writeText(out.toString() + "\n")
    }

    @Test
    fun hkdfIsTheRfcOne() {
        // RFC 5869, test case 1.
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = ComputerLink.hkdf(ikm, salt, info, 42)
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm.joinToString("") { "%02x".format(it) })
    }
}
