package com.linuxkernel44.openwebdav.util

import android.webkit.MimeTypeMap

enum class FileKind { FOLDER, VIDEO, AUDIO, IMAGE, SUBTITLE, ARCHIVE, OTHER }

object FileTypes {
    private val video = setOf(
        "mp4", "m4v", "mkv", "avi", "mov", "wmv", "flv", "f4v", "webm", "mpg", "mpeg", "mpe",
        "ts", "m2ts", "mts", "3gp", "3g2", "ogv", "vob", "divx", "asf", "rm", "rmvb", "mxf",
    )
    private val audio = setOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "oga", "opus", "wma", "aiff", "alac", "mka")
    private val image = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
    private val subtitle = setOf("srt", "ass", "ssa", "sub", "vtt", "idx", "sup", "smi")
    private val archive = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso")

    fun kindOf(name: String, isDirectory: Boolean = false, contentType: String? = null): FileKind {
        if (isDirectory) return FileKind.FOLDER
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in video -> FileKind.VIDEO
            ext in audio -> FileKind.AUDIO
            ext in image -> FileKind.IMAGE
            ext in subtitle -> FileKind.SUBTITLE
            ext in archive -> FileKind.ARCHIVE
            contentType?.startsWith("video/") == true -> FileKind.VIDEO
            contentType?.startsWith("audio/") == true -> FileKind.AUDIO
            contentType?.startsWith("image/") == true -> FileKind.IMAGE
            else -> FileKind.OTHER
        }
    }

    fun mimeOf(name: String, contentType: String? = null): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return contentType?.substringBefore(';')?.trim()?.takeIf { it.contains('/') && it != "application/octet-stream" }
            ?: "application/octet-stream"
    }
}
