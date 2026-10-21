package com.linuxkernel44.openwebdav.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.OutOfQuotaPolicy
import com.linuxkernel44.openwebdav.OpenWebDavApp
import com.linuxkernel44.openwebdav.R
import com.linuxkernel44.openwebdav.util.FileTypes
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException

/** Downloads one file into the public Downloads folder, with a foreground notification. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL)?.toHttpUrlOrNull() ?: return Result.failure()
        val name = inputData.getString(KEY_NAME) ?: return Result.failure()
        val credentials = (applicationContext as OpenWebDavApp).credentialStore.load() ?: return Result.failure()
        val client = WebDavClient(credentials)

        setForeground(foregroundInfo(name, -1))
        val target = openTarget(applicationContext, name, FileTypes.mimeOf(name, inputData.getString(KEY_MIME)))
        var lastUpdate = 0L
        return try {
            target.out.use { out ->
                client.download(url, out) { done, total ->
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 700) {
                        lastUpdate = now
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        notificationManager().notify(notificationId(), buildProgress(name, percent))
                    }
                }
            }
            target.commit()
            notifyDone(name, success = true)
            Result.success()
        } catch (e: CancellationException) {
            target.abort()
            throw e
        } catch (e: Exception) {
            target.abort()
            notifyDone(name, success = false)
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(inputData.getString(KEY_NAME).orEmpty(), -1)

    private fun foregroundInfo(name: String, percent: Int): ForegroundInfo {
        val notification = buildProgress(name, percent)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(notificationId(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId(), notification)
        }
    }

    private fun buildProgress(name: String, percent: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(name)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .addAction(
                0,
                applicationContext.getString(R.string.cancel),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
            )
            .build()

    private fun notifyDone(name: String, success: Boolean) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(name)
            .setContentText(
                applicationContext.getString(if (success) R.string.download_complete else R.string.download_failed),
            )
            .setAutoCancel(true)
        if (success) {
            val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.setContentIntent(
                android.app.PendingIntent.getActivity(
                    applicationContext, 0, intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        runCatching { manager.notify(notificationId(), builder.build()) }
    }

    private fun notificationManager() =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun notificationId() = (id.hashCode() and 0x7fffffff) % 100_000 + 1000

    private class Target(val out: OutputStream, val commit: () -> Unit, val abort: () -> Unit)

    private fun openTarget(context: Context, name: String, mime: String): Target {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/OpenWebDAV")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw java.io.IOException("Could not create download entry")
            val out = resolver.openOutputStream(uri) ?: throw java.io.IOException("Could not open download entry")
            return Target(
                out,
                commit = {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                },
                abort = { resolver.delete(uri, null, null) },
            )
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OpenWebDAV")
        dir.mkdirs()
        val file = uniqueFile(dir, name)
        return Target(
            FileOutputStream(file),
            commit = { MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null) },
            abort = { file.delete() },
        )
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        while (file.exists()) {
            file = File(dir, "$base ($n)$ext")
            n++
        }
        return file
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        private const val KEY_URL = "url"
        private const val KEY_NAME = "name"
        private const val KEY_MIME = "mime"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.download_channel_description) }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        fun enqueue(context: Context, entry: DavEntry) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_URL, entry.url.toString())
                        .putString(KEY_NAME, entry.name)
                        .putString(KEY_MIME, entry.contentType)
                        .build(),
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("download-${entry.url}", ExistingWorkPolicy.KEEP, request)
        }
    }
}
