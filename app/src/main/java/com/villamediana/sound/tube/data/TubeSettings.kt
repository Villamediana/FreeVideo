package com.villamediana.sound.tube.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tubeStore by preferencesDataStore(name = "tube_settings")

/** FreeVideo's own preferences (theme and highlight color stay in FreeSong's [com.villamediana.sound.data.SettingsStore]). */
data class TubePrefs(
    /** Sharpest video asked for (short side, 720 = "720p"). */
    val maxHeight: Int = 720,
    /** At the end of a video, the next one starts by itself. */
    val autoplay: Boolean = true,
    /** Nothing new goes to the history (and so the feed stops learning). */
    val historyPaused: Boolean = false,
    /** Video keeps its sound when the app goes to the background (instead of pausing). */
    val keepPlayingInBackground: Boolean = true,
    /** Leaving the app with a video playing turns it into a small floating window. */
    val pictureInPicture: Boolean = true,
    /** "Do que você gosta?" was answered or skipped. */
    val interestsAsked: Boolean = false,
    /** The speed chosen on the video (1 = normal); every video after it plays at it too. */
    val speed: Float = 1f,
    /** Captions on the video (for the next videos too). */
    val captionsOn: Boolean = false,
    /** The language of the captions chosen ("" = the app's). */
    val captionLanguage: String = "",
)

class TubeSettings(private val context: Context) {
    private object K {
        val maxHeight = intPreferencesKey("max_height")
        val autoplay = booleanPreferencesKey("autoplay")
        val historyPaused = booleanPreferencesKey("history_paused")
        val background = booleanPreferencesKey("background")
        val interestsAsked = booleanPreferencesKey("interests_asked")
        val pip = booleanPreferencesKey("pip")
        val speed = floatPreferencesKey("speed")
        val captionsOn = booleanPreferencesKey("captions_on")
        val captionLanguage = androidx.datastore.preferences.core.stringPreferencesKey("caption_language")
    }

    val prefs: Flow<TubePrefs> = context.tubeStore.data.map { p ->
        TubePrefs(
            maxHeight = p[K.maxHeight] ?: 720,
            autoplay = p[K.autoplay] ?: true,
            historyPaused = p[K.historyPaused] ?: false,
            keepPlayingInBackground = p[K.background] ?: true,
            pictureInPicture = p[K.pip] ?: true,
            interestsAsked = p[K.interestsAsked] ?: false,
            speed = p[K.speed]?.takeIf { it in SPEEDS } ?: 1f,
            captionsOn = p[K.captionsOn] ?: false,
            captionLanguage = p[K.captionLanguage].orEmpty(),
        )
    }

    suspend fun current(): TubePrefs = prefs.first()

    suspend fun setMaxHeight(v: Int) = context.tubeStore.edit { it[K.maxHeight] = v }
    suspend fun setAutoplay(v: Boolean) = context.tubeStore.edit { it[K.autoplay] = v }
    suspend fun setHistoryPaused(v: Boolean) = context.tubeStore.edit { it[K.historyPaused] = v }
    suspend fun setKeepPlayingInBackground(v: Boolean) = context.tubeStore.edit { it[K.background] = v }
    suspend fun setPictureInPicture(v: Boolean) = context.tubeStore.edit { it[K.pip] = v }
    suspend fun setInterestsAsked(v: Boolean) = context.tubeStore.edit { it[K.interestsAsked] = v }
    suspend fun setSpeed(v: Float) = context.tubeStore.edit { it[K.speed] = v }
    suspend fun setCaptions(on: Boolean, language: String?) = context.tubeStore.edit {
        it[K.captionsOn] = on
        if (language != null) it[K.captionLanguage] = language
    }

    companion object {
        val QUALITIES = listOf(360, 480, 720, 1080)
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    }
}
