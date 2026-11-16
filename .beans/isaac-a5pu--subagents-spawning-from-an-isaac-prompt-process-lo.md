---
# isaac-a5pu
title: 'Subagents: spawning from an isaac prompt process loses the child''s turn'
status: in-progress
type: bug
priority: normal
tags:
    - agent
created_at: 2026-11-16T14:44:41Z
updated_at: 2026-11-16T15:38:18Z
parent: isaac-4qxr
---

Repo: **isaac-agent** (spawn / turn submit) and possibly **isaac-foundation** (CLI process).

## Observed (zanebot, 2026-11-15)

A turn run with local `isaac prompt -c zane …` (in the short-lived CLI process, logging to cli.log) called `subagent__spawn`. The child session "astute-holly" was created, but its queued turn never ran and left no turn record: the queue it went to lived in the CLI process, which exited when the parent's turn ended. The same spawn through the server (hail) worked.

## Correction (2026-11-16)

The child turn was not lost: the record (`turns/5aad39ae.edn`) exists. The prompt process **started** the child after the parent's turn ended, then exited mid-run, leaving the record `:running` forever.

## Decision (2026-11-16, Micah)

With a server configured, `isaac prompt` runs on the server (already true via `isaac remote use`, isaac-gar0). With no server, the prompt runs in its own process and **blocks until everything it started is done**: every child it spawned has finished and the parent has processed each result. Then it exits.

## Acceptance

Scenario in `isaac-agent/features/tool/subagents.feature` (scenario-level `@wip`; remove it when it passes):

- `bb features features/tool/subagents.feature:110` — `isaac prompt` with no server returns only after the child ran and the parent replied to its result: stdout has the reply to the result, the transcript has the result and the reply, the child is `done`

Scope: the standalone path only. With a server configured (`isaac remote use …`, isaac-gar0) the prompt already runs on the server and its children are the server's.

feature-baseline: isaac-agent af7101ebd7321fe96dd4d9c0c90039ffb5c0ff68
feature-blob: isaac-agent features/tool/subagents.feature d336096d92c2a799fb1eda351fbe4e15b35fb4fe 110
