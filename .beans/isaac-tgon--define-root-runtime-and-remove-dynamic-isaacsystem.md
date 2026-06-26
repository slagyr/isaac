---
# isaac-tgon
title: Define root runtime and remove dynamic isaac.system binding
status: completed
type: task
priority: high
created_at: 2026-05-21T15:55:07Z
updated_at: 2026-06-26T21:18:55Z
parent: isaac-jw6d
---

Problem

Before filesystem and other dependency migration can proceed cleanly, Isaac needs one clear runtime model. Today `isaac.system` mixes a process-wide singleton idea with thread-local binding semantics via `*system*`.

Scope

- Define the root runtime shape and document the reserved top-level runtime keys.
- Replace dynamic `*system*` access with a non-dynamic process-wide holder API.
- Keep `isaac.system` as a composition-boundary depot only; do not spread new deep `system/get` usage.
- Update top-level lifecycle/entrypoint code to install and access the root runtime through the new API.

Out of Scope

- Broad replacement of `fs/slurp` call sites.
- Full ambient dependency removal across the whole codebase.
- Renaming `fs/*-` APIs.

Acceptance

- `isaac.system` no longer relies on thread-local dynamic binding for runtime access.
- There is a clear API for installing and reading the current root runtime.
- Existing startup/lifecycle code still works with the new runtime holder.
- Specs cover the new `isaac.system` behavior, especially cross-thread visibility expectations.
- `bb spec` and `bb features` are green.

Notes

This is the foundation slice for the larger runtime-explicitness epic. Follow-on beans will thread runtime values through subsystems and then migrate ambient fs usage.

## Verification notes

- work-2 audit (2026-06-26): `*system*` eliminated codebase-wide; runtime holder is `isaac.nexus` (`defonce` atom: `install!`, `get`, `bound-runtime-fn`, `-with-nexus`).
- `isaac.system` (ACP facade) delegates to nexus — no dynamic binding.
- Cross-thread acceptance: `bb spec spec/isaac/nexus_spec.clj` → 25 examples, 0 failures (`-with-nexus` thread visibility + `bound-runtime-fn`).
- Production entrypoints install runtime via `nexus/init!` (e.g. `isaac-server` boot). hail `delivery_worker` uses `bound-runtime-fn` for deferred work.
- Completed: tgon acceptance met; no further code changes required for this slice.
