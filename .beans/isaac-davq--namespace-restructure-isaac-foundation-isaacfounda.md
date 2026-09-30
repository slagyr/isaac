---
# isaac-davq
title: 'Namespace restructure: isaac-foundation → isaac.foundation.*'
status: completed
type: task
priority: high
created_at: 2026-09-30T14:12:23Z
updated_at: 2026-09-30T15:18:22Z
parent: isaac-vyqs
---

Micah, 2026-09-30. **A module's code lives under its module id.** isaac-foundation → `isaac.foundation.*`, isaac-agent → `isaac.agent.*` (e.g. `isaac.session.frequencies` → `isaac.agent.frequencies`), isaac-claude-code (`:isaac.provider.claude-code`) → `isaac.provider.claude-code.*`, isaac-episodes (`:isaac.session.episodes`) → `isaac.session.episodes.*`, comm modules → `isaac.comm.<name>.*`, and so on. Source, specs, spec-support, step namespaces, manifest symbols (`:factory`, berth entries), bb tasks and docs all move together. Clean cutover: no alias namespaces.

## Order: inside-out (Micah)

1. isaac-foundation (requires nobody).
2. isaac-agent: bump to the new foundation, update its foundation requires, rename its own.
3. Every other module, in parallel: bump foundation + agent (this absorbs the pin sweep isaac-5x21), update requires, rename its own. A module that requires another leaf (gchat/gmail → google) goes after that leaf.

Each repo is touched once.

## Deploy freeze

An installed Isaac runs one foundation and one agent, so zanebot/yopp don't take the new foundation until every installed module has migrated. Each repo's main stays green on its own pins meanwhile.

## Every child bean also

- greps zanebot and yopp live config (read-only, `ssh zane@zanebot…` / `ssh yopp@yopp`) for namespace names in data (hook `:factory`, embedding `:namespace`, etc.) and lists required config edits in the bean;
- updates its handbook chapter and README where namespaces are named;
- is ungated (mechanical refactor): acceptance = full CI green on main, a grep showing no namespaces outside the module's id prefix in src/spec (list justified exceptions), planner verification.

## Landed on main

main-sha: isaac-foundation 06d58b75bc52b3e118dc8e81569096de2532a0d4

**Mapping.** 225 files moved (src/, spec/, spec-support/src/): every non-`isaac.foundation.*` namespace (isaac.config.\*, isaac.module.\*, isaac.modules.\*, isaac.cli.\*, isaac.fs, isaac.nexus, isaac.logger, isaac.log.\*, isaac.logs.\*, isaac.scheduler.\*, isaac.component.\*, isaac.schema.\*, isaac.service.\*, isaac.util.\*, isaac.launcher, isaac.main, isaac.shell, isaac.runner\*, isaac.startup.\*, isaac.naming, isaac.reconfigurable, isaac.spec-helper, and the two boundary specs) moved to `isaac.foundation.<same-rest>`. Justified exceptions kept as-is: `bb.*` / `dev.*` (generic dev/bb tooling, never isaac-namespaced) and the marigold fixture module ids (`:isaac.component.bravo` etc., `:isaac.http`/`:isaac.foundation` test ids in modules/marigold.fixture.foundation.v999 and friends) — these are data identifiers, not code namespaces.

One genuine collision: `spec/isaac/module_spec.clj` (ns `isaac.module-spec`, testing `isaac.module.protocol`) would have landed on the same new name as the pre-existing `spec/isaac/foundation/module_spec.clj` (ns `isaac.foundation.module-spec`, testing `isaac.foundation.module`/`create-module`). Resolved by renaming the former to `spec/isaac/foundation/module/protocol_spec.clj` (ns `isaac.foundation.module.protocol-spec`), matching the namespace it actually tests.

