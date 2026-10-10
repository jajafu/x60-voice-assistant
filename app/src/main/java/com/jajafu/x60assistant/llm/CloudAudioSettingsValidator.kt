package com.jajafu.x60assistant.llm

import com.jajafu.x60assistant.data.AssistantSettings

/** Validates the independent Audio Provider settings used by optional cloud audio paths. */
object CloudAudioSettingsValidator {
    fun validateTts(
        settings: AssistantSettings,
        hasApiKey: Boolean,
        allowHttp: Boolean,
    ): String? {
        if (!settings.cloudTtsEnabled) return null
        return validateCommon(settings, hasApiKey)
            ?: endpointError(settings.audioBaseUrl, "/audio/speech", allowHttp)
    }

    fun validateAsr(
        settings: AssistantSettings,
        hasApiKey: Boolean,
        allowHttp: Boolean,
    ): String? {
        if (!settings.cloudAsrEnabled) return null
        return validateCommon(settings, hasApiKey)
            ?: endpointError(settings.audioBaseUrl, "/audio/transcriptions", allowHttp)
    }

    fun validate(
        settings: AssistantSettings,
        hasApiKey: Boolean,
        allowHttp: Boolean,
    ): String? {
        if (configuredTts(settings, hasApiKey)) {
            validateTts(settings, hasApiKey, allowHttp)?.let { return it }
        }
        if (configuredAsr(settings, hasApiKey)) {
            validateAsr(settings, hasApiKey, allowHttp)?.let { return it }
        }
        return null
    }

    private fun validateCommon(settings: AssistantSettings, hasApiKey: Boolean): String? = when {
        !hasApiKey -> "雲端語音已開啟，但尚未設定 Audio API Key。"
        settings.audioBaseUrl.isBlank() -> "雲端語音已開啟，但尚未設定 Audio Base URL。"
        else -> null
    }

    private fun endpointError(baseUrl: String, endpoint: String, allowHttp: Boolean): String? =
        runCatching { ProviderUrl.normalize(baseUrl, endpoint, allowHttp) }
            .exceptionOrNull()
            ?.message
            ?.let { "Audio Base URL 無效：$it" }

    private fun configuredTts(settings: AssistantSettings, hasApiKey: Boolean): Boolean =
        settings.cloudTtsEnabled && hasApiKey && settings.audioBaseUrl.isNotBlank()

    private fun configuredAsr(settings: AssistantSettings, hasApiKey: Boolean): Boolean =
        settings.cloudAsrEnabled && hasApiKey && settings.audioBaseUrl.isNotBlank()
}
