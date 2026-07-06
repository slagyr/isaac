---
# isaac-3tvq
title: Provider walls defer hail deliveries instead of burning attempts
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-07-06T16:32:20Z
updated_at: 2026-07-06T19:13:35Z
---

## Goal

**Dead-letter poison, wait out weather — never confuse the two.** The 5-attempt dead-letter loop exists to stop poison hails (a hail that breaks its target every delivery). Today `attempts` increments on ANY turn failure, including environmental ones that say nothing about the hail. When a provider hits a usage wall, every hail on the board dead-letters within minutes — and each retry re-drives a giant turn into a known wall.

## Observed (2026-07-06, zanebot)

codex `usage_limit_reached` + anthropic credit exhaustion dead-lettered 6 hails across every band (work, verify, plan, ci-failure) in ~30 minutes, including the isaac-4tn1→verify handoff and three legitimate lcay planner escalations. None were poison. The failed/*.edn records carried no error detail (diagnosis required log archaeology).

Note: busy sessions were NOT part of the burn — `session-available?` gates before any attempt and leaves the delivery pending at zero cost (existing scenario "a delivery to an in-flight session is left pending"). The original premise of this bean was wrong. Part of the observed churn was the age-based inflight recovery stealing live >5-min turns (see Interim default below); the durable fix for that is isaac-7li9/isaac-vdfc.

## Design (2026-07-06, Micah-approved)

Hail delivery must not be concerned with retry pricing or provider semantics. Two-outcome contract:

- **The drive (isaac-agent) classifies.** A provider wall (429 / usage_limit_reached / quota-credit exhaustion) makes the turn result `{:unavailable? true, :retry-after-ms N}` — N from the 429 `Retry-After` header when present, else a drive-side default. Genuine failures return `{:error ...}` exactly as today.
- **The hail worker treats `:unavailable?` like busy**: the delivery returns to pending with `:next-attempt-at` = now + retry-after — NO attempts increment, no class table, no provider knowledge in hail. New log event `:hail/delivery-deferred` (warn) for visibility. A walled provider means hails wait indefinitely rather than dead-letter — the hail isn't broken.
- **Genuine-failure path untouched**: attempts++, backoff, 5-attempt dead-letter.
- **Dead-letter records carry the error keyword + message** (isaac-cehc parity on the record, not just the log).
- **Interim default**: `default-inflight-recovery-ms` 300000 → 7200000 (2h) — the age-only orphan heuristic steals live turns longer than 5 min (clears their in-flight guard, burns attempts as :worker-crash, enables double-drive on the same session). 2h makes theft unrealistic; crash recovery just takes longer. This heuristic is deleted entirely by isaac-7li9/isaac-vdfc.

## Scenarios (approved one-by-one, 2026-07-06)

Committed @wip:
- isaac-hail `features/delivery.feature` (commit 129d733): unavailable-defer (line 535), 2h recovery window boundary (line 568), cehc dead-letter record detail amended (line 418). 0tf3 scenarios' claimed-at moved 09:00Z -> 07:00Z (forward-compatible with the 2h default; green now and after).
- isaac-agent `features/llm/provider_walls.feature` (commit 93c1180): 429 + Retry-After classification (line 16), usage-limit default (line 38).

New machinery (approved): Grover scripted response types `unavailable` (pre-classified result, hail-side) and `http-error` (raw status/retry-after/message, drive-side); step `the turn result is unavailable with retry-after-ms N`. Config key `:defaults :provider-retry-after-ms` (default 1800000, hot-reloadable). New log events: `:hail/delivery-deferred` (warn, hail) and `:chat/provider-walled` (warn, drive).

Classification triggers: HTTP 429, OR known wall messages (`usage_limit_reached`, credit-balance exhaustion — anthropic's arrives as a 400 invalid_request_error, so message-matching is required for it).

## Acceptance

- [x] `bb features features/delivery.feature:475` green — unavailable defer (isaac-hail)
- [x] `bb features features/delivery.feature:413` green — cehc dead-letter record detail (isaac-hail)
- [x] ~~2h inflight recovery~~ — superseded by isaac-7li9 turn-marker claim on rebase; scenario removed
- [x] `bb features features/llm/provider_walls.feature` green (isaac-agent, both scenarios)
- [x] Full suites green in both repos; @wip removed from remaining isaac-3tvq scenarios

## Handoff (work-2, 2026-07-06)

- **isaac-agent** `249a745`: `provider_wall.clj` classifies 429/wall messages; `turn.clj` normalizes before response processing; grover `unavailable`/`http-error` types; fixed `retry-after` coercion (Long from grover was crashing classify).
- **isaac-hail** `09ab921`: `defer-delivery!` on `:unavailable?` (re-queues delivery, no attempts++); dead-letter merges error fields; merged with isaac-7li9 turn-marker claim + isaac-2xj5 suspend; agent pin bumped to `249a745`.