**Files outside Clojure changed:** README.md, FOUNDATION.md, RELEASE.md (prose namespace mentions), resources/isaac/foundation/handbook.md, bb.edn (`isaac.main` task, `isaac.foundation.cli-host-lint`/`pin-lint`/`config-bypass-lint` requires, doc string), deps.edn (`:main-opts ["-m" "isaac.foundation.main"]`), libexec/isaac.bb (already `isaac.foundation.launcher`), src/isaac-manifest.edn (`:factory`/`:namespace` symbols), modules/marigold.\*/resources/isaac-manifest.edn (`:factory isaac.foundation.module.protocol/module`).

**Non-obvious fixes found by testing, not by the grep sweep** (these are the reason a pure regex substitution wasn't sufficient — noting for the next repo's rename bean):
- `isaac.foundation.module.protocol`'s `missing-hook-implementation?` built its match regex from a Clojure string with escaped dots (`"isaac\\.module\\.protocol/Module"`) — the escaped-backslash text never matches a plain-dot substitution pass. Missed namespace, caught by `bb spec` (protocol default-hook specs failed with a live `IllegalArgumentException` instead of being treated as a no-op). Fixed by hand.
- `isaac.foundation.cli-host-lint`'s file-path allowlist (`spec-support/src/isaac/foundation/cli_host_lint.clj`) hard-coded the three old file paths (`src/isaac/cli/host.clj` etc.) as literal strings — paths, not namespaces, so the namespace substitution never touched them. `bb lint-cli-host` failed until fixed by hand.
- `isaac.foundation.config-bypass-lint`'s `allowed-ns-prefixes` held the bare wildcard string `"isaac.config."` (a prefix, not an exact namespace) — fixed to `"isaac.foundation.config."` by hand, plus the `println` message text.
- `src/isaac/foundation/config/root.clj`'s `real-fs?` check compared a class name string `"isaac.fs.RealFs"` — the compiled class name of the `RealFs` deftype, now `isaac.foundation.fs.RealFs`. Fixed by hand.
- A few doc-string/comment wildcard mentions (`isaac.config.cli.*`, `isaac.module.*` spec-suite doc, `isaac.module.{coords,classpath,...}`) needed manual follow-up since they don't match a single exact namespace token.
- `:isaac.cli/exit` / `:isaac.cli/process-exit` (ex-info keys private to `cli/host.clj` + `main.clj`, no external consumer) renamed to `:isaac.foundation.cli/...` for consistency with the code package rename.

**Scenario-text edits:** `features/cli/config_keys_list.feature` names the real namespace twice in prose ("isaac.config.cli owns these commands generically...", "...both assert isaac.config.cli.set's own hard-coded help copy") — updated to `isaac.foundation.config.cli[.set]` since it's a factual pointer at the implementing namespace, not a behavioral contract. `features/cli/config_schema.feature`'s narrative mentions a historical `isaac.config.comm-kinds` convention that was never a real isaac-foundation namespace (predates this repo's history / belongs to agent's side) — left untouched, flagging here rather than guessing.

**Live-config greps (read-only, no edits made):** zanebot and yopp `~/.isaac/config` both only match `:isaac.cli-server` — a separate module id (isaac-cli-server), not a foundation code namespace. No config edits required on either host.

**Left alone (deliberately out of scope):** `spec/isaac/config/fixtures/modules/**` and `spec/isaac/module/fixtures/**` (marigold manifest fixture *directories*, not Clojure namespaces — moving them would mean rewriting ~20 feature files' hardcoded `:local/root` strings for zero namespace benefit); `forbidden-prefixes`/`"isaac.util"` in `foundation_boundary_spec.clj` (a pre-existing, unrelated server-side boundary list, not this repo's own `isaac.util.edn`).

