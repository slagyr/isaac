---
# isaac-j4m5
title: 'Namespace restructure: isaac-gmail under its module id'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-30T14:12:35Z
updated_at: 2026-09-30T17:53:00Z
parent: isaac-vyqs
blocked_by:
    - isaac-on0o
    - isaac-tacl
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

## Findings (landed, main 09f5077)

isaac-gmail's own namespaces were already `isaac.comm.gmail.*` (src/spec) —
nothing to rename there. The work was: bump pins and fix every cross-module
require.

**Pins** (deps.edn + bb.edn): isaac-foundation → `06d58b75bc52b3e118dc8e81569096de2532a0d4`
(exact, per foundation's own pin in isaac-agent), isaac-agent →
`123d71850b480dc0859886e1a4fa53e082c258f1`, isaac-http →
`56998543b3e5c40593d2a3ea97b16550e3731463` (already migrated main), isaac-google
→ `d16c9ac7ceda55c7c0dae583f047a590e9f154b2` (already migrated main; only one
coordinate, no `-spec`/`-test-support` variant existed).

**Cross-module requires** — every `isaac.<x>` require of foundation/agent code
updated to its new home (looked up via each repo's own migration-commit
renames, not guessed): `isaac.api`→`isaac.agent.api`,
`isaac.comm.{factory,protocol,registry}`→`isaac.agent.comm.*`,
`isaac.config.defaults`→`isaac.agent.config.defaults`,
`isaac.llm.{api.grover,auth.store}`→`isaac.agent.llm.*`,
`isaac.session.{session-steps,store.memory,store.spi,transcript}`→`isaac.agent.session.*`,
`isaac.{fs,logger,nexus,module.discovery,module.protocol,scheduler.runtime,
config.api,config.loader,config.root,config.schema-compose,config.schema.resolve,
step-tables}`→`isaac.foundation.*`. Berth/config keywords
(`:isaac.google/registration`, `:isaac.google/handler`, `:isaac.config/schema`,
`comms.gmail.*` config keys) are data contracts and were left alone.

**Two non-obvious fixes the pin bump exposed (not pre-existing — confirmed
green on pre-change main with old pins):**

1. **`resources/isaac-manifest.edn`'s `gmail-routes` `:key-spec` was
   `{:type :string}`.** isaac-foundation's new conformed-over-raw config
   overlay (bean isaac-dnib) canonicalizes a dynamic map's `:id`-typed keys
   between raw and conformed (keyword `:ops` vs conformed's canonical form),
   but a `:string`-typed key-spec conforms a keyword key via `str` (`":ops"`,
   colon included) instead of `name` (`"ops"`) — so canonicalization never
   unifies them, and BOTH a correct `:ops` entry and a bogus `":ops"`-string
   entry land in the resolved config. Since string-sort puts `:` before
   ASCII letters, the bogus entry matched first and every routed message got
   mislabeled `isaac/:<route>` instead of `isaac/<route>` — every route/label
   feature scenario failed. Fixed by changing `:key-spec` to `{:type :id}`,
   the convention every other manifest in foundation/agent/http already uses
   for this exact pattern. Verified empirically (0 failures with the fix,
   consistent failures without it, independent of anything else in this
   bean).
2. **Feature-step namespace collision.** Renamed gmail's own step ns to
   `isaac.comm.gmail.gmail-steps` (not `.steps`) — keeping the `-steps` tail
   so it still matches the existing `isaac.**-steps` gherclj glob. Naming it
   `isaac.comm.gmail.steps` compiled but silently dropped 5 scenarios to
   "pending: not yet implemented", because gherclj's generated-spec `:require`
   aliases a namespace by its *last dot-segment only* (`ns->alias`) — both
   `isaac.comm.gmail.steps` and isaac-google's already-landed
   `isaac.google.steps` would alias to `steps` in any spec file needing both,
   throwing `IllegalStateException: Alias steps already exists`. Also added
   `"-s" "isaac.google.steps"` to the `:features` `gherclj.main` opts, since
   isaac-google's dotted step namespace doesn't match the `isaac.**-steps`
   glob either (flagged in the parent brief).

**Live config**: read-only grep of `~/.isaac/config` on zanebot (no gmail
config present) and yopp (`gmail-routes/micah.edn`, `isaac.edn`) for every
renamed namespace — no hits. yopp's `isaac.edn` only names the `:isaac.comm.gmail`
module id and the `:gmail` comm-type keyword, both unchanged data contracts.
No config edits needed on either host.

**Tests**: `bb ci` — native `bb spec` 147 examples/0 failures/249 assertions;
`bb jvm-features` 45 examples/0 failures/131 assertions, 0 pending — identical
counts to pre-change main. `bb jvm-spec` (JVM fallback) also green.
`bb lint`'s 90 pre-existing errors/19 warnings (clj-kondo not recognizing
speclj macros) are unchanged from main, not part of `bb ci`, out of scope.
No `bb pins` task exists in this repo.

**CI**: GitHub Actions "CI Tests" green on main
(https://github.com/slagyr/isaac-gmail/actions/runs/36754345526).

**Main sha**: `09f5077` (squashed single commit, pushed directly, no PR).
