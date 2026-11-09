package com.cantai.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class VoiceController(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    var listening by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var partial by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var onFinalResult: (String) -> Unit = {}

    val recognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            listening = false
            level = 0f
        }
        override fun onError(code: Int) {
            listening = false
            level = 0f
            val msg = describe(code)
            if (msg.isNotEmpty()) errorMessage = msg
        }
        override fun onResults(results: Bundle?) {
            listening = false
            level = 0f
            partial = ""
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) onFinalResult(text)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.getDefault())
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA &&
                    r != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        main.post { speaking = true }
                    }
                    override fun onDone(utteranceId: String?) {
                        main.post { speaking = false }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post { speaking = false }
                    }
                })
                if (!ttsReady) {
                    main.post {
                        errorMessage = "Spoken replies aren't supported for your language on this device."
                    }
                }
            } else {
                main.post { errorMessage = "Text-to-speech isn't available on this device." }
            }
        }
    }

    fun startListening() {
        if (!recognitionAvailable) {
            errorMessage = "Speech recognition isn't available on this device."
            return
        }
        stopSpeaking()
        errorMessage = null
        partial = ""
        try {
            val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
                it.setRecognitionListener(listener)
                recognizer = it
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            r.startListening(intent)
            listening = true
        } catch (e: Exception) {
            listening = false
            errorMessage = "Couldn't start listening: ${e.message ?: "unknown error"}"
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
        listening = false
        level = 0f
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cantai-${System.nanoTime()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        speaking = false
    }

    fun clearError() {
        errorMessage = null
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun describe(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_CLIENT -> ""
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap the mic and try again."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a network connection right now."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech service is busy. Try again in a moment."
        SpeechRecognizer.ERROR_AUDIO -> "There was a problem with the microphone."
        else -> "Speech recognition failed (code $code)."
    }
}
