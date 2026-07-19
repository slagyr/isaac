---
# isaac-fq28
title: 'xapx: verify cli-proxy/cli-server already native'
status: todo
type: task
priority: low
created_at: 2026-07-19T17:10:52Z
updated_at: 2026-07-19T17:10:52Z
parent: isaac-xapx
---

Parent: isaac-xapx. (Independent — these already run native; not blocked by the re-home, but should end on the shared runner too.)

## Goal
Confirm **isaac-cli-proxy** and **isaac-cli-server** already run specs/features natively (their bb.edn comment: "Babashka runs specs/features directly"), and align them onto the shared `bb.test-tasks` runner if trivial.

## Do
- Parity spot-check: `bb spec` / `bb features` / `bb ci` run native and green.
- If they hand-roll their own native invocation, switch to `tests/run-*` for consistency (optional if it adds no value — note the call either way).

## Acceptance
- [ ] Both confirmed native + green (no `clojure -M` shell-out).
- [ ] Either adopted the shared runner or documented why not.
