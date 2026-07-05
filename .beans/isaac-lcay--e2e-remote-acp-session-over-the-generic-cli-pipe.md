---
# isaac-lcay
title: 'e2e: remote ACP session over the generic cli pipe'
status: in-progress
type: feature
priority: normal
created_at: 2026-07-03T15:34:48Z
updated_at: 2026-07-05T05:11:43Z
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

## Planner unblock note (2026-07-05, prowl)

No dependency-direction conflict: `isaac-lcay` is blocked only by `isaac-895i`,
and `isaac-895i` is completed. The downstream `blocking: isaac-exi2`
relationship does not block lcay.

## Worker observations (2026-07-05)

Despite the dependency unblock, the current implementation surface still does
not match the accepted scenario contract.

What I verified:
- The accepted scenario is still `@wip` in `isaac-cli-proxy/features/integration.feature`.
- Its five approved interactive-driver-family steps are not implemented anywhere on the current cli-proxy classpath.
- A targeted gherclj run of that exact scenario reports it as pending / not yet implemented.
- The current top-level Isaac module registry in `isaac/modules.edn` still pins `:isaac.comm.acp` to git SHA `1d10231442299d41de9781d9a3a2bdf2602ce33c`, whose ACP CLI surface still includes the legacy `--remote` websocket path and older command behavior.
- This bean's scenario expects the new generic `isaac remote ... -- acp` proof, but the pinned ACP module and the missing interactive-driver steps leave the repo set in a mixed / incomplete state for this contract.

Implication:
- This is not blocked by `isaac-895i`, but it is still blocked in practice by repo/module alignment and missing approved step infrastructure.
- I should not invent the approved interactive-driver steps or force an e2e scenario against a mixed-version module set; that would risk proving the wrong contract.
- Planner / module owners need to decide whether to (a) update the module graph / ACP pin first, or (b) adjust the bean/scope so the scenario targets the currently pinned ACP surface.
