---
# isaac-i64h
title: Slash/CLI commands registered twice at boot -> :slash/override warnings
status: todo
type: bug
priority: normal
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-20T15:19:54Z
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
