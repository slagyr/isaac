---
# isaac-7ngj
title: Cron falsely records success after failed scheduled turn
status: in-progress
type: bug
priority: high
created_at: 2026-08-11T17:02:28Z
updated_at: 2026-09-18T05:49:02Z
---

Cron state can falsely record `:last-status :succeeded` even when the scheduled turn failed before any tool execution.

Observed on zanebot 2026-08-11:
- `health-checkin` fired at 09:00 America/Phoenix.
- Server log shows session `keen-narwhal` hit an Anthropic HTTP 400 and `:chat/provider-walled`.
- No tool calls ran, so no iMessage was sent.
- But `~/.isaac/cron.edn` recorded:
  - `:last-run "2026-08-11T09:00:00-0500"`
  - `:last-status :succeeded`
  - `:last-error nil`

That makes cron health look green when the job actually failed, which hides operational problems and breaks trust in cron state.

## Acceptance
- [ ] When a cron-fired turn fails before producing a successful assistant result, cron state records `:last-status :failed`.
- [ ] `:last-error` captures a useful failure summary for provider/tool/dispatch errors.
- [ ] Success is recorded only after a genuinely successful cron turn.
- [ ] Coverage exists for the failing path that previously wrote false success.

## Notes
- This surfaced while investigating missing morning health updates.
- Separate operational follow-ups exist for model swaps and migrating `tempest-vault-sync` from OpenClaw to Isaac; they are not part of this bug.

## Investigation note

Code inspection on current `isaac.cron` shows `fire-job!` calls `bridge/dispatch!` synchronously and records `:last-status` from the returned result (`:failed` when `:error` is present). So the bug is likely **not** that cron records success immediately upon trigger without waiting for the turn. More likely: a provider-failed turn is returning a non-error result to cron, or a later layer is swallowing the failure.

That means the bean needs root-cause confirmation before dispatching implementation.



## Root cause (confirmed by reading, 2026-09-18)

`isaac.cron.service/fire-job!` (service.clj:162): `failed? (boolean (:error result))`. A provider wall returns `{:unavailable? true :reason :wall …}` with **no `:error`** (isaac-agent `drive/turn.clj:458` classifies it `:provider-unavailable`; `provider_wall_spec.clj`), so the 08-11 Anthropic 400 → `:chat/provider-walled` wrote `:succeeded`. Same for an empty terminal response when the retry also comes back empty. The fix is to define success positively — the turn ended with an assistant reply — and derive `:last-error` from `:error`/`:message`, `:unavailable?`+`:reason`, or the empty-reply guard. Log `:cron/job-failed :outcome <kw>` on every non-success (today that event fires only on an exception).

## Scenarios (committed @wip — isaac-cron `features/scheduling.feature` @ f3b3a7c)

| line | scenario |
|------|----------|
| :99 | a provider wall during a cron turn records failed with the reason |
| :120 | a provider error during a cron turn records failed with the message |
| :137 | a cron turn that produces no assistant reply is not a success |
| :154 | a successful cron run clears a previous failure |

## Step ledger

| step | status |
|------|--------|
| default Grover setup / config: / the following model responses are queued: (text, error, http-error rows) / the scheduler ticks at … / the isaac file … EDN contains: / the isaac EDN file … contains: / the log has entries matching: | reuse — all existing; **no new steps** |

Check at implementation: the `http-error` row type lives in isaac-agent's session steps (`provider_walls.feature`) — confirm isaac-cron's harness loads them (it already loads `the following model responses are queued:`); the `#"regex"` value form in `EDN contains:` follows TABLES.md.

## Acceptance
```
cd isaac-cron && bb features features/scheduling.feature && bb ci
```
Version bump; pin is a train step. Field check: on zanebot, `cron.edn` for `health-checkin` after the next walled morning shows `:failed` + a reason.


## Note (planner, 2026-09-18)
Scenario :99 (a provider wall records :failed) is interim. isaac-ugpq decides that a walled turn SUSPENDS and resumes; cron will record :suspended then the final status. Implement :99 as written now (it is still better than false success); isaac-a0q6 re-cuts it.
