#!/usr/bin/env python3
"""ChatGPT study broker: Android -> this broker -> official OpenAI API -> Android.

No ChatGPT cookies, subscription sharing, browser automation, or hidden bypass.
Requires an explicitly provisioned, separately billed OPENAI_API_KEY.
No API request is issued when credentials are missing.
Use behind HTTPS reverse proxy; bind to loopback by default.
"""
from __future__ import annotations
import hmac
import json
import os
import re
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

MAX_REQUEST_BYTES = 120_000
MAX_STORIES = 150
JOB_TTL = 3600
jobs: dict[str, dict] = {}
jobs_lock = threading.Lock()
MAX_RUNNING = threading.BoundedSemaphore(2)

def json_object(raw: str) -> dict:
    value = (raw or "").strip()
    if value.startswith("```"):
        value = re.sub(r"^```(?:json)?\s*|\s*```$", "", value).strip()
    try:
        data = json.loads(value)
    except ValueError:
        start, end = value.find("{"), value.rfind("}")
        if start < 0 or end <= start:
            raise ValueError("Модель вернула не JSON")
        data = json.loads(value[start:end + 1])
    if not isinstance(data, dict):
        raise ValueError("Ожидается JSON-объект")
    return data

def sentences(text: str) -> list[str]:
    return [part.strip() for part in re.split(r"(?<=[.!?])\s+", text.strip()) if part.strip()]

def validate_story(data: dict, language: str) -> dict:
    if data.get("format") != "study_story_v1" or data.get("language") != language:
        raise ValueError("Неверный формат или язык")
    for key, limit in (("title", 250), ("body", 12000), ("translation", 12000)):
        value = data.get(key)
        if not isinstance(value, str) or not value.strip() or len(value) > limit:
            raise ValueError("Недопустимое поле: " + key)
    if len(sentences(data["body"])) != len(sentences(data["translation"])):
        raise ValueError("Число предложений оригинала и перевода не совпадает")
    return data

def openai_text(instructions: str, user_input: str) -> str:
    key = os.environ.get("OPENAI_API_KEY", "").strip()
    if not key:
        raise RuntimeError("OpenAI API не настроен; запрос не отправлен")
    payload = {
        "model": os.environ.get("OPENAI_MODEL", "gpt-4.1"),
        "instructions": instructions,
        "input": user_input,
        "max_output_tokens": 1800,
    }
    request = Request("https://api.openai.com/v1/responses",
                      data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                      headers={"Authorization": "Bearer " + key,
                               "Content-Type": "application/json"},
                      method="POST")
    try:
        with urlopen(request, timeout=100) as reply:
            data = json.load(reply)
    except HTTPError as exc:
        # Never expose secrets or raw provider payloads to Android.
        raise RuntimeError("OpenAI API: HTTP " + str(exc.code)) from None
    except URLError:
        raise RuntimeError("Нет соединения с OpenAI API") from None
    if isinstance(data.get("output_text"), str):
        return data["output_text"]
    chunks = []
    for item in data.get("output", []):
        if item.get("type") == "message":
            for piece in item.get("content", []):
                if piece.get("type") == "output_text":
                    chunks.append(piece.get("text", ""))
    if not chunks:
        raise RuntimeError("ChatGPT не вернул текст")
    return "\n".join(chunks)

def input_cards(value) -> list[dict]:
    if not isinstance(value, list):
        raise ValueError("Неверный словарь")
    result = []
    for card in value[:5000]:
        if not isinstance(card, dict):
            continue
        term = str(card.get("term", "")).strip()[:120]
        meaning = str(card.get("meaning", "")).strip()[:160]
        if term and meaning:
            result.append({"term": term, "meaning": meaning,
                           "mastered": bool(card.get("mastered", False)),
                           "phrase": bool(card.get("phrase", False))})
    return result

def audit_spec(stage: int, language: str) -> str:
    intro = "Independent professional translator audit. Language=" + language + ". "
    conditions = {
        1: ("LITERAL ACCURACY: check exact meanings, word forms, false friends, "
            "literal-first translations and idiomatic additions in parentheses. "
            "Any inaccurate word or invented meaning MUST fail."),
        2: ("GRAMMAR: check tense, person, pronouns, negation, agreement, "
            "prepositions and regional grammar. For Spanish enforce Argentinian "
            "Rioplatense voseo when used; compare Russian grammar and meaning."),
        3: ("SEMANTICS: independently cross-check every sentence against the "
            "source and context; catch omissions, paraphrase distortion, "
            "inaccurate idioms and unexplained wording."),
    }
    return intro + conditions[stage] + (
        " Return ONLY JSON: {\"pass\":true or false,\"issues\":[\"...\"],"
        "\"verified_translation\":\"full unchanged translation\"}. "
        "Do not rubber-stamp. If any doubt relevant to study accuracy, pass=false. "
        "Never invent a better translation without setting pass=false.")

