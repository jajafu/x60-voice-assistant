package com.va.voiceassistant.music

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * MusicCabin 公開語音通道（有序廣播）的發送與回傳解析。
 *
 * 對接文件：MusicCabin 的 voice-control-integration／voice-control。
 * 音樂 App 不用事先啟動，呼叫時會自動冷啟動；單次呼叫等最多約 10 秒。
 */
object MusicCabinVoiceControl {
    const val ACTION = "com.metrolist.music.action.VOICE_CONTROL"
    const val EXTRA_COMMAND = "command"
    const val EXTRA_QUERY = "query"

    const val COMMAND_PLAY_QUERY = "play_query"
    const val COMMAND_PLAY_ARTIST = "play_artist"
    const val COMMAND_PLAY_LIKED = "play_liked"
    const val COMMAND_PLAY = "play"
    const val COMMAND_PAUSE = "pause"
    const val COMMAND_STOP = "stop"
    const val COMMAND_NOW_PLAYING = "now_playing"
    const val COMMAND_NEXT = "next"
    const val COMMAND_PREVIOUS = "previous"
    const val COMMAND_LIKE = "like"
    const val COMMAND_UNLIKE = "unlike"
    const val COMMAND_REPEAT_OFF = "repeat_off"
    const val COMMAND_REPEAT_ALL = "repeat_all"
    const val COMMAND_REPEAT_ONE = "repeat_one"
    const val COMMAND_REPEAT_STATE = "repeat_state"
    const val COMMAND_VOLUME_UP = "volume_up"
    const val COMMAND_VOLUME_DOWN = "volume_down"
    const val COMMAND_VOLUME_SET = "volume_set"
    const val COMMAND_VOLUME_GET = "volume_get"

    const val SEND_TIMEOUT_MS = 10_000L

    /** 把本機指令換成通道的命令名字與參數；回傳 null 表示此指令不走通道。 */
    fun requestFor(command: MusicCommand): Pair<String, String?> = when (command) {
        is MusicCommand.SearchAndPlay -> COMMAND_PLAY_QUERY to command.query
        is MusicCommand.PlayArtist -> COMMAND_PLAY_ARTIST to command.artist
        MusicCommand.PlayFavorites -> COMMAND_PLAY_LIKED to null
        MusicCommand.Play -> COMMAND_PLAY to null
        MusicCommand.Pause -> COMMAND_PAUSE to null
        MusicCommand.Next -> COMMAND_NEXT to null
        MusicCommand.Previous -> COMMAND_PREVIOUS to null
        MusicCommand.NowPlaying -> COMMAND_NOW_PLAYING to null
        MusicCommand.SaveCurrent -> COMMAND_LIKE to null
        MusicCommand.RemoveCurrent -> COMMAND_UNLIKE to null
        MusicCommand.RepeatOne -> COMMAND_REPEAT_ONE to null
        MusicCommand.RepeatOff -> COMMAND_REPEAT_OFF to null
        MusicCommand.RepeatAll -> COMMAND_REPEAT_ALL to null
        MusicCommand.RepeatState -> COMMAND_REPEAT_STATE to null
        MusicCommand.VolumeUp -> COMMAND_VOLUME_UP to null
        MusicCommand.VolumeDown -> COMMAND_VOLUME_DOWN to null
        is MusicCommand.VolumeSet -> COMMAND_VOLUME_SET to command.percent.toString()
        MusicCommand.VolumeGet -> COMMAND_VOLUME_GET to null
        MusicCommand.Open -> error("Open uses the app launcher, not voice control")
    }
}

data class VoiceControlResult(
    val ok: Boolean,
    val status: String,
    val title: String?,
    val artist: String?,
    val mode: String?,
    val message: String,
    val artistMatched: Boolean = true,
    val isPlaying: Boolean = true,
    /** 套用或查詢後的音樂 App 音量，0～1（`volume_*` 才有）。 */
    val volume: Float? = null,
)

interface VoiceControlSender {
    suspend fun send(command: String, query: String? = null, timeoutMs: Long? = null): VoiceControlResult
}

