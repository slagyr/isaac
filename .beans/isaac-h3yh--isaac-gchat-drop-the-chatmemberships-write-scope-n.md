---
# isaac-h3yh
title: 'isaac-gchat: drop the chat.memberships write scope — no API accepts a DM request, so it buys nothing'
status: completed
type: task
priority: normal
tags:
    - gchat
created_at: 2026-09-23T15:51:29Z
updated_at: 2026-09-23T15:56:05Z
---

Added in gchat 0.2.5 (isaac-qry7 probe). members.create on a DM answers 400 for human DMs; the scope is dead weight on the consent screen. Remove it from the :isaac.google/scopes contribution and the module spec; bump 0.2.6. Note in doc that a re-login is not required (extra granted scopes are harmless).

## Handoff (worker, 2026-09-23)

Done in `isaac-gchat`, branch `bean/isaac-h3yh` (pushed, PR not opened):

- `resources/isaac-manifest.edn` — removed the `chat.memberships` (write) scope
  from `:isaac.google/scopes`; replaced its comment with why it went (no Chat
  API accepts a DM request — `members.create` on a human DM answers 400);
  bumped `:version` 0.2.5 → 0.2.6.
- `spec/isaac/comm/gchat/module_spec.clj` — dropped the same scope from the
  `:isaac.google/scopes` expectation (5 scopes → 4).
- No `doc/` directory exists in isaac-gchat, so no doc update was needed.

2 files changed, 7 insertions(+), 7 deletions(-). Gates: `bb lint` on the two
changed files (0/0), `bb spec` (126 examples, 0 failures), `bb features` (36
examples, 0 failures), `bb ci` (config-bypass-lint ok + both suites green).
Note: a full-tree `bb lint` shows 65 pre-existing errors/warnings in unrelated
spec files (unresolved speclj macros, e.g. `describe`/`it`/`should=`) that
predate this change and are not part of `bb ci`; left untouched.

Commit: `6a7a3ba` "Release 0.2.6: drop the chat.memberships write scope
(isaac-h3yh)" on `bean/isaac-h3yh`, pushed to origin. Left in-progress, no
tags — ready for hand-off/dispatch to verify.

## Landed on main

main-sha: isaac-gchat 6a7a3ba (0.2.6)

Planner check 2026-09-23: bb spec 126/0, bb features 36/0. Fast-forwarded; registry repinned.
