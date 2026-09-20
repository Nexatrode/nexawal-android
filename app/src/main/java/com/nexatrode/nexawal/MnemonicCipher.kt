package com.nexatrode.nexawal

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed AES-GCM helper for wallet mnemonic persistence.
 *
 * The mnemonic never needs to be stored in plaintext on disk.
 * We store:
 * - Base64(iv)
 * - Base64(ciphertextWithTag)
 */
object MnemonicCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val LEGACY_KEY_ALIAS = "com.nexatrode.nexawal.wallet.mnemonic"
    private const val DEVICE_AUTH_KEY_ALIAS = "com.nexatrode.nexawal.wallet.mnemonic.device-auth.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    // The plaintext is cached only for the unlocked app session. This short window exists solely
    // so the operation immediately following the system prompt can unwrap or wrap the seed.
    private const val AUTH_VALIDITY_SECONDS = 30

    data class EncryptedMnemonic(
        val ivBase64: String,
        val ciphertextBase64: String,
    )

    @JvmStatic
    fun encrypt(plaintext: String, requireDeviceAuth: Boolean = false): EncryptedMnemonic {
        require(plaintext.isNotBlank()) { "mnemonic must not be blank" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(requireDeviceAuth))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv ?: error("Cipher did not return an IV")

        return EncryptedMnemonic(
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        )
    }

    @JvmStatic
    fun decrypt(
        ivBase64: String,
        ciphertextBase64: String,
        requireDeviceAuth: Boolean = false,
    ): String {
        require(ivBase64.isNotBlank()) { "iv must not be blank" }
        require(ciphertextBase64.isNotBlank()) { "ciphertext must not be blank" }

        val iv = Base64.decode(ivBase64, Base64.DEFAULT)
        val ciphertext = Base64.decode(ciphertextBase64, Base64.DEFAULT)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(requireDeviceAuth),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )

        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, StandardCharsets.UTF_8)
    }

    /** Create the protected key before showing the prompt so the resulting auth token can use it. */
    @JvmStatic
    fun prepareDeviceAuthKey() {
        getOrCreateSecretKey(requireDeviceAuth = true)
    }

    private fun getOrCreateSecretKey(requireDeviceAuth: Boolean): SecretKey {
        val alias = if (requireDeviceAuth) DEVICE_AUTH_KEY_ALIAS else LEGACY_KEY_ALIAS
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
        if (requireDeviceAuth) {
            spec.setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    AUTH_VALIDITY_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
        } else {
            spec.setUserAuthenticationRequired(false)
        }

        keyGenerator.init(spec.build())
        return keyGenerator.generateKey()
    }
}
