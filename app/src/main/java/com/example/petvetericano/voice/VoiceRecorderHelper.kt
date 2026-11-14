package com.example.petvetericano.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.io.IOException

/**
 * Gestor seguro de captura de audio con MediaRecorder para el dictado por voz.
 * Soporta grabación en formato AAC/M4A de alta fidelidad optimizado para procesamiento por IA (Gemini).
 */
class VoiceRecorderHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var startTime: Long = 0L
    private var currentOutputFile: File? = null

    fun isRecording(): Boolean = isRecording

    fun getDurationMs(): Long {
        return if (isRecording) {
            SystemClock.elapsedRealtime() - startTime
        } else {
            0L
        }
    }

    /**
     * Inicia la grabación de audio en el archivo temporal especificado.
     */
    @Throws(IOException::class, IllegalStateException::class)
    fun startRecording(outputFile: File) {
        if (isRecording) {
            stopRecording()
        }

        currentOutputFile = outputFile

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
            startTime = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            recorder.release()
            currentOutputFile?.delete()
            currentOutputFile = null
            isRecording = false
            throw e
        }
    }

    /**
     * Detiene la grabación y retorna el archivo grabado.
     */
    fun stopRecording(): File? {
        if (!isRecording) return null

        val file = currentOutputFile
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Manejar posibles excepciones de parada prematura si dura menos de 1 seg
        } finally {
            mediaRecorder = null
            isRecording = false
            currentOutputFile = null
        }
        return file
    }

    /**
     * Cancela la grabación activa y elimina el archivo parcial.
     */
    fun cancelRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
            isRecording = false
            try {
                currentOutputFile?.delete()
            } catch (_: Exception) {
            }
            currentOutputFile = null
        }
    }
}

