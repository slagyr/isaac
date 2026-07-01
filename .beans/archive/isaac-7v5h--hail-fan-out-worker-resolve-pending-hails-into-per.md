---
# isaac-7v5h
title: 'Hail router: resolve pending hails into delivery files'
status: completed
type: feature
priority: normal
created_at: 2026-05-23T21:55:37Z
updated_at: 2026-05-25T15:48:07Z
parent: isaac-ugx7
blocked_by:
    - isaac-i4ly
    - isaac-wr7d
---

## Motivation

Slice 3 of the Hail epic, **step 1 of a two-step delivery pipeline**. The
substrate (isaac-vduq) writes raw hails to `<state-dir>/hail/pending/`. The
router ticks on the shared scheduler, resolves each pending hail's
`:frequency` into delivery obligations, and writes one delivery file per
obligation to `<state-dir>/hail/deliveries/`. It does **not** dispatch turns
— that is the delivery worker (isaac-wte9, step 2).

Routing is **fail-fast**: a hail that cannot produce at least one delivery
moves to `<state-dir>/hail/undeliverable/` with a `:reason`. After a tick,
every processed hail has left `pending/` — to `deliveries/` or
`undeliverable/`. No retry, no self-heal, no pending-forever.

## Scope

### Worker loop

A Reconfigurable module registering a scheduler task (`hail/route`) on
startup, ticking ~1s. Same shape as `cron/service.clj` and
`comm/delivery/worker.clj`.

On each tick:
1. Enumerate `<state-dir>/hail/pending/*.edn`
2. For each hail, resolve `:frequency` and either emit deliveries or move it
   to `undeliverable/`
3. Remove the raw hail from `pending/` once processed

### Delivery file

Each obligation becomes a file at
`<state-dir>/hail/deliveries/<delivery-id>.edn` with a globally sequential id
(`delivery-N`, via `isaac.naming`). The delivery wraps the original hail
verbatim under `:hail` plus resolved addressing — keeping hail-schema and
delivery-schema separate:

```clojure
;; bound (reach :all, or reach :one resolving to exactly one session)
{:id "delivery-1"
 :hail {:id "hail-1" :frequency {...} :payload {...} :prompt "..." :from :cli}
 :crew :bartholomew :session :engine-room
 :attempts 0}

;; unbound (reach :one over a pool — session bound later by the delivery worker)
{:id "delivery-1"
 :hail {...}
 :crew nil :session nil
 :candidates [{:crew :atticus :session :bridge} {:crew :cordelia :session :first-watch}]
 :attempts 0}
```

### Address resolution

`:frequency` can carry any combination of `:band`, `:crew`, `:session`,
`:crew-tags`, `:session-tags`. Band lookup (`isaac.hail.bands`) yields the
band's filter; it intersects with hail-side fields. Resolution is a **pure
function** (frequency + live crews/sessions → candidate `(crew, session)`
set) so it is testable apart from the I/O.

### Binding rule (obligations locked at hail time — snapshot)

- `:reach :all` → one bound delivery per currently-matching session. Zero
  matches → `undeliverable` (`:no-recipients`).
- `:reach :one`, pool size 1 (incl. direct `:session` / `:crew`) → one bound
  delivery.
- `:reach :one`, pool size >1 → one unbound delivery carrying the frozen
  `:candidates` snapshot.
- `:reach :one`, pool size 0 → `undeliverable` (`:no-recipients`).
- Unknown band → `undeliverable` (`:unknown-band`).

Latecomers miss out: sessions that appear after the tick are not added.

### Emission order

Deliveries for a single `:all` hail are emitted **sorted by session id**
(deterministic `delivery-N` assignment).

## Out of scope (deferred)

- Turn dispatch / binding of unbound deliveries (isaac-wte9, step 2).
- TTL / retry of undeliverable hails (fail-fast by decision).
- Cross-install hails (`band@host`).

## Acceptance

- Worker registered on the shared scheduler (`hail/route`, ~1s).
- Reads pending hails; writes delivery files; removes the raw hail.
- Resolves all `:frequency` forms and their intersections.
- Binding rule above applied correctly (bound / unbound+candidates /
  undeliverable).
- Unresolvable hails move to `undeliverable/` with the right `:reason`; never
  linger in `pending/`.

## Feature file

`features/hail/router.feature` — 10 `@wip` scenarios. Run:

```
bb features features/hail/router.feature
```

**Definition of done:** remove `@wip` from `features/hail/router.feature`
and `bb features features/hail/router.feature` is green.

**New step introduced:** `the hail router ticks` (parallel to
`the delivery worker ticks`).

## Exceptions

- `features/hail/router.feature` is allowed to change beyond `@wip` removal to
  reflect the two-step hail delivery pipeline introduced in `9c41756f`.
- Authorized non-`@wip` edits include replacing the original per-session inbox
  plus `hail/delivered/<hail>.edn` forensic model with the current
  `hail/deliveries/<delivery>.edn` and `hail/undeliverable/<hail>.edn`
  pipeline.
- Authorized non-`@wip` edits also include the current `:reach :one`
  unbound-`candidates` snapshot semantics, fail-fast undeliverable reasons
  (`:unknown-band`, `:no-recipients`), and the shared-scheduler registration
  scenario for `hail/route`.

## Relationship to other beans

- Parent: isaac-ugx7 (Hail epic).
- Consumes pending records from isaac-vduq (substrate).
- Uses isaac-i4ly (bands) for band lookup and isaac-wr7d (tagging) for tag
  filters.
- Unblocks isaac-wte9 (delivery worker, step 2), which consumes
  `hail/deliveries/`.


## Verification failed

HEAD: 990fdf6ef5a8ab134d81a307fbcba7ff4dc8e960
Working tree: clean

Verification stopped at acceptance check 1.

The bean has no `## Exceptions` section, but `features/hail/router.feature` was changed after the original spec commit in ways beyond `@wip` removal. The original feature landed in `159f581f`; a later bean commit, `9c41756f`, rewrote the scenarios and delivery model from per-session inbox / delivered retry semantics to the current deliveries / undeliverable pipeline with unbound `:candidates`; and the final work commit, `e6b4089e`, only removed `@wip`.

Per the repo verifier rules, feature-file changes are only allowed when they are limited to `@wip` removal or explicitly authorized in the bean. Because this bean has no exception covering that rewrite, I did not continue to the test gate. If the feature rewrite is intentional, add a `## Exceptions` section authorizing the non-`@wip` edits to `features/hail/router.feature` and re-hand off for verify.
