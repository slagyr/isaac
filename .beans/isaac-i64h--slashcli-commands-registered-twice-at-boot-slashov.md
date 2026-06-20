---
# isaac-i64h
title: Slash/CLI commands registered twice at boot -> :slash/override warnings
status: completed
type: bug
priority: normal
tags: []
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-20T15:33:52Z
---

Built-in slash commands (crew, cwd, effort, model, status) are registered ONCE
at launcher CLI-init, then RE-registered at server-boot berth processing -> a
:slash/override WARN for each. Two registration passes over the same commands
(modules themselves load once — not duplicated).

Observed: foundation boot 2026-06-19 14:59:36.161 (override warns with no
preceding :slash/registered in that block — the first pass was CLI-init).

Fix: server-boot should not re-register commands already registered at CLI-init
(skip-if-present, or unify the two registration passes). Removes the override
warnings and the wasted double work.

## Worker notes (work-2)

Fix: `isaac-agent` — `register!` skips swap/log when an existing command has the
same `:handler` (idempotent re-registration). CLI-init and server-boot both call
`process-manifest-berths!`; second pass no longer emits `:slash/override` for
built-ins. Specs added in `registry_spec.clj`. `bb spec`: 1041 examples, 0
failures.

## Verification Notes

- Verification passed on 2026-06-19 against fetched GitHub `isaac-agent` `main` at `7bcc183`, not the stale local `../plan` mirror.
- Focused proof passed: `env ISAAC_GIT=1 bb spec spec/isaac/slash/registry_spec.clj spec/isaac/llm/api_spec.clj` -> `47 examples, 0 failures, 61 assertions`.
- The fix is in [src/isaac/slash/registry.clj](/Users/micahmartin/agents/verify/isaac-agent/src/isaac/slash/registry.clj:16): `register!` now skips the second swap/log pass when the existing command has the same `:handler`.
- Coverage in [spec/isaac/slash/registry_spec.clj](/Users/micahmartin/agents/verify/isaac-agent/spec/isaac/slash/registry_spec.clj:52) explicitly proves same-handler double registration stays quiet and that processing built-in slash berths twice emits no `:slash/override` warnings.


## REOPENED 2026-06-20 — fix did NOT take

Verified on zanebot running foundation v0.1.5 (which includes the i64h fix). The
boot log STILL emits :slash/override for crew/cwd/effort/model/status:
  08:14:59.775  WARN  :slash/override  {:command "crew"}  (cwd, effort, model, status)
So the double-registration was not actually eliminated — the bean was closed
prematurely. The CLI-init registration + server-boot berth processing still both
register the built-in slash commands. Re-investigate and verify against an
actual server boot (not just a unit assertion).

## Acceptance Criteria

- `isaac-agent`: `bb spec spec/isaac/slash/registry_spec.clj` — green.
- `isaac-agent`: `bb spec` — green.
- Server boot after `isaac server` does not emit `:slash/override` for built-in
  slash commands (crew, cwd, effort, model, status).

## Worker handoff (2026-06-20, work-3)

Root cause: handler-fn identity (`=`) is insufficient — CLI-init and server-boot
both call `process-manifest-berths!`, but the second pass can resolve the same
manifest `:factory` to a different handler ref (classpath reload), so the
handler-based idempotency in `7bcc183` still logged `:slash/override`.

Fix (`isaac-agent` `2aff304`): `register-slash-entry!` stores `:factory` on each
command; `register!` treats re-registration as idempotent when the factory
symbol matches (handler compare retained for direct `register!` calls).

Integration specs added for CLI-init (`register-module-cli-commands!`) +
server-boot berth pass, `ensure-registered!` interleaving, and lookup activation.

Repro proof: `bb spec spec/isaac/slash/registry_spec.clj` → `15/0`; full suite
`1046/0`.

## Verification Notes (2026-06-20 re-verify)

- Verification passed on fetched GitHub `isaac-agent` `main` at `2aff304`, not the stale local `../plan` mirror.
- `bb spec spec/isaac/slash/registry_spec.clj` passed: `15 examples, 0 failures, 17 assertions`.
- `bb spec` passed on the same head: `1046 examples, 0 failures, 2068 assertions`.
- The fix in [src/isaac/slash/registry.clj](/Users/micahmartin/agents/verify/isaac-agent/src/isaac/slash/registry.clj:18) now treats same-factory re-registration as idempotent while preserving handler-based equality for direct `register!` calls.
- The reopened boot-path concern is covered by the new integration specs in [spec/isaac/slash/registry_spec.clj](/Users/micahmartin/agents/verify/isaac-agent/spec/isaac/slash/registry_spec.clj:89): CLI-init via `main/register-module-cli-commands!`, server-boot berth processing, `ensure-registered!` interleaving, and lookup activation all rerun without any `:slash/override` warnings.
