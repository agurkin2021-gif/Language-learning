# Included third-party voice: Piper Daniela

This Android application optionally includes an offline synthesizer selectable as
"Piper Daniela — Аргентина" for Spanish study. The model is shipped in a quantized
INT8 format; using the voice does not require installing another app.

- Model: Piper es_AR-daniela-high, INT8 conversion distributed by sherpa-onnx.
- Original voice: https://huggingface.co/rhasspy/piper-voices/tree/main/es/es_AR/daniela/high
- Model card: https://huggingface.co/rhasspy/piper-voices/blob/main/es/es_AR/daniela/high/MODEL_CARD
- Original dataset: https://www.openslr.org/61/ (Attribution-ShareAlike 4.0 International).
- Sherpa ONNX native runtime: https://github.com/k2-fsa/sherpa-onnx
- Sherpa ONNX license: Apache-2.0.

See the individual upstream model, dataset, phonemizer and runtime licenses
before redistributing. The INT8 conversion is a quantization of the original voice,
not a newly trained or re-recorded voice.

Exact pinned upstream package digests are documented in
`scripts/prepare_embedded_daniela.py`.
