## Три профиля: постоянный словарь и синхронизация

Единственный репозиторий — этот `Language-learning`. Канонический полный
словарь каждого профиля хранится отдельно в ветке `live-content`:

- **Папа:** `content/papa/vocabulary-es.json`, `vocabulary-en.json`.
- **Мама:** `content/mama/vocabulary-es.json`, `vocabulary-en.json`.
- **Дима:** `content/dima/vocabulary-es.json`, `vocabulary-en.json`.

Android обменивается изменениями в обе стороны через GitHub, сохраняет
освоенные карточки и удалённые записи. Три вспомогательных скрипта:
`scripts/sync_papa.py`, `sync_mama.py`, `sync_dima.py`;
инструкции работы ChatGPT — `AGENTS.md`.

Первые пустые JSON-файлы не означают, что личные словари уже выгружены.
Они заполнятся после подключения приложения к GitHub с правом записи.
**Репозиторий публичный; эти словари будут доступны всем.**

> **НОВЫЕ РАССКАЗЫ — ТОЛЬКО ЧЕРЕЗ STORY INBOX.** Полное обязательное
> правило: [AGENTS.md](AGENTS.md). При запросе «добавь рассказ» создаётся
> GitHub Issue `STORY: <название>` с телом JSON. Workflow
> `.github/workflows/story-inbox.yml` вызывает валидатор в ветке
> `live-content` и публикует **единственный** feed:
> `live-content/content/stories.json`. Старые story-файлы не использовать.
> Для переключения установленного Android на этот feed требуется однократный
> выпуск APK из `codex/android-v1`, не APK на каждый рассказ.

# Language-learning