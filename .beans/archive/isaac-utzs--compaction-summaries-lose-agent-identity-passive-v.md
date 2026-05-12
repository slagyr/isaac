---
# isaac-utzs
title: "Compaction summaries lose agent identity (passive voice, no I/user attribution)"
status: completed
type: bug
priority: normal
created_at: 2026-04-30T01:11:31Z
updated_at: 2026-05-05T22:44:50Z
---

## Description

After compaction, the resulting summary uses passive voice and
omits agent/user attribution. When the agent re-reads the summary
on a later turn, it cannot tell whether described actions were
done by itself or by the user.

## Evidence

zanebot session 'tidy-comet', compaction entry summary:

  > Discussed Hakkō-ryū... Then debugged isaac-live Discord gateway
  > disconnects... Inspection of `src/isaac/acp/ws.clj` found the bug
  > ... Files shown/edited in the conversation included: ...

\"Discussed\", \"debugged\", \"Inspection found\", \"shown/edited\" —
all passive. No \"I\" or \"the user\". Marvin re-reading this on a
later turn (compounded by orphan tool calls — see related bead) said
he thought the user had done all the work. Reasonable inference
from his point of view, but wrong.

## Fix

Update the compaction prompt to instruct the LLM to:
- Use first person (\"I\") for actions taken by the agent.
- Refer to the user as \"the user\" (or by name if obvious).
- Preserve the actor for each action: who asked, who did, who
  verified.

Compaction prompt source: src/isaac/context/manager.clj
compaction-request fn (or the system message it builds).

## Spec

A regression scenario can assert the compaction PROMPT (sent to
the LLM, not the resulting summary) contains attribution
instructions. Example:

  Scenario: compaction prompt instructs first-person attribution
    Given a session with prior turns
    When compaction runs
    Then the last LLM request's system prompt contains \"first person\"
    And the last LLM request's system prompt contains \"the user\"

Asserting on the *generated summary* is unreliable (LLM output
varies). Asserting on the *prompt instructions* is deterministic.

Or: extend the existing 'compaction targets only the oldest messages'
scenario with an assertion about the request shape.

## Definition of done

- Compaction prompt instructs first-person + user-attribution.
- A spec asserts the prompt shape includes attribution language.
- Manual smoke: compact a long conversation and read the summary —
  it should say \"I did X\" / \"the user asked Y\".
- bb features and bb spec green.

## Related

- isaac-???? (orphan toolCalls bead) — compounds with this bug.
  When tool calls have no results AND the summary is passive, the
  agent has no narrative thread to reconstruct what happened.

## Notes

Verification failed: automated checks are green and the compaction prompt/spec coverage is present, but the bead definition of done still requires a manual smoke check of a real compacted summary showing explicit 'I ...' / 'the user ...' attribution. That manual summary evidence is not present here.

