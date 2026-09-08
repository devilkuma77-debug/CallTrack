package com.calltech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

data class RecordingMeta(
    val file: File,
    val durationMs: Long,
    val startedAt: Long,
)

object CallRecorder {
    private const val TAG = "CallRecorder"
    private const val MIN_FILE_BYTES = 512L

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L
    private var isRecording = false

    fun start(context: Context): Boolean {
        if (isRecording) {
            return true
        }

        if (!hasMicPermission(context)) {
            Log.w(TAG, "RECORD_AUDIO permission missing")
            return false
        }

        return try {
            val recordingsDir = File(context.cacheDir, "recordings").apply { mkdirs() }
            val file = File(recordingsDir, "call_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = buildRecorder(context, file)
            mediaRecorder.start()

            recorder = mediaRecorder
            outputFile = file
            startedAt = System.currentTimeMillis()
            isRecording = true

            Log.d(TAG, "Recording started: ${file.absolutePath}")
            true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start recording", error)
            cleanupRecorder(deleteFile = true)
            false
        }
    }

    fun stop(): RecordingMeta? {
        if (!isRecording) {
            return null
        }

        val file = outputFile
        val started = startedAt

        try {
            recorder?.stop()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to stop recording cleanly", error)
        }

        cleanupRecorder(deleteFile = false)
        isRecording = false
        outputFile = null
        startedAt = 0L

        if (file == null || !file.exists() || file.length() < MIN_FILE_BYTES) {
            file?.delete()
            Log.w(TAG, "Recording discarded (empty or too small)")
            return null
        }

        val durationMs = (System.currentTimeMillis() - started).coerceAtLeast(0L)
        Log.d(TAG, "Recording saved: ${file.name}, duration=${durationMs}ms")

        return RecordingMeta(
            file = file,
            durationMs = durationMs,
            startedAt = started,
        )
    }

    private fun buildRecorder(context: Context, file: File): MediaRecorder {
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        val audioSource = resolveAudioSource()
        mediaRecorder.setAudioSource(audioSource)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioEncodingBitRate(128_000)
        mediaRecorder.setAudioSamplingRate(44_100)
        mediaRecorder.setOutputFile(file.absolutePath)
        mediaRecorder.prepare()

        Log.d(TAG, "MediaRecorder prepared with source=$audioSource")
        return mediaRecorder
    }

    private fun resolveAudioSource(): Int {
        val candidates = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            @Suppress("DEPRECATION")
            MediaRecorder.AudioSource.VOICE_CALL,
        )

        return candidates.first()
    }

    private fun cleanupRecorder(deleteFile: Boolean) {
        try {
            recorder?.reset()
        } catch (_: Exception) {
        }

        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        recorder = null

        if (deleteFile) {
            outputFile?.delete()
        }
    }

    private fun hasMicPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
