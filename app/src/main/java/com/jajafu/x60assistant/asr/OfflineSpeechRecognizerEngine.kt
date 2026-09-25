package com.jajafu.x60assistant.asr

import com.jajafu.x60assistant.audio.AudioCaptureEngine
import com.jajafu.x60assistant.audio.AudioFrame
import com.jajafu.x60assistant.orchestration.MonotonicClock
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File
import kotlin.math.max

/** Decodes one complete VAD-ended utterance with sherpa's offline recognizer. */
class OfflineSpeechRecognizerEngine(
    override val modelId: String,
    modelDirectory: File,
    pack: AsrModelPack,
    private val clock: MonotonicClock,
) : SpeechRecognizerEngine {
    private val recognizer: OfflineRecognizer
    private val padShortAudio = pack.recognizerKind == AsrRecognizerKind.X_ASR_OFFLINE_TRANSDUCER
    private val chunks = ArrayList<ShortArray>()
    private var acceptedSamples = 0
    private var active = false

    init {
        val decoderName = requireNotNull(pack.decoderFile) { "ASR decoder is missing from pack metadata" }
        val encoder = File(modelDirectory, pack.modelFile)
        val decoder = File(modelDirectory, decoderName)
        val tokens = File(modelDirectory, pack.tokensFile)
        if (!encoder.isFile || !decoder.isFile || !tokens.isFile) {
            throw AsrModelInvalidException("Offline ASR model files are missing")
        }
        val modelConfig = when (pack.recognizerKind) {
            AsrRecognizerKind.MOONSHINE_V2 -> OfflineModelConfig(
                moonshine = OfflineMoonshineModelConfig(
                    encoder = encoder.absolutePath,
                    mergedDecoder = decoder.absolutePath,
                ),
                tokens = tokens.absolutePath,
                numThreads = 2,
                provider = "cpu",
            )
            AsrRecognizerKind.X_ASR_OFFLINE_TRANSDUCER -> {
                val joiner = File(modelDirectory, requireNotNull(pack.joinerFile))
                val bpe = File(modelDirectory, requireNotNull(pack.bpeVocabFile))
                if (!joiner.isFile || !bpe.isFile) {
                    throw AsrModelInvalidException("X-ASR model files are missing")
                }
                OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        joiner = joiner.absolutePath,
                    ),
                    tokens = tokens.absolutePath,
                    numThreads = 2,
                    provider = "cpu",
                    modelType = "transducer",
                    modelingUnit = "bpe",
                    bpeVocab = bpe.absolutePath,
                )
            }
            else -> throw IllegalArgumentException("Unsupported offline ASR model: ${pack.id}")
        }
        recognizer = OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = AudioCaptureEngine.SAMPLE_RATE_HZ, featureDim = 80, dither = 0f),
                modelConfig = modelConfig,
            ),
        )
    }

    override fun beginSession() {
        cancel()
        active = true
    }

    override fun accept(frame: AudioFrame): String? {
        check(active) { "ASR session is not active" }
        chunks += frame.samples.copyOf()
        acceptedSamples += frame.samples.size
        return null
    }

    override fun finish(): AsrResult {
        check(active) { "ASR session is not active" }
        val start = clock.elapsedRealtimeMs()
        val audioDurationMs = acceptedSamples.toLong() * 1_000 / AudioCaptureEngine.SAMPLE_RATE_HZ
        if (acceptedSamples == 0) {
            cancel()
            return AsrResult("", 0, 0, 0.0, 0)
        }
        try {
            // This X-ASR encoder can abort native inference on very short utterances.
            val sampleCount = if (padShortAudio) {
                max(acceptedSamples, AudioCaptureEngine.SAMPLE_RATE_HZ)
            } else {
                acceptedSamples
            }
            val samples = FloatArray(sampleCount)
            var offset = 0
            for (chunk in chunks) {
                for (sample in chunk) samples[offset++] = sample / 32768f
            }
            val stream = recognizer.createStream()
            val text = try {
                stream.acceptWaveform(samples, AudioCaptureEngine.SAMPLE_RATE_HZ)
                recognizer.decode(stream)
                recognizer.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
            val durationMs = max(0, clock.elapsedRealtimeMs() - start)
            return AsrResult(
                text = text,
                audioDurationMs = audioDurationMs,
                recognitionDurationMs = durationMs,
                realTimeFactor = durationMs.toDouble() / audioDurationMs,
                finalLatencyMs = durationMs,
            )
        } finally {
            cancel()
        }
    }

    override fun cancel() {
        chunks.clear()
        acceptedSamples = 0
        active = false
    }

    override fun close() {
        cancel()
        recognizer.release()
    }
}
