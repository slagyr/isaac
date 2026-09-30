---
# isaac-qn7h
title: 'Namespace restructure: isaac-claude-code under its module id'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-30T14:12:23Z
updated_at: 2026-09-30T17:41:14Z
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

main-sha: isaac-claude-code 8c54c38a7a44215bedad58c30e3d3bece725c0f9

**Pin correction (per coordinator, mid-task):** pinned isaac-foundation to exactly `06d58b75bc52b3e118dc8e81569096de2532a0d4` (not `33ac50d`) because that's the exact foundation sha isaac-agent's own `123d71850b480dc0859886e1a4fa53e082c258f1` pins — `bb pins`/lint-pins fails CI with "incoherent pins" when a repo's own foundation pin disagrees with its agent dependency's transitive foundation pin (isaac-http hit this). isaac-agent bumped to `123d71850b480dc0859886e1a4fa53e082c258f1` (current main). isaac-http (a spec/features-only test dependency here, no direct code import) bumped to its migrated main `56998543b3e5c40593d2a3ea97b16550e3731463`.

**Mapping.** This module's own 3 src files + 8 spec files + 1 resource, all previously under `isaac.llm.*` (this repo's own namespace prefix pre-migration, `isaac-claude-code` having been extracted from `isaac-agent`), moved to `isaac.provider.claude-code.*` matching the manifest's module id:
- `isaac.llm.api.claude-cli` → `isaac.provider.claude-code.api.claude-cli`
- `isaac.llm.mcp-listener` → `isaac.provider.claude-code.mcp-listener`
- `isaac.llm.mcp-route` → `isaac.provider.claude-code.mcp-route`
- `isaac.llm.claude-cli-spec/-real-spec/-steps`, `claude-driver-spec`, `handbook-chapter-spec`, `mcp-listener-spec`, `mcp-route-spec/-steps` → same-named specs under `isaac.provider.claude-code.*`
- `resources/isaac/llm/handbook.md` → `resources/isaac/provider/claude_code/handbook.md` (package no longer matched; manifest's `:handbook` updated to match)

Every foundation/agent require in this repo's own code got the new prefix (foundation: `config.env`, `config.loader`, `config.schema-compose`, `config.schema.resolve`, `fs`, `logger`, `marigold`, `module.discovery`, `module.protocol`, `nexus`; agent: `bridge.cancellation`, `comm.null`, `drive.turn`, `drive.provider-wall` (docstring only), `llm.api.protocol`, `llm.followup`, `llm.prompt.builder`, `llm.providers`, `llm.tool-loop`, `marigold.agent`, `mcp.turns`, `session.session-steps`, `session.spec-helper`, `session.store.spi`, `step-tables`, `tool.names`, `tool.registry`, `tool.tools-steps`). Manifest symbols updated: `:factory isaac.foundation.module.protocol/module`, `:claude-cli {:factory isaac.provider.claude-code.api.claude-cli/make}`, `:handler isaac.provider.claude-code.mcp-route/handle`, `:handbook "isaac/provider/claude_code/handbook.md"`. Berth/module-id keywords (`:isaac.agent/llm-api`, `:isaac.http/route`, `:isaac.agent/provider-template`, `:isaac.provider.claude-code`) left untouched — data contracts, per the parent bean.

**Non-obvious pick: `isaac.step-tables` → `isaac.agent.step-tables`, not `isaac.foundation.step-tables`.** Both repos now ship their own diverged copy of what used to be one bare `isaac.step-tables` file. Foundation's copy uses `re-matches` (whole-string) for `:regex` table cells; agent's copy uses `re-find` (substring) for `:regex` on strings. Pre-migration, this repo's own step files (`claude_cli_steps.clj`, `mcp_route_steps.clj`) got the lenient substring behavior by classpath accident (agent's copy silently won the collision on the bare `isaac.step-tables` path). Picked `isaac.agent.step-tables` to preserve that existing substring-matching behavior exactly (this module extracted from isaac-agent; its step files' table conventions match agent's).

**Found by testing, not the grep sweep:** `features/llm/api/claude_driver.feature`'s scenario "isaac-nni3" had a `#"stream-json requires --verbose"` regex cell (no `.*` wrapper) in a "the log has entries matching:" table. That step is answered by **foundation's own** `isaac.foundation.log-steps`, which has *always* required `isaac.foundation.step-tables` directly (even at the old pin) — not the bare, agent-collision-won `isaac.step-tables`. Foundation's step-tables' `:regex` is `re-matches` (whole-string), so this cell only ever "passed" because the old pin's `isaac.foundation.log-steps` itself required the bare `[isaac.step-tables :as match]` (pre-davq), which resolved to agent's lenient copy by the same classpath accident. davq's rename correctly decoupled `isaac.foundation.log-steps` to require its own `isaac.foundation.step-tables` — exposing that this cell was never a real whole-string match. Fixed by wrapping it `#"(?s).*stream-json requires --verbose.*"` to match the sibling cells' existing style (line 217 already does this for a similar case) — behavior-preserving, not a scope change.

**Live-config greps (read-only).** zanebot (`ssh zane@zanebot.tail66e5f8.ts.net`) `~/.isaac/config`: no hits for `isaac.llm.` — no code-namespace references in live data (only the untouched `:isaac.provider.claude-code` module-id keyword would appear, which is correct as-is). No config edits needed on zanebot. **yopp was unreachable this session** (`ssh zane@yopp.tail66e5f8.ts.net`: `Permission denied (publickey)`) — could not complete the yopp-side grep; flagging for a follow-up check once yopp access is available, though per the "Deploy freeze" section neither host takes the new foundation/agent yet regardless.

**Test results.** `bb ci` (config-bypass-lint + lint-cli-host + bb spec + bb features): green, 98 spec examples / 303 assertions (3 pending, opt-in `@real` smoke) + 67 feature examples / 223 assertions — identical counts to pre-change main. `bb jvm-spec`: 87/89 (2 failures), confirmed byte-for-byte identical on pristine pre-change main under the same `HOME=<scratch>` isolation — pre-existing `:model-exists?` lex-validator classpath leak (the same trap isaac-davq's bean documented), not a rename regression. `bb jvm-features`: 67/67 green; the "timed out after 60s" JVM-shutdown message after is also reproduced identically on pre-change main (exit 0, harmless teardown quirk). No `bb pins` task exists in this repo. Full grep of the tracked tree: 0 remaining `isaac.llm.` references; all src/spec namespaces are `isaac.provider.claude-code.*` (plus the untouched berth/module-id keywords, justified above).

**GitHub CI on main-sha 8c54c38:** `verify` (bb ci) — green.
