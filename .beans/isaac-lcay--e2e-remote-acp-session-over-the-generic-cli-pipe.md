---
# isaac-lcay
title: 'e2e: remote ACP session over the generic cli pipe'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-07-03T15:34:48Z
updated_at: 2026-07-07T01:24:51Z
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

## Worker observations (2026-07-06, scrapper)

A concrete contract conflict remains on the real subprocess path the accepted
proof requires.

What I verified in the live environment:
- `/usr/local/bin/isaac` does expose `acp`, and the command is module-gated.
- A fixture root can be prepared with `:isaac.comm.acp` and ACP test config.
- The real proof path still depends on selecting that disposable root from the
  spawned `isaac` subprocess behind `/cli`.
- Driving the installed launcher as `isaac acp` against a disposable fixture did
  not stay inside the fixture's intended model/provider state for ACP runtime:
  initialize/prompt behavior surfaced the default user-root Anthropic model and
  returned a live-credit error instead of the fixture echo-model response.
- The prior lcay investigation already established the likely seam: `isaac
  --root <fixture-root> acp` appears to resolve the command but ACP runtime does
  not honor the top-level root consistently from `isaac.main/run`.

Implication:
- The accepted e2e scenario cannot currently prove a disposable fixture-root ACP
  session via forwarded argv alone, even though the generic cli pipe itself is
  present.
- Finishing lcay as written would require an unreviewed workaround (mutating the
  real default root, injecting process env for the spawned launcher, or fixing
  ACP root propagation in product code outside the approved bean scope).
- Returning to planner for scope/unblock decision is safer than baking the wrong
  proof harness.

## Worker observations (2026-07-06, scrapper) — root propagation blocker confirmed

Fresh verification this turn narrowed the conflict to real ACP root selection on
the accepted subprocess path.

What I verified:
- A targeted gherclj run of the accepted scenario still reports it pending (`bb -m gherclj.main -f features -s isaac.**-steps -s isaac.cli-proxy.feature-bootstrap -t slow -t wip features/integration.feature:41`), so the approved interactive-driver steps remain unimplemented work in `isaac-cli-proxy`.
- The installed launcher is not the blocker by itself: `/usr/local/bin/isaac acp --help` succeeds, and `/usr/local/bin/isaac --root /tmp/lcay-fixture acp --help` also resolves the command.
- A disposable fixture root at `/tmp/lcay-fixture` with `:isaac.comm.acp` plus Grover echo-model config works when the launcher root is supplied via environment: `ISAAC_ROOT=/tmp/lcay-fixture isaac acp` starts, accepts ACP JSON-RPC over stdio, and prompt turns use the fixture echo model.
- The accepted `/cli` proof path cannot rely on that environment workaround; `isaac-cli-server` forwards only argv to `isaac`, and the scenario contract expects disposable-root selection from the launched command path.
- On that path, real turns launched as `isaac --root /tmp/lcay-fixture acp` still appear to initialize against the default `~/.isaac` model/provider state rather than the fixture config. The observed failure mode is a live default-root provider/model being used during initialize/prompt instead of the fixture echo-model response.
- This is consistent with the durable suspicion that ACP runtime setup is not honoring the top-level `:root` chosen by `isaac.main/run` once ACP runtime resolves its config.

Implication:
- The generic cli pipe is present, but the accepted disposable-fixture-root ACP proof cannot currently be completed via forwarded argv alone on the current product behavior.
- Completing lcay as written would require either an unapproved workaround (`ISAAC_ROOT` env injection / default-root mutation / pointer-file harnessing) or a product fix so `isaac --root <fixture-root> acp` actually uses that root for runtime behavior.
- So from `isaac-cli-proxy` alone I can implement the missing interactive-driver steps, but I still cannot satisfy the accepted *real subprocess + isolated echo-model fixture* proof hermetically.
- Returning to planner remains the correct handoff until scope or prerequisites are adjusted.

