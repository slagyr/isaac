---
# isaac-ps7a
title: 'Namespace restructure: isaac-cli-server under its module id'
status: completed
type: task
priority: normal
created_at: 2026-09-30T14:12:24Z
updated_at: 2026-09-30T17:52:22Z
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

main-sha: isaac-cli-server f1477a7

### Namespace mapping
- isaac.cli.args -> isaac.foundation.cli.args
- isaac.cli.host -> isaac.foundation.cli.host
- isaac.cli.registry -> isaac.foundation.cli.registry
- isaac.logger -> isaac.foundation.logger
- isaac.nexus -> isaac.foundation.nexus
- isaac.startup.classpath-cache -> isaac.foundation.startup.classpath-cache
- isaac.spec-helper -> isaac.foundation.spec-helper
- isaac.config.api -> isaac.foundation.config.api
- isaac.config.schema-compose -> isaac.foundation.config.schema-compose
- isaac.config.schema.resolve -> isaac.foundation.config.schema.resolve
- isaac.fs -> isaac.foundation.fs
- isaac.module.discovery -> isaac.foundation.module.discovery
- isaac.module.berths -> isaac.foundation.module.berths
- isaac.module.protocol -> isaac.foundation.module.protocol (manifest :factory)
- isaac.step-tables -> isaac.foundation.step-tables (NOT isaac.http.step-tables — this repo's spec classpath pulls step-tables from isaac-foundation-test-support, confirmed by bb.edn :deps; isaac-http-test-support is only wired into the JVM deps.edn :test alias, not bb.edn's native classpath)
- Own namespaces (isaac.cli-server.*) were already under the module-id prefix before this bean; no rename needed there.
- Pins bumped: isaac-foundation -> 06d58b75bc52b3e118dc8e81569096de2532a0d4 (exact, per isaac-agent 123d718's own foundation pin); isaac-http -> 56998543b3e5c40593d2a3ea97b16550e3731463 (current main). No isaac-agent/isaac-google pin needed — this repo doesn't depend on either.
- Berth/config keywords (:isaac.config/schema, :isaac.http/route) left untouched, as instructed.

### Live-config findings
Read-only grep of zanebot (~/.isaac/config) and yopp (~/.isaac/config) for all old namespace names above: no hits on either host. No config edits needed.

### Tests / CI
- bb ci (native): config-bypass-lint ok; 20 spec examples/53 assertions green; 20 feature examples/88 assertions green.
- bb jvm-spec / bb jvm-features: both fail with 'Could not locate speclj/main on classpath' — reproduced identically on pre-change main (same isaac-foundation-test-support test_timeout.clj stack trace at the old pin), so this is a pre-existing environmental issue, not introduced by this bean.
- No bb lint or bb pins task exists in this repo's bb.edn.
- GitHub CI (main, run 36754331420): green.

## Planner verification (2026-09-30)

Verified on f1477a7: all namespaces isaac.cli-server.*, CI green. Pre-existing: bb jvm-spec/jvm-features fail locally with speclj/main not on classpath (also on old main).
