---
# isaac-iozn
title: "Chunked compaction budget is inverted; grover stub doesn't enforce context window"
status: completed
type: bug
priority: high
created_at: 2026-04-28T21:39:24Z
updated_at: 2026-04-29T00:35:58Z
---

## Description

isaac-hxrp's chunked compaction shipped with two compounding defects
that let it appear to work in tests while still failing in production.

## Defect 1: chunk-budget formula is inverted

src/isaac/context/manager.clj

  (defn- chunk-budget [context-window]
    (max 1 (* 2 context-window)))

This makes each chunk 2x the model's context window — bigger than the
LLM can read in one shot. For zanebot's 278528-token window the
budget is 557056. Chunked compaction therefore produces chunks that
themselves overflow and trigger the same :api-error from openai-codex
as one-shot compaction.

Likely intent: a fraction of the window, e.g. (quot context-window 2)
or (- context-window soul-tokens) with explicit safety margin for
the system prompt + tail headroom.

## Defect 2: grover stub does not enforce context window

The scenario "compaction splits the head when it exceeds the context
window in one shot" (features/context/compaction.feature) passes
despite the inverted formula because grover (test stub) accepts any
prompt size:

  context-window=60, total-tokens=200
  chunk-budget = 2*60 = 120
  -> 2 chunks of 100 tokens each
  -> each chunk is bigger than the 60-token window
  -> grover accepts anyway, test green
  -> openai-codex rejects, production red

The stub should optionally enforce a context-window cap and return
a context-length error when the prompt exceeds it. With that in
place, the chunked scenario would have caught defect 1 immediately.

## Fix

1. Correct chunk-budget so chunks fit in the real window with
   headroom for soul and tail. Pick a formula and add a comment
   explaining the math.
2. Teach grover to reject prompts exceeding a configured
   context-window. Default behavior unchanged for existing scenarios
   that don't care; new flag like {:enforce-context-window true}
   on the model entity, or always-on if the model has
   :context-window set.
3. Revise the chunked-compaction scenario's token counts/queued
   responses to match the corrected chunk count under the new
   formula. Verify it actually fails BEFORE the fix once grover
   enforces, then green AFTER the fix.
4. Live verification on zanebot: clever-signal session should
   either compact-via-chunks successfully or hit the surrender
   threshold and disable. Today neither happens.

## Definition of done

- chunk-budget formula corrected with a comment justifying the math
- grover (or an explicit test stub) rejects prompts above
  context-window when configured to do so
- features/context/compaction.feature chunked scenario updated and
  passes; verified to fail before the formula fix
- bb features and bb spec green
- Manual: zanebot's clever-signal session either recovers or
  surrenders cleanly within max-compaction-attempts turns

## Related

- isaac-hxrp (compaction recovery) closed but the chunking half
  is functionally broken; this bead completes that work.

## Notes

Verified current main satisfies the bead acceptance criteria: chunk-budget is corrected, grover enforces context-window when configured, the chunked compaction feature scenario passes, and targeted bb spec / bb features coverage are green. No new code changes were necessary in this session.

