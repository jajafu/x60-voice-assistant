# M1 KWS Model Pack

## Runtime

| 項目 | 值 |
|---|---|
| Project | `k2-fsa/sherpa-onnx` |
| Version | `1.13.8` |
| Android artifact | `sherpa-onnx-1.13.8.aar` |
| Size | 50,129,134 bytes |
| SHA256 | `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96` |
| Source | `https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8` |
| License | Apache-2.0（runtime source） |

Gradle 只從固定 `v1.13.8` release URL 解析 artifact，沒有 `latest`、`+` 或浮動版本。APK ABI 固定 `arm64-v8a`。

## Model

| 項目 | 值 |
|---|---|
| Model ID | `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20` |
| Archive | `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2` |
| Size | 32,885,699 bytes |
| SHA256 | `68447f4fbc67e70eee3a93961f36e81e98f47aef73ce7e7ca00885c6cd3616a6` |
| Source | `https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2` |
| Format | streaming Zipformer transducer ONNX；`phone+ppinyin` tokens |
| Model license | 上游未明示；未確認 |

App 使用以下最小組合：

| 檔名 | Bytes | SHA256 |
|---|---:|---|
| `encoder-epoch-13-avg-2-chunk-16-left-64.int8.onnx` | 4,599,656 | `408bbd740838c42d5bf6d1c5b80b3c88b616c7860b92d980328b5b068c76ae48` |
| `decoder-epoch-13-avg-2-chunk-16-left-64.onnx` | 759,829 | `63a22dd60f40fff082ac3e09afa507f6787da36df76ded2fbe145fa233e22c21` |
| `joiner-epoch-13-avg-2-chunk-16-left-64.int8.onnx` | 86,629 | `190d4067b4cc20b72a42a1916e69d92052000fb7051a427ebb1bc72a69207dc1` |
| `tokens.txt` | 1,928 | `2d3f32311f9b692b964da3c90e830258d3e78e013cb0c992dbfb15cd5a1a71b0` |

預設 `keywords.txt` 由 sherpa-onnx 1.13.8 `text2token` 以 `phone+ppinyin`、官方 `tokens.txt`／`en.phone` 產生：

```text
x iǎo zh ù sh ǒu @小助手
```

## 授權查核結論

sherpa runtime repository 的 Apache-2.0 不應自動推定為 model weights 的授權。官方模型 archive 不含 `LICENSE`／`NOTICE`，官方 KWS 頁與 release 沒有列出此模型權重及 `tokens.txt` 的授權。官方 issue `#3760` 曾要求釐清這三個 KWS model 的使用與再散布權，但在沒有維護者公開答覆的情況下關閉。

因此 Repository 只提交來源、檔名、大小、格式與 SHA256，以及由專案自行建立的 keyword；模型 binary 與 `tokens.txt` 都被 `.gitignore` 排除。`scripts/prepare-m1-kws-assets.ps1` 僅供本機個人評估。授權釐清前，不得發佈含模型 APK。

## M2 VAD / ASR

| Pack | Archive / file | Bytes | SHA256 | 用途 |
|---|---|---:|---|---|
| Silero VAD | `silero_vad.onnx` | 643,854 | `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6` | APK asset，本機 VAD；上游 MIT |
| Small ASR | `sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01.tar.bz2` | 21,264,113 | `b3b309f7ce4a737195fcc6963ea19b0653a7d3401580af5ae0d3e284cbb71f0b` | X60 預設 |
| Standard ASR | `sherpa-onnx-streaming-zipformer-ctc-zh-int8-2025-06-30.tar.bz2` | 127,965,713 | `f2ab7a5deb02717801f6a5b26c751b42f8a2db891b07f5b095e6da7442081448` | benchmark 候選 |

Small pack 逐檔：

| 檔名 | Bytes | SHA256 |
|---|---:|---|
| `model.int8.onnx` | 26,342,340 | `68c9c943840f7d9cf3e8a4970ba50f404feb5277f611fa82b7e72267786fa84a` |
| `tokens.txt` | — | `6fed8c6c248516f38e7faa19404b57413e8ce259f1cbc1fa4aebc86eac32fdfd` |
| `bbpe.model` | — | `503204e0690eff065e30d0e01898c9ab06d0e6dc376a741eb6846198f95b2f82` |

Standard pack 的 `model.int8.onnx` 為 162,290,887 bytes，SHA256 `24ffdc19ba9aaed5a6a9beaede1e087745217d82425cf4041bca0c696661801e`；`tokens.txt` SHA256 `6193c7ea1c96d0d9a1e9652789b40d13a8a913b434a5451e93158f5a09fd6652`。

兩個 ASR archive 來自 sherpa-onnx 官方 model release，與 1.13.8 的 OnlineRecognizer / Zipformer2 CTC Android API 相容；但 archive 未附模型專屬 `LICENSE`／`NOTICE`。因此 provisioning script 只供本機評估，ASR files 放在 App Private Storage 且不進 APK／Git。詳細比較與尚待測欄位見 `docs/asr-benchmark.md`。
