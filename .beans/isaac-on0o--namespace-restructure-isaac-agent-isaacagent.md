---
# isaac-on0o
title: 'Namespace restructure: isaac-agent → isaac.agent.*'
status: completed
type: task
priority: high
created_at: 2026-09-30T14:12:23Z
updated_at: 2026-09-30T15:58:19Z
parent: isaac-vyqs
blocked_by:
    - isaac-davq
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

main-sha: isaac-agent 123d71850b480dc0859886e1a4fa53e082c258f1

**Pin bump.** deps.edn + bb.edn: `io.github.slagyr/isaac-foundation`, `-spec`, `-test-support`, `marigold.bridge`, `marigold.longwave` all repinned `0c6e881` → `06d58b75bc52b3e118dc8e81569096de2532a0d4` (isaac-davq's landed sha). No `bb pins` task exists in this repo (foundation-only); the pin sites are the git/sha literals in deps.edn/bb.edn, found by grepping the old sha.

**Mapping.** 213 files moved under src/, spec/, test-resources/ (2 more relocated by hand, see below): every agent-owned `isaac.<x>.*` namespace (agent, api, attention, bridge, charge, cli.common, comm, config.{agent-steps,check-contributions,defaults,resolve,schema-spec,schema.root,provider-validation-spec}, crew, drive, effort, legacy-api-spec, llm, marigold*, mcp, module.{provider-test,tool-test}, prompt, resource-pool, session.*, slash, spec-helper, step-tables, tool, turn, util) moved to `isaac.agent.<same-rest>`, **except** `isaac.session.frequencies[-cli][-spec]` → `isaac.agent.frequencies[-cli][-spec]` (Micah's explicit call — not `isaac.agent.session.frequencies`). Every reference to isaac-foundation's own namespaces (`isaac.config.*`, `isaac.cli.{api,host,registry,table}`, `isaac.component.*`, `isaac.module.*`, `isaac.fs`, `isaac.logger`, `isaac.main`, `isaac.nexus`, `isaac.marigold`, `isaac.scheduler-steps`, etc. — 234 distinct old→new pairs, derived by diffing this repo's `isaac.<x>` tokens against isaac-foundation's post-davq `isaac.foundation.<x>` tree) got the `foundation.` segment. `isaac.agent.*` (component.clj, module.clj, config/{install,runtime}.clj) already existed pre-bean — only their internal requires needed the foundation-prefix update.

**Left alone (justified, module ids / data, not code namespaces):** `isaac.comm.telly`, `isaac.api.tin-can`, `isaac.providers.kombucha`, `isaac.session.lantern`, `isaac.slash.echo` (this repo's own marigold-style fixture *modules* under `modules/`, matching the eventual real per-module convention already — their manifests' `:factory`/`:namespace` symbols pointing at agent's own or foundation's code WERE updated); berth/config keywords (`:isaac.config/schema`, `:isaac.config/check`, `:isaac.config/validation-ref`, `:isaac/component`, `:isaac/cli`, `:isaac.agent/*`) — data contracts, per the bean; other modules' berth/module-id keywords appearing in agent's own specs as negative-test fixtures (`:isaac.http/*`, `:isaac.server/*`, `:isaac.tool.boom`, `:isaac.tool.doodad`, `:isaac.tool.lens`, `:isaac.tool.mcp`, `:isaac.comm.lazy`, `:isaac.episodes`, `:isaac.fixture.component`, `:isaac.fixture.unknown`, `:isaac.unknown`); the handbook's factual mention of the separate isaac-episodes module (`isaac.session.episodes`, own future bean).

**Two pre-existing bugs found and fixed as part of landing cleanly** (unrelated to this rename, but blocking a clean file-move / lint pass):
- `spec/isaac/turn/tool_spec.clj` was a byte-for-byte duplicate of `spec/isaac/tool/builtin_spec.clj` (same `(ns isaac.tool.builtin-spec ...)`, introduced by isaac-d6pw), colliding with the move target. Deleted the misplaced duplicate. This also means `isaac.agent.turn.tool` (durable-turn CLI inspection, isaac-d6pw) has **no dedicated spec** — pre-existing gap, flagging rather than fixing here (out of scope for a namespace rename).
- `spec/isaac/config/install_spec.clj` and `spec/isaac/config/runtime_spec.clj` already declared `(ns isaac.agent.config.install-spec ...)` / `isaac.agent.config.runtime-spec` (commits "Rename agent {install,runtime} ns to avoid shadowing isaac-server") but were never physically moved — `bb lint` failed with "Namespace name does not match file name" until relocated to `spec/isaac/agent/config/`.

**Non-obvious fixes found by testing/grep, not by the blind substitution pass** (the reason a pure regex pass wasn't sufficient — echoing isaac-davq's note for the next rename bean):
- `src/isaac/tool/grep.clj`'s `(instance? isaac.fs.MemFs fs*)` is a **compiled class-name** reference (foundation's `MemFs` deftype) — the same trap as davq's `RealFs`. A whole-token dictionary match won't catch a namespace+ClassName token; fixed to `isaac.foundation.fs.MemFs` by hand.
- `src/bb/cli_host_lint.clj`'s `hosted-cli` allowlist was a vector of hardcoded **old file paths** (`"src/isaac/session/cli.clj"` etc.) — the same shape as davq's cli-host-lint trap. `:when (bfs/exists? path)` would have silently made the lint a no-op forever. Updated to the five new `src/isaac/agent/...` paths.
- `spec/isaac/comm/factory_spec.clj`'s `"loads the contributing module's :namespace on first dispatch"` spec asserted a keyword (`:isaac.comm.factory-lazy-fixture/lazy`) that `isaac.comm.factory` derives **from the fixture's own real namespace name** at dispatch time — not a berth/module-id keyword, so it had to track the rename to `:isaac.agent.comm.factory-lazy-fixture/lazy`. Caught by `bb spec`, not by the keyword-exclusion heuristic (keywords are usually data contracts here, but this one mirrors code).
- `bb.edn`'s `config-bypass-lint` task doc-string ("Fail on raw config slurp/read outside isaac.config.*") names **foundation's** enforced prefix, not agent's own config tree — a blind substitution turned it into `isaac.agent.config.*`; corrected to `isaac.foundation.config.*` (matches `isaac.foundation.config-bypass-lint`'s actual `allowed-ns-prefixes`).
- `src/isaac/agent/config/runtime.clj`'s docstring names a **sibling repo's own not-yet-renamed namespace** ("lives in isaac-http's `isaac.config.runtime`") — text-identical to foundation's old `isaac.config.runtime`, but semantically isaac-http's own file (out of scope, its own future bean). Reverted that one mention; left the genuine foundation reference two lines above (`isaac.foundation.config.loader`) renamed.
- `features/module/component_extension.feature` (foundation component-berth extension scenarios) builds synthetic third-party module fixtures inline (`isaac.fixture.component`, `isaac.fixture.unknown`, `/tmp/modules/...`) — a blind pass partially renamed the bare-symbol occurrences (`:factory`, `:namespace`, path strings) while correctly leaving the keyword `:id` alone, producing self-inconsistent fixture data. Reverted the whole file; these are arbitrary made-up module ids, not real agent/foundation namespaces.
- `resources/isaac/agent/handbook.md` mentions `isaac.session.episodes` (isaac-episodes' own future namespace, not agent's) as a factual pointer — reverted that one mention; the `isaac.session.frequencies` → `isaac.agent.frequencies` mention (Frequencies section) is correctly renamed.
- `AGENTS.md`'s testing-discipline section references `isaac.spec-helper` in prose — updated to `isaac.agent.spec-helper` (legitimate factual pointer, not a rename miss).

