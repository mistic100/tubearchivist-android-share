package fr.strangeplanet.tubearchivist.share

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed class ConfigReadResult {
    data object Empty : ConfigReadResult()
    data class Loaded(val config: InstanceConfig) : ConfigReadResult()
    data class Failed(val message: String) : ConfigReadResult()
}

class SecureConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val keyStore: KeyStore
        get() = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    fun read(): ConfigReadResult {
        val encrypted = preferences.getString(CONFIG_KEY, null) ?: return ConfigReadResult.Empty
        val autoDownload = preferences.getBoolean(AUTODL_KEY, false)
        return try {
            val combined = decrypt(encrypted)
            val separator = combined.indexOf('\n')
            if (separator <= 0 || separator == combined.lastIndex) {
                ConfigReadResult.Failed("Saved settings could not be read. Enter the instance URL and API token again.")
            } else {
                ConfigReadResult.Loaded(
                    InstanceConfig(
                        baseUrl = combined.substring(0, separator),
                        apiToken = combined.substring(separator + 1),
                        autoDownload = autoDownload
                    )
                )
            }
        } catch (_: GeneralSecurityException) {
            ConfigReadResult.Failed("Saved credentials could not be decrypted. Enter the instance URL and API token again.")
        } catch (_: IOException) {
            ConfigReadResult.Failed("Saved credentials could not be read. Enter the instance URL and API token again.")
        } catch (_: IllegalArgumentException) {
            ConfigReadResult.Failed("Saved settings are invalid. Enter the instance URL and API token again.")
        }
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    fun write(config: InstanceConfig) {
        val encrypted = encrypt("${config.baseUrl}\n${config.apiToken}")
        val ok = preferences.edit()
            .putString(CONFIG_KEY, encrypted)
            .putBoolean(AUTODL_KEY, config.autoDownload)
            .commit();
        if (!ok) {
            throw IOException("Could not save the encrypted instance settings.")
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val combined = cipher.iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= GCM_IV_LENGTH_BYTES) {
            throw GeneralSecurityException("Encrypted settings are incomplete.")
        }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "instance_config"
        const val CONFIG_KEY = "encrypted_config"
        const val AUTODL_KEY = "auto_download"
        const val KEY_ALIAS = "tube_archivist_api_token"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
