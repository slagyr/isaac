---
# isaac-ptkg
title: 'Foundation pre-work: split isaac.config.api into read-side and isaac.config.runtime'
status: in-progress
type: task
priority: normal
created_at: 2026-06-12T12:49:21Z
updated_at: 2026-06-12T13:27:12Z
parent: isaac-brth
---

Phase A step 4 of the isaac-foundation extraction (see isaac-brth reshaping
note). isaac.config.api (foundation set) currently requires
change-source/configurator/install, dragging in isaac.comm.registry and
isaac.session.store. Split the facade.

- [ ] New server ns src/isaac/config/runtime.clj: move install!,
      install-config-berths!, reload!, validate-config!, Reconfigurable /
      on-startup! / on-config-change!, reconcile!, slot-impl, ->name, and the
      change-source fns (watch-service-source, memory-source, start!, stop!,
      poll!, notify-path!) out of config/api.clj.
- [ ] config.api retains the read side: load-config-result, normalize-config,
      load-config!, snapshot, dangerously-install-config!, root, default-root,
      env, set-env-override!, clear-env-overrides! — and drops its requires on
      change-source, configurator, install.
- [ ] Re-point the ~13 server callers (server/app.clj, session/context.clj,
      hooks.clj, cron/service.clj, hail/bands.clj, comm/null.clj,
      session/cli.clj, session/store/*, charge.clj, bridge/prompt_cli.clj,
      api.clj, spec_helper.clj). Mechanical; can land in two green sub-steps
      (lifecycle fns, then change-source fns).

## Acceptance

- bb spec and bb features green.
- rg 'config.install|config.configurator|change.source' src/isaac/config/api.clj returns zero hits.
