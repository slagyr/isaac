---
# isaac-8i8z
title: Collapse isaac-manifest.edn into deps.edn :isaac/manifest
status: todo
type: task
priority: normal
created_at: 2026-06-11T14:44:59Z
updated_at: 2026-06-11T17:54:35Z
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


## ROLLED BACK 2026-06-11

Discovered during a zanebot deploy: the manifest-collapse work broke module discovery for any non-`:local/root` coord. `manifest-resource` enumerates `getResources("deps.edn")` on the JVM context classloader — but `deps.edn` is project metadata, not a classpath resource. tools.deps puts each dep's `:paths` (typically `src/`, `resources/`) on the classpath, NOT the project root where `deps.edn` lives. So fetched git-coord modules never showed up in the module-index; their `:isaac.server/comm` (and other berth) contributions were invisible; user config `:type :discord`/`:imessage` then failed `:registered-in?` validation with "unknown :type".

The in-repo tests passed because they use `:local/root` coords, which hit `resolve-manifest-resource`'s local branch and read `<root>/deps.edn` from disk directly. The git path (which is the production path) was never exercised by the test surface, so the gap went unnoticed.

Reverts pushed:

- isaac core (765cbda1): restored `src/isaac-manifest.edn` everywhere (core, fixtures, modules) and reverted loader changes.
- isaac-acp (9f7aacb): restored `src/isaac-manifest.edn`, deps.edn back to pre-collapse.
- isaac-discord (babf0a8): restored `src/isaac-manifest.edn`, deps.edn back to pre-collapse. (Also reverts the brace-fix commit `d87c6fe` that I'd pushed on top — needed if anyone wanted to retry the collapse later.)
- isaac-imessage (f470125): restored `src/isaac-manifest.edn`, deps.edn back to pre-collapse.

Reset to `todo`. If retried, the new acceptance must include: a production-shape test with a non-`:local/root` coord (e.g. a marigold fixture loaded via `:git/url` against a local tracking branch, or whatever the test harness can simulate), so the git-coord discovery path is genuinely exercised.

The "single source of truth for module deps" win is still desirable. Path forward is either (a) read `deps.edn` directly from each dep's resolved on-disk root (asking tools.deps where the dep landed) instead of via `getResources`, or (b) keep the manifest at `resources/isaac-manifest.edn` and accept the dual-file shape.
