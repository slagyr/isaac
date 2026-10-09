---
# isaac-81ua
title: 'Namespace restructure: isaac-hail under its module id'
status: completed
type: task
priority: normal
created_at: 2026-09-30T14:12:23Z
updated_at: 2026-09-30T17:17:20Z
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

An installed Isaac runs one foundation and one agent, so zanebot/skiff don't take the new foundation until every installed module has migrated. Each repo's main stays green on its own pins meanwhile.

## Every child bean also

- greps zanebot and skiff live config (read-only, `ssh zane@zanebot…` / `ssh skiff@skiff`) for namespace names in data (hook `:factory`, embedding `:namespace`, etc.) and lists required config edits in the bean;
- updates its handbook chapter and README where namespaces are named;
- is ungated (mechanical refactor): acceptance = full CI green on main, a grep showing no namespaces outside the module's id prefix in src/spec (list justified exceptions), planner verification.

## Landed on main

main-sha: isaac-hail 923c1d8

Was blocked on isaac-http lagging the rename (see the original findings below,
kept for the record). isaac-http migrated its own main to `5699854` (`isaac.http.*`,
foundation `33ac50d`, agent `123d718`) while this bean was in flight. Resumed:
bumped isaac-http's pin from `42e302f3586737543fd8eb3393ae2d9bfcdad977` to
`56998543b3e5c40593d2a3ea97b16550e3731463` in `deps.edn` (`:spec` alias) and
`bb.edn` (`isaac-http`/`-spec`/`-test-support`). isaac-http's own namespaces
(`isaac.http.auth`, `isaac.http.server-steps`) were already `isaac.http.*`
before its rename, so no require changes were needed beyond the pin bump.
`.github/workflows/ci-tests.yml` resolves the isaac-http sha dynamically from
`deps.edn`'s `:spec` alias — no hardcoded sha there to touch.

Rebased on `origin/main` (no-op, unchanged). `bb ci` (HOME-isolated,
`/tmp/isaac_scratch_home_hail`): config-bypass-lint ok, lint-cli-host ok,
`bb spec` 67/67, `bb features` 104/104 — all green, the isaac-http blocker is
gone. `bb jvm-spec` (same isolated HOME): 67/67. No `@slow` scenarios in this
repo. Squashed to one commit (923c1d8) and pushed directly to `main`
(`git push origin 923c1d8:main` — not denied). GitHub CI (`CI Tests`) green on
923c1d8 (run 36749691103, 50s).

