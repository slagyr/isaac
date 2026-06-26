---
# isaac-3wic
title: Configurable hail id naming strategy
status: draft
type: feature
priority: normal
created_at: 2026-06-26T03:33:53Z
updated_at: 2026-06-26T03:33:56Z
blocked_by:
    - isaac-hoaq
---

Make hail id minting strategy configurable via Isaac config, mirroring `:sessions :naming-strategy` (isaac-atpy). Today isaac-hoaq hardcodes bare `:short-uuid` in `queue.clj`; this bean adds a config dispatch so installs can choose `:short-uuid` (default), `:uuid`, or `:sequential` (`hail-N`).

## Problem
- Hail minting is hardcoded to `ShortUuidStrategy` — no config knob.
- Sessions already have `:sessions :naming-strategy`; hail should follow the same pattern for consistency and test determinism (`:sequential` → predictable `hail-1` in feature Backgrounds).
- Existing on-disk `hail-N` records coexist regardless of strategy; ids are opaque strings everywhere downstream.

## Config shape (proposed — planner confirm path)
Add a **new snapshot-only config slice** in isaac-hail manifest (NOT the `:hail` bands entity map):

```
:hail-settings :naming-strategy
```

- **Path:** `config/isaac.edn` → `{:hail-settings {:naming-strategy :short-uuid}}`
- **Default when absent:** `:short-uuid` (preserve post-hoaq behavior)
- **Allowed values:** `:short-uuid`, `:uuid`, `:sequential` (keyword or string, like sessions)
- **Schema:** isaac-hail `src/isaac-manifest.edn` — new `:hail-settings` entry with `:naming-strategy` validated `[:one-of? :short-uuid :uuid :sequential]`

Alternative names (`:hail-defaults`, nested under `:defaults`) — planner pick one; avoid overloading the `:hail` bands table.

## Production — isaac-hail
**`src/isaac/hail/queue.clj`** (single mint path: `send!` + router `next-id`):
- Extract `make-hail-naming-strategy [cfg root fs*]` (or `ensure-hail-naming-strategy!` cached on nexus like sessions).
- Dispatch on `(get-in cfg [:hail-settings :naming-strategy])`:
  - `:short-uuid` → `(naming/->ShortUuidStrategy nil)` — bare 8-hex
  - `:uuid` → `(naming/->UuidStrategy nil)` — bare full UUID
  - `:sequential` → `(naming/->SequentialStrategy root "hail" "hail-" fs*)` + restore counter sync against existing hail files before mint (the old `sync-hail-counter!` / `store/max-hail-seq` path) so `hail-2` follows delivered `hail-1`
- `naming-strategy` reads from installed config snapshot (already available in `send!` via `snapshot-config`).

**Out of scope:** `delivery_worker.clj` session spawn ids (`session-` SequentialStrategy) — session naming, not hail.

## Tests
**New feature:** `features/hail-naming.feature` (mirror `isaac-agent/features/session/naming.feature`):
1. With `hail-settings.naming-strategy sequential`, two sends mint `hail-1` then `hail-2` (deterministic paths OK).
2. With `hail-settings.naming-strategy short-uuid`, two sends produce distinct bare 8-hex ids.
3. (Optional third) `:uuid` → full UUID format assertion.

**Specs:** `spec/isaac/hail/queue_spec.clj` — dispatch unit tests per strategy; default remains `:short-uuid` when config absent.

**Existing minting features:** No required revert — id-agnostic steps from hoaq stay valid for default. Sequential Background is optional ergonomics for new scenarios only.

**Fixture features** (router/delivery/spawn with Given `hail-1`): unaffected — explicit ids, not minting.

## Acceptance
- `config/isaac.edn` may set `hail-settings.naming-strategy` to `:short-uuid`, `:uuid`, or `:sequential`.
- Default (unset) → `:short-uuid` (same as current hoaq production).
- `:sequential` restores `hail-N` minting with counter sync; `:uuid` / `:short-uuid` do not touch `hail/.counter`.
- Router reach-`:all` child ids use the same configured strategy.
- `features/hail-naming.feature` green; `bb spec` + `bb features` green in isaac-hail.

## Dependencies
- **Blocked by:** isaac-hoaq (short-uuid hardcoded default + id-agnostic test rework must land first)
- **Uses:** isaac-a3fb strategies in isaac-foundation (already shipped)

## Notes
- Surfaced 2026-06-25 discussion: Micah wanted `:short-uuid` as hail default; this bean adds configurability without changing the default.
- zanebot / existing terminals: no migration; strategy only affects newly minted ids.
- Planner: confirm config path name and whether `isaac config get/set` needs path-navigator examples in bean body.
