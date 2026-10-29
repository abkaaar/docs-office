package com.docsoffice.app.core.crypto

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Streaming AEAD vault encryption backed by Tink + Android Keystore.
 */
interface VaultCrypto {
    fun isAvailable(): Boolean

    fun encrypt(input: InputStream, output: OutputStream, associatedData: ByteArray = ByteArray(0))

    fun decrypt(input: InputStream, output: OutputStream, associatedData: ByteArray = ByteArray(0))

    fun encryptFile(source: File, destination: File, associatedData: ByteArray = ByteArray(0)) {
        source.inputStream().use { input ->
            destination.outputStream().use { output ->
                encrypt(input, output, associatedData)
            }
        }
    }

    fun decryptFile(source: File, destination: File, associatedData: ByteArray = ByteArray(0)) {
        source.inputStream().use { input ->
            destination.outputStream().use { output ->
                decrypt(input, output, associatedData)
            }
        }
    }
}
