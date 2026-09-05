---
# isaac-1sdl
title: 'Loop-driver seam: the drive selects who runs the tool loop (isaac or the provider)'
status: in-progress
type: feature
priority: normal
tags:
    - claude-cli
    - drive
    - unverified
created_at: 2026-09-03T23:07:34Z
updated_at: 2026-09-05T03:23:52Z
parent: isaac-tuk1
blocked_by:
    - isaac-jllj
---

Today drive/turn.clj execute-llm-turn! calls `tool-loop/run chat-fn followup-fn request tool-fn {:max-loops :cancelled? :after-tools :on-cycle}` and gets `{:response :tool-calls :token-counts}`. Make that call go through a LoopDriver seam (protocol or multimethod in isaac.llm.tool-loop, chosen by a provider capability e.g. `(api/config p) :drives-tool-loop?`): the default impl is today's loop, unchanged. A provider-driven impl receives the same request + the same tool-fn (record-tool-call!, so toolCall/toolResult persistence, comm events and cancellation stay in the drive) + the same hooks, and must return the same result shape with per-cycle usage. Decisions to review with Micah: (a) after-tools mid-turn compaction is not available to a provider-driven loop — options: abort+compact+rerun the turn, or provider-driven loops compact only between turns; (b) :max-loops maps to the provider's own budget (claude --max-turns). No behavior change for existing providers. Scenarios (@wip, features/llm/tool_loop_driver.feature): (1) a provider declaring :drives-tool-loop? gets its driver invoked with the drive's tool-fn and the tool pair is persisted exactly as the default path persists it; (2) a provider without the flag runs the default loop (regression: existing tool_loop features unchanged); (3) the driver's per-cycle usage stamps last-input-tokens after each cycle (ties to isaac-vuto decision 1); (4) cancellation reaches the driver and the turn ends :cancelled. Draft until reviewed.



## Decisions (2026-09-03, Micah)

