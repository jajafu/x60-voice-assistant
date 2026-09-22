package com.jajafu.x60assistant.diagnostics

import com.jajafu.x60assistant.data.AssistantSettings
import java.net.URI

object DiagnosticsExporter {
    fun build(
        runtime: M1DiagnosticsSnapshot,
        device: DeviceDiagnostics,
        settings: AssistantSettings,
        logs: List<SafeLogEntry>,
    ): String = buildString {
        appendLine("X60 Voice Assistant diagnostics")
        appendLine("state=${runtime.phase}")
        appendLine("service_uptime_ms=${runtime.serviceUptimeMs}")
        appendLine("service_restart_count=${runtime.serviceRestartCount}")
        appendLine("wake_count=${runtime.wakeCount}")
        appendLine("audio_frames=${runtime.audioFrameCount}")
        appendLine("audio_errors=${runtime.audioErrorCount}")
        appendLine("dropped_frames=${runtime.droppedFrameCount}")
        appendLine("audio_queue=${runtime.audioQueueDepth}/${runtime.audioQueueHighWaterMark}")
        appendLine("asr_model=${runtime.asrModelId}")
        appendLine("asr_rtf=${runtime.asrRtf ?: "-"}")
        appendLine("asr_final_latency_ms=${runtime.asrFinalLatencyMs ?: "-"}")
        appendLine("transcript_length=${runtime.transcriptLength}")
        appendLine("transcript_hash=${runtime.transcriptHash}")
        appendLine("answer_length=${runtime.answerLength}")
        appendLine("answer_hash=${runtime.answerHash}")
        appendLine("llm_http_status=${runtime.llmHttpStatus ?: "-"}")
        appendLine("llm_first_token_ms=${runtime.llmFirstTokenLatencyMs ?: "-"}")
        appendLine("llm_total_ms=${runtime.llmTotalLatencyMs ?: "-"}")
        appendLine("provider_host=${safeHost(settings.baseUrl)}")
        appendLine("tts_queue=${runtime.ttsQueueDepth}/${runtime.ttsQueueHighWaterMark}")
        appendLine("tts_engine=${runtime.ttsEngine}")
        appendLine("tts_voice=${runtime.ttsVoice}")
        appendLine("battery_percent=${device.batteryPercent}")
        appendLine("temperature_c=${device.temperatureCelsius ?: "-"}")
        appendLine("thermal_status=${device.thermalStatus ?: "-"}")
        appendLine("last_error=${runtime.lastError ?: "-"}")
        appendLine("logs:")
        logs.takeLast(100).forEach {
            appendLine("${it.elapsedRealtimeMs}|${it.event}|${it.errorCode ?: "-"}|${it.detail.orEmpty()}")
        }
    }

    private fun safeHost(baseUrl: String): String = runCatching {
        val uri = URI(baseUrl)
        "${uri.scheme ?: "-"}://${uri.host ?: "-"}${if (uri.port >= 0) ":${uri.port}" else ""}"
    }.getOrDefault("-")
}
