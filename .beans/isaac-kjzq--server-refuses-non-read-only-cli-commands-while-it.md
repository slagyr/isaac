---
# isaac-kjzq
title: Server refuses non-read-only CLI commands while its module basis is stale (restart pending); :read-only manifest hints
status: todo
type: feature
priority: high
tags:
    - cli
created_at: 2026-09-18T01:24:43Z
updated_at: 2026-09-18T01:24:43Z
parent: isaac-eqkb
blocked_by:
    - isaac-qvhy
---

Child of isaac-eqkb (split out of isaac-gar0, 2026-09-17). Blocked by isaac-qvhy (embedded dispatch).

## Why

Once local `isaac` routes through the server (isaac-gar0), the CLIENT cannot judge whether the server's classpath is current — and after `modules upgrade` / a foundation bump the server is behind on-disk until restart. A client-side basis check was the old isaac-eqkb design; it belongs on the server, which already knows its loaded basis (isaac-tki3 `:basis`: foundation version + module SHAs; config mtimes EXEMPT — hot-reloaded).

## Design

- cli-server embedded dispatch compares the loaded basis to the on-disk basis (cheap: reuse the startup-cache basis computation, memoized on the watched mtimes) before running a hosted command.
- Behind ⇒ commands not marked read-only are refused: stderr `server restart pending — restart the server, or run with --local`, exit **75** (EX_TEMPFAIL), log `:cli/refused-stale-basis :argv`. Read-only commands still run.
- Manifest `:isaac/cli` entries gain `:read-only true` or `:read-only #{"list" "show"}` (per subcommand; first non-flag arg after the command). Registry passthrough (foundation one-liner, like `:local-only`). Default = mutates.
- Hints to add: foundation `help`, `logs`, `config get|show|validate`, `modules list`; agent `sessions list|show`, `crew`, `turns list`; hail `hail list|show|status`; episodes `recall`, `episodes list`; worksite `worksites list`. (Reads only — when unsure, leave it mutating.)
- Cold `isaac` (`--local`, or no remote setting) is unaffected: a cold process always has the on-disk basis.

## Scenarios (committed @wip — isaac-cli-server `features/cli/endpoint.feature` @ 6e384cb)

| line | scenario |
|------|----------|
| :225 | a server whose module basis is stale refuses a mutating command with restart pending |
| :238 | a server whose module basis is stale still runs a read-only command |
| :248 | read-only is per subcommand when the manifest lists subcommands |
| :264 | a current basis runs everything |

Fixture commands added to isaac-qvhy's set: `fx-read` (`:read-only true`, prints "read ok"), `fx-multi` (`:read-only #{"list"}`).

## Step ledger

| step | status |
|------|--------|
| the cli-server handler with the fixture commands registered | reuse (isaac-qvhy) |
| a /cli client sends start with argv … / the handler sends frames: / the cli log has entries matching: | reuse |
| **the server's loaded module basis is behind the on-disk basis** | **NEW — binds the basis seam so loaded ≠ on-disk for the scenario** |

One new step.

## Acceptance
```
cd isaac-cli-server && bb features features/cli/endpoint.feature && bb ci
cd isaac-foundation && bb spec spec/isaac/cli   # :read-only passthrough
```
Manifests with `:read-only` hints: each module's `bb ci` green (manifest schema accepts the key). Version bumps; pins are a train step.
