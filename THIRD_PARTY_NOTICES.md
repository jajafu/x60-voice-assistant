# Third-Party Notices

本專案（小助手 Voice Assistant）的原始碼依 [Apache License 2.0](LICENSE) 釋出。
以下第三方元件與模型權重不是本專案的著作，著作權仍屬原開發者；本專案不主張任何權利，也不對它們另行授權。

本 App **免費提供、不販售**。APK 僅由本專案的 GitHub Release 散布，內容與此處列出的版本一致。

## sherpa-onnx 1.13.8

- Project: `k2-fsa/sherpa-onnx`
- Source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8
- Runtime license: Apache License 2.0
- Android AAR SHA256: `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`

## 語音喚醒（KWS）模型

- Model ID: `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20`
- Source: https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2
- Archive SHA256: `68447f4fbc67e70eee3a93961f36e81e98f47aef73ce7e7ca00885c6cd3616a6`

**上游授權狀態（查核日期 2026-10-20）**

- ModelScope 模型頁（`pkufool/icefall-kws-zipformer-zh-en-3M-2025-12-20`）標示 **Apache License 2.0**，頁面更新日期 2026-10-08；維護者 `pkufool` 於上游 issue `#3802` 表示已將該模型頁授權更新為 Apache 2.0。
- GitHub release archive 仍未附 `LICENSE`／`NOTICE`；`#3802` 仍在追問該聲明是否涵蓋 archive 內的 ONNX 權重、`tokens.txt`、`en.phone`，以及商業使用、再散布與訓練資料來源。
- 同系列模型（`sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01`、`sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01`）同樣在模型庫標示 Apache-2.0。

**訓練語料**

- 同系列的英文模型使用 GigaSpeech，資料條款為「非商業研究與教育用途」。
- 同系列的中文模型使用 WenetSpeech，資料條款為「非商業」。
- 本專案使用的中英模型沒有公告訓練語料。
- 模型權重授權與訓練資料授權是兩件事；GigaSpeech 官方表示在其上訓練的模型可能可商用，但需由使用者自行確認。

**本專案的處理方式**

- 模型檔不納入 Git，只在免費、非商業的 APK 中原樣散布。
- 不修改模型、不另行授權、不主張著作權、不販售。
- 若要用於商業用途，請自行向上游確認；本專案不提供任何保證。

## Silero VAD

- Project: `snakers4/silero-vad`
- License: MIT
- 檔案：`silero_vad.onnx`（643,854 bytes，SHA256 `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`）

## OkHttp 4.12.0

- Project: Square OkHttp
- Source: https://github.com/square/okhttp
- License: Apache License 2.0

## AndroidX / Jetpack Compose

- Source: https://android.googlesource.com/platform/frameworks/support/
- License: Apache License 2.0

## 未隨 APK 散布的模型

1.0.15 之前的版本曾提供在 App 內下載 X-ASR 等備用 ASR 模型；1.0.16 起該功能移除，App 只使用手機系統語音辨識。App 現僅內建 KWS 與 Silero VAD 模型（授權狀態見上方與 `docs/model-packs.md`）。
