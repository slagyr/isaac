---
# isaac-wa78
title: '`isaac sessions set <id>.tags` rejects every value: "must be a set of keywords"'
status: draft
type: bug
tags:
    - cli
    - sessions
created_at: 2026-09-04T16:59:46Z
updated_at: 2026-09-04T16:59:46Z
---

2026-09-04 on zanebot (agent 0.1.44): `isaac sessions set 'isaac-work-1.tags' '#{:ci :isaac}'` (and `[:ci :isaac]`, `:ci :isaac`, `ci,isaac`, `#{"ci" "isaac"}`) all fail with `must be a set of keywords` (session/schema.clj:64 — `:tags` is `mutable {:type :ignore …}` with a validation the CLI's parsed value never satisfies; the value probably arrives as a string or vector, not a set). There is no other way to tag an existing session; the only working path is creating a new one via `prompt --tag … --create always`. Fix: parse the value as EDN, coerce a vector/sequence of keywords or strings to a set of keywords, and accept a comma-separated shorthand; scenario: `sessions set s.tags '#{:ci :isaac}'` → `sessions list` shows both tags; `sessions set s.tags ci,isaac` → same. Found while recovering isaac-work-1 (isaac-jz6h).
