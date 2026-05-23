---
# isaac-7v5h
title: 'Hail fan-out worker: resolve pending hails into per-session deliveries'
status: todo
type: feature
created_at: 2026-05-23T21:55:37Z
updated_at: 2026-05-23T21:55:37Z
parent: isaac-ugx7
blocked_by:
    - isaac-i4ly
    - isaac-wr7d
---

## Motivation

The substrate (isaac-vduq) writes pending hails to
`<state-dir>/hail/pending/`. Without delivery they sit there
forever. This bean adds the **fan-out worker** — the consumer that
ticks on the shared scheduler, resolves each pending hail's address
into a set of (crew, session) listeners, applies the reach mode,
and writes delivery records to per-session inboxes. Pending hails
are removed once all matching listeners are delivered.

## Scope

### Worker loop

A `Reconfigurable` module registering a scheduler task on startup,
ticking ~1s. Same shape as `cron/service.clj` and
`comm/delivery/worker.clj`.

On each tick:
1. Enumerate `<state-dir>/hail/pending/*.edn`
2. For each hail not yet processed, resolve and deliver
3. Remove pending file when all matching listeners delivered

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

### Pending lifecycle

A hail's pending record is removed once **all matching listeners
have been delivered**. If no listeners match (empty resolution),
the hail stays in pending for the next tick — self-healing if
crews/sessions/bands are added later.

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
- Reads pending hails; writes delivery records; removes pending
  when all matching delivered.
- Resolves all address forms: band, crew, session, crew-tags,
  session-tags, and combinations (intersection).
- Applies `:reach :one` with idle-first + capacity gate;
  `:reach :all` to all matching pairs.
- Empty resolution (no listeners) leaves the hail in pending.
- Delivery records contain: hail-id, frequency, payload, prompt
  (resolved per addressing form), from, delivered-at.

## Feature scenarios

`features/hail/fanout.feature`, `@wip`. Scenarios to draft in a
later round:

- Band-addressed pending hail → delivery to matching session.
- Direct crew-addressed hail → delivery to crew's idle session.
- Direct session-addressed hail → delivery to that session.
- Tag-filter address → delivery to first matching (crew, session).
- `:reach :all` → deliveries to every matching pair.
- Unknown band → hail stays in pending.
- Empty resolution → hail stays in pending.
- Combined fields (band + session-tag) → intersection delivers.

Mirror style from `features/comm/delivery/queue.feature`.

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
