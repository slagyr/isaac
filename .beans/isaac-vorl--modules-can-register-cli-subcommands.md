---
# isaac-vorl
title: Modules can register CLI subcommands
status: todo
type: feature
priority: normal
created_at: 2026-05-20T20:12:56Z
updated_at: 2026-05-20T20:47:35Z
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

- [ ] Decide bootstrap approach (early discovery vs deferred dispatch).
- [ ] Add `:cli` to manifest schema + `known-extend-kinds` in `isaac/module/manifest.clj`.
- [ ] Wire dispatch in `isaac/main.clj` to surface module-contributed commands.
- [ ] Document the contract in ISAAC.md (or wherever module extension kinds are described).
- [ ] Add a sample/test module that contributes a CLI command, with a spec.

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
