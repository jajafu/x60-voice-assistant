package com.jajafu.x60assistant.data

data class AssistantSettings(
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
    val ttsEngine: String = "",
    val ttsVoice: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val connectionVerified: Boolean = false,
) {
    fun normalized(): AssistantSettings = copy(
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
        speechRate = speechRate.coerceIn(0.5f, 2.0f),
        pitch = pitch.coerceIn(0.5f, 2.0f),
    )

    companion object {
        const val DEFAULT_ASR_MODEL_ID =
            "sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01"
        const val DEFAULT_SYSTEM_INSTRUCTION = """使用繁體中文口語回答。先回答重點，再補充必要說明。
回答將由語音朗讀，避免 Markdown 表格、過多符號、長網址與太長的清單。
若答案很長，先提供簡短版本，再詢問是否要繼續。
不知道時直接說明，不要捏造資料。"""
    }
}
