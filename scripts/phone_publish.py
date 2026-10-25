#!/usr/bin/env python3
"""Sign the tested APK and publish three files to the private phone-builds branch."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.error
import urllib.request
import zipfile

CHANNEL = "phone-builds"
APK_NAME = "SpanishLearning.apk"
FILES = (APK_NAME, "SHA256SUMS.txt", "build-info.json")


class PublishError(Exception):
    """Fixed, non-secret diagnostic messages only."""


class Superseded(Exception):
    pass


class GitHub:
    def __init__(self, repository, token):
        self.base = "https://api.github.com/repos/" + repository
        self.token = token

    def request(self, route, *, method="GET", data=None, missing_ok=False):
        headers = {"Authorization": "Bearer " + self.token, "Accept": "application/vnd.github+json",
                   "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "SpanishLearning-phone-publish"}
        payload = None if data is None else json.dumps(data).encode("utf-8")
        if payload is not None:
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(self.base + route, data=payload, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code == 404 and missing_ok:
                return None
            if error.code in (409, 422) and method in ("POST", "PATCH") and route.startswith("/git/refs"):
                raise Superseded("Ветка для APK изменилась во время публикации. Чужой коммит не перезаписан.") from None
            raise PublishError("GitHub API вернул HTTP " + str(error.code)) from None
        except urllib.error.URLError:
            raise PublishError("Не удалось связаться с GitHub") from None

    def blob(self, sha):
        blob = self.request("/git/blobs/" + sha)
        if blob.get("encoding") != "base64":
            raise PublishError("Не удалось прочитать файл ветки APK")
        return base64.b64decode(blob["content"])


def set_output(key, value):
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(key + "=" + value + "\n")


def report(status, message, artifact_ready=None):
    print(message)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
            output.write("\n## APK для телефона\n\n" + message + "\n")
    set_output("status", status)
    if artifact_ready is not None:
        set_output("artifact_ready", str(artifact_ready).lower())


def signing_configuration(raw):
    try:
        config = json.loads(raw)
    except json.JSONDecodeError:
        raise PublishError("ANDROID_SIGNING_CONFIG должен содержать JSON") from None
    fields = ("keystore_base64", "store_password", "key_alias", "key_password")
    if not isinstance(config, dict) or any(not isinstance(config.get(key), str) or not config[key] for key in fields):
        raise PublishError("В ANDROID_SIGNING_CONFIG отсутствует обязательное поле")
    for key in fields:
        value = config[key].replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        print("::add-mask::" + value, flush=True)
    return config


def trusted_source(repository, revision):
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    if os.environ.get("GITHUB_EVENT_NAME") == "pull_request":
        head = event.get("pull_request", {}).get("head", {})
        if (head.get("repo", {}).get("full_name") == repository
                and head.get("ref") == "codex/android-v1" and head.get("sha") == revision):
            return "codex/android-v1"
    elif (os.environ.get("GITHUB_EVENT_NAME") == "push"
          and os.environ.get("GITHUB_REF") == "refs/heads/main" and event.get("after") == revision):
        return "main"
    raise PublishError("Публикация разрешена только из доверенной ветки этого репозитория")


def ensure_source_current(github, branch, revision):
    current = github.request("/git/ref/heads/" + branch)
    if current["object"]["sha"] != revision:
        raise Superseded("Исходная ветка уже изменилась. Устаревшая сборка не публикуется.")


def read_channel(github):
    reference = github.request("/git/ref/heads/" + CHANNEL, missing_ok=True)
    if reference is None:
        return None
    head = reference["object"]["sha"]
    commit = github.request("/git/commits/" + head)
    tree = github.request("/git/trees/" + commit["tree"]["sha"])
    entries = {entry["path"]: entry for entry in tree["tree"]}
    if tree.get("truncated") or set(entries) != set(FILES) or any(
            entry["type"] != "blob" or entry["mode"] != "100644" for entry in entries.values()):
        raise PublishError("Ветка phone-builds содержит неожиданные файлы; они не изменены")
    metadata = json.loads(github.blob(entries["build-info.json"]["sha"]))
    return {"head": head, "entries": entries, "metadata": metadata}


def check_channel(channel, expected):
    if channel is None:
        return
    info = channel["metadata"]
    if (not isinstance(info, dict) or type(info.get("run_number")) is not int
            or type(info.get("version_code")) is not int or info["version_code"] != 100 + info["run_number"]
            or any(info.get(key) != expected[key] for key in ("repository", "application_id", "certificate_sha256", "channel"))):
        raise PublishError("Метаданные опубликованного APK несовместимы с постоянным каналом обновлений")
    if info["version_code"] > expected["version_code"]:
        raise Superseded("Уже опубликован более высокий versionCode. Эта сборка не заменяет его.")
    if info["version_code"] == expected["version_code"] and any(info.get(key) != value for key, value in expected.items()):
        raise PublishError("Этот versionCode уже занят другой сборкой; опубликованный APK не изменён")


def run_tool(arguments, environment=None):
    result = subprocess.run([str(value) for value in arguments], env=environment,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    if result.returncode:
        # Keystore details may occur in stderr; never print tool output on failure.
        raise PublishError("Не прошла операция Android SDK: " + Path(str(arguments[0])).name)
    return result.stdout.decode("utf-8", errors="replace")


def verify_package(apk, sdk, number):
    badging = run_tool([sdk / "aapt", "dump", "badging", apk])
    expected = ("package: name='ru.spanish.learning' versionCode='" + str(100 + number)
                + "' versionName='0.1." + str(number) + "-test'")
    if expected not in badging or "application-debuggable" in badging:
        raise PublishError("APK имеет неверную версию/package либо является debug-сборкой")


def verify_signed(apk, unsigned, sdk, certificate):
    verification = run_tool([sdk / "apksigner", "verify", "--verbose", "--print-certs", apk])
    digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", verification)
    if len(digests) != 1 or digests[0].lower() != certificate or not re.search(r"Verified using v2 scheme .*: true", verification):
        raise PublishError("Не подтверждены постоянный сертификат и подпись APK v2")
    run_tool([sdk / "zipalign", "-c", "4", apk])
    # v2/v3 signatures live outside ZIP entries. Reuse must preserve the tested payload.
    def entries(path):
        with zipfile.ZipFile(path) as archive:
            if len(archive.namelist()) != len(set(archive.namelist())):
                raise PublishError("APK содержит повторяющиеся ZIP-записи")
            return {name: hashlib.sha256(archive.read(name)).digest() for name in archive.namelist()}
    if entries(apk) != entries(unsigned):
        raise PublishError("Содержимое подписанного APK отличается от проверенной сборки этого запуска")


def sign_apk(unsigned, destination, sdk, config):
    try:
        key_bytes = base64.b64decode(config["keystore_base64"], validate=True)
    except ValueError:
        raise PublishError("Некорректный keystore_base64") from None
    with tempfile.TemporaryDirectory(prefix="spanish-signing-", dir=os.environ.get("RUNNER_TEMP")) as directory:
        keystore = Path(directory) / "phone-signing.p12"
        keystore.write_bytes(key_bytes)
        keystore.chmod(0o600)
        environment = os.environ.copy()
        environment["ANDROID_STORE_PASSWORD"] = config["store_password"]
        environment["ANDROID_KEY_PASSWORD"] = config["key_password"]
        run_tool([sdk / "apksigner", "sign", "--ks", keystore, "--ks-key-alias", config["key_alias"],
                  "--ks-pass", "env:ANDROID_STORE_PASSWORD", "--key-pass", "env:ANDROID_KEY_PASSWORD",
                  "--v1-signing-enabled", "false", "--v2-signing-enabled", "true", "--v4-signing-enabled", "false",
                  "--out", destination, unsigned], environment)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--unsigned-apk", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    raw = os.environ.get("ANDROID_SIGNING_CONFIG", "")
    if not raw:
        report("waiting-for-secret", "Ожидает добавления секрета ANDROID_SIGNING_CONFIG. Новый ключ и APK для телефона не создаются.")
        return
    config = signing_configuration(raw)
    repository, revision = os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("BUILD_REVISION", "")
    number_text, run_id = os.environ.get("GITHUB_RUN_NUMBER", ""), os.environ.get("GITHUB_RUN_ID", "")
    token = os.environ.get("GITHUB_TOKEN", "")
    if (not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) or not re.fullmatch(r"[a-f0-9]{40}", revision)
            or not re.fullmatch(r"[0-9]{1,8}", number_text) or not run_id.isdigit() or not token):
        raise PublishError("Некорректные метаданные запуска GitHub Actions")
    branch = trusted_source(repository, revision)
    certificate = Path("config/phone-signing.sha256").read_text(encoding="ascii").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", certificate):
        raise PublishError("Некорректный публичный SHA-256 сертификата проекта")
    number = int(number_text)
    expected = {"repository": repository, "application_id": "ru.spanish.learning", "source_revision": revision,
                "run_id": run_id, "run_number": number, "version_code": 100 + number,
                "version_name": "0.1." + number_text + "-test", "certificate_sha256": certificate, "channel": CHANNEL}
    github = GitHub(repository, token)
    ensure_source_current(github, branch, revision)
    channel = read_channel(github)
    check_channel(channel, expected)
    sdk = Path(os.environ.get("ANDROID_HOME", "")) / "build-tools" / "35.0.0"
    verify_package(args.unsigned_apk, sdk, number)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    apk = args.output_dir / APK_NAME
    if channel and channel["metadata"]["version_code"] == expected["version_code"]:
        for name in FILES:
            (args.output_dir / name).write_bytes(github.blob(channel["entries"][name]["sha"]))
        digest = hashlib.sha256(apk.read_bytes()).hexdigest()
        if (channel["metadata"].get("apk_sha256") != digest
                or (args.output_dir / "SHA256SUMS.txt").read_bytes() != (digest + "  " + APK_NAME + "\n").encode()):
            raise PublishError("Контрольная сумма ранее опубликованного APK не совпала")
        verify_signed(apk, args.unsigned_apk, sdk, certificate)
        report("already-published", "Эта сборка уже находится в phone-builds. Подпись и содержимое проверены; файлы не изменены.", True)
        return
    sign_apk(args.unsigned_apk, apk, sdk, config)
    verify_signed(apk, args.unsigned_apk, sdk, certificate)
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    (args.output_dir / "SHA256SUMS.txt").write_text(digest + "  " + APK_NAME + "\n", encoding="ascii")
    (args.output_dir / "build-info.json").write_text(json.dumps({**expected, "apk_sha256": digest}, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    set_output("artifact_ready", "true")
    tree_entries = []
    for name in FILES:
        blob = github.request("/git/blobs", method="POST",
                              data={"content": base64.b64encode((args.output_dir / name).read_bytes()).decode("ascii"), "encoding": "base64"})
        tree_entries.append({"path": name, "mode": "100644", "type": "blob", "sha": blob["sha"]})
    # No base_tree: the publication branch contains exactly these three files, no workflows or keys.
    tree = github.request("/git/trees", method="POST", data={"tree": tree_entries})
    previous = channel["head"] if channel else None
    commit = github.request("/git/commits", method="POST", data={"tree": tree["sha"],
        "parents": [previous] if previous else [], "message": "Phone build " + number_text + ": " + revision})
    ensure_source_current(github, branch, revision)
    current = read_channel(github)
    check_channel(current, expected)
    if (current["head"] if current else None) != previous:
        raise Superseded("Ветка для APK обновилась во время подготовки. Опубликованные файлы не перезаписаны.")
    if previous:
        github.request("/git/refs/heads/" + CHANNEL, method="PATCH", data={"sha": commit["sha"], "force": False})
    else:
        github.request("/git/refs", method="POST", data={"ref": "refs/heads/" + CHANNEL, "sha": commit["sha"]})
    report("published", "Опубликована версия " + expected["version_name"] + " в приватной ветке phone-builds. Постоянная подпись и SHA-256 проверены.\n\n"
           + "[APK для телефона](https://github.com/" + repository + "/raw/refs/heads/phone-builds/" + APK_NAME + ")", True)


if __name__ == "__main__":
    try:
        main()
    except Superseded as error:
        report("superseded", str(error))
    except PublishError as error:
        report("failed", str(error))
        raise SystemExit(1)
    except Exception:
        report("failed", "Публикация APK остановлена из-за непредвиденной ошибки; секреты не выведены.")
        raise SystemExit(1)
