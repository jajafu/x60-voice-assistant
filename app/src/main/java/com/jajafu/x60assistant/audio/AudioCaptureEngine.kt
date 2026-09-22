package com.jajafu.x60assistant.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.jajafu.x60assistant.diagnostics.VoiceErrorCode
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class AudioCaptureEngine(
    private val context: Context,
    val frameQueue: BoundedAudioFrameQueue = BoundedAudioFrameQueue(FRAME_QUEUE_CAPACITY),
    private val onFrame: () -> Unit,
    private val onDroppedFrame: () -> Unit,
    private val onReadError: (VoiceErrorCode, String) -> Unit,
    private val onFatalError: (VoiceErrorCode, String) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val resourceLock = Any()

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var readThread: Thread? = null

    fun start(): String {
        check(!running.get()) { "AudioCaptureEngine is already running" }
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        ) { "RECORD_AUDIO permission is not granted" }

        val (record, sourceName) = createAndStartWithFallback()
        synchronized(resourceLock) {
            check(audioRecord == null) { "Only one AudioRecord may be owned" }
            audioRecord = record
        }
        running.set(true)
        readThread = Thread(::readLoop, "x60-audio-read").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return sourceName
    }

    fun stop() {
        if (!running.getAndSet(false) && audioRecord == null) {
            frameQueue.clear()
            return
        }

        val record = synchronized(resourceLock) { audioRecord }
        runCatching {
            if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        }
        readThread?.interrupt()
        if (Thread.currentThread() !== readThread) {
            runCatching { readThread?.join(THREAD_JOIN_TIMEOUT_MS) }
        }
        readThread = null

        synchronized(resourceLock) {
            runCatching { audioRecord?.release() }
            audioRecord = null
        }
        frameQueue.clear()
    }

    private fun createAndStartWithFallback(): Pair<AudioRecord, String> {
        var voiceRecognitionFailure: Throwable? = null
        try {
            val record = createRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            startRecord(record)
            return record to SOURCE_VOICE_RECOGNITION
        } catch (error: Throwable) {
            voiceRecognitionFailure = error
        }

        try {
            val record = createRecord(MediaRecorder.AudioSource.MIC)
            startRecord(record)
            return record to SOURCE_MIC
        } catch (micError: Throwable) {
            val detail = "VOICE_RECOGNITION=${voiceRecognitionFailure.javaClass.simpleName}; " +
                "MIC=${micError.javaClass.simpleName}"
            throw AudioInitializationException(detail, micError)
        }
    }

    private fun createRecord(source: Int): AudioRecord {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("RECORD_AUDIO permission was revoked")
        }
        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferBytes <= 0) {
            throw AudioInitializationException("getMinBufferSize=$minBufferBytes")
        }
        val bufferBytes = max(minBufferBytes, FRAME_BYTES * 2)
        val record = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .build()
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            val invalidState = record.state
            record.release()
            throw AudioInitializationException("AudioRecord state=$invalidState")
        }
        return record
    }

    private fun startRecord(record: AudioRecord) {
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw AudioInitializationException("recordingState=${record.recordingState}")
            }
        } catch (error: Throwable) {
            runCatching { record.release() }
            throw error
        }
    }

    private fun readLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val readBuffer = ShortArray(FRAME_SAMPLES)
        while (running.get()) {
            val record = synchronized(resourceLock) { audioRecord } ?: break
            val read = try {
                record.read(
                    readBuffer,
                    0,
                    readBuffer.size,
                    AudioRecord.READ_BLOCKING,
                )
            } catch (securityError: SecurityException) {
                reportFatal(VoiceErrorCode.PERMISSION_MIC_DENIED, securityError.javaClass.simpleName)
                break
            } catch (runtimeError: RuntimeException) {
                reportFatal(VoiceErrorCode.AUDIO_READ_FAILED, runtimeError.javaClass.simpleName)
                break
            }

            when {
                read > 0 -> {
                    val frame = AudioFrame(
                        samples = readBuffer.copyOf(read),
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                    )
                    if (frameQueue.offerDroppingOldest(frame)) onDroppedFrame()
                    onFrame()
                }

                read == AudioRecord.ERROR_DEAD_OBJECT -> {
                    reportFatal(VoiceErrorCode.AUDIO_READ_DEAD_OBJECT, "read=$read")
                    break
                }

                read < 0 -> {
                    onReadError(VoiceErrorCode.AUDIO_READ_FAILED, "read=$read")
                    reportFatal(VoiceErrorCode.AUDIO_READ_FAILED, "read=$read")
                    break
                }
            }
        }
    }

    private fun reportFatal(code: VoiceErrorCode, detail: String) {
        running.set(false)
        onReadError(code, detail)
        onFatalError(code, detail)
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val FRAME_DURATION_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE_HZ * FRAME_DURATION_MS / 1_000
        private const val FRAME_BYTES = FRAME_SAMPLES * 2
        private const val FRAME_QUEUE_CAPACITY = 50
        private const val THREAD_JOIN_TIMEOUT_MS = 1_500L
        const val SOURCE_VOICE_RECOGNITION = "VOICE_RECOGNITION"
        const val SOURCE_MIC = "MIC"
    }
}

class AudioInitializationException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
