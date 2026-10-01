---
# isaac-ziqg
title: A resumed turn closes the record it replaces
status: draft
type: bug
priority: normal
created_at: 2026-10-01T17:23:56Z
updated_at: 2026-10-01T17:23:56Z
---

Micah, 2026-10-01 ("every resume should clear the record it picks up"). After a restart, the boot resume scan re-queues interrupted turns as NEW turn records (`origin.kind :resume`), but the original record keeps `state: running` forever. Seen on zanebot: cron turns 25f87b25 and 9bfff473 still show `running` after their resumes c14d6e86/f30c7125 finished.

## Intent
When a resume picks up an interrupted turn, the original record is closed (e.g. `state: finished`, outcome `:resumed`/`:superseded`, with a pointer to the resuming turn id), so `isaac turns` never shows a ghost `running` turn.

## Acceptance (to write as scenarios when planned)
- Restart with an in-flight turn → after resume, the original record is not `running` and names the resuming turn; the resuming turn names its source.
