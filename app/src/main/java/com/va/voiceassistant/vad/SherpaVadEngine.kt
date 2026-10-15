package com.va.voiceassistant.vad

import android.content.res.AssetManager
import com.va.voiceassistant.audio.AudioFrame
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

class SherpaVadEngine(
    assetManager: AssetManager,
    modelPath: String = MODEL_ASSET_PATH,
    private val threshold: Float = DEFAULT_THRESHOLD,
) : VadEngine {
    private val vad = Vad(
        assetManager = assetManager,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = modelPath,
                threshold = threshold,
                minSilenceDuration = 0.25f,
                minSpeechDuration = 0.25f,
                windowSize = WINDOW_SAMPLES,
                maxSpeechDuration = 60f,
            ),
            sampleRate = 16_000,
            numThreads = 1,
            provider = "cpu",
            debug = false,
        ),
    )
    private val pending = ArrayDeque<Short>()
    private var lastObservation = VadObservation(false, null)

    override fun reset() {
        pending.clear()
        vad.reset()
        lastObservation = VadObservation(false, null)
    }

    override fun accept(frame: AudioFrame): VadObservation {
        frame.samples.forEach(pending::addLast)
        while (pending.size >= WINDOW_SAMPLES) {
            val samples = FloatArray(WINDOW_SAMPLES) { pending.removeFirst() / 32768f }
            val probability = vad.compute(samples)
            lastObservation = VadObservation(probability >= threshold, probability)
        }
        return lastObservation
    }

    override fun close() {
        pending.clear()
        vad.release()
    }

    companion object {
        const val MODEL_ASSET_PATH = "models/vad/silero_vad.onnx"
        const val MODEL_SHA256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
        const val WINDOW_SAMPLES = 512
        const val DEFAULT_THRESHOLD = 0.5f
    }
}
