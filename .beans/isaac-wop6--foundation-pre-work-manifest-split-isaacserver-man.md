---
# isaac-wop6
title: 'Foundation pre-work: manifest split — :isaac.server manifest, berth-by-berth move'
status: completed
type: task
priority: normal
created_at: 2026-06-12T12:49:54Z
updated_at: 2026-06-12T15:16:30Z
parent: isaac-brth
blocked_by:
    - isaac-nst5
    - isaac-0cc4
---

Phase A step 7 of the isaac-foundation extraction (see isaac-brth reshaping
note). Split src/isaac-manifest.edn (today: ALL berths + contributions) into
the foundation manifest (:isaac.core — :cli berth + init only) and a server
manifest (:isaac.server, discovered via the :builtin? mechanism). Each
sub-step moves one berth decl + its contributions and lands green; decl and
contributions can move independently since find-berth-decl scans the whole
index.

- [ ] Create resources/isaac-manifest.edn {:id :isaac.server :builtin? true
      :version "0.1.0" :factory isaac.server.module/create-module} + trivial
      src/isaac/server/module.clj (create-module -> isaac.module/module) +
      add "resources" to bb.edn :paths. Discovered but inert — green.
- [ ] :isaac.server/route decl + the two route entries.
- [ ] :isaac.server/llm-api decl + 5 adapters; fix llm/providers.clj:32 (reads
      the :isaac.core entry); audit declared-module-api-ids /
      known-llm-api-ids (config/loader.clj:526-544,609 — known-llm-api-ids
      looks caller-less; verify and delete, else include builtin ids).
- [ ] :isaac.server/tools decl + 17 tools; generalize register-core-tool!
      (loader.clj:340-355) to a berth-generic
      register-builtin-berth-entry! [berth-id entry-id] over builtin-index
      (caller: tool/builtin.clj:162-164).
- [ ] :isaac.server/provider-template decl + 6 templates.
- [ ] :isaac.server/slash-commands decl + 5 commands; fix
      slash/builtin.clj:163-164 (reads core entry; switch to the
      :isaac.server builtin entry / activate that module).
- [ ] :isaac.server/comm decl (+ :isaac.server/provider, :isaac.server/hook
      decls); move comm-kinds (loader.clj:318-332) to a server ns (only
      caller: config/cli/schema.clj:53); strip the legacy-kind translation
      table out of supporting-module-id (loader.clj:272-290) — callers pass
      the berth keyword directly.
- [ ] The 9 :cli command entries move to the server manifest's top-level :cli
      vector (init stays in the foundation manifest). No code change —
      process-manifest-berths! picks up :cli contributions from any module.

Each sub-step first updates the features/specs that assert core-manifest
contents (features/module/*.feature, spec/isaac/module/*,
manifest_self_consistency_spec) — expectations keyed to :isaac.core move to
:isaac.server; check error-key strings like module-index["isaac.core"]...

## Acceptance

- bb spec and bb features green after each sub-step.
- src/isaac-manifest.edn declares only the :cli berth and the init command.
- resources/isaac-manifest.edn declares the seven :isaac.server/* berths and
  all server contributions.
