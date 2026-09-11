---
# isaac-tic5
title: 'Checkpoints inside the turn: :cycle config group (limit, checkpoint-every, prompts), loop nudge every N cycles, continuations default 2'
status: completed
type: feature
priority: high
tags:
    - agent
    - hail
    - config
created_at: 2026-09-10T21:15:48Z
updated_at: 2026-09-11T08:06:03Z
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
   - wrap-up: "Your cycle budget for this turn is exhausted. Do not start new
     work. First save any work in progress the way your instructions say to,
     then reply with a short note: what is done, what is next, and the exact
     place to resume from." (Today's hardcoded text ends with "Your next turn
     resumes from this note." — DROP it: only true for hail continuations;
     Micah 2026-09-10.)
   - checkpoint: "Checkpoint: save work in progress the way your instructions
     say to, then continue. Do not stop."
   "The way your instructions say to" is the contract: the crew's soul/skill
   defines what saving means. Task-specific wording (bean branch, verify,
   tests) belongs only in crew/band overrides such as scrapper's in (7). A
   scenario pins that the defaults contain none of: git, commit, branch, bean,
   test, verify.

## Acceptance

Scenarios (@wip):
- isaac-agent f705093: `features/llm/checkpoint.feature` (nudge every Nth
  cycle; crew checkpoint-prompt override; no config → no nudge),
  `features/llm/turn_exhaustion.feature` (crew wrap-up-prompt override),
  `features/config/cycle.feature` (:cycle-limit rejected naming :cycle
  {:limit}; `config schema crew.value.cycle` lists the knobs).
- isaac-hail 6339b55: `features/delivery.feature` (band :cycle map overrides
  the crew on the charge; default continuations 2). Existing ntt6 scenarios
  there move from `cycle-limit` to `cycle.limit`.
- Spec-level: the drive's default checkpoint and wrap-up texts contain none of
  git, commit, branch, bean, test, verify (decision 9).
- One new step in isaac-agent: `LLM request {n} matches:` (grover twin of
  `outbound HTTP request N matches:`). New transcript entry type `checkpoint`
  {:cycle N}; new log event `:turn/checkpoint-nudged`.

```
cd isaac-agent
bb features features/llm/checkpoint.feature features/llm/turn_exhaustion.feature features/config/cycle.feature features/tool/tool_loop_limit.feature
bb spec spec/isaac/drive spec/isaac/config
bb ci
cd ../isaac-hail
bb features features/delivery.feature
bb spec && bb ci
```

All pass with @wip removed; every existing `cycle-limit` feature/spec moves
to `cycle.limit` (clean cutover). Train: agent + hail bumps, then zanebot
config cutover — crew files `:cycle-limit` → `:cycle {...}` (scrapper per
decision 7), band files `cycle-limit:` → `cycle:`; isaac-hail default
continuations 2 lands with the hail bump.


## Implementation handoff (2026-09-11, scrapper@isaac-work-2)

Implemented the clean `:cycle` cutover and checkpoint/wrap-up behavior.

- isaac-agent branch: `bean/isaac-tic5` @ `86a9a92ce6e665526f48ce6804945f80a07c593a` (base `origin/main@dcf0954d5b68bdaa191d244471b5dd04e72b6119`)
  - nested cycle layering, checkpoint nudges/markers/logging, configurable wrap-up prompts, persisted terminal wrap-up response, schema/checks, and clean removal of charge-level `:cycle-limit` input alias
- isaac-hail branch: `bean/isaac-tic5` @ `9ee08f3840f29c201c68804ead4e24717` (base `origin/main@3faafd411a3e9698f260d201c68804ead4e24717`)
  - band `:cycle` overlay, no band/charge `:cycle-limit` alias, continuation default 2; pins agent `86a9a92ce6e665526f48ce6804945f80a07c593a`
- isaac skill documentation: main `27f8ffb72111d5dd2f71f4d502512dbb71eb838b`
- zanebot config/installed skill: main `c932e91` (`scrapper.edn` uses the approved 250/50 cycle map and prompts); `isaac config validate` passes. No service restart performed.

Acceptance evidence:

- Agent four requested features: 13 examples, 0 failures, 35 assertions.
- Agent focused charge/turn specs: 108 examples, 0 failures, 282 assertions.
- Agent drive + directly changed config specs: 105 examples, 0 failures, 294 assertions.
- Hail delivery feature: 30 examples, 0 failures, 107 assertions.
- Hail specs: 156 examples, 0 failures, 359 assertions.
- Hail inheritance/prompt features rerun after an unrelated parallel-suite collision: 12 examples, 0 failures, 46 assertions.
- No relevant `@wip` remains.

Known pre-existing/flaky full-suite reds, reproduced outside this diff:

- Agent `bb spec spec/isaac/drive spec/isaac/config`: three `resolve-history-retention` examples fail for missing nexus filesystem on `origin/main` too.
- Agent `bb ci`: one file-tool cwd example failed in the full suite, then passed alone (37 examples, 0 failures).
- Hail `bb ci`: full feature run had inheritance/template state failures; both affected feature files passed immediately when focused. Earlier CI attempt also hit the fixed 60-second suite cap.



## Landed on main (2026-09-11)

main-sha: isaac-agent 04f890b3bba8111bbf6bc66be2548b462f450fc9
main-sha: isaac-hail 13939041b0c56decb8aacfe2bf7988568cced30a
main-sha: isaac 27f8ffb72111d5dd2f71f4d502512dbb71eb838b
