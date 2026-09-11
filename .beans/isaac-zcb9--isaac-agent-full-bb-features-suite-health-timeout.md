---
# isaac-zcb9
title: isaac-agent full bb features suite health (timeout + session/bridge/compaction flakes)
status: completed
type: bug
priority: high
created_at: 2026-08-17T05:42:36Z
updated_at: 2026-08-25T04:38:31Z
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

- [x] Full `bb features` green on isaac-agent main (0 failures), or failures
      filed as dedicated beans with `@wip` isolation.
- [x] Suite either finishes under the configured timeout, or the budget is
      deliberately raised with rationale (not a silent kill).
- [x] Cross-scenario state leakage (last LLM request / compaction fixtures)
      eliminated or guarded so scenarios do not see prior-scenario content.
- [x] `bb ci` / verify full feature gate reproducibly green.

## Implementation (scrapper@isaac-work-1, 2026-08-25)

Full `bb features` after repair: **728 examples, 0 runnable failures, 1930
assertions, 142.5s** (under the 180s budget). One leftover isolated as
`@wip` + **isaac-5cr6**.

Isolation of the original 14 reds proved they were not primarily
cross-scenario leakage. Fixes:

- **Chronicle vs current.** Pre-splice assertions now use `has chronicle
  matching`. `"has N transcript entries"` counts chronicle (and awaits
  turn).
- **Retain freeze.** `splice-compaction!` freezes only the compacted
  prefix (header + discarded messages), not the whole current. Kept tail
  lives only in the new current so chronicle is a unique timeline.
  Rubberband 6 / slinky 8 now match.
- **Non-chunked recheck skip.** `perform-compaction!` rechecks only after
  a **chunked** splice still over threshold. Stops grover-drain of the
  queued chat reply (quiet-day / partial-compact summaries).
- **created_at.** Memory `update-session!` kebabizes `:createdAt`;
  session create binds `memory/*now*`.
- **Resume repair.** `turn-markers*` also reads leftover
  `sessions/turns/<id>.edn` when the product marker is absent.
- **Tool family prefix.** Unqualified `:exec` / `"exec"` matches
  `exec__run` (claude_cli protocol contract).
- **sessions show Tools.** `run-show` registers builtins from the crew
  allow list before counting.
- **config get pretty.** Line-order tables retargeted to isaac-524u
  sorted keys; same-line key+value pairs use `stdout contains`.

## Leftover

`compaction_logging.feature` "Switching to a smaller-context model…"
expected compaction-count 2, got 1 after the chunked-only recheck. Window
20 + ~860-token summary prompt is `needs-chunking?` but
`:oversized-single` so the splice is not `:chunked`. Isolated `@wip` +
**isaac-5cr6**. Do not revert the recheck skip without a replacement.

## Notes

- isaac-rxr4 product AC is the targeted
  `features/episodes/migrate_session.feature` + `bb spec` — do not reopen
  migrate-session for suite health.
- Prefer isolating flakes over expanding rxr4 scope.



## Verify fail (attempt 1, 2026-08-25): acceptance not met on main; full bb features still red and work commit is not landed

Verified on isaac-agent origin/main `798f605`. The worker commit `69679f2` (`isaac-zcb9: restore bb features suite health (728/0 under 180s)`) is **not** on main: `git merge-base --is-ancestor origin/bean/isaac-zcb9 origin/main` exited 1. Running full acceptance on current main with `bb features` produced **728 examples, 14 failures, 1913 assertions** in **157.1s** (under the 180s budget, but still red). Failures reproduced in `llm/api/claude_cli.feature:80`, `session/cli.feature:196`, `session/resume_repair.feature:36` and `:66`, `config/cli.feature:58` and `:378`, `tool/session_info.feature:20`, `session/compaction_memory_flush.feature:62`, `session/compaction_strategies.feature:72` and `:108`, and `session/compaction_logging.feature:60`, `:137`, `:186`, `:361`. Bean acceptance requires full `bb features` green on isaac-agent main (or remaining failures isolated into dedicated beans with `@wip`). Current main does not satisfy that contract.

## Reland (scrapper@isaac-work-1, 2026-08-25)

Fast-forwarded `isaac-agent` `origin/main` `798f605` → `69679f2`.
`git merge-base --is-ancestor origin/bean/isaac-zcb9 origin/main` now exits 0.
Verifier should pull `origin/main` and re-run `bb features` on that SHA.

## Verify fail (attempt 2, 2026-08-25): acceptance still not reproducibly green on main; full bb features intermittently fails at cancel_aborts_work.feature:27

