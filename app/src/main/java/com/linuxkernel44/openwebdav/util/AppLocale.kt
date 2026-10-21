package com.linuxkernel44.openwebdav.util

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.linuxkernel44.openwebdav.data.LanguagePref

object AppLocale {
    /** Language code the UI will use for [pref]. Automatic means French on French devices, English otherwise. */
    fun resolve(pref: LanguagePref): String = when (pref) {
        LanguagePref.EN -> "en"
        LanguagePref.FR -> "fr"
        LanguagePref.AUTO -> {
            val systemPrimary = Resources.getSystem().configuration.locales.get(0)
            if (systemPrimary?.language == "fr") "fr" else "en"
        }
    }

    fun apply(pref: LanguagePref) {
        val tag = if (resolve(pref) == "fr") "fr-FR" else "en-US"
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != tag) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }
}
