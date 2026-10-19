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

因此 Repository 只提交來源、檔名、大小、格式與 SHA256，以及由專案自行建立的 keyword；模型 binary 與 `tokens.txt` 都被 `.gitignore` 排除。`scripts/prepare-m1-kws-assets.ps1`（KWS）與 `scripts/prepare-vad-asset.ps1`（Silero VAD）僅供本機與 release 建置準備。上游尚未明確確認 GitHub archive 與所有配套檔的授權範圍前，維持不公開散布含此 KWS 模型 APK 的限制。

## M2 VAD / ASR

| Pack | Archive / file | Bytes | SHA256 | 用途 |
|---|---|---:|---|---|
| Silero VAD | `silero_vad.onnx` | 643,854 | `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6` | APK asset，本機 VAD；上游 MIT |
| Standard ASR | `sherpa-onnx-streaming-zipformer-ctc-zh-int8-2025-06-30.tar.bz2` | 127,965,713 | `f2ab7a5deb02717801f6a5b26c751b42f8a2db891b07f5b095e6da7442081448` | 預設備用 |
| Moonshine v2 base zh | `sherpa-onnx-moonshine-base-zh-quantized-2026-02-27.tar.bz2` | 99,905,420 | `6495d4240f66bcdd2bbbbfefaa687c463150d467854001270934e88940b733c1` | 中文比較候選；整句辨識 |
| X-ASR zh-en punct INT8 | `sherpa-onnx-x-asr-zipformer-transducer-zh-en-punct-int8-2026-06-03.tar.bz2` | 136,396,739 | `5d02c36d7b44e886b7c8f0d8e051f8713acab96c264bb6ef9e718be39a6a2224` | 改由「模型」頁按鈕從雲端硬碟下載；句尾交付辨識結果 |

Standard pack 的 `model.int8.onnx` 為 162,290,887 bytes，SHA256 `24ffdc19ba9aaed5a6a9beaede1e087745217d82425cf4041bca0c696661801e`；`tokens.txt` SHA256 `6193c7ea1c96d0d9a1e9652789b40d13a8a913b434a5451e93158f5a09fd6652`。

Silero VAD 由 `scripts/prepare-vad-asset.ps1` 下載並校驗後放入 `app/src/main/assets/models/vad/silero_vad.onnx`；它是 APK 內建資產，服務啟動時若缺檔會直接以 `VAD_INIT_FAILED` 停止。`scripts/prepare-m2-model-packs.ps1` 已改為呼叫同一支腳本，避免 URL／SHA256 兩份。release workflow 與 `assembleRelease` 前的 `verifyReleaseModelAssets` 檢查都會確認它在 APK 內。

Moonshine pack 逐檔：

| 檔名 | Bytes | SHA256 |
|---|---:|---|
| `encoder_model.ort` | 31,326,816 | `c725a24b58595905921ea2a47e2bcf0f18c78f4d171d96136f2dcbc8c77a58a6` |
| `decoder_model_merged.ort` | 109,424,520 | `bf79fce626e123739ec37eceb2b2a010a93d720da266dd5d8ef9a47ef9a7dc36` |
| `tokens.txt` | 549,350 | `2870d843e14c1e187bf1913a521562a63b53933814bd7f2145120468f494a049` |
| `LICENSE` | 13,344 | `6148d7574a6554b7379b633cfd4c4fe5840c3f548d13bc83e00b52dc6fa00abd` |

X-ASR pack 逐檔（以 GitHub release archive 內容計算；Hugging Face 目錄的 encoder／decoder 版本不同，不能混用雜湊）：

| 檔名 | Bytes | SHA256 |
|---|---:|---|
| `encoder-epoch-99-avg-1.int8.onnx` | 161,015,713 | `7f6aa62056efd8af9da13e0faa81cd3f284d2fb2e3b63de56fd2dfd3450910dc` |
| `decoder-epoch-99-avg-1.onnx` | 11,309,084 | `72f47405d3c1033bebccbef82f90071e7b4ba3e71b9c986f2b74244b25723aed` |
| `joiner-epoch-99-avg-1.int8.onnx` | 2,581,422 | `aedb7fa697b2ab43f20499826fff7c997eea7d67db77be97769aeeeb726e63b3` |
| `bpe.model` | 119,265 | `f87a38025a5fdd1e4e9591f6a44bb81295097ce0b80df6f4ab9f44e52c64ca5f` |
| `tokens.txt` | 58,806 | `b818a60878b9aae978cbb8ad594acbd403d76d1af2e31ef4197c84e2dbdba27c` |

三個 archive 均來自 sherpa-onnx 官方 model release。Standard 使用 1.13.8 的 `OnlineRecognizer` / Zipformer2 CTC API；X-ASR 與 Moonshine v2 使用 `OfflineRecognizer`，在 VAD 偵測一句話結束後辨識並交付結果。X-ASR release 的 encoder 介面是整句輸入，不適用 `OnlineRecognizer`。X-ASR 作者的 [X-ASR 模型頁](https://huggingface.co/GilgameshWind/X-ASR-zh-en)標示 Apache-2.0，但 sherpa-onnx 壓縮檔未附模型專屬授權檔；對外散布 APK 前仍需核對第三方聲明中的 KWS 模型授權。Moonshine 中文權重附有 Moonshine AI Community License。X-ASR 改由使用者雲端硬碟下載（App「模型」頁按鈕，下載後解壓到 App Private Storage 並逐檔驗證 SHA256），APK 不再內建；X-ASR 單一來源為使用者雲端硬碟（刻意取捨：APK 變小，但連結失效就無法安裝；屆時需更新 `X_ASR_DRIVE_FILE_ID` 並發版）。舊版已選的 Standard 或 Moonshine 設定會自動改用 X-ASR；既有模型檔不會被自動刪除。