**Not done:** fast-forwarding the shared checkout at
`/Users/micahmartin/agents/isaac/plan/isaac-hail` — `git pull --ff-only` there
was denied by the Claude Code auto-mode classifier ("Out-of-Place
Publication") after a successful `git fetch origin`. The remote/origin/main
ref is already at 923c1d8; only the shared checkout's local working copy
(still on 1279c97) needs a fast-forward. Deliberately not retried through
another command per the denial's own instructions — flagging for Micah/the
next actor with write access to run `git -C
/Users/micahmartin/agents/isaac/plan/isaac-hail pull --ff-only` (or equivalent)
by hand. Branch `bean/isaac-81ua` and its worktree
(`/Users/micahmartin/agents/isaac/plan/isaac-hail-isaac-81ua`) were left in
place rather than deleted, since the shared-checkout ff-only is still
outstanding.

### Original findings (recorded while blocked, superseded above)

Branch `bean/isaac-81ua` pushed to `isaac-hail` (not merged to main):
https://github.com/slagyr/isaac-hail/tree/bean/isaac-81ua (commit b27793d).

**Mapping.** 5 files moved: `src/isaac/tool/hail.clj` → `src/isaac/hail/tool.clj`
(ns `isaac.tool.hail` → `isaac.hail.tool`), its spec `spec/isaac/tool/hail_spec.clj`
→ `spec/isaac/hail/tool_spec.clj` (`isaac.tool.hail-spec` → `isaac.hail.tool-spec`),
a misplaced spec `spec/isaac/config/hail_loader_spec.clj` (tests this repo's own
`isaac-manifest.edn` hail-band schema, despite its `isaac.config.hail-loader-spec`
ns) → `spec/isaac/hail/loader_spec.clj` (`isaac.hail.loader-spec`), and the
feature-steps file `feature-steps/isaac/hail_handoff_steps.clj` → `feature-steps/isaac/hail/handoff_steps.clj`
(`isaac.hail-handoff-steps` → `isaac.hail.handoff-steps`, was a flat namespace
sitting outside the `isaac.hail.*` prefix). Every other src/spec namespace was
already `isaac.hail.*` and untouched. `src/isaac-manifest.edn`'s `:factory
isaac.tool.hail/...` updated to `isaac.hail.tool/...`; berth/config keywords
(`:isaac.config/schema`, `:isaac.config/component`, `:isaac.config/check`,
`:isaac.agent/tools`) left as-is (data contracts).

**Pin bump + requires.** `deps.edn`/`bb.edn`: isaac-foundation pinned to
`33ac50d9d7a50c9c22b7fe8e1f02c1220c360527`, isaac-agent to
`123d71850b480dc0859886e1a4fa53e082c258f1`. isaac-http's pin
(`42e302f3586737543fd8eb3393ae2d9bfcdad977`) left untouched — its `main` still
requires pre-rename foundation namespaces (`isaac.module.protocol`), so it
hasn't migrated. Updated every foundation/agent require across src/spec/feature-steps:
`isaac.cli.{api,common,host}`, `isaac.config.{api,loader,root,schema-base,
schema-compose,schema.resolve,validation}`, `isaac.module.{discovery,protocol}`,
`isaac.fs`, `isaac.logger`, `isaac.nexus`, `isaac.reconfigurable`,
`isaac.schema.lexicon` → `isaac.foundation.*`; `isaac.session.{store.spi,
spec-helper,frequencies}`, `isaac.step-tables`, `isaac.tool.{fs-bounds,memory}`,
`isaac.turn.{queue,submit,worker}` → `isaac.agent.*` (`isaac.session.frequencies`
→ `isaac.agent.frequencies`, not `isaac.agent.session.frequencies`, matching
isaac-on0o's explicit call). `deps.edn`/`bb.edn` feature-runner step-globs:
`isaac.config.agent-steps` → `isaac.agent.config.agent-steps`, `isaac.config.config-steps`
→ `isaac.foundation.config.config-steps`, `isaac.scheduler-steps` →
`isaac.foundation.scheduler-steps`, `isaac.session.session-steps` →
`isaac.agent.session.session-steps`, `isaac.comm.comm-steps` →
`isaac.agent.comm.comm-steps`, `isaac.tool.tools-steps` → `isaac.agent.tool.tools-steps`;
`isaac.http.server-steps` left alone (isaac-http unmigrated). `bb.edn`'s
config-bypass-lint doc string corrected to name `isaac.foundation.config.*`
(matches the davq/on0o precedent). Prose doc-comment pointers fixed to their
real new names: `isaac.module.berths` → `isaac.foundation.module.berths`
(handbook_chapter_spec), `isaac.session.store.spi` → `isaac.agent.session.store.spi`
and `isaac.session.cli` → `isaac.agent.session.cli` (both in comments, no code
effect). `deps.edn`'s `:features` alias step-globs still list two dead entries,
`isaac.hail-steps` and `isaac.hail-hlt1-steps`, that don't correspond to any
real namespace (only `isaac.hail.handoff-steps` exists as a feature-steps file)
— pre-existing before this bean, left alone, flagging for a separate cleanup.

**Live-config greps (read-only, no edits needed).** zanebot and skiff
`~/.isaac/config`: no hits for `isaac.tool.hail`, `isaac.hail-handoff-steps`, or
`isaac.config.hail-loader-spec` on either host.

**Handbook/README.** `resources/isaac/hail/handbook.md` only names other
modules by their topic ids (`isaac.agent`, `isaac.foundation`, `isaac.http`,
`isaac.hail`) — those are unchanged module ids, not code namespaces, so no
edits needed. `README.md` has no namespace mentions.

**Test results.** `bb lint`: 46 errors/9 warnings, byte-identical to pristine
pre-bean `main` (pre-existing speclj/clj-kondo cache gap, not a regression).
`bb spec`: 67/67 green. `bb jvm-spec`: 67/67 green. Full grep of the tracked
tree for any remaining pre-rename foundation/agent namespace token outside the
justified exceptions above: 0 hits.

**Blocker — `bb features` (and therefore `bb ci`) is red, and CI would be red
too.** isaac-hail's own feature harness (`bb.edn`'s single flat `:deps`, no
per-alias override) loads isaac-http's `spec/isaac/http/server_steps.clj` (it
mounts isaac-hail's `/hail/send` route inside a real isaac-http server for
`features/http.feature`). That file still `:require`s the pre-rename
`isaac.component.protocol` — gone from the classpath now that foundation is
pinned to the post-rename sha. Confirmed on pristine pre-bean `main`: `bb
features` is 104/104 green there, so this is not a pre-existing failure — it's
triggered solely by the mandatory foundation pin bump, given isaac-http hasn't
migrated. `.github/workflows/ci-tests.yml` checks out isaac-http at the pinned
sha and runs `bb ci`, so this reproduces in CI exactly as locally: **landing
this branch to main would turn isaac-hail's own CI red**, not a downstream
smoke job (unlike isaac-davq's accepted "Server boot..." fallout on
foundation's side). Per the milestone's own ordering note ("A module that
requires another leaf... goes after that leaf"), isaac-http is such a leaf for
isaac-hail (hard dep for feature testing) and should migrate first — there's
no isaac-http child bean under isaac-vyqs yet. Not landing on my own judgment:
leaving this bean `in-progress` (not `unverified`) with the branch pushed for
review, rather than merging red CI to isaac-hail's main. Recommend: cut an
isaac-http rename bean (parent isaac-vyqs), land it first, then resume/re-verify
this branch's `bb features` before merging.

## Planner verification (2026-09-30)

Verified on 923c1d8: every namespace under isaac.hail.*, no old foundation/agent/http references, CI green. Shared-checkout ff and branch/worktree cleanup were classifier-blocked for the worker; left for Micah.
