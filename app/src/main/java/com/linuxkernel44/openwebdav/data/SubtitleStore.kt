package com.linuxkernel44.openwebdav.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.linuxkernel44.openwebdav.util.FileTypes
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * VLC only accepts subtitles as local file paths, so a remote subtitle is saved to a public
 * folder VLC can read before the video starts.
 */
object SubtitleStore {
    private const val FOLDER = "OpenWebDAV/Subtitles"
    private const val MAX_BYTES = 5L * 1024 * 1024

    /** Returns the absolute path of the saved subtitle, or null if it couldn't be saved. */
    suspend fun save(context: Context, client: WebDavClient, subtitle: DavEntry): String? {
        if (subtitle.size != null && subtitle.size > MAX_BYTES) return null
        val bytes = ByteArrayOutputStream()
        client.download(subtitle.url, bytes) { _, _ -> }
        val data = bytes.toByteArray()
        if (data.size > MAX_BYTES) return null
        return if (Build.VERSION.SDK_INT >= 29) saveWithMediaStore(context, subtitle.name, data)
        else saveLegacy(context, subtitle.name, data)
    }

    private fun publicDir(): File =
        @Suppress("DEPRECATION")
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)

    private fun saveWithMediaStore(context: Context, name: String, data: ByteArray): String? {
        val resolver = context.contentResolver
        val relative = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"
        // Replace our own earlier copy so the file name (and therefore the path) stays predictable.
        resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(name, relative),
        )
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, FileTypes.mimeOf(name))
            put(MediaStore.Downloads.RELATIVE_PATH, relative)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(data) } ?: return null
        val savedName = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: name
        return File(publicDir(), savedName).path
    }

    private fun saveLegacy(context: Context, name: String, data: ByteArray): String? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val dir = publicDir().apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(data)
        return file.path
    }
}
