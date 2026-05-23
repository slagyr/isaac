---
# isaac-pctr
title: 'Naming: extract isaac.naming with NamedDomain + NameStrategy protocols'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-05-23T16:00:28Z
updated_at: 2026-05-23T16:07:53Z
---

## Motivation

Session naming has the right bones — multimethod over strategy
keyword, sequential counter with collision skipping, adjective-noun
for discoverable names — but it's locked inside
`isaac.session.naming`. Hail (isaac-ugx7's substrate) wants the same
machinery for counted hail ids; future domains (cron jobs,
deliveries) likely too. The current `:adjective-noun` strategy also
has a quiet correctness gap: it doesn't check for collisions, so
~17k combinations of 130×130 words can duplicate after about 130
sessions per birthday math.

Extract a reusable `isaac.naming` with protocol-shaped strategies
(each algorithm a record) and a tiny domain callback for collision
checks. Sessions migrate with no observable behavior change; the
collision retry on adjective-noun is a new internal safety net.

## Scope

### `isaac.naming` module

```clojure
(defprotocol NamedDomain
  "A namespace where generated names might collide."
  (name-taken? [this name]))

(defprotocol NameStrategy
  "An algorithm for producing fresh names."
  (generate [this]))

(defrecord SequentialStrategy    [state-dir counter-key prefix fs] ...)
(defrecord AdjectiveNounStrategy [domain adjectives nouns]         ...)
```

`SequentialStrategy` is counter-authoritative — no domain needed.
`AdjectiveNounStrategy` retries via the domain when `name-taken?`
returns true, capped at 1000 attempts.

### Session migration

`isaac.session.naming` becomes a thin shim that builds the right
strategy from config and delegates to `isaac.naming/generate`. A
`SessionDomain` record provides `name-taken?` over the session
store. Public surface of `isaac.session.naming` unchanged.

## Out of scope (deferred)

- **Hail consumption** — lands with isaac-ugx7 substrate.
- **UUID strategy** — not currently used by anyone; trivial to add
  later.
- **Configurable word lists** — adjectives/nouns stay hardcoded
  inside `isaac.naming`.
- **Cross-process counter locking** — same v1 trust model as the
  rest of Isaac.

## Acceptance

- `NamedDomain` and `NameStrategy` protocols exist as described.
- `SequentialStrategy` and `AdjectiveNounStrategy` records
  implement `NameStrategy`.
- Sequential reads/increments/writes the counter at
  `<state-dir>/<counter-key>`; produces `<prefix><n>`.
- AdjectiveNoun retries on collision (domain `name-taken?` true),
  throws on attempt exhaustion (cap 1000).
- `isaac.session.naming` migrated; existing
  `features/session/naming.feature` continues to pass unchanged.

## Verification

**Speclj-only.** No new Gherkin feature file — the library has no
direct CLI surface.

- Existing `features/session/naming.feature` is the regression
  contract; must continue passing.
- New Speclj specs at `spec/isaac/naming_spec.clj` cover protocol
  contracts, each strategy's algorithm, counter persistence,
  collision retry, exhaustion throw.

**Definition of done:** new Speclj specs green, existing session
naming feature still green, `bb ci` green.

## Relationship to other beans

- **Blocks isaac-ugx7 (Hail) substrate** — Hail's `send!` uses
  `SequentialStrategy` to mint ids.
- **No upstream blockers.**

## Summary of Changes

- Created `src/isaac/naming.clj`: `NamedDomain` and `NameStrategy` protocols; `SequentialStrategy` (counter-authoritative, reads/increments/writes counter file); `AdjectiveNounStrategy` with collision retry loop capped at 1000 attempts.
- Migrated `src/isaac/session/naming.clj` to a thin shim: added `SessionDomain` record implementing `NamedDomain` via store `contains?` with name→id slugification; `generate` dispatches on strategy keyword to build the appropriate record and delegate to `isaac.naming/generate`; public surface (`generate`, `strategy`) unchanged.
- Created `spec/isaac/naming_spec.clj`: 13 specs covering protocol satisfaction, `SequentialStrategy` counter persistence and sequential increment, `AdjectiveNounStrategy` retry behavior and attempt-exhaustion throw.
- Added intent comment on `Thread/sleep` in `spec/isaac/scheduler_steps.clj`.
