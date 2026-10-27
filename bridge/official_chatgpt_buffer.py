#!/usr/bin/env python3
"""No-API intermediary for the official consumer ChatGPT UI.

A *transfer/validation* intermediary, not an unofficial ChatGPT client.
ChatGPT itself must still be opened, sent the prompt, and return its replies
through official user-initiated copy/share actions. No silent model calls.

CLI:
  prepare --request request.json --state /path/to/job.json
  receive --state job.json --reply chatgpt_story.json
  next-audit --state job.json
  audit --state job.json --reply audit_1.json   (repeat for stages 1,2,3)
  export --state job.json --output verified_story.json
  status --state job.json

The state file never contains credentials. The three audits are genuinely
separate *messages*, not three flags in the first ChatGPT response.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import tempfile

MAX_BYTES = 120_000
STORY_FORMAT = "study_story_v1"
AUDIT_NAMES = ("literal", "grammar", "semantic")

def read_json(path: Path) -> dict:
    if path.stat().st_size > MAX_BYTES:
        raise ValueError("Файл слишком большой")
    raw = path.read_text(encoding="utf-8-sig").strip()
    if raw.startswith("```"):
        first_newline = raw.find("\\n")
        last_fence = raw.rfind("```")
        if first_newline < 0 or last_fence <= first_newline:
            raise ValueError("Неполный JSON-ответ из ChatGPT")
        raw = raw[first_newline + 1:last_fence].strip()
    parsed = json.loads(raw)
    if not isinstance(parsed, dict):
        raise ValueError("Ожидается JSON-объект")
    return parsed

def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8",
                                     dir=path.parent, prefix=".buffer-",
                                     delete=False) as handle:
        os.chmod(handle.name, 0o600)
        json.dump(data, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
        temporary = handle.name
    try:
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)

def no_linebreaks(value: object, limit: int) -> str:
    if not isinstance(value, str):
        raise ValueError("Ожидается текст")
    clean = " ".join(value.strip().split())
    if not clean or len(clean) > limit:
        raise ValueError("Пустой или слишком длинный текст")
    return clean

def chunks(text: str) -> list[str]:
    return [v.strip() for v in re.split(r"(?<=[.!?…])\s+", text.strip()) if v.strip()]

def prepare(request: dict) -> dict:
    language = request.get("language")
    if language not in ("es", "en"):
        raise ValueError("Язык должен быть es или en")
    task = no_linebreaks(request.get("task"), 1500)
    words = request.get("cards", [])
    if not isinstance(words, list):
        raise ValueError("cards должен быть списком")
    cards = []
    for card in words[:200]:
        if not isinstance(card, dict):
            continue
        source = card.get("term")
        meaning = card.get("meaning")
        try:
            source = no_linebreaks(source, 120)
            meaning = no_linebreaks(meaning, 180)
        except ValueError:
            continue
        cards.append({"term": source, "meaning": meaning,
                      "mastered": bool(card.get("mastered", False)),
                      "phrase": bool(card.get("phrase", False))})
    job_id = secrets.token_hex(16)
    context = {"language": language, "cards": cards,
               "task": task, "job_id": job_id}
    intro = (
        "Ты преподаватель иностранных языков. Это учебный материал, не творческий "
        "вольный перевод. Уважай исходное написание и не подменяй слова созвучными. "
        "Проверяй дословный смысл, грамматику и соответствие русского перевода. "
        "Испанский: именно аргентинский rioplatense с voseo. Английский: EN. "
        "Сгенерируй рассказ по теме с живыми грамматически корректными фразами, "
        "ориентируйся на известный словарь. Русский перевод должен точно "
        "соответствовать оригиналу, предложение к предложению. "
        "Ответь ТОЛЬКО JSON формата study_story_v1 с полями format, language, "
        "title, body, translation. НЕ утверждай, что сделал три независимых "
        "аудита: я пришлю отдельные задания аудита после этого ответа."
    )
    prompt = intro + "\n\nДанные ученика:\n" + json.dumps(context, ensure_ascii=False)
    return {"protocol": 1, "job_id": job_id, "language": language,
            "task": task, "cards": cards, "story": None, "audits": [],
            "status": "await_story", "prompt": prompt}

def normalize_story(reply: dict, language: str) -> dict:
    if reply.get("format") != STORY_FORMAT or reply.get("language") != language:
        raise ValueError("Неверный формат рассказа или язык")
    story = {"format": STORY_FORMAT, "language": language}
    for key, maxlen in (("title", 250), ("body", 16000), ("translation", 16000)):
        text = reply.get(key)
        if not isinstance(text, str) or not text.strip() or len(text) > maxlen:
            raise ValueError("Некорректное поле: " + key)
        story[key] = text.strip()
    if len(chunks(story["body"])) < 2:
        raise ValueError("Для аудита нужен рассказ из нескольких предложений")
    if len(chunks(story["body"])) != len(chunks(story["translation"])):
        raise ValueError("Количество предложений не совпадает")
    return story

def receive(state: dict, reply: dict) -> dict:
    if state["status"] != "await_story":
        raise ValueError("Сейчас не ожидается рассказ")
    state["story"] = normalize_story(reply, state["language"])
    state["status"] = "await_audit_1"
    state.pop("prompt", None)
    return state

def next_audit(state: dict) -> str:
    stage = len(state["audits"]) + 1
    if state["status"] != f"await_audit_{stage}" or stage > 3:
        raise ValueError("Нет следующего этапа аудита")
    descriptions = (
        "Проверь ДОСЛОВНО каждый перевод и исходную словоформу. Не допускай "
        "добавленных значений, пропусков, приблизительных синонимов и подмен.",
        "Независимо проверь ГРАММАТИКУ: времена, лицо, число, падежи в переводе, "
        "местоимения, отрицания, предлоги, voseo (Аргентина).",
        "Независимо проверь СЕМАНТИКУ, контекст и устойчивые выражения, "
        "сопоставь каждое предложение оригинала и русского текста. "
        "Проверь, нет ли смысловых искажений."
    )
    task = descriptions[stage-1]
    return (
        f"Это САМОСТОЯТЕЛЬНЫЙ аудит №{stage}/3. {task} "
        "Если есть хотя бы одна ошибка или сомнение, верни pass=false. "
        "Ничего молча не исправляй. "
        "Ответь ТОЛЬКО JSON {"
        f'"stage":{stage},"pass":true,"issues":[],"verified_translation":'
        '"точно тот же русский текст, без единого изменения"}. '
        "Проверь следующий исходный материал и его перевод:\n" +
        json.dumps(state["story"], ensure_ascii=False)
    )

def audit(state: dict, reply: dict) -> dict:
    stage = len(state["audits"]) + 1
    if stage > 3 or state["status"] != f"await_audit_{stage}":
        raise ValueError("Аудит не ожидается или предыдущий этап не завершён")
    if type(reply.get("pass")) is not bool or reply.get("stage") != stage:
        raise ValueError("Отсутствует правильное подтверждение этапа аудита")
    if reply.get("verified_translation") != state["story"]["translation"]:
        raise ValueError("Аудит изменил перевод: материал требует переделки")
    issues = reply.get("issues")
    if not isinstance(issues, list) or any(not isinstance(x, str) for x in issues):
        raise ValueError("Неправильный формат найденных ошибок")
    if not reply["pass"] or issues:
        state["status"] = "rejected"
        state["rejection"] = {"stage": stage, "issues": issues}
        return state
    state["audits"].append({"stage": stage, "pass": True,
                            "kind": AUDIT_NAMES[stage-1]})
    state["status"] = f"await_audit_{stage + 1}" if stage < 3 else "verified"
    return state

def export(state: dict) -> dict:
    if state.get("status") != "verified" or len(state.get("audits", [])) != 3:
        raise ValueError("Нельзя выгружать непроверенный учебный материал")
    if [x.get("stage") for x in state["audits"]] != [1, 2, 3]:
        raise ValueError("Нарушена последовательность проверок")
    # Revalidate after reading the persisted state. No fabricated audit flags.
    return normalize_story(state["story"], state["language"])

def main() -> None:
    parser = argparse.ArgumentParser(description="Посредник для официального ChatGPT без API")
    parser.add_argument("action", choices=("prepare", "receive", "next-audit", "audit", "export", "status"))
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--request", type=Path)
    parser.add_argument("--reply", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.action == "prepare":
        if args.request is None: parser.error("Нужен --request")
        state = prepare(read_json(args.request))
        write_json(args.state, state)
        print(state["prompt"])
        return
    state = read_json(args.state)
    if args.action == "status":
        print(state.get("status", "unknown"))
        return
    if args.action == "next-audit":
        print(next_audit(state))
        return
    if args.action in ("receive", "audit"):
        if args.reply is None: parser.error("Нужен --reply")
        reply = read_json(args.reply)
        state = receive(state, reply) if args.action == "receive" else audit(state, reply)
        write_json(args.state, state)
        print(state["status"])
        return
    if args.action == "export":
        if args.output is None: parser.error("Нужен --output")
        write_json(args.output, export(state))
        print(str(args.output))

if __name__ == "__main__":
    main()
