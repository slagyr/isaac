---
# isaac-vorl
title: Modules can register CLI subcommands
status: completed
type: feature
priority: normal
created_at: 2026-05-20T20:12:56Z
updated_at: 2026-05-20T21:25:40Z
---

## Motivation

The manifest already supports `:comm`, `:hook`, `:llm/api`, `:provider`,
`:slash-commands`, `:tools` (`isaac/module/manifest.clj:32`). It does **not**
support CLI commands. Today, any subcommand of `isaac` must live in core —
which is why ACP-as-module came up as awkward in conversation: turning ACP
into a module would invert the layering, since `isaac chat`/`isaac acp` are
CLI surfaces.

Adding `:cli` as an extension kind closes that gap and gives parity with the
other extension types.

## Design constraint: bootstrap order

CLI arg parsing happens in `isaac.main` before config is loaded; module
discovery currently runs *during* config load (`isaac/config/loader.clj` calls
`module-loader/discover!`). For CLI dispatch to see module-contributed
commands, one of:

- Pull a lightweight discovery pass up into `main.clj` before subcommand
  dispatch, then reuse the index when full config load runs.
- Defer CLI dispatch until after a minimal config load that only resolves
  `:modules` (no full validation).

Measured cost of `discover!` is ~8 ms warm on a host with 8 git-coord modules
(zanebot, 2026-05-20). Cold-cache pays the usual one-time git fetch — same
deal modules already make today.

## Sketch

Add `:cli` to `known-extend-kinds` in `isaac/module/manifest.clj` with the
same shape as the other extension kinds:

```edn
{:cli {:foo {:factory my.ns/cli-command
             :description "Does foo"}}}
```

`main.clj` builds the subcommand table by merging built-in commands with
`(get-in module-index [<id> :manifest :cli])` entries before dispatch.

## TODOs

- [x] Decide bootstrap approach (early discovery vs deferred dispatch).
- [x] Add `:cli` to manifest schema + `known-extend-kinds` in `isaac/module/manifest.clj`.
- [x] Wire dispatch in `isaac/main.clj` to surface module-contributed commands.
- [x] Document the contract in ISAAC.md (or wherever module extension kinds are described).
- [x] Add a sample/test module that contributes a CLI command, with a spec.

## Acceptance criteria

- A module declaring `:cli {<name> {:factory ... :description ...}}` in its
  manifest can register a new `isaac <name>` subcommand.
- `isaac help` (or equivalent) lists module-contributed commands alongside
  built-ins.
- `bb spec` and `bb features` pass.

## Notes

Captured from a 2026-05-20 conversation about whether ACP should be a module.
Conclusion was probably-not (ACP looks more like a transport than a comm),
but the conversation surfaced this missing extension point regardless.

## Summary of Changes

- Added `:cli` as a new module extension kind:
  - `src/isaac/module/manifest.clj`: `:cli` added to `manifest-schema` and `known-extend-kinds`; factory presence validated
  - `src/isaac/module/loader.clj`: `register-cli-extension!` calls `cli/register-module-command!`; `:cli` added to `register-extensions!`; `clear-activations!` clears module-contributed CLI commands
  - `src/isaac/main.clj`: `register-module-cli-commands!` runs early discovery before dispatch, reading `isaac.edn` for `:modules` entries
- Added `src/isaac/cli.clj`: `register-module-command!` + `clear-module-commands!` for tracked module command lifecycle
- Added `modules/isaac.cli.greeter/`: sample module contributing the `greet` command
- Specs: `manifest_spec.clj` (2 tests), `loader_spec.clj` (1 test for `:cli` activation)
- Feature: `features/cli/module_cli.feature` (3 scenarios: dispatch, help listing, unknown-command error)



**Status:** unverified — awaiting review



## Verification failed

HEAD: 4cd2d0a7c43aba1ac2876dc7061e6482b6290ae9
Working tree: clean

1. Early module CLI discovery bypasses the normal config loader and reads `isaac.edn` with raw `edn/read-string` (`src/isaac/main.clj:30-39`). That means valid configs that rely on normal `${VAR}` substitution in `:modules` coordinates will not surface module CLI commands in help or dispatch. Discovery errors are swallowed, so this degrades silently to `Unknown command` instead of a config error.
2. `register-module-cli-commands!` never clears previously registered module commands before re-discovery (`src/isaac/main.clj:28-40`), even though `src/isaac/cli.clj` provides `clear-module-commands!`. In the same JVM, a module command registered by one `isaac.main/run` call remains available on later runs even after the config file or `:modules` entry is removed.

What is correct: `bb spec` and `bb features` are green in a clean clone, and the happy-path module CLI feature works for a literal local/root config.
