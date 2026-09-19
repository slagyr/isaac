---
# isaac-addw
title: 'config validate falsely rejects crews on a module-contributed session policy (episodes): the check reads the runtime registry, not the module index'
status: completed
type: bug
priority: high
tags:
    - config
    - agent
created_at: 2026-09-19T02:11:47Z
updated_at: 2026-09-19T02:47:27Z
---

## Problem (yopp + zanebot, 2026-09-18)

`isaac config validate` reports `crew.<x>.session-policy - references undefined session policy (got "episodes"); known: chronicle` on every host where a crew uses `:episodes`, even though the episodes module is installed and the server runs that policy fine (yopp's `yopp` crew, zanebot's two episode crews).

`isaac-agent` `config/checks.clj:229 check-session-policy` asks the RUNTIME registry (`session-policy/known-policy-names` → `registered-names`) — which only holds policies whose `:isaac.agent/session-policy` berth factory has run. In the server that is boot; in the `config validate` CLI process the berth factory never runs, so only the built-in `chronicle` is known. The module's manifest contribution (`:isaac.agent/session-policy {:episodes {:factory isaac.session.policy.episodes/create}}`) is right there in the module index and is ignored.

Consequences beyond the false error: `config set` refuses writes into a crew it considers invalid; deploy checklists use `validate` as a gate; operators learn to ignore validate.

## Fix
Derive known policy names the way comm kinds are validated (manifest-side, `[:registered-in? :isaac.http/comm]` / `isaac.config.comm-kinds`): built-ins ∪ every `:isaac.agent/session-policy` key contributed by a module in the index. Keep the runtime registry for `create`; validation must not depend on registration having happened. Probably a `known-policy-names` that takes the module index (or reads `registered-in/*module-index*`), with the runtime registry as a fallback.

## Scenario (@wip, worker writes — isaac-agent features/config/*.feature, config-validate family; existing steps: module fixture with a manifest contribution, `isaac is run with "config validate"`, stdout/stderr assertions)
1. a crew on a session policy contributed by an installed module validates cleanly in the CLI (module fixture contributes `:isaac.agent/session-policy {:lantern …}`; crew `:session-policy :lantern`; `config validate` → exit 0, no "undefined session policy").
2. a crew on a policy no module contributes still errors, naming the known set including the module's (`known: chronicle, lantern`).
3. `config set crew.<x>.model …` on a crew using a module-contributed policy succeeds (the false error no longer blocks mutation).

## Acceptance
```
cd isaac-agent && bb features features/config && bb spec spec/isaac/config && bb ci
```
Field: `isaac config validate` on yopp and zanebot → 0 errors (the `tools.directories` warning is separate).

## Handoff

branch: bean/isaac-addw @ b1c02b0 (base origin/main@76320fa)

`known-policy-names` unions runtime factories with `:isaac.agent/session-policy` keys from the module index. `check-session-policy` reads that set. Fixture module `isaac.session.lantern`. Specs + `bb features features/config` + `bb ci` green. Do not land/pin.


## Landed on main (2026-09-19)

main-sha: isaac-agent e58263755f1bc43aa590b01366cbb1456ffaab69
