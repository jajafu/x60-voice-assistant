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
    val followUpSeconds: Int = 10,
    val audioResumeDelayMs: Long = 300,
    val asrModelId: String = DEFAULT_ASR_MODEL_ID,
    val musicAppName: String = DEFAULT_MUSIC_APP_NAME,
    val musicPackageName: String = DEFAULT_MUSIC_PACKAGE_NAME,
    val musicServiceName: String = DEFAULT_MUSIC_SERVICE_NAME,
    val ttsEngine: String = "",
    val ttsVoice: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val connectionVerified: Boolean = false,
) {
    fun normalized(): AssistantSettings = copy(
        wakePhrase = wakePhrase.trim().ifBlank { DEFAULT_WAKE_PHRASE }.take(MAX_WAKE_PHRASE_LENGTH),
        wakeAcknowledgement = wakeAcknowledgement.trim()
            .ifBlank { DEFAULT_WAKE_ACKNOWLEDGEMENT }
            .take(MAX_WAKE_ACKNOWLEDGEMENT_LENGTH),
        temperature = temperature.coerceIn(0.0, 2.0),
        maxOutputTokens = maxOutputTokens.coerceIn(16, 4_096),
        connectTimeoutSeconds = connectTimeoutSeconds.coerceIn(3, 30),
        readTimeoutSeconds = readTimeoutSeconds.coerceIn(10, 120),
        contextTurns = contextTurns.coerceIn(0, 12),
        waitForSpeechMs = waitForSpeechMs.coerceIn(3_000, 10_000),
        minSpeechMs = minSpeechMs.coerceIn(150, 600),
        trailingSilenceMs = trailingSilenceMs.coerceIn(500, 1_500),
        maxUtteranceMs = maxUtteranceMs.coerceIn(10_000, 60_000),
        followUpSeconds = followUpSeconds.coerceIn(0, 30),
        audioResumeDelayMs = audioResumeDelayMs.coerceIn(250, 500),
        asrModelId = DEFAULT_ASR_MODEL_ID,
        musicAppName = musicAppName.trim().take(100),
        musicPackageName = musicPackageName.trim().take(200),
        musicServiceName = musicServiceName.trim().take(300),
        speechRate = speechRate.coerceIn(0.5f, 2.0f),
        pitch = pitch.coerceIn(0.5f, 2.0f),
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
        const val DEFAULT_SYSTEM_INSTRUCTION = """使用繁體中文口語回答。先回答重點，50字以內，有必要我會再補充請你說明。
回答將由語音朗讀，避免 Markdown格式碼、emoji、表格、過多符號、網址與太長的清單。記得移除所有引用標記或引用來源。
若答案很長，先提供簡短版本，再詢問是否要繼續。
不知道時直接說明，不要捏造資料。"""
    }
}
