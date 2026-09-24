---
# isaac-1q9m
title: 'isaac-claude-code: drop the vestigial `isaac mcp-bridge` CLI command'
status: completed
type: task
priority: normal
created_at: 2026-09-24T17:58:50Z
updated_at: 2026-09-24T18:34:30Z
---

Micah, 2026-09-24: "Why does Zane still have an MCP bridge command? … definitely drop the command."

The driver never invokes it: `write-mcp-config!` launches the bridge as `bb -cp <process classpath> -m isaac.mcp-bridge.main --turn ID --url URL`, straight by namespace. The `:isaac/cli {:mcp-bridge …}` entry in `src/isaac-manifest.edn` only puts an internal per-turn plumbing command into every operator's `isaac` command list (and carries the inert `:hosted true` marker isaac-mfcd is retiring).

## Acceptance
- [ ] `:isaac/cli` entry removed from the claude-code manifest; `isaac help` on a host with the module no longer lists `mcp-bridge` (scenario in the module's CLI/bridge feature).
- [ ] The namespace and its spec stay for now (the transport bean deletes them); `write-mcp-config!` unchanged here.
- [ ] `bb ci` green; manifest patch bump.

Repo scope: isaac-claude-code.

## Handoff

- Branch: `bean/isaac-mbnb` (isaac-1q9m commit is the first commit on it; isaac-mbnb's commit follows).
- isaac-claude-code sha: `a8bf978` — isaac-1q9m: drop the vestigial mcp-bridge CLI command.
- Files: `src/isaac-manifest.edn` (removed `:isaac/cli {:mcp-bridge …}`, version 0.1.18 → 0.1.19), `spec/isaac/mcp_bridge/main_spec.clj` (swapped the "declares the mcp-bridge command hosted" assertion for one asserting the manifest has no `:isaac/cli` key at all — the namespace and its other specs stay untouched, per the bean), `features/llm/mcp_bridge.feature` (added a scenario asserting the manifest declares no `:isaac/cli` commands, via a new `claude-code-manifest-declares-no-cli-commands` step in `spec/isaac/llm/claude_cli_steps.clj`).
- `write-mcp-config!` untouched in this commit, as required.
- Counts at this commit: `bb spec` 90 examples / 0 failures / 3 pending (real-CLI smokes, expected); `bb features` 58 examples / 0 failures. `bb ci`'s `config-bypass-lint` and `lint-cli-host` both ok.
- Note for the planner: the acceptance text suggested an `isaac help`-style CLI-dispatch scenario, but this repo's feature harness (Grover fixture) doesn't load the module-registry/CLI-dispatch machinery that `isaac-foundation-spec`'s `cli_as_berth.feature` uses (that needs a real `:modules {...}` + subprocess `isaac run with X`, not wired into this repo's `bb.edn` `:features` alias). I substituted a direct manifest-content assertion (matching the pattern the old `mcp_bridge/main_spec.clj` test already used for `:hosted true`) rather than pulling in that heavier harness. Worth a look if you want the literal "isaac help lists no mcp-bridge" behavior proven end-to-end.
- The feature file housing this scenario (`mcp_bridge.feature`) is renamed to `mcp_transport.feature` in the isaac-mbnb commit that follows — the "no CLI command" scenario rides along in that rename, still present and green.

## Landed on main

main-sha: isaac-claude-code a8bf978 (0.1.19), squashed with isaac-mbnb onto 591b5a0c6c9e7906a4772cd57e5221266d304e8b. Planner verified: bb spec 90/0, bb features 60/0.