def checked_translation(source: str, translation: str, language: str,
                        call, stage_callback) -> list[dict]:
    if len(sentences(source)) != len(sentences(translation)):
        raise ValueError("Разное число предложений")
    checks = []
    for stage in (1, 2, 3):
        stage_callback("Аудит перевода " + str(stage) + "/3")
        raw = call(audit_spec(stage, language),
                   json.dumps({"source": source, "translation": translation},
                              ensure_ascii=False))
        result = json_object(raw)
        if result.get("pass") is not True:
            raise ValueError("Аудит " + str(stage) + "/3 отклонил перевод: " +
                             ", ".join(map(str, result.get("issues", [])))[:350])
        if result.get("verified_translation") != translation:
            raise ValueError("Аудит " + str(stage) + "/3 изменил перевод")
        checks.append({"stage": stage, "passed": True})
    return checks

def generate(req: dict, call=openai_text, progress=lambda stage: None) -> dict:
    language = req.get("language")
    if language not in ("es", "en"):
        raise ValueError("Поддерживаются только es и en")
    task = req.get("task", "")
    if not isinstance(task, str) or not task.strip() or len(task) > 2000:
        raise ValueError("Напишите задание не длиннее 2000 символов")
    kind = req.get("kind", "story")
    if kind not in ("story", "word", "chat"):
        raise ValueError("Неизвестный тип запроса")
    cards = input_cards(req.get("cards", []))
    prior = sorted(cards, key=lambda x: (not x["mastered"], not x["phrase"]))
    # Keep context bounded while preserving frequent mastered expressions.
    snapshot = json.dumps(prior[:180], ensure_ascii=False)

    if kind == "chat":
        progress("ChatGPT отвечает с учётом контекста приложения")
        history = req.get("history", [])
        if not isinstance(history, list):
            raise ValueError("История диалога должна быть списком")
        cleaned = []
        for turn in history[-12:]:
            if isinstance(turn, dict) and turn.get("role") in ("user", "assistant"):
                text = turn.get("text", "")
                if isinstance(text, str) and 0 < len(text) <= 3000:
                    cleaned.append({"role": turn["role"], "text": text})
        instructions = (
            "You are an exacting Argentinian Spanish or English language tutor "
            "for a Russian learner. For Spanish use Rioplatense voseo; treat "
            "the student's transcriptions as literal input, never silently "
            "substitute a similarly sounding word. Use the supplied current "
            "personal dictionary as reference. Reply in Russian. Do not claim "
            "to have added a word or story unless the Android app confirms it. "
            "If giving translations, prefer the exact literal grammatical form "
            "and add an idiomatic meaning only in parentheses. "
            "Do not invent vocabulary or translations."
        )
        answer = call(instructions, json.dumps({
            "language": language, "task": task, "history": cleaned,
            "learner_vocabulary": prior[:180]}, ensure_ascii=False))
        if not isinstance(answer, str) or not answer.strip() or len(answer) > 20000:
            raise ValueError("Ответ ChatGPT пустой или слишком длинный")
        return {"status": "ok", "kind": "chat",
                "language": language, "text": answer.strip(), "audits": []}

    if kind == "story":
        progress("ChatGPT создаёт рассказ")
        instructions = (
            "You are an expert " + ("Argentinian Spanish (voseo)" if language == "es" else "English") +
            " language teacher for Russian learners. Use learner vocabulary. "
            "Write a coherent short story and sentence-aligned accurate Russian translation. "
            "Make it natural; never fabricate a translation. Output ONLY JSON: "
            "{\"format\":\"study_story_v1\",\"language\":\"" + language +
            "\",\"title\":\"...\",\"body\":\"...\",\"translation\":\"...\"}. "
            "Use exactly 4 to 7 original sentences and matching translated sentences.")
        data = validate_story(json_object(call(instructions, json.dumps(
            {"task": task, "learner_vocabulary": snapshot}, ensure_ascii=False))), language)
        audits = checked_translation(data["body"], data["translation"],
                                     language, call, progress)
        return {"status": "ok", "kind": "story", "language": language,
                "title": data["title"], "body": data["body"],
                "translation": data["translation"], "audits": audits}

    word = req.get("word", "")
    if not isinstance(word, str) or not word.strip() or len(word) > 120:
        raise ValueError("Введите слово или выражение")
    progress("ChatGPT переводит точную словоформу")
    intro = ("Translate the EXACT input word form into Russian for a serious language "
             "learner. Literal accurate form first. Natural meaning in parentheses "
             "if needed. Spanish must be Rioplatense when relevant. Return only JSON: "
             "{\"word\":\"original\",\"translation\":\"accurate translation\"}.")
    data = json_object(call(intro, json.dumps({"language": language, "word": word,
                    "context": task, "learner_vocabulary": snapshot}, ensure_ascii=False)))
    translated = str(data.get("translation", "")).strip()
    if data.get("word") != word or not translated or len(translated) > 600:
        raise ValueError("ChatGPT изменил исходное слово или не перевёл его")
    audits = checked_translation(word, translated, language, call, progress)
    return {"status": "ok", "kind": "word", "language": language, "word": word,
            "translation": translated, "audits": audits}

