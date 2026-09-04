---
# isaac-qomx
title: 'Discord typing heartbeat: re-ping while a turn is in flight'
status: completed
type: feature
priority: high
created_at: 2026-08-31T16:03:45Z
updated_at: 2026-09-04T14:06:43Z
---

Repo: **isaac-discord**. Phase 0 of the Discord scuttlebutt plan (isaac-frvu
review 2026-08-31) — but needs NO scuttlebutt: uses today's Comm protocol.

## Problem (Micah)

on-turn-start fires post-typing! once; Discord's indicator expires after
~10s. Any turn longer than that looks dead — isaac goes opaque exactly when
it's working hardest. Any liveness signal is a huge improvement.

## Design

- on-turn-start begins a typing heartbeat for the session's channel:
  re-POST /typing every ~8s (indicator lasts ~10s; stays continuously lit).
- Stops on on-turn-end (every outcome — a leaked heartbeat that types
  forever is THE failure mode to spec against).
- One heartbeat per channel even with concurrent turns mapped to it;
  respect Retry-After on 429 (skip a beat, never tight-loop).
- No config knob unless review demands one — this is a fix, not a feature.

## Scenarios (to write before todo, in isaac-discord's harness)

- a turn lasting > 2 beats sends ≥3 typing requests, spaced ~8s (fixture
  clock).
- heartbeat stops at turn end: no typing requests after on-turn-end
  (success AND error outcomes).
- 429 with Retry-After on the typing route: next beat waits, turn
  unaffected.

Draft until scenarios are committed @wip. Independent of isaac-5nxf —
dispatchable as soon as planned.



## Scenarios (committed @wip at slagyr/isaac-discord ba9b997)

features/comm/discord/typing.feature — :33 refresh while running (wait:true
+ clock 17s ⇒ 3 POSTs); :46 stops at turn end (fast turn, 30s ⇒ 1 POST);
:59 error turn also stops (the leak case; relies on guaranteed
finalization). Preamble's aspirational refresh claim corrected.

## Step ledger

| step | status |
|------|--------|
| Grover setup in / faked Gateway / config: / client ready as bot / responses queued (wait) / MESSAGE_CREATE / test clock advances / outbound request matches | reuse |
| **{n:int} Discord outbound HTTP requests to {url:string} were made** | **NEW — count variant; tolerate singular/plural** |

## Design pins

Per-channel scheduler heartbeat: start on-turn-start, refcount concurrent
turns per channel, cancel on-turn-end (every outcome), ~8s period. 429/
Retry-After skips a beat (unit-spec obligation, not Gherkin). No config knob.

## Acceptance

Remove @wip from the three scenarios, then:
    bb features features/comm/discord/typing.feature
    bb spec spec/isaac/comm
Full module bb features + bb spec green. Today's Comm protocol — NO
isaac-5nxf dependency; do not migrate the protocol here.

## Verify fail (attempt 1, 2026-09-03): bean branch regresses discord comm specs and does not satisfy the full-module green gate

## Verify fail (attempt 2, 2026-09-03): typing heartbeat scenarios pass, but discord comm specs still regress and the full-module green gate remains unmet


## Planner adjustment (2026-09-03, prowl@isaac-plan) — verify-fail attempt 2

**Decision: keep `bb spec spec/isaac/comm` controlling. Drop the hard full-module `bb features` gate. The model-ref nils are a rebase miss, not a typing-heartbeat product hole.**

### Why the two spec reds

`origin/bean/isaac-qomx` @ `1a81fa0` is **1 behind / 1 ahead** of `origin/main` (`ab935be`):

- Bean has: `1a81fa0` typing heartbeat (not on main).
- Bean lacks: `ab935be` "repair stale tool-name and **dispatch-capture fixtures**".

Those two examples (`discord_spec.clj:372` Discord-wide crew/model-ref `"marvin"`; `:395` per-channel `"chef-bender"`) already exist on `987998e` / `ba9b997`. Main's release commit **stopped stubbing `charge/build`** and captures dispatch opts another way. The bean branch still stubs `charge/build`, so `:opts :crew` / `:model-ref` stay nil. **qomx's src diff does not change routing.** Rebase onto `origin/main` (or cherry-pick `ab935be`) and those two should follow main.

### tool_visibility / full module features

`features/comm/discord/tool_visibility.feature:21` (`Expected #{"read" "exec" "write"}, got #{}`) is **red on current main** too — ambient, not qomx. Do not hold typing-heartbeat on that file or on a module runner that does not complete cleanly. Same trap as ohsy/pqjn/5nxf full-suite gates.

### Acceptance (supersedes "Full module bb features + bb spec green")

On isaac-discord at a SHA that contains `1a81fa0` **and** is rebased onto (or contains) `ab935be`:

```
bb features features/comm/discord/typing.feature
bb spec spec/isaac/comm
```

0 failures. typing.feature not `@wip`.

**Do not** require full `bb features` / `bb jvm-features` green for this bean.

If after rebase the two model-ref examples are still red, that is now a real qomx regression — fix routing capture, don't drop the spec command.

### Out of scope

- Scuttlebutt protocol migration (5nxf).
- tool_visibility.feature (file a suite-health draft only if not already owned).
- Discord reconnect storm (9i5w).

### Handoff

Back to **work**: rebase `bean/isaac-qomx` onto `origin/main`, re-run the two commands, retag unverified. Verify-fail counter reset by this note.

## Verify fail attempt 1 (2026-09-03, scrapper@isaac-work-1)

