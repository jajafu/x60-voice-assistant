# ASR Model Benchmark

## Candidate packs

| Model ID | Format | Archive bytes | Archive SHA256 | Model bytes | Model SHA256 | License | Runtime |
|---|---|---:|---|---:|---|---|---|
| `sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01` | streaming Zipformer2 CTC INT8 | 21,264,113 | `b3b309f7ce4a737195fcc6963ea19b0653a7d3401580af5ae0d3e284cbb71f0b` | 26,342,340 | `68c9c943840f7d9cf3e8a4970ba50f404feb5277f611fa82b7e72267786fa84a` | 上游 archive 未附模型授權；僅本機評估 | sherpa-onnx 1.13.8 |
| `sherpa-onnx-streaming-zipformer-ctc-zh-int8-2025-06-30` | streaming Zipformer2 CTC INT8 | 127,965,713 | `f2ab7a5deb02717801f6a5b26c751b42f8a2db891b07f5b095e6da7442081448` | 162,290,887 | `24ffdc19ba9aaed5a6a9beaede1e087745217d82425cf4041bca0c696661801e` | 上游 archive 未附模型授權；僅本機評估 | sherpa-onnx 1.13.8 |
| `sherpa-onnx-moonshine-base-zh-quantized-2026-02-27` | Moonshine v2 quantized；整句辨識 | 99,905,420 | `6495d4240f66bcdd2bbbbfefaa687c463150d467854001270934e88940b733c1` | 140,751,336（encoder + decoder） | `c725a24b…` / `bf79fce6…` | Moonshine AI Community License；個人評估 | sherpa-onnx 1.13.8 |
| `sherpa-onnx-x-asr-zipformer-transducer-zh-en-punct-int8-2026-06-03` | 中英混合 Zipformer transducer INT8；整句辨識 | 136,396,739 | `5d02c36d7b44e886b7c8f0d8e051f8713acab96c264bb6ef9e718be39a6a2224` | 174,906,219（encoder + decoder + joiner） | `7f6aa620…` / `72f47405…` / `aedb7fa6…` | archive 未附模型專屬授權；僅本機評估 | sherpa-onnx 1.13.8 |

Small 與 Standard archive 由 sherpa-onnx 官方 `asr-models` release 取得，並在 2026-09-22 實際計算 SHA256。模型與 tokens 不進 Git；App 載入前逐檔驗證 SHA256，錯誤時禁止載入。

Moonshine archive 於 2026-09-24 下載至 `F:\VoiceAssistant\model-cache`，並計算上表的 archive、encoder、decoder、tokens 與 LICENSE SHA256；同目錄保留經校驗的 Standard archive。X-ASR archive 於 2026-09-25 下載並逐檔計算 SHA256。App 可直接把候選模型安裝在手機 App 私有儲存空間，並同時保留 Standard、Moonshine 與 X-ASR。當時測試手機（vivo X60）尚未連線，因此尚未完成 X-ASR 手機辨識速度、記憶體與準確度實測。

比較時對三個模型使用相同的真人語句與收音條件，包含中英混合、數字、「我要聽 BLACKPINK 的歌」及「BLACKPINK 是誰」，記錄逐字稿、CER／WER、final latency、RTF、記憶體及連續使用穩定度。Moonshine 與 X-ASR 在 App 中於句尾執行整句辨識，只交付 final transcript。模型是否優於 Standard 仍須由測試手機實測判定。

## vivo X60 result（歷史機型測試結果）

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

這一筆只證明該測試手機（vivo X60）上的 KWS → VAD → ASR 程式路徑可執行，不是 20 句準確率、P95、peak PSS、30 次穩定度或真人驗收。兩個模型的正式比較仍待驗收。

### Standard INT8 安裝與啟動短測（2026-09-24）

- 測試手機（vivo X60 `V2046A`）：以 `prepare-m2-model-packs.ps1 -Pack standard -InstallToDevice` 安裝模型到 App 私有資料夾，手機端 `sha256sum` 確認 `model.int8.onnx` 與 `tokens.txt` 分別符合上表的固定 SHA256；Small 模型仍保留，供日後切回。
- 更新版 App `1.0.0-rc2` / versionCode `6`，Debug APK SHA256 `A86288F4BF5C1CAC39C69D115D7ACF40325924AAD08C78D86851167A07C0F0EF`，以 `adb install -r` 覆蓋安裝，保留原 App 資料。`testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest` 通過。
- 在 App「設定 → 本機語音辨識」選用 Standard 並儲存。啟動後 Service dump 回報 `asr_model_id=sherpa-onnx-streaming-zipformer-ctc-zh-int8-2025-06-30`、`state=IDLE_KWS`、`audio_source=VOICE_RECOGNITION`、`audio_errors=0`、`dropped_frames=0`；後續 `audio_frames=1238`，表示麥克風持續收音。一次 `dumpsys meminfo` 快照的 total PSS 為 347,599 KB，僅供這次啟動參考。
- 尚未以真人語音比較辨識正確率、辨識延遲、長時間記憶體與耗電；本次只確認 Standard 模型完整、App 實際載入並進入待命。
