---
# isaac-ziqg
title: A resumed turn closes the record it replaces
status: completed
type: bug
priority: normal
created_at: 2026-10-01T17:23:56Z
updated_at: 2026-10-01T18:51:22Z
---

Micah, 2026-10-01 ("every resume should clear the record it picks up"). After a restart, the boot resume scan re-queues interrupted turns as NEW turn records (`origin.kind :resume`), but the original record keeps `state: running` forever. Seen on zanebot: cron turns 25f87b25 and 9bfff473 still show `running` after their resumes c14d6e86/f30c7125 finished.

## Intent
When a resume picks up an interrupted turn, the original record is closed (e.g. `state: finished`, outcome `:resumed`/`:superseded`, with a pointer to the resuming turn id), so `isaac turns` never shows a ghost `running` turn.

## Acceptance (to write as scenarios when planned)
- Restart with an in-flight turn → after resume, the original record is not `running` and names the resuming turn; the resuming turn names its source.

## Decision + Acceptance (Micah, 2026-10-01; gated)

When the boot resume scan re-queues an interrupted turn, any `:running` record for that session is closed: `:state :finished`, `:outcome :interrupted`, `:resumed-by <new id>`; the new record carries `:resumes <old id>`. `isaac turns show` and `turns list` display both links.
- The @wip scenarios in isaac-agent `features/turn/resume_records.feature` pass with @wip removed.
- `bb ci`, `bb jvm-spec`, `bb jvm-features` green.

feature-baseline: isaac-agent fead24a5574b7260b74a99b3453abfbe5168013b
feature-blob: isaac-agent features/turn/resume_records.feature 8bb970799be25eb01ff84309e132d61bd988d101

## Landed on main (2026-10-01)

main-sha: isaac-agent def0bfe65c8fad85897c0c8cf9f02ff460804050

`enqueue-resume-turn!` (bridge/resume.clj) now looks up the stuck `:running`
turn-queue record for the marker's session before enqueuing the resumed turn,
closes it (`:state :finished`, `:outcome :interrupted`, `:resumed-by <new-id>`),
and stamps `:resumes <old-id>` on the new record. `turns show`/`turns list`
(turn/cli.clj) display both fields; `turns list` scans all turns (not just the
held/queued/waiting-session subset) for `:resumes` so the link still shows once
both records have finished. `bb ci` and `bb jvm-spec` green; `bb jvm-features`
hit the two known `turn_store.feature` timing flakes (unrelated scenarios) on
both runs — reran per the gated brief, same two each time. CI green:
https://github.com/slagyr/isaac-agent/actions/runs/36909327198
