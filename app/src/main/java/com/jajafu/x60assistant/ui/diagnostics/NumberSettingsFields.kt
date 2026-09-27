package com.jajafu.x60assistant.ui.diagnostics

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jajafu.x60assistant.data.AssistantSettings
import java.util.Locale

/**
 * 數字設定欄位的輸入規則。
 *
 * 設定欄位必須保留「輸入中的字串」：空白、小數點、單一負號都是合法的輸入中間狀態。
 * 若在 `onValueChange` 直接把文字轉成數字並丟棄無法解析的輸入，使用者清空欄位或修改
 * 前導數字時，畫面就會被還原成上一個可解析的值（例如預設 6），且無法輸入 0。
 *
 * 這裡只放不依賴 Compose 的純邏輯，讓輸入規則可以用 JVM 單元測試驗證。
 */
object NumberFieldPolicy {
    enum class Kind { INTEGER, DECIMAL }

    private val INTEGER_PATTERN = Regex("^-?\\d{0,9}$")
    private val DECIMAL_PATTERN = Regex("^-?\\d{0,9}(\\.\\d{0,3})?$")

    fun accepts(text: String, kind: Kind): Boolean = when (kind) {
        Kind.INTEGER -> INTEGER_PATTERN.matches(text)
        Kind.DECIMAL -> DECIMAL_PATTERN.matches(text)
    }

    /** 回傳可接受的文字；null 代表這次輸入不合法，呼叫端應保留原本的文字。 */
    fun filter(text: String, kind: Kind): String? = if (accepts(text, kind)) text else null

    fun parseNumber(text: String, kind: Kind): Double? = when (kind) {
        Kind.INTEGER -> text.toIntOrNull()?.toDouble()
        Kind.DECIMAL -> text.toDoubleOrNull()
    }

    fun errorMessage(
        text: String,
        kind: Kind,
        range: ClosedFloatingPointRange<Double>,
        rangeLabel: String,
    ): String? {
        if (text.isBlank()) return "請輸入數值。"
        if (!accepts(text, kind)) return "只能輸入數字。"
        val value = parseNumber(text, kind) ?: return "請輸入完整的數值。"
        if (value < range.start || value > range.endInclusive) return "請輸入 $rangeLabel 範圍內的數值。"
        return null
    }
}

/** 單一數字設定欄位的狀態；[text] 永遠保留使用者目前輸入的字串。 */
@Stable
class NumberFieldState internal constructor(
    initialText: String,
    val kind: NumberFieldPolicy.Kind,
    private val range: ClosedFloatingPointRange<Double>,
    val rangeLabel: String,
) {
    var text by mutableStateOf(initialText)
        private set

    val value: Double? get() = NumberFieldPolicy.parseNumber(text, kind)

    val error: String? get() = NumberFieldPolicy.errorMessage(text, kind, range, rangeLabel)

    val isValid: Boolean get() = error == null

    fun update(next: String) {
        text = NumberFieldPolicy.filter(next, kind) ?: text
    }

    internal fun reset(newText: String) {
        text = NumberFieldPolicy.filter(newText, kind) ?: newText
    }
}

/**
 * 設定頁所有數字欄位的狀態。
 *
 * 上下限取自 [AssistantSettings] 的共用常數，讓畫面顯示的範圍、驗證結果與儲存時的
 * `normalized()` 夾限來自同一個來源。數字欄位的值在編輯期間不寫回 `AssistantSettings`，
 * 送出時才由 [applyTo] 合併，避免畫面文字與資料類別互相覆蓋。
 */
@Stable
class NumberSettingsFields(initial: AssistantSettings) {
    val temperature = NumberFieldState(
        formatDecimal(initial.temperature),
        NumberFieldPolicy.Kind.DECIMAL,
        AssistantSettings.TEMPERATURE_RANGE,
        rangeLabel(AssistantSettings.TEMPERATURE_RANGE, decimals = 1),
    )
    val maxOutputTokens = intField(
        initial.maxOutputTokens,
        AssistantSettings.MAX_OUTPUT_TOKENS_RANGE,
    )
    val connectTimeoutSeconds = intField(
        initial.connectTimeoutSeconds,
        AssistantSettings.CONNECT_TIMEOUT_SECONDS_RANGE,
        unit = " 秒",
    )
    val readTimeoutSeconds = intField(
        initial.readTimeoutSeconds,
        AssistantSettings.READ_TIMEOUT_SECONDS_RANGE,
        unit = " 秒",
    )
    val contextTurns = intField(initial.contextTurns, AssistantSettings.CONTEXT_TURNS_RANGE)
    val waitForSpeechSeconds = secondsField(
        formatMillisecondsAsSeconds(initial.waitForSpeechMs),
        AssistantSettings.WAIT_FOR_SPEECH_SECONDS_RANGE,
    )
    val minSpeechMs = NumberFieldState(
        initial.minSpeechMs.toString(),
        NumberFieldPolicy.Kind.INTEGER,
        AssistantSettings.MIN_SPEECH_MS_RANGE.asDoubleRange(),
        rangeLabel(AssistantSettings.MIN_SPEECH_MS_RANGE, unit = " ms"),
    )
    val trailingSilenceMs = NumberFieldState(
        initial.trailingSilenceMs.toString(),
        NumberFieldPolicy.Kind.INTEGER,
        AssistantSettings.TRAILING_SILENCE_MS_RANGE.asDoubleRange(),
        rangeLabel(AssistantSettings.TRAILING_SILENCE_MS_RANGE, unit = " ms"),
    )
    val maxUtteranceSeconds = secondsField(
        formatMillisecondsAsSeconds(initial.maxUtteranceMs),
        AssistantSettings.MAX_UTTERANCE_SECONDS_RANGE,
    )
    val followUpSeconds = intField(
        initial.followUpSeconds,
        AssistantSettings.FOLLOW_UP_SECONDS_RANGE,
        unit = " 秒",
    )

