---
# isaac-wuow
title: 'Provider spend budgets: track usage, auto-throttle crews, notify'
status: draft
type: feature
priority: normal
created_at: 2026-07-06T20:06:07Z
updated_at: 2026-07-06T20:06:07Z
---


## Gap

Isaac has no awareness of provider usage limits. On 2026-07-06 the codex crews burned ~65M input tokens (60.2M cached, API-reported) in ~50 minutes and hit the subscription wall mid-board; anthropic credits exhausted the same morning. Nothing in the runtime noticed, throttled, or warned — diagnosis required log archaeology, and every in-flight hail dead-lettered against the wall (now mitigated by isaac-3tvq's deferral, but deferral is damage control, not prevention).

Today the only watchdog is a planner-session cron loop polling a log-parsing script every ~25 minutes with a manual tripwire (>8M est. input tokens/hour sustained → set :max-in-flight 0 by hand). It dies with the session, reacts in tens of minutes, and lives outside the product.

## Proposed capability

Isaac already sees every request's usage (request body size; API-reported cached-tokens and reasoning-tokens; per-session input/output/cache counters). Make spend a first-class runtime concern:

1. **Track** usage per provider over a rolling window (e.g. tokens by class: fresh input, cached, output/reasoning — the classes limits are priced in).
2. **Budget** via config (hot-reloadable): per-provider window budget, e.g. `:providers :chatgpt :budget {:window-ms ... :input-tokens ...}`. No budget configured = no change in behavior.
3. **Throttle** when a budget is exceeded: stop dispatching turns for crews on that provider (semantics of :max-in-flight 0 — deliveries left pending, nothing dead-letters, per the busy/unavailable precedent). Resume automatically when the window rolls off.
4. **Notify** on throttle/resume via the notification comm; log a structured event either way.

## Founding calibration (2026-07-06)

One exhausted codex window on the $100/mo plan ≈ 65M est. input / 60.2M cached / 316K reasoning across 749 requests. Healthy pre-fix hour ≈ 6M est. input; with the :head 0.15 override expect ~2M. Interim manual tripwire: >8M est. input/hour for 2 consecutive checks.

## Notes

- Interacts with isaac-3tvq: budgets prevent hitting the wall; 3tvq defers gracefully when the wall is hit anyway (e.g. external usage on the same account). Both needed.
- The unknown worth investigating during spec: whether codex usage limits discount cached reads — determines whether the budget should weight cached tokens (this morning: 87% of input was cache-reads).
- Replaces the planner-session watchdog loop once live.
