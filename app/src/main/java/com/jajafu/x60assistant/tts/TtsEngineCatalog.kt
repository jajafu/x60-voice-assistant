package com.jajafu.x60assistant.tts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * 偏好排序：中文優先、離線優先、zh-TW 優先，再依名稱。與 [AndroidTtsEngine] 的
     * 自動選擇順序一致，讓清單第一個就是實際會被用到的 voice。
     */
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
        private const val LOCALE_ZH = "zh"
        private const val LOCALE_ZH_TW = "zh-TW"

        /**
         * 偏好排序：中文優先、離線優先、zh-TW 優先，再依名稱。與 [AndroidTtsEngine] 的
         * 自動選擇順序一致，讓清單第一個就是實際會被用到的 voice。不依賴 Context，可測。
         */
        fun preferredOrder(voices: List<TtsVoiceOption>): List<TtsVoiceOption> = voices.sortedWith(
            compareBy(
                { if (it.locale.startsWith(LOCALE_ZH)) 0 else 1 },
                { if (it.requiresNetwork) 1 else 0 },
                { if (it.locale.startsWith(LOCALE_ZH_TW)) 0 else 1 },
                { it.label },
            ),
        )
    }
}

/** 顯示用文字：voice 名稱、語言，以及是否需要網路。 */
fun TtsVoiceOption.displayLabel(): String = buildString {
    append(label)
    append("（")
    append(locale.ifBlank { "未知語言" })
    append(if (requiresNetwork) "・需要網路" else "・離線")
    append("）")
}
