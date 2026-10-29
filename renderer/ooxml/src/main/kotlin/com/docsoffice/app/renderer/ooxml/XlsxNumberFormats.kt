package com.docsoffice.app.renderer.ooxml

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Formats Excel serial values using built-in / custom numFmt patterns.
 * Does **not** recalculate formulas — only formats a numeric or string raw value.
 *
 * Unsupported complex patterns (conditional sections, locale currency ids `[$$-409]`, etc.)
 * fall back to a plain decimal / ISO date rather than inventing misleading output.
 */
object XlsxNumberFormats {
    private val us = Locale.US
    private val symbols = DecimalFormatSymbols(us)

    /** Excel epoch: 1899-12-30 (Lotus 1900 leap-year bug preserved). */
    private val excelEpoch = LocalDate.of(1899, 12, 30)

    fun format(
        raw: String,
        numFmtId: Int,
        customPattern: String?,
        isDateStyle: Boolean = false,
    ): String {
        if (raw.isBlank()) return ""
        if (numFmtId == 49) return raw // @ text
        val pattern = customPattern ?: builtinPattern(numFmtId) ?: "General"
        val number = raw.toDoubleOrNull()
        if (number == null) return raw
        if (pattern.equals("General", ignoreCase = true) || numFmtId == 0) {
            return formatGeneral(number)
        }

        val lower = pattern.lowercase(us)
        val looksLikeDate = isDateStyle ||
            DATE_HINT.containsMatchIn(lower)

        return when {
            looksLikeDate && isLikelyExcelDateSerial(number) -> formatDateTime(number, numFmtId, pattern)
            pattern.contains('%') -> formatPercent(number, pattern)
            else -> formatDecimal(number, pattern)
        }
    }

    fun builtinPattern(id: Int): String? = when (id) {
        0 -> "General"
        1 -> "0"
        2 -> "0.00"
        3 -> "#,##0"
        4 -> "#,##0.00"
        9 -> "0%"
        10 -> "0.00%"
        11 -> "0.00E+00"
        14 -> "m/d/yy"
        15 -> "d-mmm-yy"
        16 -> "d-mmm"
        17 -> "mmm-yy"
        18 -> "h:mm AM/PM"
        19 -> "h:mm:ss AM/PM"
        20 -> "h:mm"
        21 -> "h:mm:ss"
        22 -> "m/d/yy h:mm"
        37 -> "#,##0;(#,##0)"
        38 -> "#,##0;(#,##0)"
        39 -> "#,##0.00;(#,##0.00)"
        40 -> "#,##0.00;(#,##0.00)"
        45 -> "mm:ss"
        49 -> "@"
        else -> null
    }

    private val DATE_HINT = Regex("""[ydh]|am/pm|mm:ss|:mm""")

    private fun formatGeneral(n: Double): String {
        if (n == floor(n) && abs(n) < 1e15) return n.toLong().toString()
        return DecimalFormat("0.##########", symbols).format(n)
    }

    private fun formatPercent(n: Double, pattern: String): String {
        val pct = n * 100.0
        val df = DecimalFormat(if (pattern.contains("0.00")) "0.00" else "0", symbols)
        return df.format(pct) + "%"
    }

    private fun formatDecimal(n: Double, pattern: String): String {
        val first = pattern.substringBefore(';')
        val cleaned = first
            .replace(Regex("""\[[^\]]+]"""), "")
            .replace("\"", "")
            .replace("_)", "")
            .replace("_(", "")
            .replace("_", "")
            .replace("*", "")
            .replace('?', '#')
            .trim()
            .ifBlank { "0.##########" }
        val currencyPrefix = when {
            first.contains("€") -> "€"
            first.contains("£") -> "£"
            first.contains("$") && !first.contains("E+") -> "$"
            else -> ""
        }
        val javaPattern = cleaned.replace("$", "").replace("€", "").replace("£", "")
        return try {
            currencyPrefix + DecimalFormat(javaPattern, symbols).format(n)
        } catch (_: IllegalArgumentException) {
            formatGeneral(n)
        }
    }

    private fun isLikelyExcelDateSerial(n: Double): Boolean = n in 1.0..2_958_465.0

    private fun formatDateTime(serial: Double, numFmtId: Int, pattern: String): String {
        val days = floor(serial).toLong()
        val fraction = serial - days
        val date = excelEpoch.plusDays(days)
        val totalSeconds = (fraction * 86_400).roundToLong().coerceIn(0, 86_399)
        val time = LocalTime.ofSecondOfDay(totalSeconds)
        val dateTime = LocalDateTime.of(date, time)

        val formatter = when (numFmtId) {
            14 -> DateTimeFormatter.ofPattern("M/d/yy", us)
            15 -> DateTimeFormatter.ofPattern("d-MMM-yy", us)
            16 -> DateTimeFormatter.ofPattern("d-MMM", us)
            17 -> DateTimeFormatter.ofPattern("MMM-yy", us)
            18 -> DateTimeFormatter.ofPattern("h:mm a", us)
            19 -> DateTimeFormatter.ofPattern("h:mm:ss a", us)
            20 -> DateTimeFormatter.ofPattern("H:mm", us)
            21 -> DateTimeFormatter.ofPattern("H:mm:ss", us)
            22 -> DateTimeFormatter.ofPattern("M/d/yy H:mm", us)
            45 -> DateTimeFormatter.ofPattern("mm:ss", us)
            else -> inferFormatter(pattern)
        }
        return runCatching { formatter.format(dateTime) }
            .getOrElse { DateTimeFormatter.ISO_LOCAL_DATE.format(date) }
    }

    private fun inferFormatter(pattern: String): DateTimeFormatter {
        val lower = pattern.lowercase(us)
        return when {
            lower.contains("yyyy") && lower.contains("mm") && lower.contains("dd") ->
                DateTimeFormatter.ofPattern("yyyy-MM-dd", us)
            lower.contains("h") && lower.contains("mm") && lower.contains("ss") ->
                DateTimeFormatter.ofPattern("H:mm:ss", us)
            lower.contains("h") && lower.contains("mm") ->
                DateTimeFormatter.ofPattern("H:mm", us)
            else -> DateTimeFormatter.ofPattern("M/d/yyyy", us)
        }
    }
}
