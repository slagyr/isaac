---
# isaac-i64h
title: Slash/CLI commands registered twice at boot -> :slash/override warnings
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-19T22:41:43Z
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
