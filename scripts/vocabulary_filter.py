#!/usr/bin/env python3
"""Fail-closed filter for proposed language cards using every private phone snapshot.

Usage:
  VOCABULARY_GITHUB_TOKEN=... python3 scripts/vocabulary_filter.py \
    --profile papa --language es --candidates proposals.json --output approved.json

Never commit snapshots, tokens, or personal progress to the public repository.
"""
import argparse
import base64
import json
import os
import re
import sys
import unicodedata
import urllib.error
import urllib.parse
import urllib.request


def normalized(text):
    return " ".join(unicodedata.normalize("NFC", text).casefold().split())


def github_json(path, token):
    request = urllib.request.Request(
        "https://api.github.com" + path,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": "Bearer " + token,
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "SpanishLearning-private-vocabulary-preflight",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as exc:
        raise ValueError("GitHub access failed (HTTP %s)" % exc.code) from None


def load_full_archive(repo, profile, language, token):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
        raise ValueError("Invalid repository")
    base = "/repos/" + repo
    info = github_json(base, token)
    if not info.get("private") or info.get("full_name", "").lower() != repo.lower():
        raise ValueError("Refusing to read a public or unexpected repository")
    branch = info.get("default_branch")
    if not branch:
        raise ValueError("Private repository has no default branch")
    tree = github_json(base + "/git/trees/" + urllib.parse.quote(branch, safe="") + "?recursive=1", token)
    if tree.get("truncated"):
        raise ValueError("Repository tree is truncated; cannot verify full vocabulary")
    folder = "vocabulary/" if profile == "papa" else "vocabulary/" + profile + "/"
    pattern = re.compile(r"^" + re.escape(folder) + re.escape(language) + r"-[^/]+\.json$")
    paths = [entry["path"] for entry in tree.get("tree", [])
             if entry.get("type") == "blob" and pattern.fullmatch(entry.get("path", ""))]
    if not paths:
        raise ValueError("No private vocabulary snapshots for the requested profile and language")

    known = set()
    count = 0
    for path in sorted(paths):
        encoded = github_json(base + "/contents/" +
                              urllib.parse.quote(path, safe="/") + "?ref=" +
                              urllib.parse.quote(branch, safe=""), token)
        if encoded.get("type") != "file" or encoded.get("encoding") != "base64":
            raise ValueError("Invalid private vocabulary file")
        snapshot = json.loads(base64.b64decode(encoded["content"]))
        if snapshot.get("schema") != 1 or snapshot.get("language") != language:
            raise ValueError("Snapshot schema or language mismatch")
        if snapshot.get("profile", "papa") != profile:
            raise ValueError("Snapshot profile mismatch")
        entries = snapshot.get("entries")
        if not isinstance(entries, list):
            raise ValueError("Incomplete private vocabulary snapshot")
        for entry in entries:
            if not isinstance(entry, dict) or not isinstance(entry.get("text"), str):
                raise ValueError("Invalid vocabulary entry")
            value = normalized(entry["text"])
            if not value:
                raise ValueError("Empty vocabulary entry")
            # Both mastered and still-learning cards block duplicate insertion.
            known.add(value)
            count += 1
    if not count:
        raise ValueError("Private archive is empty; no complete vocabulary verification")
    return known, len(paths)


def filter_cards(candidates, known):
    if candidates.get("version") != 1 or not isinstance(candidates.get("items"), list):
        raise ValueError("Invalid candidate feed")
    approved = []
    seen = set()
    for card in candidates["items"]:
        if not isinstance(card, dict) or card.get("type") not in ("word", "phrase"):
            raise ValueError("Invalid candidate")
        if not all(isinstance(card.get(key), str) and card[key].strip()
                   for key in ("id", "spanish", "meaning")):
            raise ValueError("Candidate requires id, spanish, meaning")
        key = normalized(card["spanish"])
        if key not in known and key not in seen:
            approved.append(card)
            seen.add(key)
    return {"version": 1, "items": approved}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="agurkin2021-gif/Language-learning-private")
    parser.add_argument("--profile", required=True, choices=("papa", "mama", "dima"))
    parser.add_argument("--language", required=True, choices=("es", "en"))
    parser.add_argument("--candidates", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    token = os.environ.get("VOCABULARY_GITHUB_TOKEN", "")
    if not token:
        parser.error("VOCABULARY_GITHUB_TOKEN must be set; no output written")
    try:
        known, sources = load_full_archive(args.repo, args.profile, args.language, token)
        with open(args.candidates, encoding="utf-8") as source:
            candidates = json.load(source)
        approved = filter_cards(candidates, known)
        # Never touch the output until every private snapshot has been verified.
        if os.path.abspath(args.output) == os.path.abspath(args.candidates):
            raise ValueError("Output must differ from input")
        with open(args.output, "x", encoding="utf-8") as target:
            json.dump(approved, target, ensure_ascii=False, indent=2)
            target.write("\n")
        print("Verified %d private snapshots; proposed %d; approved %d; duplicates skipped %d"
              % (sources, len(candidates["items"]), len(approved["items"]),
                 len(candidates["items"]) - len(approved["items"])), file=sys.stderr)
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print("Preflight FAILED: " + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
