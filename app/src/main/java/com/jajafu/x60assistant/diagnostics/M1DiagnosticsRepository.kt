package com.jajafu.x60assistant.diagnostics

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class VoiceServicePhase {
    STOPPED,
    STARTING,
    IDLE_KWS,
    ERROR,
}

data class M1DiagnosticsSnapshot(
    val phase: VoiceServicePhase = VoiceServicePhase.STOPPED,
    val audioFrameCount: Long = 0,
    val wakeCount: Long = 0,
    val lastWakeScore: Float? = null,
    val lastWakeTimestampEpochMs: Long? = null,
    val serviceUptimeMs: Long = 0,
    val audioErrorCount: Long = 0,
    val droppedFrameCount: Long = 0,
    val lastError: VoiceErrorCode? = null,
    val audioSource: String = "—",
    val wakeLockHeld: Boolean = false,
    val modelId: String = "—",
    val modelSha256: String = "—",
)

object M1DiagnosticsRepository {
    private val audioFrames = AtomicLong(0)
    private val wakeCount = AtomicLong(0)
    private val audioErrors = AtomicLong(0)
    private val droppedFrames = AtomicLong(0)
    private val startedAtElapsedMs = AtomicLong(0)
    private val phase = AtomicReference(VoiceServicePhase.STOPPED)
    private val lastError = AtomicReference<VoiceErrorCode?>(null)
    private val audioSource = AtomicReference("—")
    private val wakeLockHeld = AtomicReference(false)
    private val lastWakeScore = AtomicReference<Float?>(null)
    private val lastWakeEpochMs = AtomicReference<Long?>(null)
    private val modelId = AtomicReference("—")
    private val modelSha256 = AtomicReference("—")
    private val mutableSnapshot = MutableStateFlow(M1DiagnosticsSnapshot())

    val snapshot = mutableSnapshot.asStateFlow()
    val ringLog = SafeRingLog()

    fun resetForStart(modelIdValue: String, modelSha256Value: String) {
        audioFrames.set(0)
        wakeCount.set(0)
        audioErrors.set(0)
        droppedFrames.set(0)
        startedAtElapsedMs.set(SystemClock.elapsedRealtime())
        phase.set(VoiceServicePhase.STARTING)
        lastError.set(null)
        audioSource.set("—")
        wakeLockHeld.set(false)
        lastWakeScore.set(null)
        lastWakeEpochMs.set(null)
        modelId.set(modelIdValue)
        modelSha256.set(modelSha256Value)
        ringLog.clear()
        log("SERVICE_STARTING")
        publish()
    }

    fun onAudioFrame() {
        audioFrames.incrementAndGet()
    }

    fun onDroppedFrame() {
        droppedFrames.incrementAndGet()
    }

    fun onAudioError(code: VoiceErrorCode, detail: String? = null) {
        audioErrors.incrementAndGet()
        recordError(code, detail)
    }

    fun onWake(configuredScore: Float) {
        wakeCount.incrementAndGet()
        lastWakeScore.set(configuredScore)
        lastWakeEpochMs.set(System.currentTimeMillis())
        log("WAKE_DETECTED", detail = "keyword=小助手")
        publish()
    }

    fun setIdle(audioSourceValue: String) {
        audioSource.set(audioSourceValue)
        phase.set(VoiceServicePhase.IDLE_KWS)
        log("IDLE_KWS", detail = "source=$audioSourceValue")
        publish()
    }

    fun setWakeLockHeld(held: Boolean) {
        wakeLockHeld.set(held)
        log(if (held) "WAKE_LOCK_ACQUIRED" else "WAKE_LOCK_RELEASED")
        publish()
    }

    fun recordError(code: VoiceErrorCode, detail: String? = null) {
        lastError.set(code)
        phase.set(VoiceServicePhase.ERROR)
        log("ERROR", code, detail)
        publish()
    }

    fun setStopped() {
        phase.set(VoiceServicePhase.STOPPED)
        wakeLockHeld.set(false)
        log("SERVICE_STOPPED")
        publish()
    }

    fun publish() {
        val started = startedAtElapsedMs.get()
        val currentPhase = phase.get()
        mutableSnapshot.value = M1DiagnosticsSnapshot(
            phase = currentPhase,
            audioFrameCount = audioFrames.get(),
            wakeCount = wakeCount.get(),
            lastWakeScore = lastWakeScore.get(),
            lastWakeTimestampEpochMs = lastWakeEpochMs.get(),
            serviceUptimeMs = if (started == 0L || currentPhase == VoiceServicePhase.STOPPED) {
                0
            } else {
                (SystemClock.elapsedRealtime() - started).coerceAtLeast(0)
            },
            audioErrorCount = audioErrors.get(),
            droppedFrameCount = droppedFrames.get(),
            lastError = lastError.get(),
            audioSource = audioSource.get(),
            wakeLockHeld = wakeLockHeld.get(),
            modelId = modelId.get(),
            modelSha256 = modelSha256.get(),
        )
    }

    private fun log(
        event: String,
        errorCode: VoiceErrorCode? = null,
        detail: String? = null,
    ) {
        ringLog.add(
            SafeLogEntry(
                elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                event = event,
                errorCode = errorCode,
                detail = detail,
            ),
        )
    }
}
