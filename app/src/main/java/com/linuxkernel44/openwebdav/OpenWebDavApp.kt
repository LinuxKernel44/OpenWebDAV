package com.linuxkernel44.openwebdav

import android.app.Application
import com.linuxkernel44.openwebdav.data.CredentialStore
import com.linuxkernel44.openwebdav.data.DownloadWorker
import com.linuxkernel44.openwebdav.data.SettingsStore
import com.linuxkernel44.openwebdav.util.AppLocale

class OpenWebDavApp : Application() {
    val settings by lazy { SettingsStore(this) }
    val credentialStore by lazy { CredentialStore(this) }

    override fun onCreate() {
        super.onCreate()
        AppLocale.apply(settings.language.value)
        DownloadWorker.createChannel(this)
    }
}