    val allValid: Boolean get() = states().all { it.isValid }

    /** 合併數字欄位；無法解析時保留 [settings] 原值，實際擋下無效輸入的是 [allValid]。 */
    fun applyTo(settings: AssistantSettings): AssistantSettings = settings.copy(
        temperature = temperature.value ?: settings.temperature,
        maxOutputTokens = maxOutputTokens.intValue() ?: settings.maxOutputTokens,
        connectTimeoutSeconds = connectTimeoutSeconds.intValue() ?: settings.connectTimeoutSeconds,
        readTimeoutSeconds = readTimeoutSeconds.intValue() ?: settings.readTimeoutSeconds,
        contextTurns = contextTurns.intValue() ?: settings.contextTurns,
        waitForSpeechMs = waitForSpeechSeconds.milliseconds() ?: settings.waitForSpeechMs,
        minSpeechMs = minSpeechMs.milliseconds() ?: settings.minSpeechMs,
        trailingSilenceMs = trailingSilenceMs.milliseconds() ?: settings.trailingSilenceMs,
        maxUtteranceMs = maxUtteranceSeconds.milliseconds() ?: settings.maxUtteranceMs,
        followUpSeconds = followUpSeconds.intValue() ?: settings.followUpSeconds,
    )

    internal fun assign(saved: AssistantSettings) {
        temperature.reset(formatDecimal(saved.temperature))
        maxOutputTokens.reset(saved.maxOutputTokens.toString())
        connectTimeoutSeconds.reset(saved.connectTimeoutSeconds.toString())
        readTimeoutSeconds.reset(saved.readTimeoutSeconds.toString())
        contextTurns.reset(saved.contextTurns.toString())
        waitForSpeechSeconds.reset(formatMillisecondsAsSeconds(saved.waitForSpeechMs))
        minSpeechMs.reset(saved.minSpeechMs.toString())
        trailingSilenceMs.reset(saved.trailingSilenceMs.toString())
        maxUtteranceSeconds.reset(formatMillisecondsAsSeconds(saved.maxUtteranceMs))
        followUpSeconds.reset(saved.followUpSeconds.toString())
    }

    internal fun texts(): List<String> = states().map { it.text }

    /** 使用者尚未改動任何數字欄位；此時外部設定更新才可以覆蓋畫面。 */
    internal fun isPristine(saved: AssistantSettings): Boolean = texts() == NumberSettingsFields(saved).texts()

    private fun states(): List<NumberFieldState> = listOf(
        temperature,
        maxOutputTokens,
        connectTimeoutSeconds,
        readTimeoutSeconds,
        contextTurns,
        waitForSpeechSeconds,
        minSpeechMs,
        trailingSilenceMs,
        maxUtteranceSeconds,
        followUpSeconds,
    )

    private companion object {
        fun intField(initial: Int, range: IntRange, unit: String = "") = NumberFieldState(
            initial.toString(),
            NumberFieldPolicy.Kind.INTEGER,
            range.asDoubleRange(),
            rangeLabel(range, unit = unit),
        )

        fun secondsField(initial: String, range: ClosedFloatingPointRange<Double>) = NumberFieldState(
            initial,
            NumberFieldPolicy.Kind.DECIMAL,
            range,
            rangeLabel(range, decimals = 0, unit = " 秒"),
        )
    }
}

private fun IntRange.asDoubleRange(): ClosedFloatingPointRange<Double> = first.toDouble()..last.toDouble()

private fun LongRange.asDoubleRange(): ClosedFloatingPointRange<Double> = first.toDouble()..last.toDouble()

private fun rangeLabel(range: IntRange, unit: String = ""): String = "${range.first}–${range.last}$unit"

private fun rangeLabel(range: LongRange, unit: String = ""): String = "${range.first}–${range.last}$unit"

private fun rangeLabel(range: ClosedFloatingPointRange<Double>, decimals: Int, unit: String = ""): String =
    "${formatFixed(range.start, decimals)}–${formatFixed(range.endInclusive, decimals)}$unit"

private fun formatFixed(value: Double, decimals: Int): String =
    String.format(Locale.ROOT, "%.${decimals}f", value)

private fun formatDecimal(value: Double): String =
    String.format(Locale.ROOT, "%.4f", value).trimEnd('0').trimEnd('.').ifEmpty { "0" }

private fun NumberFieldState.intValue(): Int? = value?.toInt()

private fun NumberFieldState.milliseconds(): Long? = value?.let { (it * 1_000).toLong() }
