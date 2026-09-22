package com.jajafu.x60assistant.llm

class ConversationHistory(private val maxTurns: Int) {
    private val turns = ArrayDeque<Pair<String, String>>()

    init {
        require(maxTurns in 0..12)
    }

    @Synchronized
    fun messages(systemInstruction: String, currentUserText: String): List<ChatMessage> = buildList {
        if (systemInstruction.isNotBlank()) add(ChatMessage("system", systemInstruction))
        turns.forEach { (user, assistant) ->
            add(ChatMessage("user", user))
            add(ChatMessage("assistant", assistant))
        }
        add(ChatMessage("user", currentUserText))
    }

    @Synchronized
    fun add(user: String, assistant: String) {
        if (maxTurns == 0) return
        turns.addLast(user to assistant)
        while (turns.size > maxTurns) turns.removeFirst()
    }

    @Synchronized
    fun clear() = turns.clear()
}
