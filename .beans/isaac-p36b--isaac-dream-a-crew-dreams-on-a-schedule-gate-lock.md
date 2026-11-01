---
# isaac-p36b
title: 'isaac-dream: code-run dreams on a crew''s schedule — digest, diary, reinforce, supersede'
status: draft
type: feature
priority: normal
created_at: 2026-10-29T16:52:25Z
updated_at: 2026-11-01T17:42:14Z
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
5. Code validates every item. Evidence must name sessions in this window that belong to this crew; anything else is dropped and noted in the diary. New habits are stored `proposed`, or `active` under `:auto`. Reinforcing raises an active habit's count and last-reinforced date with no approval (ruling, 2026-10-08).
6. Write the diary entry, move the watermark to the end of what was read, release the lock, send the digest.

A failed model call leaves the watermark where it was, writes a diary entry saying it failed, and sends a digest saying so.

`isaac dream run` runs the same steps now, ignoring the schedule and `enabled` (so a disabled crew can be tried by hand), but not the new-messages rule.

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

## Acceptance (to be replaced by baselined scenarios)

- A scheduled dream with new messages makes one model call, stores a proposed habit with valid evidence, writes a diary entry, moves the watermark, and sends one digest to the notify target.
- A scheduled fire with no new messages makes no model call and sends nothing.
- A held lock makes a second dream stop without a model call.
- Tool results in the window never reach the model request.
- Evidence naming another crew's session, or one outside the window, is dropped and recorded in the diary.
- A reinforcement raises the count and date on an active habit without changing its state.
- Only crews whose resolved `dream.enabled` is true get a scheduled task; an enabled crew with no resolved schedule fails config validation naming the crew.
- `:auto` from `defaults.crew.dream.approval` stores a new habit as active; a crew-level `:manual` overrides it.
- Approving a habit that supersedes another retires the other.
- A failed model call leaves the watermark unchanged and sends a failure digest.
- `isaac dream run --crew <id>` dreams now; `isaac dream list` without `--crew` shows proposed habits across crews; an unknown `--crew` exits non-zero.
- Root-level `dream` config is gone; `crew.<id>.dream` and `defaults.crew.dream` validate.
- The handbook covers what a dream is, the config, the digest, the diary, and the operator CLI.
