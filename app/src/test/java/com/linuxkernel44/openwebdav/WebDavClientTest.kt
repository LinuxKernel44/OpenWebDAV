package com.linuxkernel44.openwebdav

import com.linuxkernel44.openwebdav.data.Credentials
import com.linuxkernel44.openwebdav.data.DavError
import com.linuxkernel44.openwebdav.data.WebDavClient
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials as BasicAuth
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class WebDavClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WebDavClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = WebDavClient(Credentials(server.url("/").toString(), "user", "pa:ss"))
    }

    @After
    fun tearDown() = server.shutdown()

    private val multistatus = """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
<d:response><d:href>/dav/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
<d:response><d:href>/dav/Sub%20Folder/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
<d:response><d:href>/dav/film.mp4</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>42</d:getcontentlength></d:prop></d:propstat></d:response>
</d:multistatus>"""

    @Test
    fun listSendsPropfindWithBasicAuthAndSkipsSelf() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus))
        val entries = client.list(server.url("/dav"))

        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("/dav/", request.path)
        assertEquals("1", request.getHeader("Depth"))
        assertEquals(BasicAuth.basic("user", "pa:ss"), request.getHeader("Authorization"))

        assertEquals(listOf("Sub Folder", "film.mp4"), entries.map { it.name })
        assertTrue(entries[0].isDirectory)
        assertTrue(entries[0].url.toString().endsWith("/dav/Sub%20Folder/"))
        assertFalse(entries[1].isDirectory)
        assertEquals(42L, entries[1].size)
    }

    @Test
    fun mapsHttpStatusesToErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(207).setBody("not xml at all"))
        val url = server.url("/dav/")

        try { client.list(url); fail() } catch (e: DavError.Auth) { }
        try { client.list(url); fail() } catch (e: DavError.NotFound) { }
        try { client.list(url); fail() } catch (e: DavError.Http) { assertEquals(503, e.code) }
        try { client.list(url); fail() } catch (e: DavError.Parse) { }
    }

    @Test
    fun unreachableServerIsANetworkError() = runBlocking {
        val url = server.url("/")
        server.shutdown()
        try { client.list(url); fail() } catch (e: DavError.Network) { }
    }

    @Test
    fun downloadStreamsBodyAndReportsProgress() = runBlocking {
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(payload)))
        val out = ByteArrayOutputStream()
        var lastDone = 0L
        client.download(server.url("/f.bin"), out) { done, _ -> lastDone = done }
        assertTrue(payload.contentEquals(out.toByteArray()))
        assertEquals(payload.size.toLong(), lastDone)
    }
}
