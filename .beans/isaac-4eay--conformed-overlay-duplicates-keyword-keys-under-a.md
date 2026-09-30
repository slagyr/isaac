---
# isaac-4eay
title: Conformed overlay duplicates keyword keys under a :string key-spec (":ops" beside :ops)
status: draft
type: bug
priority: high
created_at: 2026-09-30T17:54:28Z
updated_at: 2026-09-30T17:54:28Z
---

Found 2026-09-30 by the gmail restructure (isaac-j4m5). Foundation's conformed-over-raw overlay (isaac-dnib) canonicalizes dynamic-map keys between raw and conformed config. A map whose `:key-spec` is `{:type :string}` conforms a keyword key with `str` (":ops", colon included), not `name` ("ops"). Raw `:ops` and conformed ":ops" never unify, so BOTH land in the loaded config: gmail-routes produced a bogus ":ops" route that sorted first and mislabeled every message "isaac/:<route>". gmail worked around it by switching its key-spec to `{:type :id}`.

Latent everywhere: `:key-spec {:type :string}` also appears in agent, cron, google, hail, hooks, mcp, discord and foreman manifests. Any of those tables written with keyword keys (EDN files, `config set`) gets the duplicate once zanebot/yopp take the new foundation.

## Wanted (planner recommendation, awaiting Micah)
Fix in foundation: a keyword key under a :string key-spec conforms via `name`, so raw and conformed unify and the loaded table has one entry. Then decide per manifest whether :string or :id is the right key type (no bulk manifest change in this bean).

Scenario TBD: a Marigold table with a :string key-spec, EDN keyword keys, loaded config has exactly one entry per key.
