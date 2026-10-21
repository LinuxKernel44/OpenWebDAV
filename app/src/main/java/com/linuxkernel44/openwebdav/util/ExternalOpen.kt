package com.linuxkernel44.openwebdav.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object ExternalOpen {
    const val VLC_PACKAGE = "org.videolan.vlc"
    private const val PLAY_STORE_APP = "market://details?id=$VLC_PACKAGE"
    private const val PLAY_STORE_WEB = "https://play.google.com/store/apps/details?id=$VLC_PACKAGE"
    private const val F_DROID = "https://f-droid.org/packages/$VLC_PACKAGE/"

    fun isVlcInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(VLC_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Opens [url] in VLC; [subtitlePath] is a local file path, the only form VLC accepts for subtitles. */
    fun playInVlc(context: Context, url: String, title: String, subtitlePath: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setPackage(VLC_PACKAGE)
            .setDataAndTypeAndNormalize(Uri.parse(url), "video/*")
            .putExtra("title", title)
        if (subtitlePath != null) intent.putExtra("subtitles_location", subtitlePath)
        return start(context, intent)
    }

    /** Lets the user pick any app able to open [url]. */
    fun openWithChooser(context: Context, url: String, mime: String, title: String): Boolean {
        val view = Intent(Intent.ACTION_VIEW).setDataAndTypeAndNormalize(Uri.parse(url), mime)
        return start(context, Intent.createChooser(view, title))
    }

    /** Opens the default app for [mime] on [url] without any chooser; false if nothing can handle it. */
    fun openDefault(context: Context, uri: Uri, mime: String, grantRead: Boolean = false): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        if (grantRead) intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(context, intent)
    }

    fun installVlc(context: Context, fDroid: Boolean) {
        if (fDroid) {
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse(F_DROID)))
        } else if (!start(context, Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_APP)))) {
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_WEB)))
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareText(context: Context, text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(context, Intent.createChooser(send, title))
    }

    fun openWebPage(context: Context, url: String) {
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
