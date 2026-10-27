#!/usr/bin/env python3
import unittest
from official_chatgpt_buffer import prepare, receive, next_audit, audit, export

class NoApiBufferTests(unittest.TestCase):
    def sample(self):
        return prepare({"language": "es", "task": "Рассказ о коте",
                        "cards": [{"term": "tenés", "meaning": "ты имеешь",
                                   "mastered": True, "phrase": False}]})
    def story(self):
        return {"format":"study_story_v1", "language":"es",
                "title":"Un gato", "body":"Tengo un gato. El gato duerme.",
                "translation":"У меня есть кот. Кот спит."}

    def test_prepares_context_no_api_and_correct_locale(self):
        state=self.sample()
        prompt=state["prompt"]
        self.assertIn("tenés", prompt)
        self.assertIn("voseo", prompt)
        self.assertIn("study_story_v1", prompt)
        self.assertEqual(state["status"], "await_story")

    def test_separate_three_step_audits_are_mandatory(self):
        state=receive(self.sample(),self.story())
        with self.assertRaisesRegex(ValueError,"непроверенный"):
            export(state)
        for i,term in enumerate(("ДОСЛОВНО","ГРАММАТИКУ","СЕМАНТИКУ"),1):
            prompt=next_audit(state)
            self.assertIn(term,prompt)
            state=audit(state,{"stage":i,"pass":True,"issues":[],
                "verified_translation":self.story()["translation"]})
            self.assertEqual(len(state["audits"]),i)
        self.assertEqual(state["status"],"verified")
        self.assertEqual(export(state),self.story())

    def test_audit_fail_prevents_export(self):
        state=receive(self.sample(),self.story())
        state=audit(state,{"stage":1,"pass":False,"issues":["Неточный перевод"],
            "verified_translation":self.story()["translation"]})
        self.assertEqual(state["status"],"rejected")
        with self.assertRaises(ValueError):
            export(state)

    def test_cannot_fake_audit_from_first_story_response(self):
        invalid={**self.story(),"audits":[{"stage":1,"pass":True},
                                          {"stage":2,"pass":True},{"stage":3,"pass":True}]}
        state=receive(self.sample(),invalid)
        self.assertEqual(len(state["audits"]),0)
        with self.assertRaises(ValueError):
            export(state)

    def test_rewritten_translation_is_not_silently_substituted(self):
        state=receive(self.sample(),self.story())
        with self.assertRaisesRegex(ValueError,"изменил перевод"):
            audit(state,{"stage":1,"pass":True,"issues":[],
                         "verified_translation":"У меня была собака. Собака спит."})

    def test_out_of_order_audit_rejected(self):
        state=receive(self.sample(),self.story())
        with self.assertRaises(ValueError):
            audit(state,{"stage":3,"pass":True,"issues":[],
                "verified_translation":self.story()["translation"]})

    def test_story_sentence_mismatch_rejected(self):
        wrong=self.story()
        wrong["translation"]="У меня есть кот."
        with self.assertRaisesRegex(ValueError,"предложений"):
            receive(self.sample(),wrong)

if __name__=="__main__":
    unittest.main()
