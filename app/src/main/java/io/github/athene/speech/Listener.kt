package io.github.athene.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Why a listening attempt produced nothing. */
enum class ListenError { NOTHING_HEARD, NO_PERMISSION, UNAVAILABLE }

/**
 * Hears one utterance with the device's speech recogniser. Must be created, used and released on the main thread.
 * Needs the RECORD_AUDIO permission; ask for it before calling [listen].
 */
class Listener(context: Context) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    /**
     * Starts listening for speech in [locale]. Exactly one of [onResult] (with every transcription the recogniser
     * offers, best first) or [onError] is called when it ends.
     */
    fun listen(locale: String, onResult: (List<String>) -> Unit, onError: (ListenError) -> Unit) {
        if (!available) {
            onError(ListenError.UNAVAILABLE)
            return
        }
        cancel()
        val created = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = created
        var finished = false
        created.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                if (finished) return
                finished = true
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (heard.isEmpty()) onError(ListenError.NOTHING_HEARD) else onResult(heard)
            }

            override fun onError(error: Int) {
                if (finished) return
                finished = true
                onError(
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ListenError.NO_PERMISSION
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ListenError.NOTHING_HEARD
                        else -> ListenError.UNAVAILABLE
                    },
                )
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        created.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5),
        )
    }

    /** Stops listening without reporting anything. */
    fun cancel() {
        recognizer?.destroy()
        recognizer = null
    }
}