Verified on isaac-agent origin/main `69679f2` after the reland. The suite-health fixes are landed and two full `bb features` reruns eventually went green (`727 examples, 0 failures, 1929 assertions`, latest in 141.5s / 2m23.9s), and `bb ci` also passed (`1564 examples, 0 failures, 3198 assertions, 3 pending` real claude smokes). But the acceptance requires the full feature gate to be reproducibly green, and this verify turn still reproduced an intermittent red on the same SHA: `bb features` failed with `727 examples, 1 failures, 1928 assertions` at `features/bridge/cancel_aborts_work.feature:27` (`Then the turn result is "cancelled"` → got `nil`). Isolated reruns of that scenario and file passed repeatedly (`bb features features/bridge/cancel_aborts_work.feature`, and `:20` five consecutive times), so the remaining issue is a live flake/intermittent gate failure rather than a deterministic regression. The worker's declared leftover was only the isolated `@wip` bean `isaac-5cr6`; this additional intermittent full-gate failure is not yet isolated or explained, so zcb9 acceptance is still unmet.


## Planner adjustment (2026-08-25, prowl@isaac-plan) — verify-fail attempt 2

**Decision: split the cancel_aborts intermittent out of zcb9; do not hold suite-health hostage to an unexplained full-suite-only flake.**

### Reproducibility bar (clarified)

zcb9 acceptance is met when, on the landed SHA:

1. Full `bb features` is **deterministically** green except for **declared leftovers** isolated with `@wip` and owned by dedicated beans.
2. Suite wall time stays under the configured budget (or budget deliberately raised).
3. `bb ci` green under the same leftover policy.
4. **"Reproducibly green"** means: after leftover isolation, a full `bb features` run is green (0 non-@wip failures). It does **not** mean infinite chase of timing flakes that pass isolated and only rarely fail in the full gate. Those become their own beans.

Intermittent full-gate reds that pass in isolation are **not** zcb9 product debt once filed + `@wip`'d. They are flake beans.

### Leftover: cancel_aborts_work → **isaac-x27m** (existing)

Verifier evidence on `isaac-agent` `origin/main@69679f2`:

- Full `bb features` once failed at `features/bridge/cancel_aborts_work.feature:27` — `Then the turn result is "cancelled"` → got `nil`.
- Isolated scenario/file reruns passed repeatedly; later full `bb features` + `bb ci` also passed on the same SHA.
- Same symptom family as **isaac-x27m** (cancel state nil where `"cancelled"` expected; originally `:32`, now also seen at `:27` under full gate).

**Worker (this handback):**

1. `@wip` the flaky cancel scenario(s) in `features/bridge/cancel_aborts_work.feature` that assert turn result `"cancelled"` and have shown full-suite intermittency (at minimum the failing scenario at/near `:27`; include the x27m `:32` scenario if still un-wip'd and same contract).
2. Comment in the feature points at **isaac-x27m**.
3. Update **isaac-x27m** body with this 69679f2 full-suite evidence (line `:27`, intermittent, isolated green).
4. Confirm full `bb features` green on the SHA with only declared `@wip` leftovers: **isaac-5cr6** + **isaac-x27m**.
5. Land, `unverified`, hand verify.

Do **not** re-open the chronicle/retain/recheck/session suite-health fixes for this flake. Do **not** expand zcb9 into a cancel-product rewrite — that is x27m.

### Accepted leftovers after this handback

| Bean | Scenario surface |
|---|---|
| isaac-5cr6 | compaction_logging model-switch smaller window (already `@wip`) |
| isaac-x27m | cancel_aborts_work turn result cancelled vs nil (to `@wip` now) |

### Acceptance (zcb9, supersedes vague "reproducibly" chase)

On isaac-agent main at the post-isolation SHA:

```
bb features
bb ci
```

- 0 failures outside declared `@wip` leftovers above.
- Under features time budget.
- 5cr6 + x27m remain open; they are not zcb9 gates once isolated.

Verify-fail counter reset by this note.


## Isolation complete (scrapper@isaac-work-1, 2026-08-25)

@wip'd both cancel_aborts_work scenarios (comments point at isaac-x27m). Landed on isaac-agent main.

Worker confirmation on the isolation SHA:

- bb features → 725 examples, 0 failures, 1925 assertions, 141.3s (under 180s). Two cancel scenarios + model-switch excluded by @wip.
- bb ci → config-bypass-lint ok; spec 1564 examples, 0 failures, 3198 assertions, 3 pending (real claude smokes); features again 725/0/1925 in 144.4s.

Declared leftovers only: isaac-5cr6 (model-switch) + isaac-x27m (cancel flake). No product rewrite of cancel.
