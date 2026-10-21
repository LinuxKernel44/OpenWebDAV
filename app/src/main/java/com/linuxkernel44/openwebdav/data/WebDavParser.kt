package com.linuxkernel44.openwebdav.data

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory

/** One `<response>` element of a WebDAV multistatus document. */
data class RawResponse(
    val href: String,
    val isCollection: Boolean,
    val size: Long?,
    val modified: Long?,
    val contentType: String?,
)

object WebDavParser {
    fun parse(input: InputStream): List<RawResponse> {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            // Not every platform implementation knows this feature; entities are not resolved either way.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val handler = MultistatusHandler()
        factory.newSAXParser().parse(InputSource(input), handler)
        return handler.results
    }

    private class MultistatusHandler : DefaultHandler() {
        val results = ArrayList<RawResponse>()

        private val path = ArrayList<String>()
        private val text = StringBuilder()

        private var href: String? = null
        private var isCollection = false
        private var size: Long? = null
        private var modified: Long? = null
        private var contentType: String? = null

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            val name = nameOf(localName, qName)
            path.add(name)
            text.setLength(0)
            if (name == "response") {
                href = null
                isCollection = false
                size = null
                modified = null
                contentType = null
            } else if (name == "collection" && parent(1) == "resourcetype") {
                isCollection = true
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = nameOf(localName, qName)
            val value = text.toString().trim()
            when {
                name == "href" && parent(1) == "response" -> href = value
                parent(1) == "prop" -> when (name) {
                    "getcontentlength" -> size = value.toLongOrNull()
                    "getlastmodified" -> modified = parseDate(value)
                    "getcontenttype" -> contentType = value.ifEmpty { null }
                }
                name == "response" -> href?.takeIf { it.isNotEmpty() }?.let {
                    results += RawResponse(it, isCollection, size, modified, contentType)
                }
            }
            text.setLength(0)
            if (path.isNotEmpty()) path.removeAt(path.lastIndex)
        }

        /** Name of the ancestor [levelsUp] above the element being processed. */
        private fun parent(levelsUp: Int): String? = path.getOrNull(path.lastIndex - levelsUp)

        private fun nameOf(localName: String?, qName: String?): String =
            localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')

        private fun parseDate(value: String): Long? = runCatching {
            java.time.ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
