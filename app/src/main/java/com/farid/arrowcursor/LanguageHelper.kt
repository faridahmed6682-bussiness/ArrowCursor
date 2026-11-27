package com.farid.arrowcursor

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageHelper {

    fun wrapContext(context: Context): Context {
        val code = CursorSettings(context).language
        if (code == "system") {
            return context
        }
        val locale = when (code) {
            "en" -> Locale.ENGLISH
            "bn" -> Locale("bn")
            else -> return context
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun setLanguage(context: Context, code: String) {
        CursorSettings(context).language = code
    }
}
