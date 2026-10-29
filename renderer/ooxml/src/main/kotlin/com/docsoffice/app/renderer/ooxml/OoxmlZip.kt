package com.docsoffice.app.renderer.ooxml

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Hardened OOXML zip access: entry count, declared size, streamed byte budget,
 * and compression-ratio checks. Macros / embedded executables are never executed.
 */
internal object OoxmlZip {
    const val MAX_ENTRY_BYTES: Long = 32L * 1024L * 1024L
    const val MAX_TOTAL_UNCOMPRESSED: Long = 96L * 1024L * 1024L
    const val MAX_ENTRIES: Int = 3_000
    const val MAX_COMPRESSION_RATIO: Long = 100L

    private val totalBytesRead = ThreadLocal.withInitial { 0L }

    fun open(file: File): ZipFile = ZipFile(file)

    fun resetBudget() {
        totalBytesRead.set(0L)
    }

    fun readEntryText(zip: ZipFile, name: String): String? {
        val entry = zip.getEntry(name) ?: return null
        if (!isEntrySafe(entry)) return null
        return openEntry(zip, name)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }

    fun openEntry(zip: ZipFile, name: String): InputStream? {
        val entry = zip.getEntry(name) ?: return null
        if (!isEntrySafe(entry)) return null
        val raw = zip.getInputStream(entry) ?: return null
        return BudgetedInputStream(raw, entry)
    }

    fun listEntries(zip: ZipFile): List<String> {
        return zip.entries().asSequence().take(MAX_ENTRIES).map { it.name }.toList()
    }

    fun resolveEntry(zip: ZipFile, relativePath: String): String? {
        val normalized = relativePath.removePrefix("/").removePrefix("./")
        val candidates = listOf(
            normalized,
            "xl/$normalized",
            "ppt/$normalized",
            "word/$normalized",
            normalized.removePrefix("xl/"),
            normalized.removePrefix("ppt/"),
            normalized.removePrefix("word/"),
        )
        return candidates.firstOrNull { zip.getEntry(it) != null }
            ?: listEntries(zip).firstOrNull {
                it.endsWith(normalized.substringAfterLast('/'), ignoreCase = true)
            }
    }

    fun resolveRelative(zip: ZipFile, baseDir: String, target: String): String? {
        if (target.startsWith("/")) {
            return resolveEntry(zip, target.removePrefix("/"))
        }
        val base = baseDir.trimEnd('/') + "/"
        val combined = base + target
        val segments = combined.split('/')
        val stack = ArrayDeque<String>()
        for (segment in segments) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(segment)
            }
        }
        val path = stack.joinToString("/")
        return if (zip.getEntry(path) != null) path else resolveEntry(zip, path)
    }

    fun extractEntry(zip: ZipFile, entryName: String, dest: File): Boolean {
        val entry = zip.getEntry(entryName) ?: return false
        if (!isEntrySafe(entry)) return false
        return try {
            dest.parentFile?.mkdirs()
            openEntry(zip, entryName)?.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var written = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        written += n
                        if (written > MAX_ENTRY_BYTES) {
                            dest.delete()
                            return false
                        }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: return false
            dest.exists() && dest.length() > 0L
        } catch (_: Throwable) {
            dest.delete()
            false
        }
    }

    fun parseRelationships(relsXml: String): Map<String, Rel> {
        val results = linkedMapOf<String, Rel>()
        Regex("""<Relationship\b[^>]*/?>""")
            .findAll(relsXml)
            .forEach { match ->
                val tag = match.value
                val id = attr(tag, "Id") ?: return@forEach
                val type = attr(tag, "Type").orEmpty()
                val target = attr(tag, "Target") ?: return@forEach
                results[id] = Rel(id = id, type = type, target = target)
            }
        return results
    }

    fun isEntrySafe(entry: ZipEntry): Boolean {
        if (entry.isDirectory) return false
        val declared = entry.size
        val compressed = entry.compressedSize
        if (declared > MAX_ENTRY_BYTES) return false
        if (declared > 0 && compressed > 0 && declared / compressed > MAX_COMPRESSION_RATIO) {
            return false
        }
        return true
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""\b$name="([^"]+)"""").find(tag)?.groupValues?.get(1)

    data class Rel(val id: String, val type: String, val target: String)

    private class BudgetedInputStream(
        private val delegate: InputStream,
        private val entry: ZipEntry,
    ) : InputStream() {
        private var entryBytes = 0L

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) account(1)
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) account(n.toLong())
            return n
        }

        private fun account(n: Long) {
            entryBytes += n
            if (entryBytes > MAX_ENTRY_BYTES) {
                throw SecurityException("OOXML entry exceeds size budget: ${entry.name}")
            }
            val total = (totalBytesRead.get() ?: 0L) + n
            totalBytesRead.set(total)
            if (total > MAX_TOTAL_UNCOMPRESSED) {
                throw SecurityException("OOXML package exceeds total uncompressed budget")
            }
            val declared = entry.size
            if (declared > 0 && entryBytes > declared + 1024) {
                throw SecurityException("OOXML entry larger than declared size")
            }
        }

        override fun close() = delegate.close()
    }
}
