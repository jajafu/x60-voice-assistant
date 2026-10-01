package com.jajafu.x60assistant.music

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController as Media3Controller
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
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
) : MusicActionExecutor {
    private val appContext = context.applicationContext
    private val appLauncher = MusicAppLauncher(appContext, musicPackageName, musicAppName)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    override suspend fun pauseForVoice(): Boolean = withContext(Dispatchers.Main.immediate) {
        // 沒播音樂就不綁 MusicCabin 服務，省掉一次無用的 MediaBrowser 連線。
        if (audioManager?.isMusicActive == false) return@withContext false
        try {
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

    override suspend fun resumeAfterVoice(): Boolean = withContext(Dispatchers.Main.immediate) {
        try {
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

    override suspend fun stopForClose(): Boolean = withContext(Dispatchers.Main.immediate) {
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

    override suspend fun execute(command: MusicCommand): MusicActionResult = withContext(Dispatchers.Main.immediate) {
        if (command == MusicCommand.Open) {
            return@withContext MusicAppLaunchReplyFormatter.explicitOpen(
                appLauncher.openOrNotify(),
                musicAppName,
            )
        }
        try {
            executeConnected(command)
        } catch (_: TimeoutCancellationException) {
            if (command == MusicCommand.RepeatOne || command == MusicCommand.RepeatOff) {
                MusicActionResult("循環設定沒有確認成功，請再試一次。")
            } else if (command == MusicCommand.SaveCurrent || command == MusicCommand.RemoveCurrent ||
                command == MusicCommand.PlayFavorites) {
                MusicActionResult("MusicCabin 喜愛清單操作等太久，請到 App 確認結果。")
            } else launchForPlaybackSetup(command)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            MusicActionResult("音樂 App 拒絕了控制要求。")
        } catch (_: Exception) {
            if (command == MusicCommand.RepeatOne || command == MusicCommand.RepeatOff) {
                MusicActionResult("無法確認循環設定，沒有回報成功。")
            } else if (command == MusicCommand.SaveCurrent || command == MusicCommand.RemoveCurrent) {
                MusicActionResult("無法確認 MusicCabin 收藏狀態，請到 App 確認結果。")
            } else launchForPlaybackSetup(command)
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
        if (command == MusicCommand.NowPlaying) {
            return MusicActionResult("目前無法讀取$musicAppName 的歌曲資訊。")
        }
        if (command == MusicCommand.RepeatOne || command == MusicCommand.RepeatOff) {
            return MusicActionResult("目前無法連接$musicAppName，循環設定沒有變更。")
        }
        if (command !is MusicCommand.Play && command !is MusicCommand.SearchAndPlay &&
            command != MusicCommand.PlayFavorites
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
            MusicCommand.RepeatOne, MusicCommand.RepeatOff -> return setRepeatMode(command)
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
                if (!supports(state, PlaybackState.ACTION_PLAY_FROM_SEARCH)) {
                    return MusicActionResult("$musicAppName 沒有提供語音搜尋播放介面。")
                }
                val oldId = controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                val oldTitle = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                controls.playFromSearch(command.query, Bundle.EMPTY)
                val playing = awaitState(controller, SEARCH_WAIT_MS) {
                    val currentId = controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                    val currentTitle = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    it?.state == PlaybackState.STATE_PLAYING && currentTitle != null &&
                        (currentId != oldId || currentTitle != oldTitle)
                }
                return if (playing) nowPlaying(command, controller.metadata)
                else MusicActionResult("$musicAppName 尚未找到並開始播放這首歌，請在 App 中確認搜尋結果。")
            }
            MusicCommand.Open -> error("Handled before connecting")
        }
    }

    private suspend fun setRepeatMode(command: MusicCommand): MusicActionResult {
        if (musicPackageName == DEFAULT_PACKAGE_NAME && !MusicCabinFavoriteAccess.isGranted(appContext)) {
            return MusicActionResult("單曲循環需要通知存取權，請到小助手的音樂 App 設定開啟。")
        }
        val target = if (command == MusicCommand.RepeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
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
                    repeat(2) {
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
                if (target == Player.REPEAT_MODE_ONE) "已開啟單曲循環。" else "已取消單曲循環。"
            } else {
                "循環設定沒有確認成功，請再試一次。"
            })
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
        private const val PAUSE_CONFIRM_TIMEOUT_MS = 900L
        private const val RESUME_CONFIRM_TIMEOUT_MS = 1_500L
        private const val PLAYBACK_WAIT_MS = 4_000L
        private const val SEARCH_WAIT_MS = 20_000L
        private const val REPEAT_CONFIRM_TIMEOUT_MS = 3_000L
        private const val REPEAT_STEP_TIMEOUT_MS = 1_000L
    }
}
