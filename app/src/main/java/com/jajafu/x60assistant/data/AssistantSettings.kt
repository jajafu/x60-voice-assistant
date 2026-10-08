package com.jajafu.x60assistant.data

data class AssistantSettings(
    val autoStartListening: Boolean = false,
    val wakePhrase: String = DEFAULT_WAKE_PHRASE,
    val wakeAcknowledgement: String = DEFAULT_WAKE_ACKNOWLEDGEMENT,
    val baseUrl: String = "",
    val model: String = "",
    val endpoint: String = "/chat/completions",
    val streaming: Boolean = true,
    val temperature: Double = 0.3,
    val maxOutputTokens: Int = 512,
    val connectTimeoutSeconds: Int = 10,
    val readTimeoutSeconds: Int = 45,
    val contextTurns: Int = 6,
    val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    val waitForSpeechMs: Long = 5_000,
    val minSpeechMs: Long = 250,
    val trailingSilenceMs: Long = 800,
    val maxUtteranceMs: Long = 20_000,
    val followUpSeconds: Int = DEFAULT_FOLLOW_UP_SECONDS,
    val audioResumeDelayMs: Long = 300,
    val asrModelId: String = DEFAULT_ASR_MODEL_ID,
    val musicAppName: String = DEFAULT_MUSIC_APP_NAME,
    val musicPackageName: String = DEFAULT_MUSIC_PACKAGE_NAME,
    val musicServiceName: String = DEFAULT_MUSIC_SERVICE_NAME,
    val navAppName: String = DEFAULT_NAV_APP_NAME,
    val navPackageName: String = DEFAULT_NAV_PACKAGE_NAME,
    val voiceVolumeControl: Boolean = DEFAULT_VOICE_VOLUME_CONTROL,
    val llmCorrectionEnabled: Boolean = DEFAULT_LLM_CORRECTION_ENABLED,
    val ttsEngine: String = "",
    val ttsVoice: String = "",
    val ttsVolume: Float = DEFAULT_TTS_VOLUME,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val connectionVerified: Boolean = false,
    val cloudTtsEnabled: Boolean = DEFAULT_CLOUD_TTS_ENABLED,
    val cloudTtsModel: String = DEFAULT_CLOUD_TTS_MODEL,
    val cloudTtsVoice: String = DEFAULT_CLOUD_TTS_VOICE,
    val cloudAsrEnabled: Boolean = DEFAULT_CLOUD_ASR_ENABLED,
    val cloudAsrModel: String = DEFAULT_CLOUD_ASR_MODEL,
    val cloudAsrLanguage: String = "",
) {
    /**
     * 雲端語音是否可啟用。沿用 LLM 的 Base URL 與 API Key，因此只檢查開關與 Key；
     * Model 欄位在 [normalized] 已回填預設值，不會是空白。
     */
    fun cloudTtsReady(hasApiKey: Boolean): Boolean = cloudTtsEnabled && hasApiKey

    fun cloudAsrReady(hasApiKey: Boolean): Boolean = cloudAsrEnabled && hasApiKey
    fun normalized(): AssistantSettings = copy(
        wakePhrase = wakePhrase.trim().ifBlank { DEFAULT_WAKE_PHRASE }.take(MAX_WAKE_PHRASE_LENGTH),
        wakeAcknowledgement = wakeAcknowledgement.trim()
            .ifBlank { DEFAULT_WAKE_ACKNOWLEDGEMENT }
            .take(MAX_WAKE_ACKNOWLEDGEMENT_LENGTH),
        temperature = temperature.coerceIn(TEMPERATURE_RANGE),
        maxOutputTokens = maxOutputTokens.coerceIn(MAX_OUTPUT_TOKENS_RANGE),
        connectTimeoutSeconds = connectTimeoutSeconds.coerceIn(CONNECT_TIMEOUT_SECONDS_RANGE),
        readTimeoutSeconds = readTimeoutSeconds.coerceIn(READ_TIMEOUT_SECONDS_RANGE),
        contextTurns = contextTurns.coerceIn(CONTEXT_TURNS_RANGE),
        waitForSpeechMs = waitForSpeechMs.coerceIn(WAIT_FOR_SPEECH_MS_RANGE),
        minSpeechMs = minSpeechMs.coerceIn(MIN_SPEECH_MS_RANGE),
        trailingSilenceMs = trailingSilenceMs.coerceIn(TRAILING_SILENCE_MS_RANGE),
        maxUtteranceMs = maxUtteranceMs.coerceIn(MAX_UTTERANCE_MS_RANGE),
        followUpSeconds = followUpSeconds.coerceIn(FOLLOW_UP_SECONDS_RANGE),
        audioResumeDelayMs = audioResumeDelayMs.coerceIn(AUDIO_RESUME_DELAY_MS_RANGE),
        asrModelId = DEFAULT_ASR_MODEL_ID,
        musicAppName = musicAppName.trim().take(100),
        musicPackageName = musicPackageName.trim().take(200),
        musicServiceName = musicServiceName.trim().take(300),
        navAppName = navAppName.trim().take(100),
        navPackageName = navPackageName.trim().take(200),
        ttsEngine = ttsEngine.trim().take(MAX_TTS_IDENTIFIER_LENGTH),
        ttsVoice = ttsVoice.trim().take(MAX_TTS_IDENTIFIER_LENGTH),
        ttsVolume = ttsVolume.coerceIn(TTS_VOLUME_RANGE),
        speechRate = speechRate.coerceIn(SPEECH_RATE_RANGE),
        pitch = pitch.coerceIn(PITCH_RANGE),
        cloudTtsModel = cloudTtsModel.trim().ifBlank { DEFAULT_CLOUD_TTS_MODEL }.take(MAX_CLOUD_IDENTIFIER_LENGTH),
        cloudTtsVoice = cloudTtsVoice.trim().ifBlank { DEFAULT_CLOUD_TTS_VOICE }.take(MAX_CLOUD_IDENTIFIER_LENGTH),
        cloudAsrModel = cloudAsrModel.trim().ifBlank { DEFAULT_CLOUD_ASR_MODEL }.take(MAX_CLOUD_IDENTIFIER_LENGTH),
        cloudAsrLanguage = cloudAsrLanguage.trim().take(MAX_CLOUD_IDENTIFIER_LENGTH),
    )

    companion object {
        const val DEFAULT_WAKE_PHRASE = "小助手"
        const val MAX_WAKE_PHRASE_LENGTH = 8
        const val DEFAULT_WAKE_ACKNOWLEDGEMENT = "我在"
        const val MAX_WAKE_ACKNOWLEDGEMENT_LENGTH = 20
        const val DEFAULT_ASR_MODEL_ID =
            "sherpa-onnx-x-asr-zipformer-transducer-zh-en-punct-int8-2026-06-03"
        const val DEFAULT_MUSIC_APP_NAME = "MusicCabin"
        const val DEFAULT_MUSIC_PACKAGE_NAME = "com.jajafu.musiccabin"
        const val DEFAULT_MUSIC_SERVICE_NAME = "com.metrolist.music.playback.MusicService"

        /**
         * 導航 App 預設為 Google 地圖。已於 2026-09-30 於 vivo X60（Android 13）確認
         * `https://www.google.com/maps/dir/?api=1&destination=<地點>` 可直達路線預覽頁。
         * 高德地圖（`androidamap://route/plan/?dname=`）已實測可用但需多一次選點，尚未納入本輪。
         */
        const val DEFAULT_NAV_APP_NAME = "Google 地圖"
        const val DEFAULT_NAV_PACKAGE_NAME = "com.google.android.apps.maps"

        /**
         * 語音調整手機音量的總開關，依 `docs/phone-control-plan.md` §5 要求可隨時關閉手機操作。
         * 關閉時 `VoiceForegroundService` 不建立音量執行器，指令會落到一般問答流程。
         */
        const val DEFAULT_VOICE_VOLUME_CONTROL = true

        /**
         * X-ASR 同音錯字的 LLM 校正總開關（Issue #24）。
         * 開啟時本機 Router 全 miss 才多打一次 LLM，關閉即回舊行為不上網。
         */
        const val DEFAULT_LLM_CORRECTION_ENABLED = true

        /**
         * TTS 輸出的增益，套用到 TextToSpeech.Engine.KEY_PARAM_VOLUME。
         * 1.0 是引擎預設值；Android 標示範圍為 0.0–1.0，超過 1.0 是否放大取決於引擎，
         * 可能破音或被忽略，因此預設維持 1.0 以維持既有行為。
         */
        const val DEFAULT_TTS_VOLUME = 1.0f
        const val MAX_TTS_IDENTIFIER_LENGTH = 200

        /**
         * 雲端語音 spike 設定。開關預設全關，既有行為維持純本機；
         * 兩者皆沿用 LLM 的 Base URL 與 API Key，不另存憑證。
         * TTS 單次上限 4096 字元依 OpenAI `/audio/speech` 文件。
         */
        const val DEFAULT_CLOUD_TTS_ENABLED = false
        const val DEFAULT_CLOUD_TTS_MODEL = "gpt-4o-mini-tts"
        const val DEFAULT_CLOUD_TTS_VOICE = "alloy"
        const val DEFAULT_CLOUD_ASR_ENABLED = false
        const val DEFAULT_CLOUD_ASR_MODEL = "whisper-1"
        const val MAX_CLOUD_IDENTIFIER_LENGTH = 100
        const val CLOUD_TTS_MAX_CHARS = 4096

        /**
         * 喚醒詞開啟新一輪時會清除對話歷史，只有這個視窗內的追問才帶著上一輪。
         * 預設 3 秒：只足夠接一句「那還有呢」，不會把無關話題拖進下一輪提問。
         */
        const val DEFAULT_FOLLOW_UP_SECONDS = 3
        const val DEFAULT_SYSTEM_INSTRUCTION = """使用繁體中文口語回答。先回答重點，50字以內，有必要我會再補充請你說明。
回答將由語音朗讀，避免 Markdown格式碼、emoji、表格、過多符號、網址與太長的清單。記得移除所有引用標記或引用來源。
若答案很長，先提供簡短版本，再詢問是否要繼續。
不知道時直接說明，不要捏造資料。"""

        // 設定頁顯示的上下限、欄位驗證與 normalized() 的夾限共用同一組常數。
        val TEMPERATURE_RANGE = 0.0..2.0
        val MAX_OUTPUT_TOKENS_RANGE = 16..4_096
        val CONNECT_TIMEOUT_SECONDS_RANGE = 3..30
        val READ_TIMEOUT_SECONDS_RANGE = 10..120
        val CONTEXT_TURNS_RANGE = 0..12
        val FOLLOW_UP_SECONDS_RANGE = 0..30
        val WAIT_FOR_SPEECH_MS_RANGE = 3_000L..10_000L
        val MIN_SPEECH_MS_RANGE = 150L..600L
        val TRAILING_SILENCE_MS_RANGE = 500L..1_500L
        val MAX_UTTERANCE_MS_RANGE = 10_000L..60_000L
        val AUDIO_RESUME_DELAY_MS_RANGE = 250L..500L
        val TTS_VOLUME_RANGE = 0.0f..2.0f
        val SPEECH_RATE_RANGE = 0.5f..2.0f
        val PITCH_RANGE = 0.5f..2.0f
        val WAIT_FOR_SPEECH_SECONDS_RANGE =
            (WAIT_FOR_SPEECH_MS_RANGE.first / 1_000.0)..(WAIT_FOR_SPEECH_MS_RANGE.last / 1_000.0)
        val MAX_UTTERANCE_SECONDS_RANGE =
            (MAX_UTTERANCE_MS_RANGE.first / 1_000.0)..(MAX_UTTERANCE_MS_RANGE.last / 1_000.0)
    }
}