**Live-config greps (read-only, no edits made).** zanebot (`ssh zane@zanebot.tail66e5f8.ts.net`) and yopp (`ssh yopp@yopp`) `~/.isaac/config` both only match module-id keywords already declared in the bean's own mapping table (`:isaac.comm.acp`, `:isaac.comm.discord`, `:isaac.comm.imessage`, `:isaac.comm.gchat`, `:isaac.comm.gmail`, `:isaac.session.episodes`, `:isaac.tool.mcp`) — none are isaac-agent's own internal code namespaces. No config edits required on either host.

**Test results.** `bb ci` (config-bypass-lint + lint-cli-host + `bb spec` + `bb features`): green. `bb spec`: 1835/1835. `bb features`: 818/818, 1 pending (pre-existing `Mid-turn compaction keeps the request in flight...` — same pending scenario as before the rename). `bb jvm-spec` (real JVM via `clojure -M:spec`): 1835/1835. The known-flaky `turn_store.feature:130` scenario passed in this run. All runs isolated via `HOME=/tmp/isaac_scratch_home` per isaac-davq's test-isolation note. `bb lint` shows the same ~535 pre-existing clj-kondo `Unresolved symbol` errors (speclj macros unresolved without a warmed `.clj-kondo` cache) on **both** this branch and pristine pre-change `main` — confirmed identical, not a rename regression.

**GitHub CI on main-sha 123d718:** `CI Tests / verify` — green (`bb ci` passed end-to-end on a fresh checkout against the real published foundation sha). No cross-repo "boots against another repo's live main" job exists in this repo's workflow (unlike foundation's "Server boot..." job) — isaac-agent's CI is fully self-contained via git/sha pins, so no isaac-davq-style expected-red job here.

Full grep of the tracked tree (excluding `modules/`, which keeps its own fixture-module namespaces by design) for any remaining pre-rename namespace token: 0 unjustified hits. `src/`, `spec/` namespace prefixes: 100% `isaac.agent.*` (plus the pre-existing `bb.cli-host-lint` exception, generic dev tooling per the davq precedent).

## Planner verification (2026-09-30)

Verified: every src/spec/spec-support namespace is `isaac.agent.*` (bb/dev tooling aside); no old foundation or agent namespace references remain beyond a docstring naming isaac-http's own not-yet-renamed namespace. CI Tests green on 123d718. Follow-up noted: `isaac.agent.turn.tool` has no dedicated spec after the duplicate spec was deleted. Note: the shared checkout was left renamed `isaac-agent.hidden-for-test`; restoring it needs Micah.
