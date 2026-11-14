package com.example.petvetericano.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.io.IOException

/**
 * Resultado seguro al detener una grabación de voz.
 */
sealed class RecordingStopResult {
    data class Success(val file: File, val durationMs: Long) : RecordingStopResult()
    data class TooShort(val durationMs: Long) : RecordingStopResult()
    object SilenceDetected : RecordingStopResult()
    object CorruptOrFailed : RecordingStopResult()
    object NotRecording : RecordingStopResult()
}

/**
 * Gestor seguro de captura de audio con MediaRecorder para el dictado por voz.
 * Optimizado para voz en español (AAC/M4A, mono, 16 kHz, 64 kbps), con validación de duración,
 * detección de silencio total y liberación garantizada de recursos.
 */
class VoiceRecorderHelper(private val context: Context) {

    companion object {
        const val MIN_DURATION_MS = 1500L
        const val MAX_DURATION_MS = 180_000L
        const val WARNING_DURATION_MS = 160_000L
        private const val MIN_VALID_FILE_BYTES = 512L
        private const val SILENCE_AMPLITUDE_THRESHOLD = 250
    }

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var startTime: Long = 0L
    private var currentOutputFile: File? = null
    private var peakAmplitude: Int = 0
    private var amplitudeSampleCount: Int = 0

    fun isRecording(): Boolean = isRecording

    fun getDurationMs(): Long {
        return if (isRecording) {
            SystemClock.elapsedRealtime() - startTime
        } else {
            0L
        }
    }

    /**
     * Obtiene la amplitud máxima instantánea del micrófono y actualiza el pico observado
     * para la visualización en UI y detección de silencio total.
     */
    fun getMaxAmplitude(): Int {
        if (!isRecording) return 0
        val amp = try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
        amplitudeSampleCount++
        if (amp > peakAmplitude) {
            peakAmplitude = amp
        }
        return amp
    }

    /**
     * Inicia la grabación usando AudioSource.VOICE_RECOGNITION con respaldo a MIC.
     */
    @Throws(IOException::class, IllegalStateException::class)
    fun startRecording(outputFile: File) {
        if (isRecording) {
            cancelRecording()
        }

        peakAmplitude = 0
        amplitudeSampleCount = 0
        currentOutputFile = outputFile

        try {
            iniciarConFuente(outputFile, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        } catch (primaryErr: Exception) {
            safelyDeleteFile(outputFile)
            iniciarConFuente(outputFile, MediaRecorder.AudioSource.MIC)
        }
    }

    private fun iniciarConFuente(outputFile: File, audioSource: Int) {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(64000)
                setMaxDuration(MAX_DURATION_MS.toInt())
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
            startTime = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            try {
                recorder.reset()
            } catch (_: Exception) {
            }
            try {
                recorder.release()
            } catch (_: Exception) {
            }
            mediaRecorder = null
            isRecording = false
            safelyDeleteFile(outputFile)
            currentOutputFile = null
            throw e
        }
    }

    /**
     * Detiene la grabación, valida integridad del archivo y detecta duración mínima o silencio.
     * Nunca devuelve un archivo corrupto ni sin finalizar.
     */
    fun stopRecording(): RecordingStopResult {
        if (!isRecording) return RecordingStopResult.NotRecording

        val file = currentOutputFile
        val durationMs = SystemClock.elapsedRealtime() - startTime
        var stopFailed = false

        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
            stopFailed = true
        } finally {
            try {
                mediaRecorder?.reset()
            } catch (_: Exception) {
            }
            try {
                mediaRecorder?.release()
            } catch (_: Exception) {
            }
            mediaRecorder = null
            isRecording = false
            currentOutputFile = null
        }

        if (durationMs < MIN_DURATION_MS) {
            safelyDeleteFile(file)
            return RecordingStopResult.TooShort(durationMs)
        }

        if (stopFailed || file == null || !file.exists() || file.length() < MIN_VALID_FILE_BYTES) {
            safelyDeleteFile(file)
            return RecordingStopResult.CorruptOrFailed
        }

        if (amplitudeSampleCount >= 3 && peakAmplitude < SILENCE_AMPLITUDE_THRESHOLD) {
            safelyDeleteFile(file)
            return RecordingStopResult.SilenceDetected
        }

        return RecordingStopResult.Success(file, durationMs)
    }

    /**
     * Cancela la grabación activa, libera recursos y elimina el archivo parcial.
     */
    fun cancelRecording() {
        if (!isRecording && mediaRecorder == null) {
            safelyDeleteFile(currentOutputFile)
            currentOutputFile = null
            return
        }

        val file = currentOutputFile
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        } finally {
            try {
                mediaRecorder?.reset()
            } catch (_: Exception) {
            }
            try {
                mediaRecorder?.release()
            } catch (_: Exception) {
            }
            mediaRecorder = null
            isRecording = false
            currentOutputFile = null
            safelyDeleteFile(file)
        }
    }

    private fun safelyDeleteFile(file: File?) {
        try {
            if (file != null && file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }
}

