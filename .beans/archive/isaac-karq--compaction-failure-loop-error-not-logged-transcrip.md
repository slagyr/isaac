---
# isaac-karq
title: "Compaction failure loop: error not logged, transcript grows unbounded"
status: completed
type: bug
priority: high
created_at: 2026-04-28T17:44:46Z
updated_at: 2026-04-28T17:56:41Z
---

## Description

Compaction failures log :session/compaction-failed without including
the underlying error from ctx/compact!. On zanebot the failure repeats
every turn (transcript climbed 345k -> 1.02M tokens, context window
278528) but no diagnostic information surfaces in logs.

## Scope (this bead is logging-only)

src/isaac/drive/turn.clj:338-339

  (if (:error result)
    (log/error :session/compaction-failed :session key-str)

Change to include `:error (:error result)` (and any other relevant
keys from result) so the underlying provider/runtime error is visible.

ctx/compact! returns the error-bearing response per
src/isaac/context/manager.clj:151. The shape of (:error result) needs
to be checked there to decide which fields to log.

## Out of scope (separate follow-up bead)

The recovery strategy for "compaction itself can't fit in the context
window" — likely the actual failure mode at 1M tokens — is a behavioral
change that needs its own Gherkin feature. File once we have the real
error message in logs and know what we're defending against.

## Definition of done

- compaction-failed log entries include the underlying error
- a unit spec covers the new log fields when compact! returns :error
- bb spec + bb features green

## Notes

Implemented logging-only fix in src/isaac/drive/turn.clj to include underlying compaction :error and :message on :session/compaction-failed. Added unit coverage in spec/isaac/cli/chat_spec.clj. Verification: bb spec passes. bb features has one unrelated failure already covered by ready bead isaac-aept: ACP compaction notification expects agent_thought_chunk but currently gets agent_message_chunk.

