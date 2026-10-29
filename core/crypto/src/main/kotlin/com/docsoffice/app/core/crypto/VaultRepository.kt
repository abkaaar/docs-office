package com.docsoffice.app.core.crypto

import android.content.Context
import android.util.Base64
import com.docsoffice.app.core.database.DocumentRepository
import com.docsoffice.app.core.database.VaultItemDao
import com.docsoffice.app.core.database.VaultItemEntity
import com.docsoffice.app.core.files.FileOperations
import com.docsoffice.app.core.files.PathClassifier
import com.docsoffice.app.core.files.SupportedFormats
import com.docsoffice.app.core.model.DocSource
import com.docsoffice.app.core.model.Document
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class VaultItem(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val extension: String,
    val sizeBytes: Long,
    val createdAt: Long,
)

@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: VaultCrypto,
    private val vaultItemDao: VaultItemDao,
    private val documentRepository: DocumentRepository,
    private val fileOperations: FileOperations,
    private val session: VaultSession,
) {
    fun observeItems(): Flow<List<VaultItem>> =
        vaultItemDao.observeAll().map { list ->
            if (!session.isUnlocked()) {
                emptyList()
            } else {
                list.mapNotNull { entity ->
                    runCatching {
                        VaultItem(
                            id = entity.id,
                            displayName = decryptName(entity.encryptedName),
                            mimeType = entity.mimeType,
                            extension = entity.extension,
                            sizeBytes = entity.sizeBytes,
                            createdAt = entity.createdAt,
                        )
                    }.getOrNull()
                }
            }
        }

    fun observeCount(): Flow<Int> = vaultItemDao.observeCount()

    suspend fun moveDocumentToVault(document: Document): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(session.isUnlocked()) { "Vault is locked" }
            check(crypto.isAvailable()) { "Vault encryption unavailable" }
            val source = fileOperations.fileFromUriString(document.uri)
                ?: error("Source file not available")
            val id = UUID.randomUUID().toString()
            val blob = File(vaultDir(), "$id.bin")
            crypto.encryptFile(source, blob, id.toByteArray())
            val encryptedName = encryptName(document.displayName)
            vaultItemDao.upsert(
                VaultItemEntity(
                    id = id,
                    encryptedName = encryptedName,
                    blobPath = blob.absolutePath,
                    mimeType = document.mimeType,
                    extension = document.extension,
                    sizeBytes = document.sizeBytes,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            documentRepository.deleteByIds(listOf(document.id))
            runCatching { source.delete() }
            Unit
        }
    }

    suspend fun openDecrypted(itemId: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            check(session.isUnlocked()) { "Vault is locked" }
            val entity = vaultItemDao.getById(itemId) ?: error("Item not found")
            val blob = File(entity.blobPath)
            check(blob.exists()) { "Vault blob missing" }
            val name = decryptName(entity.encryptedName)
            val out = File(decryptCacheDir(), "${itemId}_$name")
            if (out.exists()) out.delete()
            crypto.decryptFile(blob, out, itemId.toByteArray())
            out
        }
    }

    suspend fun moveOutToLibrary(itemId: String): Result<Document> = withContext(Dispatchers.IO) {
        runCatching {
            check(session.isUnlocked()) { "Vault is locked" }
            val entity = vaultItemDao.getById(itemId) ?: error("Item not found")
            val decrypted = openDecrypted(itemId).getOrThrow()
            val destDir = File(context.getExternalFilesDir(null), "VaultExport").apply { mkdirs() }
            val name = decryptName(entity.encryptedName)
            val dest = File(destDir, name)
            check(fileOperations.copyVerify(decrypted, dest)) { "Export copy failed" }
            val uri = dest.toURI().toString()
            val id = PathClassifier.stableId(uri, dest.length(), dest.lastModified())
            val doc = Document(
                id = id,
                uri = uri,
                displayName = name,
                extension = entity.extension,
                mimeType = entity.mimeType.ifBlank { SupportedFormats.mimeTypeFor(entity.extension) },
                sizeBytes = dest.length(),
                modifiedAt = dest.lastModified(),
                addedAt = System.currentTimeMillis(),
                source = DocSource.OTHER,
            )
            documentRepository.upsertAll(listOf(doc))
            deleteItem(itemId)
            doc
        }
    }

    suspend fun deleteItem(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(session.isUnlocked()) { "Vault is locked" }
            val entity = vaultItemDao.getById(itemId)
            if (entity != null) {
                File(entity.blobPath).delete()
                vaultItemDao.deleteById(itemId)
            }
            File(decryptCacheDir(), itemId).parentFile
            decryptCacheDir().listFiles()?.filter { it.name.startsWith("${itemId}_") }?.forEach {
                it.delete()
            }
            Unit
        }
    }

    fun clearDecryptCache() {
        decryptCacheDir().listFiles()?.forEach { it.delete() }
    }

    private fun vaultDir(): File =
        File(context.filesDir, "vault").apply { mkdirs() }

    private fun decryptCacheDir(): File =
        File(context.cacheDir, "vault_open").apply { mkdirs() }

    private fun encryptName(name: String): String {
        val out = ByteArrayOutputStream()
        crypto.encrypt(ByteArrayInputStream(name.toByteArray(Charsets.UTF_8)), out, NAME_AD)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun decryptName(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val out = ByteArrayOutputStream()
        crypto.decrypt(ByteArrayInputStream(bytes), out, NAME_AD)
        return out.toString(Charsets.UTF_8.name())
    }

    companion object {
        private val NAME_AD = "vault-name".toByteArray()
    }
}
