package com.va.voiceassistant.music

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController as Media3Controller
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.va.voiceassistant.diagnostics.M1DiagnosticsRepository
import java.util.concurrent.ExecutionException
import android.os.Bundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class MusicActionResult(
    val message: String,
    val deferredMessage: String? = null,
    val confirmedPlayback: Boolean = false,
)

/** 調整音樂 App 自己音量的指令：目前只有走公開語音通道的 MusicCabin 支援。 */
private val MusicCommand.isVolumeControl: Boolean
    get() = this == MusicCommand.VolumeUp || this == MusicCommand.VolumeDown ||
        this is MusicCommand.VolumeSet || this == MusicCommand.VolumeGet

interface MusicActionExecutor {
    suspend fun execute(command: MusicCommand): MusicActionResult
    /** Returns true only when this request sent a pause to an active player. */
    suspend fun pauseForVoice(): Boolean
    /** Never starts a player that is already playing or has no paused session. */
    suspend fun resumeAfterVoice(): Boolean
    /** Stops a player held for this voice session so it cannot auto-resume afterward. */
    suspend fun stopForClose(): Boolean
}

/** Controls the user-selected exported media browser; MusicCabin likes need notification access. */
class MusicCabinController(
    context: Context,
    private val musicPackageName: String = DEFAULT_PACKAGE_NAME,
    private val musicServiceName: String = DEFAULT_SERVICE_NAME,
    private val musicAppName: String = "MusicCabin",
    voiceSender: VoiceControlSender? = null,
) : MusicActionExecutor {
    private val appContext = context.applicationContext
    private val appLauncher = MusicAppLauncher(appContext, musicPackageName, musicAppName)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val voiceControl: VoiceControlSender by lazy {
        voiceSender ?: OrderedBroadcastVoiceControlSender(appContext, musicPackageName)
    }

    /**
     * MusicCabin（含 .gms／.debug 變體）走公開語音通道，不再用媒體連線；
     * 其他播放器與車機 fallback 維持舊路。
     */
    private val isVoiceControlPackage: Boolean =
        musicPackageName == DEFAULT_PACKAGE_NAME || musicPackageName.startsWith("$DEFAULT_PACKAGE_NAME.")

    /** 經 fallback 暫停的實際播放 session；resume 只對同一台做，避免誤開別的播放器。 */
    private var fallbackSession: MediaController? = null

    override suspend fun pauseForVoice(): Boolean = withContext(Dispatchers.Main.immediate) {
        // 已持有 fallback 暫停時（例如 TTS 結束進 Follow Up 再次呼叫），保留引用並直接回 false。
        // 已暫停的音樂會讓 isMusicActive 回 false；若走到下方清空，就會導致結束後無法恢復。
        fallbackSession?.let { held ->
            if (runCatching { held.playbackState?.state }.getOrNull() == PlaybackState.STATE_PAUSED) {
                return@withContext false
            }
            // 持有但已不在 PAUSED（使用者手動恢復或 session 失效）：丟掉舊引用，重新判斷。
            fallbackSession = null
        }
        // 沒播音樂就不綁 MusicCabin 服務，省掉一次無用的 MediaBrowser 連線。
        // （已持有的 PAUSED 已在上方提前返回，此處不再清空，避免誤刪。）
        if (audioManager?.isMusicActive == false) {
            return@withContext false
        }
        if (isVoiceControlPackage) return@withContext pauseMusicCabinForVoice()
        if (pauseConfiguredApp()) {
            fallbackSession = null
            return@withContext true
        }
        pauseActiveSession()
    }

    /**
     * 語音對話期間把 MusicCabin 暫停；對方沒在播（實際是別台在響）才沿用車機 fallback，
     * 被隱私開關擋下或出錯就回 false，讓 TTS 用 duck，不假稱已暫停。
     */
    private suspend fun pauseMusicCabinForVoice(): Boolean {
        var result = sendVoiceCommand(MusicCabinVoiceControl.COMMAND_PAUSE, null, VOICE_PAUSE_TIMEOUT_MS)
            ?: return false
        if (result.status == "no_song") return pauseActiveSession()
        if (result.status != "timeout") return result.ok
        // 逾時：進入前呼叫端已確認音樂在播，期間唯一動作者就是這則暫停廣播；
        // 音樂已停即視同暫停生效（回 true 讓對話結束後排程恢復），避免音樂卡在暫停。
        // 已知反例（known-issues 1.0.14）：這段時間若音樂因自然播畢或使用者手動停止，
        // 事後恢復的 play 在空佇列下可能改播喜愛清單；取捨是換掉更常見的「已暫停卻不恢復」。
        if (audioManager?.isMusicActive == false) return true
        // 仍在播：可能只是回得慢，再送一次（對方多半已被第一次廣播喚起）。
        result = sendVoiceCommand(MusicCabinVoiceControl.COMMAND_PAUSE, null, VOICE_PAUSE_TIMEOUT_MS)
            ?: return false
        if (result.status == "no_song") return pauseActiveSession()
        if (result.status == "timeout" && audioManager?.isMusicActive == false) return true
        return result.ok
    }

    private suspend fun pauseConfiguredApp(): Boolean {
        return try {
            withTimeoutOrNull(VOICE_CONTROL_TIMEOUT_MS) {
                val browser = connect()
                try {
                    val controller = MediaController(appContext, browser.sessionToken)
                    val state = controller.playbackState
                    if (state?.state !in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING) ||
                        !supports(state, PlaybackState.ACTION_PAUSE)
                    ) return@withTimeoutOrNull false
                    controller.transportControls.pause()
                    awaitState(controller, PAUSE_CONFIRM_TIMEOUT_MS) {
                        it?.state == PlaybackState.STATE_PAUSED
                    }
                    true
                } finally {
                    browser.disconnect()
                }
            } ?: false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 設定裡的 App 連不上時的車機 fallback：對實際在播的 session 下暫停。
     *
     * 車機原生播放器多半不公開 `MediaBrowserService`，只能走這條路。
     * 需要通知存取權；沒授權就回 false 維持舊行為（TTS duck），不假稱已暫停。
     * 只有確認進 PAUSED 才算成功並記住 controller；直接停掉或不理的播放器不碰，
     * 避免結束後誤把別的聲音打開。
     */
    private suspend fun pauseActiveSession(): Boolean {
        if (!MusicCabinFavoriteAccess.isGranted(appContext)) {
            fallbackSession = null
            return false
        }
        val paused = try {
            withTimeoutOrNull(VOICE_CONTROL_TIMEOUT_MS) {
                val manager = appContext.getSystemService(MediaSessionManager::class.java)
                    ?: return@withTimeoutOrNull false
                val component = ComponentName(appContext, MusicCabinFavoriteListenerService::class.java)
                val sessions = manager.getActiveSessions(component)
                // snapshots 與 sessions 同順序：用索引取 token，避免同套件多 session 時拿到錯誤的那一台。
                val snapshots = sessions.map {
                    ActiveSessionSnapshot(
                        it.packageName,
                        it.playbackState?.state ?: PlaybackState.STATE_NONE,
                    )
                }
                val pickedIndex = ActiveSessionPicker.playingIndex(snapshots)
                    ?: return@withTimeoutOrNull false
                val token = sessions.getOrNull(pickedIndex)?.sessionToken
                    ?: return@withTimeoutOrNull false
                val controller = MediaController(appContext, token)
                if (!supports(controller.playbackState, PlaybackState.ACTION_PAUSE)) {
                    return@withTimeoutOrNull false
                }
                controller.transportControls.pause()
                val paused = awaitState(controller, PAUSE_CONFIRM_TIMEOUT_MS) {
                    it?.state == PlaybackState.STATE_PAUSED
                }
                if (paused) {
                    fallbackSession = controller
                    true
                } else {
                    false
                }
            } ?: false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (!paused) fallbackSession = null
        return paused
    }

    override suspend fun resumeAfterVoice(): Boolean = withContext(Dispatchers.Main.immediate) {
        // 這輪暫停的是 fallback 那台，就只恢復它；設定裡的 App 沒被我們暫停，不能順手打開。
        if (fallbackSession != null) return@withContext resumeActiveSession()
        if (isVoiceControlPackage) return@withContext resumeMusicCabinAfterVoice()
        resumeConfiguredApp()
    }

    /** 只在剛才暫停成功後呼叫；隊列還在所以不會誤觸空隊列播喜愛。
     *  逾時不重送：已送達的 play 遲早生效（音樂自然恢復），而 MusicCabin 對「已在播放時
     *  收到 play」的語義未經合約驗證，重送有重播風險（isRetryableAfterTimeout 同樣排除播放）。 */
    private suspend fun resumeMusicCabinAfterVoice(): Boolean {
        return sendVoiceCommand(MusicCabinVoiceControl.COMMAND_PLAY, null, VOICE_RESUME_TIMEOUT_MS)?.ok == true
    }

    private suspend fun resumeConfiguredApp(): Boolean {
        return try {
            withTimeoutOrNull(VOICE_CONTROL_TIMEOUT_MS) {
                val browser = connect()
                try {
                    val controller = MediaController(appContext, browser.sessionToken)
                    val state = controller.playbackState
                    if (state?.state != PlaybackState.STATE_PAUSED || !supports(state, PlaybackState.ACTION_PLAY)) {
                        return@withTimeoutOrNull false
                    }
                    controller.transportControls.play()
                    awaitState(controller, RESUME_CONFIRM_TIMEOUT_MS) {
                        it?.state == PlaybackState.STATE_PLAYING
                    }
                } finally {
                    browser.disconnect()
                }
            } ?: false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    /** 只恢復經 fallback 暫停的那一台；它已經不在 PAUSED 就當沒這回事。 */
    private suspend fun resumeActiveSession(): Boolean {
        val controller = fallbackSession ?: return false
        try {
            return withTimeoutOrNull(VOICE_CONTROL_TIMEOUT_MS) {
                if (controller.playbackState?.state != PlaybackState.STATE_PAUSED) {
                    return@withTimeoutOrNull false
                }
                // 對話期間使用者改播另一台時放棄自動恢復，避免兩台疊播。
                if (hasOtherPlaying()) return@withTimeoutOrNull false
                controller.transportControls.play()
                awaitState(controller, RESUME_CONFIRM_TIMEOUT_MS) {
                    it?.state == PlaybackState.STATE_PLAYING
                }
            } ?: false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        } finally {
            // 不論成功、逾時或已被使用者手動恢復，都不再持有，避免之後誤開別的聲音。
            fallbackSession = null
        }
    }

    /**
     * 是否有其他播放器正在播放。
     *
     * 呼叫端已確認持有的 fallback 為 PAUSED，因此任何 PLAYING／BUFFERING 必然屬於
     * 另一個 session。TTS 不建立 MediaSession，不會在此誤判；無通知存取權時退回
     * AudioManager.isMusicActive 啟發式（同樣僅在持有為 PAUSED 時解讀為他台在播）。
     */
    private fun hasOtherPlaying(): Boolean {
        if (MusicCabinFavoriteAccess.isGranted(appContext)) {
            val snapshots = runCatching {
                val manager = appContext.getSystemService(MediaSessionManager::class.java)
                    ?: return audioManager?.isMusicActive == true
                val component = ComponentName(appContext, MusicCabinFavoriteListenerService::class.java)
                manager.getActiveSessions(component).map {
                    ActiveSessionSnapshot(
                        it.packageName,
                        it.playbackState?.state ?: PlaybackState.STATE_NONE,
                    )
                }
            }.getOrNull() ?: return audioManager?.isMusicActive == true
            return ActiveSessionPicker.hasPlaying(snapshots)
        } else {
            return audioManager?.isMusicActive == true
        }
    }

    override suspend fun stopForClose(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (fallbackSession != null) return@withContext stopFallbackSession()
        if (isVoiceControlPackage) return@withContext stopMusicCabin()
        try {
            withTimeoutOrNull(VOICE_CONTROL_TIMEOUT_MS) {
                val browser = connect()
                try {
                    val controller = MediaController(appContext, browser.sessionToken)
                    val state = controller.playbackState
                    if (state == null || state.state in setOf(PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED)) {
                        return@withTimeoutOrNull false
                    }
                    when {
                        supports(state, PlaybackState.ACTION_STOP) -> {
                            controller.transportControls.stop()
                            awaitState(controller, PAUSE_CONFIRM_TIMEOUT_MS) {
                                it?.state in setOf(PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED)
                            }
                        }
                        state.state in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING) &&
                            supports(state, PlaybackState.ACTION_PAUSE) -> {
                            controller.transportControls.pause()
                            awaitState(controller, PAUSE_CONFIRM_TIMEOUT_MS) {
                                it?.state == PlaybackState.STATE_PAUSED
                            }
                        }
                        state.state == PlaybackState.STATE_PAUSED -> true
                        else -> false
                    }
                } finally {
                    browser.disconnect()
                }
            } ?: false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 「關閉音樂」也要能停掉 fallback 那台，否則暫停的是原生播放器、
     * 停的卻是設定裡的 App。處理完一律清除引用，避免之後誤恢復。
     */
    private suspend fun stopFallbackSession(): Boolean {
        val controller = fallbackSession ?: return false
        try {
            return withTimeoutOrNull(VOICE_CONTROL_TIMEOUT_MS) {
                val state = controller.playbackState
                if (state == null || state.state in setOf(PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED)) {
                    return@withTimeoutOrNull false
                }
                when {
                    supports(state, PlaybackState.ACTION_STOP) -> {
                        controller.transportControls.stop()
                        awaitState(controller, PAUSE_CONFIRM_TIMEOUT_MS) {
                            it?.state in setOf(PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED)
                        }
                    }
                    state.state in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING) &&
                        supports(state, PlaybackState.ACTION_PAUSE) -> {
                        controller.transportControls.pause()
                        awaitState(controller, PAUSE_CONFIRM_TIMEOUT_MS) {
                            it?.state == PlaybackState.STATE_PAUSED
                        }
                    }
                    state.state == PlaybackState.STATE_PAUSED -> true
                    else -> false
                }
            } ?: false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        } finally {
            fallbackSession = null
        }
    }

    /** 「關閉音樂」走停止命令（清單留著）；本來就沒播也算達到目的。
     *  逾時不重送：已送達的 stop 遲早生效，重送沒有額外好處。 */
    private suspend fun stopMusicCabin(): Boolean {
        val result = sendVoiceCommand(MusicCabinVoiceControl.COMMAND_STOP, null, VOICE_STOP_TIMEOUT_MS)
            ?: return false
        return result.ok || result.status == "no_song"
    }

    private fun isInstalled(): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getApplicationInfo(
                musicPackageName,
                android.content.pm.PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getApplicationInfo(musicPackageName, 0)
        }
        true
    }.getOrDefault(false)

    override suspend fun execute(command: MusicCommand): MusicActionResult = withContext(Dispatchers.Main.immediate) {
        if (command == MusicCommand.Open) {
            return@withContext MusicAppLaunchReplyFormatter.explicitOpen(
                appLauncher.openOrNotify(),
                musicAppName,
            )
        }
        // App 內音量只走公開語音通道；其他播放器不支援，也不用系統音量代打。
        if (command.isVolumeControl && !isVoiceControlPackage) {
            return@withContext MusicActionResult("$musicAppName 不支援用語音調整 App 內音量。")
        }
        if (isVoiceControlPackage) return@withContext executeViaVoiceControl(command)
        try {
            executeConnected(command)
        } catch (_: TimeoutCancellationException) {
            if (command == MusicCommand.RepeatOne || command == MusicCommand.RepeatOff ||
                command == MusicCommand.RepeatAll || command == MusicCommand.RepeatState
            ) {
                MusicActionResult("循環設定沒有確認成功，請再試一次。")
            } else if (command == MusicCommand.SaveCurrent || command == MusicCommand.RemoveCurrent ||
                command == MusicCommand.PlayFavorites
            ) {
                MusicActionResult("MusicCabin 喜愛清單操作等太久，請到 App 確認結果。")
            } else launchForPlaybackSetup(command)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            MusicActionResult("音樂 App 拒絕了控制要求。")
        } catch (_: Exception) {
            if (command == MusicCommand.RepeatOne || command == MusicCommand.RepeatOff ||
                command == MusicCommand.RepeatAll || command == MusicCommand.RepeatState
            ) {
                MusicActionResult("無法確認循環設定，沒有回報成功。")
            } else if (command == MusicCommand.SaveCurrent || command == MusicCommand.RemoveCurrent) {
                MusicActionResult("無法確認 MusicCabin 收藏狀態，請到 App 確認結果。")
            } else launchForPlaybackSetup(command)
        }
    }

    /**
     * MusicCabin 走公開語音通道：一次廣播、對方回什麼就說什麼。
     * 舊版沒有這個通道時提示更新，不走回舊連線（Issue #29 已確認）。
     */
    private suspend fun executeViaVoiceControl(command: MusicCommand): MusicActionResult {
        // 沒安裝走啟動引導：找不到 App 會直接回 NOT_FOUND 訊息，不會真的連上舊路。
        if (!isInstalled()) return launchForPlaybackSetup(command)
        val (voiceCommand, query) = MusicCabinVoiceControl.requestFor(command)
        val result = sendVoiceCommand(
            voiceCommand,
            query,
            retryOnTimeout = MusicCabinVoiceControl.isRetryableAfterTimeout(command),
        ) ?: return MusicActionResult("音樂操作失敗，請再試一次。")
        return MusicCabinVoiceReplies.forCommand(command, result, musicAppName)
    }

    /**
     * 送出語音通道指令並把（指令、狀態、耗時）寫進 ring log，供診斷匯出事後核對；送出失敗回 null。
     * retryOnTimeout 時，逾時對「重送不會改變結果」的指令（呼叫端以 isRetryableAfterTimeout 判斷）
     * 再送一次：第一次逾時多半是對方程序剛被廣播喚起、送出的動作仍會完成，重送把
     * 「動作已生效但回覆丟失」轉成確定回覆。僅逾時重送；送出丟例外（send_error）不重送。
     */
    private suspend fun sendVoiceCommand(
        voiceCommand: String,
        query: String?,
        timeoutMs: Long? = null,
        retryOnTimeout: Boolean = false,
    ): VoiceControlResult? {
        val first = sendOnce(voiceCommand, query, timeoutMs) ?: return null
        if (first.status != "timeout" || !retryOnTimeout) return first
        return sendOnce(voiceCommand, query, MusicCabinVoiceControl.RETRY_TIMEOUT_MS) ?: first
    }

    private suspend fun sendOnce(
        voiceCommand: String,
        query: String?,
        timeoutMs: Long?,
    ): VoiceControlResult? {
        val started = SystemClock.elapsedRealtime()
        return try {
            val result = voiceControl.send(voiceCommand, query, timeoutMs)
            M1DiagnosticsRepository.recordMusicVoiceControl(
                voiceCommand,
                result.status,
                SystemClock.elapsedRealtime() - started,
            )
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            M1DiagnosticsRepository.recordMusicVoiceControl(
                voiceCommand,
                "send_error",
                SystemClock.elapsedRealtime() - started,
            )
            null
        }
    }

    private suspend fun executeConnected(command: MusicCommand): MusicActionResult =
        withTimeout(CONNECTION_AND_ACTION_TIMEOUT_MS) {
            val browser = connect()
            try {
                control(browser, MediaController(appContext, browser.sessionToken), command)
            } finally {
                browser.disconnect()
            }
        }

    private suspend fun launchForPlaybackSetup(command: MusicCommand): MusicActionResult {
        // 音量指令沒有歌可以「先播放一首歌」，找不到 App 就照實說，不開 App 也不假稱已調整。
        if (command.isVolumeControl) {
            return MusicActionResult("找不到$musicAppName，無法調整音樂 App 的音量。")
        }
        if (command == MusicCommand.NowPlaying) {
            return MusicActionResult("目前無法讀取$musicAppName 的歌曲資訊。")
        }
        if (command == MusicCommand.RepeatOne || command == MusicCommand.RepeatOff ||
            command == MusicCommand.RepeatAll || command == MusicCommand.RepeatState
        ) {
            return MusicActionResult("目前無法連接$musicAppName，循環設定沒有變更。")
        }
        if (command !is MusicCommand.Play && command !is MusicCommand.SearchAndPlay &&
            command !is MusicCommand.PlayArtist && command != MusicCommand.PlayFavorites
        ) {
            return MusicActionResult("還沒連上$musicAppName，請先播放一首歌。")
        }
        val launchMode = appLauncher.openOrNotify()
        if (launchMode == MusicAppLaunchMode.DIRECT) {
            delay(APP_LAUNCH_RETRY_DELAY_MS)
            try {
                return executeConnected(command)
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
            } catch (_: Exception) {
                // The app opened but still needs its own first-run setup.
            }
        }
        return MusicAppLaunchReplyFormatter.playbackSetup(launchMode, musicAppName)
    }

    private suspend fun connect(): MediaBrowser = suspendCancellableCoroutine { continuation ->
        lateinit var browser: MediaBrowser
        browser = MediaBrowser(
            appContext,
            ComponentName(musicPackageName, musicServiceName),
            object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() {
                    if (continuation.isActive) continuation.resume(browser)
                }

                override fun onConnectionFailed() {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("connect-failed"))
                }

                override fun onConnectionSuspended() {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("connect-suspended"))
                }
            },
            null,
        )
        continuation.invokeOnCancellation { browser.disconnect() }
        browser.connect()
    }

    private suspend fun control(
        browser: MediaBrowser,
        controller: MediaController,
        command: MusicCommand,
    ): MusicActionResult {
        val state = controller.playbackState
        val controls = controller.transportControls
        when (command) {
            MusicCommand.RepeatOne, MusicCommand.RepeatOff, MusicCommand.RepeatAll ->
                return setRepeatMode(command)
            MusicCommand.RepeatState -> return readRepeatState()
            MusicCommand.VolumeUp, MusicCommand.VolumeDown, is MusicCommand.VolumeSet,
            MusicCommand.VolumeGet ->
                return MusicActionResult("$musicAppName 不支援用語音調整 App 內音量。")
            MusicCommand.NowPlaying -> {
                if (state?.state !in setOf(
                        PlaybackState.STATE_PLAYING,
                        PlaybackState.STATE_BUFFERING,
                        PlaybackState.STATE_PAUSED,
                    )
                ) return MusicActionResult("目前沒有正在播放的歌曲。")
                val metadata = controller.metadata
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?.substringBefore(", 播放次數")
                    ?.substringBefore("，播放次數")
                return MusicActionResult(MusicReplyFormatter.currentTrack(title, artist))
            }
            MusicCommand.SaveCurrent -> return changeCurrentFavorite(browser, controller, shouldBeLiked = true)
            MusicCommand.RemoveCurrent -> return changeCurrentFavorite(browser, controller, shouldBeLiked = false)
            MusicCommand.PlayFavorites -> return playFavorites(browser, controller)
            MusicCommand.Play -> {
                if (state?.state == PlaybackState.STATE_PLAYING) {
                    return nowPlaying(command, controller.metadata)
                }
                if (!supports(state, PlaybackState.ACTION_PLAY)) return MusicActionResult("現在還無法播放音樂。")
                controls.play()
                if (awaitState(controller, PLAYBACK_WAIT_MS) { it?.state == PlaybackState.STATE_PLAYING }) {
                    return nowPlaying(command, controller.metadata)
                }
                return MusicActionResult("已送出播放指令，但還沒確認音樂開始。")
            }
            MusicCommand.Pause -> {
                if (state?.state == PlaybackState.STATE_PAUSED && controller.metadata != null) {
                    return MusicActionResult("音樂已經暫停了。")
                }
                if (!supports(state, PlaybackState.ACTION_PAUSE)) return MusicActionResult("目前沒有可暫停的音樂。")
                controls.pause()
                return if (awaitState(controller, PLAYBACK_WAIT_MS) { it?.state == PlaybackState.STATE_PAUSED }) {
                    MusicActionResult("好的，已暫停音樂。")
                } else MusicActionResult("已送出暫停指令，但還沒確認音樂暫停。")
            }
            MusicCommand.Next, MusicCommand.Previous -> {
                val next = command == MusicCommand.Next
                val action = if (next) PlaybackState.ACTION_SKIP_TO_NEXT else PlaybackState.ACTION_SKIP_TO_PREVIOUS
                if (!supports(state, action) || controller.metadata == null) {
                    return MusicActionResult("目前沒有可切換的歌曲。")
                }
                val oldId = controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                val oldTitle = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                if (next) controls.skipToNext() else controls.skipToPrevious()
                val changed = awaitState(controller, PLAYBACK_WAIT_MS) {
                    val currentId = controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                    val currentTitle = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    (currentId != null && currentId != oldId) || (currentTitle != null && currentTitle != oldTitle)
                }
                return if (changed) nowPlaying(command, controller.metadata)
                else MusicActionResult("已送出切歌指令，但還沒確認曲目變更。")
            }
            is MusicCommand.SearchAndPlay -> {
                return searchAndPlay(controller, command, command.query)
            }
            is MusicCommand.PlayArtist -> {
                return searchAndPlay(controller, command, command.artist)
            }
            MusicCommand.Open -> error("Handled before connecting")
        }
    }

    /** 舊路（非 MusicCabin 播放器）：歌手播就當成用歌手名搜尋播放。 */
    private suspend fun searchAndPlay(
        controller: MediaController,
        command: MusicCommand,
        query: String,
    ): MusicActionResult {
        val state = controller.playbackState
        val controls = controller.transportControls
        if (!supports(state, PlaybackState.ACTION_PLAY_FROM_SEARCH)) {
            return MusicActionResult("$musicAppName 沒有提供語音搜尋播放介面。")
        }
        val oldId = controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        val oldTitle = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        controls.playFromSearch(query, Bundle.EMPTY)
        val playing = awaitState(controller, SEARCH_WAIT_MS) {
            val currentId = controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            val currentTitle = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            it?.state == PlaybackState.STATE_PLAYING && currentTitle != null &&
                (currentId != oldId || currentTitle != oldTitle)
        }
        return if (playing) nowPlaying(command, controller.metadata)
        else MusicActionResult("$musicAppName 尚未找到並開始播放這首歌，請在 App 中確認搜尋結果。")
    }

    private suspend fun setRepeatMode(command: MusicCommand): MusicActionResult {
        if (musicPackageName == DEFAULT_PACKAGE_NAME && !MusicCabinFavoriteAccess.isGranted(appContext)) {
            return MusicActionResult("單曲循環需要通知存取權，請到小助手的音樂 App 設定開啟。")
        }
        val target = when (command) {
            MusicCommand.RepeatOne -> Player.REPEAT_MODE_ONE
            MusicCommand.RepeatAll -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        // 全部循環經自訂切換最多轉三段（單曲→關→全部），單曲／關兩段就夠。
        val maxSteps = if (target == Player.REPEAT_MODE_ALL) 3 else 2
        val token = SessionToken(appContext, ComponentName(musicPackageName, musicServiceName))
        val future = Media3Controller.Builder(appContext, token).buildAsync()
        try {
            val controller = awaitFuture(future)
            val customToggle = SessionCommand(TOGGLE_REPEAT_ACTION, Bundle.EMPTY)
            if (!controller.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE) &&
                !controller.availableSessionCommands.contains(customToggle)
            ) {
                return MusicActionResult("$musicAppName 沒有開放循環控制。")
            }
            if (controller.repeatMode != target) {
                if (controller.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)) {
                    controller.repeatMode = target
                } else {
                    repeat(maxSteps) {
                        if (controller.repeatMode == target) return@repeat
                        val previous = controller.repeatMode
                        val result = awaitFuture(controller.sendCustomCommand(customToggle, Bundle.EMPTY))
                        if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                            return MusicActionResult("$musicAppName 拒絕循環控制，設定沒有變更。")
                        }
                        val changed = withTimeoutOrNull(REPEAT_STEP_TIMEOUT_MS) {
                            while (controller.repeatMode == previous) delay(100)
                            true
                        } == true
                        if (!changed) return MusicActionResult("循環設定沒有確認成功，請再試一次。")
                    }
                }
            }
            val confirmed = withTimeoutOrNull(REPEAT_CONFIRM_TIMEOUT_MS) {
                val checkFuture = Media3Controller.Builder(appContext, token).buildAsync()
                try {
                    val check = awaitFuture(checkFuture)
                    while (check.repeatMode != target) delay(100)
                    true
                } finally {
                    Media3Controller.releaseFuture(checkFuture)
                }
            } == true
            return MusicActionResult(if (confirmed) {
                if (target == Player.REPEAT_MODE_ONE) "已開啟單曲循環。"
                else if (target == Player.REPEAT_MODE_ALL) "已開啟全部循環。"
                else "已取消單曲循環。"
            } else {
                "循環設定沒有確認成功，請再試一次。"
            })
        } finally {
            Media3Controller.releaseFuture(future)
        }
    }

    /** 舊路（非 MusicCabin 播放器）：直接讀目前的循環模式，不切換。 */
    private suspend fun readRepeatState(): MusicActionResult {
        val token = SessionToken(appContext, ComponentName(musicPackageName, musicServiceName))
        val future = Media3Controller.Builder(appContext, token).buildAsync()
        return try {
            val controller = awaitFuture(future)
            when (controller.repeatMode) {
                Player.REPEAT_MODE_ONE -> MusicActionResult("目前是單曲循環。")
                Player.REPEAT_MODE_ALL -> MusicActionResult("目前是全部循環。")
                else -> MusicActionResult("目前沒有開循環。")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MusicActionResult("目前無法查詢循環模式。")
        } finally {
            Media3Controller.releaseFuture(future)
        }
    }

    private suspend fun changeCurrentFavorite(
        browser: MediaBrowser,
        controller: MediaController,
        shouldBeLiked: Boolean,
    ): MusicActionResult {
        if (musicPackageName != DEFAULT_PACKAGE_NAME) {
            return MusicActionResult("收藏功能目前只支援 MusicCabin，請在設定中選擇 MusicCabin。")
        }
        val mediaId = currentSongId(controller)
            ?: return MusicActionResult("目前沒有可${if (shouldBeLiked) "收藏" else "取消收藏"}的 MusicCabin 歌曲。")
        val initiallyLiked = isLiked(browser, mediaId)
        if (initiallyLiked == shouldBeLiked) {
            return MusicActionResult(if (shouldBeLiked) "已經收藏過了" else "這首歌尚未收藏。")
        }
        if (!MusicCabinFavoriteAccess.isGranted(appContext)) {
            return MusicActionResult("要變更 MusicCabin 收藏，請到小幫手設定的「音樂 App」開啟通知存取權。")
        }
        if (currentSongId(controller) != mediaId) {
            return MusicActionResult("歌曲剛剛切換了，請再說一次${if (shouldBeLiked) "收藏" else "取消收藏"}。")
        }
        when (sendMedia3LikeCommand(mediaId, controller)) {
            LikeCommandOutcome.CHANGED_SONG ->
                return MusicActionResult("歌曲剛剛切換了，請再說一次${if (shouldBeLiked) "收藏" else "取消收藏"}。")
            LikeCommandOutcome.NOT_AVAILABLE ->
                return MusicActionResult("MusicCabin 未開放收藏指令，請確認通知存取權已啟用。")
            LikeCommandOutcome.REJECTED ->
                return MusicActionResult("MusicCabin 拒絕了收藏變更，沒有修改喜愛清單。")
            LikeCommandOutcome.ACCEPTED -> Unit
        }
        val started = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - started < SAVE_CONFIRM_WAIT_MS) {
            delay(SAVE_CONFIRM_POLL_MS)
            if (currentSongId(controller) != mediaId) {
                return MusicActionResult("已送出收藏變更，但歌曲已切換，請到 MusicCabin 確認結果。")
            }
            if (isLiked(browser, mediaId) == shouldBeLiked) {
                return MusicActionResult(if (shouldBeLiked) "收藏成功" else "已取消")
            }
        }
        return MusicActionResult("收藏變更失敗，MusicCabin 沒有更新喜愛清單。")
    }

    private suspend fun sendMedia3LikeCommand(
        expectedSongId: String,
        platformController: MediaController,
    ): LikeCommandOutcome {
        val token = SessionToken(appContext, ComponentName(musicPackageName, musicServiceName))
        val future = Media3Controller.Builder(appContext, token).buildAsync()
        try {
            val controller = awaitFuture(future)
            if (currentSongId(platformController) != expectedSongId ||
                controller.currentMediaItem?.mediaId?.substringAfterLast('/')
                    ?.let { it != expectedSongId } == true
            ) {
                return LikeCommandOutcome.CHANGED_SONG
            }
            val command = SessionCommand(TOGGLE_LIKE_ACTION, Bundle.EMPTY)
            if (!controller.availableSessionCommands.contains(command)) {
                return LikeCommandOutcome.NOT_AVAILABLE
            }
            val result = awaitFuture(controller.sendCustomCommand(command, Bundle.EMPTY))
            return if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                LikeCommandOutcome.ACCEPTED
            } else LikeCommandOutcome.REJECTED
        } finally {
            Media3Controller.releaseFuture(future)
        }
    }

    private suspend fun <T> awaitFuture(future: ListenableFuture<T>): T =
        suspendCancellableCoroutine { continuation ->
            future.addListener({
                try {
                    val result = future.get()
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            if (error is ExecutionException) error.cause ?: error else error,
                        )
                    }
                }
            }, ContextCompat.getMainExecutor(appContext))
            continuation.invokeOnCancellation { future.cancel(true) }
        }

    private enum class LikeCommandOutcome { ACCEPTED, NOT_AVAILABLE, REJECTED, CHANGED_SONG }

    private suspend fun playFavorites(browser: MediaBrowser, controller: MediaController): MusicActionResult {
        if (musicPackageName != DEFAULT_PACKAGE_NAME) {
            return MusicActionResult("播放喜愛清單目前只支援 MusicCabin，請在設定中選擇 MusicCabin。")
        }
        val first = likedPage(browser, LIKED_PLAYLIST_ID)
            .firstOrNull { it.isPlayable && it.mediaId?.startsWith("$LIKED_PLAYLIST_ID/") == true }
            ?: return MusicActionResult("MusicCabin 的喜愛清單目前沒有歌曲。")
        val firstId = first.mediaId ?: return MusicActionResult("MusicCabin 的喜愛清單資料不完整。")
        if (!supports(controller.playbackState, PlaybackState.ACTION_PLAY_FROM_MEDIA_ID)) {
            return MusicActionResult("MusicCabin 目前無法從喜愛清單開始播放。")
        }
        // 開發決定（rc19）：MusicCabin 實際已開始播放時，MediaSession 的佇列、歌曲 ID
        // 和播放狀態仍可能延遲或不一致。這個入口只確認清單非空且支援播放指令；
        // 送出後不再比對清單／等待狀態，以免錯誤回覆「尚未確認」。收藏與取消收藏仍須驗證結果。
        controller.transportControls.playFromMediaId(firstId, Bundle.EMPTY)
        return MusicActionResult(
            message = "好的，正在播放喜愛歌曲。",
            deferredMessage = "好的，準備播放喜愛歌曲。",
        )
    }

    private fun currentSongId(controller: MediaController): String? {
        val state = controller.playbackState?.state
        if (state == null || state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED) return null
        return controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            ?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }

    private suspend fun isLiked(browser: MediaBrowser, songId: String): Boolean {
        var parent = LIKED_PLAYLIST_ID
        val visited = mutableSetOf<String>()
        while (visited.add(parent)) {
            val children = likedPage(browser, parent)
            if (children.any { it.isPlayable && it.mediaId?.startsWith("$LIKED_PLAYLIST_ID/") == true &&
                    it.mediaId?.substringAfterLast('/') == songId }) return true
            parent = children.firstOrNull { it.mediaId?.startsWith("android_auto_page/") == true }
                ?.mediaId ?: return false
        }
        error("MusicCabin returned a repeated playlist page")
    }

    private suspend fun likedPage(browser: MediaBrowser, parentId: String): List<MediaBrowser.MediaItem> =
        suspendCancellableCoroutine { continuation ->
            val callback = object : MediaBrowser.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowser.MediaItem>) {
                    browser.unsubscribe(parentId, this)
                    if (continuation.isActive) continuation.resume(children.toList())
                }

                override fun onError(parentId: String) {
                    browser.unsubscribe(parentId, this)
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("browse-failed"))
                }
            }
            continuation.invokeOnCancellation { browser.unsubscribe(parentId, callback) }
            browser.subscribe(parentId, callback)
        }

    private fun supports(state: PlaybackState?, action: Long): Boolean =
        state != null && state.actions and action != 0L

    private suspend fun awaitState(
        controller: MediaController,
        timeoutMs: Long,
        predicate: (PlaybackState?) -> Boolean,
    ): Boolean {
        val started = android.os.SystemClock.elapsedRealtime()
        while (android.os.SystemClock.elapsedRealtime() - started < timeoutMs) {
            if (predicate(controller.playbackState)) return true
            delay(200)
        }
        return predicate(controller.playbackState)
    }

    private fun nowPlaying(command: MusicCommand, metadata: MediaMetadata?): MusicActionResult {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.substringBefore(", 播放次數")
            ?.substringBefore("，播放次數")
            ?.takeIf { it.isNotBlank() }
        return MusicReplyFormatter.confirmedPlayback(command, title, artist)
    }

    companion object {
        const val DEFAULT_PACKAGE_NAME = "com.jajafu.musiccabin"
        const val DEFAULT_SERVICE_NAME = "com.metrolist.music.playback.MusicService"
        private const val LIKED_PLAYLIST_ID = "playlist/LP_LIKED"
        private const val TOGGLE_LIKE_ACTION = "TOGGLE_LIKE"
        private const val TOGGLE_REPEAT_ACTION = "TOGGLE_REPEAT_MODE"
        private const val SAVE_CONFIRM_WAIT_MS = 4_000L
        private const val SAVE_CONFIRM_POLL_MS = 350L
        private const val CONNECTION_AND_ACTION_TIMEOUT_MS = 30_000L
        private const val APP_LAUNCH_RETRY_DELAY_MS = 1_500L
        private const val VOICE_CONTROL_TIMEOUT_MS = 2_000L
        private const val VOICE_PAUSE_TIMEOUT_MS = 4_000L
        private const val VOICE_RESUME_TIMEOUT_MS = 6_000L
        private const val VOICE_STOP_TIMEOUT_MS = 6_000L
        private const val PAUSE_CONFIRM_TIMEOUT_MS = 900L
        private const val RESUME_CONFIRM_TIMEOUT_MS = 1_500L
        private const val PLAYBACK_WAIT_MS = 4_000L
        private const val SEARCH_WAIT_MS = 20_000L
        private const val REPEAT_CONFIRM_TIMEOUT_MS = 3_000L
        private const val REPEAT_STEP_TIMEOUT_MS = 1_000L
    }
}
