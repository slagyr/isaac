---
# isaac-root
title: Collapse :home and :state-dir into a single :root concept
status: draft
type: refactor
priority: normal
created_at: 2026-05-31T00:00:00Z
updated_at: 2026-05-31T00:00:00Z
---

## Motivation

Two intertwined naming problems make the on-disk model harder to reason
about than it needs to be:

1. **`--home` collides with `$HOME`.** The flag means "Isaac's home", but
   the reader's eye parses it as "user home". Errors, docstrings, and
   step phrasings all suffer the same ambiguity.
2. **Parallel concepts `:home` and `:state-dir`.** Production treats
   `:home = ~` and derives `:state-dir = home + "/.isaac"`. Tests blur
   the line — some pass the test dir as `:home` (so config lands at
   `<dir>/.isaac/config/...`) while also registering a flat `:state-dir`
   on the nexus. `isaac-2w4d` already named this wrinkle:

   > Test harnesses (e.g. session_steps in-memory-state) model
   > state-dir inconsistently with production: they pass the test dir
   > as `:home` (config lands at `<dir>/.isaac/config`) but register a
   > *flat* state-dir (`<dir>`, no `.isaac`) on the nexus.

`isaac-2w4d` closed half the loop (loader stamps state-dir onto the
config map). The other half — collapsing the two-concept model into
one — is the goal of this bean.

## Proposed model

**One concept: `:root`. It IS the Isaac data directory.**

- CLI: `--root <dir>` and `ISAAC_ROOT` env var. `--root` points DIRECTLY
  at the data dir (no `.isaac` appended internally).
- Pointer file (`~/.config/isaac.edn`, fallback `~/.isaac.edn`): key
  becomes `:root`; value is the data dir directly.
- Default value (no flag/env/pointer): `~/.isaac`. The only place the
  `.isaac` literal still lives is as the *default value* of root.
- Drop: `--home`, `:home` key, `*resolved-home*`, `resolve-home`,
  `extract-home-flag`, `current-home`, `init-state-dir!`,
  `:state-dir` as a user-settable concept, `paths/default-state-dir`.
- Add: `--root`, `:root`, `*root*`, `resolve-root`,
  `extract-root-flag`, `init-root!`, `default-root`.
- Move: `src/isaac/home.clj` → `src/isaac/root.clj`.

### Why not just rename `--home` → `--root`?

That keeps the parent/data distinction and continues spawning the
exact inconsistency described above. Surface rename without semantic
collapse leaves the test harness still confused.

## Inventory (collected 2026-05-31)

### Source files referencing `home` / `:state-dir`

- `src/isaac/home.clj` — the namespace; 85 LOC, 4 dynamic vars, 8 fns.
- `src/isaac/main.clj` — flag parsing, opts plumbing, help text.
- `src/isaac/cli.clj` — `init-run`, `:display-home`, `:home` consumption.
- `src/isaac/config/paths.clj` — `default-state-dir`, `state-dir` param
  name across every helper.
- `src/isaac/config/api.clj` — `:home` opt key, `home/user-home`.
- `src/isaac/config/cli/common.clj` — `resolve-state-dir`.
- `src/isaac/config/loader.clj` — `missing-config-message` (uses
  state-dir), state-dir derivation.
- `src/isaac/config/change_source*.{clj,cljc}` — `home` param threaded.
- `src/isaac/server/app.clj` — derives `config-home` from `state-dir`.
- `src/isaac/llm/auth/cli.clj` — `home/state-dir`, `:state-dir`.
- `src/isaac/slash/builtin.clj` — `home/user-home`.
- `src/isaac/service/macos.clj` — `home/user-home`, `:home` plist key.
- `src/isaac/session/context.clj` — `:home state-dir` ctx key.

### Spec files with hardcoded `/.isaac/` subpaths

- `spec/isaac/cli_spec.clj` — `(str test-home "/.isaac/config/...")` ×8.
- `spec/isaac/home_spec.clj` — rename to `root_spec.clj`, rewrite all.
- `spec/isaac/main_spec.clj` — `{:home …}` pointer assertions.
- `spec/isaac/marigold.clj` — `home`, `state-dir` helpers.
- `spec/isaac/hooks_spec.clj`, `bridge_spec.clj`, `api_spec.clj`,
  `charge_spec.clj`, `nexus_spec.clj`, `configurator_steps.clj` —
  `:state-dir` keys throughout nexus setup.

