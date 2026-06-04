---
# isaac-ho18
title: Migrate :provider, :llm/api, :slash-commands, :hook to declared berths (phase 7)
status: todo
type: task
priority: normal
created_at: 2026-06-04T14:52:21Z
updated_at: 2026-06-04T14:52:43Z
parent: isaac-brth
blocked_by:
    - isaac-8yxs
    - isaac-jr64
    - isaac-ma0j
    - isaac-2ecl
---

Phase 7 of isaac-brth. The four mostly-mechanical migrations. Each
follows the route/tools pattern: today's hardcoded top-level manifest
key becomes a foundation-declared berth; the existing registration fn
stays as the berth's `:factory`; legacy validators (`:provider-exists?`,
etc.) swap for `[:registered-in?]`.

The four kinds are independent of each other; the worker may land them
as a single PR or split into four. Either way, the conversion shape is
the same.

## Berths to declare in isaac core's manifest

- `:isaac.server/provider` — provider templates
  (e.g. `:helm-systems`, `:starcore`). Manifest berth (modules declare)
  + config berth (users instantiate per-provider with their own API
  keys).
- `:isaac.server/llm-api` — LLM API factories. Manifest-only — users
  don't configure APIs directly; they're picked up via providers.
- `:isaac.server/slash-commands` — manifest-shipped slash commands.
  Manifest-only.
- `:isaac.server/hook` — event handlers. Manifest-only.

## Per-kind change list

For each kind:

1. Add berth declaration to isaac core's manifest (`:berths {...}`).
2. Move existing built-in entries (in `src/isaac-manifest.edn`) from
   the top-level kind key to a contribution to the new namespaced
   berth.
3. Remove the top-level key from `module/manifest.clj`'s
   `manifest-schema`, `known-extend-kinds`, `known-keys`.
4. Remove the kind from `module-loader/register-handler!` calls in
   the relevant registration namespace (slash/registry, hooks, etc.).
5. Swap legacy existence validators for `[:registered-in?]`:
   - `[:provider-exists?]` → `[:registered-in? :isaac.server/provider]`
   - `[:llm-api-exists?]` → `[:registered-in? :isaac.server/llm-api]`
   - (`:slash-commands` and `:hook` don't have widely-used existence
     refs today — confirm during impl.)
6. Cross-repo: update isaac-discord / isaac-imessage / isaac-acp
   manifests if they contribute to any of these kinds. (Most likely
   slash-commands and/or hooks.)

## Acceptance

No new Gherkin. Existing per-kind feature tests + greps.

- `bb features features/provider/` (or wherever provider scenarios
  live) passes.
- `bb features` for llm/api, slash-commands, hooks each pass.
- `bb spec` green; no regressions.
- Greps:
  - `rg ':provider\s*{' src/` — zero (legacy form gone).
  - Same for `:llm/api`, `:slash-commands`, `:hook` at the
    top-level-manifest position.
  - `rg ':provider-exists\?|:llm-api-exists\?' src/` — zero.
  - `rg 'register-handler!.*:provider|register-handler!.*:llm/api|register-handler!.*:slash-commands|register-handler!.*:hook' src/` — zero.

## Out of scope

- `:comm` migration (phase 8 — its own bean).
- New provider/api/slash/hook features. This is conversion only.
- Cross-tenant or multi-LLM-routing concerns introduced by the
  config-berth treatment of `:provider`.

## Dependencies

- isaac-8yxs (manifest-only berth processing) — covers the
  registration mechanism for all four.
- isaac-jr64 (config berth processing) — needed for the
  `:isaac.server/provider` config-side. The other three are
  manifest-only and only need 8yxs.
- isaac-ma0j ([:registered-in?] validator).
- isaac-2ecl (:dynamic-schema) — for `:isaac.server/provider`'s
  user-instantiated config slots, where per-provider extra schema
  comes from the manifest contribution (analogous to comm).

## Notes for the worker

- `:isaac.server/provider` is the closest analog to `:isaac.server/comm`
  — both are config berths with manifest contributions registering
  available impls. Use the comm pattern (multimethod dispatch on
  `:type`, `:dynamic-schema` merging) as the template.
- `:isaac.server/llm-api` is plumbing under providers. Users
  reference an API by id in their provider config; foundation
  resolves via `[:registered-in?]`. No user-facing config slots.
- `:isaac.server/slash-commands` keeps its existing dispatch path
  (slash registry); the berth declaration just replaces how
  registration happens.
- `:isaac.server/hook` follows the same shape as slash-commands.
- If splitting into separate PRs, do `:slash-commands` and `:hook`
  first (smallest, no config side), then `:llm/api`, then
  `:provider` (config-berth — most complex of the four).
