package com.va.voiceassistant.llm

class SseParser(private val onData: (String) -> Unit) {
    private val dataLines = mutableListOf<String>()

    fun acceptLine(line: String?) {
        if (line == null || line.isEmpty()) {
            flush()
            return
        }
        if (line.startsWith(':')) return
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(' ')) value = value.drop(1)
        if (field == "data") dataLines += value
    }

    fun finish() = flush()

    private fun flush() {
        if (dataLines.isEmpty()) return
        onData(dataLines.joinToString("\n"))
        dataLines.clear()
    }
}
