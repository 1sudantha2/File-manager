package com.nexa.filemanager.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Properties
import java.util.UUID
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class VaultRepository(context: Context) {
    private val directory = File(context.filesDir, "safe_vault").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("vault_security", Context.MODE_PRIVATE)
    val configured: Boolean get() = prefs.contains("pin_hash")
    val lockedUntil: Long get() = prefs.getLong("locked_until", 0)
    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 210_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    suspend fun setPin(pin: String) = withContext(Dispatchers.IO) {
        require(!configured) { "Vault PIN is already configured." }
        require(pin.length in 6..12 && pin.all { it in '0'..'9' }) { "Choose a 6–12 digit PIN." }
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        check(prefs.edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP)).commit()) { "Could not save PIN." }
    }
    suspend fun unlock(pin: String): Boolean = withContext(Dispatchers.IO) {
        require(System.currentTimeMillis() >= lockedUntil) { "Too many attempts. Try again in a minute." }
        check(configured) { "Set up a PIN first." }
        val salt = Base64.decode(prefs.getString("salt", ""), Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString("pin_hash", ""), Base64.NO_WRAP)
        val valid = MessageDigest.isEqual(derive(pin, salt), expected)
        val attempts = if (valid) 0 else prefs.getInt("attempts", 0) + 1
        prefs.edit().putInt("attempts", if (attempts >= 5) 0 else attempts)
            .putLong("locked_until", if (attempts >= 5) System.currentTimeMillis() + 60_000 else 0).commit()
        valid
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "nexa_vault_aes_v1"
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    suspend fun items(): List<VaultItem> = withContext(Dispatchers.IO) {
        directory.listFiles { f -> f.extension == "properties" }.orEmpty().mapNotNull { info ->
            runCatching {
                val p = Properties().apply { FileInputStream(info).use { load(it) } }
                val id = info.nameWithoutExtension
                val payload = File(directory, "$id.vault")
                if (!payload.exists()) null else VaultItem(id, p.getProperty("name"), p.getProperty("original"), p.getProperty("size").toLong(), payload)
            }.getOrNull()
        }.sortedBy { it.name.lowercase() }
    }
    suspend fun store(file: File) = withContext(Dispatchers.IO) {
        require(file.isFile) { "Vault accepts individual files, not folders. ZIP a folder first." }
        val id = UUID.randomUUID().toString()
        val output = File(directory, "$id.vault")
        val temp = File(directory, "$id.pending")
        val metadata = File(directory, "$id.properties")
        try {
            val originalSize = file.length()
            val modified = file.lastModified()
            FileOutputStream(temp).use { raw ->
                FileInputStream(file).use { VaultCodec.encrypt(it, raw, key(), id, originalSize) }
                raw.fd.sync()
            }
            check(file.length() == originalSize && file.lastModified() == modified) { "Source changed during encryption. Original was kept; retry." }
            Files.move(temp.toPath(), output.toPath())
            val props = Properties().apply { setProperty("name", file.name); setProperty("original", file.absolutePath); setProperty("size", file.length().toString()) }
            FileOutputStream(metadata).use { props.store(it, null); it.fd.sync() }
            check(file.delete()) { "Encrypted copy saved, but original could not be removed. Remove the original manually." }
        } finally { temp.delete() }
    }
    suspend fun restore(item: VaultItem, destination: File) = withContext(Dispatchers.IO) {
        val target = FileRules.child(destination, item.name)
        require(!target.exists()) { "Original already exists. Choose another folder or rename the original first." }
        val temp = File(destination, ".restore-${UUID.randomUUID()}")
        try {
            FileInputStream(item.payload).use { input ->
                FileOutputStream(temp).use { output ->
                    VaultCodec.decrypt(input, output, key(), item.id, item.size)
                    output.fd.sync()
                }
            }
            check(temp.length() == item.size) { "Vault file size verification failed." }
            Files.move(temp.toPath(), target.toPath())
            check(item.payload.delete()) { "Restored, but encrypted copy could not be removed." }
            File(directory, "${item.id}.properties").delete()
        } finally { temp.delete() }
    }
}
