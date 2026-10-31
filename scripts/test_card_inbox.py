#!/usr/bin/env python3
"""Regression tests for the profile-safe CARD inbox. No network or GitHub token."""
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

import card_inbox as ci


class CardInboxTests(unittest.TestCase):
    def setUp(self):
        self.sandbox = TemporaryDirectory()
        self.original_root, self.original_content = ci.ROOT, ci.CONTENT
        ci.ROOT = Path(self.sandbox.name)
        ci.CONTENT = ci.ROOT / "content"
        for profile in ci.PROFILES:
            for lang in ci.LANGUAGES:
                path = ci.CONTENT / profile / f"vocabulary-{lang}.json"
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps({
                    "version": 1, "profile": profile, "language": lang,
                    "items": [self.record("word", "base-" + profile + "-" + lang)]
                }, ensure_ascii=False), encoding="utf-8")

    @staticmethod
    def record(kind="phrase", spanish="No lo sabía", deleted=False, mastered=False):
        item = {
            "kind": kind, "spanish": spanish, "revision": 1,
            "origin": "test-device", "deleted": deleted,
        }
        if not deleted:
            item.update({"meaning": "тест", "category": 0, "mastered": mastered})
        return item

    def tearDown(self):
        ci.ROOT, ci.CONTENT = self.original_root, self.original_content
        self.sandbox.cleanup()

    def request(self, profile="mama", language="es", text="No lo sabía"):
        return {
            "profile": profile, "language": language,
            "items": [{"type": "phrase", "spanish": text, "meaning": "Я этого не знала"}],
        }

    def items(self, profile, language="es"):
        path = ci.canonical_card_path(profile, language)
        if not path.exists():
            return []
        return json.loads(path.read_text(encoding="utf-8"))["items"]

    def test_only_selected_profile_is_written(self):
        result = ci.publish(self.request("mama"))
        self.assertIn("PUBLISHED=1", result)
        self.assertEqual(len(self.items("mama")), 1)
        self.assertFalse(ci.canonical_card_path("papa", "es").exists())
        self.assertFalse(ci.canonical_card_path("dima", "es").exists())

    def test_multi_profile_same_issue(self):
        req = self.request()
        req.pop("profile")
        req["profiles"] = ["papa", "mama"]
        result = ci.publish(req)
        self.assertIn("PUBLISHED=2", result)
        self.assertEqual(len(self.items("papa")), 1)
        self.assertEqual(len(self.items("mama")), 1)
        self.assertFalse(ci.canonical_card_path("dima", "es").exists())

    def test_replay_and_normalized_repeat_are_idempotent(self):
        ci.publish(self.request())
        req = self.request(text="  NO  LO    SABÍA ")
        result = ci.publish(req)
        self.assertIn("EXISTING=1", result)
        self.assertEqual(len(self.items("mama")), 1)

    def test_deleted_archive_card_is_never_restored(self):
        path = ci.CONTENT / "mama" / "vocabulary-es.json"
        contents = json.loads(path.read_text(encoding="utf-8"))
        contents["items"] = [self.record(deleted=True)]
        path.write_text(json.dumps(contents, ensure_ascii=False), encoding="utf-8")
        self.assertIn("DELETED_BLOCKED=1", ci.publish(self.request()))
        self.assertEqual(self.items("mama"), [])

    def test_mastered_archive_card_is_not_reimported(self):
        path = ci.CONTENT / "mama" / "vocabulary-es.json"
        contents = json.loads(path.read_text(encoding="utf-8"))
        contents["items"] = [self.record(mastered=True)]
        path.write_text(json.dumps(contents, ensure_ascii=False), encoding="utf-8")
        self.assertIn("EXISTING=1", ci.publish(self.request()))
        self.assertEqual(self.items("mama"), [])

    def test_rejects_missing_profile_and_bad_route(self):
        request = self.request()
        request.pop("profile")
        with self.assertRaises(ci.InvalidCard):
            ci.publish(request)
        with self.assertRaises(ci.InvalidCard):
            ci.publish(self.request(profile="../../main"))
        with self.assertRaises(ci.InvalidCard):
            ci.publish(self.request(language="ru"))

    def test_rejects_double_profile_and_duplicate_batch(self):
        request = self.request()
        request["profiles"] = ["mama"]
        with self.assertRaises(ci.InvalidCard):
            ci.publish(request)
        request = self.request()
        request["items"].append({"type": "phrase", "spanish": "NO LO SABÍA", "meaning": "То же"})
        with self.assertRaises(ci.InvalidCard):
            ci.publish(request)

    def test_word_requires_category(self):
        request = self.request()
        request["items"] = [{"type": "word", "spanish": "casa", "meaning": "дом"}]
        with self.assertRaises(ci.InvalidCard):
            ci.publish(request)
        request["items"][0]["category"] = 2
        self.assertIn("PUBLISHED=1", ci.publish(request))
        self.assertEqual(self.items("mama")[0]["category"], 2)

    def test_empty_archive_fails_closed_for_unseen_card(self):
        path = ci.CONTENT / "mama" / "vocabulary-es.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        doc["items"] = []
        path.write_text(json.dumps(doc), encoding="utf-8")
        with self.assertRaisesRegex(ci.InvalidCard, "ARCHIVE_NOT_READY"):
            ci.publish(self.request())
        self.assertEqual(self.items("mama"), [])

    def test_empty_archive_allows_existing_feed_idempotency(self):
        ci.publish(self.request())
        path = ci.CONTENT / "mama" / "vocabulary-es.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        doc["items"] = []
        path.write_text(json.dumps(doc), encoding="utf-8")
        self.assertIn("EXISTING=1", ci.publish(self.request()))
        self.assertEqual(len(self.items("mama")), 1)

    def test_invalid_archive_never_publishes(self):
        path = ci.CONTENT / "mama" / "vocabulary-es.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        doc["items"] = [{"kind": "phrase", "spanish": "Sin firma", "deleted": False}]
        path.write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
        with self.assertRaisesRegex(ci.InvalidCard, "Incomplete canonical"):
            ci.publish(self.request())
        self.assertEqual(self.items("mama"), [])

    def test_failed_multi_profile_request_is_atomic(self):
        request = self.request()
        request.pop("profile")
        request["profiles"] = ["papa", "mama"]
        path = ci.CONTENT / "mama" / "vocabulary-es.json"
        document = json.loads(path.read_text(encoding="utf-8"))
        document["items"] = []
        path.write_text(json.dumps(document), encoding="utf-8")
        with self.assertRaisesRegex(ci.InvalidCard, "ARCHIVE_NOT_READY"):
            ci.publish(request)
        self.assertEqual(self.items("papa"), [])
        self.assertEqual(self.items("mama"), [])

    def test_verify_remote_readback(self):
        request = self.request()
        with self.assertRaisesRegex(ci.InvalidCard, "READBACK_FAILED"):
            ci.verify(request)
        ci.publish(request)
        self.assertIn("VERIFIED=1", ci.verify(request))

    def test_separate_english_and_spanish(self):
        ci.publish(self.request(language="en"))
        self.assertEqual(len(self.items("mama", "en")), 1)
        self.assertEqual(self.items("mama", "es"), [])
        self.assertEqual(self.items("papa", "en"), [])

    def test_all_six_routes_validate(self):
        ci.publish(self.request())
        self.assertIn("six profile/language routes", ci.check())


if __name__ == "__main__":
    unittest.main()
