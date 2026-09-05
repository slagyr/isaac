---
# isaac-jgng
title: Compaction requests sized to the context window die 'closed' on chatgpt; cap per-request tokens and adapt chunk size on failure
status: in-progress
type: bug
priority: high
tags:
    - agent
    - compaction
    - unverified
created_at: 2026-09-04T16:29:12Z
updated_at: 2026-09-05T17:03:41Z
---

Repo: **isaac-agent** (`src/isaac/session/compaction.clj` chunk plan,
`src/isaac/drive/turn.clj perform-compaction!`).

## Problem

The chunk planner sizes summarization requests against the model's context
window: 90k-token histories on a 278k window are sent as ONE request
(`needs-chunking false`). On chatgpt those requests never answer — they die
"closed" after ~15 min (see the SSE-stall bean) — five attempts in a row on
2026-09-04 03:39–03:55Z, then `compaction-stopped :too-many-failures`, then
the turn ran to `context-exhausted` at 274k. The same history on Opus
(200k window) split into two chunks and succeeded. Repeated 2026-09-04
15:44–16:15Z at 48k tokens. Compaction is currently the single largest
consumer of worker wall clock on zanebot.

## Design (settle before todo)

- Cap the per-request summarization size independently of the window:
  `:compaction {:max-request-tokens N}` (proposed default 32k) — chunk
  whenever history exceeds it, regardless of `needs-chunking` by window.
- On a failed/stalled chunk, halve the chunk size and retry once before
  counting a consecutive failure (adaptive), so one bad provider day does not
  brick the session (companion to isaac-vrtb).
- Consider a dedicated summarization effort (low) — reasoning models at
  effort 7 may sit silent for the whole think; summaries do not need it.
  Open question for Micah: fixed low effort vs inherit.
- Do NOT change the trigger threshold here (that is config; tono-work-1's
  session-level 0.3 was hand-set).

## Acceptance (sketch)

Spec: a 90k-token history with `max-request-tokens 32k` on a 278k window
plans ≥3 chunks; a chunk failure with `:stream-stalled` retries at half size
before incrementing consecutive-failures. Feature: existing
`compaction_logging` / `compaction` scenarios stay green; one new scenario
shows chunked compaction under the cap with the Grover fixture.


## Decision (2026-09-04, Micah): fixed low effort for summarization

- Compaction requests run at a fixed low effort, independent of the session's
  effort: `:compaction {:effort 2}` (universal 0–10 knob; translated per API
  as usual, omitted for models with `:allows-effort false`). Configurable in
  the same compaction layers as `:threshold` / `:head`; code default 2.
- Same model as the session (no dedicated compaction model).
- Plus the two mechanical guards from the design: `:max-request-tokens`
  (default 32k) forces chunking regardless of window; a chunk that fails
  with `:stream-stalled` / "closed" retries once at half size before
  counting a consecutive failure.

## Acceptance (runnable)

Specs (`spec/isaac/session/compaction_spec.clj`, `spec/isaac/drive/turn_spec.clj`):
1. The summarization request built by `summarize-messages` carries
   `:effort 2` when the session effort is 7; a model with `:allows-effort
   false` gets no effort key.
2. `:compaction {:effort 5}` in crew config overrides the default.
3. A 90k-token history with `max-request-tokens 32000` on a 278k window
   plans ≥3 chunks; 20k tokens plans 1.
4. A chunk failing with `{:error :stream-stalled}` is retried once at half
   the chunk size; only a second failure increments `consecutive-failures`.

Feature (`features/session/compaction_logging.feature` or `compaction.feature`):
one scenario with the Grover fixture showing a chunked compaction under the
cap and `the last LLM request matches` `reasoning.effort` = low (Responses
bucketing of effort 2). Existing compaction scenarios stay green.

    bb spec spec/isaac/session spec/isaac/drive
    bb features features/session/compaction.feature features/session/compaction_logging.feature

Full gate green. Hand off `--tag=unverified`.

## Scenarios (committed @wip at slagyr/isaac-agent ee990a9) — supersede the sketch

`features/session/compaction_requests.feature` — 4 scenarios:
1. compaction request carries `effort 2` while the reply request keeps the
   session's `effort 7`.
2. `compaction.effort 5` in crew config overrides the default (same policy
   merge as threshold/head; model-level works identically — "just like all
   the other model config", Micah).
3. `compaction.max-request-tokens 60` on a 200-token window: a history that
   fits the window is still split into 3 chunks (`:session/compaction-chunked
   :chunks 3` in the log); the chronicle holds the merged summary.
4. A summary request that fails `error | closed` is retried once at half
   size (`:session/compaction-chunk-retry :attempt 1`), consecutive-failures
   stays 0, chronicle holds the merged summary.

## Step ledger

| step | status |
|------|--------|
| Isaac root / config: / isaac EDN file exists with / sessions exist (`effort`, `compaction.head`, `crew` columns) / session has transcript / model responses queued (`text`, `error`) / user sends / the compaction request matches / the last LLM request matches / the log has entries matching / the following sessions match (`compaction.consecutive-failures`) / session has chronicle matching | reuse |
| **`:session/compaction-chunk-retry`** | **NEW info event** (`:attempt`, chunk tokens before/after) — add to the ISAAC.md registered-events table, coverage = scenario 4 |
| `:session/compaction-chunked` | existing info event — add to the ISAAC.md table with scenario 3 as coverage |
| `compaction.effort`, `compaction.max-request-tokens` | NEW compaction policy keys (schema + `compaction-policy-keys`); code defaults 2 and 32000 |

## Spec-only obligations

- `allows-effort false` model → summary request carries no `:effort`.
- Retry-at-half happens ONCE: a second drop on the halved chunks increments
  `consecutive-failures` (no descent to single messages).
- Retry branch keys on transport-class failures only: `:stream-stalled`
  (isaac-6zk5) or an `:llm-error` whose message is "closed"; context-length
  errors keep their existing overflow path.
- Scenario 3's fixture is tuned by message length against the Grover token
  estimate — adjust text or cap to land on exactly 3 chunks, never the
  assertion.

## Acceptance (final)

Remove @wip from `features/session/compaction_requests.feature`, then:

    bb features features/session/compaction_requests.feature
    bb features features/session/compaction_logging.feature features/session/compaction_overflow.feature features/session/compaction_template.feature
    bb spec spec/isaac/session spec/isaac/drive

0 failures; full `bb features` + `bb spec` green. Hand off `--tag=unverified`.

## Implementation notes (scrapper @ isaac-work-2)

branch: bean/isaac-jgng @ 12a1625e3b7ccea64b3bb3f03c4b480e73109c0d (base origin/main@212b17cf924f160c47cfda0f90a30d9512efddaf)

- compaction.effort default 2 (policy-merge like threshold/head); omitted when model :allows-effort false
- compaction.max-request-tokens default 32000 forces chunking independent of the model window
- transport-class drop (:stream-stalled / llm-error "closed") retries once at half size; second drop increments consecutive-failures
- :session/compaction-chunk-retry logged on the retry; scenario 3 fixture tuned to 3 chunks (window 800 / cap 670)
- gates: bb spec 1636/0; bb features compaction_requests + logging/overflow/template 24/0
- clojure -M:features: compaction_requests green. cancel_aborts_work second scenario flakes when run after this suite (passes in isolation / after stash of this branch) — pre-existing flake, not this bean
