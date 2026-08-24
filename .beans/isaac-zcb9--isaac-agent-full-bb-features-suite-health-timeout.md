---
# isaac-zcb9
title: isaac-agent full bb features suite health (timeout + session/bridge/compaction flakes)
status: in-progress
type: bug
priority: high
created_at: 2026-08-17T05:42:36Z
updated_at: 2026-08-24T22:23:43Z
---

Split from isaac-rxr4 (episodes migrate-session). NOT a migrate-session product
defect — full `bb features` was already red on pre-rxr4 parent `469a0fc`
(5 failures, ~231s) and exceeds the 180s `bb features` budget (~230–270s →
verify exit 124).

## Goal

Restore a reproducibly green full feature gate on **isaac-agent** (and align
the 180s budget with reality if the suite is legitimately longer).

## Evidence (2026-08-17, scrapper@isaac-work-2 / verify)

On pre-bean parent `469a0fc`:
- full `bb features` → 644 examples, **5 failures**, ~231s

On rxr4 HEAD `fd8060a` (without 180s kill):
- full `bb features` → 651 examples, **11 failures**, ~272s
- failures are session/bridge/compaction/prompts — **none** are
  `migrate_session.feature`
- suite wall time exceeds bb features' **180s** budget → verify exit 124

Sample failure areas on HEAD:
- `prompts/session-identity.feature`
- `session/context_management.feature` (possible cross-scenario leakage of
  last LLM request / compaction fixture)
- `session/context_window_guard.feature`
- `bridge/suspend.feature`
- `session/llm_interaction.feature`
- `session/compaction_strategies.feature`
- `session/compaction_logging.feature`

Bean-local harness touches on rxr4 that *may* add leakage noise (not the
baseline red):
- `step_tables.clj` — regex matching via `pr-str` for non-string actuals
- `session_steps.clj` — `last-llm-request-matches` falls back to
  `drive-dispatch/last-request`

## Acceptance

- [ ] Full `bb features` green on isaac-agent main (0 failures), or failures
      filed as dedicated beans with `@wip` isolation.
- [ ] Suite either finishes under the configured timeout, or the budget is
      deliberately raised with rationale (not a silent kill).
- [ ] Cross-scenario state leakage (last LLM request / compaction fixtures)
      eliminated or guarded so scenarios do not see prior-scenario content.
- [ ] `bb ci` / verify full feature gate reproducibly green.

## Notes

- isaac-rxr4 product AC is the targeted
  `features/episodes/migrate_session.feature` + `bb spec` — do not reopen
  migrate-session for suite health.
- Prefer isolating flakes over expanding rxr4 scope.
