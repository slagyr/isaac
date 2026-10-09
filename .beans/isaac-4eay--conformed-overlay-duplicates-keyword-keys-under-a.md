---
# isaac-4eay
title: Conformed overlay duplicates keyword keys under a :string key-spec (":ops" beside :ops)
status: completed
type: bug
priority: high
created_at: 2026-09-30T17:54:28Z
updated_at: 2026-09-30T19:18:47Z
---

Found 2026-09-30 by the gmail restructure (isaac-j4m5). Foundation's conformed-over-raw overlay (isaac-dnib) canonicalizes dynamic-map keys between raw and conformed config. A map whose `:key-spec` is `{:type :string}` conforms a keyword key with `str` (":ops", colon included), not `name` ("ops"). Raw `:ops` and conformed ":ops" never unify, so BOTH land in the loaded config: gmail-routes produced a bogus ":ops" route that sorted first and mislabeled every message "isaac/:<route>". gmail worked around it by switching its key-spec to `{:type :id}`.

Latent everywhere: `:key-spec {:type :string}` also appears in agent, cron, google, hail, hooks, mcp, discord and foreman manifests. Any of those tables written with keyword keys (EDN files, `config set`) gets the duplicate once zanebot/skiff take the new foundation.

## Wanted (planner recommendation, awaiting Micah)
Fix in foundation: a keyword key under a :string key-spec conforms via `name`, so raw and conformed unify and the loaded table has one entry. Then decide per manifest whether :string or :id is the right key type (no bulk manifest change in this bean).

Scenario TBD: a Marigold table with a :string key-spec, EDN keyword keys, loaded config has exactly one entry per key.

## Acceptance (gated, Micah approved 2026-09-30)

- The @wip scenarios in isaac-foundation `features/cli/config_string_keys.feature` pass with @wip removed: keyword keys in a `{:type :string}` key-spec table load as one entry each (`config keys` lists `north`, `south` only), and the entry carries its conformed values (`berth "3"` → 3).
- Fix in foundation (conform/overlay), not in module manifests. No manifest key-spec changes in this bean.
- `bb ci`, `bb features-slow`, `bb jvm-spec` green.

feature-baseline: isaac-foundation 69c8def0dc9924247908223147c1a15e326579ca
feature-blob: isaac-foundation features/cli/config_string_keys.feature c386a74309d76e7c12212e634e6c89c4513e10e8

## Landed on main (2026-09-30)

main-sha: isaac-foundation 78cd7bcb4e45da489f10aa8d6f07508ba1c5f128
