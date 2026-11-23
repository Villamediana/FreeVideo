package com.villamediana.sound.tube

import android.content.Context
import com.villamediana.sound.R
import com.villamediana.sound.online.NewPipeHttp
import java.util.Locale

/**
 * The language YouTube is asked for: the one the app is showing (the strings Android picked — pt, es,
 * en — not the phone's own, which may be one the app has no words for), with the phone's country.
 */
object ContentLanguage {
    fun of(context: Context): Locale {
        val language = context.getString(R.string.content_language).ifBlank { "pt" }
        val phone = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val country = phone.country.takeIf { it.length == 2 } ?: Locale.getDefault().country.takeIf { it.length == 2 } ?: "BR"
        return Locale(language, country)
    }

    /** YouTube follows the app's language (at start and whenever the language changes). */
    fun apply(context: Context) = NewPipeHttp.relocalize(of(context))
}
