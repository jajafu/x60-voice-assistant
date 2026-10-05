package com.jajafu.x60assistant.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class CorrectedIntent {
    NAVIGATE,
    MUSIC_SEARCH,
    CHAT,
    UNKNOWN,
}

data class CorrectedTranscript(
    val corrected: String,
    val intent: CorrectedIntent,
    val destination: String? = null,
    val query: String? = null,
)

interface TranscriptCorrector {
    suspend fun correct(transcript: String, requestId: String): CorrectedTranscript?
}

/**
 * X-ASR 同音錯字的 LLM 校正（Typeless 兩段式）。
 *
 * 本機 Router 先命中就直接執行不上網；只有全部 miss 才呼叫一次。
 * 回傳只接受固定 JSON，解析失敗就回 null 让呼叫方維持舊行為。
 */
class LlmTranscriptCorrector(
    private val provider: LlmProvider,
) : TranscriptCorrector {
    override suspend fun correct(transcript: String, requestId: String): CorrectedTranscript? {
        if (transcript.isBlank()) return null
        val text = StringBuilder()
        provider.streamChat(buildRequest(transcript, requestId)).collect { text.append(it.text) }
        return parseJson(text.toString())
    }

    companion object {
        const val CORRECTION_SYSTEM_PROMPT =
            "你是繁體中文語音辨識校正器。輸入是中文ASR逐字稿，可能有同音錯字。" +
                "只做同音/形近修正，不擴寫、不回答問題。" +
                "判斷意圖：navigate（導航/帶我去/我要去）、music_search（播放/放歌/點歌）、chat、unknown。" +
                "只能回JSON，不要其他文字：" +
                "{\"corrected\":\"...\",\"intent\":\"navigate|music_search|chat|unknown\"," +
                "\"destination\":\"地點或null\",\"query\":\"歌名或null\"}。" +
                "destination只放地點名，query只放歌名/歌手，字數與原文相近。"

        const val CORRECTION_MAX_TOKENS = 128

        fun buildRequest(transcript: String, requestId: String): ChatRequest = ChatRequest(
            messages = listOf(
                ChatMessage("system", CORRECTION_SYSTEM_PROMPT),
                ChatMessage("user", transcript),
            ),
            maxOutputTokens = CORRECTION_MAX_TOKENS,
            temperature = 0.0,
            requestId = requestId,
        )

        @Serializable
        private data class RawCorrection(
            val corrected: String = "",
            val intent: String = "unknown",
            val destination: String? = null,
            val query: String? = null,
            val song: String? = null,
        )

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parseJson(raw: String): CorrectedTranscript? {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val parsed = runCatching {
                json.decodeFromString<RawCorrection>(raw.substring(start, end + 1))
            }.getOrNull() ?: return null
            val corrected = parsed.corrected.trim()
            if (corrected.isEmpty() || corrected.length > 200) return null
            val intent = when (parsed.intent.trim().lowercase()) {
                "navigate", "導航" -> CorrectedIntent.NAVIGATE
                "music_search", "music", "音樂" -> CorrectedIntent.MUSIC_SEARCH
                "chat" -> CorrectedIntent.CHAT
                else -> CorrectedIntent.UNKNOWN
            }
            val destination = parsed.destination?.trim()?.takeIf { it.length in 2..60 }
            val query = (parsed.query ?: parsed.song)?.trim()?.takeIf { it.length in 2..100 }
            if (intent == CorrectedIntent.NAVIGATE && destination == null) return null
            if (intent == CorrectedIntent.MUSIC_SEARCH && query == null) return null
            return CorrectedTranscript(corrected, intent, destination, query)
        }
    }
}