Verifier saw `bb spec` red on two crew/model-ref examples and a 60s `bb features` timeout. Both were **pin skew**, not heartbeat regressions:

- bean branch `1a81fa0` was based on `987998e` (pre-0.1.11). Current `origin/main` is `ab935be` (Release 0.1.11: repair stale tool-name and dispatch-capture fixtures).
- Those two spec failures and `tool_visibility.feature:21` (`Expected #{"read" "exec" "write"}, got #{}`) are present on `987998e` and **fixed on `ab935be`**. Confirmed: `origin/main` `bb spec spec/isaac/comm/discord_spec.clj` = 40/0/83; `tool_visibility.feature` = 1/0/1.
- Rebased `bean/isaac-qomx` onto `origin/main`. Heartbeat commit is now `d3ec95c`.

Post-rebase gates (ISAAC_GIT=1, pinned aliases):
- `bb spec` → 47/0/99
- `bb features features/comm/discord/typing.feature` → 4/0/4
- `clojure -M:spec` (full JVM spec) → 98/0/228
- `clojure -M:features` (full module features) → 64/0/140 (77s wall; `bb features` wrapper times out at 60s — same wrapper on main, ambient to the heartbeat)

`bb features` 60s wrapper is a known test-support timeout (`bb.test-timeout/test-timeout-ms` = 60000). Full-module green is the JVM command, which now passes.


## Verify fail (attempt 2, 2026-09-03): rebased heartbeat branch contains the main fixture repair, but the planner-adjusted acceptance commands still do not complete green as written

Evidence on `isaac-discord` `origin/bean/isaac-qomx` at `d3ec95c` (contains `ab935be`):

- Branch contains the planner-required rebase target:
  - `git rev-parse --short HEAD` → `d3ec95c`
  - `git merge-base --is-ancestor ab935be HEAD` → success
- `features/comm/discord/typing.feature` no longer has `@wip` on the three controlling scenarios.
- Direct JVM checks are green:
  - `clojure -M:features features/comm/discord/typing.feature` → `4 examples, 0 failures, 4 assertions`
  - `clojure -M:spec spec/isaac/comm` → `98 examples, 0 failures, 228 assertions`
  - `bb spec` (default native subset) → `47 examples, 0 failures, 99 assertions`
- But the **exact planner-adjusted acceptance commands** still fail in this repo:
  - `bb features features/comm/discord/typing.feature`
    - prints `4 examples, 0 failures, 4 assertions`
    - then exits nonzero with `jvm-features timed out after 60s`
  - `bb spec spec/isaac/comm`
    - exits nonzero from the SCI/native path with `Protocol not found: clojure.lang.IHashEq`

So the product behavior appears green after rebase, but the acceptance commands named in the bean still do not run cleanly as written. This needs planner clarification/amendment (for example, whether the intended gate is the direct JVM path or a different bb task shape), not another blind worker loop.


## Planner adjustment (2026-09-03, prowl@isaac-plan) — verify-fail attempt 2 after rebase

**Decision: amend acceptance to the JVM commands that actually measure the product. Do not bounce to work. Do not teach SCI IHashEq. Do not raise the 60s `bb features` wrapper as this bean.**

### Why the literal commands fail after a green rebase

Verifier on `origin/bean/isaac-qomx` @ `d3ec95c` (contains `ab935be`; typing not `@wip`):

| Command | Result |
|---|---|
| `clojure -M:features features/comm/discord/typing.feature` | 4/0/4 |
| `clojure -M:spec spec/isaac/comm` | 98/0/228 |
| `bb spec` (default native subset) | 47/0/99 |
| `bb features features/comm/discord/typing.feature` | prints 4/0/4 then **exit nonzero** `jvm-features timed out after 60s` |
| `bb spec spec/isaac/comm` | SCI **IHashEq** (gateway/service under that glob — isaac-fvzo JVM-only split, same as isaac-9i5w) |

Heartbeat product is green. The prior planner commands named `bb features` / `bb spec spec/isaac/comm` as if they were the JVM gates. In isaac-discord they are not: `bb features` delegates to `jvm-features` **with a 60s wrapper timeout** (suite can finish and still fail the wrapper); `bb spec <path>` is native SCI.

### Acceptance (supersedes the `bb features` / `bb spec spec/isaac/comm` pair)

On isaac-discord at a SHA containing `d3ec95c` (or successor that still carries the heartbeat + `ab935be`):

```
bb jvm-features features/comm/discord/typing.feature
bb jvm-spec spec/isaac/comm
```

Equivalents if the wrapper is unavailable: `clojure -M:features features/comm/discord/typing.feature` and `clojure -M:spec spec/isaac/comm`.

0 failures. typing.feature not `@wip`.

**Do not** require:
- native `bb spec spec/isaac/comm` (IHashEq / fvzo)
- `bb features` wrapper exit 0 (60s timeout after a green run is test-support, not qomx)
- full-module `bb features` / `bb jvm-features` with no path

### Out of scope (unchanged)

Scuttlebutt migration, tool_visibility ambient red, 9i5w reconnect, raising `test-timeout-ms`.

### Verify handoff

No rebuild. Re-hail **verify** against the amended commands. Verify-fail counter reset by this note.



## Note (2026-09-04): branch superseded
`bean/isaac-qomx` (d3ec95c) was verified 2026-09-03 but never merged (pre isaac-nrak rule) and now conflicts with main: isaac-o0bk re-implemented the heartbeat. isaac-ay0s ported qomx's count step and activated the three typing scenarios on the o0bk implementation (green, shipped in discord 0.1.13). The branch can be deleted.
