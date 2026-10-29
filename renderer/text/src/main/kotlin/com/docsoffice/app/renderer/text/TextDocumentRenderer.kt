package com.docsoffice.app.renderer.text

import com.docsoffice.app.renderer.api.DocBlock
import com.docsoffice.app.renderer.api.DocumentRenderer
import com.docsoffice.app.renderer.api.DocumentSourceHandle
import com.docsoffice.app.renderer.api.OpenFailure
import com.docsoffice.app.renderer.api.RenderSession
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.flow

class TextDocumentRenderer : DocumentRenderer {
    override val supportedMimeTypes: Set<String> = setOf(
        "text/plain",
        "text/markdown",
        "text/csv",
        "application/json",
        "text/xml",
        "text/html",
    )

    override suspend fun open(source: DocumentSourceHandle, password: String?): RenderSession {
        val file = runCatching { File(URI(source.uri)) }.getOrNull()
            ?: throw OpenFailure.Unsupported
        if (!file.exists()) throw OpenFailure.Corrupt
        if (file.length() > 20L * 1024L * 1024L) throw OpenFailure.TooLarge
        val lines = TextLineLoader.loadLines(file)
        return RenderSession.Reflow(
            blocks = flow {
                lines.forEach { emit(DocBlock(it)) }
            },
        )
    }
}

object TextLineLoader {
    private const val MAX_LINE = 8_000
    private const val MAX_LINES = 50_000
    /** Default page size for incremental large-file reading. */
    const val DEFAULT_CHUNK_LINES = 2_000
    private const val DEFAULT_CHUNK_BYTES = 512L * 1024L

    data class TextChunk(
        val lines: List<String>,
        /** Absolute byte offset to resume from (after complete lines). */
        val nextByteOffset: Long,
        val exhausted: Boolean,
        val encoding: String = "UTF-8",
    )

    fun loadLines(file: File): List<String> {
        return loadLinesChunk(file, startByte = 0L, maxLines = MAX_LINES).lines
    }

    /**
     * Reads a bounded chunk of lines starting at [startByte].
     * Syncs to a UTF-8 character boundary when [startByte] > 0 so multibyte
     * characters are not corrupted at chunk edges. [nextByteOffset] advances
     * only past complete lines (except at EOF), so resume never re-reads the
     * entire file.
     */
    fun loadLinesChunk(
        file: File,
        startByte: Long,
        maxLines: Int = DEFAULT_CHUNK_LINES,
        maxBytes: Long = DEFAULT_CHUNK_BYTES,
    ): TextChunk {
        val length = file.length()
        if (!file.exists() || startByte >= length) {
            return TextChunk(emptyList(), startByte.coerceAtLeast(length), exhausted = true)
        }

        FileInputStream(file).use { fis ->
            val channel = fis.channel
            var pos = startByte.coerceAtLeast(0L)
            channel.position(pos)
            if (pos > 0) {
                // Skip UTF-8 continuation bytes (10xxxxxx) so decoding starts on a lead byte.
                val one = ByteArray(1)
                while (pos < length) {
                    if (fis.read(one) != 1) break
                    pos++
                    if (one[0].toInt() and 0xC0 != 0x80) {
                        pos--
                        channel.position(pos)
                        break
                    }
                }
            }

            val budget = minOf(maxBytes, length - pos).toInt().coerceAtLeast(0)
            val buffer = ByteArray(budget)
            var filled = 0
            while (filled < budget) {
                val n = fis.read(buffer, filled, budget - filled)
                if (n < 0) break
                filled += n
            }

            val atEof = pos + filled >= length
            var end = filled
            if (!atEof && filled > 0) {
                // Keep only complete lines so the next chunk can resume cleanly.
                while (end > 0 && buffer[end - 1] != '\n'.code.toByte()) {
                    end--
                }
                if (end == 0) {
                    // No newline in this window — take the whole window to make progress.
                    end = filled
                }
            }

            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            val text = decoder.decode(java.nio.ByteBuffer.wrap(buffer, 0, end)).toString()

            val rawLines = ArrayList<String>(minOf(maxLines, 256))
            var start = 0
            var i = 0
            while (i < text.length && rawLines.size < maxLines) {
                when (text[i]) {
                    '\n' -> {
                        var line = text.substring(start, i)
                        if (line.endsWith('\r')) line = line.dropLast(1)
                        rawLines += truncateLine(line)
                        start = i + 1
                    }
                }
                i++
            }
            if (atEof && start <= text.length && rawLines.size < maxLines) {
                var line = text.substring(start)
                if (line.endsWith('\r')) line = line.dropLast(1)
                // Include trailing content without a final newline at EOF.
                if (line.isNotEmpty() || start < text.length) {
                    rawLines += truncateLine(line)
                }
            }

            // If we stopped early due to maxLines, advance only past emitted lines —
            // even when the whole file fit in the read buffer (atEof).
            val consumedBytes = if (rawLines.size >= maxLines) {
                val joined = rawLines.joinToString("\n") + "\n"
                joined.toByteArray(Charsets.UTF_8).size.coerceAtMost(end)
            } else {
                end
            }

            val next = (pos + consumedBytes).coerceAtMost(length)
            return TextChunk(
                lines = rawLines,
                nextByteOffset = next,
                exhausted = next >= length,
            )
        }
    }

    private fun truncateLine(line: String): String =
        if (line.length > MAX_LINE) line.take(MAX_LINE) + "…" else line

    /** Lightweight CSV split respecting simple quoted fields. */
    fun parseCsvRows(file: File, maxRows: Int = 5_000, maxCols: Int = 64): List<List<String>> {
        return loadLinesChunk(file, 0L, maxLines = maxRows).lines.take(maxRows).map { line ->
            splitCsvLine(line).take(maxCols)
        }
    }

    fun splitCsvLine(line: String): List<String> {
        val result = ArrayList<String>(8)
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result += current.toString()
        return result
    }
}
