---
# isaac-bzls
title: "Drop (or :crew x :agent x) read-side fallbacks"
status: completed
type: task
priority: low
created_at: 2026-04-23T01:33:10Z
updated_at: 2026-04-23T20:43:57Z
---

## Description

Across the codebase, session/config reads fall back from :crew to :agent for backward compat with old data:

  (or (:crew session) (:agent session) "main")

With write-side dual-key removed and old sessions being acceptable casualties (user direction), these fallbacks can go.

Sites to clean:
- src/isaac/acp/server.clj:168
- src/isaac/cli/sessions.clj:75,76
- src/isaac/config/loader.clj:383 — (or (:crew cfg) (:agents cfg) {})
- src/isaac/drive/turn.clj:436
- src/isaac/session/storage.clj
- src/isaac/tool/memory.clj:19
- any other call site

After: reads consult only :crew (and :crew for cfg). Sessions with only the legacy :agent field fall through to "main" default, which is acceptable.

Blocked by: isaac-y26h (write-side dual-key removal — must land first so we don't strip the fallback while new writes still produce :agent).

Acceptance:
1. No (or (:crew ...) (:agent ...)) patterns in src/
2. No (or (:crew cfg) (:agents cfg)) patterns in src/
3. bb features and bb spec pass

