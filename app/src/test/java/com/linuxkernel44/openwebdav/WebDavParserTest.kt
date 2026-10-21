package com.linuxkernel44.openwebdav

import com.linuxkernel44.openwebdav.data.WebDavParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavParserTest {
    private val xml = """<?xml version="1.0" encoding="utf-8"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>/links/</d:href>
    <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
  </d:response>
  <d:response>
    <d:href>/links/My%20Movie%20(2020).mkv</d:href>
    <d:propstat><d:prop>
      <d:resourcetype/>
      <d:getcontentlength>1234567</d:getcontentlength>
      <d:getlastmodified>Wed, 09 Sep 2026 10:30:00 GMT</d:getlastmodified>
      <d:getcontenttype>video/x-matroska</d:getcontenttype>
    </d:prop></d:propstat>
  </d:response>
  <D:response xmlns:D="DAV:">
    <D:href>/links/Season%201/</D:href>
    <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype><D:owner><D:href>/someone</D:href></D:owner></D:prop></D:propstat>
  </D:response>
</d:multistatus>"""

    @Test
    fun parsesFilesAndFolders() {
        val result = WebDavParser.parse(xml.byteInputStream())
        assertEquals(3, result.size)

        val folder = result[0]
        assertEquals("/links/", folder.href)
        assertTrue(folder.isCollection)

        val file = result[1]
        assertEquals("/links/My%20Movie%20(2020).mkv", file.href)
        assertFalse(file.isCollection)
        assertEquals(1234567L, file.size)
        assertEquals("video/x-matroska", file.contentType)
        assertEquals(1788949800000L, file.modified)
    }

    @Test
    fun ignoresNestedHrefInsideProperties() {
        val result = WebDavParser.parse(xml.byteInputStream())
        assertEquals("/links/Season%201/", result[2].href)
        assertNull(result[2].size)
        assertNotNull(result[2])
    }

    @Test
    fun emptyMultistatusGivesEmptyList() {
        val result = WebDavParser.parse("""<d:multistatus xmlns:d="DAV:"/>""".byteInputStream())
        assertTrue(result.isEmpty())
    }
}
