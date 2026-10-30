> **НОВЫЕ РАССКАЗЫ — ТОЛЬКО ЧЕРЕЗ STORY INBOX.** Полное обязательное
> правило: [AGENTS.md](AGENTS.md). При запросе «добавь рассказ» создаётся
> GitHub Issue `STORY: <название>` с телом JSON. Workflow
> `.github/workflows/story-inbox.yml` вызывает валидатор в ветке
> `live-content` и публикует **единственный** feed:
> `live-content/content/stories.json`. Старые story-файлы не использовать.
> Для переключения установленного Android на этот feed требуется однократный
> выпуск APK из `codex/android-v1`, не APK на каждый рассказ.

# Language-learning