#!/usr/bin/env python3
"""Only supported publisher of new word/phrase cards into Android live feeds.

Source: GitHub issue "CARD: ..." on main -> card-inbox.yml -> this script
in live-content. Never write vocabulary snapshots: those are owned by Android.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import unicodedata

ROOT = Path(__file__).resolve().parent.parent
CONTENT = ROOT / "content"
PROFILES = {"papa", "mama", "dima"}
LANGUAGES = {"es", "en"}
TYPES = {"word", "phrase"}
MAX_REQUEST_ITEMS = 100
MAX_FEED_ITEMS = 5000
MAX_FEED_BYTES = 1024 * 1024


class InvalidCard(ValueError):
    pass


def norm(value):
    return " ".join(unicodedata.normalize("NFKC", value).casefold().split())


def nonempty(value, label, limit):
    if not isinstance(value, str) or not value.strip() or len(value.strip()) > limit:
        raise InvalidCard(f"{label} requires 1..{limit} characters")
    return value.strip()


def profiles_for(request):
    if "profile" in request and "profiles" in request:
        raise InvalidCard("Use either profile or profiles, never both")
    targets = request.get("profiles", request.get("profile"))
    if isinstance(targets, str):
        targets = [targets]
    if not isinstance(targets, list) or not targets or len(targets) > 3:
        raise InvalidCard("An explicit profile or profiles array is required")
    if len(set(targets)) != len(targets) or any(p not in PROFILES for p in targets):
        raise InvalidCard("profiles must be unique, chosen from papa/mama/dima")
    return targets


def validate_request(request):
    if not isinstance(request, dict):
        raise InvalidCard("Issue body must be ONE JSON object")
    allowed = {"profile", "profiles", "language", "items"}
    if set(request) - allowed:
        raise InvalidCard(f"Unknown top-level keys: {sorted(set(request)-allowed)}")
    profiles = profiles_for(request)
    language = request.get("language")
    if language not in LANGUAGES:
        raise InvalidCard("language must be explicitly es or en")
    items = request.get("items")
    if not isinstance(items, list) or not 1 <= len(items) <= MAX_REQUEST_ITEMS:
        raise InvalidCard("items must contain 1..100 cards")
    result = []
    keys = set()
    for i, raw in enumerate(items, 1):
        if not isinstance(raw, dict):
            raise InvalidCard(f"items[{i}] must be an object")
        if set(raw) - {"type", "spanish", "meaning", "category"}:
            raise InvalidCard(f"items[{i}] has unexpected fields")
        kind = raw.get("type")
        if kind not in TYPES:
            raise InvalidCard(f"items[{i}].type must be word or phrase")
        spanish = nonempty(raw.get("spanish"), f"items[{i}].spanish", 500)
        meaning = nonempty(raw.get("meaning"), f"items[{i}].meaning", 1000)
        category = raw.get("category", 0)
        if kind == "word":
            if type(category) is not int or category not in (1, 2, 3):
                raise InvalidCard(f"items[{i}].category required: 1 verb / 2 noun / 3 adjective")
        elif "category" in raw and category != 0:
            raise InvalidCard(f"items[{i}].category must be omitted for phrases")
        if norm(spanish) == "":
            raise InvalidCard(f"items[{i}].spanish is empty")
        key = (kind, norm(spanish))
        if key in keys:
            raise InvalidCard(f"Repeated card in the request: {spanish!r}")
        keys.add(key)
        record = {"type": kind, "spanish": spanish, "meaning": meaning}
        if kind == "word":
            record["category"] = category
        result.append(record)
    return profiles, language, result


def canonical_card_path(profile, language):
    # User data is NEVER allowed to choose an arbitrary path.
    if profile not in PROFILES or language not in LANGUAGES:
        raise InvalidCard("Invalid profile or language")
    return CONTENT / profile / f"cards-{language}.json"


def read_json(path):
    if not path.is_file():
        return None
    data = path.read_bytes()
    if len(data) > MAX_FEED_BYTES:
        raise InvalidCard(f"{path.relative_to(ROOT)} exceeds 1 MiB")
    try:
        return json.loads(data.decode("utf-8"))
    except (ValueError, UnicodeDecodeError) as exc:
        raise InvalidCard(f"{path.relative_to(ROOT)} is not valid UTF-8 JSON") from exc


def load_feed(path):
    root = read_json(path)
    if root is None:
        return {"version": 1, "items": []}
    if not isinstance(root, dict) or root.get("version") != 1 or not isinstance(root.get("items"), list):
        raise InvalidCard(f"Bad feed schema in {path.relative_to(ROOT)}")
    if len(root["items"]) > MAX_FEED_ITEMS:
        raise InvalidCard("More than 5000 cards")
    seen_ids = set()
    seen_words = set()
    for entry in root["items"]:
        if not isinstance(entry, dict) or entry.get("type") not in TYPES:
            raise InvalidCard("Invalid existing card type")
        card_id = nonempty(entry.get("id"), "existing id", 120)
        text = nonempty(entry.get("spanish"), "existing spanish", 500)
        nonempty(entry.get("meaning"), "existing meaning", 1000)
        if entry["type"] == "word" and type(entry.get("category")) is not int:
            raise InvalidCard("Existing word lacks category")
        key = (entry["type"], norm(text))
        if key in seen_words or card_id in seen_ids:
            raise InvalidCard("Existing feed has duplicated word/type or id")
        seen_words.add(key)
        seen_ids.add(card_id)
    return root


def archive_keys(profile, language):
    """Use the Android's actual full sync schema and fail on incomplete records."""
    path = CONTENT / profile / f"vocabulary-{language}.json"
    archive = read_json(path)
    if archive is None:
        raise InvalidCard("Missing full vocabulary archive: " + str(path.relative_to(ROOT)))
    if not isinstance(archive, dict) or archive.get("version") != 1 or archive.get("profile") != profile or archive.get("language") != language or not isinstance(archive.get("items"), list):
        raise InvalidCard("Malformed personal vocabulary archive: " + str(path.relative_to(ROOT)))
    if len(archive["items"]) > 20000:
        raise InvalidCard("Full vocabulary archive too large")
    keys, deleted = set(), set()
    for entry in archive["items"]:
        if not isinstance(entry, dict):
            raise InvalidCard("Malformed vocabulary record")
        spanish = nonempty(entry.get("spanish"), "archive spanish", 500)
        # Android VocabularyReconcile.serialize() writes "kind", never "type".
        kind = entry.get("kind")
        revision, origin, removed = entry.get("revision"), entry.get("origin"), entry.get("deleted")
        if kind not in TYPES or type(revision) is not int or revision < 0 or not isinstance(origin, str) or not 1 <= len(origin) <= 120 or type(removed) is not bool:
            raise InvalidCard("Incomplete canonical vocabulary record")
        if not removed:
            nonempty(entry.get("meaning"), "archive meaning", 1000)
            if type(entry.get("mastered")) is not bool:
                raise InvalidCard("Missing mastered flag in vocabulary")
        key = (kind, norm(spanish))
        if key in keys:
            raise InvalidCard("Duplicate canonical vocabulary entry")
        keys.add(key)
        if removed:
            deleted.add(key)
    return keys, deleted, len(archive["items"]) == 0


