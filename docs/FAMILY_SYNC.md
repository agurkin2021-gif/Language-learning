# Единый приём рассказов / Family content delivery

**СТРОГО ОДИН ВХОД ДЛЯ ВСЕХ РАССКАЗОВ:** `agurkin2021-gif/Language-learning`,
ветка `live-content`, файл `content/stories.json`. Публикацию выполняет
`scripts/story_inbox.py` из этой же ветки. Обращения к `content/live_content.json`,
`content/papa/stories-es.json`, `content/stories-es.json` или
`content/mama/stories-es.json` запрещены для НОВЫХ рассказов.

При запросе «добавь рассказ» агент принимает текст и целевые `profile`/`language`,
подготавливает JSON и добавляет запись в **единственный** файл. Не нужно
пересобирать APK для каждого нового рассказа после однократного перехода
приложения на единый канал.

Схема входящей записи:

```json
{
  "profile": "papa",
  "language": "es",
  "title": "Un día de invierno",
  "spanish": "Hacía frío. Caminé por el bosque.",
  "russian": "Было холодно. Я шёл по лесу."
}
```

`profile`: строго `papa`, `mama`, `dima`; `language`: строго `es` или `en`.
Поле `spanish` содержит текст на выбранном языке, включая английский при
`language=en`. Русский перевод обязателен. Допустим также `id` (стабильный ID).
Нельзя помещать личный словарь, прогресс и токены в публичный файл.

Публикация в локальном checkout ветки `live-content`:

```bash
python3 scripts/story_inbox.py add < /path/to/story.json
python3 scripts/story_inbox.py check
git add content/stories.json
git commit -m "Publish family story"
git push origin live-content
```

При работе через GitHub connector меняется **только** `content/stories.json`
ветки `live-content` через `fetch_file` → добавление элемента → `update_file`
с полученным SHA → повторное чтение для подтверждения. Перед записью соблюдаются
те же условия, что в `scripts/story_inbox.py`: обязательные поля, уникальный ID,
UTF-8, не более 500 историй, не более 1 MiB, выравнивание количества
предложений/строк текста и перевода по Android `Sentences.split`.
GitHub Actions `Validate single story feed` выполняет независимую проверку.

Повтор одного запроса по тому же профилю, языку и названию не создаёт дубль.
Исправление рассказа делается с сохранением прежнего `id` через
`python3 scripts/story_inbox.py add --replace` или эквивалентную правку
единого JSON, а не новой публикацией в соседний файл. Сброс базы на телефоне
может блокировать автоматическое восстановление рассказов — не сбрасывать
данные ради решения проблемы импорта.

Карточки остаются на прежних отдельных входах
`content/<profile>/cards-<language>.json`. Это НЕ вход рассказов.
Личные архивы карточек в приватном GitHub продолжают работать независимо.
