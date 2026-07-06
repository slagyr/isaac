---
# isaac-gnji
title: 'ACP runtime loses the top-level --root: isaac --root <dir> acp resolves the default user root'
status: in-progress
type: bug
priority: normal
created_at: 2026-07-06T19:55:07Z
updated_at: 2026-07-06T20:06:24Z
blocking:
    - isaac-lcay
---


## Gap

Diagnosed live by the lcay worker (scrapper, 2026-07-06, hail e01d4351): `isaac --root <fixture-root> acp` resolves the acp command, but the ACP CLI/runtime ignores or loses the top-level root from `isaac.main/run` — the spawned ACP session surfaced the default user-root model/provider state (real Anthropic config) instead of the fixture root's echo model. Repeatable with a prepared disposable fixture root.

## Impact

Blocks isaac-lcay (e2e: remote ACP session over the generic cli pipe): the accepted proof requires the subprocess `isaac acp` to run against a disposable fixture root, and every workaround (mutating the real default root, injecting env into the spawned launcher) is unreviewable. Any other subcommand that spawns or re-enters the runtime may share the propagation gap — audit while fixing.

## Notes

- Suspect seam: root flows from `isaac.main/run` global options into the command runtime; the ACP path likely re-derives root from the environment/home instead of the passed option.
- Acceptance sketch (planner to confirm during spec): `isaac --root <fixture> acp` must resolve models/config/sessions exclusively from <fixture>; lcay's e2e then proceeds unmodified.
