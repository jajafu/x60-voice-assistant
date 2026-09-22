# ASR Model Benchmark

## Candidate packs

| Model ID | Format | Archive bytes | Archive SHA256 | Model bytes | Model SHA256 | License | Runtime |
|---|---|---:|---|---:|---|---|---|
| `sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01` | streaming Zipformer2 CTC INT8 | 21,264,113 | `b3b309f7ce4a737195fcc6963ea19b0653a7d3401580af5ae0d3e284cbb71f0b` | 26,342,340 | `68c9c943840f7d9cf3e8a4970ba50f404feb5277f611fa82b7e72267786fa84a` | 上游 archive 未附模型授權；僅本機評估 | sherpa-onnx 1.13.8 |
| `sherpa-onnx-streaming-zipformer-ctc-zh-int8-2025-06-30` | streaming Zipformer2 CTC INT8 | 127,965,713 | `f2ab7a5deb02717801f6a5b26c751b42f8a2db891b07f5b095e6da7442081448` | 162,290,887 | `24ffdc19ba9aaed5a6a9beaede1e087745217d82425cf4041bca0c696661801e` | 上游 archive 未附模型授權；僅本機評估 | sherpa-onnx 1.13.8 |

兩個 archive 都由 sherpa-onnx 官方 `asr-models` release 取得並在 2026-09-22 實際計算 SHA256。模型與 tokens 不進 Git；App 載入前逐檔驗證 SHA256，錯誤時禁止載入。

## vivo X60 result

### Small INT8 candidate

| 項目 | 結果 |
|---|---|
| 測試方式 | Windows `zh-TW` 系統 TTS 從手機附近喇叭播放；不是真人語料 |
| 測試句 | 今天台北天氣如何 |
| Final transcript | 今天台北天氣如何 |
| 主要語意 | 保留 |
| RTF | 0.087 |
| Final result latency | 0 ms（毫秒 clock 下小於 1 ms；需正式批次再量） |
| Audio error / dropped frame | 0 / 0 |
| OOM / LMK / service restart | 無（單句短測） |

這一筆只證明 X60 上的 KWS → VAD → ASR 程式路徑可執行，不是 20 句準確率、P95、peak PSS、30 次穩定度或真人驗收。standard candidate 尚未安裝／執行，兩個模型的正式比較仍待驗收。
