---
# isaac-xqy1
title: migrate-layout must carry each crew's recall vectors into sessions/<crew>/recall/ instead of rebuilding placeholder rows
status: in-progress
type: bug
priority: critical
created_at: 2026-09-11T04:15:24Z
updated_at: 2026-09-11T04:15:24Z
parent: isaac-b6w0
---

Repo: isaac-agent (src/isaac/episodes/layout.clj rebuild-recall!). Rehearsed on a copy of the zanebot store (2026-09-11): after migrate-layout every crew's new sessions/<crew>/recall/index.edn has :dims 1 :model "" and rows with :vector [0.0]; the real index (episodes/marvin/index.edn: dims 768, model nomic-embed-text, 1.8 MB vectors.json) is left behind under episodes/<crew>/ and never read again. recall/query filters rows to the configured model, so every migrated scene is 'stale' and recall returns nothing — marvin would lose recall of everything before today. No CLI re-embeds an index (isaac recall only queries; isaac embed only embeds text).

## Required
- rebuild-recall! reads the legacy episodes/<crew>/index.edn + vectors.json when present, re-keys each row with :session-id (episode-id → session-id after the move), keeps :model and the unpacked vector, writes sessions/<crew>/recall/ with the same dims/model, then deletes the legacy index files so episodes/ ends empty. Scenes with no legacy row fall back to the current behaviour (placeholder row) and are logged.
- Scenario (@wip → green) in layout.feature: the migrate scenario plants a legacy episodes/cordelia/index.edn + vectors.json for the scene (model mini-embed, a known vector) and asserts the migrated index keeps model mini-embed and the row's vector, and that episodes/cordelia/ no longer exists.

## Acceptance
layout.feature green; bb spec && bb features green; dry run + real run on the zanebot rehearsal copy: sessions/marvin/recall/index.edn has :model nomic-embed-text and the same row count as episodes/marvin/index.edn had; isaac recall --crew marvin 'Fermi' returns hits on the rehearsal root.
