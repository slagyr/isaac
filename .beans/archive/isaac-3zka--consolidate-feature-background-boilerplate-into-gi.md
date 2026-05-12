---
# isaac-3zka
title: "Consolidate feature Background boilerplate into 'Given default Grover setup'"
status: completed
type: task
priority: normal
created_at: 2026-04-23T21:03:49Z
updated_at: 2026-04-25T10:06:04Z
---

## Description

Roughly 50 of 94 feature files repeat the same 8-10 Background lines establishing a grover-based test setup: in-memory state dir, grover provider, echo model, main crew with 'You are Isaac.' soul. Only the opt-ins (log capture, tool registration, server boot, ACP initialize) differ across features.

Introduce a single rich step:

  Given default Grover setup

That writes to the in-memory fs:
- target/test-state as state dir
- config/providers/grover.edn (api: grover)
- config/models/grover.edn (model: echo, provider: grover, context-window: 32768)
- config/crew/main.edn (model: grover, soul: 'You are Isaac.')

Optional variant for features that need a different state-dir name:
  Given default Grover setup in "my-state-dir"

Opt-ins stay as separate steps (already existing or to be standardized):
- And log capture is enabled  (or reuse 'config: log.output = memory')
- And the built-in tools are registered
- And the Isaac server is running
- And the ACP client has initialized

Scope:
- Add the step def to spec/isaac/features/steps/ (likely config.clj or a new setup.clj)
- Migrate the ~50 features currently repeating the grover+main boilerplate
- Features with NO Background or a minimal state-dir-only Background stay as-is (different test shape)
- Features that declare additional models/crew on top of main keep those declarations; only the baseline gets collapsed

Acceptance:
1. 'Given default Grover setup' is registered
2. The ~50 features that currently repeat the baseline collapse to one Background line (plus any opt-ins)
3. grep -rn 'config/crew/main.edn' features/ returns fewer matches — only features that need a non-default main
4. bb features and bb spec pass

Note: depends on isaac-fej2 (disk-config migration) being far enough along that features are already writing disk config rather than using 'the following crew exist' atoms.

## Notes

Verification failed: acceptance item 2/3 still not met. The new step is registered and both bb spec / bb features are green, but the migration still appears incomplete. Current grep shows 47 feature uses of 'Given default Grover setup' and still 33 matches for 'config/crew/main.edn' in features/. Several remaining matches are still baseline setup or simple opt-in overrides rather than obvious non-default-main exceptions (for example features/session/prompt_building.feature, features/providers/openai/auth.feature, features/bridge/tool_visibility.feature, and features/acp/cancel.feature).

