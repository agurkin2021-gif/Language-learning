#!/usr/bin/env python3
"""SINGLE entrypoint for publishing family stories; GitHub branch: live-content.

One and only destination: content/stories.json on this branch.
Usage:
    python3 scripts/story_inbox.py add < submission.json
    python3 scripts/story_inbox.py add --replace < correction.json
    python3 scripts/story_inbox.py check
Input is one UTF-8 JSON object with profile, language, title, spanish, russian.\nA correction may add "replace": true; the existing imported ID will be kept.
Do not write to old per-profile story files or content/live_content.json.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
FEED = ROOT / "content" / "stories.json"
PROFILES = {"papa", "mama", "dima"}
LANGUAGES = {"es", "en"}
MAX_ITEMS = 500
MAX_BYTES = 1024 * 1024


class InvalidStory(ValueError):
    pass


def spoken_chunks(text: str) -> list[str]:
    # Match the Android Sentences.split() rule, including its 240-char TTS chunks.
    chunks = []
    for line in re.split(r"(?<=[.!?…])\s+|[\r\n]+", text.strip()):
        rest = line.strip()
        while len(rest) > 240:
            cut = rest.rfind(" ", 0, 241)
            if cut < 1:
                cut = 240
            chunks.append(rest[:cut].strip())
            rest = rest[cut:].strip()
        if rest:
            chunks.append(rest)
    return chunks


def validated(value: dict, *, make_id: bool = False) -> dict:
    if not isinstance(value, dict):
        raise InvalidStory("Input must be a JSON object")
    if value.get("type", "story") != "story":
        raise InvalidStory("Only type=story is allowed")
    profile = value.get("profile")
    language = value.get("language")
    if profile not in PROFILES or language not in LANGUAGES:
        raise InvalidStory("profile must be papa/mama/dima, language must be es/en")
    fields = {"title": 300, "spanish": 20000, "russian": 20000}
    data = {}
    for key, limit in fields.items():
        raw = value.get(key)
        if not isinstance(raw, str) or not raw.strip() or len(raw.strip()) > limit:
            raise InvalidStory(f"{key} must be nonempty text of at most {limit} characters")
        data[key] = raw.strip()
    es_chunks, ru_chunks = spoken_chunks(data["spanish"]), spoken_chunks(data["russian"])
    if not es_chunks or len(es_chunks) != len(ru_chunks):
        raise InvalidStory(
            f"Android TTS alignment error: {len(es_chunks)} text chunks, "
            f"{len(ru_chunks)} Russian chunks; align sentences/lines first"
        )
    identity = "\0".join((profile, language, data["title"].casefold()))
    stable_id = "story-" + hashlib.sha256(identity.encode("utf-8")).hexdigest()[:20]
    story_id = value.get("id", stable_id if make_id else None)
    if not isinstance(story_id, str) or not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9_-]{0,119}", story_id):
        raise InvalidStory("id must be a stable 1..120 character ASCII identifier")
    return {
        "id": story_id, "type": "story", "profile": profile, "language": language,
        "title": data["title"], "spanish": data["spanish"], "russian": data["russian"],
    }


def load_feed() -> dict:
    if not FEED.is_file():
        raise InvalidStory("Canonical content/stories.json is missing")
    raw = FEED.read_bytes()
    if len(raw) > MAX_BYTES:
        raise InvalidStory("Feed exceeds 1 MiB")
    try:
        root = json.loads(raw.decode("utf-8"))
    except (ValueError, UnicodeDecodeError) as exc:
        raise InvalidStory("Feed must contain valid UTF-8 JSON") from exc
    if not isinstance(root, dict) or root.get("version") != 1 or not isinstance(root.get("items"), list):
        raise InvalidStory("Feed must be {version:1, items:[...]}")
    if len(root["items"]) > MAX_ITEMS:
        raise InvalidStory("Feed exceeds 500 stories")
    ids = set()
    titles = set()
    normalized = []
    for entry in root["items"]:
        item = validated(entry)
        key = (item["profile"], item["language"], item["id"])
        title_key = (item["profile"], item["language"], item["title"].casefold())
        if key in ids or title_key in titles:
            raise InvalidStory(f"Duplicate story id/title: {key}")
        ids.add(key)
        titles.add(title_key)
        normalized.append(item)
    return {"version": 1, "items": normalized}


def publish(incoming: dict, *, replace: bool = False) -> str:
    root = load_feed()
    item = validated(incoming, make_id=True)
    entries = root["items"]
    duplicate = next((i for i, old in enumerate(entries)
        if old["profile"] == item["profile"] and old["language"] == item["language"]
        and (old["id"] == item["id"] or old["title"].casefold() == item["title"].casefold())), None)
    if duplicate is not None:
        previous = entries[duplicate]
        if not replace:
            if previous == item:
                return f"ALREADY_PUBLISHED {previous['id']} (no changes)"
            raise InvalidStory("Story already exists. To correct it, use add --replace")
        # Keep the same imported ID: corrected text must not create a second story.
        item["id"] = previous["id"]
        entries[duplicate] = item
        action = "REPLACED"
    else:
        if replace:
            raise InvalidStory("--replace requested but story does not exist")
        entries.append(item)
        action = "PUBLISHED"
    if len(entries) > MAX_ITEMS:
        raise InvalidStory("Maximum 500 stories reached")
    new_text = json.dumps(root, ensure_ascii=False, indent=2) + "\n"
    if len(new_text.encode("utf-8")) > MAX_BYTES:
        raise InvalidStory("Maximum feed size of 1 MiB reached")
    # Atomic update; failed validation never rewrites the published file.
    temp = FEED.with_suffix(".json.tmp")
    temp.write_text(new_text, encoding="utf-8")
    os.replace(temp, FEED)
    return f"{action} {item['profile']}/{item['language']} {item['id']} (path: content/stories.json)"


def main() -> int:
    parser = argparse.ArgumentParser(description="One and only story publishing entrypoint")
    parser.add_argument("command", choices=["add", "check"])
    parser.add_argument("--replace", action="store_true", help="Correct an existing story with its existing ID")
    args = parser.parse_args()
    try:
        if args.command == "check":
            root = load_feed()
            print(f"VALID {FEED.relative_to(ROOT)}: {len(root['items'])} stories")
        else:
            request = json.load(sys.stdin)
            if not isinstance(request, dict):
                raise InvalidStory("Input must be one JSON object")
            correction = request.pop("replace", False)
            if not isinstance(correction, bool):
                raise InvalidStory("replace must be a boolean")
            print(publish(request, replace=args.replace or correction))
        return 0
    except (InvalidStory, ValueError, UnicodeDecodeError) as exc:
        print(f"INVALID STORY: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
