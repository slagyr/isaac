---
# isaac-c0n8
title: 'Foundation pre-work: :isaac.config/schema contribution berth'
status: in-progress
type: task
priority: normal
created_at: 2026-06-12T12:50:15Z
updated_at: 2026-06-12T15:17:09Z
parent: isaac-brth
blocked_by:
    - isaac-niku
    - isaac-wop6
---

Phase A step 9 of the isaac-foundation extraction (see isaac-brth reshaping
note). The loader's biggest server coupling is its pervasive require of
isaac.config.schema (crew/models/providers/hail/hooks tables — all server
domain). Replace with a foundation-declared contribution berth gathered
directly from the module-index during load (same direct-manifest-read pattern
as manifest-capability-ids / find-manifest-entry /
config.berths/effective-root-schema — NOT process-manifest-berths!, whose
timing is post-load). Contribution values carry symbols resolved with
requiring-resolve (schema fragments contain fns; can't live in EDN).

- [ ] Extract foundation utilities: new src/isaac/config/schema_base.clj with
      ->id, schema-fields, strip-validation-annotations, and the minimal base
      root schema ({:modules ...} from schema.clj:386-390).
      isaac.config.schema re-exposes them so its server-side requirers
      (mutate, cli/*, schema/manifest, schema/term) don't change.
- [ ] Declare :isaac.config/schema in the foundation manifest (no :factory —
      process-manifest-berth! skips it by design). Contribution shape keyed by
      top-level config key: {:fragment <symbol> :entity-dir <str>
      :frontmatter? <bool> :inline-companions? <bool>
      :companion {:field <kw> :mode :exclusive|:required|:optional}}.
- [ ] Server manifest contributes fragments + descriptors for every current
      top-level key (crew, cron, hooks, hail, models, providers, defaults,
      comms, server, gateway, acp, sessions, slash-commands, tools, tz,
      prefer-entity-files, prompt-paths, ...). isaac.config.schema keeps
      assembling its own root from the same vars — no drift.
- [ ] Loader composes: base root + requiring-resolve'd fragments
      (collision-checked, reuse isaac.schema.dynamic merge semantics) +
      config.berths/effective-root-schema on top. Temporary parity spec:
      composed == static schema/root.
- [ ] Derive entity-kind knowledge from descriptors instead of literals:
      config-files-present?, entity-files frontmatter set #{"crew" "cron"
      "hooks"}, dangling-md-warnings, schema-for, merge-root-entity kind
      list, companion-resolution branches in resolve-entity-data
      (loader.clj:427-461), resolve-cron-prompts inline-root handling,
      normalize-config table conform. Legacy-shape migration in
      normalize-config stays foundation (keyword-only data logic).
- [ ] Remove [isaac.config.schema :as schema] from config/loader.clj; delete
      the fallback and the parity spec.

Watch: normalize-config is called on snapshot configs outside load (resolution
fns, config.api/normalize-config) — schema lookups need a process-cached
last-composed root schema (set during load-config-result), pass-through when
nothing composed. Verify spec'd callers that pass bare maps without
:module-index.

## Acceptance

- bb spec and bb features green after each sub-step.
- rg 'isaac.config.schema' src/isaac/config/loader.clj returns zero hits
  (schema-base is the only schema require left).
