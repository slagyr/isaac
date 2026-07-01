---
# isaac-w8dw
title: Restore schema_composition feature into isaac-foundation
status: completed
type: task
priority: normal
created_at: 2026-06-15T17:03:15Z
updated_at: 2026-06-15T19:08:32Z
---

Restore baseline features/module/schema_composition.feature ("Module schema composition") into isaac-foundation.

Target: isaac-foundation/features/module/schema_composition.feature + step-defs/helpers.

Implementation notes (from w8dw research):
- Re-home onto foundation fixtures (marigold.comm.parlor / marigold.providers.fizz) + a NEW gherkin step that
  binds module-loader/*foundation-index-override* to the chartroom test index (mirror config-marigold/with-manifest;
  pattern proven in spec/isaac/config/cli/schema_spec.clj).
- Chartroom test schema carries cross-validations (:watch defaults -> berth-exists?/gauge-exists?), so scenarios
  must build on a VALID baseline config (like mutate_spec's aboard), not a bare slot.
- DROP the baseline :tools scenario — no foundation fixture for it.

Acceptance: schema_composition green under isaac-foundation bb features, no @wip/pending, real schema-compose
machinery via real fixture modules (no faked steps).

SCOPE CHANGE (was: also activation.feature): activation.feature is NOT foundation-restorable — it tests
server-side LAZY activation (server started, :module/activated for a user comm module isaac.comm.telly,
:telly/started, :comms table) that foundation's eager loader doesn't have; the generic remainder is already
covered by foundation's discovery/berth_contributions/config_berth_processing features. Moved to isaac-shnq.


## Implemented

Repo: isaac-foundation @ 160db84
Files: features/module/schema_composition.feature (new), spec/isaac/config/config_steps.clj (+chartroom step, override binding in load-result), spec/isaac/config/marigold.clj (+chartroom-test-index accessor)
Verify: cd isaac-foundation && bb features features/module/schema_composition.feature
Result: 7 examples, 0 failures, 7 assertions; no @wip/pending. Full suite: features 84/0, spec 747/0.

Re-homed onto real fixtures marigold.comm.parlor (signals: :loft/:color/:mood with [:present-when?]/[:one-of?]) and marigold.providers.fizz (foundries: :fizz-level :int). New step \"the chartroom fixture modules are available\" binds *foundation-index-override* to the chartroom test index (per baked-in notes). Real schema-compose machinery, no fakes. :tools scenario dropped (no foundation fixture). Note: foundry validation error keys are dotted (foundries.tea.fizz-level) vs signals open-map bracketed (signals[:bert].loft) -- expected, different validation paths. activation.feature handled separately under isaac-shnq.
