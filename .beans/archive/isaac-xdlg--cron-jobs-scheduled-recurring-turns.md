---
# isaac-xdlg
title: "Cron jobs: scheduled recurring turns"
status: completed
type: feature
priority: normal
created_at: 2026-04-22T15:17:59Z
updated_at: 2026-04-22T16:52:43Z
---

## Description

Infrastructure for firing agent turns on a cron schedule. Core of the OpenClaw-parity cron feature; zanebot has 3 active cron jobs (git-backup, health-checkin, tempest-vault-sync) that this replaces.

Scope (v1):
- Cron job defined in isaac.edn root config under :cron map, keyed by job name: :cron {<name> {:expr 'cron-str' :crew :id :input 'prompt'}}
- Runtime state lives in <state-dir>/cron.edn: {<name> {:last-run 'iso' :last-status :succeeded | :failed :last-error 'msg'}}
- Scheduler loop inside the Isaac server wakes periodically, evaluates cron expressions against current time, fires due jobs
- Firing a job = creating/resuming a session and sending :input as a user message via the normal chat flow
- Missed windows (while Isaac was down) are skipped silently with a warn log
- TZ: root config :tz (IANA string) overrides JVM system default for cron evaluation only
- Standard 5-field cron (minute hour day month weekday)

Out of scope (separate beads):
- One-off tasks (:at shape) + pruning — isaac-nnns
- Task-to-comm delivery (post results to Discord/Slack/etc.) — isaac-xww2
- Per-job TZ override
- Any task runtime beyond cron (subagent/acp/cli/etc.)
- Job run history / progress events / lost detection
- CLI for job management (bb isaac cron list/create/delete); seed via config for now

See features/cron/scheduling.feature for the 3 @wip scenarios.

## Acceptance Criteria

1. Implement cron job storage (root config :cron section, state at <state-dir>/cron.edn), cron parsing + eval, scheduler loop, and job-firing (session creation + prompt dispatch).
2. Root config :tz drives cron interpretation; system default if unset.
3. Add the 3 step-defs listed above.
4. Remove @wip from all 3 scenarios in features/cron/scheduling.feature.
5. bb features features/cron/scheduling.feature passes.
6. bb features passes overall.
7. bb spec passes.

## Design

Implementation notes:
- Namespace: src/isaac/cron/ with scheduler.clj, cron.clj (parser/eval), state.clj.
- Cron parser: minimal 5-field parser (bb-compatible). Public contract: given cron expr + prev-fire time + now + zone, return next-fire-at or nil if too-far-future.
- TZ: isaac.cron parses (:tz config) else ZoneId/systemDefault.
- Scheduler loop: tick every ~30s (configurable). For each job, compute next fire time from last-run (or job first-seen time) in the configured zone. If now >= next-fire AND now - next-fire < one-tick-window, fire. Else if now > next-fire by more than a tick, skip with warn log.
- Fire: create/find a session (naming strategy handles id), send job's :input as user message through the existing process-user-input! path.
- Write back last-run (ISO-8601 with zone), last-status (:succeeded | :failed), last-error (if failed) to <state-dir>/cron.edn under the job's name.

New step-defs to add:
1. 'the scheduler ticks at "<iso-instant>":' — binds clock to the given instant, runs one scheduler pass.
2. 'the EDN state file "<relpath>" contains:' — k-v table over <state-dir>/<relpath>.edn. Relative path eliminates the state-dir prefix duplication in every scenario. Same k-v semantics as the existing 'the EDN file "<abs-path>" contains:' step.
3. 'session "<name>" does not exist' — negative assertion paired with the existing 'session "<name>" exists'.

## Notes

Cron scheduling implemented and pushed in commit 573e790. bb spec passes, the cron feature scenarios pass, and unrelated full-suite feature failures are tracked separately in isaac-wtag. Marking this bead unverified for /verify.

