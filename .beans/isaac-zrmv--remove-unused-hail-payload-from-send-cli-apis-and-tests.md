---
# isaac-zrmv
title: Remove unused :payload from hail send (CLI, HTTP, tool) and tests — dead code
status: in-progress
type: task
priority: normal
created_at: 2026-06-29T18:00:00Z
updated_at: 2026-07-02T00:57:30Z
---

## Summary
The `:payload` (and `--payload` CLI flag) support for hails is dead code. It is written when sending hails (via CLI, HTTP, hail-send tool) but **never read or used** in any delivery, rendering, origin context, turn, or skill execution path. Band hails use `:params` for template data; direct hails use explicit `--prompt`. Remove the unused support everywhere.

## Problem
- CLI still documents and accepts `--payload EDN`
- HTTP hail endpoint still accepts and stores `:payload`
- `hail-send` tool still accepts "payload" arg and puts it in the record
- Dozens of specs, features, and tests either use `--payload` in examples or assert `(:payload ...)` on stored records
- Help text and tool schemas continue to describe it
- But:
  - `prepare/render-band-prompt` only uses `:params`
  - `delivery_worker/hail-origin` only forwards `:params` and `:prompt`
  - No code in turns, charge, prompt builders, or skills ever inspects `:payload` from a hail
  - Persisted hails may contain it, but it has no effect

This creates confusion (see recent `--payload` vs `--params` questions) and unnecessary code.

## Root cause sketch
`:payload` was the original general data bag. When band-templated hails were introduced, `:params` was added for template bindings, but the old `:payload` path was never removed or wired into the execution flow.

## Acceptance criteria
- Remove `--payload` option, parsing, and handling from `isaac-hail/src/isaac/hail/cli.clj` (including `send-help` and `build-hail`)
- Remove `:payload` handling from `isaac-hail/src/isaac/hail/http.clj` (`build-record`)
- Remove "payload" support from `isaac-hail/src/isaac/tool/hail.clj` (arg handling, tool schema, docstring)
- Update / remove references in all tests and features:
  - `isaac-hail/spec/isaac/hail/cli_spec.clj`
  - `isaac-hail/spec/isaac/hail/http_spec.clj`
  - `isaac-hail/spec/isaac/hail/queue_spec.clj`
  - `isaac-hail/spec/isaac/tool/hail_spec.clj`
  - `isaac-hail/features/send.feature`
  - `isaac-hail/features/send-addressing.feature`
  - `isaac-hail/features/http.feature`
  - `isaac-hail/features/crew-tool.feature`
  - `isaac-hail/features/hail-naming.feature`
  - `isaac-hail/features/delivery.feature` (the "payload" table column in hail setup — use params or remove if irrelevant)
  - `isaac-hail/features/router.feature` (payload in tables)
  - Any other files that mention hail payload in a non-generic sense
- Update the outdated usage string in CLI help (it still leads with `--payload`)
- Verify that band hails (`--band` + `--params`) and direct hails (`--prompt`) continue to work exactly as before
- `bb features isaac-hail` (or equivalent) passes after changes
- No behavior change for users of the documented `--params` / band paths
- (If needed) Note that old persisted hails containing `:payload` are harmless and can be left as-is

## Scenarios / test guidance
- Band-templated sends should continue using `--params` (data goes into the rendered prompt via the band .md template)
- Direct/non-band sends use explicit `--prompt`
- Remove or convert tests that only existed to prove `--payload` round-tripped

## Notes
- Hails sent via bands **are** templated prompts: the body of the `config/hail/*.md` after frontmatter is the template; `--params` supplies the bindings (plus the band's own `data` map).
- `:payload` was never part of the origin/context passed to turns or the hail-bean-* skills.
- After removal, the only ways to attach data to a hail are `--params` (for bands) or including it inside a full hail via stdin (`--from-json`).
- This cleanup is safe because the field provided no observable behavior.
