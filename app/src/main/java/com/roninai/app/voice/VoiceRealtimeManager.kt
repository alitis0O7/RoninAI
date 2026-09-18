package com.roninai.app.voice

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

data class VoiceInfo(
    val state: VoiceState = VoiceState.IDLE,
    val amplitude: Float = 0f,
    val rmsAmplitude: Float = 0f,
    val isVoiceDetected: Boolean = false,
    val ttsReady: Boolean = false,
    val errorMessage: String? = null,
    val currentText: String = ""
)

class VoiceRealtimeManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(VoiceInfo())
    val state: StateFlow<VoiceInfo> = _state.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var tts: TextToSpeech? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    private var isTtsInitialized = false

    private var onVoiceResult: ((String) -> Unit)? = null
    private var onAmplitudeUpdate: ((Float) -> Unit)? = null

    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    fun setVoiceResultListener(listener: (String) -> Unit) {
        onVoiceResult = listener
    }

    fun setAmplitudeListener(listener: (Float) -> Unit) {
        onAmplitudeUpdate = listener
    }

    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(state = VoiceState.SPEAKING)
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.value = _state.value.copy(state = VoiceState.IDLE)
                    }

                    @Deprecated("Deprecated in API")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(
                            state = VoiceState.ERROR,
                            errorMessage = "TTS playback error"
                        )
                    }
                })
                isTtsInitialized = true
                _state.value = _state.value.copy(ttsReady = true)
                Log.i(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed: $status")
                _state.value = _state.value.copy(
                    errorMessage = "TTS initialization failed"
                )
            }
        }
    }

    fun startListening() {
        if (_state.value.state == VoiceState.LISTENING) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _state.value = _state.value.copy(
                    state = VoiceState.ERROR,
                    errorMessage = "AudioRecord initialization failed"
                )
                return
            }

            _state.value = _state.value.copy(
                state = VoiceState.LISTENING,
                amplitude = 0f,
                rmsAmplitude = 0f
            )

            audioRecord?.startRecording()

            recordingJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2)
                val audioBuffer = ByteArrayOutputStream()
                var silenceCount = 0
                var voiceDetected = false

                while (_state.value.state == VoiceState.LISTENING) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                    if (readCount > 0) {
                        // Calculate amplitude
                        var sum = 0.0
                        for (i in 0 until readCount) {
                            sum += abs(buffer[i].toDouble())
                        }
                        val rms = sqrt(sum / readCount).toFloat()
                        val normalizedAmplitude = (rms / Short.MAX_VALUE).coerceIn(0f, 1f)

                        _state.value = _state.value.copy(
                            amplitude = normalizedAmplitude,
                            rmsAmplitude = rms
                        )
                        onAmplitudeUpdate?.invoke(normalizedAmplitude)

                        // Voice Activity Detection
                        val isVoice = rms > VAD_THRESHOLD
                        if (isVoice) {
                            voiceDetected = true
                            silenceCount = 0
                            _state.value = _state.value.copy(isVoiceDetected = true)

                            // Write to buffer for potential processing
                            val byteBuffer = ByteBuffer.allocate(readCount * 2)
                                .order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until readCount) {
                                byteBuffer.putShort(buffer[i])
                            }
                            audioBuffer.write(byteBuffer.array())
                        } else {
                            silenceCount++
                            if (silenceCount > SILENCE_FRAME_COUNT && voiceDetected) {
                                // Voice was detected then silence - process the audio
                                _state.value = _state.value.copy(isVoiceDetected = false)
                                val audioData = audioBuffer.toByteArray()
                                audioBuffer.reset()
                                voiceDetected = false
                                silenceCount = 0

                                if (audioData.isNotEmpty()) {
                                    processVoiceInput(audioData)
                                }
                            } else if (silenceCount > SILENCE_FRAME_COUNT * 3) {
                                _state.value = _state.value.copy(isVoiceDetected = false)
                            }
                        }

                        delay(10) // ~16ms per frame at 16kHz
                    }
                }
            }
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(
                state = VoiceState.ERROR,
                errorMessage = "Microphone permission not granted"
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                state = VoiceState.ERROR,
                errorMessage = e.message
            )
        }
    }

    fun stopListening() {
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null

        _state.value = _state.value.copy(
            state = VoiceState.IDLE,
            amplitude = 0f,
            rmsAmplitude = 0f,
            isVoiceDetected = false
        )
    }

    fun speak(text: String) {
        if (!isTtsInitialized || text.isBlank()) return

        _state.value = _state.value.copy(
            state = VoiceState.SPEAKING,
            currentText = text
        )

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "roninai_speech")
    }

    fun stopSpeaking() {
        tts?.stop()
        _state.value = _state.value.copy(state = VoiceState.IDLE)
    }

    fun sendTextForVoiceResponse(text: String) {
        _state.value = _state.value.copy(
            state = VoiceState.PROCESSING,
            currentText = text
        )
        onVoiceResult?.invoke(text)
    }

    private fun processVoiceInput(audioData: ByteArray) {
        _state.value = _state.value.copy(state = VoiceState.PROCESSING)

        // Convert PCM to base64 for API
        val base64Audio = android.util.Base64.encodeToString(
            audioData,
            android.util.Base64.NO_WRAP
        )

        // In a full implementation, this would send to STT API
        // For now, we emit a placeholder and rely on the text input fallback
        Log.d(TAG, "Voice input captured: ${audioData.size} bytes")

        // Simulate STT result - in production, integrate with Whisper API or similar
        scope.launch {
            delay(100) // Simulated processing
            // The actual STT integration would go here
            _state.value = _state.value.copy(
                state = VoiceState.IDLE,
                isVoiceDetected = false
            )
        }
    }

    fun setTtsLanguage(locale: Locale) {
        tts?.language = locale
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    fun isListening(): Boolean = _state.value.state == VoiceState.LISTENING

    fun cleanup() {
        stopListening()
        stopSpeaking()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsInitialized = false
        scope.cancel()
        mainScope.cancel()
    }

    companion object {
        private const val TAG = "VoiceRealtimeManager"
        private const val VAD_THRESHOLD = 500f
        private const val SILENCE_FRAME_COUNT = 30

        @Volatile
        private var INSTANCE: VoiceRealtimeManager? = null

        fun getInstance(context: Context): VoiceRealtimeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceRealtimeManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
