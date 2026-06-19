---
# isaac-i64h
title: Slash/CLI commands registered twice at boot -> :slash/override warnings
status: todo
type: bug
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-19T22:22:26Z
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
