---
# isaac-b1ir
title: 'isaac-dream: a crew dreams over its conversations and keeps private habits'
status: draft
type: feature
priority: normal
created_at: 2026-10-08T20:38:14Z
updated_at: 2026-10-08T20:38:14Z
parent: isaac-pcm3
blocked_by:
    - isaac-3dnw
---

DESIGN DRAFT. Rough intent only; Micah is also discussing dreaming with another agent, so reconcile with that thread before writing scenarios. Part of the prompts-and-habits epic. Blocked by the system-section berth.

## Idea

A crew periodically reviews its own conversations ("dreams") and distills them into **habits**: guidance that applies only to that crew. Habits reach the model through the system-section berth, as the dream module's own section.

## Rulings (Micah, 2026-10-08)

- Habits are kept separate from skills and the prompt catalog. They do not follow the `SKILL.md` structure and the prompt module does not know they exist.
- The name is habits.
- A crew's habits are private to that crew.

## Proposed shape of a habit

Records owned by the module, not markdown files:

- the lesson: a sentence or two of guidance;
- when it applies: a short trigger, so narrow habits can be shown only when relevant;
- evidence: the sessions it came from;
- strength: raised when a later dream reaches the same conclusion, decayed when it does not;
- dates: first formed, last reinforced.

Each dream can reinforce, merge or retire habits. The system section is the strongest few that fit a budget.

## Constraints

- **Labelled as self-derived.** The section says these are lessons the crew learned, so the model weighs them below the soul and project rules.
- **Budgeted.** Habits cost tokens on every turn. The section has a cap.
- **Last in the system text.** Habits change daily; the stable prefix ahead of them stays cached.
- **Private from other crews, not from the operator.** There must be a way to read, and to wipe, what a crew has dreamed.
- **Injection persistence.** Dreaming turns conversation text into standing instructions. Text a stranger wrote in an email or a Discord message could be distilled into a permanent habit. Evidence links are what make that reviewable; consider excluding or down-weighting untrusted-origin turns.
- **Module-owned state.** Habit records live in the module's own storage, not on session records.

## Open questions

- What triggers a dream: a cron job, idle time, or an explicit command.
- Which model dreams, and on whose budget.
- How this relates to episodic memory and recall (isaac-51xy), which also reads across a crew's past conversations.

## Likely repo scope

A new `isaac-dream` module. Needs nothing from `isaac-agent` beyond the system-section berth.
