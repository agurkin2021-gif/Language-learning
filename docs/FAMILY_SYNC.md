# Separate family content synchronization

The private GitHub repository configured in Android Settings is required. Never commit a GitHub token to source code. The three profiles remain independent: `papa`, `mama`, `dima`.

## Incoming content (edit in the private repository)

| Profile | Spanish cards | Spanish stories | English cards | English stories |
|---|---|---|---|---|
| Papa | `content/papa/cards-es.json` | `content/stories-es.json` | `content/papa/cards-en.json` | `content/stories-en.json` |
| Mama | `content/mama/cards-es.json` | `content/mama/stories-es.json` | `content/mama/cards-en.json` | `content/mama/stories-en.json` |
| Dima | `content/dima/cards-es.json` | `content/dima/stories-es.json` | `content/dima/cards-en.json` | `content/dima/stories-en.json` |

Cards feed format:

```json
{
  "version": 1,
  "items": [
    {"id": "dima-example-001", "type": "word", "spanish": "aprovechar", "meaning": "использовать с пользой", "category": 1}
  ]
}
```

Categories: 1 verbs, 2 nouns, 3 adjectives. For expressions use `"type":"phrase"` and omit category. Each id must be stable and unique within the file. Never reuse a deleted id for a different card. A new file is optional: missing files are treated as empty feeds. Incoming content is additive, not destructive; changing a translation on GitHub will not overwrite an existing studied card. An explicit reset prevents automatic reimport.

Stories feed format (existing):

```json
{
  "version": 1,
  "items": [
    {"id": "dima-story-001", "type": "story", "title": "Un día", "spanish": "Hoy es un día especial.", "russian": "Сегодня особенный день."}
  ]
}
```

## Outgoing backups (written by Android)

- `vocabulary/es-<device-id>.json` and `vocabulary/en-<device-id>.json` for Papa (legacy paths)
- `vocabulary/mama/{es,en}-<device-id>.json` and `vocabulary/dima/{es,en}-<device-id>.json`
- `stories/{papa,mama,dima}/{es,en}-<device-id>.json`

Backups are device-specific snapshots, not editable incoming feeds. Do not edit them to add cards; edit the `content/<profile>/cards-<language>.json` feed instead. Backups preserve card mastery and review counts, and include all locally stored stories. There is currently **no automatic cross-device conflict resolution, restore of deleted phone data, or bidirectional merge of edits**. The phone remains the authority for its own study state. No UI controls are added.

## Release checks

Build and run Android tests; verify Papa/Mama/Dima import isolation, repeated sync idempotency, deleted-card tombstones, explicit-reset behavior, offline retries, and APK update migration before releasing. A GitHub commit alone does not install an APK.
