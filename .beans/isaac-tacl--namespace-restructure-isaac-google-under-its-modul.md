---
# isaac-tacl
title: 'Namespace restructure: isaac-google under its module id'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-30T14:12:23Z
updated_at: 2026-09-30T17:13:59Z
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

## Findings (branch pushed, not landed — blocked on isaac-http)

Work done on `bean/isaac-tacl` in `isaac-google`, pushed to origin, **not
merged to main**.

**isaac-google's own namespaces are already `isaac.google.*`** — nothing to
rename there. The work is: bump `deps.edn`/`bb.edn` pins to isaac-foundation
`33ac50d9d7a50c9c22b7fe8e1f02c1220c360527` and isaac-agent
`123d71850b480dc0859886e1a4fa53e082c258f1`, and rename every cross-module
reference to their renamed namespaces: `isaac.cli.{api,common,registry}`,
`isaac.component.{factory,protocol,registry,runtime}`,
`isaac.config.{loader,root,schema-compose,schema.resolve}`, `isaac.fs`,
`isaac.logger`, `isaac.main`, `isaac.module.{berths,discovery,protocol}`,
`isaac.nexus`, `isaac.runner`, `isaac.scheduler.runtime`,
`isaac.schema.lexicon` → `isaac.foundation.*`; `isaac.comm.delivery.queue`,
`isaac.llm.{auth.store,http,providers-steps}`, `isaac.tool.memory` →
`isaac.agent.*` — across `src/`, `spec/`, `feature-steps/`, a
`requiring-resolve` symbol (`isaac.component.registry/instance-for` in
`registration.clj`), a fixture manifest `:factory` symbol
(`test-resources/marigold/skybeam/resources/isaac-manifest.edn`), and factual
doc-string/comment pointers (`isaac.runner`, `isaac.component.runtime`,
`isaac.llm.providers-steps` mentions in `feature-steps/isaac/google_steps.clj`
and `spec/isaac/google/handbook_chapter_spec.clj`). Left alone (isaac-http's
own, unmigrated, bean isaac-fkqz still `todo`): `isaac.http.*`,
`isaac.config.server-config`. Left alone (fixture/data, false-positive
matches, not namespaces): `isaac.acme.example`, `isaac.example`,
`isaac.tonotop.example` (fixture URLs), `isaac.edn` (a config filename
mentioned in a string), `isaac.cron`/`isaac.episodes` (other modules' ids,
mentioned in a comment), `:isaac.agent/tools` and other `:isaac.config/*`
berth keywords (data contracts, per the bean).

**Live-config greps (read-only, no edits needed).** zanebot and yopp
`~/.isaac/config`: no hits for any of the renamed namespace tokens on either
host.

**Handbook/README.** No edits needed — `resources/isaac/google/handbook.md`
only names other modules by their (unchanged) module ids (`isaac.agent`,
`isaac.foundation`), and `README.md` has no namespace mentions.

**Test results.** `bb lint`: 101 errors/16 warnings, byte-identical to
pristine pre-bean `main` (pre-existing clj-kondo speclj-macro gap, not a
regression). `bb spec` / `bb jvm-spec`: 267/268 green — the one failure is
real but not fixable here (see below). Full grep of the tracked tree for any
remaining pre-rename foundation/agent namespace token outside the justified
exceptions above: 0 hits.

**The one spec failure** — `handbook_chapter_spec.clj`'s config-schema-compose
test: `config-schema collision at :comms [:schema :value-spec :factory]:
isaac.agent.comm.factory/create! vs isaac.comm.factory/create!`.
`isaac.foundation.module.discovery` composes every `:builtin? true` manifest
found on the classpath; isaac-agent's new manifest contributes
`isaac.agent.comm.factory/create!` for the `:comms` berth, but isaac-http's
still-pinned old manifest (sha `689d3686`, unmigrated) contributes the same
berth path with the old bare `isaac.comm.factory/create!` — a real value
mismatch now that agent renamed. Confirmed **not** present on pristine
pre-bean `main` (3/3 green there, both sides still bare and identical). Not
fixable from isaac-google's side; resolves once isaac-http migrates.

