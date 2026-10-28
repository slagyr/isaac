---
# isaac-b1ir
title: 'isaac-dream: a crew dreams over its conversations and keeps private habits'
status: todo
type: feature
priority: normal
created_at: 2026-10-08T20:38:14Z
updated_at: 2026-10-28T07:51:11Z
parent: isaac-pcm3
---

Bean 1 of dreaming. New repo `isaac-dream` (`https://github.com/slagyr/isaac-dream`). Habits, `dream__propose`, operator CLI, always-on section at order 900, per-crew auto-approval. `@wip` scenarios are in that repo.

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
- **Episodes:** not required. The dream reads transcripts by time window through isaac-agent (isaac-d3qj), so chronicle and episodes crews both dream. Scenes drop who said each line; transcripts keep it. Recall stays what it is: lookup on a cue. Dreaming is the pass that needs no cue.

### Concerns

- **Cache, and how habits load (Micah, 2026-10-08).** The system text precedes the transcript, so any change to it re-bills the whole conversation. The habits section therefore must not vary by turn. Micah's model: habits load like skills. The system section holds a menu of triggers, stable between approvals, and grants a load tool; the model loads a habit's body when the conversation reaches its trigger. A per-person habit ("Cordelia's preferences") is one menu line; when Cordelia speaks the model loads it, and when someone else speaks next it may load theirs as well, so both sit in context. Loaded bodies arrive as tool results at the end of the transcript and do not disturb the cache. This needs nothing beyond isaac-3dnw as written (section text plus tool names). Three kinds follow:
  - always-on: short universal habits, printed in full in the section;
  - triggered: a menu line, body loaded by the model;
  - per-person: a triggered habit whose trigger is the speaker.
  Possible later hardening, not needed to start: the speaker is a fact, not a judgment, so with attribution (isaac-v403) the module could attach a person's habit to their first message in a session instead of relying on the model to load it. Also to settle: loaded habits are lost at compaction and must be reloadable, and a long roster makes a long menu.
- **Crew-to-crew teaching.** An instruction from another crew member carries no weight on its own, only through the contact it is acting for (`:for`). Otherwise agents grow habits with no human behind them.
- **Untrusted handles.** Weight follows the handle's authenticated flag, not the name on it. A forged From line must not speak with an executive's weight.
- **The soul is out of reach.** Dreaming produces habits only. At most the digest may suggest a soul change for a person to make by hand. Separately, `handbook__configure` can rewrite a crew's soul today with no review; check which crews hold that tool.
- **Strength scoring.** Hermes-style weighted scores are easy to cargo-cult. With a person approving, start with a count of reinforcing dreams and a last-reinforced date; add decay when there is data.

### Handbook (Micah, 2026-10-08)

The dream module's chapter must say which grants a dreaming crew needs (`session__list`, `session__read`, the propose tool) and what a dream looks like when one is missing, so a model can tell the operator why dreaming is broken instead of failing quietly.

### Borrowed from Hermes (NousResearch/hermes-agent#25309)

Stage then promote; a diary; a run gate with a lock; supersession instead of accumulation. Not borrowed: a capped flat memory file, and pausing the gateway to dream.


## Decisions (2026-10-28, Micah)

Bean 1 (this bean): habit records + `dream__propose` + operator approve/reject/retire/wipe/list + always-on system section at order 900. Not this bean: cron, watermark, diary, digest, skill-like load menu, per-person habits, contact `:weight`, `:from` on `session__read`, section character cap.

Storage is module state under the Isaac root (`dream/<crew>/…`), not a config table. Crews propose; only the operator CLI can change state to active/rejected/retired or wipe. Only `active` habits reach the model.

`isaac-3dnw` has landed; dreaming can plug into `:isaac.agent/system-sections`.

Decision (2026-10-28, Micah): auto-approval is in bean 1. Habit states stay proposed/active/rejected/retired. The promoter is a policy, default `:manual`. Per-crew `dream.<crew>.approval :auto` (config path, not a crew-schema key) promotes a valid propose to active in the same step. Reject/retire/wipe stay on the CLI. Scenario 12 locks that seam.

The repo exists with the common infrastructure: `https://github.com/slagyr/isaac-dream` — deps, `bb ci`, CI workflows, manifest (`:builtin? true`), an empty module, a stub handbook chapter, and the twelve `@wip` scenarios.

## Design (bean 1)

One file per habit under the Isaac root, not config:

```
dream/<crew>/<id>.edn
```

Record fields the scenarios lock: `:state`, `:lesson`, `:evidence`. Habit id is the filename stem (`moonflowers`, `geode`). Propose may choose the id; assertions match on state/lesson/evidence.

