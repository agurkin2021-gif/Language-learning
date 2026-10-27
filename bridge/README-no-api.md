# No-API посредник: обычный ChatGPT, без дополнительной оплаты

`bridge/official_chatgpt_buffer.py` — отдельный Python-скрипт, работающий со **стандартным ChatGPT Free/Plus** через текстовые сообщения. Он не использует OpenAI API, Subscription Sharing, cookies, скрытый браузер или Qwen. Это **буфер подготовки и проверки данных**, а не неофициальная библиотека для входа в ChatGPT.

## Что реализовано

1. Принимает актуальный снимок словаря, язык и тему, готовит задание для обычного ChatGPT с аргентинским испанским (voseo) или английским.
2. Принимает ответ-рассказ в JSON, проверяет язык, формат, текст и соответствие количества предложений оригинала и русского перевода.
3. Создаёт **три отдельных сообщения для аудита**, каждый этап требует отдельного ответа модели: (1) дословность, (2) грамматика, (3) семантика.
4. Если ответ аудита указывает ошибку, меняет перевод или нарушает порядок этапов, скрипт блокирует экспорт. Сам скрипт не может определить семантические ошибки без модели, поэтому подтверждение модели не гарантирует абсолютную точность.
5. Только после трёх положительных ответов экспортирует `study_story_v1`, совместимый с существующей функцией Android-приложения «Забрать рассказ из ChatGPT».

**Ограничение:** ChatGPT Plus/Free не предоставляет официального скрытого программного канала чтения/отправки сообщений из стороннего Android-приложения. Поэтому при бесплатном варианте отправка подготовленных сообщений в обычный ChatGPT и возврат ответов происходят через пользовательское копирование/«Поделиться». Этот посредник НЕ выполняет полностью автоматический невидимый обмен. Не называйте его бесшовной интеграцией. На подписку ChatGPT продолжают распространяться собственные лимиты.

## Использование

Входной файл `request.json` (создаётся локально):

```json
{
  "language": "es",
  "task": "Короткий рассказ о коте и мальчике",
  "cards": [
    {"term": "tenés", "meaning": "ты имеешь", "mastered": true, "phrase": false},
    {"term": "la casa", "meaning": "дом", "mastered": false, "phrase": false}
  ]
}
```

После установки Python 3.10+ ничего скачивать и устанавливать не нужно.

```bash
# Подготовленный текст выводится в терминал; отправь его в обычный ChatGPT.
python3 bridge/official_chatgpt_buffer.py prepare --request request.json --state job.json

# Сохрани JSON-ответ с рассказом от ChatGPT в chatgpt_story.json
python3 bridge/official_chatgpt_buffer.py receive --state job.json --reply chatgpt_story.json

# Отдельно отправь в ChatGPT инструкцию первого аудита
python3 bridge/official_chatgpt_buffer.py next-audit --state job.json

# Сохрани JSON-ответ первого аудита, затем повтори для второго и третьего
python3 bridge/official_chatgpt_buffer.py audit --state job.json --reply audit_1.json
python3 bridge/official_chatgpt_buffer.py next-audit --state job.json
python3 bridge/official_chatgpt_buffer.py audit --state job.json --reply audit_2.json
python3 bridge/official_chatgpt_buffer.py next-audit --state job.json
python3 bridge/official_chatgpt_buffer.py audit --state job.json --reply audit_3.json

# Экспорт возможен только после трёх положительных проверок
python3 bridge/official_chatgpt_buffer.py export --state job.json --output story.json
```

Результат `story.json` можно передать в Android-приложение обычным механизмом «Поделиться» или импортировать содержимое JSON в окне ChatGPT. Данные хранятся в локальном `job.json` с ограниченными правами доступа. На своём компьютере не публикуйте `job.json` и `request.json` в публичных репозиториях: в них есть учебные данные.

## Тестирование

```bash
python3 -m unittest discover -s bridge -p 'test_*.py' -v
```

Скрипт не добавляет кнопки и не меняет экран Android. Это отдельный проверяемый буфер и протокол, который можно использовать при дальнейшей интеграции через официальный канал, когда такой станет доступен. **Не подменяйте им `chatgpt_broker.py` без изменения Android-клиента:** `chatgpt_broker.py` использует отдельно оплачиваемый API и при установленном ключе совершает платные вызовы.
