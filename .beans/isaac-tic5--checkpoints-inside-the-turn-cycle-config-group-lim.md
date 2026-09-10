---
# isaac-tic5
title: 'Checkpoints inside the turn: :cycle config group (limit, checkpoint-every, prompts), loop nudge every N cycles, continuations default 2'
status: draft
type: feature
priority: high
tags:
    - agent
    - hail
    - config
created_at: 2026-09-10T21:15:48Z
updated_at: 2026-09-10T21:15:48Z
---

Repos: **isaac-agent** (tool loop / drive: `llm/tool_loop.clj` hooks, `drive/turn.clj`
wrap-up; crew schema in `resources/isaac-manifest.edn`; charge override path),
**isaac-hail** (`bands.clj` default continuations, band `cycle:` override,
`delivery_worker.clj` charge), plus config cutover on zanebot (crew + band files)
and a Checkpoint section in `hail-bean-work` (orchestration).

## Why

A hail continuation is expensive: the new turn re-orients (skill load, git
pull, beans show, re-reading the files the note names — 20–40 cycles of the
120) and, until isaac-x0cw lands, does not even see the wrap-up note. ntt6
made the cycle cap safe (wrap-up + bounded continuation) but the cap is
still the only thing that makes a worker save its work. With isaac-yk0u
cheapening cycles, turns should get longer and continuations rarer; the
save point has to move inside the turn.

## Decisions (2026-09-10, Micah)

1. **Checkpoint is a loop nudge, not a turn end.** Every `checkpoint-every`
   cycles (counted at cycle end, via the existing loop hooks) the drive
   appends a user-role nudge to the next request — same mechanism and
   register as the wrap-up nudge. Costs no cycle; rides alongside the tool
   results. Logged `:turn/checkpoint-nudged :session :cycle`; persisted in
   the transcript as a `checkpoint` marker entry so a resumed turn and the
   verifier can see when checkpoints happened.
2. **Off unless configured.** No default `checkpoint-every`; hail bands set it
   on the charge, so chat crews (Discord, ACP, CLI) are never told to save
   their work.
3. **One config group, `:cycle`.** Clean cutover, no alias:
   ```clojure
   :cycle {:limit            250      ; was :cycle-limit (hard-rejected now, message names :cycle :limit)
           :checkpoint-every 50       ; nil = off
           :checkpoint-prompt "…"     ; optional; drive default text otherwise
           :wrap-up-prompt    "…"}    ; optional; replaces the hardcoded nudge
   ```
   Schema'd like `:compaction`, so `isaac config schema crew.value.cycle`
   lists the knobs. Layering as today: built-in defaults → crew → charge.
   Hail band frontmatter carries the same map (`cycle:` replaces
   `cycle-limit:`); cron jobs likewise.
4. **Prompts are data on the charge.** The drive ships generic default texts
   (the current wrap-up nudge; a checkpoint nudge of the same shape:
   "Checkpoint: save work in progress the way your instructions say to, then
   continue. Do not stop."). Crews/bands may replace either. The drive never
   learns git or beans (see drive-stays-generic).
5. **The skill defines the checkpoint.** `hail-bean-work` gains a
   *Checkpoint* section: commit on green and push to the bean branch;
   refresh the bean's done/next note; if tests are red, note the red and
   keep going; never hand off from a checkpoint unless acceptance is met.
6. **Continuations are a last resort.** `isaac-hail` default continuations
   3 → **2**. isaac-work's override (6) already removed (2026-09-10);
   bands use the default.
7. **Scrapper config (zanebot, applied with the train):**
   ```clojure
   :cycle {:limit 250
           :checkpoint-every 50
           :checkpoint-prompt "Checkpoint. If your last test run was green, commit and push to your bean branch now. Refresh the bean's done/next note in one edit: what is done, what is next, the exact file:line to resume from. If tests are red, say so in the note and keep going. Then continue where you were — do not stop, and do not hand off unless acceptance is met."
           :wrap-up-prompt "Your cycle budget for this turn is exhausted. Start nothing new. Commit and push whatever is green to your bean branch. Rewrite the bean's done/next note: what is done, what is next, the exact file:line to resume from, and the last test command with its result. If acceptance is met, hand off to verify now. Reply with only that note; your next turn starts from it."}
   ```
8. **Prerequisite:** isaac-x0cw (wrap-up note persisted for the continuation).
9. **Default prompts are task-agnostic (Micah, 2026-09-10).** The drive's
   built-in texts assume nothing about git, tests, beans, or the kind of task:
   - wrap-up (unchanged from today): "Your cycle budget for this turn is
     exhausted. Do not start new work. First save any work in progress the way
     your instructions say to, then reply with a short note: what is done, what
     is next, and the exact place to resume from. Your next turn resumes from
     this note."
   - checkpoint: "Checkpoint: save work in progress the way your instructions
     say to, then continue. Do not stop."
   "The way your instructions say to" is the contract: the crew's soul/skill
   defines what saving means. Task-specific wording (bean branch, verify,
   tests) belongs only in crew/band overrides such as scrapper's in (7). A
   scenario pins that the defaults contain none of: git, commit, branch, bean,
   test, verify.

## Acceptance

Scenarios to be planted one at a time (features/llm/checkpoint.feature,
turn_exhaustion.feature additions, config schema feature; isaac-hail
band/continuation features). Then:

```
cd isaac-agent && bb features features/llm/checkpoint.feature features/llm/turn_exhaustion.feature features/tool/tool_loop_limit.feature && bb spec && bb ci
cd isaac-hail && bb features && bb spec && bb ci
```
