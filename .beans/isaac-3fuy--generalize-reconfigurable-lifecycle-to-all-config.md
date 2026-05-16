---
# isaac-3fuy
title: Generalize Reconfigurable lifecycle; wire hooks and cron through it
status: todo
type: feature
priority: high
created_at: 2026-05-16T16:52:28Z
updated_at: 2026-05-16T17:47:10Z
---

## Problem

Hot-reload "works" today only because most config consumers (crew, models, providers) happen to read the snapshot fresh on demand — accidental hotness, not contract-enforced. Two consumers are broken by design:

- **Hooks** — bypass the `Reconfigurable` framework via a parallel `hooks/reconcile-config-hooks!` pathway called manually from `app.clj:131`. Only handles add/remove of hook names; content changes are silently ignored.
- **Cron** — scheduler started once at boot with initial cfg in `app.clj:182`; no reload pathway at all.

Both shipped because the lifecycle "framework" (`isaac.configurator/reconcile!`) is hardcoded to walk `[:comms]` only. Anything outside the comm tree is on its own.

## Audit findings

**The framework that exists** (`src/isaac/configurator.clj`):

- `Reconfigurable` protocol with `on-startup!` and `on-config-change!`
- `configurator/reconcile!` does proper diff-and-dispatch (lines 118-120): when slice content changes, invokes `on-config-change!` on the existing instance
- Hardcoded to one path (`[:comms]`) via `comm/registry.clj:4`

**Reconfigurable implementations in `src/`:**

| Type | File | Status |
|---|---|---|
| `HooksModule` | `hooks.clj:70` | Implements both methods but **never invoked through `reconcile!`** — hooks isn't a comm slot. Dead code via that path. |
| `NullComm` | `comm/null.clj:19` | No-op. Correct. |
| Comms (Discord, ACP, CLI, Memory) | own files | Wired correctly — they are comm slots. |

**Snapshot-only consumers** (accidentally hot, no callback wiring):

- Crew configs, models, providers, tool allowlists — read fresh per use via `config/snapshot`. Works, but there's no contract distinguishing "I need lifecycle" from "I'm fine reading on demand."

## Plan

Single PR, atomic. Generalize the framework + wire the bypassing consumers through it.

1. **Generalize `configurator/reconcile!` to multiple paths.** A registry table maps config path → factory: `[:comms]` → comm tree (existing), `[:hooks]` → `HooksModule`, `[:cron]` → cron module. `reconcile!` walks each.
2. **Wire hooks through the framework.** Delete `hooks/reconcile-config-hooks!` and the manual call sites in `app.clj`. `HooksModule.on-config-change!` becomes the live owner of `[:hooks]`; receives the diffed slice and re-registers entries whose template/frontmatter content changed.
3. **Wire cron through the framework.** Cron becomes a Reconfigurable component owning `[:cron]`. Adding/changing/removing a cron job takes effect without restart.
4. **Forbid parallel pathways.** Only `configurator/reconcile!` may invoke `on-config-change!`. No more bespoke per-component reload helpers.

## Feature spec

`features/lifecycle/hot_reload.feature` (committed with `@wip`):

| # | Scenario | Line |
|---|---|---|
| 1 | Hook template content change is picked up at runtime | features/lifecycle/hot_reload.feature:12 |
| 2 | Cron prompt content change is picked up at runtime | features/lifecycle/hot_reload.feature:29 |

Run: `bb features features/lifecycle/hot_reload.feature`

### New step definitions required

Two new step phrases — workers must implement these alongside the production code:

- `the hook "<name>" registry entry has:` — table assertion against the in-memory hook registry in `isaac.hooks`
- `the cron job "<name>" has:` — table assertion against the in-memory cron job state in the cron scheduler

Both follow the same shape as the existing `the comm "<name>" exists with state:` step used in `features/lifecycle/reconciler.feature`. Reuse that helper's pattern.

## Out of scope

- **Third-party module-registered hooks.** Useful follow-up — once the lifecycle is enforced, route handlers can register hooks via the framework instead of via parallel logic. Separate bean.
- **Schema-ownership meta-test.** Property assertion that every entity-key has an owner — split into its own bean, blocked-by this one.
- **Refactoring snapshot-only consumers** (crew, models, etc.) to use callbacks. They work fine via fresh reads; tagging them `:snapshot-only` is the meta-test bean's job.

## Acceptance

- [ ] `configurator/reconcile!` supports multiple registered config paths, not just `[:comms]`
- [ ] `HooksModule` owns `[:hooks]` via the framework; `hooks/reconcile-config-hooks!` deleted; `app.clj` no longer calls it
- [ ] Cron module owns `[:cron]` via the framework; scheduler reacts to runtime config changes
- [ ] Two new step definitions implemented (`the hook ... registry entry has:`, `the cron job ... has:`)
- [ ] `@wip` removed from `features/lifecycle/hot_reload.feature`
- [ ] `bb features features/lifecycle/hot_reload.feature` — green
- [ ] Unit specs for `HooksModule.on-config-change!` and the cron component cover the diff cases (add, remove, content-change, no-change)

## Related

- **Schema-ownership meta-test bean** (TBD, separate) — blocked-by this bean
- Hooks bug observed live on zanebot 2026-05-16: new JSON-line templates on disk, old templates served from in-memory registry because content changes don't propagate
