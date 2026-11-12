package io.github.athene.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CompletableDeferred
import java.util.Locale

/** Reads text aloud with the device's text-to-speech engine. Nothing is bundled or sent anywhere by the app. */
class Speaker(context: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }

    /** Whether the engine has a voice for [locale] (a BCP-47 tag such as "es-ES"). */
    suspend fun supports(locale: String): Boolean {
        if (!ready.await()) return false
        return try {
            tts.isLanguageAvailable(Locale.forLanguageTag(locale)) >= TextToSpeech.LANG_AVAILABLE
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /** Says [text], cutting off anything still being said. Does nothing if the engine is not ready. */
    fun speak(text: String, locale: String, slow: Boolean = false) {
        if (!ready.isCompleted || !ready.getCompleted()) return
        tts.language = Locale.forLanguageTag(locale)
        tts.setSpeechRate(if (slow) SLOW_RATE else NORMAL_RATE)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "athene")
    }

    fun stop() {
        if (ready.isCompleted && ready.getCompleted()) tts.stop()
    }

    companion object {
        private const val NORMAL_RATE = 0.9f
        private const val SLOW_RATE = 0.55f
    }
}
