---
# isaac-xd2z
title: Crew model fallback chain on provider exhaustion (Codex → Grok → Claude Code Opus as config, not ops)
status: draft
type: feature
priority: normal
tags:
    - agent
    - llm
created_at: 2026-09-18T04:50:22Z
updated_at: 2026-09-18T04:50:22Z
---

Make the overnight failover rule a crew property instead of an ops script.

## Decision (2026-09-18, Micah)

If Codex (chatgpt) runs out of usage, crews on it move to Grok; if Grok runs out, move to Claude Code Opus. Tonight this is `~/.isaac/ops/failover.sh` on zanebot, run from the planner's watch every 15 min: a provider is "exhausted" when the last 60 min hold ≥3 `:chat/provider-walled` events and zero successful `turn/model-response-summary` for it; it rewrites `crew.<id>.model` via `isaac config set` (hot-reload) and appends to `~/.isaac/ops/failover-ledger.txt` for the morning restore. Today's data: chatgpt walled 9× and succeeded 9× in the same hour — rate limits, not exhaustion — so the rule must not fire on walls alone.

## Decision (2026-09-18, Micah) — where the config lives

Crew config, with a default in the global config: `:defaults :model-fallbacks [...]` is the base cascade, `crew.<id>.model-fallbacks [...]` overrides it. The cascade is an ordered list of model ids, as many as wanted; when a model hits its usage limit the turn moves to the next one in the list, and so on down the cascade.

## Feature

- Crew schema: `:model-fallbacks [:grok-4-6 :claude-opus]` (ordered). Global `:defaults :model-fallbacks` as the base; crew overrides.
- `drive/provider_wall.clj` already classifies walls and defers with `retry-after`. New: when a turn is walled, and the provider has been walled ≥N times with no success in the last W minutes (`:defaults :provider-exhausted {:walls 3 :window-ms 3600000}`), the turn **re-dispatches on the next fallback model in the same turn** instead of deferring; log `:chat/model-fallback :from :to :provider`. Session records the override so the whole session stays on the fallback until the primary has a success (probe on next turn start after `retry-after`).
- Hail deferral unchanged when no fallback is configured.
- `isaac crew status` (or existing status) shows the active fallback per crew.

## Scenarios to draft (grover, provider walls stubbed)

1. Primary walled with ≥3 walls and no success in the window → the same turn completes on the first fallback; log carries from/to.
2. Walls with recent successes → deferral as today, no fallback.
3. First fallback also exhausted → second fallback.
4. After the primary succeeds again → next turn returns to the primary (session override cleared).
5. No fallbacks configured → identical to current behaviour.

Out of scope: cost accounting; per-model rate budgets.
