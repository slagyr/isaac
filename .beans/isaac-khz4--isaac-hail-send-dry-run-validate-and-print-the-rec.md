---
# isaac-khz4
title: 'isaac hail send --dry-run: validate and print the record without enqueueing'
status: in-progress
type: feature
priority: high
tags:
    - isaac-hail
created_at: 2026-08-25T22:45:37Z
updated_at: 2026-08-25T23:34:28Z
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
