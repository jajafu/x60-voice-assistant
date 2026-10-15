package com.va.voiceassistant.asr

import com.va.voiceassistant.audio.AudioCaptureEngine
import com.va.voiceassistant.audio.AudioFrame
import com.va.voiceassistant.orchestration.MonotonicClock
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import java.io.File
import kotlin.math.max

class SherpaSpeechRecognizerEngine(
    override val modelId: String,
    modelDirectory: File,
    pack: AsrModelPack,
    private val clock: MonotonicClock,
) : SpeechRecognizerEngine {
    private val recognizer: OnlineRecognizer
    private var stream: OnlineStream? = null
    private var acceptedSamples = 0L
    private var recognitionWorkMs = 0L
    private var endpointAtMs = 0L
    private var lastPartial = ""

    init {
        val model = File(modelDirectory, pack.modelFile)
        val tokens = File(modelDirectory, pack.tokensFile)
        if (!model.isFile || !tokens.isFile) throw AsrModelInvalidException("ASR model files are missing")
        recognizer = OnlineRecognizer(
            assetManager = null,
            config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = AudioCaptureEngine.SAMPLE_RATE_HZ, featureDim = 80, dither = 0f),
                modelConfig = OnlineModelConfig(
                    zipformer2Ctc = OnlineZipformer2CtcModelConfig(model.absolutePath),
                    tokens = tokens.absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                    modelingUnit = if (pack.bpeVocabFile != null) "bpe" else "cjkchar",
                    bpeVocab = pack.bpeVocabFile?.let { File(modelDirectory, it).absolutePath }.orEmpty(),
                ),
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 0.8f, 0f),
                    rule2 = EndpointRule(true, 0.8f, 0f),
                    rule3 = EndpointRule(false, 0f, 20f),
                ),
                enableEndpoint = false,
                decodingMethod = "greedy_search",
                maxActivePaths = 4,
            ),
        )
    }

    override fun beginSession() {
        cancel()
        stream = recognizer.createStream()
        acceptedSamples = 0
        recognitionWorkMs = 0
        endpointAtMs = 0
        lastPartial = ""
    }

    override fun accept(frame: AudioFrame): String? {
        val activeStream = checkNotNull(stream) { "ASR session is not active" }
        val before = clock.elapsedRealtimeMs()
        val samples = FloatArray(frame.samples.size) { frame.samples[it] / 32768f }
        activeStream.acceptWaveform(samples, AudioCaptureEngine.SAMPLE_RATE_HZ)
        acceptedSamples += samples.size
        while (recognizer.isReady(activeStream)) recognizer.decode(activeStream)
        recognitionWorkMs += max(0, clock.elapsedRealtimeMs() - before)
        val partial = recognizer.getResult(activeStream).text.trim()
        return partial.takeIf { it.isNotBlank() && it != lastPartial }?.also { lastPartial = it }
    }

    override fun finish(): AsrResult {
        val activeStream = checkNotNull(stream) { "ASR session is not active" }
        endpointAtMs = clock.elapsedRealtimeMs()
        val before = endpointAtMs
        activeStream.inputFinished()
        while (recognizer.isReady(activeStream)) recognizer.decode(activeStream)
        val text = recognizer.getResult(activeStream).text.trim()
        val after = clock.elapsedRealtimeMs()
        recognitionWorkMs += max(0, after - before)
        val audioDurationMs = acceptedSamples * 1_000 / AudioCaptureEngine.SAMPLE_RATE_HZ
        val result = AsrResult(
            text = text,
            audioDurationMs = audioDurationMs,
            recognitionDurationMs = recognitionWorkMs,
            realTimeFactor = if (audioDurationMs == 0L) 0.0 else recognitionWorkMs.toDouble() / audioDurationMs,
            finalLatencyMs = max(0, after - endpointAtMs),
        )
        activeStream.release()
        stream = null
        return result
    }

    override fun cancel() {
        runCatching { stream?.release() }
        stream = null
        acceptedSamples = 0
        recognitionWorkMs = 0
        lastPartial = ""
    }

    override fun close() {
        cancel()
        recognizer.release()
    }
}
