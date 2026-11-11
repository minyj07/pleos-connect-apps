package com.example.drivecoach.share

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import kotlin.experimental.xor
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareCryptoTest {

    private val plain = """{"v":1,"kind":"TRIP"}""".toByteArray()

    @Test
    fun sealedBlobOpensWithItsKey() {
        val sealed = ShareCrypto.seal(plain)
        assertContentEquals(plain, ShareCrypto.open(sealed.blob, sealed.key))
    }

    @Test
    fun layoutIsMagicFlagsIvThenCiphertextAndTag() {
        val sealed = ShareCrypto.seal(plain)
        assertEquals("DCS1", sealed.blob.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(0, sealed.blob[4].toInt())
        assertEquals(5 + ShareCrypto.IV_BYTES + plain.size + 16, sealed.blob.size)
        assertEquals(ShareCrypto.KEY_BYTES, sealed.key.size)
    }

    @Test
    fun everySealDrawsANewKeyAndIv() {
        val a = ShareCrypto.seal(plain)
        val b = ShareCrypto.seal(plain)
        assertFalse(a.key.contentEquals(b.key))
        assertFalse(a.blob.copyOfRange(5, 17).contentEquals(b.blob.copyOfRange(5, 17)))
    }

    @Test
    fun wrongKeyFails() {
        val sealed = ShareCrypto.seal(plain)
        assertFailsWith<AEADBadTagException> { ShareCrypto.open(sealed.blob, ByteArray(ShareCrypto.KEY_BYTES)) }
    }

    @Test
    fun tamperedCiphertextOrIvFails() {
        val sealed = ShareCrypto.seal(plain)
        for (index in listOf(sealed.blob.size - 1, sealed.blob.size - 20, 8)) {
            val bad = sealed.blob.copyOf().also { it[index] = it[index] xor 1 }
            assertFailsWith<AEADBadTagException>("byte $index") { ShareCrypto.open(bad, sealed.key) }
        }
    }

    @Test
    fun unknownFormatIsRejectedBeforeDecrypting() {
        val sealed = ShareCrypto.seal(plain)
        val badMagic = sealed.blob.copyOf().also { it[0] = 'X'.code.toByte() }
        val badFlags = sealed.blob.copyOf().also { it[4] = 1 }
        assertFailsWith<IllegalArgumentException> { ShareCrypto.open(badMagic, sealed.key) }
        assertFailsWith<IllegalArgumentException> { ShareCrypto.open(badFlags, sealed.key) }
        assertFailsWith<IllegalArgumentException> { ShareCrypto.open(sealed.blob.copyOf(20), sealed.key) }
    }

    @Test
    fun fixedRandomGivesAReproducibleTestVector() {
        val a = ShareCrypto.seal(plain, FixedRandom())
        val b = ShareCrypto.seal(plain, FixedRandom())
        assertContentEquals(a.blob, b.blob)
        assertTrue(ShareLink.build("https://share.example.com/", "AAAAAAAAAAAAAAAAAAAAAA", a.key).startsWith("https://share.example.com/s#AAAAAAAAAAAAAAAAAAAAAA."))
    }
}

/** Deterministic stand-in for SecureRandom, for fixed test vectors only. */
class FixedRandom(private var next: Int = 1) : SecureRandom() {
    override fun nextBytes(bytes: ByteArray) {
        for (i in bytes.indices) bytes[i] = (next++ and 0xFF).toByte()
    }
}
