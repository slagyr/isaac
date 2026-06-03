---
# isaac-izw7
title: Remove deprecated --home, :state-dir, :isaac-home, :isaac-state-dir, :home aliases
status: in-progress
type: task
priority: normal
created_at: 2026-06-03T08:57:09Z
updated_at: 2026-06-03T14:37:45Z
---

Finish the isaac-root collapse by removing all backward-compat
aliases. No `--home`/`:home`, no `:state-dir`/`:isaac-state-dir`,
no `:isaac-home`. One name: `--root` / `:root` / `isaac.root`.

## Surface to remove

CLI flags:

- `--home` (legacy alias that appends `/.isaac`) — `src/isaac/root.clj`
  `extract-home-flag` and the second-priority lookup branch in
  `resolve-root` (root.clj:9, root.clj:92-100, root.clj:123-143).

Code-level keys / nexus slots:

- `:state-dir` everywhere it still appears as an alias for `:root` —
  opts maps, nexus reads (`system/get :state-dir`), threading through
  `:state-dir` in opts payloads. Per the umbrella `isaac-root` bean
  inventory, every consumer should read via `isaac.root/current-root`
  instead.
- `:home` opt key in `src/isaac/config/api.clj` and friends.
- `:isaac-home` nexus slot (if any remain).
- `:display-home` opt key — replace with `:display-root`.

CLI / step phrasings:

- Step phrases "an in-memory Isaac state directory" and "an empty
  Isaac state directory" (~50 features) — replace with "an Isaac root
  at". Per the inventory in isaac-root.
- `cli_steps.clj` helpers `empty-isaac-home`, `isaac-home-contains-config`,
  `isaac-home-has-no-config` — rename/replace.

main.clj transitional code:

- `:state-dir resolved-root` merge at `src/isaac/main.clj:119` (the
  comment there reads "`:state-dir` is the legacy internal key kept
  as a synonym ...").
- The `--home` parse path in main.clj (if still present).

## Why now

We just hit a deploy-blocking bug (zanebot resume failure) because the
rename was half-done: `isaac-acp` was reading `(system/get :state-dir)`
but the server only registers the new `isaac.root/init-root!` atom.
Each remaining alias is another opportunity for the same class of bug.
The longer aliases live, the more new code is written against them.

## Out of scope

- Behavior change. Default root is still `~/.isaac`; pointer file is
  still `~/.config/isaac.edn`. Only the legacy *names* go.
- Pointer-file migration helper. The umbrella `isaac-root` bean already
  chose breaking-change semantics (option a) — old `{:home ...}` pointer
  files just stop working.

## Acceptance

No new Gherkin needed. This bean removes vestigial names; the
existing suite already exercises the surviving (`--root` / `:root` /
`isaac.root`) paths, so a green suite after the rip IS the
behavioural contract. Writing scenarios that assert the old names
are gone would just be tests of dead code.

Definition of done:

- Full spec/feature suite green: `bb spec` and `bb features`.
- These greps return zero matches:

  ```
  rg -- '--home\b'        src spec features
  rg -- ':state-dir\b'    src spec
  rg -- ':isaac-home\b'   src spec
  rg -- ':display-home\b' src spec
  ```

- `isaac.root/extract-home-flag`, the `explicit-home` arity of
  `resolve-root`, and the `--home` lookup branch are deleted (not
  just unused).
- `src/isaac/main.clj` no longer threads `:state-dir resolved-root`
  as a synonym; the "legacy internal key kept as a synonym" comment
  is gone.

## Related

- Parent (conceptual): `isaac-root` (the umbrella collapse). Not set as
  a formal `parent:` because isaac-root is type `refactor` and the
  beans schema only allows milestone/epic/feature parents on tasks.
- Predecessor: `isaac-2w4d` (loader-side state-dir fix, completed).
- Cross-repo follow-up: `isaac-acp` was just patched (`ac505c6`) to read
  `isaac.root/current-root` instead of `system/get :state-dir`. Confirm
  no other module repos (`isaac-discord`, `isaac-imessage`) still reach
  for the old nexus key.
