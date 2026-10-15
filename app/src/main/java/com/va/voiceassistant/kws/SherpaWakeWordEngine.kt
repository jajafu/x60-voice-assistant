package com.va.voiceassistant.kws

import android.content.res.AssetManager
import android.os.SystemClock
import com.va.voiceassistant.audio.AudioCaptureEngine
import com.va.voiceassistant.audio.AudioFrame
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/** Frame-driven KWS. It never owns a microphone, thread, queue, player, or state. */
class SherpaWakeWordEngine(
    assetManager: AssetManager,
    private val wakePhrase: String = KwsModelPack.KEYWORD,
) : WakeWordEngine {
    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var lastWakeElapsedMs = Long.MIN_VALUE

    init {
        val keywordSpec = WakePhraseCompiler.compile(assetManager, wakePhrase)
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
        spotter = createdSpotter
        stream = try {
            createdSpotter.createStream(keywordSpec)
        } catch (error: Throwable) {
            createdSpotter.release()
            spotter = null
            throw error
        }
    }

    override fun accept(frame: AudioFrame): Boolean {
        val activeSpotter = checkNotNull(spotter) { "KWS is closed" }
        val activeStream = checkNotNull(stream) { "KWS is closed" }
        val samples = FloatArray(frame.samples.size) { frame.samples[it] / 32768.0f }
        activeStream.acceptWaveform(samples, AudioCaptureEngine.SAMPLE_RATE_HZ)
        while (activeSpotter.isReady(activeStream)) {
            activeSpotter.decode(activeStream)
            if (activeSpotter.getResult(activeStream).keyword == wakePhrase) {
                activeSpotter.reset(activeStream)
                val now = SystemClock.elapsedRealtime()
                if (lastWakeElapsedMs == Long.MIN_VALUE || now - lastWakeElapsedMs >= KwsModelPack.COOLDOWN_MS) {
                    lastWakeElapsedMs = now
                    return true
                }
            }
        }
        return false
    }

    override fun reset() {
        val activeSpotter = spotter ?: return
        val activeStream = stream ?: return
        activeSpotter.reset(activeStream)
    }

    override fun close() {
        runCatching { stream?.release() }
        stream = null
        runCatching { spotter?.release() }
        spotter = null
    }
}
