#!/usr/bin/env python3
import json
import os
import unittest
from unittest.mock import patch
from chatgpt_broker import (
    generate, checked_translation, validate_story, json_object, openai_text
)

class StudyBrokerTests(unittest.TestCase):
    def test_story_requires_exactly_three_distinct_successful_audits(self):
        story = {"format": "study_story_v1", "language": "es",
                 "title": "Los amigos",
                 "body": "Mi gato camina. Después duerme.",
                 "translation": "Мой кот гуляет. Потом спит."}
        stages = []
        calls = []
        def fake_call(instructions, data):
            calls.append(instructions)
            if len(calls) == 1:
                return json.dumps(story, ensure_ascii=False)
            return json.dumps({"pass": True, "issues": [],
                               "verified_translation": story["translation"]},
                              ensure_ascii=False)
        answer = generate({"language": "es", "kind": "story",
                           "task": "Про двух друзей", "cards": []},
                          call=fake_call, progress=stages.append)
        self.assertEqual(len(calls), 4)
        self.assertEqual([v["stage"] for v in answer["audits"]], [1, 2, 3])
        self.assertIn("LITERAL", calls[1])
        self.assertIn("GRAMMAR", calls[2])
        self.assertIn("SEMANTICS", calls[3])
        self.assertEqual(stages[-3:], ["Аудит перевода 1/3",
                                        "Аудит перевода 2/3",
                                        "Аудит перевода 3/3"])

    def test_third_stage_veto_prevents_success(self):
        calls = []
        def fake_call(instructions, data):
            calls.append(instructions)
            return json.dumps({"pass": len(calls) != 3,
                               "issues": ["неверный смысл"] if len(calls) == 3 else [],
                               "verified_translation": "Привет."}, ensure_ascii=False)
        with self.assertRaisesRegex(ValueError, "3/3"):
            checked_translation("Hola.", "Привет.", "es", fake_call, lambda x: None)

    def test_audit_rewrite_is_rejected(self):
        def fake(instructions, data):
            return json.dumps({"pass": True, "verified_translation": "Неправильно."})
        with self.assertRaisesRegex(ValueError, "изменил перевод"):
            checked_translation("Hola.", "Привет.", "es", fake, lambda x: None)

    def test_audit_rejects_different_sentence_counts(self):
        with self.assertRaisesRegex(ValueError, "предложений"):
            checked_translation("Uno. Dos.", "Один.", "es",
                                lambda a, b: "{}", lambda a: None)

    def test_single_word_exact_form_and_three_audits(self):
        responses = [
            json.dumps({"word": "tenés", "translation": "ты имеешь"}),
            json.dumps({"pass": True, "verified_translation": "ты имеешь"}),
            json.dumps({"pass": True, "verified_translation": "ты имеешь"}),
            json.dumps({"pass": True, "verified_translation": "ты имеешь"})
        ]
        def fake(*args):
            return responses.pop(0)
        answer = generate({"language": "es", "kind": "word", "word": "tenés",
                           "task": "tenés", "cards": []}, call=fake)
        self.assertEqual(answer["word"], "tenés")
        self.assertEqual(len(answer["audits"]), 3)

    def test_no_key_means_no_paid_request(self):
        with patch.dict(os.environ, {"OPENAI_API_KEY": ""}):
            with self.assertRaisesRegex(RuntimeError, "не настроен"):
                openai_text("instructions", "input")

    def test_story_malformed_rejected(self):
        with self.assertRaises(ValueError):
            validate_story({"format":"study_story_v1", "language":"es",
                            "title":"x", "body":"Uno. Dos.",
                            "translation":"Один."}, "es")

    def test_extract_json_from_fenced_response(self):
        obj = json_object("```json\n{\"pass\":true}\n```")
        self.assertTrue(obj["pass"])

if __name__ == "__main__":
    unittest.main()
