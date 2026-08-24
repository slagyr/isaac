---
# isaac-q8tr
title: 'Compaction keeps the turn''s request: mid-turn compaction no longer strands bean work'
status: todo
type: task
created_at: 2026-08-24T01:41:54Z
updated_at: 2026-08-24T01:41:54Z
---

Defect found 2026-08-23 auditing stalled worker turns (da0r x2, bbov, mjr4, h5dk continuations). Fixed locally by the planner at Micah's request (A+B+C), shipped as isaac-agent 0.1.37 (d1ee132).

## Diagnosis (2026-08-23, planner; transcripts isaac-work-1 / isaac-work-2 on zanebot)

1. Work sessions run `:rubberband`; `compaction-target` sets `:first-kept-entry-id nil` — everything is compacted, INCLUDING the current turn's originating hail message.
2. The summarizer prompt ("Review this conversation. Call memory__write... then produce a concise summary") was narrated by the model as the user's request: work-2's summary literally opened "The user asked me to review the conversation and record durable memory."
3. That summary is injected as a user-role message — the only ask the model can see. With hail-bean-work forbidding self-continuation, the worker concluded "incoming was persist-notes, not implement", wrote a do-not-resume memory note, and stopped. Bean left claimed + dirty. Every long bean turn died at its first compaction; the memory notes made the next hail worse.

## Fix (isaac-agent d1ee132, release 0.1.37)

- A. Compaction entry carries `:turnRequest` — the most recent user message among the compacted entries when it is the turn being served (no plain assistant reply after it, no newer user message in the kept tail; chained compactions propagate it). Bounded at 4000 chars.
- B. Summarizer prompt: "This instruction is not part of the conversation: never narrate it or attribute it to the user. Say what the user asked, what was done, and what remains unfinished."
- C. Prompt builder renders the summary as `[Compacted history; not a new request]` + summary + `Pending request, verbatim:` + the request. Summarizer INPUT for chained compactions stays the bare summary (keeps tiny-window fixtures and chunking math unchanged).
- Specs: compaction_spec (turnRequest recorded; prompt wording), builder_spec (framing + re-seed order). Scenario revisions: prompt_building.feature (rendered summary now framed), context_management.feature knife-edge fixture threshold 0.8 -> 0.9 (13-token margin; +23 tokens of framing tipped a second compaction).
- Regression: full `bb spec spec` and `bb features` match pristine main exactly (24 spec / 13 feature failures pre-existing from in-flight config-schema work).

## Acceptance (regression feature, 2026-08-24)

Live (not `@wip`) — product fix `d1ee132` already makes it green. Red on `84c0a7f` (hail count 0 after rubberband splice). New steps: none.

```
bb features features/session/compaction_mid_turn.feature:12
```

Scenario: `rubberband mid-turn still carries the originating hail`
- `Then session "mid-rb" has compaction`
- `And the last LLM request mentions "Don't lose this ask." exactly 1 time`
- `And the last LLM request mentions "HUGE-LEMON-PAYLOAD" exactly 0 times`

Do not drop this scenario. It is the turn-loop contract 92h's stubbed spec never had.

## Follow-ups

- Rehail stalled beans after deploy: isaac-bbov, isaac-da0r, isaac-mjr4 (work-1 checkout still carries da0r's uncommitted TDD; work-2 carries mjr4's).
- Worker memory notes "resume only on explicit hail" are now stale advice — harmless once turns survive compaction.
- Design note for isaac-tdgt/bbov: the deeper cure is worksite-aware turn finalization; this fix removes the trigger, not the fragility of long single turns.