## Worker observations (2026-07-06, scrapper) — interactive steps implemented, real /cli subprocess still mismatched

I implemented the approved interactive-driver family locally in `isaac-cli-proxy` and wired a real slow-feature harness around a live cli-server plus ACP JSON-RPC stdin/stdout driving so the accepted scenario is no longer pending from missing steps alone.

What I verified this turn:
- `bb spec spec/isaac/cli_proxy/proxy_spec.clj spec/isaac/cli_proxy/integration_steps.clj` passes in `isaac-cli-proxy`.
- Existing repo suites still pass with the local harness work present: `bb spec`, `bb features`, and `bb features-slow` are green in `isaac-cli-proxy`.
- A disposable fixture root with `:isaac.comm.acp` pinned to `3b48d97` and model provider `"grover:openai"` works when driven directly through the installed launcher: `isaac --root <fixture-root> acp` accepts ACP JSON-RPC, returns initialize/session/new results, and streams a `ping` response from the Grover echo path.
- The same fixture root also resolves the command surface directly: `isaac --root <fixture-root> acp --help` succeeds and `isaac --root <fixture-root> modules` reports `isaac.comm.acp 0.1.5 ok`.
- But the accepted **real** path (`isaac remote ...` -> cli-proxy -> cli-server -> spawned subprocess `isaac --root <fixture-root> acp`) still fails before ACP initialize: the subprocess stdout returns `Unknown command: acp` plus a top-level command list that lacks `acp` entirely.
- This failure is stricter than the earlier root-propagation suspicion: on the cli-server-spawned subprocess path, even explicitly forwarded `--root <fixture-root>` does not yield the same command/module surface that direct launcher invocation sees.

Implication:
- The approved interactive-driver work is implementable and mostly done, but the accepted proof is still blocked by a real subprocess launcher/bootstrap mismatch behind cli-server, not by pending steps anymore.
- To finish lcay as written, planner/module owners need to decide whether the proof may use a different launcher/bootstrap seam, or whether a prerequisite fix is required so the cli-server-spawned `isaac` process sees the configured fixture-root ACP module surface consistently.

## Planner (2026-07-06, Micah via planning partner)

Decision on the plan-review-loop return: the accepted proof stays AS WRITTEN — the real remote path (remote -> cli-proxy -> cli-server -> spawned acp) is the point of this bean; a different launcher/bootstrap seam would gut it. The subprocess module-surface mismatch is filed as isaac-7t1k (high), which now gates this bean. Resume lcay once isaac-7t1k is fixed; the interactive-driver work already done stands.

## Resolution (unverified — for verifier)

Implemented in `isaac-cli-proxy` commit **8adcbb6**.

What changed:
- un-`@wip`ed the accepted slow integration scenario in `features/integration.feature`
- added the approved interactive-driver step family in `spec/isaac/cli_proxy/integration_steps.clj`
- the harness now provisions a disposable fixture root with `:isaac.comm.acp` plus an echo-model config, starts a real cli-server, runs `isaac remote` interactively, drives ACP JSON-RPC over stdin/stdout, and asserts initialize/session/new/prompt/clean-shutdown over the real generic pipe
- bumped the cli-proxy test dependency / harness pin for `io.github.slagyr/isaac-cli-server` to **cf80a29** so the acceptance run includes the shipped `isaac-7t1k` explicit-root spawn fix

Verification run on the final rebased commit:
- `bb spec`
- `bb features`
- `bb features-slow`
- targeted accepted proof: `bb features-slow features/integration.feature:40`

Observed result:
- the accepted remote ACP proof is green end-to-end through `remote -> cli-proxy -> cli-server -> spawned isaac acp`
- argv forwarding includes explicit `--root <fixture-root>` and the spawned subprocess resolves ACP from that fixture root
- initialize response arrives before EOF, session/new returns a session id, prompt response streams before exit, stdin close yields exit code 0



## Verify fail (attempt 1, 2026-07-07): documented acceptance command runs 0 examples because integration.feature is tagged @slow
