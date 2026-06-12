---
# isaac-t7mq
title: 'Foundation pre-work: :isaac.config/check contribution berth'
status: in-progress
type: task
priority: normal
created_at: 2026-06-12T12:50:29Z
updated_at: 2026-06-12T15:28:24Z
parent: isaac-brth
blocked_by:
    - isaac-c0n8
    - isaac-m37j
---

Phase A step 10 of the isaac-foundation extraction (see isaac-brth reshaping
note). The loader's server-domain validation passes move to a server ns and
flow back in through a foundation-declared :isaac.config/check berth (map of
check-id -> {:fn <symbol>}, no :factory; gathered directly during load like
:isaac.config/schema).

- [ ] Declare :isaac.config/check in the foundation manifest.
- [ ] New server ns src/isaac/config/checks.clj: move check-tools,
      check-slash-commands, check-comms (+ comm-base-fields, which uses
      schema/comm-instance), check-provider-types, resolved-provider-errors
      (now calls isaac.config.resolve/resolve-provider),
      check-crew-compaction, manifest-ref-errors (+ manifest-schema-kinds),
      comm-reserved-schema-errors, find-manifest-entry helpers.
- [ ] Make the generic engines they use public foundation fns
      (annotation-errors*, semantic-errors, validate-manifest-config,
      validation-context — consider a dedicated isaac.config.validation ns if
      loader.clj is unwieldy).
- [ ] Loader invokes each contributed check with a ctx map
      {:config :raw-providers :module-index :root :result} and concatenates
      {:errors :warnings} into the existing aggregation (loader.clj:1147-1161).
- [ ] One green sub-step per check; server manifest contribution added as each
      moves.
- [ ] Verify error-key parity against loader_spec golden outputs
      (berths/normalize-errors post-processes and errors sort by :key, so
      contributed-check ordering should be cosmetic).
- [ ] Audit known-provider-ids / known-comm-ids / declared-module-api-ids for
      zero callers — delete or relocate to isaac.config.checks.

## Acceptance

- bb spec and bb features green after each sub-step.
- config/loader.clj contains no server-domain check fns; all checks arrive
  via the berth.
