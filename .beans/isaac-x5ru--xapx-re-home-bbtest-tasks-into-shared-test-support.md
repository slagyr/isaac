---
# isaac-x5ru
title: 'xapx: Re-home bb.test-tasks into shared test-support (native runner)'
status: completed
type: task
priority: high
created_at: 2026-07-19T17:10:28Z
updated_at: 2026-07-19T17:38:37Z
parent: isaac-xapx
---

Parent: isaac-xapx. **This blocks every other xapx child** — nothing else can `:require` the native runner until it lands.

## Goal
Re-home foundation's `bb.test-tasks` runner into a shared, dependable location so every module repo can `:require ([bb.test-tasks :as tests])` and call `run-spec!` / `run-features!` / `run-ci!` / `run-jvm-spec!` / `run-jvm-features!`. ONE implementation, N consumers — never copy-pasted per repo.

## Why
`bb.test-tasks` lives at `isaac-foundation/bb/test_tasks.clj`, on foundation's own bb classpath only (foundation `:paths` includes `"."`). Modules depend on isaac-foundation via git but do NOT get `bb/test_tasks.clj`, so they cannot require it today.

## Do
- Pick the home: expose it via foundation's test-support (`spec-support` `deps/root`, already pulled by every module as `isaac-foundation-test-support`), or a new dedicated shared `deps/root`. Decide and document.
- Ensure the file resolves as namespace `bb.test-tasks` from a CONSUMER repo's bb classpath (paths/deps wiring).
- Keep `test_timeout.clj` / any helper it needs alongside.

## Acceptance
- [x] A consumer module's `bb.edn` can `:requires ([bb.test-tasks :as tests])` and run `bb spec` natively — proven in ONE lightweight pilot consumer (**isaac-hail**, chosen for a small suite and no wrinkles) before the sweep. The full isaac-agent parity conversion is the separate blocked child isaac-h5xm.
- [x] No copy of `test_tasks.clj` added to any consumer repo.
- [x] Home + wiring documented in this bean for the other children to follow.

## Decision + wiring (for sibling xapx children)

**Home:** `isaac-foundation-test-support` (`deps/root "spec-support"`).

Canonical sources (edit these):
- `isaac-foundation/spec-support/src/bb/test_tasks.clj` — ns `bb.test-tasks`
- `isaac-foundation/spec-support/src/bb/test_timeout.clj` — ns `bb.test-timeout`

Foundation keeps thin re-exports at `bb/test_tasks.clj` + `bb/test_timeout.clj` so its own `:paths ["."]` still resolves without depending on its own test-support coord. Prefer editing the `spec-support` copies and mirroring into `bb/` when foundation-local resolution is still needed.

**Consumer bb.edn pattern** (proven on isaac-hail @ `440b765`):

```clojure
{:paths ["src" "resources" "spec" #_optional-feature-steps]
 :deps  {io.github.slagyr/isaac-foundation-test-support
         {:git/url "https://github.com/slagyr/isaac-foundation.git"
          :git/sha "95cff519223491a61e5bfc46ec5e88b44d4debdc" ; isaac-x5ru
          :deps/root "spec-support"}
         speclj/speclj {:mvn/version "3.13.0"}
         io.github.slagyr/gherclj {:git/tag "v1.3.0" :git/sha "9c3bb1d"}
         babashka/process {:mvn/version "0.5.22"}
         ;; + module runtime deps}
 :tasks {spec     {:requires ([bb.test-tasks :as tests])
                   :task (apply tests/run-spec! *command-line-args*)}
         features {:requires ([bb.test-tasks :as tests])
                   :task (apply tests/run-features! *command-line-args*)}
         ci       {:requires ([bb.test-tasks :as tests])
                   :task (do (run 'config-bypass-lint)
                             (tests/run-ci!))}
         jvm-spec {:requires ([bb.test-tasks :as tests])
                   :task (apply tests/run-jvm-spec! *command-line-args*)}
         jvm-features {:requires ([bb.test-tasks :as tests])
                       :task (apply tests/run-jvm-features! *command-line-args*)}}}
```

**Defaults:** `*spec-dir*` `"spec"`, `*features-dir*` `"features"`, `*step-globs*` `["isaac.**-steps"]`, `*jvm-spec-cmd*` `["clj" "-M:test:spec"]`, `*jvm-features-cmd*` `["clj" "-M:test:features"]`.

**Overrides** (bind around the call):
- Explicit step namespaces (hail pilot): `(binding [tests/*step-globs* ["isaac.foundation.fs-steps" … "isaac.hail-steps"]] (apply tests/run-features! …))`
- JVM alias path: `(binding [tests/*jvm-spec-cmd* ["clojure" "-M:spec"]] …)`

**Do NOT** copy `test_tasks.clj` into consumer repos. Bump the test-support `:git/sha` when foundation changes the runners.

## Pilot proof (isaac-hail)

- foundation branch `bean/isaac-x5ru` @ `95cff51` — exposes runners under test-support
- hail branch `bean/isaac-x5ru` @ `440b765` — bb.edn requires + native `bb spec`
- `bb -e '(require '[bb.test-tasks :as t]) …'` → require-ok
- `bb spec --focus spec/isaac/hail/store_spec.clj` → 5 examples, 0 failures (native bb, no clojure shell-out)
- No `test_tasks.clj` in hail repo

## Notes for blocked children

- Full hail feature parity / full-suite native conversion is out of this bean's scope (acceptance is require + run-spec pilot). isaac-h5xm (agent) and the sweep children should pin foundation SHA ≥ `95cff51` (or the main SHA after this merges) and follow the wiring above.
- Hail pilot pins foundation-test-support to the bean branch SHA; after foundation merges to main, bump consumers to the main SHA.

## MERGED to main (2026-07-19)

- **foundation main = `43cf46e00087bf066a9e065ccc3d48dd2814ac23`** — pin `isaac-foundation-test-support` `:git/sha` to THIS in every consumer bb.edn/deps.edn.
- **hail main = `a520a4f`** — the isaac-hail pilot conversion is DONE and merged (native `bb spec`, pinned to foundation 43cf46e). Do NOT re-convert hail.
- bean branches `bean/isaac-x5ru` deleted on both repos.
