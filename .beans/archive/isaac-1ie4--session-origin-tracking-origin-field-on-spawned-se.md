---
# isaac-1ie4
title: "Session origin tracking: :origin field on spawned sessions"
status: completed
type: feature
priority: low
created_at: 2026-04-22T17:25:31Z
updated_at: 2026-04-23T00:02:17Z
---

## Description

Every session records a structural :origin field identifying where it was spawned from. Enables filtering history by spawner (e.g. 'all transcripts from cron X') and answering 'what produced this session?' without parsing free text.

Shape:
- :origin {:kind :cli}                             — isaac prompt / chat
- :origin {:kind :cron :name "health-check"}     — scheduler-spawned
- :origin {:kind :acp}                             — via ACP server
- :origin {:kind :discord :guild ... :channel ...} — adapter (future)

Every session has an origin; there is no nil/absent case. The kind tells you the spawner; kind-specific keys (like :name for cron) carry additional identity.

Scope (v1):
- Cron firing path sets :origin {:kind :cron :name <job-name>}
- CLI prompt/chat commands set :origin {:kind :cli}
- ACP session-creation path sets :origin {:kind :acp}
- :origin is persisted on the session record and visible via storage/list-sessions

Out of scope:
- CLI filters like 'isaac sessions list --origin cron:health-check' (separate bead)
- Discord/adapter origins (land with the Discord epic)

Feature file: features/session/origin.feature (two @wip scenarios)

## Acceptance Criteria

1. Remove @wip from both scenarios in features/session/origin.feature
2. bb features features/session/origin.feature passes (both scenarios green)
3. bb features and bb spec pass with 0 failures

## Notes

Persisted structural :origin on sessions, defaulting storage-created sessions to {:kind :cli} and wiring cron, CLI prompt, and ACP session creation to set explicit origins. Verified with bb features features/session/origin.feature, bb features, and bb spec in commit 9c220a6.

