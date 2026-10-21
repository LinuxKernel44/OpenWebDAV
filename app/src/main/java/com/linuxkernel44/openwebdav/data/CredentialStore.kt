package com.linuxkernel44.openwebdav.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Keeps the URL, username and password encrypted with a key held by the Android Keystore. */
class CredentialStore(private val context: Context) {

    private val prefs: SharedPreferences? by lazy { openPrefs() }

    fun load(): Credentials? {
        val prefs = prefs ?: return null
        val url = prefs.getString(KEY_URL, null) ?: return null
        val user = prefs.getString(KEY_USER, null) ?: return null
        val pass = prefs.getString(KEY_PASS, null) ?: return null
        return Credentials(url, user, pass)
    }

    fun save(credentials: Credentials) {
        prefs?.edit()
            ?.putString(KEY_URL, credentials.baseUrl)
            ?.putString(KEY_USER, credentials.username)
            ?.putString(KEY_PASS, credentials.password)
            ?.apply()
    }

    fun clear() {
        prefs?.edit()?.clear()?.commit()
    }

    private fun openPrefs(): SharedPreferences? = try {
        create()
    } catch (e: Exception) {
        // The keystore key can be lost (restore, lock screen reset); start over rather than crash.
        context.deleteSharedPreferences(FILE)
        try {
            create()
        } catch (e2: Exception) {
            null
        }
    }

    private fun create(): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val FILE = "secure_credentials"
        const val KEY_URL = "url"
        const val KEY_USER = "username"
        const val KEY_PASS = "password"
    }
}
