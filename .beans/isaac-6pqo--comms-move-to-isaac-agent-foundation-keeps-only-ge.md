---
# isaac-6pqo
title: Comms move to isaac-agent; foundation keeps only generic berth/registry machinery
status: todo
type: task
priority: normal
created_at: 2026-09-30T02:43:44Z
updated_at: 2026-09-30T02:44:57Z
---

## Ruling

Micah, 2026-09-29: isaac-foundation's code must never name another module's
config, berths, ids or concepts. Everything foundation needs comes from the
loaded schema and module manifests. Clean cutover, no back-compat.

## Problem

`isaac-foundation` hard-codes the `:isaac.agent/comm` berth and the comm
concept by name:

- `src/isaac/comm/factory.clj` — the whole namespace is
  `isaac.comm.factory`, a per-slot factory hard-wired to the
  `:isaac.agent/comm` berth key (`manifest-comm-contribution`,
  `impl-id`/`create!` docstrings, `:comm/activated` log event).
- `src/isaac/comm/registry.clj` — `*registry*` defaults `:path [:comms]`;
  `fresh-registry` defaults to `[:comms]`. `:comms` is agent's config root,
  not a foundation concept.
- `src/isaac/module/berths.clj:220-223` — `retired-berth-messages` hard-codes
  three retired keys and their replacements by name:
  ```clojure
  (def ^:private retired-berth-messages
    {:isaac.http/service ":isaac.http/service is retired; use :isaac/component"
     :isaac.http/comm   ":isaac.http/comm is retired; use :isaac.agent/comm"
     :isaac.server/comm ":isaac.server/comm is retired; use :isaac.agent/comm"})
  ```
  This is foundation code naming isaac-http, isaac-server, and isaac-agent
  berths by id, permanently (not a temporary migration aid — nothing ages it
  out).

The generic manifest-only berth machinery (`process-manifest-berths!`,
`contributions-to-berth`, `contribution-validation-errors`,
`validate-contributions!`, etc.) in `module/berths.clj` is already
generic — it takes berth-id and berth-decl as data and never names a
specific berth. Only `retired-berth-messages` breaks that.

## Wanted

1. Move `src/isaac/comm/factory.clj` and `src/isaac/comm/registry.clj` to
   `isaac-agent` (package as `isaac.agent.comm.factory` /
   `isaac.agent.comm.registry`, or keep bare names under an agent-owned ns
   root — worker's call, follow isaac-agent's existing ns conventions).
   Update `:isaac.agent/comm` berth's `:schema :value-spec :factory` (in
   isaac-agent's manifest, wherever it's declared — check
   `isaac-agent/resources/isaac-manifest.edn`) to point at the new
   namespace/symbol.
2. `isaac.comm.registry`'s default `:path [:comms]` moves with it — it's
   fine for the agent-owned copy to know `:comms` is its own root path.
3. Delete `retired-berth-messages` entirely from
   `isaac/module/berths.clj`. `unknown-berth-error` falls back to the
   existing generic message ("berth not declared by any installed
   module") for ANY unrecognized berth key, retired or never-existed —
   no special-cased hint. This matches the "no back-compat" ruling: a
   retired key gets the same treatment as a typo.
4. Grep isaac-agent (and any other module) for anything that currently
   imports `isaac.comm.factory` / `isaac.comm.registry` by that exact
   namespace and update the require.
5. Check for any OTHER caller of `isaac.comm.registry` across sibling
   repos (isaac-discord, isaac-imessage, isaac-gchat, isaac-hail, etc. —
   comm-impl modules likely call `register-factory!`/`registered?`/
   `comm-for` to plug into the legacy comm-registry path
   (`comm-registry/factory-for` in the old `create!` fallback)). Every
   caller's require moves from `isaac.comm.registry` to the new
   `isaac.agent.comm.registry` (or wherever it lands).

## Acceptance

- `grep -rn "isaac\.comm\." isaac-foundation/src` returns nothing.
- `grep -rn ":isaac\.agent/comm\|:isaac\.http/comm\|:isaac\.server/comm" isaac-foundation/src/isaac/module/berths.clj` returns nothing.
- Foundation's own berth/module feature scenarios (module/*.feature) still
  pass unchanged — they don't name comm, so this shouldn't touch them;
  if any DOES incidentally reference `:isaac.agent/comm` as its example
  berth, swap it for a Marigold fixture berth id instead (see isaac-3y69's
  `config_schema.feature` pattern).
- isaac-agent's own comm-related specs/features (wherever `comm/factory`
  and `comm/registry`'s current spec coverage lives — grep
  `isaac-foundation/spec` for `comm.factory`/`comm.registry` specs and move
  those too) pass green after the move.
- Any comm-impl module (isaac-discord, isaac-imessage, isaac-gchat, ...)
  that requires the moved namespaces has its require updated and its own
  CI green (bump its isaac-agent pin if the comm registry API becomes
  isaac-agent's, not foundation's — this is a NEW cross-repo dependency
  those modules didn't have before if they previously only depended on
  foundation for comm-registry; check whether they already depend on
  isaac-agent for other reasons, which is likely since comm impls plug into
  `:isaac.agent/comm`).

## Likely repo scope

- `isaac-foundation`: delete `src/isaac/comm/`, delete
  `retired-berth-messages` in `src/isaac/module/berths.clj`, move/delete
  associated specs.
- `isaac-agent`: add `src/isaac/agent/comm/factory.clj` (or similar),
  `src/isaac/agent/comm/registry.clj`; update manifest's comm-berth
  `:factory` reference; add specs.
- `isaac-discord`, `isaac-imessage`, `isaac-gchat`, and any other comm-impl
  module: update `isaac.comm.registry` requires. **Check each one's
  current isaac-agent pin** — if they don't already depend on isaac-agent
  directly (some may only depend on isaac-foundation today), this bean
  adds a new direct dependency; note the pin bump in each repo's deps.edn.

## Notes

- This is the piece of the cleanup most likely to touch other repos
  transitively (any comm-impl module). Grep across ALL sibling checkouts
  for `isaac.comm.registry` / `isaac.comm.factory` before starting, not
  just the ones checked out under `plan/` right now — some comm modules
  may not be cloned locally.
- Not blocked by isaac-dnib (different files entirely: comm/, module/berths.clj
  vs. normalize.clj/loader.clj/schema_base.clj/schema/term.clj/validation.clj).
  Can land independently and in parallel with dnib.

## Open questions

- Does deleting `retired-berth-messages` outright (vs. keeping a *generic*
  mechanism for a module to register its own retired-key hints) match what
  Micah wants? The "no back-compat" ruling suggests yes — a retired key is
  just an unknown key now — but if Micah wants friendlier hints to survive,
  that's a bigger design (a berth-registered hint table, contributed by
  each owning module) and should be its own bean, not folded in here.

## Decision (planner recommendation; Micah approved the cleanup 2026-09-30)

Also fold in: `isaac.module.lifecycle` names `:isaac.http` as the server module. Replace it with a manifest flag (e.g. `:server? true` on the module that runs the server process) so foundation names no module id.

## Ungated

Refactor with no new user-visible behavior, so no new scenarios: acceptance is both repos' full CI green (existing scenarios are the regression net), the grep checks named in this bean, and planner verification. Worker hands off with `tag=unverified`.
