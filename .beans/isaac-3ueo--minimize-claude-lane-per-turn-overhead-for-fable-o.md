---
# isaac-3ueo
title: Minimize claude-lane per-turn overhead for Fable on zanebot
status: draft
type: feature
priority: normal
created_at: 2026-07-13T16:03:13Z
updated_at: 2026-07-13T16:03:13Z
---

## Goal

Minimize per-turn overhead on the claude subscription lane so Fable (claude-fable-5) is usable as a worker model on zanebot. Measured 2026-07-13: claude CLI turns run ~15.7s vs grok ~10.4s for the same one-sentence prompt — a ~5s fixed per-turn tax from spawning the `claude` binary cold (config load, subscription session re-establish) every turn, since Isaac runs it with --no-session-persistence.

## Two paths to Fable on zanebot — Micah to choose

**Path A — Fable via the metered anthropic provider (:api "messages", api-key).**
Fable model id `claude-fable-5` over api.anthropic.com is a normal HTTPS request — same low overhead as grok, no CLI spawn. Cost: metered per-token (not subscription). If the anthropic provider already has a key in ~/.isaac/.env, this is a config-only change: a `models/fable.edn` {:model "claude-fable-5" :provider :anthropic}. FASTEST to Fable; costs money.

**Path B — reduce the claude CLI cold-start (subscription, free-at-margin).**
Investigation, not a foregone fix. Candidates:
- A warm/persistent claude process (daemon) that turns feed, instead of spawn-per-turn — biggest potential win, most complexity; needs lifecycle mgmt in the adapter.
- claude --output-format stream-json with a persisted session id (reuse subscription session across turns) — trades the isaac-owns-everything stance (auws) for speed; evaluate.
- Trim what the CLI loads per invocation (skip MCP/plugins/settings discovery if flags allow).
Each candidate: measure the delta before committing. Fable via CLI is only worth it if B gets the tax well under grok's latency.

## Recommendation for spec time

Confirm whether the goal is subscription-Fable (Path B research) or just Fable-fast (Path A, likely a same-day config change if a key exists). If Path A satisfies, B becomes a lower-priority optimization for the whole claude lane rather than a Fable blocker.

## Note

Distinct from the isaac CLI startup tax (see classpath-cache follow-up) — that is Isaac's own boot; this is the claude subprocess. A server turn pays only the claude spawn, not Isaac's boot.

## Status

Draft per Micah 2026-07-13.