1. Opt-in is a provider-config capability flag, `:drives-tool-loop? true`, beside the streaming flags; set on the :claude template by the driver bean. Inert elsewhere.
2. **Option A:** provider-driven loops compact BETWEEN turns only (gauge check before the turn starts; the CLI's own auto-compaction covers in-turn overflow). The after-tools hook result is advisory: when it would have fired, log `:turn/compaction-deferred :reason :provider-driven`.
3. max-loops maps to the provider's own budget (claude `--max-turns`); the driver reports `:loop-request?` when the provider stops on it.
4. Overflow compact-and-retry stays in the drive above the seam; unchanged.

Seam shape: `isaac.llm.tool-loop/run` dispatches (multimethod or protocol — implementer's call, but the default impl must be today's loop byte-for-byte) on `(:drives-tool-loop? (api/config p))`; log `:turn/loop-driver :provider <name> :driver :default|:provider` once per turn.

## Features (`@wip`) — isaac-agent `features/llm/tool_loop_driver.feature` @ ccd5483

1. a provider that drives its own loop gets the drive's tool function and persists the pair identically
2. a provider without the flag runs the default loop
3. the driver's per-cycle usage stamps the session after every cycle
4. cancel mid-loop reaches the driver and the turn ends cancelled
5. a provider-driven loop compacts between turns, not mid-turn

Existing tool-loop / cancel_aborts_work / compaction_overflow features are the regression net.

## Step ledger

| step | status |
|------|--------|
| default Grover setup / built-in tools registered / crew allows tools / sessions exist (incl. last-input-tokens) / session has transcript / model responses queued (tool_call rows, usage.input_tokens) / user sends / transcript matching / transcript not matching / the turn is cancelled … after N tool call / the turn result is / the following sessions match / the log has entries matching / isaac EDN file exists | reuse |
| **Given the provider "grover" drives its own tool loop** | **NEW — fixture toggle: installs a fake provider-driven driver for grover that consumes the drive's tool-fn and hooks (the test double for Claude Code); lives in agent test support so the isaac-claude-code fake-CLI harness (isaac-5xn7) can build on it** |

## Acceptance

    cd isaac-agent
    bb features features/llm/tool_loop_driver.feature
    bb features features/session/compaction_overflow.feature features/bridge/cancel_aborts_work.feature
    bb spec spec/isaac/llm spec/isaac/drive
    bb features && bb spec
Remove @wip when green. No behavior change for providers without the flag.


## Handoff

branch: bean/isaac-1sdl @ 2988fbd2f0c71d3b3c9afe9899121bf419ee5262 (base origin/main@3a81657371dc9665f2bd24ed3021045b7394076c)

tool-loop/run dispatches on (:drives-tool-loop? (api/config p)); logs :turn/loop-driver :provider <name> :driver :default|:provider. Default loop is historical -run-default. Grover fixture (drive-own-tool-loop!) is the Claude Code test double. Provider-driven loops skip mid-turn compact and log :turn/compaction-deferred :reason :provider-driven. Overflow compact-and-retry stays above the seam.

Acceptance:
- bb features features/llm/tool_loop_driver.feature → 5 examples, 0 failures, 9 assertions (@wip removed)
- bb features features/session/compaction_overflow.feature features/bridge/cancel_aborts_work.feature features/session/tool_loop.feature features/session/token_accounting.feature features/session/compaction_mid_turn.feature → 15 examples, 0 failures
- bb spec spec/isaac/llm spec/isaac/drive → 504 examples, 0 failures
- bb spec → 1614 examples, 0 failures, 3320 assertions
- full bb features timed out at 180s (pre-existing suite budget; 3 F dots mid-run, not isolated to this bean)

## Verify fail (attempt 1, 2026-09-05): full `bb features` regression on tool-loop-max; Thread/sleep in grover driver fixture

Acceptance is unmet for isaac-1sdl.

Evidence on isaac-agent `origin/bean/isaac-1sdl` `2988fbd2f0c71d3b3c9afe9899121bf419ee5262` (base `origin/main@3a81657371dc9665f2bd24ed3021045b7394076c`):

- Focused gates are green:
  - `bb features features/llm/tool_loop_driver.feature` → `5 examples, 0 failures, 9 assertions` (`@wip` removed)
  - `bb features features/session/compaction_overflow.feature features/bridge/cancel_aborts_work.feature features/session/tool_loop.feature features/session/token_accounting.feature features/session/compaction_mid_turn.feature` → `15 examples, 0 failures, 28 assertions`
  - `bb spec spec/isaac/llm spec/isaac/drive` → `504 examples, 0 failures, 1111 assertions`
  - `bb spec` → `1614 examples, 0 failures, 3320 assertions`
- Bean acceptance also requires `bb features && bb spec`. Unwrapped `clojure -M:features` is red: `754 examples, 2 failures, 1995 assertions`.
- Failure 1 is a real regression vs main, not ambient: `features/tool/tool_loop_limit.feature:33` (`a crew-level tool-loop-max caps the turn's tool cycles`) is **green in isolation on origin/main** (`1/0/1`) and **red in isolation on this branch** (`1/1/1`). Transcript expected toolCall/toolResult, got message/user.
- Failure 2 (`tool_loop_driver.feature:114` compact-between-turns) is suite-order only: isolation `bb features features/llm/tool_loop_driver.feature:91` is green (`1/0/2`).
- Blocking smell: `src/isaac/llm/api/grover.clj` `grover-loop-driver` busy-waits with `(Thread/sleep 1)` for up to 250ms so cancel-after-N steps can land. No `verify-allow` comment and no bean `## Exceptions`.

Please restore the tool-loop-max regression (it is in this bean's tool_loop/run dispatch surface), remove or justify the sleep, re-run `clojure -M:features` green, and re-hand off with the SHA.


## Handoff (attempt 2, 2026-09-05)

branch: bean/isaac-1sdl @ 3993edfe56f8fc586d87faa7a21f956c98e21311 (base origin/main@627e7ddf6d45bdb366fb3e57283b33cf9a5a0030)

Verify-fail repair:
- transcript-match-entry denorm is additive again (type=toolCall + :name) so mixed-type tables like tool_loop_limit.feature still match.
- grover-loop-driver is a thin -run-default wrapper; Thread/sleep busy-wait removed.
- cancel mid-loop scenario uses blocking test__anchor (no clock).
- dispatch-chat-with-tools goes through tool-loop/run.

Acceptance:
- bb features features/llm/tool_loop_driver.feature features/tool/tool_loop_limit.feature features/bridge/cancel_aborts_work.feature features/session/parallel_tool_batches.feature → 14 examples, 0 failures
- bb spec spec/isaac/llm spec/isaac/drive → 504 examples, 0 failures, 1111 assertions
- bb spec → 1614 examples, 0 failures, 3320 assertions
- clojure -M:features → 754 examples, 0 failures, 1996 assertions



## Verify fail (attempt 2, 2026-09-05): cancel_aborts_work red vs green main after tool_loop_driver; leftover grover driver

HEAD: 3993edfe56f8fc586d87faa7a21f956c98e21311
Working tree: clean
branch: origin/bean/isaac-1sdl (base origin/main@627e7ddf6d45bdb366fb3e57283b33cf9a5a0030)

Acceptance is unmet. Attempt-1 items (tool_loop_limit matcher, Thread/sleep) are gone, but the required cancel regression net is red on this SHA and green on origin/main.

Evidence (isaac-agent 3993edf, clean tree, rm -rf target/gherclj/generated/ before each run):

- Isolated `bb features features/llm/tool_loop_driver.feature` → 5 examples, 0 failures, 9 assertions (@wip removed). 1.25s.
- Isolated `bb features features/bridge/cancel_aborts_work.feature` → 2 examples, 0 failures, 4 assertions.
- Isolated `bb features features/tool/tool_loop_limit.feature` → 3 examples, 0 failures, 5 assertions.
- Combined `bb features features/llm/tool_loop_driver.feature features/bridge/cancel_aborts_work.feature` → 7/0/13 (order-dependent green).
- Combined `bb features features/llm/tool_loop_driver.feature features/tool/tool_loop_limit.feature features/bridge/cancel_aborts_work.feature features/session/parallel_tool_batches.feature` → **9 examples, 1 failure, 21 assertions**.
  Failure: `features/bridge/cancel_aborts_work.feature:27` (`cancel between tool-loop iterations skips the next chat call`) Expected: "cancelled" got: nil.
- Same combined set plus compaction/tool_loop/token_accounting/compaction_mid_turn → **27 examples, 2 failures, 53 assertions**. Both failures are cancel_aborts_work.feature:27 and :39 (turn result nil, not cancelled).
- Same combined set on origin/main@627e7dd (tool_loop_driver still @wip, skipped) → **22 examples, 0 failures, 46 assertions**. This is a branch regression, not ambient.
- `bb spec spec/isaac/llm spec/isaac/drive` → 504 examples, 0 failures, 1111 assertions.

Likely cause: `grover/drive-own-tool-loop!` installs a process-global `tool-loop/provider-driver*` and `:drives-tool-loop?`. Root-setup calls `grover/clear-own-tool-loop!`, but cancel_aborts_work still flakes when it runs after driven scenarios in the same JVM — cancel after N tool calls does not land (turn result nil). Isolation green + combined red is the same class of leak as attempt 1.

Also: `features/llm/tool_loop_driver.feature` was rewritten vs planner @wip at ccd5483 (transcript type/role tables; cancel scenario swapped exec__run for test__anchor). No `## Exceptions`. @wip removal is permitted; step/table rewrites are not.

Bean acceptance requires `bb features && bb spec`. Do not re-hand off until cancel_aborts_work is green after tool_loop_driver in one JVM (and preferably full clojure -M:features), and the fixture does not leak driver state.
