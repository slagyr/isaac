---
# isaac-dcxk
title: 'foundation: server-only commands are handed to a registered forwarder (berth :isaac/cli-forwarder)'
status: in-progress
type: feature
priority: high
tags:
    - cli
created_at: 2026-11-16T18:26:17Z
updated_at: 2026-11-16T18:40:57Z
---

Repo: **isaac-foundation**. Bean A of three (B: cli-server + cli-proxy routing with a local token; C: Foreman push bus). Micah, 2026-11-16: "Some commands are server-only." Commands like `foreman signal` and `dream run` run in a separate process today when typed on the host, so their writes never pass through the server (no stream push) and they miss server-held state (the dream OAuth bug). Spec: `features/cli/server_only.feature`.

## What

- **Flag.** `:isaac/cli` entries accept `:server-only true` (manifest schema in `src/isaac-manifest.edn`, registry in `isaac.foundation.cli.registry`). Unlike `:local-only`, it means the command must run inside the server.
- **Berth.** A new `:isaac/cli-forwarder` berth: at most one forwarder (a module contributes a factory). Contract: given `{:argv [...] :root "<root>"}` it returns `{:exit <n>}` (the command ran elsewhere; that is the exit code) or `{:no-server true}` (nothing to forward to).
- **Dispatch.** When a `:server-only` command is invoked in a CLI process and a forwarder is registered, hand it the full argv (command and its args, minus `--root` handled as today) and exit with its code. On `:no-server`, or with no forwarder, run the command locally as today. Never forward during embedded dispatch (`isaac.foundation.cli.host` `run-embedded*`): a command already inside the server runs there.
- **Fixtures** (`modules/`, next to `marigold.cli.greeter`):
  - `marigold.cli.where`: contributes `where` with `:server-only true`; prints `process` when it runs locally.
  - `marigold.cli.forwarder`: contributes the forwarder; prints `forwarded: <argv joined by spaces>` and answers from the root file `forwarder.edn` (`{:exit n}`, default 0, or `{:result :no-server}`).

The real forwarder (finding the running server, the local token) is bean B.

## Acceptance

`bb features features/cli/server_only.feature` green with `@wip` removed (5 scenarios), plus `bb spec`, `bb features` and `bb jvm-features` green. Do not bump the version (the deploy train does).

feature-baseline: isaac-foundation 6303e29d012a7cb1a8df2a16bcfd98d04acd2ce7
feature-blob: isaac-foundation features/cli/server_only.feature dbdf3b96910b4f0ef4413a5117979d730c6e1857


## Landed on main (2026-11-16)

main-sha: isaac-foundation 0f69809ad40e874a44609ecd8e027f49570105b7

Implemented server-only CLI dispatch, optional single forwarder berth, and Marigold fixtures. bb spec, bb features, bb ci, focused server_only feature green. JVM features: 367 examples, 0 failures with JAVA_TOOL_OPTIONS=-Dclojure.gitlibs.dir=<checkout>/target/gitlibs; JVM process remains alive past the runner timeout after Speclj finishes (also reproduced at 180s).
