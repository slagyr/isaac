---
# isaac-7v5h
title: 'Hail router: resolve pending hails into per-session deliveries'
status: todo
type: feature
priority: normal
created_at: 2026-05-23T21:55:37Z
updated_at: 2026-05-24T03:16:08Z
parent: isaac-ugx7
blocked_by:
    - isaac-i4ly
    - isaac-wr7d
---

## Motivation

The substrate (isaac-vduq) writes pending hails to
`<state-dir>/hail/pending/`. Without delivery they sit there
forever. This bean adds the **hail router** — the consumer that
ticks on the shared scheduler, resolves each pending hail's
`:frequency` into a set of (crew, session) listeners, applies the
reach mode, and writes delivery records to per-session inboxes.
After all matching listeners have been delivered, the pending file
moves to `hail/delivered/<id>.edn` with resolution metadata for
forensic visibility ("which crews and sessions actually got it").

## Scope

### Worker loop

A `Reconfigurable` module registering a scheduler task on startup,
ticking ~1s. Same shape as `cron/service.clj` and
`comm/delivery/worker.clj`.

On each tick:
1. Enumerate `<state-dir>/hail/pending/*.edn`
2. For each hail, resolve and deliver
3. After all matching listeners receive their delivery, move
   pending → `hail/delivered/<id>.edn` with resolution metadata

### Address resolution

The hail's `:frequency` can carry any combination of:

- `:band <name>` — look up declaration via `isaac.hail.bands`
- `:crew <id-or-list>` — explicit recipients
- `:session <id-or-list>` — explicit recipients
- `:crew-tags #{...}` — tag-filter on crew
- `:session-tags #{...}` — tag-filter on session

Band lookup yields the band's own filter rules; these merge with
any hail-side fields (intersection — band rules constrain, hail
fields constrain further). The resolved filter is then:
`{:crew [...] :crew-tags #{...} :session [...] :session-tags #{...}
:reach :one|:all}`.

### Listener matching

Given the resolved filter:

1. Find candidate crews: in `:crew` OR satisfying `:crew-tags`.
2. For each candidate crew, find candidate sessions: in `:session`
   OR satisfying `:session-tags`, AND owned by the candidate crew.
3. Apply `:reach`:
   - `:one` — pick one (crew, session) pair (idle-first via
     `a1nu`'s `in-flight?`, capacity-gated via `can-dispatch?`)
   - `:all` — deliver to every matching pair.

### Delivery write

Each delivery is a file at
`<state-dir>/hail/sessions/<session-id>/inbox/<delivery-id>.edn`:

```clojure
{:id           "delivery-1"
 :hail-id      "hail-7"
 :frequency    {:band "bean-pickup"}   ;; original address from hail
 :payload      {:n 1}
 :prompt       "..."                    ;; resolved from band's .md
                                         ;;   OR hail's :prompt for direct
 :from         :cli
 :delivered-at "2026-05-23T14:00:00Z"}
```

Atomic write (tempfile + rename). The wake worker (slice 4) picks
this up later.

### Delivered records (forensic)

After ALL matching listeners have been delivered, the pending file
moves to `<state-dir>/hail/delivered/<hail-id>.edn`. The delivered
record preserves the original hail and adds resolution metadata:

```clojure
{:id           "hail-7"
 :frequency    {:band "engineering-intercom"}    ;; original
 :payload      {:dilithium-leak true}             ;; original
 :prompt       "..."                              ;; original or resolved
 :from         :cli                               ;; original
 :sent-at      "..."                              ;; original
 :delivered-at "2026-05-23T14:00:00Z"             ;; new
 :listeners    [{:crew :bartholomew               ;; new — who got it
                 :session :engine-room
                 :delivery-id "delivery-1"}]}
```

Operators can answer "where did hail-7 go?" by reading
`hail/delivered/hail-7.edn` rather than grepping every session
inbox. The pending file is removed after the move.

### Pending lifecycle

A hail's pending record stays in `pending/` until **all matching
listeners have been delivered**, at which point it moves to
`delivered/`. If no listeners match (empty resolution, unknown
band, etc.), the hail stays in pending for the next tick —
self-healing when crews/sessions/bands are added later.

### Address resolution edge cases

- Band name not in registry → log warning, hail stays in pending.
- Empty filter (no matching listeners) → hail stays in pending.
- No `:reach` specified (direct addressing) → default `:one` with
  idle-first selection.
- Hail addressed to a session that doesn't exist → log, stay in
  pending.

## Out of scope (deferred)

- **Hail TTL** — drop a hail after N seconds of no listeners.
- **Dead-letter dir** — explicit move of permanently undeliverable
  hails for audit.
- **Cross-install hails** — `band@host` form, follow-up.
- **Per-band session-selection policy overrides** beyond idle-first.

## Acceptance

- Worker registered on the shared scheduler with ~1s tick.
- Reads pending hails; writes delivery records; moves pending →
  `delivered/` when all matching listeners delivered.
- Resolves all `:frequency` forms: band, crew, session, crew-tags,
  session-tags, and combinations (intersection).
- Applies `:reach :one` with idle-first + capacity gate;
  `:reach :all` to all matching pairs.
- Empty resolution (no listeners) leaves the hail in pending.
- Delivery records contain: hail-id, frequency, payload, prompt
  (resolved per addressing form), from, delivered-at.
- Delivered records include `:listeners` resolution metadata.

## Feature files

- `features/hail/router.feature` — 8 `@wip` scenarios:
  - Frequency-band hail → delivery to matching session.
  - Direct `:crew` hail → delivery to crew's session.
  - Direct `:session` hail → that exact session only (not other
    sessions of the same crew).
  - `:reach :one` tag-filter picks idle over in-flight.
  - `:reach :all` delivers to every matching session.
  - Unknown band → hail stays in pending.
  - Empty resolution (no matching crews) → hail stays in pending.
  - Combined band + session-tag form an intersection.

The file uses Marigold cast (bartholomew, hieronymus, mavis,
atticus, cordelia) for entertaining scenario themes.

**New step introduced**: `When the hail router ticks` — runs one
iteration of the router; parallel to `the delivery worker ticks`
in `comm/delivery/queue.feature`.

Run targeted: `bb features features/hail/router.feature`.

**Definition of done:** remove `@wip` from
`features/hail/router.feature` and
`bb features features/hail/router.feature` is green.

## Relationship to other beans

- **Parent: isaac-ugx7 (Hail epic).**
- **Blocked by isaac-i4ly (bands registry)** for band lookup.
- **Blocked by isaac-wr7d (tagging)** for tag-based filtering
  against actual crews and sessions.
- **Companion to isaac-a1nu (concurrency).** Uses `in-flight?` and
  `can-dispatch?` for session selection. a1nu is verified.
- **Companion to isaac-vduq (substrate).** Consumes the pending
  records vduq writes.
- **Unblocks wake-integration slice (slice 4).** Wake consumes the
  delivery records this bean writes.
