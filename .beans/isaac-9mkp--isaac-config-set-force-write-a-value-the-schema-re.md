---
# isaac-9mkp
title: 'isaac config set --force: write a value the schema rejects, printing the errors as warnings (required-field groups cannot be built one key at a time)'
status: in-progress
type: bug
priority: high
tags:
    - foundation
    - config
created_at: 2026-09-19T20:52:22Z
updated_at: 2026-09-19T20:53:10Z
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
