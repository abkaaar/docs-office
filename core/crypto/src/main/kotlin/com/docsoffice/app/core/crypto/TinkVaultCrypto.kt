package com.docsoffice.app.core.crypto

import android.content.Context
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TinkVaultCrypto @Inject constructor(
    @ApplicationContext private val context: Context,
) : VaultCrypto {

    private val streamingAead: StreamingAead by lazy { createStreamingAead() }

    override fun isAvailable(): Boolean = runCatching { streamingAead }.isSuccess

    override fun encrypt(input: InputStream, output: OutputStream, associatedData: ByteArray) {
        streamingAead.newEncryptingStream(output, associatedData).use { encrypted ->
            input.copyTo(encrypted)
        }
    }

    override fun decrypt(input: InputStream, output: OutputStream, associatedData: ByteArray) {
        streamingAead.newDecryptingStream(input, associatedData).use { decrypted ->
            decrypted.copyTo(output)
        }
    }

    private fun createStreamingAead(): StreamingAead {
        StreamingAeadConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREF_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        return handle.getPrimitive(StreamingAead::class.java)
    }

    companion object {
        private const val KEYSET_NAME = "vault_streaming_keyset"
        private const val PREF_FILE = "vault_tink_prefs"
        private const val MASTER_KEY_URI = "android-keystore://docs_office_vault_master"
    }
}
