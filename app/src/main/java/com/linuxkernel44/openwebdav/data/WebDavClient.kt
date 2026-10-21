package com.linuxkernel44.openwebdav.data

import com.linuxkernel44.openwebdav.util.cleanSegments
import com.linuxkernel44.openwebdav.util.withTrailingSlash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Credentials as BasicAuth
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xml.sax.SAXException
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class WebDavClient(
    private val credentials: Credentials,
    baseHttp: OkHttpClient = sharedHttp,
) {
    private val host = credentials.baseUrl.toHttpUrl().host
    private val authHeader = BasicAuth.basic(credentials.username, credentials.password)

    // The header is only attached to requests for the configured host; OkHttp also drops it on
    // cross-host redirects, so the password never reaches a CDN or another server.
    private val http = baseHttp.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == host) {
                chain.proceed(request.newBuilder().header("Authorization", authHeader).build())
            } else {
                chain.proceed(request)
            }
        }
        .build()

    /** Lists the direct children of [url]. */
    suspend fun list(url: HttpUrl): List<DavEntry> {
        val request = Request.Builder()
            .url(url.withTrailingSlash())
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
            .header("Depth", "1")
            .build()
        try {
            return http.newCall(request).await { response ->
                when (response.code) {
                    200, 207 -> toEntries(response.request.url, WebDavParser.parse(response.body!!.byteStream()))
                    401, 403 -> throw DavError.Auth()
                    404 -> throw DavError.NotFound()
                    else -> throw DavError.Http(response.code)
                }
            }
        } catch (e: DavError) {
            throw e
        } catch (e: SAXException) {
            throw DavError.Parse(e)
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw DavError.Network(e)
        }
    }

    /** Streams the file at [url] into [out], reporting `(bytesDone, bytesTotal)`; total is -1 if unknown. */
    suspend fun download(url: HttpUrl, out: OutputStream, onProgress: (Long, Long) -> Unit) {
        val request = Request.Builder().url(url).get().build()
        try {
            http.newCall(request).await { response ->
                when (response.code) {
                    200 -> Unit
                    401, 403 -> throw DavError.Auth()
                    404 -> throw DavError.NotFound()
                    else -> throw DavError.Http(response.code)
                }
                val body = response.body!!
                val total = body.contentLength()
                val buffer = ByteArray(BUFFER_SIZE)
                var done = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        onProgress(done, total)
                    }
                }
            }
        } catch (e: DavError) {
            throw e
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw DavError.Network(e)
        }
    }

    private fun toEntries(dir: HttpUrl, responses: List<RawResponse>): List<DavEntry> {
        val dirSegments = dir.cleanSegments()
        return responses.mapNotNull { raw ->
            val resolved = dir.resolve(raw.href) ?: return@mapNotNull null
            val segments = resolved.cleanSegments()
            if (segments == dirSegments) return@mapNotNull null // the folder itself
            val name = segments.lastOrNull() ?: return@mapNotNull null
            DavEntry(
                name = name,
                url = if (raw.isCollection) resolved.withTrailingSlash() else resolved,
                isDirectory = raw.isCollection,
                size = raw.size.takeUnless { raw.isCollection },
                modified = raw.modified,
                contentType = raw.contentType,
            )
        }
    }

    /** Runs the blocking call off the main thread and cancels it as soon as the coroutine is cancelled. */
    private suspend fun <T> Call.await(block: (Response) -> T): T = coroutineScope {
        val call = this@await
        val watcher = launch(Dispatchers.Default) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            withContext(Dispatchers.IO) { call.execute().use(block) }
        } finally {
            watcher.cancel()
        }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:getcontenttype/></d:prop></d:propfind>"""

        val sharedHttp: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
