---
# isaac-9mkp
title: 'isaac config set --force: write a value the schema rejects, printing the errors as warnings (required-field groups cannot be built one key at a time)'
status: completed
type: bug
priority: high
tags:
    - foundation
    - config
created_at: 2026-09-19T20:52:22Z
updated_at: 2026-09-20T01:48:59Z
---

Found 2026-09-19 on yopp, configuring google.oauth:

    isaac config set google.oauth.client-id 6094…apps.googleusercontent.com
    error: google.oauth.client-secret - is required [file: config/isaac.edn]

google.oauth's schema has two `:present?` fields (client-id, client-secret). Since isaac-fun8, `config set` refuses a write whose RESULT fails validation — correct in general, but here each field alone fails the other's `:present?`, so the map cannot be built with `config set` at all. The only way in is the stdin form (`echo '{…}' | isaac config set google.oauth -`) or hand-editing isaac.edn. Any schema with a required-field group has this problem.

mutate.clj already has a `skip-ref-validation?` switch (reference errors only: model-exists? etc.), not exposed on the CLI and not covering value validators.

## Do

1. `isaac config set <path> <value> --force`: write the value even when the resulting config fails validation. Print every validation error that remains, prefixed `warning:`, and a final line `wrote <path> to <file> with N validation error(s) outstanding — run: isaac config validate`. Exit 0. Without `--force`, behaviour is unchanged (refuse, exit 1).
2. Same flag on `config unset`.
3. The refusal message (no --force) ends with a hint: `(use --force to write anyway, or set the whole map: echo '{…}' | isaac config set <parent> -)`.
4. `--force` does NOT bypass parse errors or writes to unknown keys — only schema validation of the resulting config.
5. Help text for set/unset documents the flag and the stdin-map form.

## Scenarios (isaac-agent features/config/set_unset.feature, alongside fun8's; steps exist for run/exit/stdout/stderr/config file contents)

1. setting the first of two required fields is refused, and the refusal names the missing sibling and hints at --force and the stdin form
2. with --force the first field is written, the outstanding error is printed as a warning, exit 0, and the second set then validates clean with no warnings
3. --force still refuses a value that does not parse (e.g. a non-number into an :int)
4. config unset --force removes a required field and warns
5. the stdin map form sets both required fields in one write with no warning (already works; pin it)

## Acceptance

    cd isaac-foundation && bb ci
    cd isaac-agent && bb features features/config/set_unset.feature && bb ci

Version bump on foundation (CLI) — rides the foundation train.

## Handoff (2026-09-19)

Implemented by **scrapper**@isaac-work-2.

- isaac-foundation: `branch: bean/isaac-9mkp @ 04304e0 (base origin/main@ba7fa5b)`
- isaac-agent: `branch: bean/isaac-9mkp @ 4489d47 (base origin/main@fd89226)`

### What was built

- `isaac.config.mutate/set-config` and `unset-config` take `:force?`. With it,
  new validation errors no longer block the write; they are carried into
  `:warnings` and the mutation applies.
- `--force` never bypasses coercion errors (`can't coerce "x" to int`) — those
  still return `:invalid`, exit 1. Unknown keys were already refused earlier by
  `nav/path->spec`, before mutate is reached.
- CLI: `inspect/mutate-option-spec` adds `--force` to `config set` and
  `config unset` (also whitelisted in `common/structured-flag?` so it may
  trail positional args). On a forced write the CLI prints
  `wrote <path> to <file> with N validation error(s) outstanding — run: isaac config validate`
  and exits 0.
- The refusal without `--force` now ends with
  `(use --force to write anyway, or set the whole map: echo '{…}' | isaac config set <parent> -)`;
  the hint is suppressed when `--force` was already supplied.
- Help text for `set`/`unset` documents the flag and the stdin-map form.

### Verification

- `isaac-foundation`: `bb ci` green (1060 examples / 0 failures; 197 feature
  examples / 0 failures, 2 pre-existing pending).
- `isaac-agent`: `bb features features/config/set_unset.feature` green (21/0),
  `bb ci` green (831 examples / 0 failures, 1 pre-existing pending) — run with
  the foundation deps temporarily overridden to the local `bean/isaac-9mkp`
  worktree; **bb.edn/deps.edn were restored and are NOT part of the commit.**

### Notes for verify

- Foundation manifest bumped `0.1.27` → `0.1.28`.
- The agent branch's foundation pin is still `df89226`-era (`df64bf15`); the new
  scenarios only pass once the foundation lands and the pin is bumped — it
  rides the foundation train. **Land foundation first.**
- Environmental repair along the way: `~/.gitlibs/_repos/file/REL/fixture-agent`
  was a stale cache whose origin pointed at a deleted verify checkout, which
  failed two `cli/modules_pins.feature` scenarios in every foundation checkout.
  Removed it; it re-clones cleanly.

## Verified (2026-09-19)

Verified by **perceptor**@isaac-verify.

- isaac-foundation `bb ci` on bean branch: 1060 examples / 0 failures (spec),
  197 examples / 0 failures, 2 pre-existing pending (features).
- Per verify.md §6a the foundation squash landed first, then the agent
  `bean/isaac-9mkp` branch was repinned from foundation `df64bf15` to the
  landed main sha `294321de` (deps.edn + bb.edn, 18 occurrences) and re-gated:
  `bb features features/config/set_unset.feature` 21/0, `bb ci` 1652/0 (spec)
  and 831/0 with 1 pre-existing pending (features).
- Acceptance scenarios present in isaac-agent `features/config/set_unset.feature`
  (6 scenarios, no @wip), covering all five bean scenarios plus a --help check.
- Env repair (not code): `~/.gitlibs/_repos/file/REL/fixture-agent` was again a
  stale cache pointing at a deleted worker worktree, failing 2
  `cli/modules_pins.feature` scenarios; removed, re-cloned clean.

## Landed on main (2026-09-19)

main-sha: isaac-foundation 294321def2b201a455758fe476efbe1a736316e9
main-sha: isaac-agent 20660e633a0382c244397c7693d65929861b31d2
