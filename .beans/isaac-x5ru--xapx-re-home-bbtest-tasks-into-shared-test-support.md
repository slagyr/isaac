---
# isaac-x5ru
title: 'xapx: Re-home bb.test-tasks into shared test-support (native runner)'
status: in-progress
type: task
priority: high
created_at: 2026-07-19T17:10:28Z
updated_at: 2026-07-19T17:17:08Z
parent: isaac-xapx
---

Parent: isaac-xapx. **This blocks every other xapx child** — nothing else can `:require` the native runner until it lands.

## Goal
Re-home foundation's `bb.test-tasks` runner into a shared, dependable location so every module repo can `:require ([bb.test-tasks :as tests])` and call `run-spec!` / `run-features!` / `run-ci!` / `run-jvm-spec!` / `run-jvm-features!`. ONE implementation, N consumers — never copy-pasted per repo.

## Why
`bb.test-tasks` lives at `isaac-foundation/bb/test_tasks.clj`, on foundation's own bb classpath only (foundation `:paths` includes `"."`). Modules depend on isaac-foundation via git but do NOT get `bb/test_tasks.clj`, so they cannot require it today.

## Do
- Pick the home: expose it via foundation's test-support (`spec-support` `deps/root`, already pulled by every module as `isaac-foundation-test-support`), or a new dedicated shared `deps/root`. Decide and document.
- Ensure the file resolves as namespace `bb.test-tasks` from a CONSUMER repo's bb classpath (paths/deps wiring).
- Keep `test_timeout.clj` / any helper it needs alongside.

## Acceptance
- [ ] A consumer module's `bb.edn` can `:requires ([bb.test-tasks :as tests])` and run `bb spec` natively — proven in ONE lightweight pilot consumer (**isaac-hail**, chosen for a small suite and no wrinkles) before the sweep. The full isaac-agent parity conversion is the separate blocked child isaac-h5xm.
- [ ] No copy of `test_tasks.clj` added to any consumer repo.
- [ ] Home + wiring documented in this bean for the other children to follow.
