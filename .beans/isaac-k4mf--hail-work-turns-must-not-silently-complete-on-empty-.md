---
# isaac-k4mf
title: Hail work turns must not silently complete on empty terminal model responses
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-03T15:55:00Z
updated_at: 2026-07-03T18:37:17Z
---

## Problem

On zanebot, `isaac-work-1` stopped active bean work twice without any explicit
completion, handoff, or blocked signal:

- `isaac-a9vf` made real progress, edited files, and passed focused spec checks
  locally, then the turn ended with an empty assistant message.
- `isaac-n5r2` was then accepted on the same session, claimed, began
  investigation, and also ended with an empty assistant message.

The current runtime clears session in-flight state when a turn returns without
`tool-calls` and without `:error`, even if the final assistant content is empty.
That makes the session eligible for new hail delivery while bean work is still
unfinished.

## Investigation findings (2026-07-03)

- This was **not** mid-turn interruption. `isaac-a9vf` completed a turn, then
  `isaac-n5r2` was delivered on the next turn.
- The real orchestration failure is that a hail-driven work turn can end with:
  - executed tools
  - no final handoff / commit / blocked signal
  - empty assistant content
  - no runtime error
- Isaac currently accepts that shape as a normal completed turn.

## Contract (refined 2026-07-03, planner + Micah review)

**Layer split — the runtime never learns about beans or hails:**

1. **Agent runtime (isaac-agent, generic):** "terminal model response with empty content and no error" is a suspicious terminal shape for ANY turn (with or without prior tool calls). Guard: exactly ONE bounded retry — re-request with a continuation nudge appended. If the retry is also empty, the turn FAILS explicitly with `:error :empty-terminal-response` (an error result, never a normal completion). No bean/hail knowledge in the runtime.
2. **Hail delivery (isaac-hail):** a turn that fails means the delivery FAILED — it flows into the existing failed/retry/undeliverable machinery (attempts increment, redelivery, undeliverable + notification at exhaustion) instead of silently freeing the session. Redelivery is the outer retry loop.
3. **Completion evidence (verify-band hail sent, .beans pushed) is skill/prompt discipline** — checked by crews per hail-bean-* skills, NOT by the runtime. Cut from the runtime contract deliberately: it keeps the fix small, generic, and loop-safe.

Retry budget is 1 (not "small"): the minimum that distinguishes a transient provider hiccup from a dead turn; hail redelivery provides the outer retries.

## Desired behavior

For hail-driven bean work, an empty terminal model response is not enough to
prove the task is done.

The system should require explicit completion evidence before treating the turn
as successfully finished and making the session available for new bean delivery.

Examples of acceptable completion evidence:

- a verify-band or plan-band hail was sent
- a bean status/notes commit was made and pushed
- a non-empty assistant completion summary is present
- an explicit blocked/conflict escalation was recorded

## Loop risk

Yes, there is loop risk if the fix is "just keep asking the model again until it
talks."

Any recovery behavior must be bounded and outcome-based. Likely shape:

- detect suspicious terminal result (`executed-tools > 0`, `content == ""`,
  no handoff / no bean-side effect)
- allow at most a small bounded retry budget
- then fail the turn explicitly if completion evidence is still missing

Do not allow unbounded reprompt loops. (Resolved by the contract above: inner retry = exactly 1; outer retries = existing bounded hail redelivery.)

## Likely repo scope

- `isaac-agent`
  - `src/isaac/llm/tool_loop.clj`
  - `src/isaac/drive/turn.clj`
- possibly hail / orchestration surfaces if completion evidence needs explicit
  tagging or metadata

## Notes

- This is related to, but separate from, the in-flight / session occupancy
  question. Even with perfect occupancy rules, a silent empty completion is
  still a bad terminal state for hail-driven work.
- This bean is intentionally `draft` until we settle the exact acceptance
  contract and bounded-retry behavior in scenarios.