### Step impls (semantic flip required)

- `spec/isaac/session/session_steps.clj` — `empty-state`,
  `in-memory-state`, `->state-dir`, `seed-minimal-config!`. Today these
  treat the given path as **parent** and seed at
  `<path>/.isaac/config/...`. After collapse: path **is** the data dir;
  seed at `<path>/config/...`.
- `spec/isaac/server/cli/cli_steps.clj` — `empty-isaac-home`,
  `isaac-home-contains-config`, `isaac-home-has-no-config`. Same flip.

### Feature files (~60) using the old vocabulary

- `features/cli/init.feature`, `home_pointer.feature` (rename →
  `root_pointer.feature`), `llm/cli-usage.feature`, `version.feature`.
- ~50 features under `features/config/`, `features/module/`,
  `features/scheduler/`, `features/llm/`, `features/session/`,
  `features/bridge/` use
  `"Given an in-memory Isaac state directory \"X\""` or
  `"Given an empty Isaac state directory \"X\""`. After collapse,
  rephrase to `"Given an Isaac root at \"X\""` AND drop the `.isaac`
  segment from any nested path assertions in those scenarios.

### Docs

- `AGENTS.md`, `ISAAC.md`, `README.md`.

## Migration concerns

1. **Pointer file breaking change.** Existing `~/.config/isaac.edn`
   `{:home "/parent"}` (meaning data at `/parent/.isaac`) is
   incompatible with `{:root "/parent"}` (meaning data at `/parent`
   directly). Two options:
   - **a.** Breaking change with a CHANGELOG note; user reruns
     `isaac init` or hand-edits the pointer.
   - **b.** Read `:root` first, fall back to `:home` with a
     deprecation warn; preserve old semantics for `:home`. Remove the
     fallback later.

   Recommend **(a)** — small audience, clean break, matches the rename
   philosophy.

2. **On-disk default unchanged.** `~/.isaac/` remains the default data
   dir; users not using the flag/env/pointer notice nothing.

3. **Test path expectations.** Every spec/feature that asserts a path
   under `<test-state>/.isaac/...` drops the `.isaac` segment. That's
   the bulk of the diff.

## Phased land plan

Three PRs to keep review tractable:

- **PR 1 — Source collapse.** New `isaac.root` ns;
  main/cli/paths/api/loader/common/app updated. CLI flag is `--root`;
  `--home` removed. Pointer file uses `:root`. Spec changes limited to
  internal-symbol callers (feature files untouched yet — they'll be
  red here, intentionally).
- **PR 2 — Test harness flip.** Update `session_steps.clj` and
  `cli_steps.clj` step impls; rephrase Given steps to
  `"Isaac root at X"`; update all ~60 feature files' Given phrasings
  AND any inline path assertions to drop `.isaac/`. Rename
  `home_pointer.feature` → `root_pointer.feature`.
- **PR 3 — Docs + no-config polish.** `AGENTS.md`, `ISAAC.md`,
  `README.md`; tighten the no-config error to mention the root path;
  add any deferred scenarios.

The no-config-found feature scenarios that prompted this bean are
being added now against today's vocabulary in
`features/cli/init.feature`. PR 2 will rephrase them along with the
rest.

## TODOs

- [ ] PR 1: source collapse (per inventory above).
- [ ] PR 2: test/feature harness flip.
- [ ] PR 3: docs + cleanup.
- [ ] Decide pointer-file migration approach (a vs b above).
- [ ] Confirm naming on remaining details: `:display-home` opt key,
      `:isaac-home` nexus slot, `config-home` local in
      `server/app.clj`, `{HOME}` template placeholder in
      `service/macos.clj`.

## Acceptance criteria

- `isaac --help` shows `--root <dir>` only (no `--home`).
- `~/.config/isaac.edn` with `{:root "/some/dir"}` resolves to
  `/some/dir/config/isaac.edn` (no `.isaac` segment injected).
- No `:home` or `:state-dir` opt keys remain in the codebase
  (`rg -- ':home\b|:state-dir' src spec` comes up clean). `:root` is
  the only term.
- Step phrasing in features says `"Isaac root"`, not
  `"isaac home"` or `"Isaac state directory"`.
- All specs and features still pass.

## Related

- `isaac-2w4d` (completed) — earlier half of this work; closed
  loader/nexus state-dir gap and left the "harness inconsistency"
  wrinkle for this bean.
