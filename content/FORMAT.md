# Live content feed

The installed Android app reads `content/live_content.json` from branch `live-content` whenever it starts or returns from ChatGPT.

Add new objects to `items`. Never reuse an existing `id`.

## Word
```json
{"id":"word-unique-id","type":"word","spanish":"barco","meaning":"лодка","category":2}
```
Categories: 1 = verb, 2 = noun, 3 = adjective.

## Fixed expression
```json
{"id":"phrase-unique-id","type":"phrase","spanish":"tener ganas de","meaning":"хотеть, иметь желание"}
```

## Story
```json
{"id":"story-unique-id","type":"story","title":"El pescador","spanish":"El pescador mira el mar. Vuelve a casa.","russian":"Рыбак смотрит на море. Он возвращается домой."}
```

For stories, Spanish and Russian must contain the same number of sentences.

Content changes here do not require rebuilding the APK. Code/interface changes still require a normal app update.
