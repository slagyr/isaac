---
# isaac-3q8i
title: Comm modules build charges at inbound (no more request map)
status: todo
type: task
priority: normal
created_at: 2026-05-21T00:22:13Z
updated_at: 2026-05-21T00:22:13Z
parent: isaac-895
blocked_by:
    - isaac-a9y0
---

## Scope (child of isaac-895; depends on isaac-a9y0)

Once `isaac.charge` exists (isaac-a9y0), each comm should build a
charge at its inbound edge and call `(bridge/dispatch! charge)`
directly. This eliminates the "request" map from the comm surface.

## Proposed change

Per-comm refactor — each is independently shippable as its own commit
under this bean:

| Comm | Repo | File(s) |
|------|------|---------|
| ACP | isaac | `src/isaac/comm/acp/*.clj` (inbound handler) |
| HTTP server | isaac | `src/isaac/server/app.clj` (or wherever inbound HTTP turn requests originate) |
| Discord | isaac-discord | `src/isaac/comm/discord/*.clj` |
| iMessage | isaac-imessage | `src/isaac/comm/imessage/*.clj` |

Each comm:

```clojure
(defn on-inbound [event]
  (when-let [session-key (route event)]
    (-> (charge/build {:input       (extract-text event)
                       :session-key session-key
                       :channel     *this-comm*
                       :crew        (crew-for event)
                       :origin      (origin event)})
        (bridge/dispatch!))))
```

## Safety net

- `features/comm/acp/*` for ACP.
- `features/server/*` for HTTP.
- `isaac-discord/features/comm/discord/*` (separate repo).
- `isaac-imessage/features/comm/imessage/*` (separate repo).

All existing comm feature suites must pass without modification.

## Acceptance

- No comm constructs a "request" map; each inbound calls `charge/build`
  directly.
- `bridge/dispatch!` receives charges from every comm.
- The word "request" does not appear in `src/isaac/comm/`, in
  `isaac-discord/src/`, or in `isaac-imessage/src/`.
- All existing comm feature suites pass.

## Cross-repo note

Two of the four comms live in sibling repos (`isaac-discord`,
`isaac-imessage`). Worker should commit each repo's change separately
and push the isaac core change first (since the sibling repos pin a
specific isaac git sha).
