package com.nexa.filemanager.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class VaultCodecTest {
    private fun key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun encrypt(data: ByteArray, key: SecretKey, id: String = "test-id"): ByteArray = runBlocking {
        ByteArrayOutputStream().also { VaultCodec.encrypt(ByteArrayInputStream(data), it, key, id, data.size.toLong()) }.toByteArray()
    }
    private fun decrypt(data: ByteArray, key: SecretKey, size: Long, id: String = "test-id"): ByteArray = runBlocking {
        ByteArrayOutputStream().also { VaultCodec.decrypt(ByteArrayInputStream(data), it, key, id, size) }.toByteArray()
    }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Unauthenticated data was accepted") } catch (e: Exception) { assertNotNull(e) }
    }
    @Test fun roundTripsEmptySmallAndMultiChunkFiles() {
        val key = key()
        listOf(0, 1, 65535, 65536, 65537, 300000).forEach { size ->
            val original = ByteArray(size) { (it % 251).toByte() }
            assertArrayEquals(original, decrypt(encrypt(original, key), key, size.toLong()))
        }
    }
    @Test fun rejectsTamperingWrongKeyIdentityTruncationAndTrailingData() {
        val key = key()
        val original = ByteArray(180000) { (it % 251).toByte() }
        val encoded = encrypt(original, key)
        val corrupt = encoded.copyOf().also { it[40] = (it[40].toInt() xor 1).toByte() }
        rejected { decrypt(corrupt, key, original.size.toLong()) }
        rejected { decrypt(encoded, key(), original.size.toLong()) }
        rejected { decrypt(encoded, key, original.size.toLong(), "other-file") }
        rejected { decrypt(encoded.copyOf(encoded.size - 1), key, original.size.toLong()) }
        rejected { decrypt(encoded + byteArrayOf(1), key, original.size.toLong()) }
        rejected { decrypt(encoded, key, original.size.toLong() - 1) }
    }
    @Test fun everyEncryptionUsesFreshNonces() {
        val key = key()
        val original = "private document".toByteArray()
        assertFalse(encrypt(original, key).contentEquals(encrypt(original, key)))
    }
}