## Provider-normalization suspect (added 2026-07-03, from Micah)

isaac-n5r2's death had a stranger final provider shape: **:model nil and a tiny final payload** — suggesting the responses-API stream may have terminated abnormally (e.g. ended without a proper response.completed) and the provider layer normalized the partial into an EMPTY SUCCESS instead of an error.

Worker must root-cause this alongside the guard:

- If a degenerate stream termination (missing response.completed / nil model / empty accumulated response) is detectable at the provider layer (`isaac.llm.api.responses`), it should surface as an :error response THERE — errors already fail turns via the existing path, and the empty-terminal guard then serves as defense-in-depth rather than primary detection.
- The runtime guard (scenarios below) stands regardless: it catches every shape the provider layer cannot classify.

## Acceptance scenarios (committed @wip, 2026-07-03)

- isaac-agent `features/session/error_handling.feature` — 3 scenarios: nudge recovery, bounded explicit fail (retry=1, queue-enforced boundedness), post-tool-execution variant. Dry-run reproduced the bug (`Expected "done.", got: ""`). Zero new steps.
- isaac-hail `features/delivery.feature` — 1 scenario: empty-terminal turn failure ⇒ attempts increment + back-off to deliveries/ (existing dead-letter convention), never delivered/. Zero new steps.

Acceptance: un-@wip all 4; `bb spec`/`bb features` green in isaac-agent and isaac-hail; provider-normalization root cause written into this bean (fix at provider layer if classifiable).

## Implementation (work-3, 2026-07-03)

- **isaac-agent `9c27a7b`:** `guard-empty-terminal-response` in `drive/turn.clj` — one continuation nudge, then `:error :empty-terminal-response`. Responses API `incomplete-responses-stream?` surfaces degenerate streams (nil model, no `response.completed`, empty content/tool-calls) as `:llm-error` at the provider layer. Match DSL `messages[-1]` path support in `spec/isaac/step_tables.clj`.
- **isaac-hail `c0ecab7`:** un-@wip delivery scenario; agent pin bumped. Existing delivery worker `:error` → `reschedule!` path covers turn failures — no hail code change required.
- **Provider root cause:** zanebot's nil-model empty payload matches a Responses API stream that never emitted `response.completed`; previously normalized to empty success. Now classified as `:llm-error` at provider; turn guard is defense-in-depth.


---

## Resolution (unverified — for verifier)

No new product edits were required in this session: the implementation already landed and was verified on current heads.

### Landed implementation

- `isaac-agent` introduced the generic empty-terminal guard in **9c27a7b**:
  - one continuation nudge for a terminal empty assistant response
  - explicit `:error :empty-terminal-response` on a second empty response
  - provider-side detection in Responses API for degenerate streams that ended without `response.completed`
- `isaac-hail` accepted the delivery-side behavior in **c0ecab7**:
  - un-`@wip` delivery scenario
  - existing delivery `:error` → reschedule/dead-letter path handles the failed turn; no extra hail logic was needed

### Provider root cause

The zanebot nil-model / tiny-payload death shape matches a Responses API stream that terminated without `response.completed`. Previously that normalized into an empty success; now it is surfaced as `:llm-error` at the provider layer, with the turn guard acting as defense-in-depth for any remaining empty-terminal shapes.

### Re-verified in this session

Current heads tested:

- `isaac-agent` `19a7f9e` (contains `9c27a7b`)
- `isaac-hail` `03cdf46` (contains `c0ecab7`)

Commands/results:

- `isaac-agent: bb spec` — green
- `isaac-agent: bb features` — green
- `isaac-hail: bb spec` — green
- `isaac-hail: bb features` — green with **3 existing pending scenarios**, **0 failures**

This satisfies the bean contract: all four committed `isaac-k4mf` acceptance scenarios are no longer `@wip`, both repos are green on verification runs, and the provider-normalization root cause is documented here for the verifier.
