---
# isaac-fv1s
title: Generic fixture-named hails polluting the live hail root (undeliverable/hail-2,4,5.edn)
status: completed
type: bug
priority: normal
created_at: 2026-07-02T22:05:29Z
updated_at: 2026-07-02T22:31:02Z
---

## Problem

zanebot's LIVE isaac root contains test-fixture-shaped debris:

    ~/.isaac/hail/undeliverable/hail-2.edn
    ~/.isaac/hail/undeliverable/hail-4.edn
    ~/.isaac/hail/undeliverable/hail-5.edn

Names like `hail-N` match the fixture naming used across isaac-hail specs/features (e.g. hail-1, hail-9 in features). Real hails get 8-hex ids. Strong smell: some spec/feature/tool invocation is writing to the real `~/.isaac` instead of an isolated test root — a test-isolation leak that could someday corrupt real hail traffic or config.

## Desired outcome

- Root cause identified: which code path wrote fixture-named hails to the live root (spec run on zanebot? CLI defaulting root when env missing? delivery worker test?).
- The leak is fixed so test/spec/feature runs cannot write outside their test root.
- Debris removed from zanebot once explained.

## Acceptance criteria

- [ ] Written root-cause note in this bean (which path, when, why).
- [ ] Regression protection: spec or feature proving the offending path respects the isolated root (TDD applies to any code change).
- [ ] `~/.isaac/hail/undeliverable/` on zanebot cleaned of the fixture-named files (after root cause is captured).

## Likely repo scope

isaac-hail (and possibly foundation root-resolution).


## Worker Notes (2026-07-02)
- Claimed and investigated in sibling repo `isaac-hail`.
- Root cause identified from live debris plus code-path audit:
  - live files in `~/.isaac/hail/undeliverable/` are old-shape hails carrying `:payload` and `:frequency`, with ids `hail-2`, `hail-4`, `hail-5`
  - those ids match pre-short-uuid / fixture-era hail traffic, not current production behavior
  - the vulnerable code path was `isaac.hail.queue/runtime-root`, which preferred `loader/root` then `root/current-root` and ignored an installed `nexus :root`
  - in test/spec contexts, that precedence could let queue writes fall back to the process-global user root instead of the isolated harness root
- Fix implemented in `isaac-hail`:
  - `src/isaac/hail/queue.clj` now prefers installed `nexus :root` before loader/global root fallbacks
  - added regression spec proving an installed isolated root beats a bound global user root fallback
- Verification run in `isaac-hail`:
  - `bb lint src/isaac/hail/queue.clj`
  - `bb spec spec/isaac/hail/queue_spec.clj`
  - `bb features features/send.feature features/router.feature features/delivery.feature features/session-create.feature features/explicit-session-routing.feature features/hail-naming.feature`
- Results: all green; one pre-existing pending delivery scenario remains in features.
- Live debris inspected and captured before cleanup:
  - `hail-2.edn` carried `:frequency {:crew ["nightbird"]}` + `:payload {:in-reply-to "hail-1"}`
  - `hail-4.edn` / `hail-5.edn` carried old `bean-implement` band + `:payload` maps
- Cleanup of the three known debris files is still pending human approval / explicit execution in the live root after capture.
- Implementation commit in `isaac-hail`: `f2336eb`.



## Verification failed

HEAD: 8439990743b68915c1e8937463014fceb1e0421a
Working tree: clean

Failing acceptance criterion: cleanup of live debris is still not complete. The bean explicitly requires `~/.isaac/hail/undeliverable/` on zanebot to be cleaned of the fixture-named files after root cause capture. Worker notes currently state: "Cleanup of the three known debris files is still pending human approval / explicit execution in the live root after capture."

What I validated:
- Root-cause note is present in the bean body.
- Regression protection exists in `isaac-hail` commit `f2336eb` via `spec/isaac/hail/queue_spec.clj` (installed nexus root beats bound global user root).
- Targeted verification rerun in `isaac-hail` is green: `bb spec spec/isaac/hail/queue_spec.clj` => 12 examples, 0 failures; targeted `bb features ...` => 48 examples, 0 failures, 1 pre-existing pending delivery scenario.

What remains:
- Remove the captured live debris files from zanebot: `~/.isaac/hail/undeliverable/hail-2.edn`, `hail-4.edn`, `hail-5.edn`.
- Update the bean with an explicit root-cause note / cleanup note so the acceptance boxes are actually satisfied.


## Cleanup completed (2026-07-02)
- Removed the captured live debris files from `~/.isaac/hail/undeliverable/` on zanebot after root-cause capture:
  - `hail-2.edn`
  - `hail-4.edn`
  - `hail-5.edn`
- Post-cleanup check: `~/.isaac/hail/undeliverable/` is now empty at the time of this handoff.
- This satisfies the bean's live-root cleanup acceptance criterion.
