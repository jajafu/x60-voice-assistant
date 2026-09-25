package com.jajafu.x60assistant.asr

enum class AsrRecognizerKind { ZIPFORMER_CTC, MOONSHINE_V2, X_ASR_OFFLINE_TRANSDUCER }

data class AsrModelPack(
    val id: String,
    val label: String,
    val archiveName: String,
    val archiveBytes: Long,
    val archiveSha256: String,
    val sourceUrl: String,
    val modelFile: String,
    val tokensFile: String,
    val recognizerKind: AsrRecognizerKind = AsrRecognizerKind.ZIPFORMER_CTC,
    val decoderFile: String? = null,
    val joinerFile: String? = null,
    val bpeVocabFile: String? = null,
    val files: Map<String, String>,
    val license: String = "upstream model license not declared; local evaluation only",
)

object AsrModelPacks {
    val standard = AsrModelPack(
        id = "sherpa-onnx-streaming-zipformer-ctc-zh-int8-2025-06-30",
        label = "Standard（約 162 MB）",
        archiveName = "sherpa-onnx-streaming-zipformer-ctc-zh-int8-2025-06-30.tar.bz2",
        archiveBytes = 127_965_713,
        archiveSha256 = "f2ab7a5deb02717801f6a5b26c751b42f8a2db891b07f5b095e6da7442081448",
        sourceUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-streaming-zipformer-ctc-zh-int8-2025-06-30.tar.bz2",
        modelFile = "model.int8.onnx",
        tokensFile = "tokens.txt",
        files = linkedMapOf(
            "model.int8.onnx" to "24ffdc19ba9aaed5a6a9beaede1e087745217d82425cf4041bca0c696661801e",
            "tokens.txt" to "6193c7ea1c96d0d9a1e9652789b40d13a8a913b434a5451e93158f5a09fd6652",
        ),
    )

    val moonshine = AsrModelPack(
        id = "sherpa-onnx-moonshine-base-zh-quantized-2026-02-27",
        label = "Moonshine v2 中文（約 141 MB）",
        archiveName = "sherpa-onnx-moonshine-base-zh-quantized-2026-02-27.tar.bz2",
        archiveBytes = 99_905_420,
        archiveSha256 = "6495d4240f66bcdd2bbbbfefaa687c463150d467854001270934e88940b733c1",
        sourceUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-moonshine-base-zh-quantized-2026-02-27.tar.bz2",
        modelFile = "encoder_model.ort",
        tokensFile = "tokens.txt",
        recognizerKind = AsrRecognizerKind.MOONSHINE_V2,
        decoderFile = "decoder_model_merged.ort",
        files = linkedMapOf(
            "encoder_model.ort" to "c725a24b58595905921ea2a47e2bcf0f18c78f4d171d96136f2dcbc8c77a58a6",
            "decoder_model_merged.ort" to "bf79fce626e123739ec37eceb2b2a010a93d720da266dd5d8ef9a47ef9a7dc36",
            "tokens.txt" to "2870d843e14c1e187bf1913a521562a63b53933814bd7f2145120468f494a049",
            "LICENSE" to "6148d7574a6554b7379b633cfd4c4fe5840c3f548d13bc83e00b52dc6fa00abd",
        ),
        license = "Moonshine AI Community License; see LICENSE in model pack",
    )

    val xAsr = AsrModelPack(
        id = "sherpa-onnx-x-asr-zipformer-transducer-zh-en-punct-int8-2026-06-03",
        label = "X-ASR 中英混合（約 175 MB）",
        archiveName = "sherpa-onnx-x-asr-zipformer-transducer-zh-en-punct-int8-2026-06-03.tar.bz2",
        archiveBytes = 136_396_739,
        archiveSha256 = "5d02c36d7b44e886b7c8f0d8e051f8713acab96c264bb6ef9e718be39a6a2224",
        sourceUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-x-asr-zipformer-transducer-zh-en-punct-int8-2026-06-03.tar.bz2",
        modelFile = "encoder-epoch-99-avg-1.int8.onnx",
        tokensFile = "tokens.txt",
        recognizerKind = AsrRecognizerKind.X_ASR_OFFLINE_TRANSDUCER,
        decoderFile = "decoder-epoch-99-avg-1.onnx",
        joinerFile = "joiner-epoch-99-avg-1.int8.onnx",
        bpeVocabFile = "bpe.model",
        files = linkedMapOf(
            "encoder-epoch-99-avg-1.int8.onnx" to "7f6aa62056efd8af9da13e0faa81cd3f284d2fb2e3b63de56fd2dfd3450910dc",
            "decoder-epoch-99-avg-1.onnx" to "72f47405d3c1033bebccbef82f90071e7b4ba3e71b9c986f2b74244b25723aed",
            "joiner-epoch-99-avg-1.int8.onnx" to "aedb7fa697b2ab43f20499826fff7c997eea7d67db77be97769aeeeb726e63b3",
            "bpe.model" to "f87a38025a5fdd1e4e9591f6a44bb81295097ce0b80df6f4ab9f44e52c64ca5f",
            "tokens.txt" to "b818a60878b9aae978cbb8ad594acbd403d76d1af2e31ef4197c84e2dbdba27c",
        ),
        license = "upstream archive has no model-specific license; local evaluation only",
    )

    val all = listOf(standard, moonshine, xAsr)

    fun byId(id: String): AsrModelPack? = all.firstOrNull { it.id == id }
}