/** 用有序廣播呼叫 MusicCabin；逾時或對方沒有接收器時回失敗，不丟例外。 */
class OrderedBroadcastVoiceControlSender(
    context: Context,
    private val packageName: String,
) : VoiceControlSender {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun send(command: String, query: String?, timeoutMs: Long?): VoiceControlResult {
        return withTimeoutOrNull(timeoutMs ?: MusicCabinVoiceControl.SEND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val intent = Intent(MusicCabinVoiceControl.ACTION).apply {
                    setPackage(packageName)
                    putExtra(MusicCabinVoiceControl.EXTRA_COMMAND, command)
                    if (query != null) putExtra(MusicCabinVoiceControl.EXTRA_QUERY, query)
                }
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (continuation.isActive) continuation.resume(parseResult(getResultExtras(false)))
                    }

                    private fun parseResult(extras: Bundle?): VoiceControlResult {
                        if (extras == null) {
                            return VoiceControlResult(
                                ok = resultCode == Activity.RESULT_OK,
                                status = "no_receiver",
                                title = null,
                                artist = null,
                                mode = null,
                                message = "",
                            )
                        }
                        return VoiceControlResult(
                            ok = resultCode == Activity.RESULT_OK,
                            status = extras.getString("status").orEmpty().ifBlank { "error" },
                            title = extras.getString("title")?.takeIf { it.isNotBlank() },
                            artist = extras.getString("artist")?.takeIf { it.isNotBlank() },
                            mode = extras.getString("mode")?.takeIf { it.isNotBlank() },
                            message = extras.getString("message").orEmpty(),
                            artistMatched = extras.getBoolean("artist_matched", true),
                            isPlaying = extras.getBoolean("is_playing", true),
                            // 合約是 0～1；超出範圍或非有限值一律視為沒回報，不拿來組句子。
                            volume = extras.getFloat("volume", -1f).takeIf { it.isFinite() && it in 0f..1f },
                        )
                    }
                }
                appContext.sendOrderedBroadcast(
                    intent,
                    null,
                    receiver,
                    mainHandler,
                    Activity.RESULT_CANCELED,
                    null,
                    null,
                )
            }
        } ?: VoiceControlResult(
            ok = false,
            status = "timeout",
            title = null,
            artist = null,
            mode = null,
            message = "",
        )
    }
}

/**
 * 把通道回傳換成小助手的回話。純函式，不碰 Android，可單元測試。
 *
 * 成功照既有說法回（保留確認語氣與稍後播放兩種版本）；失敗直接念對方那句話，
 * 因為那句話跟著手機語系走而且最貼近實際狀況；被隱私開關擋下才指引去開設定。
 */
object MusicCabinVoiceReplies {
    fun forCommand(command: MusicCommand, result: VoiceControlResult, appName: String): MusicActionResult {
        if (!result.ok) return failure(command, result, appName)
        return when (command) {
            is MusicCommand.SearchAndPlay -> {
                if (result.title == null) speakNow(result, appName)
                else MusicReplyFormatter.confirmedPlayback(command, result.title, result.artist)
            }
            is MusicCommand.PlayArtist -> {
                // 沒對上歌手但有播歌：有說明就念說明，沒有才自己組一句，不假裝播對也不說沒在播。
                if (result.title != null && result.artistMatched) {
                    MusicReplyFormatter.confirmedPlayback(command, result.title, result.artist)
                } else if (result.title != null && result.message.isBlank()) {
                    MusicActionResult(
                        "沒找到${command.artist}的歌，播放最相近的${trackName(result.title, result.artist)}。",
                    )
                } else speakNow(result, appName)
            }
            MusicCommand.PlayFavorites -> {
                if (result.title == null) speakNow(result, appName)
                else MusicReplyFormatter.confirmedPlayback(command, result.title, result.artist)
            }
            MusicCommand.Play -> {
                if (result.status == "played") {
                    // 空隊列時對方已改播喜愛清單：照喜愛的說法回。
                    if (result.title == null) speakNow(result, appName)
                    else MusicReplyFormatter.confirmedPlayback(
                        MusicCommand.PlayFavorites, result.title, result.artist,
                    )
                } else if (result.title == null) speakNow(result, appName)
                else MusicReplyFormatter.confirmedPlayback(command, result.title, result.artist)
            }
            MusicCommand.Next, MusicCommand.Previous -> {
                if (result.title == null) speakNow(result, appName)
                else MusicReplyFormatter.confirmedPlayback(command, result.title, result.artist)
            }
            MusicCommand.Pause ->
                MusicActionResult("好的，已暫停音樂。")
            MusicCommand.NowPlaying -> {
                // 有歌名但沒附說明時用歌名組回覆，不要說無法讀取。
                if (result.title != null && result.message.isBlank()) {
                    MusicActionResult(MusicReplyFormatter.currentTrack(result.title, result.artist))
                } else speakNow(result, appName)
            }
            MusicCommand.SaveCurrent ->
                MusicActionResult(if (result.status == "already_liked") "已經收藏過了" else "收藏成功")
            MusicCommand.RemoveCurrent ->
                MusicActionResult(if (result.status == "already_unliked") "這首歌尚未收藏。" else "已取消")
            MusicCommand.RepeatOne, MusicCommand.RepeatOff, MusicCommand.RepeatAll ->
                MusicActionResult(repeatSetReply(command, result.mode))
            MusicCommand.RepeatState ->
                MusicActionResult(repeatStateReply(result.mode))
            MusicCommand.VolumeUp, MusicCommand.VolumeDown, is MusicCommand.VolumeSet,
            MusicCommand.VolumeGet -> volumeReply(command, result)
            MusicCommand.Open -> error("Open uses the app launcher, not voice control")
        }
    }

