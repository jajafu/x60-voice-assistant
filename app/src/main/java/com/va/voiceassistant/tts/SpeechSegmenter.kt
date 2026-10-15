package com.va.voiceassistant.tts

class SpeechSegmenter(
    private val safeLength: Int = 96,
) {
    private val pending = StringBuilder()

    init {
        require(safeLength in 24..300)
    }

    fun accept(chunk: String): List<String> {
        pending.append(chunk)
        return drain(allowRemainder = false)
    }

    /** Flush the last complete response only after the provider ended successfully. */
    fun finish(success: Boolean): List<String> = if (success) {
        drain(allowRemainder = true)
    } else {
        clear()
        emptyList()
    }

    fun clear() = pending.clear()

    private fun drain(allowRemainder: Boolean): List<String> {
        val result = mutableListOf<String>()
        while (pending.isNotEmpty()) {
            val sentenceEnd = pending.indexOfFirst { it in SENTENCE_ENDINGS }
            val cut = when {
                sentenceEnd in 0 until safeLength -> sentenceEnd + 1
                pending.length >= safeLength -> safeCutIndex(pending.toString(), safeLength)
                allowRemainder -> pending.length
                else -> break
            }
            val cleaned = MarkdownSpeechCleaner.clean(pending.substring(0, cut))
            pending.delete(0, cut)
            if (cleaned.isNotBlank()) result += cleaned
        }
        return result
    }

    private fun safeCutIndex(value: String, limit: Int): Int {
        val lowerBound = limit / 2
        for (index in limit.coerceAtMost(value.lastIndex) downTo lowerBound) {
            if (value[index].isWhitespace() || value[index] in SAFE_BREAKS) return index + 1
        }
        return limit.coerceAtMost(value.length)
    }

    companion object {
        private val SENTENCE_ENDINGS = setOf('。', '？', '！', '?', '!', '\n')
        private val SAFE_BREAKS = setOf('，', '、', ',', ';', '；', ':', '：')
    }
}
