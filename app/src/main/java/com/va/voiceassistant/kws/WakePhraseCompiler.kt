package com.va.voiceassistant.kws

import android.content.res.AssetManager
import android.icu.text.Transliterator
import android.os.Build
import com.va.voiceassistant.data.AssistantSettings
import java.text.Normalizer
import java.util.Locale

object WakePhraseCompiler {
    fun compile(assetManager: AssetManager, value: String): String {
        val phrase = value.trim()
        validateCharacters(phrase)
        if (phrase == AssistantSettings.DEFAULT_WAKE_PHRASE) return KwsModelPack.DEFAULT_KEYWORD_SPEC
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw WakePhraseException("自訂喚醒詞需要 Android 10 以上版本")
        }

        val romanized = Normalizer.normalize(
            Transliterator.getInstance("Han-Latin").transliterate(phrase).lowercase(Locale.ROOT),
            Normalizer.Form.NFC,
        )
        val tokenVocabulary = assetManager.open(KwsModelPack.TOKENS).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.substringBefore(' ').trim() }
                .filter { it.isNotEmpty() && !it.startsWith('<') }
                .toSet()
        }
        val tokens = tokenizeRomanization(romanized, tokenVocabulary)
            ?: throw WakePhraseException("無法建立「$phrase」的喚醒發音，請換一個常用中文詞")
        return "${tokens.joinToString(" ")} @$phrase"
    }

    internal fun tokenizeRomanization(romanized: String, vocabulary: Set<String>): List<String>? {
        val syllables = PINYIN_SYLLABLE.findAll(romanized).map { it.value }.toList()
        if (syllables.isEmpty()) return null
        val result = ArrayList<String>()
        for (syllable in syllables) {
            val pieces = tokenizeSyllable(syllable, vocabulary) ?: return null
            result += pieces
        }
        return result
    }

    private fun tokenizeSyllable(syllable: String, vocabulary: Set<String>): List<String>? {
        val candidates = vocabulary.filter { token -> token.all { it.isLowerCase() || it in PINYIN_MARKS } }
            .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
        val memo = HashMap<Int, List<String>?>()

        fun solve(index: Int): List<String>? {
            if (index == syllable.length) return emptyList()
            if (memo.containsKey(index)) return memo[index]
            for (token in candidates) {
                if (syllable.startsWith(token, index)) {
                    val suffix = solve(index + token.length)
                    if (suffix != null) return (listOf(token) + suffix).also { memo[index] = it }
                }
            }
            memo[index] = null
            return null
        }

        return solve(0)
    }

    private fun validateCharacters(phrase: String) {
        val count = phrase.codePointCount(0, phrase.length)
        if (count !in 2..AssistantSettings.MAX_WAKE_PHRASE_LENGTH) {
            throw WakePhraseException("喚醒詞請輸入 2–${AssistantSettings.MAX_WAKE_PHRASE_LENGTH} 個中文字")
        }
        if (phrase.codePoints().anyMatch { codePoint ->
                Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN
            }
        ) {
            throw WakePhraseException("目前喚醒模型只支援中文喚醒詞")
        }
    }

    private val PINYIN_SYLLABLE = Regex("[a-züāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜńňǹḿ]+")
    private val PINYIN_MARKS = setOf('ü', 'ā', 'á', 'ǎ', 'à', 'ē', 'é', 'ě', 'è', 'ī', 'í', 'ǐ', 'ì', 'ō', 'ó', 'ǒ', 'ò', 'ū', 'ú', 'ǔ', 'ù', 'ǖ', 'ǘ', 'ǚ', 'ǜ', 'ń', 'ň', 'ǹ', 'ḿ')
}

class WakePhraseException(message: String) : IllegalArgumentException(message)