**Blocker — `bb jvm-features` (and therefore `bb ci`) cannot even boot, and
CI would be red too.** gherclj's `"isaac.**-steps"` feature-runner glob
eagerly `require`s every matching step namespace on the classpath, including
`isaac.http.server-steps` (from the `isaac-http-spec` coordinate — google
uses it via `requiring-resolve` for the push-door/oauth-callback HTTP
scenarios). That namespace still directly `:require`s several of isaac-http's
own bare namespaces (at minimum `isaac.component.protocol`, `isaac.config.loader`,
`isaac.config.runtime`, `isaac.nexus`, `isaac.fs`, `isaac.logger`, `isaac.main`,
`isaac.module.loader`, `isaac.session.store.spi`, `isaac.comm.{factory,registry}`)
— none of which exist anymore once foundation/agent are pinned to their
renamed mains. `clojure -M:features` dies at namespace-load time with
`FileNotFoundException: Could not locate isaac/fs__init.class...` (via
`isaac.http.audit`), before any scenario runs. Confirmed **not** a
pre-existing failure: pristine pre-bean `main` runs `bb jvm-features` clean
(44/44 examples). `.github/workflows/ci-tests.yml` runs `bb ci` directly
against the pinned shas, so this reproduces in CI identically —
**landing this branch to main would turn isaac-google's own CI red**, not a
downstream smoke job (unlike isaac-davq's accepted "Server boot..." fallout,
which was foundation's own `bb ci` staying green while a *different* repo's
integration job went red).

This is the same blocker isaac-81ua (isaac-hail) independently hit and
reported. Per the milestone's own ordering note ("A module that requires
another leaf... goes after that leaf"), isaac-http is such a leaf for every
module that pulls in `isaac-http-spec`/`isaac-http-test-support` for HTTP
feature-step helpers — likely most of wave 3. isaac-fkqz (isaac-http's own
rename bean) is now claimed in-progress, which should unblock this once it
lands.

**Not landing on my own judgment.** Leaving this bean `in-progress` (not
`unverified`), branch `bean/isaac-tacl` pushed to `isaac-google` for review
rather than merging red CI to main. No main-sha to hand to isaac-gchat/
isaac-gmail yet — they should wait on isaac-fkqz landing, then this bean
resuming and merging, before pinning to isaac-google. Recommend: land
isaac-fkqz first, then re-run `bb ci` on `bean/isaac-tacl` (rebased on
whatever isaac-google/main looks like at that point) before merging.

## Landed on main

main-sha: isaac-google c9c92f868db5bcd03510be70772e29c2c4003adb

isaac-http landed its own migration (isaac-fkqz, main `56998543b3e5c40593d2a3ea97b16550e3731463`, `isaac.http.*`, on foundation `33ac50d` / agent `123d718`), which unblocked this bean. Resumed on the same worktree/branch:

- Bumped isaac-http/isaac-http-spec/isaac-http-test-support pins (`deps.edn`, `bb.edn`) old `689d3686` → `56998543b3e5c40593d2a3ea97b16550e3731463`.
- `isaac.http.{auth,http,oidc,oidc-fixture,server-steps}` requires needed no change — isaac-http's migration kept those names. The one real fix: `isaac.config.server-config` → `isaac.http.config.server-config` (3 mentions in `src/isaac/google/cli.clj`, 1 in `spec/isaac/google/cli_spec.clj` — a `requiring-resolve` symbol plus doc-string/comment pointers), confirmed against the new `isaac-http` tree (`src/isaac/http/config/server_config.clj`, `defn server-config`).
- Rebased on `origin/main` (no divergence — main hadn't moved), squashed the two working commits into one, pushed `c9c92f8:main` directly (push was accepted, not denied by the classifier).
- Fast-forwarded the shared `isaac-google` checkout to `c9c92f8`; deleted branch `bean/isaac-tacl` and its worktree.

**Test results (HOME isolated at `/tmp/isaac_scratch_home_tacl`).** `bb config-bypass-lint`: ok. `bb lint`: 101 errors/16 warnings, same pre-existing count as pristine main (not a regression). `bb spec`: **268/268 green** (the isaac-agent/isaac-http `:comms` factory collision from the earlier note is gone now that isaac-http contributes `isaac.http.comm.factory`-shaped names consistent with the new agent — resolved by isaac-http's own migration, not by anything here). `bb jvm-spec`: **268/268 green**. `bb jvm-features`: **44/44 green** (the earlier `FileNotFoundException` boot failure is gone — isaac-http's `isaac.http.server-steps` no longer requires any bare pre-rename namespace). `bb ci`: green end-to-end.

**GitHub CI on main-sha c9c92f8:** `CI Tests / verify` — green (`gh run watch 36749731708`, 43s).

Full grep of the tracked tree for any remaining pre-rename namespace token (foundation/agent/http) outside the justified exceptions in the prior note: 0 hits.

No live-config edits were needed (checked before landing, see above — no hits on zanebot or yopp for any renamed namespace token).

**For isaac-gchat / isaac-gmail:** pin isaac-google to `c9c92f868db5bcd03510be70772e29c2c4003adb`.

## Planner verification (2026-09-30)

Verified on c9c92f8: pins on migrated foundation/agent/http, CI green. Leftovers sent back: `isaac.google-steps` outside the prefix, stale lint doc-string.
