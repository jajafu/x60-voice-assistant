package com.jajafu.x60assistant.tts

object MarkdownSpeechCleaner {
    private val link = Regex("\\[([^]]+)]\\((?:[^()]|\\([^)]*\\))+\\)")
    private val image = Regex("!\\[([^]]*)](?:\\([^)]*\\))")
    private val inlineCode = Regex("`([^`]+)`")
    private val bareUrl = Regex("\\s*https?://\\S+", RegexOption.IGNORE_CASE)
    private val listPrefix = Regex("(?m)^\\s*(?:#{1,6}|[-*+] |\\d+[.)] )\\s*")
    private val markdownMarks = Regex("[*_~>|]")
    private val whitespace = Regex("\\s+")

    fun clean(value: String): String = value
        .replace(Regex("(?s)```.*?```"), " ")
        .replace(image) { it.groupValues[1] }
        .replace(link) { it.groupValues[1] }
        .replace(inlineCode) { it.groupValues[1] }
        .replace(bareUrl, "連結")
        .replace(listPrefix, "")
        .replace(markdownMarks, "")
        .replace(whitespace, " ")
        .trim()
}
