package com.linuxkernel44.openwebdav.data

import okhttp3.HttpUrl

data class Credentials(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    override fun toString() = "Credentials(baseUrl=$baseUrl, username=$username, password=***)"
}

data class DavEntry(
    val name: String,
    val url: HttpUrl,
    val isDirectory: Boolean,
    val size: Long?,
    val modified: Long?,
    val contentType: String?,
)

/** Everything that can go wrong while talking to the server. */
sealed class DavError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    class Auth : DavError("Authentication failed")
    class NotFound : DavError("Not found")
    class Http(val code: Int) : DavError("HTTP $code")
    class Network(cause: Throwable) : DavError(cause.message, cause)
    class Parse(cause: Throwable) : DavError(cause.message, cause)
}
