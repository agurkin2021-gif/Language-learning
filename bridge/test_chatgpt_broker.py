#!/usr/bin/env python3
import json
import os
import time
import threading
import urllib.error
import urllib.request
import chatgpt_broker
from http.server import ThreadingHTTPServer
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

    def test_complete_http_round_trip_no_paid_api(self):
        """Full POST -> poll -> 3 audits -> story, entirely on loopback."""
        token = "fake-integration-token-000000000"
        story = {"format": "study_story_v1", "language": "es",
                 "title": "Mis amigos", "body": "Dos amigos juegan. Luego descansan.",
                 "translation": "Два друга играют. Потом отдыхают."}
        call_count = []
        def fake_model(instructions, payload):
            call_count.append(instructions)
            if len(call_count) == 1:
                return json.dumps(story, ensure_ascii=False)
            return json.dumps({"pass": True, "issues": [],
                "verified_translation": story["translation"]}, ensure_ascii=False)
        original = chatgpt_broker.generate
        def offline_generate(request, progress):
            return original(request, call=fake_model, progress=progress)
        server = ThreadingHTTPServer(("127.0.0.1", 0), chatgpt_broker.Handler)
        process = threading.Thread(target=server.serve_forever, daemon=True)
        process.start()
        root = f"http://127.0.0.1:{server.server_port}"
        try:
            with patch.dict(os.environ, {"STUDY_BROKER_TOKEN": token,
                                        "OPENAI_API_KEY": "fake-do-not-call"}), \\
                 patch.object(chatgpt_broker, "generate", offline_generate):
                unauthenticated = urllib.request.Request(root + "/v1/jobs",
                    data=b"{}", headers={"Content-Type": "application/json"},
                    method="POST")
                with self.assertRaises(urllib.error.HTTPError) as denied:
                    urllib.request.urlopen(unauthenticated, timeout=3)
                self.assertEqual(denied.exception.code, 401)

                request = {"language": "es", "kind": "story",
                           "task": "Рассказ об играющих друзьях",
                           "cards": [{"term": "juegan", "meaning": "они играют",
                                      "mastered": True, "phrase": False}]}
                post = urllib.request.Request(root + "/v1/jobs",
                    data=json.dumps(request, ensure_ascii=False).encode(),
                    headers={"Authorization": "Bearer " + token,
                             "Content-Type": "application/json"}, method="POST")
                with urllib.request.urlopen(post, timeout=3) as response:
                    self.assertEqual(response.status, 202)
                    job_id = json.load(response)["job_id"]
                final = None
                for _ in range(40):
                    poll = urllib.request.Request(root + "/v1/jobs/" + job_id,
                        headers={"Authorization": "Bearer " + token})
                    with urllib.request.urlopen(poll, timeout=3) as response:
                        final = json.load(response)
                    if final["status"] != "running":
                        break
                    time.sleep(0.05)
                self.assertEqual(final["status"], "done")
                self.assertEqual(final["result"]["language"], "es")
                self.assertEqual(len(final["result"]["audits"]), 3)
                self.assertEqual(len(call_count), 4)
        finally:
            server.shutdown()
            server.server_close()
            process.join(timeout=3)

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
