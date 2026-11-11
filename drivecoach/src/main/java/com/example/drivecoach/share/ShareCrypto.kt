package com.example.drivecoach.share

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end sealing for "폰으로 보기": every share gets a fresh AES-256 key that travels only in the
 * link's fragment, so the share server keeps ciphertext it cannot read.
 *
 * Blob = "DCS1" | flags (1 byte, 0 = plain JSON) | iv (12) | AES-GCM ciphertext + 16-byte tag, with the
 * first 5 bytes as associated data. The phone viewer (share-server `viewer/viewer.js`) opens the same format.
 */
object ShareCrypto {

    const val KEY_BYTES = 32
    const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val MAGIC = "DCS1".toByteArray(Charsets.US_ASCII)
    private const val FLAGS_PLAIN: Byte = 0
    private val HEADER_SIZE = MAGIC.size + 1

    class Sealed(val key: ByteArray, val blob: ByteArray)

    /** [random] is only replaced in tests (fixed test vectors); a new key and IV are drawn every call. */
    fun seal(plain: ByteArray, random: SecureRandom = SecureRandom()): Sealed {
        val key = ByteArray(KEY_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = MAGIC + FLAGS_PLAIN
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return Sealed(key, header + iv + cipher.doFinal(plain))
    }

    /** Inverse of [seal]; throws on a wrong key, a tampered blob or an unknown format. */
    fun open(blob: ByteArray, key: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "key must be $KEY_BYTES bytes" }
        require(blob.size >= HEADER_SIZE + IV_BYTES + TAG_BITS / 8) { "blob too short" }
        require(blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "not a DCS1 blob" }
        require(blob[MAGIC.size] == FLAGS_PLAIN) { "unsupported flags ${blob[MAGIC.size]}" }
        val iv = blob.copyOfRange(HEADER_SIZE, HEADER_SIZE + IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(blob, 0, HEADER_SIZE)
        return cipher.doFinal(blob, HEADER_SIZE + IV_BYTES, blob.size - HEADER_SIZE - IV_BYTES)
    }
}

object ShareLink {

    /** `<viewBase>/s#<id>.<key>`: id and key sit only in the fragment, which browsers never send to a server. */
    fun build(viewBase: String, id: String, key: ByteArray): String = "${viewBase.trimEnd('/')}/s#$id.${base64Url(key)}"

    fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
