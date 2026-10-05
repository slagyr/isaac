---
# isaac-q6zb
title: 'Handbook chapter: isaac-mcp'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-10-05T14:38:34Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-mcp` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

- Cover the concepts THIS module owns (its config tables and keys, tools, comms, commands, behaviors), written for a MODEL operating Isaac, not a developer: what it is, how to change it with `handbook__configure` (config paths), how to verify, then a `### Troubleshooting` subsection under each concept. No source-code walkthroughs.
- Ground every claim in the module's code, manifest and feature scenarios. Mark anything you can't verify with `[verify]` for Micah.
- Refer to other modules' concepts by one line plus their topic id (e.g. crews → `isaac.agent`); don't re-document them. Use foundation's chapter (`isaac-foundation/src/isaac/foundation/handbook.md`) as the pattern and vocabulary.
- Schema `:description`s: any config key this module declares without a `:description` gets one (the config reference is generated from them).

## Acceptance

- Manifest `:handbook` names the chapter; loading config raises no "handbook ... not found" warning.
- A lint spec like foundation's `spec/isaac/foundation/handbook_chapter_spec.clj` (backticked `config:<path>` refs resolve in the composed schema; `isaac <command>` refs exist).
- The repo's full CI green; landed on main with a single squash commit.

## Ungated

Documentation plus a lint spec; no behavior change. Worker hands off `tag=unverified`; Micah reviews the chapter text.

## Landed on main

main-sha: isaac-mcp fd357a41982a6fa54b6122c40fa3c06f8dcfcee2

Chapter at `resources/isaac/mcp/handbook.md` (topic `isaac.tool.mcp`), manifest `:handbook` added, lint spec `spec/isaac/mcp/handbook_chapter_spec.clj` mirroring foundation's/isaac-hail's. Every `:mcp` schema key already had a `:description` — none needed backfilling.

Repinned isaac-foundation/isaac-agent/isaac-http to current main tips (6b95406 / 1d26287 / 42e302f, the same set isaac-hail just moved to) in both `deps.edn` and `bb.edn`. No fallout: isaac-mcp's specs/features don't touch turn-queue tick timing (isaac-e9jl) or http bearer-auth adjudication (isaac-q1iu), so no test-step fixes were needed. `bb ci` green locally (config-bypass-lint ok, 45 specs/125 assertions, 13 features/26 assertions) and on GitHub CI (run 36677242066, green).

Sections: MCP servers (the `mcp` config table — command/args/env/cwd/timeout-ms), Tool names and reaching a crew (`<server-id>__<tool-name>`, crew `tools.allow` glob, argument stripping), Connecting (background connect inside the server process, one-shot processes await, failure-hold backoff, `tools/list_changed` recatalog), Reaching a remote MCP server (stdio-bridge pattern; the mcp-remote `AUTH_HEADER` trap — put the token in `args`, not `env`), Failure visibility (`:mcp/*` log events).

`[verify]` (3, left for Micah): the stdio-bridge-for-remote pattern is described as the only route today since isaac-mcp has no native `:url`/`:type` transport — confirm that's still accurate; the exact bridge-tool flag/env-var name in the AUTH_HEADER example is illustrative, not exercised by any test here; worth double-checking against whatever bridge tool is actually in use.