def stable_id(profile, language, card):
    identity = "\0".join((profile, language, card["type"], norm(card["spanish"])))
    return "card-" + hashlib.sha256(identity.encode("utf-8")).hexdigest()[:24]


def publish(request, paths_out=None):
    profiles, language, requested = validate_request(request)
    planned = []
    added = already = blocked = 0
    archive_unverified = []
    # All validation happens before any file is modified.
    for profile in profiles:
        dest = canonical_card_path(profile, language)
        feed = load_feed(dest)
        archive_present, archive_deleted, empty = archive_keys(profile, language)
        if empty:
            archive_unverified.append(profile)
        existing = {(x["type"], norm(x["spanish"])) for x in feed["items"]}
        ids = {x["id"] for x in feed["items"]}
        changed = False
        for card in requested:
            key = (card["type"], norm(card["spanish"]))
            # "deleted" tombstones are authoritative; never restore removed cards.
            if key in archive_deleted:
                blocked += 1
                continue
            if key in existing or key in archive_present:
                already += 1
                continue
            # A blank archive is not proof of absence: stop rather than silently
            # duplicating cards already learned on the phone.
            if empty:
                raise InvalidCard(
                    "ARCHIVE_NOT_READY: " + profile + "/" + language +
                    " has zero synchronized records. Connect the phone's full "
                    "vocabulary sync and retry; do not bypass CARD INBOX."
                )
            record = {"id": stable_id(profile, language, card), **card}
            if record["id"] in ids:
                raise InvalidCard("Stable ID collision")
            feed["items"].append(record)
            ids.add(record["id"])
            existing.add(key)
            added += 1
            changed = True
        if len(feed["items"]) > MAX_FEED_ITEMS:
            raise InvalidCard("More than 5000 cards in " + str(dest.relative_to(ROOT)))
        if changed:
            encoded = json.dumps(feed, ensure_ascii=False, indent=2) + "\n"
            if len(encoded.encode("utf-8")) > MAX_FEED_BYTES:
                raise InvalidCard("Feed exceeds 1 MiB: " + str(dest.relative_to(ROOT)))
            planned.append((dest, encoded))
    changed_paths = []
    for path, contents in planned:
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(".json.tmp")
        tmp.write_text(contents, encoding="utf-8")
        os.replace(tmp, path)
        changed_paths.append(str(path.relative_to(ROOT)))
    if paths_out:
        Path(paths_out).write_text("".join(p + "\n" for p in changed_paths), encoding="utf-8")
    result = f"PUBLISHED={added} EXISTING={already} DELETED_BLOCKED={blocked} TARGETS={','.join(profiles)}/{language}"
    if archive_unverified:
        result += " ARCHIVE_NOT_YET_VERIFIED=" + ",".join(archive_unverified)
    return result


