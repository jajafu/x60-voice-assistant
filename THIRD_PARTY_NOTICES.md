# Third-Party Notices

## sherpa-onnx 1.13.8

- Project: `k2-fsa/sherpa-onnx`
- Source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8
- Runtime license: Apache License 2.0
- Android AAR SHA256: `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`

## OkHttp 4.12.0

- Project: Square OkHttp
- Source: https://github.com/square/okhttp
- License: Apache License 2.0

## AndroidX / Jetpack Compose

- Source: https://android.googlesource.com/platform/frameworks/support/
- License: Apache License 2.0

## Model weights

The sherpa-onnx KWS archive referenced by this repository did not contain a model-specific `LICENSE` or `NOTICE` during the 2026-09-22 review. As of 2026-10-08, the [model publisher's ModelScope repository](https://www.modelscope.cn/models/pkufool/icefall-kws-zipformer-zh-en-3M-2025-12-20) for `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20` declares Apache License 2.0, and a sherpa-onnx maintainer confirmed that the ModelScope license metadata was updated to Apache 2.0 ([issue #3802](https://github.com/k2-fsa/sherpa-onnx/issues/3802#issuecomment-6052663364)).

The GitHub release archive still has no model-specific license file. As of 2026-10-19, the maintainer has not explicitly confirmed in the issue whether the ModelScope declaration covers the exact GitHub archive and every included artifact (`.onnx` weights, `tokens.txt`, and `en.phone`), or clarified training-data provenance and commercial redistribution scope. Do not treat the runtime's Apache-2.0 license as a substitute for this clarification. Other ASR archives require their own per-model license review as documented in `docs/model-packs.md`. Model binaries remain Git-ignored and excluded from source distribution; keep public redistribution of an APK containing the KWS model on hold pending confirmation of the release artifacts' applicable terms.
