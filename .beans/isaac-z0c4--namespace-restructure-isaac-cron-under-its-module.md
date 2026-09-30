---
# isaac-z0c4
title: 'Namespace restructure: isaac-cron under its module id'
status: in-progress
type: task
priority: normal
created_at: 2026-09-30T14:12:23Z
updated_at: 2026-09-30T16:56:26Z
parent: isaac-vyqs
blocked_by:
    - isaac-on0o
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

main-sha: isaac-cron c7607f4

**Pin bump.** deps.edn + bb.edn: foundation `9ab2527` → `06d58b75bc52b3e118dc8e81569096de2532a0d4` (exact sha isaac-agent 123d718 itself pins — not foundation's own later main 33ac50d, per the coordinator's correction: `bb pins` treats a repo's foundation pin disagreeing with its agent pin's foundation pin as incoherent); agent `b1de8dc` → `123d71850b480dc0859886e1a4fa53e082c258f1`; isaac-http `71a0413` → `56998543b3e5c40593d2a3ea97b16550e3731463` (isaac-http's own isaac-fkqz rename landed mid-bean; bumped once it had, per the "only bump a pin whose target main has migrated" rule).

**Mapping.** 12 files touched (deps.edn, bb.edn, README.md, resources/isaac-manifest.edn, features/frequencies.feature, 3 src/, 4 spec/, 1 feature-steps/). This repo's own namespaces were already `isaac.cron.*` except two strays, both renamed:
- `isaac.config.schema-spec` (`spec/isaac/config/schema_spec.clj`, tests cron's own manifest schema) → `isaac.cron.config.schema-spec` (`spec/isaac/cron/config/schema_spec.clj`) — matches isaac-agent's own `isaac.agent.config.schema-spec` precedent.
- `isaac.cron-steps` (`feature-steps/isaac/cron_steps.clj`) → `isaac.cron.cron-steps` (`feature-steps/isaac/cron/cron_steps.clj`) — matches the `isaac.<module>.<subject>-steps` pattern (e.g. `isaac.foundation.scheduler-steps`).

Every foundation/agent require got the module prefix: `isaac.config.{loader,api,runtime,schema-compose,schema.resolve,validation-lexicon}`, `isaac.fs`, `isaac.logger`, `isaac.module.{protocol,discovery}`, `isaac.nexus`, `isaac.reconfigurable`, `isaac.scheduler.{cron,runtime}`, `isaac.schema.{lexicon,registered-in}`, `isaac.spec-helper` → `isaac.foundation.*`; `isaac.bridge.core`, `isaac.charge`, `isaac.comm.{delivery.queue,null}`, `isaac.llm.api.grover`, `isaac.session.{context,store.spi}`, `isaac.tool.memory` → `isaac.agent.*`; `isaac.session.frequencies` → `isaac.agent.frequencies` (Micah's explicit call, not `isaac.agent.session.frequencies`, matching isaac-on0o).

**Left alone (justified, data/module-ids/unmigrated-sibling, not this repo's code namespaces):** `:isaac.config/schema`, `:isaac.config/component` (berth keywords, data contracts); `:isaac.agent/comm` (berth keyword); `:isaac.comm.longwave` and the `:isaac.agent` key in `spec/isaac/cron/config/schema_spec.clj`'s `comm-module-index` fixture (fictitious module ids in test data, not real namespaces); `isaac.comm.telly` in deps.edn (isaac-agent's own fixture-module id, left as-is by isaac-on0o); `isaac.http.app` (isaac-http's `app` namespace was already correctly placed pre-rename — only isaac-http's *internal* requires changed, which isn't this repo's concern).

**Non-obvious fixes, for the next repo's rename bean:**
- `src/isaac/cron/service.clj`'s `(ex-info "cron scheduler requires :scheduler in isaac.nexus" {})` — a prose namespace mention inside an error message, not a require; updated to `isaac.foundation.nexus`.
- `spec/isaac/cron/handbook_chapter_spec.clj`'s docstring said "isaac.module.berths' introspection helpers" (prose, not a require) — updated to `isaac.foundation.module.berths`.
- `resources/isaac-manifest.edn`'s comment mirroring `isaac.session.frequencies/frequencies-schema` and `features/frequencies.feature`'s prose mentioning the same — both factual pointers at the now-renamed `isaac.agent.frequencies`, updated.
- `README.md` named `spec/isaac/scheduler_steps.clj` as the file covering cron registration scenarios — that path never actually existed even pre-rename (the real file was `feature-steps/isaac/cron_steps.clj`); corrected to the real (now renamed) path, `feature-steps/isaac/cron/cron_steps.clj`.
- **Real bug, not a rename miss:** `spec/isaac/cron/config/schema_spec.clj`'s "cron table conforms job maps" test binds `isaac.foundation.config.validation-lexicon/*config*` and calls `lexicon/conform` directly, relying on `:crew-exists?` already being in apron's global validations lexicon. On the *old* foundation pin, `:crew-exists?` was one of foundation's own static refs (registered at namespace load, no discovery needed). On the *new* foundation, isaac-h2oo moved it to be an isaac-agent-*contributed* ref (via the `:isaac.config/validation-ref` berth), which only lands in the global lexicon once something calls `register-contributed-existence-refs!` with a real module index — and nothing in this repo's own test suite did that before this test ran (file-load order put it first). Bumping the pins alone exposed this; fixed by adding an explicit `(before-all (nexus/-with-nexus {:fs (fs/real-fs)} (validation-lexicon/register-contributed-existence-refs! (discovery/builtin-index))))` to the describe block, matching the design isaac-agent's own manifest-driven specs rely on, so the test no longer depends on load order.
- **Transient cross-repo collision, self-resolved mid-bean:** after bumping only foundation+agent, `bb spec`/`bb features` failed with `config-schema collision at :comms ... isaac.agent.comm.factory/create! vs isaac.comm.factory/create!` (isaac-agent's new `:comms` schema fragment vs isaac-http's *old*, unmigrated one — this repo's classpath always includes isaac-http, for `isaac.http.app` in feature-step server boots) and `bb jvm-features` failed outright (`FileNotFoundException` on `isaac.config.loader`, isaac-http's app.clj still requiring foundation's pre-rename names). Same class of fallout isaac-davq flagged for its cross-repo CI smoke job. isaac-http's own isaac-fkqz rename bean landed on isaac-http's main (`5699854`) while this bean was in flight; bumping the isaac-http pin to that sha made both failures disappear — no workaround needed, no isaac-http changes made here.

**Live-config greps (read-only, no edits made).** zanebot (`ssh zane@zanebot.tail66e5f8.ts.net`) and yopp (`ssh yopp@yopp`) `~/.isaac/config`: no hits for any of this repo's old (pre-rename) namespace tokens on either host. The only matches were `:cron` (the config table key) and `:isaac.cron` (the module id in `modules.edn`/`isaac.edn`), both data identifiers that were never code namespaces and are unaffected by this rename. No config edits required on either host.

**Test results.** `bb ci` (config-bypass-lint + `bb spec` + `bb jvm-features`): green — 28 spec examples / 0 failures, 21 feature examples / 0 failures. `bb jvm-spec`: 28/28, 0 failures. `bb lint`: same 19 pre-existing clj-kondo "Unresolved symbol" errors as pristine main (speclj macros unresolved without a warmed `.clj-kondo` cache — confirmed identical in kind, one more line than main's 18 only because the new `before-all` block adds one more such line; not a real issue). No `@slow`-tagged scenarios and no slow-lane bb task exist in this repo. No `bb pins`/`lint-pins` task exists in this repo (foundation-only tooling).

Full grep of the tracked tree for any remaining pre-rename namespace token: 0 hits. Every `src/`, `spec/`, `feature-steps/` namespace declaration is `isaac.cron.*` (no justified exceptions needed — this repo has no fixture modules or bb/dev tooling of its own).

**GitHub CI on main-sha c7607f4:** `verify` (bb ci) — green.
