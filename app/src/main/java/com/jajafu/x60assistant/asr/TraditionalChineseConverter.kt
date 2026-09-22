package com.jajafu.x60assistant.asr

import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi

class TraditionalChineseConverter {
    fun convert(text: String): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Api29.convert(text)
    } else {
        text
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private object Api29 {
        private val transliterator: Transliterator? = listOf("Simplified-Traditional", "Hans-Hant")
            .firstOrNull { candidate -> Transliterator.getAvailableIDs().asSequence().any { it == candidate } }
            ?.let(Transliterator::getInstance)

        fun convert(text: String): String = transliterator?.transliterate(text) ?: text
    }
}
