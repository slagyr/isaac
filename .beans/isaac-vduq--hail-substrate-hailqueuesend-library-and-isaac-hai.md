---
# isaac-vduq
title: 'Hail substrate: hail.queue/send! library and isaac hail send CLI'
status: todo
type: feature
created_at: 2026-05-23T16:18:16Z
updated_at: 2026-05-23T16:18:16Z
parent: isaac-ugx7
blocked_by:
    - isaac-pctr
    - isaac-x0b5
---

## Motivation

Slice 1 of the Hail epic (isaac-ugx7): the producer-side foundation.
A library function `hail.queue/send!` writes a hail record EDN file
to `<state-dir>/hail/pending/<id>.edn`. The id comes from
isaac-pctr's `SequentialStrategy`. The CLI `isaac hail send` wraps
this for shell / script use.

Combined with slice 5b (the CLI) because the substrate alone has no
observable surface to Gherkin-test, and the CLI is a thin wrapper
that ships naturally with it. Slices 5a (crew tool) and 5c (HTTP
route) remain separate beans for different audiences.

## Scope

### `isaac.hail.queue/send!`

Library function. Signature roughly:

```clojure
(send! {:frequency <address-map>      ;; e.g., {:band "bean.ready"},
                                       ;;       {:crew :marvin},
                                       ;;       {:session :tidy-cavern},
                                       ;;       or combinations (intersection)
        :payload   <edn-value>
        :from      <keyword>
        :prompt    <string?>})         ;; required for non-band addressing;
                                       ;; band sends pull prompt from the
                                       ;; band's .md companion at delivery
;; → returns the full hail record (with :id and :sent-at populated)
```

- Accepts any well-formed `:frequency` address map. Substrate
  doesn't validate the address against bands/crews/sessions — it
  just persists the record. The fan-out worker resolves later.
- Generates id via `isaac.naming/generate` using a
  `SequentialStrategy` configured for the hail domain
  (`<state-dir>/hail/.counter`, prefix `hail-`).
- Captures `:sent-at` via the project's time abstraction.
- Atomically writes `<state-dir>/hail/pending/<id>.edn` (tempfile
  + rename — same pattern as `comm/delivery`).
- Returns the full record (caller may want id, timestamp).

### `isaac hail send` CLI

**v1 supports band sends only.** The first positional argument is
the band name (sugar for `:frequency {:band "<name>"}`). Direct
addressing (`--crew`, `--session`, `--crew-tags`, etc.) is a
follow-up bean.

```
isaac hail send <band-name> <edn-payload>
isaac hail send <band-name> -                       # payload from stdin
isaac hail send <band-name> <payload> --json        # full record as JSON
isaac hail send <band-name> <payload> --edn         # full record as EDN
```

- Reads payload from argv (EDN literal) or stdin (`-`).
- Sets `:from :cli`.
- Default stdout: the new hail id on a single line (for `$()`
  capture).
- `--json` / `--edn`: full hail record on stdout instead of just
  the id.
- The persisted record's `:frequency` is the address map
  `{:band "<band-name>"}`; future direct-addressing forms add
  additional keys (`:crew`, `:session`, etc.).

## Out of scope (deferred)

- **Fan-out, bands registry, wake** — separate slices of the epic.
- **Direct-addressing CLI flags** (`--crew`, `--session`,
  `--crew-tags`, `--session-tags`) — follow-up bean. v1 supports
  band-only addressing via the positional band-name arg.
- **`hail` crew tool** (slice 5a) and **`POST /hail/send`**
  (slice 5c) — separate producer surfaces.
- **JSON payload input on CLI** — HTTP endpoint covers that
  audience; CLI stays EDN-native.
- **Pending-record retention / cleanup** — owned by the fan-out
  worker when it lands.
- **Validating the band exists** when sending — substrate persists
  the address as given; fan-out worker resolves at delivery.

## Feature files

- `features/hail/send.feature` — 7 scenarios: writes record,
  sequential ids across invocations, sent-at via fixed clock,
  default stdout prints id, stdin payload via `-`, `--json` full
  record, `--edn` full record.

The file carries `@wip` — scenarios are excluded from default `bb
features` / `bb ci` runs until the implementer removes the tag.

Run targeted: `bb features features/hail/send.feature`.

**Definition of done:** remove `@wip` from
`features/hail/send.feature` and
`bb features features/hail/send.feature` is green.

## Test infrastructure note

The `sent-at` scenarios introduce a new step
`the clock is fixed at "<ts>"` that pins the project's time
function for the next `isaac is run with`. This isn't hail-specific
— it should live as a project-wide helper (likely in `cli_steps.clj`
or a new `time_steps.clj`). Implementation lands here rather than
a separate infra bean because the step is small and there's no
other consumer pending. Future tests can reuse it.

## Relationship to other beans

- **Parent: isaac-ugx7 (Hail epic).** Slice 1 (substrate) + 5b
  (CLI send) combined.
- **Blocked by isaac-pctr (Naming module).** Uses
  `SequentialStrategy` for id generation; cannot ship before pctr.
- **Blocked by isaac-x0b5 (Step infrastructure).** Two scenarios
  (`--json`, `--edn`) use `the stdout JSON contains:` /
  `the stdout EDN contains:`.
- **Unblocks downstream Hail slices** — fan-out (slice 3) consumes
  `pending/` records this bean produces; subscription matcher
  (slice 2) doesn't strictly depend on substrate but is meaningless
  without something producing hails.
