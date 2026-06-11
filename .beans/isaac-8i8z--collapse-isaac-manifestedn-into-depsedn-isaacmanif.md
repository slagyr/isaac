---
# isaac-8i8z
title: Collapse isaac-manifest.edn into deps.edn :isaac/manifest
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-11T14:44:59Z
updated_at: 2026-06-11T15:39:54Z
parent: isaac-brth
---

Today every isaac module ships two manifest files — `deps.edn`
(tools.deps / bb) and `isaac-manifest.edn` (isaac-specific). Both
name the same module deps with the same SHAs in different keyword
conventions. Bumping a version means editing two files; getting
them out of sync is a class of bug we shouldn't have to defend
against.

Collapse the isaac-specific content into `deps.edn` under a
namespaced `:isaac/manifest` key. tools.deps already ignores
unknown top-level keys; nothing in the bb / clj toolchain needs
to know about isaac to keep working.

## Exceptions

- Feature files under `features/cli/` and `features/module/` may be
  edited beyond `@wip` removal to replace
  `resources/isaac-manifest.edn` / `src/isaac-manifest.edn` fixture
  paths and prose with the new single-file `deps.edn` +
  `:isaac/manifest` contract, as long as the behavioral assertions do
  not change.

## Surface change

Before — per module, two files:

\`\`\`clojure
;; deps.edn
{:paths ["src" "resources"]
 :deps  {cheshire/cheshire             {:mvn/version "5.13.0"}
         io.github.slagyr/isaac-server {:git/url "..." :git/sha "abc..."}}}

;; resources/isaac-manifest.edn
{:id      :isaac.comm.discord
 :version "0.3.1"
 :factory isaac.comm.discord/create-module
 :deps    {:isaac.server {:git/url "..." :git/sha "abc..."}}  ; <- duplicates the SHA
 :berths  {…}
 :isaac.server/comm {…}}
\`\`\`

After — one file:

\`\`\`clojure
;; deps.edn
{:paths ["src" "resources"]
 :deps  {cheshire/cheshire             {:mvn/version "5.13.0"}
         io.github.slagyr/isaac-server {:git/url "..." :git/sha "abc..."}}
 :isaac/manifest
 {:id      :isaac.comm.discord
  :version "0.3.1"
  :factory isaac.comm.discord/create-module
  :berths  {…}
  :isaac.server/comm {…}}}
\`\`\`

The `:deps` field that used to live inside `isaac-manifest.edn`
is **dropped**. isaac derives its module-dep set by walking
`deps.edn`'s `:deps` map and treating any entry whose resolved
path contains an `:isaac/manifest` in *its* `deps.edn` as an
isaac module dep.

## Changes

1. **`module/loader.clj`** reads each module's `deps.edn`
   directly (not `resources/isaac-manifest.edn` from the
   classpath) and pulls `:isaac/manifest` from it. If the key is
   absent, treat the dep as a plain Clojure lib (not an isaac
   module).
2. **`module/manifest.clj`** loses any `:deps` field handling on
   the isaac side (it lives in deps.edn now). The manifest schema
   keeps `:id`, `:version`, `:factory`, `:berths`, the contribution
   keys, etc. — everything else.
3. **`isaac-acp`, `isaac-discord`, `isaac-imessage`, `isaac` core,
   any test fixtures** — fold the contents of
   `resources/isaac-manifest.edn` into `deps.edn` under
   `:isaac/manifest`; delete the old file.
4. **Bb fixtures** (e.g. `spec/marigold/bridge/`, `spec/marigold/longwave/`)
   that were created by isaac-jr64 — same migration: drop
   `resources/isaac-manifest.edn`, add `:isaac/manifest` to
   `deps.edn`.

## Acceptance

No new Gherkin. Existing module + berth tests are the safety net.

- `bb features` and `bb spec` green across foundation and module
  repos.
- `rg 'isaac-manifest.edn' src/ spec/ features/ resources/` returns
  zero hits in code (matches inside this bean's body and any
  archived beans are fine).
- `rg ':isaac/manifest' src/ resources/` returns the expected hits
  (one per module's deps.edn).
- No isaac module ships an `isaac-manifest.edn` file anymore;
  cross-repo verified.

## Out of scope

- Generating deps.edn from anything else. The manifest stays
  hand-authored data.
- Renaming the key (`:isaac/manifest` is fine; namespaced, clearly
  isaac-owned, future-proof).
- Tooling that lints `:isaac/manifest` shape inside deps.edn —
  apron-schema already validates the contents post-load.

## Dependencies

- None hard. Could happen at any time after the berth wave's
  manifest-shape work (isaac-htkp completed) landed.
- Probably best to do BEFORE the foundation/server extraction work
  (the off-board phase-9 work) so the newly-extracted repos get
  the clean single-file shape from day one.

## Notes for the worker

- The mem-fs tests probably need an update too — anything that
  writes `resources/isaac-manifest.edn` shifts to writing the
  manifest inside `deps.edn`.
- Watch for tooling that grep-locates `isaac-manifest.edn` outside
  the loader — IDE plugins, doc generators, etc. Probably none in
  this repo, but worth a sweep.
- `:isaac/manifest` is a top-level key on deps.edn; tools.deps
  silently ignores it. Confirm by running `clj -X:deps tree` and
  checking it doesn't error.
