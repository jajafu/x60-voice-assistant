package com.va.voiceassistant.asr

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 探測裝置上的系統語音辨識服務是否存在且支援中文。
 *
 * 兩個來源，新版優先：
 * 1. API 33+ 用 `SpeechRecognizer.checkRecognitionSupport` 直接問辨識服務「這個中文請求支不支援」，
 *    服務回答的語言清單（裝置上／待下載／線上）含 zh 系即通過。
 * 2. API < 33，或新版 API 沒答案（逾時、服務錯誤、服務不支援此 API）時，退回 AOSP 文件化的
 *    語言詳情廣播 `ACTION_GET_LANGUAGE_DETAILS`，讀 `EXTRA_SUPPORTED_LANGUAGES`。
 *
 * 兩個來源都問不到時**不擋掉系統這條路**：只要裝置有辨識服務就先信任它（未驗證），
 * 成敗交給執行中的辨識請求，並把當時問不到的原因放進 [SystemRecognitionProbe.Confirmed.unverifiedReason]
 * 供診斷追查。只有在來源明說「不支援中文」時才判定不可用。
 *
 * 為什麼放寬：2026-10 在 vivo X200s／Android 16 實測，預設辨識器
 * `com.google.android.tts/…GoogleTTSRecognitionService` 兩個來源都問不到——
 * 新版 API 回 `ERROR_CANNOT_CHECK_SUPPORT`（14，服務不允許檢查支援），
 * 語言詳情廣播則只回 Google 自己的 `com.google.recognition.extra.OFFLINE_*`（protobuf `byte[]`），
 * 沒有文件化的 `EXTRA_SUPPORTED_LANGUAGES`。嚴格要求「問到中文才用」會讓系統辨識在這種裝置上完全無法使用。
 *
 * 探測通過時會帶回「選定的中文語言標籤」交給引擎（[SystemRecognitionProbe.Confirmed.language]），
 * 因為引擎只會以單一語言送出辨識請求：僅回報 zh-CN 的服務探測通過後也會以 zh-CN 聆聽，
 * 不會被迫用 zh-TW。探測失敗的原因放進 [SystemRecognitionProbe.Unavailable.reason] 供診斷顯示。
 */
object SystemSpeechRecognizerSupport {

    /** 系統辨識引擎對外的模型識別；診斷「實際載入的模型」會顯示這個值。 */
    const val MODEL_ID = "system/android-zh"

    /** 舊路徑（語言詳情廣播）送出的語言；服務回報的是整份支援清單，此值只影響清單的偏好排序。 */
    private const val QUERY_LANGUAGE = "zh-TW"

    /** 新版 API 逐一向辨識服務確認的候選語言：先繁中，服務明確不支援才試簡中。 */
    private val SUPPORT_CANDIDATES = listOf("zh-TW", "zh-CN")

    /**
     * 引擎實際送辨識時的語言偏好順序：App 是繁中體驗，優先 zh-TW／zh-Hant 系；
     * 都沒有時仍可用 zh-CN 等其他中文變體。
     */
    private val LANGUAGE_PREFERENCE = listOf("zh-TW", "zh-Hant-TW", "zh-Hant", "zh-HK", "zh-MO", "zh-CN", "zh")

    sealed interface SystemRecognitionProbe {
        /**
         * 探測通過：[language] 是引擎要用的中文語言標籤。
         * [unverifiedReason] 有值時代表兩個來源都沒問到語言資訊，只是「系統有辨識服務」就先用
         * （例如服務沒實作 `checkRecognitionSupport`、語言詳情廣播也沒有文件化的清單）；
         * 原因只供水落石出時追查，實際能不能辨識中文由執行中的辨識請求決定。
         */
        data class Confirmed(
            val language: String,
            val unverifiedReason: String? = null,
        ) : SystemRecognitionProbe

        /** reason 只是固定短代碼，不包含任何使用者內容。 */
        data class Unavailable(val reason: String) : SystemRecognitionProbe
    }

