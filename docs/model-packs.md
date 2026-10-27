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
| Model license | ModelScope 模型頁標示 Apache License 2.0（2026-10-08 更新）；GitHub archive 與配套檔適用範圍待上游確認 |

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

sherpa runtime repository 的 Apache-2.0 不應自動推定為 model weights 的授權。`pkufool/icefall-kws-zipformer-zh-en-3M-2025-12-20` 的 [ModelScope 模型頁](https://www.modelscope.cn/models/pkufool/icefall-kws-zipformer-zh-en-3M-2025-12-20)目前標示 `Apache License 2.0`，頁面更新日期為 2026-10-08；維護者 `pkufool` 亦於 [官方 issue #3802](https://github.com/k2-fsa/sherpa-onnx/issues/3802#issuecomment-6052663364) 表示已更新該 ModelScope repo 的授權為 Apache 2.0。

但 sherpa-onnx GitHub release archive 仍未附 `LICENSE`／`NOTICE`，且截至 2026-10-19，#3802 仍在追問該聲明是否涵蓋這個 GitHub archive 的 ONNX 權重、`tokens.txt`、`en.phone`，以及商業使用、再散布和訓練資料來源。故目前只能記錄「ModelScope repo 宣告 Apache-2.0」；不能據此宣稱 GitHub archive 內每個檔案、其來源資料或含模型 APK 的公開散布已獲明確確認。`#3760` 已由維護者指向 #3802，授權討論仍以後者為準。

因此 Repository 只提交來源、檔名、大小、格式與 SHA256，以及由專案自行建立的 keyword；模型 binary 與 `tokens.txt` 都被 `.gitignore` 排除。`scripts/prepare-m1-kws-assets.ps1`（KWS）與 `scripts/prepare-vad-asset.ps1`（Silero VAD）僅供本機與 release 建置準備。本專案免費散布、不販售；APK 內的模型原樣散布，不修改、不另行授權，著作權屬上游。商業使用請自行向上游確認，詳見 `THIRD_PARTY_NOTICES.md`。

## M2 VAD

| Pack | Archive / file | Bytes | SHA256 | 用途 |
|---|---|---:|---|---|
| Silero VAD | `silero_vad.onnx` | 643,854 | `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6` | APK asset，本機 VAD；上游 MIT |

Silero VAD 由 `scripts/prepare-vad-asset.ps1` 下載並校驗後放入 `app/src/main/assets/models/vad/silero_vad.onnx`；它是 APK 內建資產，服務啟動時若缺檔會直接以 `VAD_INIT_FAILED` 停止。release workflow 與 `assembleRelease` 前的 `verifyReleaseModelAssets` 檢查都會確認它在 APK 內。

> 1.0.16 起 App 只使用系統語音辨識，不再內建或下載任何 ASR 模型；本頁曾記錄的 Standard／Moonshine／X-ASR 下載式模型 pack（含逐檔 SHA256 與授權狀態）已隨功能移除，歷史紀錄見 Git 歷程與 `docs/decision-log.md`。
