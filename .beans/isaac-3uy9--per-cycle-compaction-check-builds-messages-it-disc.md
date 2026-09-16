---
# isaac-3uy9
title: Per-cycle compaction check builds messages it discards; prompt build is untimed
status: in-progress
type: task
priority: high
created_at: 2026-09-16T14:32:36Z
updated_at: 2026-09-16T14:40:06Z
---

## Problem

The gap between a tool result and the next model request is ~2.7s per cycle. Measured on zanebot 2026-09-16 (agent 0.1.70), session `scrapper/isaac-work-1`, ~300 history entries / 1.5MB transcript:

| step | measured |
|---|---|
| `session/transcript-read` | avg **8.2ms** over 1157 reads (max 711ms) |
| `session/compaction-check` | avg **1280ms** over 289 checks (max 2094ms) |
| prompt build → outbound request | **~1.6s**, no timing event covers it |
| tool result → next `llm/http-request` | **~2.7s** |

Sample cycle: transcript read 01:22:32.012 (6.8ms) → `compaction-check :elapsed-ms 1051` at 01:22:32.020 → read 01:22:33.635 → request 01:22:33.837.

Transcript reads are NOT the cost (isaac-vfg8's logging made that visible; Micah confirmed <2ms on smaller sessions).

### Cost 1: the check materializes messages it never reads (~1.28s/cycle)

`run-compaction-check!` (drive/turn.clj ~1021) calls `compaction/plan-compaction` (session/compaction.clj:160) on every cycle:

- `plan-compaction` → `compactables` (compaction.clj:254) → for EVERY history entry `->compact-message` (:219) or `tool-pair-message` (:242): `content->text`, `pr-str` of tool arguments, `transcript/truncate-tool-result` (cap = 0.3 × context-window × 4 = **1.26M chars** at a 1,050,000 window), then string concatenation of `"I called tool <name> with id <id> and arguments <args>. The tool result was: <entire result>"`.
- For a 300-entry session that materializes ~1.4MB of strings per cycle.
- `compaction-target` (:137) then uses only `(count entries)` and `(reduce + 0 (mapv :tokens entries))` — **stamped** values. `message-token-count` (:320) is literally `(or (:tokens entry) 0)`.
- Every built string is discarded. The `:message` values are needed only when compaction actually fires (chunking/summary prompts).

### Cost 2: the prompt build is untimed, and computes a token estimate nobody reads (~1.6s/cycle)

- The live turn path is `build-chat-request` → `api/build-prompt` → `prompt/build` (drive/turn.clj ~1450): rebuild + sanitize + filter the whole message list from the transcript, then `(assoc prompt :tokenEstimate (estimate-tokens prompt))` (llm/prompt/builder.clj:442).
- `estimate-tokens` (llm/api/protocol.clj) is a `content-chars` walk over the whole prompt — ~970KB on this session.
- `:tokenEstimate` has exactly ONE consumer: `compaction.clj:128`, inside `estimate-prompt-tokens`. That function logs `:session/token-estimate`, which fired **0 times** for isaac-work-1 across a 2h window — it is not on the turn path. So the walk is pure waste there.
- `turn/request-built` (drive/turn.clj ~1471) logs after the build with no elapsed field, which is why isaac-vfg8's cycle timing did not surface this second.

### Relationship to shipped work

- **isaac-4erp** (completed) removed the chars/4 estimates that drove the gauge and the drift ratio; `context-gauge` is now a running tally of provider tokens. It did NOT touch the message materialization inside `plan-compaction`, so that cost survived its cleanup.
- **isaac-vfg8** (completed) added the cycle timing that made the 1.28s check visible, but no timer covers prompt building.
- **isaac-3ueo** is unrelated (claude-CLI cold start).

## Proposal

1. **Split counting from message building.** `plan-compaction` needs counts + stamped `:tokens` only. Give it a cheap pass over `effective-history-entries` (pair tool-call/tool-result entries for counting without rendering them), and build `:message` values only when compaction actually fires. Removes ~1.28s from every cycle on every turn, fleet-wide.
2. **Time the prompt build.** Add `:build-ms` (and ideally `:messages-count`, `:prompt-chars`) to `turn/request-built`, so the remaining ~1.6s is measured rather than inferred.
3. **Stop computing `:tokenEstimate` on the turn path** — compute it lazily (delay) or only where a consumer exists.

## Acceptance

- A session with ≥300 history entries and ≥1MB transcript logs `session/compaction-check :elapsed-ms` an order of magnitude below today's (<100ms; today 1052–2094ms).
- Spec: `plan-compaction` produces correct `:compact-count`, `:tokens-before`, `:first-kept-entry-id` for both `:rubberband` and `:slinky` WITHOUT invoking message rendering (redef `->compact-message`/`tool-pair-message` to throw; the plan still computes).
- Actual compaction still works: existing compaction feature suites stay green (chunking uses rendered messages).
- `turn/request-built` carries `:build-ms`.
- No `estimate-tokens` walk on the live turn path: `session/token-estimate` remains the only place a full-prompt estimate is computed.
- `bb ci` green.

## Deferred — not decided

- **Incremental prompt build.** Only the tail changes each cycle, yet the entire message list is rebuilt and re-sanitized every time. Caching the built prefix keyed on the last transcript entry id could remove most of the remaining ~1.6s, but it interacts with compaction splices, turn framing injection, and nonce sanitization. Measure after items 1–3 before scoping.

## Scenarios (2026-09-16)

Committed `@wip` in isaac-agent `features/session/cycle_timing.feature` (the file isaac-vfg8 created for exactly this gap):

- **the prompt build reports its own elapsed time (isaac-3uy9)** — a one-tool-call turn logs `:turn/request-built` carrying `:build-ms` and `:messages-count`. Proposal item 2; makes the ~1.6s measurable instead of inferred.

Specs to write beside the existing `plan-compaction` describe (`spec/isaac/session/compaction_spec.clj:134`, which already asserts stamped `:tokens-before`):

- `plan-compaction` returns correct `:compact-count`, `:tokens-before`, and `:first-kept-entry-id` for BOTH `:rubberband` and `:slinky` with `->compact-message` and `tool-pair-message` redefined to throw. This is the real guard for proposal item 1: it fails the moment anything reintroduces per-cycle stringification. Reuse the fixtures in the "sliding compaction target" describe below it.
- Actual compaction still renders messages when it fires — covered by the existing compaction feature suites; keep them green rather than adding new ones.

Deliberately NOT permanent scenarios:

- **The speed itself.** "compaction-check under 100ms" is wall-clock and would be flaky in the suite. It is a one-time acceptance check on zanebot (below).
- **The removed `:tokenEstimate` walk.** A removal check, which per standing practice is bean acceptance, not a permanent test. NOTE: `cycle_timing.feature` already carries an absence block from isaac-4erp (`the log has no entries matching: :session/token-estimate :before/:check/:after`), so extending that block with the turn-path estimate is a one-line change if Micah prefers the precedent over the rule — not done unasked.

## Acceptance (runnable)

- `clojure -M:features features/session/cycle_timing.feature` (isaac-agent, after removing the `@wip` tag)
- `bb spec spec/isaac/session/compaction_spec.clj`
- `bb ci` green in isaac-agent
- One-time on zanebot after deploy, on a session with >=300 history entries and >=1MB transcript: `session/compaction-check :elapsed-ms` below 100ms (today 1052-2094ms, avg 1280ms over 289 checks), and `session/token-estimate` still the only place a full-prompt estimate is computed.
