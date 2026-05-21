---
# isaac-895
title: Introduce charge for threading turn state
status: draft
type: task
priority: deferred
tags:
    - deferred
created_at: 2026-04-13T02:59:23Z
updated_at: 2026-05-21T00:10:46Z
---

## Gap

Many functions pass the same bag of options through multiple layers:
state-dir, session-key, model, provider, provider-cfg, context-window,
soul, channel. `bridge/dispatch!` already destructures 13 fields from
its inbound and builds an 11-key opts map to pass forward to
`drive/run-turn!`. Each comm reinvents its own slice of this
enrichment. The pattern is unwieldy and inconsistent.

## Proposed change

Introduce **`charge`** — a sealed, named map that is the universal
currency between comms, the bridge, and the drive. A charge contains
everything the drive needs to execute one turn.

### Vocabulary

| Term     | Part of speech | Meaning |
|----------|----------------|---------|
| `charge` | Noun           | A sealed, immutable bundle of turn state. The cartridge. |
| `turn`   | Event          | The drive's execution that consumes a charge and produces a result. The shot. |

A charge holds references where it can (e.g., `session-key` instead of
the full persisted transcript); the drive resolves references as it
runs. The previous "request" term is retired — comms build charges
directly.

### Architecture

```
comm ─charge→ bridge ─charge→ drive
              │
              ├─ slash command? handle locally
              ├─ unresolved (unknown crew, no model)? reject
              ├─ cancelled? skip
              └─ else: forward to drive
```

- **Comms build charges directly** via a shared `charge/build`
  constructor. No intermediate "request" term — the comm produces a
  charge from its inbound shape (input + channel + crew assignment +
  origin) and `charge/build` resolves the crew into the full agent
  block (soul, model, provider, provider-cfg, tools, context-window).
- **Bridge becomes a checkpoint/router** over charges. It inspects the
  charge for slash-command, cancellation, or unresolved state and
  either handles locally or forwards to the drive.
- **Drive consumes the charge** as its single argument:
  `(drive/run-turn! charge)`.

### Illustrative shape

```clojure
;; In each comm adapter:
(defn on-inbound [event]
  (when-let [session-key (route event)]
    (-> (charge/build {:input       (extract-text event)
                       :session-key session-key
                       :channel     *this-comm*
                       :crew        (crew-for event)
                       :origin      (origin event)})
        (bridge/dispatch!))))

;; In the bridge:
(defn dispatch! [charge]
  (cond
    (charge/slash? charge)        (handle-slash charge)
    (charge/cancelled? charge)    nil
    (charge/unresolved? charge)   (reject charge)
    :else                         (drive/run-turn! charge)))
```

## Benefits

- Functions take one arg instead of destructured opts bags.
- Easy to add new fields without changing every signature in the chain.
- Natural place for middleware-like transformations (enrichments,
  observability, cancellation).
- Testable — just build a charge map.
- "Request" disappears from the vocabulary; only "charge" and "turn"
  remain.

## Open design questions

1. **Error shape from `charge/build`.** When a crew lookup fails or a
   model can't be resolved, does `charge/build` throw (`ex-info`) or
   return a charge marked `:unresolved` for the bridge to reject
   cleanly?
2. **Config access from `charge/build`.** Reach into the global config
   snapshot (consistent with how the bridge does it today), or accept
   the relevant config as a constructor argument (purer but more
   verbose at each comm)?

## Migration plan — TBD

Refactoring across all paths is too big as one landing. Likely
subdivision into child beans (sequencing to be decided when this bean
leaves draft):

1. Introduce `isaac.charge` namespace; refactor `bridge ↔ drive`
   boundary only. Existing tests are the safety net.
2. Comms build charges at the inbound edge (one landing per comm).
3. `chat_cli` and `prompt_cli` refactor.

## Acceptance criteria

- `isaac.charge` namespace exists with a documented schema, constructor,
  and accessors.
- Every public function in `isaac.bridge` and `isaac.drive` takes a
  single `charge` argument (or returns one).
- No opts maps with more than 2 keys are passed between bridge and
  drive.
- The word "request" is gone from `bridge/` and `drive/` namespaces.