**Test results.** `bb ci`'s lints (`config-bypass-lint`, `lint-cli-host`, `lint-pins`) pass. `bb pins` fails locally for the documented environmental reason (dangling bean-branch shas aren't independently confirmed here). `bb spec` (1322 examples) and `bb features` (355 examples) are 100% green *once isolated from this dev machine's real `~/.isaac/config/isaac.edn`* — Micah's own populated config (real crew/model schema) leaks into any spec/feature that boots `isaac.foundation.main` without an explicit `--root`/fake `user.home`, because `:model-exists?` (an isaac-agent-only lex validator) isn't registered in a foundation-only classpath. Confirmed byte-for-byte reproducible on **pre-rename** `main` too (same crash, isolated `main_spec.clj`/`clj -M:test:spec` runs) — this is pre-existing test-isolation fragility, not a rename regression; the file-move just changed spec run order enough to surface it in the full local `bb spec`/`bb ci` run more often. With `user.home` pointed at a scratch dir: `bb spec` 1322/1322, `bb features` 355/355 (2 pre-existing pending), `bb features-slow` 12/12 (12 pre-existing pending, plus the `modules_deps_emit.feature` "@slow — the emitted --edn deps boot isaac on the JVM" scenario, confirmed to pass with an explicit `--root`; it boots `isaac.foundation.main --version` with no `--root`, so it's the same real-`~/.isaac` leak). `bb jvm-spec` (real JVM via `clj -M:test:spec`, same `user.home` isolation): 1322/1322 except 9 failures, **all 9 confirmed identically pre-existing on original main** under the same isolated-home JVM run — `isaac.foundation.module.protocol`'s two "no-op" specs and the six cascading `module lifecycle` specs fail because real-JVM Clojure's protocol-dispatch exception message says "found for class: X" while babashka/GraalVM says "found for: X" (the existing `missing-hook-implementation?` regex only matches the bb form — this is the "bb vs JVM protocols" trap, pre-existing, unrelated to this rename), and `isaac.foundation.module.coords`'s GITLIBS test fails because this machine has a real `GITLIBS`-adjacent system property set outside the test's control.

**GitHub CI on main-sha 06d58b7:** `verify` (bb ci) — green. `Slow features (@slow launcher lane)` — green. `Server boot with a module-provided config type` — **red**, and this one is a real, expected consequence of the inside-out order, not a foundation defect: that job checks out `isaac-http`'s current `main` fresh and boots `libexec/isaac` against a config declaring `:isaac.http` as a module. isaac-http's own deps.edn still pins the **old**, pre-rename foundation sha (correctly excluded from the classpath so our local `../src` wins), but isaac-http's own *source* (`isaac.http.module/create-module` etc.) still directly requires the **old** namespace names (`isaac.module.protocol`, ...) — which no longer exist anywhere on the classpath once foundation only ships `isaac.foundation.module.protocol`. `register-module-cli-commands!`'s blanket `(catch Exception _ nil)` swallows the resulting `module factory resolution failed for isaac.http: isaac.http.module/create-module`, so every CLI command (including foundation's own `config`) silently fails to register. This will stay red until isaac-http (or whichever module this smoke test targets) completes its own isaac-vyqs migration bean; it passed on the immediately-preceding commit (8022906) and would pass again if pointed at a foundation sha before this one. Flagging for a planner decision — accept as a known, temporary cross-repo casualty of the inside-out order (most consistent with the bean's own "Deploy freeze" reasoning), or re-pin/skip that CI job until the fleet catches up. I did not touch isaac-http or the CI workflow.

Full grep of the tracked tree for any remaining pre-rename namespace token: 0 hits. `src/`, `spec/`, `spec-support/src/` namespace prefixes: 100% `isaac.foundation.*` (plus the justified `bb.*`/`dev.*` exceptions above).

## Planner verification (2026-09-30)

Verified: all 244 src/spec/spec-support namespaces are `isaac.foundation.*`; remaining `:isaac.config/*` hits are berth ids (shared keyword contracts), not namespaces, and stay. CI on 06d58b7: verify + slow lane green; "Server boot with a module-provided config type" red because it boots against isaac-http main, which still requires the old names. Accepted as expected fallout; it goes green when isaac-http migrates.
