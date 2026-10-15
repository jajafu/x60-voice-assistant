package com.va.voiceassistant.asr

import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi
import java.text.Normalizer

class TraditionalChineseConverter {
    fun convert(text: String): String {
        val compatible = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(INVISIBLE_FORMATTING, "")
        val traditional = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Api29.convert(compatible)
        } else {
            compatible
        }
        return traditional.replace(SPACES_BETWEEN_HAN, "").trim()
    }

    companion object {
        private val INVISIBLE_FORMATTING = Regex("[\\u200B-\\u200D\\u2060\\uFEFF]")
        private val SPACES_BETWEEN_HAN = Regex("(?<=\\p{IsHan})\\s+(?=\\p{IsHan})")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private object Api29 {
        private val transliterator: Transliterator? = listOf("Simplified-Traditional", "Hans-Hant")
            .firstOrNull { candidate -> Transliterator.getAvailableIDs().asSequence().any { it == candidate } }
            ?.let(Transliterator::getInstance)

        fun convert(text: String): String = transliterator?.transliterate(text) ?: text
    }
}
