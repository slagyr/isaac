---
# isaac-xzef
title: Wire bb lint-pins into module bb ci (bb.edn test-support only)
status: draft
type: task
priority: high
tags:
    - ci
created_at: 2026-09-24T23:37:56Z
updated_at: 2026-09-24T23:38:02Z
blocked_by:
    - isaac-j4jr
---

Split from isaac-j4jr. Wire `bb lint-pins` into each module's `bb ci` after foundation lands.

Blocked by isaac-j4jr: foundation half must be on isaac-foundation main (squash sha recorded). Do not pin at a bean-branch sha. Do not bump `deps.edn` foundation product pins — those four shas (9ab2527 / df64bf1 / fae35d6 / 1afd934) are a fleet upgrade, not this wiring.

## How

One commit per repo. In `bb.edn` only: add `lint-pins` (foundation test-support at the **landed** foundation main sha) and call it from `bb ci`. Pins coherence reads `deps.edn` only, so a bb.edn test-support bump does not cascade sibling pins. Accept that bb's classpath may load a newer test-support than product foundation — that is the wiring, not a fleet bump.

Repos: isaac-agent, isaac-server, isaac-cli-server, isaac-cli-proxy, isaac-acp, isaac-hail, isaac-hooks, isaac-mcp, isaac-discord, isaac-imessage, isaac-claude-code, isaac-episodes, isaac-foreman, isaac-worksite, isaac-cron.

## Acceptance

Per repo, `bb lint-pins` runs inside `bb ci` and exits 0 against current main pins (every isaac-* sha fetch-reachable). Checkpoint the bean after each repo.

Do **not** recut `deps.edn` foundation product pins. Do **not** land foundation.
