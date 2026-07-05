---
# isaac-lcay
title: 'e2e: remote ACP session over the generic cli pipe'
status: in-progress
type: feature
priority: normal
created_at: 2026-07-03T15:34:48Z
updated_at: 2026-07-05T05:02:46Z
blocking:
    - isaac-exi2
blocked_by:
    - isaac-895i
---

## Context

Before deleting the ACP websocket transport, prove the replacement: a real ACP session (initialize -> prompt -> streamed response) running as `isaac remote acp ...` through cli-proxy -> cli-server -> subprocess `isaac acp` stdio.

Decision (2026-07-03, Micah): this is the gating proof for the acp cutover bean.

## Design / proof points

- ACP argv forwards verbatim through the pipe (session-selection flags --session/--crew/--resume/--create/-M work unchanged).
- JSON-RPC frames stream both directions with interactive latency (not buffered to exit).
- Clean shutdown: editor closes stdin -> acp exits -> exit frame -> proxy exits 0.

## Acceptance (scenarios after review)

- Integration feature (cli-proxy integration.feature style): spawn real server + proxy, run `remote acp`, drive initialize/newSession/prompt with a stub model, assert streamed ACP responses arrive before process exit.

## Likely repo scope

isaac-cli-proxy (integration feature), possibly small fixtures in isaac-cli-server. Blocked by the subprocess bean.

## Acceptance scenario (committed @wip, 2026-07-03)

isaac-cli-proxy `features/integration.feature` — remote ACP e2e (initialize -> session/new -> prompt -> streamed response before EOF -> clean shutdown). New steps approved: interactive-driver family (5), command-agnostic — ACP specifics live in scenario data only.

Acceptance: un-@wip; `bb features features/integration.feature` green in isaac-cli-proxy.
