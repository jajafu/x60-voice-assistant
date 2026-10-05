package com.jajafu.x60assistant.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.assistantDataStore by preferencesDataStore(name = "assistant_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AssistantSettings> = context.assistantDataStore.data.map(::fromPreferences)

    suspend fun current(): AssistantSettings = settings.first()

    suspend fun save(value: AssistantSettings) {
        val settings = value.normalized()
        context.assistantDataStore.edit { prefs ->
            prefs[Keys.AUTO_START_LISTENING] = settings.autoStartListening
            prefs[Keys.WAKE_PHRASE] = settings.wakePhrase
            prefs[Keys.WAKE_ACKNOWLEDGEMENT] = settings.wakeAcknowledgement
            prefs[Keys.BASE_URL] = settings.baseUrl.trim()
            prefs[Keys.MODEL] = settings.model.trim()
            prefs[Keys.ENDPOINT] = settings.endpoint.trim()
            prefs[Keys.STREAMING] = settings.streaming
            prefs[Keys.TEMPERATURE] = settings.temperature
            prefs[Keys.MAX_OUTPUT_TOKENS] = settings.maxOutputTokens
            prefs[Keys.CONNECT_TIMEOUT] = settings.connectTimeoutSeconds
            prefs[Keys.READ_TIMEOUT] = settings.readTimeoutSeconds
            prefs[Keys.CONTEXT_TURNS] = settings.contextTurns
            prefs[Keys.SYSTEM_INSTRUCTION] = settings.systemInstruction
            prefs[Keys.WAIT_FOR_SPEECH] = settings.waitForSpeechMs
            prefs[Keys.MIN_SPEECH] = settings.minSpeechMs
            prefs[Keys.TRAILING_SILENCE] = settings.trailingSilenceMs
            prefs[Keys.MAX_UTTERANCE] = settings.maxUtteranceMs
            prefs[Keys.FOLLOW_UP] = settings.followUpSeconds
            prefs[Keys.AUDIO_RESUME_DELAY] = settings.audioResumeDelayMs
            prefs[Keys.ASR_MODEL_ID] = settings.asrModelId
            prefs[Keys.MUSIC_APP_NAME] = settings.musicAppName
            prefs[Keys.MUSIC_PACKAGE_NAME] = settings.musicPackageName
            prefs[Keys.MUSIC_SERVICE_NAME] = settings.musicServiceName
            prefs[Keys.NAV_APP_NAME] = settings.navAppName
            prefs[Keys.NAV_PACKAGE_NAME] = settings.navPackageName
            prefs[Keys.VOICE_VOLUME_CONTROL] = settings.voiceVolumeControl
            prefs[Keys.LLM_CORRECTION_ENABLED] = settings.llmCorrectionEnabled
            prefs[Keys.TTS_ENGINE] = settings.ttsEngine
            prefs[Keys.TTS_VOICE] = settings.ttsVoice
            prefs[Keys.TTS_VOLUME] = settings.ttsVolume
            prefs[Keys.SPEECH_RATE] = settings.speechRate
            prefs[Keys.PITCH] = settings.pitch
            prefs[Keys.CONNECTION_VERIFIED] = settings.connectionVerified
        }
    }

    suspend fun markConnectionVerified(verified: Boolean) {
        context.assistantDataStore.edit { it[Keys.CONNECTION_VERIFIED] = verified }
    }

    suspend fun clear() = context.assistantDataStore.edit { it.clear() }

    private fun fromPreferences(prefs: Preferences): AssistantSettings = AssistantSettings(
        autoStartListening = prefs[Keys.AUTO_START_LISTENING] ?: false,
        wakePhrase = prefs[Keys.WAKE_PHRASE] ?: AssistantSettings.DEFAULT_WAKE_PHRASE,
        wakeAcknowledgement = prefs[Keys.WAKE_ACKNOWLEDGEMENT]
            ?: AssistantSettings.DEFAULT_WAKE_ACKNOWLEDGEMENT,
        baseUrl = prefs[Keys.BASE_URL].orEmpty(),
        model = prefs[Keys.MODEL].orEmpty(),
        endpoint = prefs[Keys.ENDPOINT] ?: "/chat/completions",
        streaming = prefs[Keys.STREAMING] ?: true,
        temperature = prefs[Keys.TEMPERATURE] ?: 0.3,
        maxOutputTokens = prefs[Keys.MAX_OUTPUT_TOKENS] ?: 512,
        connectTimeoutSeconds = prefs[Keys.CONNECT_TIMEOUT] ?: 10,
        readTimeoutSeconds = prefs[Keys.READ_TIMEOUT] ?: 45,
        contextTurns = prefs[Keys.CONTEXT_TURNS] ?: 6,
        systemInstruction = prefs[Keys.SYSTEM_INSTRUCTION] ?: AssistantSettings.DEFAULT_SYSTEM_INSTRUCTION,
        waitForSpeechMs = prefs[Keys.WAIT_FOR_SPEECH] ?: 5_000,
        minSpeechMs = prefs[Keys.MIN_SPEECH] ?: 250,
        trailingSilenceMs = prefs[Keys.TRAILING_SILENCE] ?: 800,
        maxUtteranceMs = prefs[Keys.MAX_UTTERANCE] ?: 20_000,
        followUpSeconds = prefs[Keys.FOLLOW_UP] ?: AssistantSettings.DEFAULT_FOLLOW_UP_SECONDS,
        audioResumeDelayMs = prefs[Keys.AUDIO_RESUME_DELAY] ?: 300,
        asrModelId = prefs[Keys.ASR_MODEL_ID] ?: AssistantSettings.DEFAULT_ASR_MODEL_ID,
        musicAppName = prefs[Keys.MUSIC_APP_NAME] ?: AssistantSettings.DEFAULT_MUSIC_APP_NAME,
        musicPackageName = prefs[Keys.MUSIC_PACKAGE_NAME] ?: AssistantSettings.DEFAULT_MUSIC_PACKAGE_NAME,
        musicServiceName = prefs[Keys.MUSIC_SERVICE_NAME] ?: AssistantSettings.DEFAULT_MUSIC_SERVICE_NAME,
        navAppName = prefs[Keys.NAV_APP_NAME] ?: AssistantSettings.DEFAULT_NAV_APP_NAME,
        navPackageName = prefs[Keys.NAV_PACKAGE_NAME] ?: AssistantSettings.DEFAULT_NAV_PACKAGE_NAME,
        voiceVolumeControl = prefs[Keys.VOICE_VOLUME_CONTROL] ?: AssistantSettings.DEFAULT_VOICE_VOLUME_CONTROL,
        llmCorrectionEnabled = prefs[Keys.LLM_CORRECTION_ENABLED]
            ?: AssistantSettings.DEFAULT_LLM_CORRECTION_ENABLED,
        ttsEngine = prefs[Keys.TTS_ENGINE].orEmpty(),
        ttsVoice = prefs[Keys.TTS_VOICE].orEmpty(),
        ttsVolume = prefs[Keys.TTS_VOLUME] ?: AssistantSettings.DEFAULT_TTS_VOLUME,
        speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
        pitch = prefs[Keys.PITCH] ?: 1.0f,
        connectionVerified = prefs[Keys.CONNECTION_VERIFIED] ?: false,
    ).normalized()

    private object Keys {
        val AUTO_START_LISTENING = booleanPreferencesKey("auto_start_listening")
        val WAKE_PHRASE = stringPreferencesKey("wake_phrase")
        val WAKE_ACKNOWLEDGEMENT = stringPreferencesKey("wake_acknowledgement")
        val BASE_URL = stringPreferencesKey("llm_base_url")
        val MODEL = stringPreferencesKey("llm_model")
        val ENDPOINT = stringPreferencesKey("llm_endpoint")
        val STREAMING = booleanPreferencesKey("llm_streaming")
        val TEMPERATURE = doublePreferencesKey("llm_temperature")
        val MAX_OUTPUT_TOKENS = intPreferencesKey("llm_max_output_tokens")
        val CONNECT_TIMEOUT = intPreferencesKey("llm_connect_timeout_seconds")
        val READ_TIMEOUT = intPreferencesKey("llm_read_timeout_seconds")
        val CONTEXT_TURNS = intPreferencesKey("llm_context_turns")
        val SYSTEM_INSTRUCTION = stringPreferencesKey("llm_system_instruction")
        val WAIT_FOR_SPEECH = longPreferencesKey("vad_wait_for_speech_ms")
        val MIN_SPEECH = longPreferencesKey("vad_min_speech_ms")
        val TRAILING_SILENCE = longPreferencesKey("vad_trailing_silence_ms")
        val MAX_UTTERANCE = longPreferencesKey("vad_max_utterance_ms")
        val FOLLOW_UP = intPreferencesKey("follow_up_seconds")
        val AUDIO_RESUME_DELAY = longPreferencesKey("audio_resume_delay_ms")
        val ASR_MODEL_ID = stringPreferencesKey("asr_model_id")
        val MUSIC_APP_NAME = stringPreferencesKey("music_app_name")
        val MUSIC_PACKAGE_NAME = stringPreferencesKey("music_package_name")
        val MUSIC_SERVICE_NAME = stringPreferencesKey("music_service_name")
        val NAV_APP_NAME = stringPreferencesKey("nav_app_name")
        val NAV_PACKAGE_NAME = stringPreferencesKey("nav_package_name")
        val VOICE_VOLUME_CONTROL = booleanPreferencesKey("voice_volume_control")
        val LLM_CORRECTION_ENABLED = booleanPreferencesKey("llm_correction_enabled")
        val TTS_ENGINE = stringPreferencesKey("tts_engine")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val TTS_VOLUME = floatPreferencesKey("tts_volume")
        val SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val PITCH = floatPreferencesKey("tts_pitch")
        val CONNECTION_VERIFIED = booleanPreferencesKey("llm_connection_verified")
    }
}
