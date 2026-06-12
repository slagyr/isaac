---
# isaac-ptkg
title: 'Foundation pre-work: split isaac.config.api into read-side and isaac.config.runtime'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-12T12:49:21Z
updated_at: 2026-06-12T13:50:22Z
parent: isaac-brth
---

Phase A step 4 of the isaac-foundation extraction (see isaac-brth reshaping
note). isaac.config.api (foundation set) currently requires
change-source/configurator/install, dragging in isaac.comm.registry and
isaac.session.store. Split the facade.

- [x] New server ns src/isaac/config/runtime.clj: move install!,
      install-config-berths!, reload!, validate-config!, Reconfigurable /
      on-startup! / on-config-change!, reconcile!, slot-impl, ->name, and the
      change-source fns (watch-service-source, memory-source, start!, stop!,
      poll!, notify-path!) out of config/api.clj.
- [x] config.api retains the read side: load-config-result, normalize-config,
      load-config!, snapshot, dangerously-install-config!, root, default-root,
      env, set-env-override!, clear-env-overrides! — and drops its requires on
      change-source, configurator, install.
- [x] Re-point the ~13 server callers (server/app.clj, session/context.clj,
      hooks.clj, cron/service.clj, hail/bands.clj, comm/null.clj,
      session/cli.clj, session/store/*, charge.clj, bridge/prompt_cli.clj,
      api.clj, spec_helper.clj). Mechanical; can land in two green sub-steps
      (lifecycle fns, then change-source fns).

## Acceptance

- bb spec and bb features green.
- rg 'config.install|config.configurator|change.source' src/isaac/config/api.clj returns zero hits.

## Summary of Changes

Split the config facade into a read side (config.api) and a write/lifecycle side (new config.runtime), so read-only foundation callers no longer transitively pull in isaac.comm.registry / isaac.session.store via install/configurator/change-source.

- **New src/isaac/config/runtime.clj** — the server-side facade. Holds the moved delegating fns: install!, install-config-berths!, reload!, validate-config! (-> install); Reconfigurable, on-startup!, on-config-change!, reconcile!, slot-impl, ->name (-> configurator); watch-service-source, memory-source, start!, stop!, poll!, notify-path! (-> change-source). Docstrings preserved.
- **src/isaac/config/api.clj** — dropped its requires on change-source / configurator / install and removed those 16 fns (~100 lines). Retains the read side (load/snapshot/resolve/env). ns docstring updated to point lifecycle callers at config.runtime.
- **Re-pointed callers** (aliased the new ns as runtime, matching the config.* final-segment convention): src — api.clj, comm/null.clj, hail/bands.clj (require swapped: only used Reconfigurable), plus session/cli.clj, cron/service.clj, hooks.clj, bridge/prompt_cli.clj, server/app.clj (kept config for read-side, added runtime). spec — app_spec, server_steps, session_steps(+_spec), cron/service_spec, hooks_spec, configurator_steps, api_spec, hail/bands_spec.

The bean's caller list named context.clj, session/store/*, charge.clj, spec_helper — verified these use only read-side fns (snapshot/resolve-*/root/normalize/default-history-retention), which stay in config.api, so they needed no change.

### Acceptance
- bb spec green: 1858 examples, 0 failures.
- bb features green: 744 examples, 0 failures (the isaac-6q8c flaky --follow logs test passed this run too).
- rg 'config.install|config.configurator|change.source' src/isaac/config/api.clj -> zero hits. (Had to reword the new ns docstring to avoid the literal phrase "change source", which the regex dot matched.)



## Verification failed

HEAD: 38d30486049053f3a8aa324fda55264bb3b064ed
Working tree: clean

Missing required unit spec for new production namespace `src/isaac/config/runtime.clj`. Project testing discipline in AGENTS.md requires every namespace in `src/` to have a corresponding spec in `spec/`, and says a bean is not complete if new `src/` namespaces lack corresponding `spec/` files. This bean adds `src/isaac/config/runtime.clj` but `spec/isaac/config/runtime_spec.clj` does not exist (`find spec -path "*runtime_spec.clj" -print` returned no files).

The explicit grep acceptance check passed: `rg 'config\\.install|config\\.configurator|change\\.source' src/isaac/config/api.clj` returned zero hits. I did not continue to full suite closure after this blocking spec-coverage failure.

## Verification fix (missing spec)

Addressed the blocking verification failure: the new production namespace
`src/isaac/config/runtime.clj` lacked a corresponding spec, violating
AGENTS.md:114 ("Every namespace in src/ must have a corresponding spec in
spec/").

Added **spec/isaac/config/runtime_spec.clj** (17 examples, 33 assertions). Since
config.runtime is a pure delegation facade, the spec pins the delegation
contract for all 16 public fns — args forwarded unchanged and the source's
return value passed back — mirroring how spec/isaac/api_spec.clj tests its
delegating fns (with-redefs on the source for regular fns; reify for the
Reconfigurable protocol methods on-startup!/on-config-change!; protocol
re-export check for Reconfigurable itself).

Re-ran both suites green:
- bb spec: 1878 examples, 0 failures.
- bb features: 744 examples, 0 failures.
