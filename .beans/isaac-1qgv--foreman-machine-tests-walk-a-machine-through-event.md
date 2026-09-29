---
# isaac-1qgv
title: 'Foreman machine tests: walk a machine through events without running its actions'
status: draft
type: feature
priority: normal
created_at: 2026-09-29T13:59:46Z
updated_at: 2026-09-29T13:59:46Z
parent: isaac-q3u3
---

Likely repo: **isaac-foreman**. Idea: Micah + planner, 2026-09-29 — "Foreman should help with this because we can test state machines." Orchestration has no test automation; machine config should still ship with repeatable tests.

## Contract to plan

- `isaac foreman test <file>` reads an EDN file of cases for a machine. Each case: a machine name, a starting state (default `:initial`), a sequence of events (optionally with data), the states it must pass through, and the actions each transition must **record**.
- Actions never execute under test: `:turn` submits nothing, `:log` prints nothing; the case asserts what *would* have been done (action name, type, and for `:turn` the filled prompt and target).
- Runs against the live config's machines (the same loader `config validate` uses), in a throwaway store — no instance files touched.
- Output: one line per case, PASS/FAIL, with the first mismatch (expected vs actual state or action); exit 0 only if all pass.
- Unhandled events are reported like the engine does (recorded, not fatal) — a case can expect them.

## Use

The orchestration repo gains a `bb test-machines` task that runs `isaac foreman test` over its machine test files. isaac-q6fj's bean-work machine ships with such tests.

## Scenario plan (to draft)

1. A passing case walks a machine and reports PASS; nothing executes and no instance is created.
2. A wrong expected state reports FAIL with expected vs actual and exits 1.
3. A `:turn` action is recorded with its filled prompt and target, not submitted.
4. An unhandled event can be expected; an unexpected one fails the case.

Draft until scenarios exist.
