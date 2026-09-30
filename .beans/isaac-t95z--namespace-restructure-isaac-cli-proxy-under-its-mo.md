---
# isaac-t95z
title: 'Namespace restructure: isaac-cli-proxy under its module id'
status: completed
type: task
priority: normal
created_at: 2026-09-30T14:12:24Z
updated_at: 2026-09-30T22:31:37Z
parent: isaac-vyqs
---

Micah, 2026-09-30. **A module's code lives under its module id.** isaac-foundation → `isaac.foundation.*`, isaac-agent → `isaac.agent.*` (e.g. `isaac.session.frequencies` → `isaac.agent.frequencies`), isaac-claude-code (`:isaac.provider.claude-code`) → `isaac.provider.claude-code.*`, isaac-episodes (`:isaac.session.episodes`) → `isaac.session.episodes.*`, comm modules → `isaac.comm.<name>.*`, and so on. Source, specs, spec-support, step namespaces, manifest symbols (`:factory`, berth entries), bb tasks and docs all move together. Clean cutover: no alias namespaces.

## Order: inside-out (Micah)

1. isaac-foundation (requires nobody).
2. isaac-agent: bump to the new foundation, update its foundation requires, rename its own.
3. Every other module, in parallel: bump foundation + agent (this absorbs the pin sweep isaac-5x21), update requires, rename its own. A module that requires another leaf (gchat/gmail → google) goes after that leaf.

Each repo is touched once.

## Deploy freeze

An installed Isaac runs one foundation and one agent, so zanebot/yopp don't take the new foundation until every installed module has migrated. Each repo's main stays green on its own pins meanwhile.

## Every child bean also

- greps zanebot and yopp live config (read-only, `ssh zane@zanebot…` / `ssh yopp@yopp`) for namespace names in data (hook `:factory`, embedding `:namespace`, etc.) and lists required config edits in the bean;
- updates its handbook chapter and README where namespaces are named;
- is ungated (mechanical refactor): acceptance = full CI green on main, a grep showing no namespaces outside the module's id prefix in src/spec (list justified exceptions), planner verification.

## Blocked — do not land (2026-09-30)

Work done on branch `bean/isaac-t95z` (pushed, not merged): `git -C isaac-cli-proxy worktree add -b bean/isaac-t95z ../isaac-cli-proxy-isaac-t95z origin/main`, commit `b55e141`.

**Namespace mapping.** This repo's own src/spec namespaces were already `isaac.cli-proxy.*` (cli.clj, protocol.clj, proxy.clj, token.clj, ws.clj + their specs) except one stray file: `src/isaac/config/pointer.clj` (ns `isaac.config.pointer`) → moved to `src/isaac/cli_proxy/config/pointer.clj` (ns `isaac.cli-proxy.config.pointer`). Its `read-config` used to do a raw `(edn/read-string (slurp (path)))` on the bootstrap pointer file; once moved out of `isaac.config.*`/`isaac.foundation.config.*`, `config-bypass-lint` correctly flags that as a bypass. Fixed by delegating to `isaac.foundation.config.root/pointer-config` (the same sanctioned reader `isaac.foundation.main` already uses to resolve this exact `:cli :remote` key for implicit routing) instead of duplicating the raw slurp — not a lint dodge, an existing sanctioned primitive for exactly this file. `write-config!` is untouched (raw `spit`, never flagged).

