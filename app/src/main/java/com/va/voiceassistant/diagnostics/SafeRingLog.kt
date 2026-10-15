package com.va.voiceassistant.diagnostics

data class SafeLogEntry(
    val elapsedRealtimeMs: Long,
    val event: String,
    val errorCode: VoiceErrorCode? = null,
    val detail: String? = null,
)

class SafeRingLog(private val capacity: Int = 200) {
    init {
        require(capacity > 0)
    }

    private val entries = ArrayDeque<SafeLogEntry>(capacity)

    @Synchronized
    fun add(entry: SafeLogEntry) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(
            entry.copy(
                event = sanitize(entry.event),
                detail = entry.detail?.let(::sanitize),
            ),
        )
    }

    @Synchronized
    fun snapshot(): List<SafeLogEntry> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()

    private fun sanitize(value: String): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .take(MAX_FIELD_LENGTH)

    companion object {
        private const val MAX_FIELD_LENGTH = 160
    }
}