    /**
     * 兩個探測來源取得的事實，交由 [resolve] 決定結果。
     * [supportLanguages]、[supportUnsupported] 由新版 API 提供；[languageDetails] 由語言詳情廣播提供。
     */
    internal data class ProbeFacts(
        /** API 33+ 問到的支援語言；null 表示沒問到（沒走這條路、逾時或服務錯誤）。 */
        val supportLanguages: List<String>? = null,
        /** API 33+ 明確回答「這個中文請求不支援」。 */
        val supportUnsupported: Boolean = false,
        /** 語言詳情廣播回報的清單；null 表示沒有回應。 */
        val languageDetails: List<String>? = null,
        /** 新版 API 沒答案時的診斷碼；null 表示沒走這條路。 */
        val supportReason: String? = null,
    )

    /**
     * 啟動服務時呼叫一次。新版 API 優先；它沒答案才退回落後廣播。兩個來源都最長等各自的逾時，
     * 逾時或無回覆一律視為探測不到，保守退回本機後備路徑。
     */
    suspend fun probe(context: Context, timeoutMs: Long = DEFAULT_TIMEOUT_MS): SystemRecognitionProbe {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return SystemRecognitionProbe.Unavailable("no-recognition-service")
        }
        val modern = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkRecognitionSupport(context, timeoutMs)
        } else {
            ModernProbe.NotAttempted
        }
        // 新版 API 已有結論（含明確不支援）時不必再送廣播，省一次往返也避免覆蓋明確答案。
        val languageDetails = when (modern) {
            is ModernProbe.Answered, ModernProbe.Unsupported -> null
            is ModernProbe.NoAnswer, ModernProbe.NotAttempted ->
                withTimeoutOrNull(timeoutMs) { supportedLanguages(context) }
        }
        return resolve(
            ProbeFacts(
                supportLanguages = (modern as? ModernProbe.Answered)?.languages,
                supportUnsupported = modern is ModernProbe.Unsupported,
                languageDetails = languageDetails,
                supportReason = (modern as? ModernProbe.NoAnswer)?.reason,
            ),
        )
    }

    /**
     * 依兩個來源的事實決定探測結果。純邏輯（不碰 Android API），由單元測試覆蓋。
     * 任一來源明說不支援中文就是 `chinese-unsupported`；兩個來源都問不到時先信任系統辨識
     * （未驗證），不因為「問不到」就擋掉系統這條路。
     */
    internal fun resolve(facts: ProbeFacts): SystemRecognitionProbe {
        facts.supportLanguages?.let { languages ->
            return matchChinese(languages)?.let { SystemRecognitionProbe.Confirmed(it) }
                ?: SystemRecognitionProbe.Unavailable("chinese-unsupported")
        }
        if (facts.supportUnsupported) {
            return SystemRecognitionProbe.Unavailable("chinese-unsupported")
        }
        facts.languageDetails?.let { languages ->
            return matchChinese(languages)?.let { SystemRecognitionProbe.Confirmed(it) }
                ?: SystemRecognitionProbe.Unavailable(withReason(facts.supportReason, "chinese-unsupported"))
        }
        // 兩個來源都沒有可用資訊時，唯一的事實是「系統有辨識服務」：
        // 先信任系統辨識（未驗證），成敗交由執行中的辨識請求決定，並留下當時問不到的原因。
        return SystemRecognitionProbe.Confirmed(
            QUERY_LANGUAGE,
            unverifiedReason = withReason(facts.supportReason, "language-details-unavailable"),
        )
    }

    /** 診斷用的複合原因：新版 API 的失敗碼在前，廣播路徑的結論在後。 */
    private fun withReason(prefix: String?, reason: String): String =
        if (prefix.isNullOrBlank()) reason else "$prefix+$reason"

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

    /**
     * 新版 API 的探測結果：
     * [Answered] 服務回答「這個中文請求支援」並附上語言清單；
     * [Unsupported] 服務明確回答這個候選語言不支援（[SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED]）；
     * [NoAnswer] 逾時、建立失敗、服務錯誤或服務不支援 [SpeechRecognizer.checkRecognitionSupport]；
     * [NotAttempted] API < 33，沒問過。
     */
    private sealed interface ModernProbe {
        data class Answered(val languages: List<String>) : ModernProbe
        data object Unsupported : ModernProbe
        data class NoAnswer(val reason: String) : ModernProbe
        data object NotAttempted : ModernProbe
    }

    /** 逐一問候選語言：拿到答案或遇到「非語言問題」就停；服務說不支援才換下一個候選。 */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun checkRecognitionSupport(context: Context, timeoutMs: Long): ModernProbe {
        SUPPORT_CANDIDATES.forEach { language ->
            when (val result = askSupport(context, language, timeoutMs)) {
                is ModernProbe.Answered -> return result
                is ModernProbe.NoAnswer -> return result
                ModernProbe.Unsupported -> Unit
                ModernProbe.NotAttempted -> Unit
            }
        }
        return ModernProbe.Unsupported
    }

    /** 建立系統 recognizer 只為問一次支援；無論成敗都在主執行緒收掉，不留下綁定。 */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun askSupport(context: Context, language: String, timeoutMs: Long): ModernProbe =
        withContext(Dispatchers.Main) {
            val recognizer = try {
                SpeechRecognizer.createSpeechRecognizer(context)
            } catch (error: Throwable) {
                return@withContext ModernProbe.NoAnswer("check-support-create-failed")
            }
            try {
                withTimeoutOrNull(timeoutMs) { awaitSupport(context, recognizer, language) }
                    ?: ModernProbe.NoAnswer("check-support-timeout")
            } finally {
                // SpeechRecognizer 的建立與 destroy 都必須在主執行緒；逾時取消也走這裡。
                runCatching { recognizer.destroy() }
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun awaitSupport(
        context: Context,
        recognizer: SpeechRecognizer,
        language: String,
    ): ModernProbe =
        suspendCancellableCoroutine { continuation ->
            val resumed = AtomicBoolean(false)
            fun resumeOnce(value: ModernProbe) {
                if (resumed.compareAndSet(false, true)) {
                    continuation.resumeWith(Result.success(value))
                }
            }
            val callback = object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    // 請求本身成功即代表這個中文語言可用；回報的清單只用來挑較合適的變體。
                    resumeOnce(
                        ModernProbe.Answered(
                            recognitionSupport.supportedOnDeviceLanguages +
                                recognitionSupport.installedOnDeviceLanguages +
                                recognitionSupport.pendingOnDeviceLanguages +
                                recognitionSupport.onlineLanguages +
                                language,
                        ),
                    )
                }

                override fun onError(error: Int) {
                    if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) {
                        resumeOnce(ModernProbe.Unsupported)
                    } else {
                        resumeOnce(ModernProbe.NoAnswer("check-support-error-$error"))
                    }
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            }
            runCatching {
                recognizer.checkRecognitionSupport(intent, ContextCompat.getMainExecutor(context), callback)
            }.onFailure {
                resumeOnce(ModernProbe.NoAnswer("check-support-send-failed"))
            }
        }

    /** 舊路徑：語言詳情是有序廣播，沒有任何服務回應時回 null。 */
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
                    // 「有回應／沒回應」的判準是 extras 裡有沒有語言清單，不是 result code：
                    // 實測 Google 語言包服務只設 resultExtras，resultCode 維持初始的 RESULT_CANCELED。
                    // 沒有回應時 extras 不含此 key，落回 language-details-unavailable 而非 chinese-unsupported。
                    val languages = getResultExtras(false)
                        ?.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)
                    resumeOnce(languages)
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS).apply {
                addFlags(RECEIVER_INCLUDE_BACKGROUND)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, QUERY_LANGUAGE)
            }
            runCatching {
                context.sendOrderedBroadcast(intent, null, receiver, null, Activity.RESULT_CANCELED, null, null)
            }.onFailure {
                resumeOnce(null)
            }
        }

    /**
     * `Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND`：@hide 常數，公開 SDK 沒有，故以數值帶入。
     * 沒有這個旗標時，Android 8+ 不會把隱式廣播送給待在背景的 manifest receiver；
     * 實測 vivo X200s／Android 16 的 Google 語言包 receiver 只有帶旗標才收得到，
     * 少了它探測會誤判成「沒有服務回應語言清單」。
     */
    private const val RECEIVER_INCLUDE_BACKGROUND = 0x01000000

    private const val DEFAULT_TIMEOUT_MS = 2_500L
}
