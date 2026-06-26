---
# isaac-jw6d
title: Refactor runtime deps to explicit values and remove thread-local system/fs
status: in-progress
type: epic
priority: high
created_at: 2026-05-21T15:54:56Z
updated_at: 2026-06-26T21:18:55Z
---

Problem

Isaac currently mixes explicit dependencies with ambient thread-local runtime state. `isaac.fs/*fs*` and `isaac.system/*system*` make runtime behavior depend on thread-local bindings, which breaks down once work crosses async or thread boundaries.

Intent

- Runtime dependencies are passed as values through the system and into new threads.
- `isaac.system` becomes only the process-edge holder for the current root runtime, not the primary access path inside application code.
- Dynamic `*system*` and dynamic `*fs*` stop being part of normal runtime behavior.
- Explicit filesystem operations become the norm: `fs/slurp-`, `fs/spit-`, etc. during migration, with ambient wrappers removed later.

Invariants

- No runtime code depends on thread-local `*system*`.
- No runtime code depends on thread-local `*fs*`.
- Crossing thread boundaries always carries runtime explicitly.
- Components prefer concrete deps over deep `system/get` lookups.

Exit Criteria

- The server/runtime path no longer relies on thread-local fs/system state.
- `isaac.system` is a non-dynamic process-wide holder used only at composition boundaries.
- Explicit dependency passing is the established pattern for new runtime code.
- Ambient fs wrappers are either gone or clearly deprecated and no longer used by runtime paths.

Implementation plan lands as small child beans, not one large rewrite.
