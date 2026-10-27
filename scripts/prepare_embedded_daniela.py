#!/usr/bin/env python3
"""Bundle the pinned Piper Daniela voice and sherpa-onnx runtime into Android build inputs.

These large third-party binaries are deliberately not committed into Git.
GitHub Actions fetches them once from verified upstream releases and packages
them inside the signed app APK; users do not need a separate TTS installation.
"""
import hashlib
import os
from pathlib import Path
import shutil
import tarfile
import tempfile
import time
from urllib.request import Request, urlopen
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent.parent
AAR = ROOT / "app/libs/sherpa-onnx.aar"
ASSETS = ROOT / "app/src/main/assets/embedded-daniela"
MODEL_URL = ("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
             "vits-piper-es_AR-daniela-high-int8.tar.bz2")
MODEL_SHA256 = "7218f0a119e4c16533ac187f71ab3019f2092f1594e43fef8392ae1f5b64abab"
AAR_URL = ("https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/"
           "sherpa-onnx-static-link-onnxruntime-1.13.8.aar")
AAR_SHA256 = "b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471"


def verified_download(url, expected, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists() and hashlib.sha256(destination.read_bytes()).hexdigest() == expected:
        return
    temp = destination.with_suffix(destination.suffix + ".part")
    for attempt in range(3):
        digest = hashlib.sha256()
        try:
            with urlopen(Request(url, headers={"User-Agent": "SpanishLearning-CI/1.0"}), timeout=120) as response:
                with temp.open("wb") as output:
                    while chunk := response.read(1024 * 1024):
                        output.write(chunk)
                        digest.update(chunk)
            if digest.hexdigest() != expected:
                raise ValueError(f"SHA-256 mismatch for {url}: {digest.hexdigest()}")
            temp.replace(destination)
            return
        except Exception:
            temp.unlink(missing_ok=True)
            if attempt == 2:
                raise
            time.sleep(3 * (attempt + 1))


def prepare():
    with tempfile.TemporaryDirectory(prefix="piper-daniela-") as tempname:
        archive = Path(tempname) / "daniela.tar.bz2"
        verified_download(AAR_URL, AAR_SHA256, AAR)
        with ZipFile(AAR) as bundle:
            names = bundle.namelist()
            assert "classes.jar" in names, "sherpa-onnx AAR missing Kotlin/Java API"
            assert any("arm64-v8a/" in n and "sherpa-onnx-jni" in n for n in names), "ARM64 JNI unavailable"
            assert any("x86_64/" in n and "sherpa-onnx-jni" in n for n in names), "emulator JNI unavailable"
        verified_download(MODEL_URL, MODEL_SHA256, archive)
        extracted = Path(tempname) / "extracted"
        extracted.mkdir()
        with tarfile.open(archive, "r:bz2") as tar:
            tar.extractall(extracted, filter="data")
        models = list(extracted.rglob("*.onnx"))
        assert len(models) == 1, f"Expected one Piper Daniela model, found {models}"
        source = models[0].parent
        tokens = list(extracted.rglob("tokens.txt"))
        espeak = [d for d in extracted.rglob("espeak-ng-data") if d.is_dir()]
        assert len(tokens) == 1 and len(espeak) == 1, "Piper phonetic resources missing"
        ASSETS.mkdir(parents=True, exist_ok=True)
        shutil.copy2(models[0], ASSETS / "daniela.onnx")
        shutil.copy2(tokens[0], ASSETS / "tokens.txt")
        shutil.copytree(espeak[0], ASSETS / "espeak-ng-data", dirs_exist_ok=True)
        assert (ASSETS / "daniela.onnx").stat().st_size > 10_000_000
        assert (ASSETS / "tokens.txt").stat().st_size > 100
        assert any((ASSETS / "espeak-ng-data").rglob("*")), "Empty phonetic dictionary"
        print(f"Bundled Piper Daniela INT8: model={(ASSETS / 'daniela.onnx').stat().st_size} bytes;"
              f" Android runtime={AAR.stat().st_size} bytes")


if __name__ == "__main__":
    prepare()
