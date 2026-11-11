package com.example.drivecoach.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Source of the data key; [destroy] makes everything encrypted with it unreadable (crypto-shredding). */
interface KeyProvider {
    fun key(): SecretKey
    fun destroy()
}

/**
 * AES-256-GCM key held by the Android Keystore (store security guide: driving data is encrypted at
 * rest, keys never leave the Keystore). Keystore keys generate their own IVs.
 */
class AndroidKeystoreKeyProvider(private val alias: String = "drivecoach.trips.v1") : KeyProvider {

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    @Synchronized
    override fun key(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    @Synchronized
    override fun destroy() {
        runCatching { keyStore.deleteEntry(alias) }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

/**
 * Compressed, authenticated encryption of one file: `"DCT1" | version | ivLength | iv | AES-GCM(gzip(data))`.
 * [aad] binds a blob to its slot (trip id, "index") so files cannot be swapped around.
 */
class AeadCodec(private val keys: KeyProvider) {

    fun encrypt(plain: ByteArray, aad: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.key())
        cipher.updateAAD(aad.toByteArray())
        val sealed = cipher.doFinal(gzip(plain))
        val iv = cipher.iv
        return ByteBuffer.allocate(MAGIC.size + 2 + iv.size + sealed.size)
            .put(MAGIC).put(VERSION).put(iv.size.toByte()).put(iv).put(sealed)
            .array()
    }

    fun decrypt(blob: ByteArray, aad: String): ByteArray {
        val buf = ByteBuffer.wrap(blob)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        require(magic.contentEquals(MAGIC) && buf.get() == VERSION) { "not a DriveCoach file" }
        val iv = ByteArray(buf.get().toInt()).also { buf.get(it) }
        val sealed = ByteArray(buf.remaining()).also { buf.get(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad.toByteArray())
        return gunzip(cipher.doFinal(sealed))
    }

    fun destroyKey() = keys.destroy()

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()

    private fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val VERSION: Byte = 1
        val MAGIC = "DCT1".toByteArray()
    }
}
