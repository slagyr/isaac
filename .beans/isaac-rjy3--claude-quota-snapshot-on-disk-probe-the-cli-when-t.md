---
# isaac-rjy3
title: 'Claude quota: snapshot on disk, probe the CLI when there is none'
status: todo
type: task
priority: high
tags:
    - claude-code
created_at: 2026-10-28T08:53:09Z
updated_at: 2026-10-28T08:53:09Z
---

## Why

isaac-u9xj keeps the latest Claude `rate_limit_event` in an in-memory atom. On skiff (2026-10-28, right after deploy) that left both claude-code providers `unknown` for good:
- `isaac prompt` and `isaac quota` run in their own processes, so a CLI turn's event dies with it and the CLI never sees the server's.
- No crew on skiff runs on a claude-code model by default, so server turns rarely produce an event; a restart wipes what there is.

The point of the tool is to see headroom before a reset — idle subscriptions are exactly the ones we need numbers for.

## What

1. **Snapshot on disk.** The latest event per provider id is written under the Isaac root (e.g. `cache/quota/<provider-id>.edn`: the rate_limit_info plus when it was seen). Every process reads it; it survives restarts. The in-memory atom can stay as a read-through cache.
2. **Probe when there is no fresh snapshot.** No snapshot, or one older than **10 minutes** ⇒ the meter runs one minimal CLI call with the provider's own config/env (same `subprocess-env`, `forward-env`, `:env`): `--model haiku`, no tools, no session persistence, `--output-format stream-json --verbose`, a one-word prompt. Record its event like any turn's and report from it. The agent's 2-minute report cache bounds how often this can run.
3. **Probe failure ⇒ `:unknown`** with the CLI's own message (exit code / stderr), never an error result.

## Acceptance

- Scenarios: isaac-claude-code `features/llm/quota.feature` (3 @wip; gate lines below). The old "Before its first turn … unknown" scenario is replaced by the probe-failure one.
- New step `the Claude quota memory is cleared` — drops the in-memory state only (simulates a new process); the disk snapshot stays.
- Spec the 10-minute freshness rule (fresh snapshot ⇒ no probe; stale ⇒ probe) — clock-driven unit spec is enough.
- `bb features` green.

## Planner notes

- Probe cost: one Haiku call with a tiny prompt and no tools; at most once per 10 min per provider, and only when someone asks for quota.
- `/api/oauth/usage` stays off the table: skiff's providers use setup tokens, refused with 403 `oauth_scope_insufficient`.

feature-baseline: isaac-claude-code 93c51588486fabf63fdfbe8e24237ed518a24fae
feature-blob: isaac-claude-code features/llm/quota.feature 5f022e82cbbecb05fbda76d1dcbaf00ba8747be5
