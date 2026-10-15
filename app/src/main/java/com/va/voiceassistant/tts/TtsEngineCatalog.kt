package com.va.voiceassistant.tts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class TtsEngineOption(val packageName: String, val label: String, val isSystemDefault: Boolean)

data class TtsVoiceOption(
    val name: String,
    val label: String,
    val locale: String,
    val requiresNetwork: Boolean,
)

/**
 * 讀取裝置上可用的 TTS 引擎與 voice。
 *
 * 引擎清單來自 PackageManager，成本低。voice 清單必須實際綁定引擎才取得
 * （EngineInfo.voices 在未綁定時是空的），因此每次查詢都會建立並關閉一個暫時的
 * TextToSpeech。查詢在 IO 執行緒進行並帶逾時，引擎無回應時不會卡住設定頁。
 */
class TtsEngineCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun engines(defaultEnginePackage: String?): List<TtsEngineOption> {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                packageManager.queryIntentServices(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                )
            }.getOrNull().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            runCatching { packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL) }
                .getOrNull().orEmpty()
        }
        return services.mapNotNull { resolveInfo ->
            val packageInfo = resolveInfo.serviceInfo ?: return@mapNotNull null
            val packageName = packageInfo.packageName
            val label = runCatching { resolveInfo.loadLabel(packageManager).toString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: packageName
            TtsEngineOption(
                packageName = packageName,
                label = label,
                isSystemDefault = packageName.equals(defaultEnginePackage, ignoreCase = true),
            )
        }.distinctBy { it.packageName }.sortedWith(
            compareByDescending<TtsEngineOption> { it.isSystemDefault }.thenBy { it.label },
        )
    }

    /** 綁定 [enginePackage] 以取得其 voice；留空表示使用系統預設引擎。 */
    suspend fun voices(enginePackage: String?): List<TtsVoiceOption> = withContext(Dispatchers.IO) {
        val engine = withTimeoutOrNull(BIND_TIMEOUT_MS) { bind(enginePackage) } ?: return@withContext emptyList()
        try {
            engine.voices.orEmpty().map { voice ->
                TtsVoiceOption(
                    name = voice.name,
                    label = voice.name,
                    locale = voice.locale.toLanguageTag(),
                    requiresNetwork = voice.isNetworkConnectionRequired,
                )
            }
        } finally {
            runCatching { engine.shutdown() }
        }
    }

    private suspend fun bind(enginePackage: String?): TextToSpeech? =
        suspendCancellableCoroutine { continuation ->
            // Android 可能在建構子賦值完成前就回報初始化結果，因此先保留區域變數再讀取。
            var instance: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { result ->
                val created = instance
                if (!continuation.isActive) {
                    created?.shutdown()
                    return@OnInitListener
                }
                if (result == TextToSpeech.SUCCESS && created != null) {
                    continuation.resume(created)
                } else {
                    created?.shutdown()
                    continuation.resume(null)
                }
            }
            instance = if (enginePackage.isNullOrBlank()) {
                TextToSpeech(appContext, listener)
            } else {
                TextToSpeech(appContext, listener, enginePackage)
            }
            continuation.invokeOnCancellation { instance?.shutdown() }
        }

    companion object {
        private const val BIND_TIMEOUT_MS = 4_000L

        /**
         * 只顯示中文 voice。
         *
         * 裝置上的引擎常回報數十個語言（測試手機實測曾有 30 個以上，例如 vivo X60），但小助手只需要繁中與
         * 簡中。引擎無法要求刪除 voice，因此這裡只縮限**設定頁顯示的清單**；引擎仍保有全部
         * voice，萬一中文語音損壞仍可由系統層改用其他語言。
         *
         * 引擎若完全沒有中文 voice，仍顯示完整清單而不是空白，避免使用者無從選擇。
         */
        fun preferredOrder(voices: List<TtsVoiceOption>): List<TtsVoiceOption> {
            val chinese = voices.filter { it.locale.isChineseLocale() }
            return chinese.ifEmpty { voices }.sortedWith(voiceOptionOrder)
        }

        /**
         * 繁中 voice 中，供 [AndroidTtsEngine] 自動選擇使用的排序。與 [preferredOrder] 同一套
         * 規則，讓清單第一個就是自動會被選到的那一個。
         */
        fun chineseVoices(voices: Collection<Voice>): List<Voice> = voices
            .filter { it.locale.toLanguageTag().isChineseLocale() }
            .sortedWith(
                compareBy(
                    { if (it.locale.toLanguageTag().isTraditionalChineseLocale()) 0 else 1 },
                    { if (it.isNetworkConnectionRequired) 1 else 0 },
                    { it.name },
                ),
            )
    }
}

private const val LANGUAGE_ZH = "zh"
private const val SCRIPT_HANT = "Hant"
private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")

/**
 * 繁中優先、離線優先，再依名稱。
 *
 * 繁中的判斷同時支援以書寫系統（zh-Hant）與以地區（zh-TW）標記的 locale：部分引擎（如 vivo）回報的
 * 是 `zh-Hant`，若只比對 country 會永遠落空而退到「任何中文」。
 */
private val voiceOptionOrder = compareBy<TtsVoiceOption>(
    { if (it.locale.isTraditionalChineseLocale()) 0 else 1 },
    { if (it.requiresNetwork) 1 else 0 },
    { it.label },
)

/** 語言代碼是否為中文。相容 `zh`、`zh-Hant`、`zh-TW` 等標記。 */
internal fun String.isChineseLocale(): Boolean =
    Locale.forLanguageTag(this).language.equals(LANGUAGE_ZH, ignoreCase = true)

/** 是否為繁體中文。相容以書寫系統（zh-Hant）與以地區（zh-TW／HK／MO）標記的 locale。 */
internal fun String.isTraditionalChineseLocale(): Boolean {
    val parsed = Locale.forLanguageTag(this)
    return parsed.script.equals(SCRIPT_HANT, ignoreCase = true) ||
        parsed.country.uppercase(Locale.ROOT) in TRADITIONAL_REGIONS
}

/** 顯示用文字：voice 名稱、語言，以及是否需要網路。 */
fun TtsVoiceOption.displayLabel(): String = buildString {
    append(label)
    append("（")
    append(locale.ifBlank { "未知語言" })
    append(if (requiresNetwork) "・需要網路" else "・離線")
    append("）")
}
