---
# isaac-a9y0
title: Introduce isaac.charge namespace and refactor bridge ↔ drive boundary
status: todo
type: task
priority: normal
created_at: 2026-05-21T00:21:55Z
updated_at: 2026-05-21T00:21:55Z
parent: isaac-895
---

## Scope (child of isaac-895)

The foundational landing of the charge refactor. After this bean, the
`isaac.charge` namespace exists and the bridge ↔ drive boundary speaks
charge. Comm modules and CLI entry points are out of scope (children B
and C).

## Proposed change

### Create `isaac.charge`

- Schema (c3kit.apron.schema definition for a charge map).
- Constructor `charge/build` — given `{:input :session-key :channel :crew
  :origin}` and reading from the global config snapshot, resolves the
  crew into the full agent block (soul, model, provider, provider-cfg,
  tools, context-window) and returns a charge.
- Accessors: `charge/agent`, `charge/transcript`, `charge/channel`, etc.
- Predicates: `charge/slash?`, `charge/cancelled?`, `charge/unresolved?`.
- On lookup failure (unknown crew, unresolvable model), `charge/build`
  returns a charge marked `:unresolved` with a `:reason` — no exception.

### Refactor `bridge/core.clj`

- `dispatch!` takes a single charge argument.
- The current request-to-opts translation moves into `charge/build`.
- Bridge becomes a checkpoint/router:
  ```clojure
  (defn dispatch! [charge]
    (cond
      (charge/slash? charge)        (handle-slash charge)
      (charge/cancelled? charge)    nil
      (charge/unresolved? charge)   (reject charge)
      :else                         (drive/run-turn! charge)))
  ```

### Refactor `drive/turn.clj`

- `run-turn!` takes a single charge argument (replaces `session-key +
  input + opts`).
- Internal helpers consume the charge directly.

## Surface

- **New:** `src/isaac/charge.clj` (+ `spec/isaac/charge_spec.clj`)
- **Modified:** `src/isaac/bridge/core.clj` (+ its spec)
- **Modified:** `src/isaac/drive/turn.clj` (+ its spec)
- **Safety net:** existing bridge and drive features/specs continue to
  pass without modification — behavior preserved.

## Out of scope

- Comm modules building charges at inbound (child B).
- CLI entry points (prompt, chat) building charges (child C).

## Design decisions pinned

- `charge/build` returns a charge marked `:unresolved` on lookup
  failures, not an exception. Rationale: avoid exception flow for normal
  failure modes; bridge has a clean reject path.
- `charge/build` reads from the global config snapshot
  (`config/snapshot`). Rationale: consistent with how `bridge/dispatch!`
  reads config today.

Implementer may revisit either with reason.

## Acceptance

- `isaac.charge` exists with schema, constructor, accessors, predicates.
- `bridge/dispatch!` and `drive/run-turn!` each take exactly one
  argument (the charge).
- No opts maps with more than 2 keys pass between bridge and drive.
- All existing bridge and drive feature/spec runs pass unmodified.
