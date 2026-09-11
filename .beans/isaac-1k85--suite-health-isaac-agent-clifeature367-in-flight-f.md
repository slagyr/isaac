---
# isaac-1k85
title: 'Suite health (isaac-agent): cli.feature:367 in-flight false after cancel stamp (CI-only)'
status: draft
type: bug
priority: high
tags:
    - suite-health
created_at: 2026-09-10T22:55:38Z
updated_at: 2026-09-10T22:55:38Z
---

Ambient / CI-only flake on `isaac-agent` `features/session/cli.feature:367` after **isaac-jejt** landed (`ac1bf9b`). **Not a product miss of `sessions cancel`.** Do not reopen **isaac-jejt**.

## Observed (2026-09-10)

GitHub Actions agent CI Tests run 34535363284 on `ac1bf9b`: 808 examples, 1 failure.

- `features/session/cli.feature:367` — `And session "design-chat" in-flight status is true`; Expected **true** got **false**
- Prior steps in that scenario passed: exit 0, turn marker exists with `:cancelled true`
- Only the in-flight atom was already false

https://github.com/slagyr/isaac-agent/actions/runs/34535363284

Local (same SHA):
- `bb features features/session/cli.feature:358` → 1/0/4 green
- `bb features features/session/cli.feature` → 34/0/107 green

Does not reproduce isolated or on the full cli.feature file. CI-only under the 808-example suite (Linux). Hail 8a6bee4e (ci-failure); worker did not independent-repair.

## Why this is not jejt's lazy-impl killer failing

Scenario 5 (`cli.feature:358`) is the lazy-impl killer: stamp `:cancelled` AND still in-flight (did not wait). `sessions cancel` is fire-and-forget and must **not** call `bridge/cancel!`. On CI the live turn can observe `cancelled?` (atom **or** marker) and clear in-flight before the next And step while the marker is still on disk — the stamp succeeded; the "still in-flight" observation is a race against a cooperative unwind that jejt *wants*.

## This bean owns

Make `cli.feature:358` / `:367` suite-stable on published CI without weakening "stamp and return; did not wait."

Options the worker may choose among (do not weaken intent):
1. Assert in-flight **or** marker-still-present-with-cancelled, if the scenario's observable is "did not block until unwind."
2. Hold the live turn on a gate/rendezvous so in-flight cannot clear before the And (no `Thread/sleep` in the passing path).
3. Split "stamp written" from "still in-flight" into two scenarios if they are two contracts.

Do **not** make `sessions cancel` wait for unwind (that violates fire-and-forget). Do **not** call `bridge/cancel!` from CLI.

## Acceptance

    cd isaac-agent
    bb features features/session/cli.feature:358
    bb features features/session/cli.feature

0 failures, including on GitHub Actions `bb ci` (the 808-example suite). Record the CI run. Do not reopen jejt. Do not require hail `bb features` here.
