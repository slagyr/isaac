---
# isaac-jvwr
title: Episodes recall rides the prompt message, so reset-mode crews receive it
status: todo
type: bug
priority: high
created_at: 2026-09-29T23:32:09Z
updated_at: 2026-09-29T23:32:09Z
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
