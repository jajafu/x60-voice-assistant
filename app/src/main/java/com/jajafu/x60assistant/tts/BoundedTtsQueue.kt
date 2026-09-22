package com.jajafu.x60assistant.tts

class BoundedTtsQueue(private val capacity: Int = 12) {
    private val values = ArrayDeque<String>()
    var highWaterMark: Int = 0
        private set
    var droppedCount: Long = 0
        private set

    init {
        require(capacity > 0)
    }

    @Synchronized
    fun offer(sentence: String): Boolean {
        if (sentence.isBlank()) return true
        if (values.size >= capacity) {
            droppedCount++
            return false
        }
        values.addLast(sentence)
        highWaterMark = maxOf(highWaterMark, values.size)
        return true
    }

    @Synchronized
    fun poll(): String? = values.removeFirstOrNull()

    @Synchronized
    fun clear() = values.clear()

    @get:Synchronized
    val size: Int get() = values.size
}
