package com.docsoffice.app.renderer.ooxml

object XmlText {
    private val tagRegex = Regex("<[^>]+>")
    private val spaceRegex = Regex("\\s+")

    fun decodeEntities(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#10;", "\n")
            .replace("&#13;", "\r")
    }

    fun stripTags(xml: String): String {
        return decodeEntities(
            xml
                .replace("</w:p>", "\n")
                .replace("</a:p>", "\n")
                .replace("<w:tab/>", "\t")
                .replace("<w:br/>", "\n")
                .replace("<a:br/>", "\n")
                .replace(tagRegex, ""),
        )
            .replace(spaceRegex, " ")
            .trim()
    }

    fun extractBetween(xml: String, openTag: String, closeTag: String): List<String> {
        val results = mutableListOf<String>()
        var index = 0
        while (true) {
            val start = xml.indexOf(openTag, index)
            if (start < 0) break
            val contentStart = start + openTag.length
            val end = xml.indexOf(closeTag, contentStart)
            if (end < 0) break
            results += xml.substring(contentStart, end)
            index = end + closeTag.length
        }
        return results
    }

    /** Preserve paragraph breaks for slide / shape text. */
    fun paragraphsFromDrawingXml(xml: String): List<String> {
        val parts = extractBetween(xml, "<a:p", "</a:p>")
        return parts.map { chunk -> stripTags("<a:p$chunk</a:p>") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
