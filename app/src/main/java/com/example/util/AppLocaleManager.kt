package com.example.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

data class SupportedLocale(
    val tag: String,
    val displayName: String,
    val nativeName: String,
    val flagEmoji: String
)

object AppLocaleManager {

    val SUPPORTED_LOCALES = listOf(
        SupportedLocale("pt-BR", "Português", "Português (Brasil)", "🇧🇷"),
        SupportedLocale("en-US", "English", "English (United States)", "🇺🇸"),
        SupportedLocale("es-ES", "Español", "Español", "🇪🇸"),
        SupportedLocale("de-DE", "Deutsch", "Deutsch", "🇩🇪"),
        SupportedLocale("fr-FR", "Français", "Français", "🇫🇷"),
        SupportedLocale("it-IT", "Italiano", "Italiano", "🇮🇹"),
        SupportedLocale("ja-JP", "日本語", "日本語", "🇯🇵")
    )

    fun applyLocale(languageTag: String) {
        runCatching {
            if (languageTag.isBlank() || languageTag.equals("auto", ignoreCase = true)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else {
                val appLocale = LocaleListCompat.forLanguageTags(languageTag)
                AppCompatDelegate.setApplicationLocales(appLocale)
            }
        }
    }

    fun getCurrentLanguageTag(): String {
        runCatching {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            if (!appLocales.isEmpty) {
                return appLocales[0]?.toLanguageTag() ?: "pt-BR"
            }
        }
        val defaultTag = Locale.getDefault().toLanguageTag()
        val match = SUPPORTED_LOCALES.firstOrNull { it.tag.equals(defaultTag, ignoreCase = true) }
            ?: SUPPORTED_LOCALES.firstOrNull { it.tag.startsWith(Locale.getDefault().language) }
        return match?.tag ?: "pt-BR"
    }

    fun getLocaleByTag(tag: String): SupportedLocale {
        return SUPPORTED_LOCALES.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
            ?: SUPPORTED_LOCALES.firstOrNull { it.tag.startsWith(tag.take(2), ignoreCase = true) }
            ?: SUPPORTED_LOCALES[0]
    }
}
