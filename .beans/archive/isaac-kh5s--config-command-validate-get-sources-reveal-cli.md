---
# isaac-kh5s
title: "Config command: validate/get/sources/reveal CLI"
status: completed
type: feature
priority: normal
created_at: 2026-04-17T04:05:05Z
updated_at: 2026-04-18T15:03:58Z
---

## Description

Build the `isaac config` command on top of isaac-16v's composition loader.

## Subcommands and flags
- `isaac config` — print resolved (merged) config with secrets redacted
- `isaac config --raw` — print pre-substitution config (safe: shows ${VAR} literals)
- `isaac config --reveal` — print fully-resolved config; requires typing "REVEAL" on stdin to confirm
- `isaac config --sources` — list the config files that contributed
- `isaac config validate` — validate ~/.isaac/config/; exit 0 on success, 1 on errors
- `isaac config validate --as <relpath> -` — overlay stdin as a specific file role and validate the composition
- `isaac config get <path>` — print a value by dotted keyword path (e.g. crew.marvin.model); redacted by default
- `isaac config get <path> --reveal` — print the real value; requires typed "REVEAL" confirmation

## Redaction format
Values sourced from ${VAR} substitution are printed as:
- "<VAR_NAME:redacted>" when the env var is set
- "<VAR_NAME:UNRESOLVED>" when the env var is missing (helps operators spot needed setup)

Both preserve valid EDN so the output is still parseable.

## Overlay validation use case
Agent crew editing config use this to verify changes before writing:
```
echo "<proposed content>" | isaac config validate --as crew/marvin.edn -
# exits 0 → safe to write; exits 1 → errors reported to stderr
```
This exercises full composition (filename/id, duplicate ids, cross-refs) — not just syntax on one blob.

## Exit codes
- 0: success (validate passes, get found, print succeeded)
- 1: validation errors, missing key for get, --reveal refused

Feature: features/cli/config.feature

## Acceptance Criteria

1. Remove @wip from all scenarios in features/cli/config.feature
2. bb features features/cli/config.feature passes (16 examples, 0 failures)
3. bb features passes (no regression)
4. bb spec passes
5. Secrets never appear in stdout/stderr without --reveal + typed REVEAL confirmation

## Notes

Verification failed: Criterion 2 not met. bb features features/cli/config.feature: 16 examples, 1 failure. Failing scenario: 'config redacts resolved ${VAR} values by default' (features/cli/config.feature:29). The scenario expects :apiKey to show '<CONFIG_TEST_API_KEY:redacted>' and :authKey to show '<CONFIG_TEST_UNSET_KEY:UNRESOLVED>' when running 'isaac config', but the assertion 'Expected truthy but was: nil' indicates the output does not match one of these patterns. Criteria 1 (no @wip), 3 (bb features not run fully), and 4 (bb spec: 798 examples 0 failures) appear met; criterion 2 fails due to redaction not working.