def verify(request):
    """Verify the requested cards by rereading the currently published feed."""
    profiles, language, requested = validate_request(request)
    count = 0
    for profile in profiles:
        feed = load_feed(canonical_card_path(profile, language))
        existing = {(x["type"], norm(x["spanish"])) for x in feed["items"]}
        archived, deleted, _ = archive_keys(profile, language)
        for card in requested:
            key = (card["type"], norm(card["spanish"]))
            # Both an existing published card and a previously mastered/deleted
            # archive record count as correctly handled, not necessarily newly added.
            if key not in existing and key not in archived and key not in deleted:
                raise InvalidCard(
                    "READBACK_FAILED: " + profile + "/" + language +
                    " missing " + card["spanish"]
                )
            count += 1
    return f"VERIFIED={count} TARGETS={','.join(profiles)}/{language}"


def check():
    count = 0
    for profile in sorted(PROFILES):
        for language in sorted(LANGUAGES):
            path = canonical_card_path(profile, language)
            if path.exists():
                count += len(load_feed(path)["items"])
            archive_keys(profile, language)
    return f"VALID: six profile/language routes, {count} published cards"


def main():
    parser = argparse.ArgumentParser(description="Canonical card inbox")
    parser.add_argument("command", choices=["add", "check", "verify"])
    parser.add_argument("--paths-out")
    args = parser.parse_args()
    try:
        if args.command == "check":
            print(check())
        elif args.command == "verify":
            print(verify(json.load(sys.stdin)))
        else:
            print(publish(json.load(sys.stdin), paths_out=args.paths_out))
        return 0
    except (InvalidCard, ValueError, UnicodeDecodeError, OSError) as exc:
        print("CARD INBOX REJECTED:", exc, file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
