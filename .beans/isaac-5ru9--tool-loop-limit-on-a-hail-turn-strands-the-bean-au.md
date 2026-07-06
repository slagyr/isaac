---
# isaac-5ru9
title: 'Tool-loop limit on a hail turn strands the bean: auto-continue instead of completing'
status: unverified
type: bug
priority: normal
created_at: 2026-07-06T21:23:48Z
updated_at: 2026-07-06T22:12:00Z
---


## Gap

Observed live (2026-07-06, isaac-gnji, work-1, hail 1c26c11c): a work turn hit the drive's tool-loop limit after 143 tool executions mid-investigation. The model's forced final message — "I ran several tools but did not reach a conclusion before hitting the tool loop limit. Ask me to continue if you want me to keep digging" — is a question to an empty room on an unattended hail turn. The runtime treated the turn as complete: delivery → delivered/, session gate opened, the next delivery took the session, and the bean sat claimed-but-stranded (in-progress, no pending hail, no signal) until a human noticed ~30 minutes later.

Existing nets correctly do NOT catch this: guard-empty-terminal-response (the response is not empty) and isaac-k4mf (no error). The gap is that a loop-limit ending is INCOMPLETE by definition, and the model cannot reliably self-rescue — by the time it knows it is capped it may get no further tool calls to hail its own continuation.

## Proposed fix

When a delivery-driven turn ends because the tool-loop limit was reached, the turn result carries that fact (e.g. :ended-by :tool-loop-limit) and the delivery worker re-queues the delivery as a continuation instead of completing it: same thread, no attempts increment (the work is neither poison nor weather — it is unfinished), a bounded continuation count (config, e.g. 3) so a truly non-converging bean eventually surfaces to a human, and a notification (e.g. "<bean-id> 🔁 turn hit tool-loop limit, continuing (N/3)").

Comm/cron turns: out of scope here — the limit message reaches an actual user who can reply.

## Notes

- Mitigation deployed meanwhile: hail-bean-work skill now instructs "never end a turn in limbo" (send continuation hail early). Helps voluntary endings; cannot fix involuntary cap endings — hence this bean.
- The session transcript preserves all prior investigation (compaction permitting), so continuations resume warm.

## Design refinements (2026-07-06, approved)

- **R1**: `:max-loops` (tool_loop.clj, default 100) becomes crew-configurable as `:tool-loop-max` — same layering as compaction config. Signal already exists: `:loop-request? true` on the loop result (tool_loop.clj:80); the drive propagates it into the turn result; the worker reacts.
- **R2**: `:max-continuations` (config `:hail-settings`, default 3); exhaustion dead-letters with distinct `:reason :continuations-exhausted` — failed/ triage distinguishes poison (:exhausted) from won't-converge.
- **R3**: notification rides the continuation delivery's prompt ("continuation N of 3; send the 🔁 at-a-glance, then continue — do not restart"), keeping comms model-side; the worker only logs `:hail/continuation-queued` (warn).

## Scenarios (approved one-by-one, 2026-07-06)

- isaac-hail `features/delivery.feature`: continuation re-queue (line 544), continuations-exhausted dead-letter (line 583).
- isaac-agent `features/tool/tool_loop_limit.feature`: crew-level tool-loop-max knob (line 15).

## Acceptance

- [x] `bb features features/delivery.feature:544` green (isaac-hail)
- [x] `bb features features/delivery.feature:583` green (isaac-hail)
- [x] `bb features features/tool/tool_loop_limit.feature:15` green (isaac-agent)
- [x] Continuation prompt carries the continuation notice (N of M + do-not-restart + 🔁 instruction)
- [x] Comm/cron turns unchanged (limit message reaches a real user)
- [x] Full suites green in both repos; @wip removed from all three scenarios

## Handoff

- isaac-agent `6336cb4`: crew `:tool-loop-max`, `:ended-by :tool-loop-limit`, loop-limit reply hardening
- isaac-hail `1fc9fbd`: continuation re-queue / continuations-exhausted dead-letter; agent pin bumped