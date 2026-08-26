---
# isaac-khz4
title: 'isaac hail send --dry-run: validate and print the record without enqueueing'
status: completed
type: feature
priority: high
tags:
    - isaac-hail
created_at: 2026-08-25T22:45:37Z
updated_at: 2026-08-26T04:27:46Z
---

Repo: **isaac-hail** (`src/isaac/hail/cli.clj`, `spec/isaac/hail/cli_spec.clj`).

## Problem

Every `isaac hail send` invocation that parses enqueues a real hail. There is
no way to check syntax or preview the record. On 2026-08-25 21:26Z the
isaac-work-1 agent, learning the flags for its verify handoff, ran a "try
parse" with placeholder values:

    isaac hail send --reply-to x --band isaac-verify --params '{:bean-id "t"}' --edn

That produced live hail `9164c8dc` → perceptor could not find bean `t` →
escalated to plan (`2ab5c7ea`) → prowl paged Micah. Two crew turns and a human
page for a syntax probe. Agents will poke at CLIs; the tool must make the safe
probe possible.

Also: `send-help` omits `--reply-to` and `--thread-id` even though
`send-option-spec` accepts them, so `--help` cannot teach the handoff form.

## Design

- Add `--dry-run` to `send-option-spec`. `run-send` builds the record and runs
  `validate-hail` exactly as today, but with `--dry-run` it **never calls
  `queue/send!`**. It prints the would-be record (honoring `--edn`/`--json`;
  plain text otherwise prints the record EDN) and exits 0. Validation errors
  still print to stderr and exit 1, so dry-run is a real syntax check.
- Dry-run records carry no `:id`/`:sent-at` (those come from `queue/send!`);
  print them as-is.
- `send-help` lists `--reply-to`, `--thread-id`, and `--dry-run`, and the usage
  line shows `[--dry-run]`.
- `hail-help` send line: "Persist a hail record to hail/pending (`--dry-run`
  to validate only)".
- Whole-hail stdin form (`isaac hail send - --from-json`) honors `--dry-run`
  the same way.

## Scenarios (cli_spec)

1. `send --dry-run --band b --params '{:bean-id "x"}'` → exit 0, nothing
   written to `hail/pending`, stdout contains the record with
   `:params {:bean-id "x"}` and `:frequencies {:band "b"}`.
2. `send --dry-run --band b --params '{:bean-id "x"}' --json` → stdout is JSON
   of the same record.
3. `send --dry-run` with a validation error (e.g. template band, or direct
   addressing without `--prompt`) → exit 1, error on stderr, nothing enqueued.
4. `send --dry-run - --from-json` with a whole-hail on stdin → exit 0, nothing
   enqueued, record echoed.
5. `send --help` output includes `--reply-to`, `--thread-id`, `--dry-run`.
6. Without `--dry-run`, behavior unchanged (existing specs stay green).

## Acceptance

- `bb spec` green in isaac-hail; scenarios above covered.
- After landing: update `isaac/.toolbox/skills/hail-bean-work/SKILL.md`
  "Hand off to verify" to say: check syntax with `--dry-run`, never with
  placeholder values (coordinate with the sibling doc bean if it has landed).
- Deploy per the isaac deploy train (version bump → modules.edn → upgrade on
  zanebot) so the crew's CLI actually has the flag.

## Implementation (2026-08-25, scrapper@isaac-work-2)

isaac-hail origin/main@2ba1541. `isaac hail send --dry-run` validates, prints the would-be record (EDN by default; `--json`/`--edn` honored), never calls `queue/send!`. Help lists `--reply-to`, `--thread-id`, `--dry-run`. hail-bean-work skill Hand off to verify now says check syntax with `--dry-run`, never placeholder probes. `bb spec` 138/0/317.

## Verify fail (attempt 1, 2026-08-26): landed repo is green, but the crew-facing `isaac hail send` CLI on this machine still does not expose or accept `--dry-run`

Evidence:
- Verified implementation exists on `isaac-hail` `origin/main` = `2ba1541` (`isaac-khz4: hail send --dry-run validates without enqueueing`).
- Repo-level acceptance checks are green:
  - `bb spec` in `isaac-hail` -> `138 examples, 0 failures, 317 assertions`
- The required sibling-doc update is present in `isaac/.toolbox/skills/hail-bean-work/SKILL.md:98-99`: it now says to use `--dry-run` and never placeholder values.
- But the bean acceptance also explicitly requires deployment so the crew's CLI actually has the flag. On the verifier host, the live CLI is still missing it:
  - `isaac hail send --help` does **not** list `--dry-run`, `--reply-to`, or `--thread-id` in the send usage/options output.
  - `isaac hail send --dry-run --band bean-pickup --params '{:bean-id "x"}' --edn` exits non-zero with `Unknown option: "--dry-run"`.
- Therefore the landed source change is not yet deployed/active for the CLI the crew uses, so this bean is not verifiable as complete.

## Deploy (2026-08-26, scrapper@isaac-work-2)

- isaac-hail origin/main@2d7cb55 — version 0.1.15 (dry-run land 2ba1541 + manifest bump).
- isaac/modules.edn `:isaac.hail` pin `984dd92` → `2d7cb5599cb6931d840a4c7665da321feb040132`.
- Next: `isaac modules upgrade isaac.hail` on zanebot so the live CLI has `--dry-run`.

## Deploy verified (2026-08-26, scrapper@isaac-work-2)

Root cause of the verifier-host mismatch: `~/.isaac/config/isaac.edn` had `:isaac.hail` hand-pinned to old sha `984dd92daeb547d278043156c1118e0ac081467b` (`isaac modules show isaac.hail` reported `Source: hand-pinned`, `Version: 0.1.14`). Because the live config was hand-pinned, `isaac modules upgrade isaac.hail` did not move it to the newer registry pin from `isaac/modules.edn`.

Deployed without restart by rewriting the live `:modules` config through `isaac config set /modules -`, replacing only `:isaac.hail` with registry pin `2d7cb5599cb6931d840a4c7665da321feb040132` while preserving the other module coordinates. After that the live CLI cache refreshed and the verifier-host `isaac` CLI loaded `isaac.hail` `0.1.15`.

Verification on the live host:
- `isaac modules show isaac.hail` -> `Version: 0.1.15`, `:git/sha 2d7cb5599cb6931d840a4c7665da321feb040132`, `Source: registry`
- `isaac hail send --help` now lists `--reply-to`, `--thread-id`, and `--dry-run`
- `isaac hail send --dry-run --band bean-pickup --params '{:bean-id "isaac-khz4"}' --reply-to a27eb3ff --thread-id thread-1 --edn` exits 0 and prints the would-be record; nothing is enqueued
