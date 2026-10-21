package com.linuxkernel44.openwebdav.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Turns whatever the user typed into a clean base URL, or null if it can't be used.
 * A missing scheme defaults to https; plain http is only accepted when [allowCleartext] is set.
 */
fun normalizeServerUrl(input: String, allowCleartext: Boolean = false): HttpUrl? {
    var text = input.trim()
    if (text.isEmpty()) return null
    if (!text.contains("://")) text = "https://$text"
    val url = text.toHttpUrlOrNull() ?: return null
    if (!url.isHttps && !allowCleartext) return null
    return url.newBuilder()
        .username("")
        .password("")
        .query(null)
        .fragment(null)
        .build()
        .withTrailingSlash()
}

fun HttpUrl.withTrailingSlash(): HttpUrl =
    if (encodedPath.endsWith("/")) this else newBuilder().addPathSegment("").build()

/** The folder containing this URL; the root stays the root. */
fun HttpUrl.parentUrl(): HttpUrl {
    val segments = pathSegments.filter { it.isNotEmpty() }
    val builder = newBuilder().encodedPath("/")
    segments.dropLast(1).forEach { builder.addPathSegment(it) }
    return builder.addPathSegment("").build()
}

/** Path segments of this URL with empty ones removed (decoded). */
fun HttpUrl.cleanSegments(): List<String> = pathSegments.filter { it.isNotEmpty() }

/** Same URL with the credentials embedded, as expected by players that can't set headers. */
fun HttpUrl.withCredentials(username: String, password: String): HttpUrl =
    newBuilder().username(username).password(password).build()
