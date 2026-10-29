package com.docsoffice.app.core.crypto

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vaultAuthStore by preferencesDataStore(name = "vault_auth")

@Singleton
class VaultAuth @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
    }

    val hasPin: Flow<Boolean> = context.vaultAuthStore.data.map { prefs ->
        !prefs[Keys.pinHash].isNullOrBlank() && !prefs[Keys.pinSalt].isNullOrBlank()
    }

    suspend fun isPinConfigured(): Boolean = hasPin.first()

    suspend fun setPin(pin: String) {
        require(pin.length in 4..12 && pin.all { it.isDigit() }) { "PIN must be 4–12 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        context.vaultAuthStore.edit { prefs ->
            prefs[Keys.pinSalt] = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs[Keys.pinHash] = Base64.encodeToString(hash, Base64.NO_WRAP)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.vaultAuthStore.data.first()
        val saltB64 = prefs[Keys.pinSalt] ?: return false
        val hashB64 = prefs[Keys.pinHash] ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val actual = hashPin(pin, salt)
        return actual.contentEquals(expected)
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    companion object {
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256
    }
}
