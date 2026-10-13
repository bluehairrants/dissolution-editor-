package com.dissolution.editor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiSettings(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
)

object AiSettingsStore {
    private const val PREFS = "ai_settings"
    private const val KEY_ALIAS = "dissolution_ai_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun load(context: Context): AiSettings {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = preferences.getString("api_key", null)
        return AiSettings(
            baseUrl = preferences.getString("base_url", "").orEmpty(),
            model = preferences.getString("model", "").orEmpty(),
            apiKey = encrypted?.let(::decrypt).orEmpty(),
        )
    }

    fun save(context: Context, settings: AiSettings) {
        val normalizedUrl = settings.baseUrl.trim()
        require(normalizedUrl.isEmpty() || normalizedUrl.startsWith("https://")) {
            "BossAI endpoint must use HTTPS"
        }
        val encryptedKey = settings.apiKey.trim().takeIf(String::isNotEmpty)?.let(::encrypt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("base_url", normalizedUrl)
            .putString("model", settings.model.trim())
            .putString("api_key", encryptedKey)
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrDefault("")

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}