---
# isaac-ozv9
title: 'claude tool turns live: system-prompt protocol authority + witnessed roundtrip'
status: completed
type: feature
priority: high
created_at: 2026-07-12T18:24:40Z
updated_at: 2026-07-12T18:58:49Z
---

## Goal

The claude CLI provider executes real Isaac tool roundtrips. The plumbing already works (kn7y: drive sends tool turns non-stream with tools in-request; adapter serializes defs and parses <tool_call>); what fails is AUTHORITY: with --tools "" the claude CLI knows its own harness has no tools, so it answers 'the exec tool isn't available' instead of honoring the text protocol embedded mid-prompt. Live-observed 2026-07-12 (prompt-default session; grok executed the same request fine).

## Design (for the local agent, on a FRESH pull of isaac-agent main — kn7y is merged/deployed; do not re-fix flags/process/auth)

1. Carry the protocol instruction with SYSTEM authority: pass Isaac's system text plus the tool-protocol contract via --system-prompt (or --append-system-prompt) instead of flattening into the user prompt. Contract wording along the lines of: 'You have no built-in tools. To act, emit <tool_call>{json}</tool_call> exactly; the harness executes and returns results.' Iterate wording against the real binary — this is prompt engineering with a live target.
2. Keep the messages->prompt-text history flattening for conversation turns; only system+protocol moves to the flag.
3. Acceptance is a WITNESSED roundtrip, not predicted output: run a real tool turn whose result is unguessable (e.g. exec: date +%s%N or read of a nonce file) and verify the session transcript contains the toolCall AND toolResult entries with the reply derived from the result. (2026-07-12 lesson: an echo test false-positived — claude predicted the output without executing.)
4. Stub scenarios: argv gains the system-prompt flag (update tables to the new correct behavior); add a scenario asserting the protocol contract text is in the system prompt, not the user prompt.
5. Cleanup (plan step 8): stale claude-code references in docs/bean notes; note on isaac-auws that the real path works.

## Out of scope

Streaming tool calls (:stream-supports-tool-calls stays false); work-band crew adoption (follows acceptance-by-use after this lands).


## Live acceptance witnessed (planner, 2026-07-12, zanebot agent 0.1.17)

Real tool roundtrip through the deployed provider: claude emitted
toolCall exec `date +%s%N`, harness executed, reply was the exact
nanosecond result (1783885115767366000) — transcript-verified toolCall +
toolResult entries. The claude lane is tool-capable in production.
