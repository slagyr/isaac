---
# isaac-p36b
title: 'isaac-dream: code-run dreams on a crew''s schedule — digest, diary, reinforce, supersede'
status: completed
type: feature
priority: normal
created_at: 2026-10-29T16:52:25Z
updated_at: 2026-11-01T19:47:26Z
parent: isaac-pcm3
---

Bean 2 of dreaming (bean 1: isaac-b1ir). Bean 1 stores habits and shows active ones in the system prompt, but nothing makes a crew dream. This bean adds the dream: code, run on a schedule, that hands a crew's recent transcripts to a model once, then stores what the model found and tells the operator.

Status: draft. Shape agreed with Micah 2026-10-29 / 11-01. Next: `@wip` scenarios in isaac-dream, then baseline.

## Rulings (Micah)

- **Code runs the dream, not a crew.** No prompt wakes a crew and asks it to dream itself, and no dream tools are needed. Kicking off is all code; the model is used once, to analyze. (10-29)
- **No slash command.** (10-29)
- **Schedule, not interval.** Dreams want downtime, usually at night, so the trigger is a cron expression on the shared scheduler, not "every N hours". A busy crew can still use an hourly expression. A dream also needs at least one session with new messages since the last dream. (11-01)
- **Dream config is per crew.** It lives on the crew, with crew-wide defaults under `defaults.crew`. No root-level `dream` key. This replaces bean 1's root `dream.<crew>.approval`. (11-01)
- **Transcript only.** Tool results never reach the dream, so emails, fetched pages and the like are out. Messages in the transcript are trusted by virtue of being there. (10-29)
- **Superseded habits retire.** Approving a habit that supersedes others retires them. (10-29)
- **Dream records stay out of config and out of the crew's own directory**: they live under `dream/` in the Isaac root. (11-01)

## Config

On the crew (`crew.<id>.dream`), defaulted from `defaults.crew.dream`. **Dreaming is off by default and turned on per crew** (Micah, 11-01):

- `enabled` — boolean, default false. A crew dreams only when its resolved `enabled` is true. Allowed in `defaults.crew.dream` too (fleet-wide on), but nothing sets it for you.
- `schedule` — 5-field cron expression, evaluated in the configured time zone (same as cron jobs). No built-in default: an enabled crew with no resolved schedule is a config validation error naming the crew.
- `approval` — `:manual` (default) or `:auto`. Moves here from bean 1's root `dream.<crew>.approval`. Dream is not deployed anywhere yet, so no live config needs migrating.
- `model` — model used for the dream. Default: the crew's model.
- `digest` — `{:comm … :to …}` override. Default: the existing `:attention :notify` target.

Precedent: isaac-episodes adds `crew.<id>.episodes` the same way.

Example:

```clojure
;; isaac.edn — shared settings, nobody dreams yet
:defaults {:crew {:dream {:schedule "0 3 * * *" :approval :manual}}}

;; config/crew/zane.edn — Zane on
{:model "claude" :soul "…" :dream {:enabled true}}

;; a busy crew: hourly, cheaper model
:dream {:enabled true :schedule "0 * * * *" :model "grok-fast"}
```

`dream__propose` and the habits section (bean 1) ignore `enabled`; they follow the tool grant and active habits.

## The dream

The module registers one task per scheduled crew on the shared scheduler (`nexus :scheduler`, `:kind :cron` with `:expr` and `:zone`), the same scheduler isaac-cron uses. It does not create cron jobs: those send prompts.

When a task fires, or when the operator runs `isaac dream run --crew <id>`:

1. Take the crew's lock. If it is held, stop. The lock expires after a timeout so a crashed dream does not block the next one.
2. Find the crew's sessions with messages since the watermark (the end of the last dream). None: release the lock, write nothing, send nothing.
3. Build the window: user and assistant message text only, with tool calls and tool results dropped (the same filtering as `session__read`, isaac-d3qj). If it is too big for one model call, take the oldest part that fits.
4. One model call: the dream prompt that ships with the module, the crew's soul, its current habits with ids, and the window. The model answers in a fixed structure: new habits (a short slug id, the lesson, evidence sessions, and the habits it supersedes), and reinforcements (an active habit id plus evidence sessions).
5. Code validates every item. Evidence must name sessions in this window that belong to this crew; anything else is dropped and noted in the diary. New habits are stored `proposed`, or `active` under `:auto`. Reinforcing appends a `:reinforced` history event with its evidence to an active habit, with no approval (ruling, 2026-10-08).
6. Write the diary entry, move the watermark to the end of what was read, release the lock, send the digest.

A failed model call leaves the watermark where it was, writes a diary entry saying it failed, and sends a digest saying so.