States: `proposed` | `active` | `rejected` | `retired`. Only `active` habits reach the model.

Tool: `dream__propose` (grant `:dream/propose`, not granted by default). Args: `lesson` (string) and `evidence` (vector of session names). Missing evidence is a tool error whose message mentions evidence; no habit is stored.

CLI (operator only; `:hosted true`):

```
isaac dream list --crew <id>
isaac dream approve <habit-id> --crew <id>
isaac dream reject <habit-id> --crew <id>
isaac dream retire <habit-id> --crew <id>
isaac dream wipe --crew <id>
```

System section: contribute on `:isaac.agent/system-sections` at **order 900**. Label: `These are lessons this crew learned`. Only that crew's `active` habits. Sits in the cached prefix (`cache_control.type` ephemeral). Proposed habits and other crews' habits do not appear.

Promoter: default `:manual`. `dream.<crew>.approval :auto` promotes a valid propose to `active` in the same step.

## Notes for the implementer

- Manifest: `:isaac.agent/tools {:dream/propose …}`, `:isaac.agent/system-sections` with `:order 900`, `:isaac/cli {:dream {:usage "dream <subcommand> [options]" :hosted true …}}`, `:isaac.config/schema` for `dream` keyed by crew id with `:approval` one of `:manual` / `:auto` (default `:manual`).
- Propose scenarios queue a `tool_call` then `the user sends`. Do not switch them to `When the tool "…" is called` — that step only registers builtins.
- `the isaac EDN file` writer stores `state | proposed` as the string `"proposed"` and `evidence | ["greenhouse"]` as an EDN vector. Coerce on read so seeded files and module writes both work. The new matching step should use the shared matcher DSL (string `proposed` equals keyword `:proposed`).
- `the stdout matches:` skips its first table row as a header; the list scenario already leads with `| pattern |`.
- Reject/retire: assert CLI exit 0 immediately after `isaac is run with`, then send, then the prompt assertion. Do not reorder those Thens.
- Fill `resources/isaac/dream/handbook.md`: what a habit is, grants (`:dream/propose`, and `session__list` / `session__read` for a later dreaming turn), propose args, promoter, CLI the crew cannot run, storage path, section label. Crews only; no source walkthroughs.
- Update the README's "What's here".
- Pins: `isaac-foundation` and `isaac-agent` match what `isaac-agent` main pins at planning time (`edc1848` / `5b9a667`). Keep them coherent if you bump.
- `isaac` `modules.edn` gains `:isaac.dream` at the **landed** sha, not before.

## New step invented (implement in isaac-dream)

`Then crew {id} has a habit matching:` — table of `path | value` against that crew's habit files. Existing EDN-file assertions cannot see a habit the tool just wrote under a generated id.

## Likely repo scope

`isaac-dream`, plus one registry entry in `isaac` `modules.edn` at land.

## Out of scope

Cron, watermark, diary, digest, skill-like load menu, per-person habits, contact `:weight`, `:from` on `session__read`, section character cap, `isaac dream run`.

## Acceptance

Run from `isaac-dream`, with `@wip` removed from all three feature files:

- `bb features features/habits/propose.feature:19`
- `bb features features/habits/propose.feature:32`
- `bb features features/habits/propose.feature:45`
- `bb features features/habits/propose.feature:54`
- `bb features features/habits/operator.feature:18`
- `bb features features/habits/operator.feature:40`
- `bb features features/habits/operator.feature:53`
- `bb features features/habits/operator.feature:70`
- `bb features features/habits/operator.feature:87`
- `bb features features/habits/section.feature:18`
- `bb features features/habits/section.feature:29`
- `bb features features/habits/section.feature:41`
- `bb ci` green locally, and the `CI Tests` workflow green on `main` after landing.
- Unit specs cover propose (evidence required, grant required, auto vs manual promoter), CLI state transitions, wipe isolation, and the section (active only, this crew only, order 900 / labelled).
- `resources/isaac/dream/handbook.md` documents habits, grants, promoter, CLI, storage, and the section label.
- `isaac` `modules.edn` gains an `:isaac.dream` entry at the landed sha, with a one-line description.

feature-baseline: isaac-dream c9062a910428e5560666fc4ab90f9c09cea442a1
feature-blob: isaac-dream features/habits/propose.feature c53b9928d9c1c612f7f3466be6d987d202945c06 19,32,45,54
feature-blob: isaac-dream features/habits/operator.feature 468e83d92b3d3c3d1db77ee7b138bbc9a57a9933 18,40,53,70,87
feature-blob: isaac-dream features/habits/section.feature cc306670e7416532d47e52acd8f0b79b6957bb7f 18,29,41
