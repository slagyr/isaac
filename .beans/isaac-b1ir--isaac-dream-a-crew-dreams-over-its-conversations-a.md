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

## Reconciliation with the second dreaming thread (planner, 2026-10-08)

Added from Micah's other dreaming conversation the same day. Proposed revisions; the sections above are unchanged.

### Rulings from that thread (Micah)

- **Approval first.** Micah approves every change until he trusts the process; automation comes later. So a habit has a state: proposed, active, rejected, retired. Only active habits reach the model. Approval is an operator action the crew cannot perform on itself (CLI first). Reinforcing an active habit needs no approval; new habits, rewording and merges do.
- **Everyone who talks to the crew can teach it**, weighted by who they are. The weight comes from the contact when `isaac-contacts` is installed (isaac-zt1x).
- **Works with or without contacts.** With attribution (isaac-v403) evidence names who said it and a habit may be scoped to one contact. Without it every habit is global and evidence is just the scene.
- **Dreaming is a module plus a prompt.** The prompt holds the judgment: what is a lesson, themes, where it belongs, what it supersedes. The module holds what must be deterministic or out of the crew's reach.

### What the module owns beyond the habit records

- The watermark, a run gate (enough time and enough new activity since the last dream) and a lock against double runs.
- A `dream__propose` style tool: the dreaming turn submits habits with evidence; the module validates and stores them as proposed.
- Approve / reject / retire / wipe for the operator.
- A diary: one entry per dream saying what was read, proposed, reinforced and retired.
- A digest to the operator after each dream, over the attention comm.

### Answers proposed for the open questions

- **Trigger:** a cron job in a fresh session. Cron already defaults to a new session per fire and can set the model with `with-model`, which answers "which model, whose budget" in config. The dream prompt ships with the module; if it is offered as a `/dream` command, that needs the slash-provider reshape (isaac-quji) as well.
- **Episodes:** the dream reads sealed scenes by time window (isaac-d3qj). Recall stays what it is: lookup on a cue. Dreaming is the pass that needs no cue.

### Concerns

- **Cache, and how habits load (Micah, 2026-10-08).** The system text precedes the transcript, so any change to it re-bills the whole conversation. The habits section therefore must not vary by turn. Micah's model: habits load like skills. The system section holds a menu of triggers, stable between approvals, and grants a load tool; the model loads a habit's body when the conversation reaches its trigger. A per-person habit ("Chris's preferences") is one menu line; when Chris speaks the model loads it, and when Micah speaks next it may load his as well, so both sit in context. Loaded bodies arrive as tool results at the end of the transcript and do not disturb the cache. This needs nothing beyond isaac-3dnw as written (section text plus tool names). Three kinds follow:
  - always-on: short universal habits, printed in full in the section;
  - triggered: a menu line, body loaded by the model;
  - per-person: a triggered habit whose trigger is the speaker.
  Possible later hardening, not needed to start: the speaker is a fact, not a judgment, so with attribution (isaac-v403) the module could attach a person's habit to their first message in a session instead of relying on the model to load it. Also to settle: loaded habits are lost at compaction and must be reloadable, and a long roster makes a long menu.
- **Crew-to-crew teaching.** An instruction from another crew member carries no weight on its own, only through the contact it is acting for (`:for`). Otherwise agents grow habits with no human behind them.
- **Untrusted handles.** Weight follows the handle's authenticated flag, not the name on it. A forged From line must not speak with an executive's weight.
- **The soul is out of reach.** Dreaming produces habits only. At most the digest may suggest a soul change for a person to make by hand. Separately, `handbook__configure` can rewrite a crew's soul today with no review; check which crews hold that tool.
- **Strength scoring.** Hermes-style weighted scores are easy to cargo-cult. With a person approving, start with a count of reinforcing dreams and a last-reinforced date; add decay when there is data.

### Borrowed from Hermes (NousResearch/hermes-agent#25309)

Stage then promote; a diary; a run gate with a lock; supersession instead of accumulation. Not borrowed: a capped flat memory file, and pausing the gateway to dream.
