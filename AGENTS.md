# STRICT STORY ROUTE — NON-NEGOTIABLE

For ANY request "add/publish/send a story to the Android app", use ONLY:
- Repository: `agurkin2021-gif/Language-learning`
- Branch: `live-content`
- Single canonical file: `content/stories.json`
- Intake and validation: `scripts/story_inbox.py` on **the same branch**.

Never guess or use legacy `content/live_content.json`, `content/stories-es.json`,
`content/papa/stories-es.json`, `content/mama/stories-es.json`,
`content/dima/stories-es.json`, `main`, or `codex/android-v1` for
**new story data**. Those are not the story ingress path.

Input record: `profile` = `papa|mama|dima`, `language` = `es|en`,
`title`, `spanish` (the text in the target language), `russian`,
optionally stable `id`; `type` must be `story`.
A missing profile defaults to **papa** only when that target is explicitly
established by the user; a missing language defaults to **es** only when
Spanish is established by the request. Never route unknown input by guessing.

Procedure for ChatGPT/GitHub connector:
1. Read `live-content/scripts/story_inbox.py` (canonical schema/validation).
2. Fetch `live-content/content/stories.json`; preserve existing items.
3. Validate target, nonempty fields, Android sentence/line alignment,
   duplicate ID/title, and total feed limits. For repeated requests, do
   not duplicate. For edits, keep the existing ID.
4. Update **only** `live-content/content/stories.json` with the fetched blob
   SHA; retry conflicts by refetching and merging (never overwrite concurrent stories).
5. Fetch it again and verify the exact ID and target profile/language.
6. Confirm the GitHub feed changed; do not assert that the phone imported it.
   A story should appear after the compatible APK syncs, unless the device
   disabled imports after a database reset.

For a checked-out `live-content` branch use the exact one-command script:
`python3 scripts/story_inbox.py add < submission.json`.
Automated validation: `python3 scripts/story_inbox.py check`.

To change Android behavior work ONLY on `codex/android-v1`, then wait for
CI, signed `phone-builds`, and separate on-device acceptance.
Never reset the user's database or delete the app.

**Cards** use separate paths. This policy governs STORY delivery exclusively.
