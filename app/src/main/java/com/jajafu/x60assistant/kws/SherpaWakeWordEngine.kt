package com.jajafu.x60assistant.kws

import android.content.res.AssetManager
import android.os.SystemClock
import com.jajafu.x60assistant.audio.AudioCaptureEngine
import com.jajafu.x60assistant.audio.BoundedAudioFrameQueue
import com.jajafu.x60assistant.diagnostics.VoiceErrorCode
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.util.concurrent.atomic.AtomicBoolean

class SherpaWakeWordEngine(
    private val assetManager: AssetManager,
    private val frameQueue: BoundedAudioFrameQueue,
    private val onWake: (configuredScore: Float) -> Unit,
    private val onFatalError: (VoiceErrorCode, String) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val nativeResourceLock = Any()

    @Volatile
    private var worker: Thread? = null
    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var lastWakeElapsedMs = Long.MIN_VALUE

    fun start() {
        check(!running.get()) { "SherpaWakeWordEngine is already running" }
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(
                sampleRate = AudioCaptureEngine.SAMPLE_RATE_HZ,
                featureDim = 80,
                dither = 0.0f,
            ),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = KwsModelPack.ENCODER,
                    decoder = KwsModelPack.DECODER,
                    joiner = KwsModelPack.JOINER,
                ),
                tokens = KwsModelPack.TOKENS,
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "zipformer2",
            ),
            maxActivePaths = 4,
            keywordsFile = KwsModelPack.KEYWORDS,
            keywordsScore = KwsModelPack.KEYWORDS_SCORE,
            keywordsThreshold = KwsModelPack.KEYWORDS_THRESHOLD,
            numTrailingBlanks = 2,
        )
        val createdSpotter = KeywordSpotter(assetManager = assetManager, config = config)
        val createdStream = try {
            createdSpotter.createStream()
        } catch (error: Throwable) {
            createdSpotter.release()
            throw error
        }
        spotter = createdSpotter
        stream = createdStream
        running.set(true)
        worker = Thread(::consumeLoop, "x60-sherpa-kws").apply { start() }
    }

    fun stop() {
        running.set(false)
        val currentWorker = worker
        currentWorker?.interrupt()
        if (Thread.currentThread() !== currentWorker) {
            runCatching { currentWorker?.join(THREAD_JOIN_TIMEOUT_MS) }
        }
        if (currentWorker == null || !currentWorker.isAlive) releaseNativeResources()
        frameQueue.clear()
    }

    private fun consumeLoop() {
        try {
            while (running.get()) {
                val frame = frameQueue.poll(FRAME_POLL_TIMEOUT_MS) ?: continue
                val samples = FloatArray(frame.samples.size) { index ->
                    frame.samples[index] / 32768.0f
                }
                val currentStream = stream ?: break
                val currentSpotter = spotter ?: break
                currentStream.acceptWaveform(samples, AudioCaptureEngine.SAMPLE_RATE_HZ)
                while (running.get() && currentSpotter.isReady(currentStream)) {
                    currentSpotter.decode(currentStream)
                    val result = currentSpotter.getResult(currentStream)
                    if (result.keyword == KwsModelPack.KEYWORD) {
                        currentSpotter.reset(currentStream)
                        val now = SystemClock.elapsedRealtime()
                        if (lastWakeElapsedMs == Long.MIN_VALUE ||
                            now - lastWakeElapsedMs >= KwsModelPack.COOLDOWN_MS
                        ) {
                            lastWakeElapsedMs = now
                            onWake(KwsModelPack.KEYWORDS_SCORE)
                        }
                    }
                }
            }
        } catch (_: InterruptedException) {
            // Normal stop path.
        } catch (error: Throwable) {
            if (running.getAndSet(false)) {
                onFatalError(VoiceErrorCode.KWS_RUNTIME_FAILED, error.javaClass.simpleName)
            }
        } finally {
            worker = null
            releaseNativeResources()
        }
    }

    private fun releaseNativeResources() {
        synchronized(nativeResourceLock) {
            runCatching { stream?.release() }
            stream = null
            runCatching { spotter?.release() }
            spotter = null
        }
    }

    companion object {
        private const val FRAME_POLL_TIMEOUT_MS = 250L
        private const val THREAD_JOIN_TIMEOUT_MS = 1_500L
    }
}
