---
# isaac-p36b
title: 'isaac-dream: a crew dreams on a schedule — gate, lock, diary, digest, reinforce'
status: draft
type: feature
created_at: 2026-10-29T16:52:25Z
updated_at: 2026-10-29T16:52:25Z
parent: isaac-pcm3
---

Bean 2 of dreaming (bean 1: isaac-b1ir). Bean 1 stores habits and shows active ones in the system prompt, but nothing makes a crew dream. This bean adds the dream itself: a scheduled turn that reads the crew's recent conversations, proposes or reinforces habits, and tells the operator what it did. It also makes operator review practical.

Status: draft. Needs Micah's answers to the open questions, then `@wip` scenarios in isaac-dream and a baseline.

## Why now

Bean 1 can be tried on zanebot by hand: grant `:dream/propose`, ask a crew to propose, approve with the CLI. That tests the plumbing, not dreaming. Planner review on 2026-10-29 found these gaps between "habits exist" and "a crew dreams":

- No trigger and no dream prompt.
- No memory of where the last dream stopped, and nothing stopping two dreams running at once.
- Review is slow: `isaac dream list` needs `--crew` per crew, shows no evidence, and ids are UUIDs you have to copy.
- Evidence is any non-empty string. Nothing checks that the sessions exist or belong to the crew, and evidence is the guard against injected text becoming a standing habit.
- No diary, and no word to the operator when a habit is proposed.

## Proposed shape

The module owns what has to be deterministic or out of the crew's reach. The prompt owns the judgment (rulings on isaac-b1ir, 2026-10-08).

**Trigger.** A cron job (isaac-cron) runs the dream prompt for a crew in a fresh session; the job's model picks whose budget pays. The module ships the prompt; the operator adds one cron entry per dreaming crew. The prompt tells the crew to call `dream__begin` first and `dream__end` last.

**Tools (new grants, none on by default):**

- `dream__begin` — checks the run gate and takes the crew's lock. The gate: enough time since the last dream, and new activity since the watermark. If the gate is shut or the lock is held, it says so and the turn ends. Otherwise it returns the window (`since` = watermark, `until` = now) and the crew's current habits with their ids. The crew then reads that window with `session__list` / `session__read` (isaac-d3qj).
- `dream__propose` (from bean 1) — gains a readable id (a short slug the crew picks, made unique by the module) and evidence checking: every session must exist, belong to the calling crew, and have activity in the window.
- `dream__reinforce` — the crew names an active habit and the sessions that support it again. Raises its count and last-reinforced date. No approval needed (ruling, 2026-10-08). Rewording or merging is a new propose naming the habits it supersedes.
- `dream__end` — writes the diary entry, moves the watermark to `until`, and releases the lock. The diary entry records the window, sessions read, and what was proposed and reinforced.

A dream that dies without `dream__end` leaves the watermark where it was. The lock expires after a timeout so the next dream can run.

**Digest.** After `dream__end`, the operator gets one message over the attention comm: the crew, the window, and each proposed habit with its id, lesson and evidence, so it can be approved from the message.

**Operator CLI:**

- `isaac dream list` with no `--crew` lists every crew; `--state proposed` filters. Each row shows id, state, lesson and evidence.
- `isaac dream diary --crew <id>` shows recent dreams.
- An unknown crew is an error, not an empty list.

**Storage.** Module state under the Isaac root next to the habit files: `dream/<crew>/state.edn` (watermark, lock, last run) and `dream/<crew>/diary/…`. Not session records, not config.

## Open questions (Micah)

1. Is the trigger a cron job running the shipped prompt, or should `/dream` also be a slash command (slash providers, isaac-quji, have landed)?
2. Gate defaults: minimum time between dreams (proposal: 24h) and minimum new activity (proposal: at least one session with new messages)?
3. Digest channel: the attention comm, or a named session/crew per dreaming crew?
4. Should turns from untrusted origins (email, public Discord) be left out of the window or only flagged? Proposal: flag them in `session__read` output and have the prompt refuse to propose from flagged text alone. Full weighting waits for contacts (isaac-zt1x).
5. Supersession: when an approved habit supersedes another, does approval retire the old one automatically?

## Out of scope

Triggered and per-person habits and the load menu; contact `:weight` and `:from`; section character budget; strength decay (count + last-reinforced date only, per the 2026-10-08 strength concern).

## Likely repo scope

`isaac-dream` (tools, prompt, CLI, handbook). Possibly a cron example in its handbook. No isaac-agent change expected; if session__read cannot flag origin, that part needs a small isaac-agent bean.

## Acceptance (to be replaced by baselined scenarios)

- A cron-fired dream with the gate open reads the window, proposes a habit with valid evidence, writes a diary entry, moves the watermark, and sends one digest.
- A second dream inside the gate interval ends without reading or proposing.
- Two dreams at once: the second is refused by the lock.
- A propose citing another crew's session, a missing session, or one outside the window is refused.
- Reinforce raises the count and date on an active habit without changing its state.
- `isaac dream list` with no `--crew` shows proposed habits across crews with evidence; an unknown `--crew` exits non-zero.
- The handbook covers the dream prompt, the four tools and their grants, the gate, the diary, and what a dream looks like when a grant is missing.
