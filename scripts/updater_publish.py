#!/usr/bin/env python3
"""Sign and publish the dedicated updater APK."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile

from phone_publish import (
    GitHub, PublishError, Superseded, ensure_source_current, run_tool,
    signing_configuration, trusted_source, verify_signed
)

CHANNEL = "updater-builds"
PACKAGE = "ru.spanish.learning.updater"
APK_NAME = "SpanishLearningUpdater.apk"
FILES = (APK_NAME, "SHA256SUMS.txt", "build-info.json")


def set_output(key, value):
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"{key}={value}\n")


def report(status, message, artifact_ready=None):
    print(message)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
            output.write("\n## Updater для телефона\n\n" + message + "\n")
    set_output("status", status)
    if artifact_ready is not None:
        set_output("artifact_ready", str(artifact_ready).lower())


def read_channel(github):
    reference = github.request("/git/ref/heads/" + CHANNEL, missing_ok=True)
    if reference is None:
        return None
    head = reference["object"]["sha"]
    commit = github.request("/git/commits/" + head)
    tree = github.request("/git/trees/" + commit["tree"]["sha"])
    entries = {entry["path"]: entry for entry in tree["tree"]}
    if tree.get("truncated") or set(entries) != set(FILES):
        raise PublishError("Ветка updater-builds содержит неожиданные файлы")
    metadata = json.loads(github.blob(entries["build-info.json"]["sha"]))
    return {"head": head, "entries": entries, "metadata": metadata}




def sign_updater_apk(unsigned, destination, sdk, config):
    try:
        key_bytes = base64.b64decode(config["keystore_base64"], validate=True)
    except ValueError:
        raise PublishError("Некорректный keystore_base64") from None
    with tempfile.TemporaryDirectory(prefix="spanish-updater-signing-", dir=os.environ.get("RUNNER_TEMP")) as directory:
        keystore = Path(directory) / "updater-signing.p12"
        keystore.write_bytes(key_bytes)
        keystore.chmod(0o600)
        environment = os.environ.copy()
        environment["ANDROID_STORE_PASSWORD"] = config["store_password"]
        environment["ANDROID_KEY_PASSWORD"] = config["key_password"]
        run_tool([
            sdk / "apksigner", "sign",
            "--ks", keystore,
            "--ks-key-alias", config["key_alias"],
            "--ks-pass", "env:ANDROID_STORE_PASSWORD",
            "--key-pass", "env:ANDROID_KEY_PASSWORD",
            "--v1-signing-enabled", "false",
            "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "false",
            "--v4-signing-enabled", "false",
            "--out", destination,
            unsigned,
        ], environment)

def verify_package(apk, sdk, number):
    badging = run_tool([sdk / "aapt", "dump", "badging", apk])
    expected = (
        "package: name='" + PACKAGE + "' versionCode='" + str(1000 + number)
        + "' versionName='1.0." + str(number) + "'"
    )
    if expected not in badging or "application-debuggable" in badging:
        raise PublishError("Updater APK имеет неверный package/version либо является debug-сборкой")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--unsigned-apk", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    raw = os.environ.get("ANDROID_SIGNING_CONFIG", "")
    if not raw:
        report("waiting-for-secret", "Ожидает ANDROID_SIGNING_CONFIG.")
        return

    config = signing_configuration(raw)
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    revision = os.environ.get("BUILD_REVISION", "")
    number_text = os.environ.get("GITHUB_RUN_NUMBER", "")
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    token = os.environ.get("GITHUB_TOKEN", "")
    if (not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository)
            or not re.fullmatch(r"[a-f0-9]{40}", revision)
            or not re.fullmatch(r"[0-9]{1,8}", number_text)
            or not run_id.isdigit() or not token):
        raise PublishError("Некорректные метаданные запуска")

    branch = trusted_source(repository, revision)
    number = int(number_text)
    certificate = Path("config/phone-signing.sha256").read_text(encoding="ascii").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", certificate):
        raise PublishError("Некорректный SHA-256 сертификата")

    expected = {
        "repository": repository,
        "application_id": PACKAGE,
        "source_revision": revision,
        "run_id": run_id,
        "run_number": number,
        "version_code": 1000 + number,
        "version_name": "1.0." + number_text,
        "certificate_sha256": certificate,
        "channel": CHANNEL,
    }

    github = GitHub(repository, token)
    ensure_source_current(github, branch, revision)
    channel = read_channel(github)
    if channel:
        info = channel["metadata"]
        if (info.get("application_id") != PACKAGE
                or info.get("certificate_sha256") != certificate
                or info.get("channel") != CHANNEL):
            raise PublishError("Опубликованный канал Updater несовместим")
        if int(info.get("version_code", -1)) > expected["version_code"]:
            raise Superseded("Уже опубликован более новый Updater.")

    sdk = Path(os.environ.get("ANDROID_HOME", "")) / "build-tools" / "35.0.0"
    verify_package(args.unsigned_apk, sdk, number)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    apk = args.output_dir / APK_NAME
    sign_updater_apk(args.unsigned_apk, apk, sdk, config)
    verification = run_tool([sdk / "apksigner", "verify", "--verbose", "--print-certs", apk])
    safe_lines = [
        line for line in verification.splitlines()
        if line.startswith("Signer #") and "certificate SHA-256 digest:" in line
        or line.startswith("Verified using v2 scheme")
    ]
    print("\n".join(safe_lines))
    verify_signed(apk, args.unsigned_apk, sdk, certificate)

    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    (args.output_dir / "SHA256SUMS.txt").write_text(digest + "  " + APK_NAME + "\n", encoding="ascii")
    (args.output_dir / "build-info.json").write_text(
        json.dumps({**expected, "apk_sha256": digest}, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    set_output("artifact_ready", "true")

    tree_entries = []
    for name in FILES:
        blob = github.request(
            "/git/blobs",
            method="POST",
            data={
                "content": base64.b64encode((args.output_dir / name).read_bytes()).decode("ascii"),
                "encoding": "base64",
            },
        )
        tree_entries.append({"path": name, "mode": "100644", "type": "blob", "sha": blob["sha"]})

    tree = github.request("/git/trees", method="POST", data={"tree": tree_entries})
    previous = channel["head"] if channel else None
    commit = github.request(
        "/git/commits",
        method="POST",
        data={
            "tree": tree["sha"],
            "parents": [previous] if previous else [],
            "message": "Updater build " + number_text + ": " + revision,
        },
    )
    ensure_source_current(github, branch, revision)
    current = read_channel(github)
    if (current["head"] if current else None) != previous:
        raise Superseded("Канал Updater изменился во время публикации.")

    if previous:
        github.request("/git/refs/heads/" + CHANNEL, method="PATCH",
                       data={"sha": commit["sha"], "force": False})
    else:
        github.request("/git/refs", method="POST",
                       data={"ref": "refs/heads/" + CHANNEL, "sha": commit["sha"]})

    report(
        "published",
        "Опубликован Updater " + expected["version_name"] + ".\n\n"
        + "[Updater APK](https://github.com/" + repository
        + "/raw/refs/heads/" + CHANNEL + "/" + APK_NAME + ")",
        True,
    )


if __name__ == "__main__":
    try:
        main()
    except Superseded as error:
        report("superseded", str(error))
    except PublishError as error:
        report("failed", str(error))
        raise SystemExit(1)
    except Exception:
        report("failed", "Публикация Updater остановлена из-за непредвиденной ошибки.")
        raise SystemExit(1)