`isaac dream run` runs the same steps now, ignoring the schedule and `enabled` (so a disabled crew can be tried by hand), but not the new-messages rule.

## Traceability and versioning (Micah, 11-01)

**Every habit traces back to the messages that caused it.**

- Evidence is session plus transcript entry ids, not session names: `[{:session "greenhouse" :entries ["e-7f3a" "e-7f3c"]}]`.
- The dream window labels each line `<session>#<n>` (e.g. `greenhouse#1 2026-04-21T14:00:00Z user: …`). The model cites labels; code maps them to entry ids and drops any label not in the window.
- Each habit records the dream that made it (`:dream`, the diary entry's timestamp).
- `isaac dream show <habit-id> --crew <id>` prints the habit, its history, and the quoted evidence lines read back from the transcripts. Evidence stores ids, not copies. A deleted session shows as missing evidence, not an error.
- Live `dream__propose` (bean 1) cites the calling session and the current message. Bean 1's session-name evidence migrates to the new shape.

**Habits are versioned by supersession plus history, not version numbers.**

- A lesson's text never changes in place. Rewording or merging is a new habit that `:supersedes` the old ones; approving it retires them and sets their `:superseded-by`.
- Each habit keeps an append-only `:history` of events with time and source: `:proposed`, `:approved`, `:rejected`, `:retired`, `:reinforced` (with its new evidence), `:superseded`. Reinforcement count and last-reinforced date are derived from it.
- `isaac dream approve <id> --lesson "…"` approves a reworded copy: a new active habit that supersedes the proposal.

Record shape:

```clojure
{:id         "moonflowers-first"
 :state      :active
 :lesson     "Water the moonflowers before anything else."
 :evidence   [{:session "greenhouse" :entries ["e-7f3a" "e-7f3c"]}]
 :supersedes ["moonflowers"]
 :dream      "2026-04-22T03:00:00Z"
 :history    [{:at "2026-04-22T03:00:00Z" :event :proposed :by {:dream "2026-04-22T03:00:00Z"}}
              {:at "2026-04-22T08:14:00Z" :event :approved :by :operator}]}
```

## Digest

A short message after a dream, sent only when there is something to act on: new proposals, or a failed dream. A dream that finds nothing writes its diary entry and sends nothing. Example:

> Zane dreamed over Oct 28 09:00 – Oct 29 03:00 (6 sessions).
> Proposed `check-ci-before-closing`: "Wait for CI on main before closing a bean." Evidence: bean-isaac-b1ir. Supersedes: none.
> Reinforced `push-before-hail` (now 4×).
> `isaac dream approve check-ci-before-closing --crew zane`

Goes to `crew.<id>.dream.digest` if set, else `:attention :notify`.

## Storage

Under the Isaac root, beside bean 1's habit files: `dream/<crew>/state.edn` (watermark, lock, last run) and `dream/<crew>/diary/<timestamp>.edn` (window, sessions read, what was proposed, reinforced, dropped, and any failure).

## Operator CLI

- `isaac dream run --crew <id>` — dream now.
- `isaac dream list` with no `--crew` lists every crew; `--state proposed` filters. Each row shows id, state, lesson, evidence, and supersedes.
- `isaac dream diary --crew <id>` — recent dreams.
- `approve` retires the habits the approved habit supersedes.
- An unknown crew is an error, not an empty list.

## Stays from bean 1

`dream__propose` stays for live "remember this" moments. Dreaming does not use it. It gains the same evidence check (sessions must exist and belong to the crew) and a readable slug id.

## Out of scope

Agent-triggered dreams (a tool can call the same code later); triggered and per-person habits and the load menu; contact `:weight`; section character budget; strength decay.

## Likely repo scope

`isaac-dream` only: schema move, scheduler task, dream runner, prompt, digest, diary, CLI, handbook. It uses isaac-agent's model dispatch and transcript reading directly, as isaac-episodes does (`segment.clj`, `distill.clj`).

## Scenarios (planner, 2026-11-01)

26 `@wip` scenarios on isaac-dream main `2b5b1fa`:

- `features/dream/run.feature` (12) — window, labels, evidence, reinforce, supersede, watermark, lock, failure, approval policy.
- `features/dream/schedule.feature` (4) — enabled-only tasks, local-time firing, validation, hot reload.
- `features/dream/digest.feature` (4) and `features/dream/diary.feature` (1).
- `features/habits/operator.feature` (3 new) — cross-crew list, `approve --lesson`, unknown crew.
- `features/habits/propose.feature` (2 re-`@wip`'d) — live propose attaches its own evidence (calling session + current message); crew-level `dream.approval`. The old "propose without evidence is refused" scenario is gone: evidence is no longer a tool argument.

Bean 1's seeded habits in operator/section now use `{:session … :entries …}` evidence; those scenarios stay live.

## New step (implement in isaac-dream)

`When the dream service is started` — starts the dream module's component on the scenario's scheduler (the one `the scheduler is started with the clock at …` created), against the loaded config, so `the scheduled tasks include:`, `the clock advances "…" and pending handlers complete`, and `the config is reloaded` act on it. The foundation runner step cannot do this: it builds its own real-clock scheduler and loads only the modules it is handed. Precedent: isaac-cron's `the Isaac system is started` / `the scheduler ticks at`. `the config is reloaded` is the real reconcile path, so the dream component must reschedule on config change (no restart).

## Notes for the implementer

- Matcher: a blank value cell matches nil (`evidence[1]`, `lock`); the literal text `nil` does not.
- The model's reply is JSON text: `{"propose":[{"id","lesson","evidence":["<session>#<n>"],"supersedes":[…]}],"reinforce":[{"id","evidence":[…]}]}`. Window lines: `<session>#<n> <iso time> <role>: <text>`, numbered per session from the first in-window line.
- Watermark moves to the dream's start time. Stale lock: one hour, fixed.
- Digest config keys are `:comm` / `:to`; the delivery record carries `:comm` / `:target`.
- Diary files: `dream/<crew>/diary/<iso with - for :>.edn`.
- Lesson text never changes in place; `history` is append-only.
- Update the handbook and README for dreaming, config, digest, diary, `show`, `diary`, `run`, and the new evidence shape.
- `isaac` `modules.edn`: bump `:isaac.dream` to the landed sha.

## Acceptance

Run from `isaac-dream`, with `@wip` removed from every scenario listed:

- `bb features features/dream/run.feature`
- `bb features features/dream/schedule.feature`
- `bb features features/dream/digest.feature`
- `bb features features/dream/diary.feature`
- `bb features features/habits/operator.feature:109`
- `bb features features/habits/operator.feature:137`
- `bb features features/habits/operator.feature:159`
- `bb features features/habits/propose.feature:20`
- `bb features features/habits/propose.feature:47`
- `bb ci` green locally, and the `CI Tests` workflow green on isaac-dream `main` after landing.
- Unit specs cover window building (tool output dropped, labels), evidence mapping and drops, history events, supersession on approve, watermark/lock, digest routing, and crew/defaults config resolution.
- `isaac` `modules.edn` `:isaac.dream` points at the landed sha.

feature-baseline: isaac-dream 2b5b1fa60f91e5661511219ed10ad77e00daba1d
feature-blob: isaac-dream features/dream/run.feature f83d48db0daf50cddba7c6328191c9bcc41c834d
feature-blob: isaac-dream features/dream/schedule.feature a7b03fe817fd9637e1e1139e7e1f87f841db637f
feature-blob: isaac-dream features/dream/digest.feature afaa53d425276b54b5e04085dd25156e93f06511
feature-blob: isaac-dream features/dream/diary.feature f9e201740bc511afc5eb2efa0fdd1fae3aeb7a4e
feature-blob: isaac-dream features/habits/operator.feature ad3732181f7c6aafd8620ce46d95510dd491bdec
feature-blob: isaac-dream features/habits/propose.feature b86ba0036ccc253856e3813c0539846a8ec3fc8a

## Work checkpoint (2026-11-01, scrapper@bean-isaac-p36b)

Done: isaac-dream `bean/isaac-p36b` pushed at b11b61b, rebased on origin/main. All 26 baselined scenarios have @wip removed; `bb ci` green after rebase (lint-cli-host OK, 22 specs/32 assertions, 35 feature examples/113 assertions), documentation updated, mixed assistant tool-call text filtered. `bb bean-gate verify isaac-p36b --dir isaac-dream=../isaac-dream-p36b` PASS (earlier default-dir invocation saw unrelated shared checkout and failed; never use default dir here). Next: land module via squash at `../isaac-dream-p36b` and update isaac `modules.edn` pin on bean branch, then re-gate using `--dir isaac-dream=../isaac-dream-p36b` or `--ref isaac-dream=origin/main`. Resume from `../isaac-dream-p36b:git merge --squash bean/isaac-p36b` after `git checkout main && git pull --ff-only`. Record main-shas and complete. No landing yet.

## Landed on main (2026-11-01)

main-sha: isaac-dream cba14035c6443c7a69d51c089bd27ee2cf3f8315
main-sha: isaac d37c061dc3454df19dbf802e5abe886f512e156b

`bb ci` green in isaac-dream (22 specs, 35 feature examples) and isaac (79 examples). `bb bean-gate verify isaac-p36b --dir isaac-dream=../isaac-dream-p36b` PASS on landed squash commits.
