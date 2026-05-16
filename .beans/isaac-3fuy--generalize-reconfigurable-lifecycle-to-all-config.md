---
# isaac-3fuy
title: Generalize Reconfigurable lifecycle to all config consumers
status: draft
type: feature
priority: high
created_at: 2026-05-16T16:52:28Z
updated_at: 2026-05-16T16:52:28Z
---

## Problem

The hot-reload "framework" is real but narrowly scoped, and two consumers are broken by design (hooks, cron). Both bypass the protocol via parallel pathways or by being started once at boot with no callback wiring. Hot-reload works today only because most other consumers (crew, models, providers) happen to read the config snapshot on demand — that's accidental hotness, not contract-enforced.

## Audit findings

**The framework that exists** (`src/isaac/configurator.clj`):

- `Reconfigurable` protocol with `on-startup!` and `on-config-change!`
- `configurator/reconcile!` does proper diff-and-dispatch: walks slots, compares old vs new slice, invokes `on-config-change!` when slice content changes
- **Hardcoded to walk `[:comms]`** via the comm-registry's path. Nothing else.

**Reconfigurable implementations in `src/`**:

| Type | File | Status |
|---|---|---|
| `HooksModule` | `hooks.clj:70` | Implements both methods — but **never invoked through `reconcile!`**. Hooks isn't in `[:comms]` in user configs. Effectively dead code via that path. |
| `NullComm` | `comm/null.clj:19` | No-op. Correct. |
| Discord, ACP, CLI, Memory comms | own files | Wired correctly (they are comm slots). |

**Parallel pathways** (bypass the framework):

- `hooks/reconcile-config-hooks!` is called directly from `server/app.clj:131` and `:214`. Only handles add/remove of hook *names*, not content changes within a name. **This is the bug** that left the new JSON-line hook templates stale in memory after disk edits.
- Cron scheduler started once at boot in `app.clj:182`; no reload path. Adding a cron job to config would require restart. Silently broken on a different axis.

**Snapshot-only consumers** (accidentally hot because they read fresh per use):

- Crew configs — read per-turn via `resolve-crew-context`
- Models, providers, tool allowlists — same pattern

These happen to work, but there's no contract distinguishing "I need lifecycle" from "I'm fine reading on demand." Future caching anywhere makes silent-staleness easy.

## Plan

1. **Generalize the registry.** Allow multiple registries beyond `[:comms]`. Each registry binds a config path to a factory: `[:hooks]` → `HooksModule`, `[:cron]` → cron module, `[:comms]` → comm tree (existing). The same `reconcile!` walks each on every config change.
2. **Wire hooks through the framework.** Delete `hooks/reconcile-config-hooks!` and the manual calls in `app.clj`. `HooksModule` becomes the live owner of `[:hooks]`; its `on-config-change!` receives the diffed slice and re-registers entries whose content changed (the current bug).
3. **Wire cron through the framework.** Cron becomes a Reconfigurable component owning `[:cron]`. Adding/removing/changing a cron job takes effect without restart.
4. **Meta-test ownership.** A spec iterates every top-level entity key in the schema (`:hooks`, `:cron`, `:crew`, `:models`, `:providers`, `:comms`, …) and asserts each is either (a) claimed by a registered Reconfigurable, or (b) explicitly tagged `:snapshot-only` in the schema. Adding a new config key without one or the other fails CI.

## Out of scope

- Third-party module-registered hooks. Mentioned in conversation as a future idea — useful but not in scope here. Once the config lifecycle is enforced, that follow-up is straightforward.
- Refactoring snapshot-only consumers (crew, models, etc.) to use callbacks. They work fine on snapshot reads; the meta-test just makes the categorization explicit.
- Eliminating the hooks in-memory registry entirely (route handler could read templates from snapshot at request time). Worth considering separately as a simplification, but not required for the lifecycle fix.

## Acceptance

- [ ] One generalized registry mechanism; `[:hooks]` and `[:cron]` registered alongside the existing `[:comms]`
- [ ] `hooks/reconcile-config-hooks!` and any other parallel reload pathways deleted; only `configurator/reconcile!` invokes `on-config-change!`
- [ ] `HooksModule.on-config-change!` re-registers hooks whose template (or any frontmatter) content changed — not just whose name appeared/disappeared
- [ ] Cron component owns `[:cron]`; adding/changing a job at runtime takes effect without restart
- [ ] Schema-ownership meta-test fails if any top-level entity key lacks an owner or `:snapshot-only` tag
- [ ] All `@wip` scenarios pass; `@wip` removed from the feature file
- [ ] `bb features features/server/hot_reload.feature` (or equivalent path) — green

## Related

- isaac-p7k1 (turn-funnel) — independent
- isaac-cdqk (context-mode) — exercised the snapshot-read path, which is the "accidentally hot" category this bean would formalize
