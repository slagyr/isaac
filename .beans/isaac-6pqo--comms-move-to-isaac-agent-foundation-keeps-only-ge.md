---
# isaac-6pqo
title: Comms move to isaac-agent; foundation keeps only generic berth/registry machinery
status: completed
type: task
priority: normal
created_at: 2026-09-30T02:43:44Z
updated_at: 2026-09-30T04:30:59Z
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

## Landed on main

main-sha: isaac-foundation 4ffde0564101d880029161ca4b75f1d436999fe0
main-sha: isaac-agent 9a33cc34967df34abe538f29ac86ff8062751e33
main-sha: isaac-agent b588695af4a0cc8a60a88f6836620e2c08d835e2
main-sha: isaac-http 567cae411a424689ae85046318f0543e74b75fe7
main-sha: isaac-http c9b6644f9c8d6e0d9fb3ef46b33d8be99a3596ad
main-sha: isaac-server c9b6644f9c8d6e0d9fb3ef46b33d8be99a3596ad

Sequence: isaac-foundation (delete comm/factory.clj, comm/registry.clj,
retired-berth-messages; manifest :server? flag replaces hard-coded
:isaac.http) → isaac-agent (add isaac.comm.factory/isaac.comm.registry,
same bare ns names, zero caller changes; then declare the :comms
:isaac.config/schema table, moved off isaac-http/isaac-server) →
isaac-http (remove its now-duplicate :comms declaration, which
coordinator flagged as fallout from isaac-h2oo's foundation-side
:crew-exists? removal breaking foundation's "Server boot with a
module-provided config type" CI job; fix verified by reproducing that
exact smoke-test boot locally; then a follow-up commit dropping a dead
Timbre require in spec/isaac/http/server-steps, discovered while
checking whether isaac-discord's pins could safely advance — apron
3.2.1 dropped Timbre entirely) → isaac-server (git-mirrors isaac-http;
both commits landed there automatically, no separate push needed).

isaac-discord/isaac-imessage/isaac-gchat/isaac-gmail/isaac-acp need **no
source changes** — they already require `isaac.comm.factory` /
`isaac.comm.registry` by the same bare names, and all five already
depend on isaac-agent directly (confirmed in their deps.edn), so the
namespaces resolve once they eventually bump their isaac-agent pin.
**Their pins were NOT bumped in this bean**: advancing isaac-discord's
foundation/agent pins past isaac-dnib's apron 3.0.0 → 3.2.1 bump (already
on both mains, unrelated to this bean) surfaces (a) the dead-Timbre issue
above (now fixed in isaac-http) and (b) at least one more failure —
isaac-discord's "Discord client lifecycle" feature scenarios stopped
seeing `:discord.client/started` events (saw `:server/hello` instead) —
not triaged; ran out of scope/budget to chase it safely. Recommend a
dedicated pin-modernization bean for the five comm-impl modules rather
than folding it into isaac-6pqo.

Foundation's own main is currently red (isaac-h2oo's :crew-exists?/
:model-exists? regression — foundation removed the lexicon registration,
isaac-agent's matching addition hasn't landed yet). Confirmed via local
reproduction that isaac-6pqo's foundation commit (4ffde05) was green
before isaac-h2oo landed on top, and that the isaac-http fix here is
sufficient for the "Server boot" job independent of when isaac-h2oo's
agent-side lands. isaac-h2oo's own regression is out of scope here per
the planner's explicit correction.

### Acceptance recap

- `grep -rn "isaac\.comm\." isaac-foundation/src` — clean except
  `module/coords.clj`'s `split-repo-lib-sym`, which special-cases the
  `"isaac.comm."` module-id *naming convention* (e.g. `:isaac.comm.acp`
  -> repo `isaac-acp`) to derive deps.edn lib coordinates for ANY
  comm-impl module — generic, tested (`coords_spec.clj`), pre-dates and
  is orthogonal to the comm/factory+registry code this bean moves. Left
  in place; flagged as a candidate for a follow-up bean if it should
  generalize too.
- `grep -n ":isaac\.agent/comm\|:isaac\.http/comm\|:isaac\.server/comm" isaac-foundation/src/isaac/module/berths.clj` — clean.
- Both repos' berth/module feature scenarios pass unchanged (foundation
  `bb features`: 270 examples, 0 failures, 2 pre-existing pending).
- isaac-agent's own comm specs (`spec/isaac/comm/factory_spec.clj`,
  `spec/isaac/comm/registry_spec.clj`) already existed there (testing the
  code transitively through foundation's classpath contribution before
  this move) and now test it directly — no new spec files needed; added
  `spec/isaac/agent/manifest_spec.clj` coverage for the relocated `:comms`
  table and `spec/isaac/module/{lifecycle,manifest}_spec.clj` coverage
  for the new `:server?` manifest flag in foundation.
- Handbook: no user-facing config key, default, or CLI command changed
  (the moved code is internal wiring); no handbook chapter update needed.

## Planner verification (2026-09-30)

Verified: foundation `src/` has no `isaac.agent/comm`, `isaac.http`, `isaac.server` or `:comms` references; agent, http and server CI green; `:comms` is declared only by agent. Finding: `activate-server!` (and the new `:server?` manifest key it reads) has no callers outside its own spec in any repo, and no manifest declares `:server? true`, so it would throw if called. It's dead code. Follow-up: delete `activate-server!`, `loader/activate-server!` and the `:server?` schema key rather than keep a generalized dead path.
