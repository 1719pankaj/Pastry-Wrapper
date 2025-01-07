package com.example.cpplearner.util

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment

class SpeechRecognizerManager(private val fragment: Fragment) {
    private var speechRecognizer: SpeechRecognizer
    private var isListening = false
    private var onTextUpdateListener: ((String) -> Unit)? = null
    private var onListeningStateChangeListener: ((Boolean) -> Unit)? = null

    init {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(fragment.requireContext())
        setupRecognitionListener()
    }

    private fun setupRecognitionListener() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                onListeningStateChangeListener?.invoke(true)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
//                if (isListening) {
//                    startListening()
//                }
                isListening = false
                stopListening()
            }

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                    if (isListening)
                        startListening()
                    return
                }

                val message = when (error) {
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
                    else -> "Recognition error"
                }

                Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show()
                stopListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { text ->
                    onTextUpdateListener?.invoke(text)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { text ->
                    onTextUpdateListener?.invoke(text)
                }

                if (isListening) {
                    startListening()
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer.startListening(intent)
            isListening = true
            onListeningStateChangeListener?.invoke(true)
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "startListening failed", e)
            Toast.makeText(fragment.requireContext(), "Failed to start voice input", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopListening() {
        isListening = false
        onListeningStateChangeListener?.invoke(false)
        speechRecognizer.stopListening()
    }

    fun isListening(): Boolean = isListening

    fun setOnTextUpdateListener(listener: (String) -> Unit) {
        onTextUpdateListener = listener
    }

    fun setOnListeningStateChangeListener(listener: (Boolean) -> Unit) {
        onListeningStateChangeListener = listener
    }

    fun destroy() {
        speechRecognizer.destroy()
    }
}