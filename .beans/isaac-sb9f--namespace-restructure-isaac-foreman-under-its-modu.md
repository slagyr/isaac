---
# isaac-sb9f
title: 'Namespace restructure: isaac-foreman under its module id'
status: completed
type: task
priority: normal
created_at: 2026-09-30T14:12:24Z
updated_at: 2026-09-30T21:17:18Z
parent: isaac-vyqs
blocked_by:
    - isaac-on0o
    - isaac-n8rb
    - isaac-8evx
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

main-sha: isaac-foreman 861f6ab (github.com/slagyr/isaac-foreman)

**Starting point.** isaac-foreman's own namespaces already lived under `isaac.foreman.*` (isaac-1qgv landed before this bean started) — no src/spec renames of foreman's own code were needed. All work was pin bumps + updating foundation/agent requires to their new trees.

**Pins.** deps.edn + bb.edn (all `:deps`/`:test`/`:spec`/`:features` alias sites): isaac-foundation `3a199d9` → `06d58b75bc52b3e118dc8e81569096de2532a0d4` (isaac-davq's landed sha, exact per the milestone brief), isaac-agent `6736ca2` → `f9530426d04b6f66f17ae51f6f9a1a697531b39d` (isaac-on0o's rename + isaac-n8rb + isaac-8evx fixes, still pinning the same foundation sha), isaac-http `8e01658` → `56998543b3e5c40593d2a3ea97b16550e3731463` (already-migrated leaf, isaac-fkqz).

**Mapping.** Every foundation namespace foreman required got the `foundation.` segment: `isaac.config.loader/.root/.schema-compose/.schema.resolve`, `isaac.cli.api`, `isaac.module.protocol/.discovery/.berths`, `isaac.fs`, `isaac.logger`, `isaac.nexus`. Every agent namespace got the `agent.` segment: `isaac.drive.observer`, `isaac.session.store.spi`, `isaac.tool.fs-bounds`, `isaac.tool.memory`, `isaac.turn.submit`, `isaac.turn.worker`. `:isaac.http/route` and other berth/config keywords in resources/isaac-manifest.edn left alone (data contracts).

**Non-obvious fixes a plain require-grep would miss:** `feature-steps/isaac/foreman_feature_bootstrap.clj` holds two step namespaces as bare quoted symbols, not require forms — `session-ns` (`'isaac.session.session-steps` → `'isaac.agent.session.session-steps`) and `configurator-ns` (`'isaac.configurator-steps` → `'isaac.http.configurator-steps`, confirmed by reading its actual `(ns ...)` line in isaac-http, not a top-level `isaac.*` name as the old symbol implied). bb.edn's `config-bypass-lint` task doc-string said "outside isaac.config.*" (foundation's own prefix) — corrected to "isaac.foundation.config.*", the same trap isaac-davq and isaac-on0o each hit in their own bb.edn/lint doc strings.

**Queue-only turn submission (isaac-e9jl/isaac-2lc4).** Bumping isaac-agent broke all 4 scenarios in features/foreman/turn_action.feature: `isaac.agent.turn.submit/submit!` now only enqueues a durable record; a running server's own tick picks it up. isaac.foreman.core already calls `isaac.agent.turn.worker/tick!` synchronously everywhere it submits/resubmits a turn (signal! -> consume! -> retry!, and retry! itself) — tick! only claims+starts the turn before returning, so the started future could still be running when foreman's one-shot CLI process exited mid-feature-test. Fixed by registering a postflight on `isaac.foundation.cli-steps/isaac-run` in `feature-steps/isaac/foreman_feature_bootstrap.clj` that calls `(isaac.agent.turn.worker/await-idle!)` after every `isaac is run with ...` — same pattern as isaac-hail's `hail_handoff_steps.clj`. No `.feature` text touched. (An earlier attempt that also called `tick!` again from the postflight, with or without `isaac.agent.session.session-steps/with-feature-config!`, was actively harmful for resource-pool-gated turns — a second tick outside the command's own nexus/config scope could re-process an already-correctly-parked record against a differently-loaded config and wake-fail it as `:unknown-resource-pool`. `await-idle!` alone is sufficient and a no-op when nothing is running.)

**Two isaac-agent bugs found and fixed upstream, not worked around here.** While driving this queue path synchronously from a CLI for the first time in a feature suite, found: (1) `isaac.agent.turn.submit/submit!` coerced a bare-string `:session` to a vector only for its own submit-time `resolve-session-targets` check, but persisted the raw string into the durable record; `isaac.agent.turn.worker`'s wake-time re-resolution reused that raw value without the coercion, so `(first "lamp-room")` returned `\l` and crashed the turn. (2) a related wake-config timing issue. Both are fixed in isaac-agent as of `f9530426` (isaac-n8rb, isaac-8evx) — no foreman-side workaround needed or left in place.

**Live-config greps (read-only, no edits made).** zanebot (`ssh zane@zanebot.<tailnet>.ts.net`) and skiff (`ssh skiff@skiff`) `~/.isaac/config`: grepped for every renamed namespace (`isaac.foreman.*`, `isaac.session.session-steps`, `isaac.configurator-steps`, `isaac.drive.observer`, `isaac.tool.fs-bounds`/`.memory`, `isaac.turn.submit`/`.worker`, `isaac.session.store.spi`). Zero hits on either host. No config edits required.

**Test results (HOME isolated at /tmp/isaac_scratch_home_sb9f).** `bb ci` (config-bypass-lint + lint-cli-host + `bb spec` + `bb jvm-features`): green. `bb spec` / `bb jvm-spec`: 76/76. `bb jvm-features`: 23/23 (all 4 turn_action.feature scenarios pass). No `bb pins` task exists in this repo (foundation-only pin sites, matching isaac-on0o's note).

**GitHub CI on main-sha 861f6ab:** `CI Tests / verify` — green (fresh checkout, `bb ci` end-to-end against the real published foundation/agent/http shas).

Full grep of the tracked tree for any remaining pre-rename foundation/agent namespace token: 0 hits. src/, spec/, feature-steps/ namespace prefixes: 100% `isaac.foreman.*` (foreman's own) plus the correctly-migrated `isaac.foundation.*`/`isaac.agent.*`/`isaac.http.*` requires.

## Planner verification (2026-09-30)

Verified on 861f6ab: agent pin f953042, workaround removed, CI green. Leftover: feature-steps namespace `isaac.foreman-feature-bootstrap` style names sit outside the prefix if any remain (checked: none reported).