    private fun failure(command: MusicCommand, result: VoiceControlResult, appName: String): MusicActionResult {
        // 對方沒有接收器或逾時：代表是沒有語音通道的舊版，提示更新（Issue #29 不走回舊連線）。
        if (result.status == "no_receiver" || result.status == "timeout") {
            return MusicActionResult("$appName 太舊沒有語音控制，請更新 $appName 後再試。")
        }
        if (result.status == "denied") {
            return MusicActionResult(
                if (command == MusicCommand.SaveCurrent || command == MusicCommand.RemoveCurrent) {
                    "要改收藏請先到${appName}的隱私設定開啟外部收藏。"
                } else {
                    "$appName 關掉了外部播放控制，請到音樂 App 的設定開啟語音控制。"
                },
            )
        }
        if (result.message.isNotBlank()) return MusicActionResult(result.message)
        return MusicActionResult("還沒連上$appName，請先播放一首歌。")
    }

    private fun speakNow(result: VoiceControlResult, appName: String): MusicActionResult {
        if (result.message.isNotBlank()) return MusicActionResult(result.message)
        return MusicActionResult("目前無法讀取$appName 的歌曲資訊。")
    }

    private fun repeatSetReply(command: MusicCommand, mode: String?): String = when (mode) {
        "one" -> "已開啟單曲循環。"
        "all" -> "已開啟全部循環。"
        "off" -> "已取消單曲循環。"
        // 模式不明時不猜是開還是關，只說已送出、請到 App 確認。
        else -> when (command) {
            MusicCommand.RepeatAll -> "已送出全部循環設定，請到 MusicCabin 確認結果。"
            MusicCommand.RepeatOne -> "已送出單曲循環設定，請到 MusicCabin 確認結果。"
            else -> "已送出取消循環設定，請到 MusicCabin 確認結果。"
        }
    }

    private fun repeatStateReply(mode: String?): String = when (mode) {
        "one" -> "目前是單曲循環。"
        "all" -> "目前是全部循環。"
        "off" -> "目前沒有開循環。"
        else -> "查不到目前的循環模式，請到 MusicCabin 確認。"
    }

    /**
     * 音量回話優先念音樂 App 給的那句話（已含套用後的百分比且跟手機語系走）；
     * 對方沒給說明時才用回傳的 0～1 音量自己組句子，避免假稱已調到。
     * 兩者都沒有時，查詢句與調整句的兜底措辭分開，避免查詢被說成「已送出指令」。
     */
    private fun volumeReply(command: MusicCommand, result: VoiceControlResult): MusicActionResult {
        if (result.message.isNotBlank()) return MusicActionResult(result.message)
        // 合約是 0～1；超出範圍或非有限值的回傳不拿來組句子，改說已送出。
        val volume = result.volume?.takeIf { it.isFinite() && it in 0f..1f }
        if (volume != null) {
            return MusicActionResult("音樂音量現在是 ${(volume * 100).roundToInt()}%。")
        }
        return if (command == MusicCommand.VolumeGet) {
            MusicActionResult("查不到目前的音樂音量，請到 MusicCabin 確認。")
        } else {
            MusicActionResult("已送出音量指令，請到 MusicCabin 確認結果。")
        }
    }

    private fun trackName(title: String, artist: String?): String {
        val cleanTitle = title.trim()
        val cleanArtist = artist?.trim()?.takeIf { it.isNotEmpty() } ?: return "《$cleanTitle》"
        return "${cleanArtist}的《$cleanTitle》"
    }
}
