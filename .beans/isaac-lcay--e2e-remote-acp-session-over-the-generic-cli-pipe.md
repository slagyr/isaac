---
# isaac-lcay
title: 'e2e: remote ACP session over the generic cli pipe'
status: in-progress
type: feature
priority: normal
created_at: 2026-07-03T15:34:48Z
updated_at: 2026-07-06T16:00:49Z
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

## Planner decision (2026-07-05, prowl) — Option (b), no ACP bump

Two distinct surfaces were conflated. Separating them dissolves the conflict:

1. **The generic remote pipe** (`isaac remote .../cli -- acp ...` → cli-proxy →
   cli-server → subprocess). This lives entirely in **isaac-cli-proxy** (the
   work repo) and **isaac-cli-server** (isaac-895i, completed). It is available
   now. This is what lcay proves.
2. **The ACP module's legacy `--remote` websocket path.** Its *deletion* is
   **isaac-exi2's** job, and exi2 is `blocked_by` lcay by design — you prove the
   replacement (lcay) *before* you delete the old transport (exi2).

Therefore lcay must run against the **currently pinned** `:isaac.comm.acp`
surface, on purpose:

- The proof drives `isaac acp` in its **local stdio** mode over the pipe
  (initialize → newSession → prompt → streamed response → clean shutdown).
  Local stdio is the *kept* surface (see exi2 "Keep"). The proof never invokes
  `--remote`.
- The legacy `--remote` websocket path still being present in the pinned ACP is
  **expected and irrelevant** to this scenario — it is not exercised. Do **not**
  bump `:isaac.comm.acp` as part of lcay. A pin bump here would invert the
  gating order.

Scope/acceptance is unchanged; it already targets the pinned surface.

**On the "missing steps":** the five interactive-driver-family steps are
**approved work to implement in this bean**, not a blocker. "New steps approved"
means the planner signed off on creating them. A targeted gherclj run reporting
the `@wip` scenario as pending is the expected starting state. Implement the
five command-agnostic steps (ACP specifics stay in scenario data), wire the
scenario against a subprocess `isaac acp` (stub model), un-@wip, and get
`bb features features/integration.feature` green in isaac-cli-proxy.

No dependency or module change is required to proceed.

## Worker observations (2026-07-05, scrapper)

Additional implementation check after the planner note found a separate concrete blocker in the actual environment/toolchain:

- `isaac-cli-proxy/features/integration.feature` still carries the accepted lcay scenario as `@wip`.
- The approved interactive-driver-family steps referenced by that scenario are still absent from the cli-proxy feature classpath; this part is implementable work per planner direction.
- However, the real launcher currently available on this machine (`/usr/local/bin/isaac`) does **not** expose `acp` at all (`isaac --root /tmp/x acp --help` -> `Unknown command: acp`).
- `isaac-cli-server` production code hardcodes the spawned binary to `isaac`, so the real e2e path will invoke that installed launcher, not the orchestration repo's split-module composition.
- The accepted proof requires a **real cli-server backed by an isaac install with an echo model** running `isaac acp` as the subprocess target. With the current installed launcher, that target command does not exist.

Implication:
- The missing step infrastructure is implementable here, but the required real-launcher e2e proof still cannot pass against the currently installed `isaac` binary.
- I should not silently swap the proof target from the real installed launcher to a different composition without planner direction.
- Planner / owners need to decide whether lcay should assume a different launcher/bootstrap surface, or whether the installed launcher on this machine must first gain the `acp` command before this e2e proof can be completed.

## Planner decision (2026-07-05, prowl) — not a blocker; fixture-root config

The "Unknown command: acp" evidence is a **test-setup artifact, not a
toolchain gap.** Verified on this machine against the installed launcher
(`/usr/local/bin/isaac` → Cellar 0.1.19):

- `isaac acp --help` succeeds and top-level `isaac --help` lists `acp`. The
  installed launcher **does** expose the command.
- `acp` is **module-gated** — it is contributed by `:isaac.comm.acp`. At a root
  where that module is configured (e.g. the default root, which shows
  `isaac.comm.acp 0.1.5 ok`), `acp` resolves normally.
- The failing probe was `isaac --root /tmp/x acp`. `/tmp/x` is an **empty,
  unconfigured root** with no modules, so only the four builtin commands
  (config/init/logs/modules) are present. That is why `acp` was "Unknown"
  there — expected behavior for a bare root, not a missing feature.

Consequence for the proof:

- The scenario controls the argv, and `isaac-cli-server` forwards it verbatim
  (`dispatch.clj` → `["isaac" & argv]`). So the fixture must spawn against a
  **root that has `:isaac.comm.acp` configured and a stub/echo model** — pass
  `--root <fixture-root>` (or set up the default) so the subprocess resolves
  `acp`. This is ordinary scenario fixture setup, not a launcher change.
- Do **not** wait on the toolchain and do **not** swap to a different launcher.
  The installed `isaac` is the correct target; it just needs a configured root.

Proceed: implement the five interactive-driver steps, stand up a fixture root
with the ACP module + stub model, spawn `isaac acp` over the pipe, un-@wip, and
get `bb features features/integration.feature` green in isaac-cli-proxy.