Updated every foundation/agent require (`isaac.cli.*` → `isaac.foundation.cli.*`, `isaac.config.*` → `isaac.foundation.config.*`, `isaac.fs`/`isaac.logger`/`isaac.nexus` → `isaac.foundation.*`, `isaac.module.*` → `isaac.foundation.module.*`, `isaac.spec-helper` → `isaac.foundation.spec-helper` (matches isaac-http's own precedent — foundation and pre-rename agent both shipped a byte-identical `isaac.spec-helper.clj`; ambiguous which one it, so followed the peer convention), `isaac.step-tables`/`isaac.util.jsonrpc` → `isaac.agent.*`, `isaac.session.session-steps` → `isaac.agent.session.session-steps`), the manifest `:factory`, and prose namespace mentions in PROTOCOL.md, the handbook chapter, and a bb.edn task doc-string. Bumped pins: foundation → `06d58b75bc52b3e118dc8e81569096de2532a0d4` (exact, per the brief), agent → `123d71850b480dc0859886e1a4fa53e082c258f1`, http → `56998543b3e5c40593d2a3ea97b16550e3731463`. isaac-cli-server and isaac-acp left pinned at their current (unmigrated) shas per the brief. Added a `gherclj` bb task (mirroring isaac-foundation's own) since the bumped isaac-foundation-test-support's `run-features-slow!` now shells out to `bb -Sforce gherclj -t slow -t ~wip <location>` per @slow scenario for classpath isolation, and this repo had no such task.

**Live-config greps (read-only, no edits).** Checked both zanebot (`ssh zane@zanebot.tail66e5f8.ts.net`) and yopp (`ssh yopp@yopp`) `~/.isaac/config` for `isaac.cli-proxy`, `isaac.config.pointer`, `isaac.cli.{host,registry,api}`, `isaac.config.{env,root,cli.common}` — zero hits on either host. `:isaac.cli-proxy` isn't installed/referenced on either deployment yet, so no config edits are needed.

**Test results.** `bb spec`: 27/27. `bb features` (excludes @slow): 29/29. `config-bypass-lint` / `lint-cli-host`: ok. All green, including on a fresh GitHub Actions runner (see below).

**Blocker — `bb features-slow` (features/integration.feature, 6 @slow scenarios) fails with:**
```
config-schema collision at :comms [:schema :value-spec :factory]: isaac.agent.comm.factory/create! vs isaac.comm.factory/create!
```
Reproduces identically on a **fresh GitHub Actions runner** (dispatched `CI Tests` via `workflow_dispatch` against `bean/isaac-t95z`, run [36756603717](https://github.com/slagyr/isaac-cli-proxy/actions/runs/36756603717) — not a local-machine config leak; first suspected that, ruled it out since the runner has no `~/.isaac`). Root cause: `integration_steps.clj`'s harness (`ensure-cli-server-route!` / `ensure-acp-command!`) merges `:isaac.cli-server` and `:isaac.comm.acp` module coordinates (both still pinned to their **unmigrated** shas — `isaac-cli-server` 007da61d/7c7774b8, `isaac-acp` c3560df7) into `:server-config :modules`, and `isaac.foundation.module.discovery/discover!` resolves those coordinates for real at feature-boot time. Neither manifest itself declares `:comms` (confirmed — grepped both), so the old-style `isaac.comm.factory/create!` value must come in transitively through one of those two unmigrated repos' own pinned (pre-rename) `isaac-agent` dependency getting resolved onto the same classpath as our now-renamed, directly-pinned `isaac-agent` (123d718) — i.e., exactly the "test dependency on another leaf that has NOT migrated breaks your suite" case the shared brief calls out. Per the brief: **stopping here rather than landing red or working around it**, since isaac-cli-server/isaac-acp are out of this bean's scope and haven't migrated yet.

**Recommendation for the planner:** either (a) isaac-cli-server and isaac-acp need their own isaac-vyqs migration beans landed first (their manifests both also declare the old `:factory isaac.module.protocol/module`, which would itself fail to resolve against the new foundation the moment they're actually activated, not just schema-composed — so they need to migrate regardless), or (b) `features/integration.feature`'s harness needs to stop dynamically resolving real, unmigrated sibling-repo coordinates during discovery (a bigger change, out of scope for a namespace-rename bean). Not proposing either myself — flagging for a decision.

**Not yet done because of the blocker:** squash+push to main, and final `bb ci`/CI confirmation. Branch `bean/isaac-t95z` and worktree `../isaac-cli-proxy-isaac-t95z` are left in place (not deleted) pending a decision.

## Follow-up (2026-09-30, later) — isaac-cli-server bump isolated the real blocker to isaac-acp

Coordinator confirmed isaac-cli-server has migrated (main `f1477a7`, isaac-ps7a). Bumped this repo's isaac-cli-server pin from `7c7774b8`/`007da61d` (both spots: deps.edn/bb.edn's product pin, and `integration_steps.clj`'s own hardcoded `cli-server-git-coord`) to `f1477a71c2efa8f7992fe4a7243eb14cc74c3e7e`, committed on `bean/isaac-t95z`, and re-ran `bb features-slow`.

**Result: the `:comms` config-schema collision is gone.** 3 of 6 `features/integration.feature` scenarios now pass cleanly (the plain remote/token ones). The remaining 3 — the ACP-specific scenarios (`isaac-lcay` generic pipe, `isaac-dqy9` ACP session inside the server, `isaac-dqy9` remote prompt turn visibility) — now fail differently:
```
Could not locate isaac/comm/registry.bb, isaac/comm/registry.clj or isaac/comm/registry.cljc on classpath.
```
(and one masked earlier in the same run as "Reached EOF before ACP initialize response" / "Could not locate isaac/cli/api.bb..."). This confirms the collision genuinely came from isaac-cli-server (now fixed) — the remaining failure is isaac-acp still requiring old, pre-rename namespaces (`isaac.comm.registry`, `isaac.cli.api`) that no longer exist against the renamed foundation/agent. Per the coordinator: isaac-acp's own migration (isaac-wqs8) waits on isaac-episodes, which waits on a push by Micah — out of this bean's control.

**Stopping here per the coordinator's instruction.** `blocked_by: isaac-wqs8` added above. Pin bump committed and pushed to `bean/isaac-t95z` (not main). `bb spec` 27/27, `bb features` 29/29, both lints green with the new pin. `bb features-slow` is 3/6 (the 3 ACP scenarios blocked on isaac-wqs8 → isaac-acp migration).

## Landed on main (2026-09-30, final)

Coordinator confirmed isaac-acp has migrated too (main `07f9fb8`, isaac-wqs8), and gave current mains for the rest: isaac-agent `f9530426d04b6f66f17ae51f6f9a1a697531b39d` (still pins foundation `06d58b75`, so this repo's own foundation pin stays put), isaac-http `5dedcafbc0fcc199a72e1d378ba78d1db9305750`, isaac-cli-server `f1477a71c2efa8f7992fe4a7243eb14cc74c3e7e`.

Bumped all three (acp, agent, http) in the worktree — deps.edn, bb.edn, and `integration_steps.clj`'s hardcoded `acp-module-coord`/`cli-server-git-coord` — rebased on `origin/main` (no-op, main hadn't moved), and reran `bb ci` with `HOME` isolated to a scratch dir. **Fully green**: `bb spec` 27/27, `bb features` 29/29, `bb features-slow` 6/6 (all six `features/integration.feature` scenarios, including the three ACP ones that were blocked). Confirmed again on a fresh GitHub Actions runner via `workflow_dispatch` against `bean/isaac-t95z` (run [36785709276](https://github.com/slagyr/isaac-cli-proxy/actions/runs/36785709276)) before touching main.

Squashed the branch's three commits to one, pushed `267b8aa:main` (accepted, no classifier denial), fast-forwarded the shared `isaac-cli-proxy` checkout, and confirmed `CI Tests` green on `main` (run [36785844754](https://github.com/slagyr/isaac-cli-proxy/actions/runs/36785844754)).

**main-sha: isaac-cli-proxy 267b8aa**

Final pin set: isaac-foundation `06d58b75bc52b3e118dc8e81569096de2532a0d4`, isaac-agent `f9530426d04b6f66f17ae51f6f9a1a697531b39d`, isaac-http `5dedcafbc0fcc199a72e1d378ba78d1db9305750`, isaac-cli-server `f1477a71c2efa8f7992fe4a7243eb14cc74c3e7e`, isaac-acp `07f9fb813e8f6b6e7b5f10776e30825796315ea4` — all migrated. `blocked_by` cleared (isaac-on0o and isaac-wqs8 both landed); tagged `unverified` for `/verify`. Branch `bean/isaac-t95z` and worktree `../isaac-cli-proxy-isaac-t95z` deleted.

## Planner verification (2026-09-30)

Verified on 267b8aa: all namespaces isaac.cli-proxy.*, features-slow 6/6, CI green.
