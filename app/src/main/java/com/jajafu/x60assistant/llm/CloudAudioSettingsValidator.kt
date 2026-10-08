package com.jajafu.x60assistant.llm

import com.jajafu.x60assistant.data.AssistantSettings

/** Validates the shared LLM settings required by the optional cloud audio paths. */
object CloudAudioSettingsValidator {
    fun validateTts(
        settings: AssistantSettings,
        hasApiKey: Boolean,
        allowHttp: Boolean,
    ): String? {
        if (!settings.cloudTtsEnabled) return null
        return validateCommon(settings, hasApiKey)
            ?: endpointError(settings.baseUrl, "/audio/speech", allowHttp)
    }

    fun validateAsr(
        settings: AssistantSettings,
        hasApiKey: Boolean,
        allowHttp: Boolean,
    ): String? {
        if (!settings.cloudAsrEnabled) return null
        return validateCommon(settings, hasApiKey)
            ?: endpointError(settings.baseUrl, "/audio/transcriptions", allowHttp)
    }

    fun validate(
        settings: AssistantSettings,
        hasApiKey: Boolean,
        allowHttp: Boolean,
    ): String? = validateTts(settings, hasApiKey, allowHttp)
        ?: validateAsr(settings, hasApiKey, allowHttp)

    private fun validateCommon(settings: AssistantSettings, hasApiKey: Boolean): String? = when {
        !hasApiKey -> "雲端語音已開啟，但尚未設定 API Key。"
        settings.baseUrl.isBlank() -> "雲端語音已開啟，但尚未設定 Base URL。"
        else -> null
    }

    private fun endpointError(baseUrl: String, endpoint: String, allowHttp: Boolean): String? =
        runCatching { ProviderUrl.normalize(baseUrl, endpoint, allowHttp) }
            .exceptionOrNull()
            ?.message
            ?.let { "雲端語音 Base URL 無效：$it" }
}
