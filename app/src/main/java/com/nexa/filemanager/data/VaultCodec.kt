package com.nexa.filemanager.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated 64 KB records keep even multi-GB vault files bounded in memory. */
object VaultCodec {
    private const val MAGIC = 0x4E585631 // NXV1
    private const val CHUNK_SIZE = 64 * 1024

    suspend fun encrypt(input: InputStream, output: OutputStream, key: SecretKey, id: String, size: Long): Long {
        require(size >= 0)
        val data = DataOutputStream(output.buffered())
        data.writeInt(MAGIC); data.writeLong(size)
        val random = SecureRandom()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val buffer = ByteArray(CHUNK_SIZE)
        var total = 0L
        var index = 0
        do {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer).coerceAtLeast(0)
            if (count == 0 && total > 0) break
            val iv = ByteArray(12).also(random::nextBytes)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD("$id:$index:$size".toByteArray(Charsets.UTF_8))
            val encrypted = cipher.doFinal(buffer, 0, count)
            data.writeInt(count); data.write(iv); data.write(encrypted)
            total += count; index++
            if (count == 0) break
        } while (true)
        require(total == size) { "Source size changed during encryption." }
        data.flush()
        return total
    }

    suspend fun decrypt(input: InputStream, output: OutputStream, key: SecretKey, id: String, size: Long) {
        val data = DataInputStream(input.buffered())
        require(data.readInt() == MAGIC) { "Unsupported vault format." }
        val expected = data.readLong()
        require(expected == size && size >= 0) { "Invalid vault size." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        var total = 0L
        var index = 0
        do {
            currentCoroutineContext().ensureActive()
            val count = data.readInt()
            require(count in 0..CHUNK_SIZE && total + count <= expected) { "Invalid encrypted chunk." }
            require(count > 0 || (expected == 0L && index == 0)) { "Unexpected empty chunk." }
            val iv = ByteArray(12).also(data::readFully)
            val encrypted = ByteArray(count + 16).also(data::readFully)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            // File identity, total size and sequence number prevent record swapping/truncation.
            cipher.updateAAD("$id:$index:$expected".toByteArray(Charsets.UTF_8))
            output.write(cipher.doFinal(encrypted))
            total += count; index++
        } while (total < expected)
        require(data.read() == -1) { "Unexpected trailing vault data." }
        output.flush()
    }
}
