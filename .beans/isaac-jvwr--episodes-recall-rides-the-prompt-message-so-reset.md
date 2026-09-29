---
# isaac-jvwr
title: Episodes recall rides the prompt message, so reset-mode crews receive it
status: todo
type: bug
priority: high
created_at: 2026-09-29T23:32:09Z
updated_at: 2026-09-29T23:46:45Z
---

Likely repo: **isaac-episodes**. Design: Micah + planner, 2026-09-29.

## Why

A `:context-mode :reset` crew's request carries only the last transcript
entry (`isaac-agent` `drive/turn.clj`, the three `(= :reset context-mode)`
builders). Episodes injects recall as its **own** user entry, appended in
`prepare-turn!` / `append-message!` before the prompt, so under reset the
recall is stored but never sent. Put a reset crew (Scrapper-style workers)
on episodes and it pays for seals, gists and embeddings while the model
never sees one recall. Blocks the Mixmaster trial.

## Design (agreed)

Episodes only; the drive, bridge and reset mode stay generic and learn
nothing about episodes.

- `prepare-turn!` still computes lineage + search recall on `:opened` /
  `:chained`, but instead of appending an entry it holds the block on the
  session (e.g. `:pending-recall`).
- The policy's `append-message!` (it already intercepts every append) puts
  the held block in front of the next **user** message's content, then
  clears it. The same happens on the first-append path that opens a
  container today.
- Reword the block's framing: "The current request is the message that
  comes after this one" is false once they share a message. Say the
  request follows the block.
- Full-context crews get the same shape; no reset special case.
- A turn that dies between prepare and append leaves the block held for
  the next user message. Acceptable.

Stored transcripts change shape: the opening user message holds recall
then the prompt. The two landed rows that asserted the bare prompt were
loosened to `#"(?s).*Set the watch rotation"` in the planning commit.
Sibling bean (seal skips recall) strips this block at seal time.

## Acceptance

- isaac-episodes `features/episodes/live.feature` — "a reset-mode crew receives its recall on the prompt message"
- Every other scenario in `features/episodes/live.feature` stays green (recall request-regex scenarios included).

feature-baseline: isaac-episodes ae6db393b3a1ee4dcd3f1cad20c178b7fff203ef
feature-blob: isaac-episodes features/episodes/live.feature 7e96ef19a21d5812b57771cc396873a4e1bc1ea7 373

## Worker conflict (2026-09-29)

Implemented on isaac-episodes `bean/isaac-jvwr` at 0d96194. `bb features features/episodes/live.feature` passes (21 examples, 138 assertions); focused specs pass (33 examples, 105 assertions). Full `bb ci` has 230 specs green but 1 failure among 93 feature examples: `features/recall/live_tools.feature:36-38` still requires the opening user message content to equal `Remember that wine talk?`. With the agreed recall-on-prompt design the actual value begins `[Recalled memory; not a request]...` and ends `Remember that wine talk?`. The bean's design explicitly loosened two bare-prompt assertions in `features/episodes/live.feature` but omitted this third one in `features/recall/live_tools.feature`. Worker may only remove `@wip` from feature files. Planner needs to loosen that row to a regex preserving the prompt suffix and rebaseline as appropriate before this can land. Gate at current branch reports PASS once the @wip is removed; suite is still red, so not landing.

feature-baseline: isaac-episodes 7df55cc1808eb2c97316d7f2def53612fbdb2495
feature-blob: isaac-episodes features/episodes/live.feature 7e96ef19a21d5812b57771cc396873a4e1bc1ea7 373
feature-blob: isaac-episodes features/recall/live_tools.feature 9968902ae47834c824c291edea752ac8503ab061 14

## Planner adjustment (2026-09-29, prowl@isaac-plan)

The third bare-prompt row is loosened. `features/recall/live_tools.feature` line 39 (was 38) now expects `#"(?s).*Remember that wine talk\?"`. The prompt suffix stays; a recall prefix is allowed. Scenario line 14, kept `@wip`.

Re-baselined onto isaac-episodes `7df55cc`. In force: `live.feature` blob `7e96ef19` line 373, and `live_tools.feature` blob `9968902a` line 14. The other live_tools scenario is not this bean's.

Rebase onto `7df55cc`. Drop `@wip` on both scenarios. Do not edit frozen scenario text.
