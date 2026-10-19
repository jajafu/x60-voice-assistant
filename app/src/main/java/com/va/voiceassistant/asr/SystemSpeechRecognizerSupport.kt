package com.va.voiceassistant.asr

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 探測裝置上的系統語音辨識服務是否存在且支援中文。
 *
 * 探測通過＝服務在、語言清單含 zh 系；此時啟動服務可直接用系統辨識，不必先下載 X-ASR。
 * 通過時會帶回「選定的中文語言標籤」交給引擎（[SystemRecognitionProbe.Confirmed.language]），
 * 因為引擎只會以單一語言送出辨識請求：僅回報 zh-CN 的服務探測通過後也會以 zh-CN 聆聽，
 * 不會被迫用 zh-TW。探測失敗的原因放進 [SystemRecognitionProbe.Unavailable.reason] 供診斷顯示。
 */
object SystemSpeechRecognizerSupport {

    /** 系統辨識引擎對外的模型識別；診斷「實際載入的模型」會顯示這個值。 */
    const val MODEL_ID = "system/android-zh"

    /** 探測時送出的語言；服務回報的是整份支援清單，此值只影響清單的偏好排序。 */
    private const val QUERY_LANGUAGE = "zh-TW"

    /**
     * 引擎實際送辨識時的語言偏好順序：App 是繁中體驗，優先 zh-TW／zh-Hant 系；
     * 都沒有時仍可用 zh-CN 等其他中文變體。
     */
    private val LANGUAGE_PREFERENCE = listOf("zh-TW", "zh-Hant-TW", "zh-Hant", "zh-HK", "zh-MO", "zh-CN", "zh")

    sealed interface SystemRecognitionProbe {
        data class Confirmed(val language: String) : SystemRecognitionProbe

        /** reason 只是固定短代碼，不包含任何使用者內容。 */
        data class Unavailable(val reason: String) : SystemRecognitionProbe
    }

    /**
     * 啟動服務時呼叫一次。語言詳情是有序廣播，最長等 [timeoutMs]；
     * 逾時或無回覆視為「探測不到」，保守退回本機後備路徑。
     */
    suspend fun probe(context: Context, timeoutMs: Long = DEFAULT_TIMEOUT_MS): SystemRecognitionProbe {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return SystemRecognitionProbe.Unavailable("no-recognition-service")
        }
        val languages = withTimeoutOrNull(timeoutMs) { supportedLanguages(context) } ?: run {
            return SystemRecognitionProbe.Unavailable("language-details-unavailable")
        }
        val language = matchChinese(languages)
            ?: return SystemRecognitionProbe.Unavailable("chinese-unsupported")
        return SystemRecognitionProbe.Confirmed(language)
    }

    /** 依 [LANGUAGE_PREFERENCE] 挑出支援的中文；找不到 zh 系才回 null。 */
    fun matchChinese(languages: List<String>): String? {
        val trimmed = languages.map { it.trim() }
        LANGUAGE_PREFERENCE.forEach { preference ->
            trimmed.firstOrNull { it.equals(preference, ignoreCase = true) }?.let { return@matchChinese it }
        }
        return trimmed.firstOrNull { it.startsWith("zh", ignoreCase = true) }
    }

    /** zh、zh-TW、zh-CN、zh-HK 等 zh 系語言都算支援；忽略大小寫以避免服務回報大小寫不一致。 */
    fun hasChinese(languages: List<String>): Boolean = matchChinese(languages) != null

    private suspend fun supportedLanguages(context: Context): List<String>? =
        suspendCancellableCoroutine { continuation ->
            // receiver 與 sendOrderedBroadcast 失敗只會生效一次；避免逾時取消後重複 resume。
            val resumed = AtomicBoolean(false)
            fun resumeOnce(value: List<String>?) {
                if (resumed.compareAndSet(false, true)) {
                    continuation.resumeWith(Result.success(value))
                }
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    // 初始 resultCode 是 RESULT_CANCELED；沒有任何服務回覆時走這裡撐 null，
                    // 讓「探測無回應」落回 language-details-unavailable，而非 chinese-unsupported。
                    val extras = if (resultCode == Activity.RESULT_OK) getResultExtras(true) else null
                    val languages = extras?.containsKey(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)
                        ?.takeIf { it }
                        ?.let { extras.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES) }
                    resumeOnce(languages)
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, QUERY_LANGUAGE)
            }
            runCatching {
                context.sendOrderedBroadcast(intent, null, receiver, null, Activity.RESULT_CANCELED, null, null)
            }.onFailure {
                resumeOnce(null)
            }
        }

    private const val DEFAULT_TIMEOUT_MS = 2_500L
}
