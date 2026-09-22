package com.jajafu.x60assistant.asr

data class AsrModelPack(
    val id: String,
    val archiveName: String,
    val archiveBytes: Long,
    val archiveSha256: String,
    val sourceUrl: String,
    val modelFile: String,
    val tokensFile: String,
    val bpeVocabFile: String? = null,
    val files: Map<String, String>,
    val license: String = "upstream model license not declared; local evaluation only",
)

object AsrModelPacks {
    val small = AsrModelPack(
        id = "sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01",
        archiveName = "sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01.tar.bz2",
        archiveBytes = 21_264_113,
        archiveSha256 = "b3b309f7ce4a737195fcc6963ea19b0653a7d3401580af5ae0d3e284cbb71f0b",
        sourceUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01.tar.bz2",
        modelFile = "model.int8.onnx",
        tokensFile = "tokens.txt",
        bpeVocabFile = "bbpe.model",
        files = linkedMapOf(
            "model.int8.onnx" to "68c9c943840f7d9cf3e8a4970ba50f404feb5277f611fa82b7e72267786fa84a",
            "tokens.txt" to "6fed8c6c248516f38e7faa19404b57413e8ce259f1cbc1fa4aebc86eac32fdfd",
            "bbpe.model" to "503204e0690eff065e30d0e01898c9ab06d0e6dc376a741eb6846198f95b2f82",
        ),
    )

    val standard = AsrModelPack(
        id = "sherpa-onnx-streaming-zipformer-ctc-zh-int8-2025-06-30",
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

    val all = listOf(small, standard)

    fun byId(id: String): AsrModelPack? = all.firstOrNull { it.id == id }
}