def job_run(job_id: str, request: dict):
    def progress(stage: str):
        with jobs_lock:
            if job_id in jobs:
                jobs[job_id]["stage"] = stage
    try:
        with MAX_RUNNING:
            answer = generate(request, progress=progress)
        with jobs_lock:
            jobs[job_id].update(status="done", stage="Готово", result=answer)
    except Exception as exc:
        with jobs_lock:
            jobs[job_id].update(status="error", stage="Ошибка",
                                error=str(exc)[:450])
    finally:
        with jobs_lock:
            jobs[job_id]["updated"] = time.time()

def cleanup():
    with jobs_lock:
        for job_id, value in list(jobs.items()):
            if value.get("status") != "running" and time.time() - value["updated"] > JOB_TTL:
                del jobs[job_id]

class Handler(BaseHTTPRequestHandler):
    server_version = "StudyBroker/1"
    def log_message(self, format, *args):
        # Never log learner words, task texts, API tokens, or responses.
        pass
    def reply(self, code, body):
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)
    def authorized(self):
        expected = os.environ.get("STUDY_BROKER_TOKEN", "")
        provided = self.headers.get("Authorization", "")
        return bool(expected) and hmac.compare_digest(provided, "Bearer " + expected)
    def do_POST(self):
        if not self.authorized():
            return self.reply(401, {"error": "Нет доступа"})
        if self.path != "/v1/jobs":
            return self.reply(404, {"error": "Путь не найден"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= MAX_REQUEST_BYTES:
                return self.reply(413, {"error": "Задание слишком большое"})
            request = json.loads(self.rfile.read(length).decode("utf-8"))
            if not isinstance(request, dict):
                raise ValueError("Ожидается JSON")
            if not os.environ.get("OPENAI_API_KEY"):
                return self.reply(503, {"error": "OpenAI API не настроен; платных запросов нет"})
            # Validate inputs before accepting the asynchronous job.
            if request.get("language") not in ("en", "es"):
                raise ValueError("Неподдерживаемый язык")
            if request.get("kind", "story") not in ("story", "word", "chat"):
                raise ValueError("Неподдерживаемый тип")
            input_cards(request.get("cards", []))
            cleanup()
            job_id = secrets.token_urlsafe(24)
            with jobs_lock:
                jobs[job_id] = {"status": "running", "stage": "В очереди",
                                "updated": time.time()}
            threading.Thread(target=job_run, args=(job_id, request), daemon=True).start()
            return self.reply(202, {"job_id": job_id})
        except (ValueError, UnicodeDecodeError) as exc:
            return self.reply(400, {"error": str(exc)[:150]})
    def do_GET(self):
        if self.path == "/health":
            return self.reply(200, {"status": "ok", "api_configured":
                                     bool(os.environ.get("OPENAI_API_KEY"))})
        if not self.authorized():
            return self.reply(401, {"error": "Нет доступа"})
        job_id = self.path.removeprefix("/v1/jobs/")
        if not self.path.startswith("/v1/jobs/") or not re.fullmatch(r"[\w-]{20,60}", job_id):
            return self.reply(404, {"error": "Путь не найден"})
        cleanup()
        with jobs_lock:
            value = jobs.get(job_id)
            if value is None:
                return self.reply(404, {"error": "Задание уже не существует"})
            data = {k: v for k, v in value.items() if k != "updated"}
        return self.reply(200, data)

def main():
    if not os.environ.get("STUDY_BROKER_TOKEN"):
        raise SystemExit("Настройте STUDY_BROKER_TOKEN (отдельный случайный секрет)")
    host = os.environ.get("STUDY_BROKER_BIND", "127.0.0.1")
    port = int(os.environ.get("STUDY_BROKER_PORT", "8765"))
    print("Study broker listening on", host, port, "(HTTP: use an HTTPS reverse proxy)")
    ThreadingHTTPServer((host, port), Handler).serve_forever()

if __name__ == "__main__":
    main()
