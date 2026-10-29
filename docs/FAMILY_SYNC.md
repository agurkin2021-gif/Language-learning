# Family content delivery (public GitHub)

No private GitHub repository or token is needed to add study material. The Android app downloads profile-specific files from the public `live-content` branch of `agurkin2021-gif/Language-learning` on app resume.

| Profile | Spanish cards | Spanish stories | English cards | English stories |
|---|---|---|---|---|
| Papa | `content/papa/cards-es.json` | `content/papa/stories-es.json` | `content/papa/cards-en.json` | `content/papa/stories-en.json` |
| Mama | `content/mama/cards-es.json` | `content/mama/stories-es.json` | `content/mama/cards-en.json` | `content/mama/stories-en.json` |
| Dima | `content/dima/cards-es.json` | `content/dima/stories-es.json` | `content/dima/cards-en.json` | `content/dima/stories-en.json` |

The Dima Spanish feed contains 300 new words. Missing files are treated as empty feeds.

Cards JSON example:

```json
{"version":1,"items":[{"id":"dima-300-001","type":"word","spanish":"admitir","meaning":"признавать","category":1}]}
```

Categories: 1 verbs, 2 nouns, 3 adjectives. Use `type: phrase` for expressions. Every item must have a unique stable ID. Incoming sync adds missing cards without resetting mastery or duplicating Spanish terms; deleted remote items are tracked to prevent repeated reimport.

Stories JSON example:

```json
{"version":1,"items":[{"id":"dima-story-001","type":"story","title":"Un día","spanish":"Hoy es un día especial.","russian":"Сегодня особенный день."}]}
```

**Privacy:** The public repository contains only manually published learning content, never device-specific mastery, private progress or automatic backups. The optional legacy private backup mechanism remains separate and requires explicit configuration. This is one-way delivery of published cards and stories, not full cross-device state merging.

An updated Android APK must be built and installed before the new public sync code works on a phone. Verify the CI build and phone migration before release.
