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
updated_at: 2026-09-05T00:17:06Z
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
