---
# isaac-cvri
title: 'config set: siblings-all-files placement rule + atomic multi-path write (set-many!)'
status: in-progress
type: feature
priority: high
created_at: 2026-09-30T03:00:10Z
updated_at: 2026-09-30T03:31:12Z
---

Design notes: planner, 2026-09-29/30, drafted while scoping isaac-lshz
(isaac-handbook's `handbook__configure`). Not scenario-ready; draft to hold
decisions and block isaac-lshz on it. Revised 2026-09-30 after Micah's
ruling: no per-call override — new-entity placement follows `config set`'s
own existing precedent, which is missing one rule.

## What it is

Two related fixes/additions to `isaac.config.mutate` (isaac-foundation):

1. **A missing placement rule in `config set`'s existing precedent**
   (`choose-set-location`): when a brand-new entity is added to a kind whose
   other entries are ALL already stored as entity files, the new one should
   become a file too — today it silently lands inline in root `isaac.edn`
   instead. This is a plain `isaac config set` bug fix, not something
   specific to `handbook__configure` — both callers get it once fixed.
2. **A new callable primitive** that applies **several `{path, value}`
   set/unset operations as one atomic write**: staged and validated as a
   single resulting config, applied all-or-nothing. Refusing the batch
   writes nothing at all — not even the pairs that were individually valid.
   This one IS new behavior (no CLI equivalent), needed by
   `handbook__configure`.

## Why (found during isaac-lshz investigation)

**Placement precedent, checked against `isaac.config.mutate` and
`features/cli/config_file_layout.feature` / isaac-agent's
`features/config/set_unset.feature`:** Micah's stated rule for where a
config write lands is:

1. editing an existing entry → write it where it already lives;
2. adding an entry to a kind whose other entries are ALL entity files (a
   directory) → the new entry becomes a file too;
3. else, if `prefer-entity-files` is set → new entity file;
4. else → inline in root `isaac.edn`.

Reading `choose-set-location` in `src/isaac/config/mutate.clj` line by
line: rules 1, 3, and 4 are implemented today —

```clojure
(defn- choose-set-location [parsed state]
  (cond
    (and (:companion? parsed) (:md-exists? state)) :md
    (and (:companion? parsed) (:inline-root-companion? state)) :root
    (and (:companion? parsed) (:inline-entity-companion? state)) :entity
    (:slice-exists? state) :slice
    (and (:entity? parsed) (:frontmatter-relative state)) :frontmatter
    (and (:entity? parsed) (:entity-root-exists? state)) :root
    (and (:entity? parsed) (:entity-exists? state)) :entity      ; rule 1 (same id already a file)
    (and (:entity? parsed) (:prefer-entity-files? state)) :entity ; rule 3
    (and (:prefer-entity-files? state) (not (:root-key-inline? state))) :slice
    :else :root))                                                 ; rule 4
```

— but **rule 2 does not exist**. Every branch that checks "does this
already exist" checks the SAME entity id (`:entity-exists?`,
`:entity-root-exists?`, `:frontmatter-relative`) — nothing inspects
*siblings* of a different id under the same root-key. `config-state` never
lists the `<entity-dir>/` directory or inspects other ids' locations at
all. Confirmed by grepping `mutate.clj` for `children`/`siblings`/`dir?`:
the only `fs/dir?` use is unrelated (copying declared local module roots).
Neither `config_file_layout.feature` (tests the file/dir-per-key split
generically, not new-entity placement) nor isaac-agent's `set_unset.feature`
(tests scalar/set-typed fields on entities that already exist, not
first-write placement) has a scenario for "kind's other entries are all
files, does a new one follow suit" — this gap was never exercised. New
CLI-level scenarios below prove it (`config_set_new_entity_placement.feature`).

**Atomic multi-path write:** `set-config`/`unset-config` each take exactly
**one** dotted path. The CLI's "stdin-map form" (`echo '{...}' | isaac
config set models.echo -`) already gives atomicity for **several fields
under one shared parent path** (one entity) — covers "two fields on one
entity, valid only together." It does **not** cover several pairs spanning
**different top-level paths/entities** in one refuse-or-commit-together
call, which `handbook__configure`'s wire contract needs (grant a tool AND
create a companion cron job in one call). No `set-many`/batch-mutation
helper exists anywhere in isaac-foundation (grepped).
`set-config`/`unset-config` already do the hard part per-path — stage a
plan, validate the *resulting* config once, apply for real
(`validate-plan`/`apply-plan!`) — the new function folds N per-path plans
into one combined plan before staging/validating/applying.

## Wanted

1. **Fix `choose-set-location` (and `choose-unset-location`, for symmetry
   on delete-empties-directory cases) to add the missing sibling rule.**
   `config-state` gains something like `:siblings-all-files?`: true when
   (a) the path names a NEW entity (not `:entity-exists?`,
   not present inline at `:entity-root-exists?`), (b) the root-key's
   inline map in root data has NO other entries for this kind (any inline
   sibling means "not all files" → false), and (c) the `<entity-dir>/`
   directory exists and has at least one child (a sibling file). Insert the
   new clause between the existing "same id already a file" check and the
   `prefer-entity-files?` check, so precedence matches Micah's order
   exactly:
   ```clojure
   (and (:entity? parsed) (:entity-exists? state)) :entity          ; rule 1
   (and (:entity? parsed) (:siblings-all-files? state)) :entity     ; rule 2 (NEW)
   (and (:entity? parsed) (:prefer-entity-files? state)) :entity    ; rule 3
   ```
   No new kwarg, no CLI flag — this is a pure bug fix to existing
   `isaac config set` behavior. Both the CLI and the new `set-many!` (below)
   get it for free since both build plans through `set-plan`/
   `choose-set-location`.

2. **`isaac.config.mutate/set-many!`** (name open) — `[root ops]` where
   `ops` is `[{:op :set :path "..." :value ...} {:op :unset :path "..."} ...]`.
   - Parses every path first; a parse failure (`:invalid-path`,
     `:missing-path`, `:missing-entity-id`) on ANY op refuses the whole
     batch before touching the filesystem.
   - Builds each op's plan (reusing `set-plan`/`unset-plan`, which now also
     carries the rule-2 fix above), **merges** the plans (`:writes` maps
     merge left-to-right in op order — a later op's write to the same
     relative file wins; `:deletes` union, minus anything a later op
     re-writes).
   - Stages the merged plan against ONE copied filesystem, validates the
     resulting config ONCE (reuse `validate-plan`), same pre-existing-error
     carry-forward / new-error-blocks semantics as today.
   - On any blocking new error: `{:status :invalid :files [] :errors [...]
     :warnings [...]}` — `apply-plan!` is never called.
   - On success: applies the merged plan in one `apply-plan!` call and
     returns `{:status :ok :files [<relative paths touched>] :errors []
     :warnings [...]}` (plural `:files`, since a batch can touch several).
   - No `--force` — matches isaac-lshz's decision that `handbook__configure`
     never bypasses validation.

3. Log event for the primitive itself follows the existing
   `:config/set`/`:config/unset` convention in `mutate-common.clj` — no new
   requirement here; `isaac-handbook` does its own `:handbook/configure`
   logging around the call (see isaac-lshz).

## Non-goals

- No CLI subcommand for multi-path set/unset in this bean — `isaac config
  set`/`unset` stay single-path commands (they just get the rule-2 fix).
  If a CLI multi-set surface is wanted later, it's a separate bean.
- Does not touch `isaac.config.loader`/`normalize` (isaac-dnib territory —
  schema defaults, conform overlay, `--raw`). This bean only touches
  `isaac.config.mutate`'s write path. Land after dnib merges to `main` to
  avoid a merge-conflict-shaped review; rebase onto `main` before starting
  work either way.

## Acceptance

- `isaac-foundation/features/cli/config_set_new_entity_placement.feature`
  (drafted, `@wip`) — 4 scenarios proving rule 2 (the fix) and guarding
  rules 3/4/1 don't regress.
- `isaac-foundation/features/config/atomic_multiset.feature` (drafted,
  `@wip`) — 5 scenarios for `set-many!`, including one that exercises the
  rule-2 fix inside a batch (no override language).
- `spec/isaac/config/mutate_spec.clj` gets unit coverage for `set-many!`
  (plan-merge order, refuse-whole-on-any-error, `:files` plural) and for
  the rule-2 placement fix directly — this is a pure function with no CLI
  surface for the batch case, so unit specs carry more of the proof than
  the feature file does; both are required per foundation's testing
  discipline.
- `bb spec` and `bb features` green.

## Open questions

- **Name**: `set-many!` vs `apply-config!` vs `mutate-many!`. No strong
  opinion; whoever implements picks one and keeps it applied consistently
  in isaac-handbook's call site.
- **Rule-2 scope for unset/delete symmetry**: when an unset empties out the
  LAST file-backed sibling of a kind, should a subsequent new entry in that
  now-empty kind still see "siblings are files" (nothing left to check) or
  fall through to rule 3/4? Recommendation: nothing left → no siblings →
  rules 3/4 apply (same as a kind that never had any entities). Flagging
  only because it's a real edge the implementer should have a test for, not
  because it's ambiguous.

## Landed on main (2026-09-30)

Implemented `set-many!` (name chosen, per the open question) and the rule-2
`:siblings-all-files?` fix in `choose-set-location`, plus a whole-entity
companion-field split fix in `set-plan` needed for scenario 5 (a new
whole-entity write with a companion field now splits it into the .md, same
as a per-field write already did). `set-many!` never blocks on reference
errors (mirrors the CLI's `set-config`/`unset-config` `:skip-ref-validation?
true` default) — required for scenario 5's forward gauge reference and a
reasonable default for a primitive meant to wire up mutually-referencing
entities. `choose-unset-location` needed no code change: `fs/children`
(not `fs/dir?`) already reads a just-emptied directory as having no
siblings, so the open-question edge case (new entry after the last
file-backed sibling was unset) falls through to rules 3/4 for free.

zanebot was down; landed locally per dispatcher instruction.

main-sha: isaac-foundation a036b84cb5d59f37b174926472f4774176faf4f9

GitHub CI on that commit: the `verify` job (`bb ci` — spec + features) is
green. Two unrelated jobs are red — "Server boot with a module-provided
config type" (cross-repo isaac-http check) and "Slow features (@slow
launcher lane)" — but both were ALREADY red on the immediately-prior commit
(isaac-n140, landed before this bean started), confirmed via
`gh run view` on that commit's run. Pre-existing, unrelated to this bean's
diff (isaac.config.mutate / config-steps / handbook only).

feature-baseline: isaac-foundation 30a11ba4b00cf3e4a9be9c060a4fe473a5821b2e
feature-blob: isaac-foundation features/cli/config_set_many.feature fd8514dfed298db9197ffba42e9cbba9dc1b3e72
feature-blob: isaac-foundation features/cli/config_set_new_entity_placement.feature c42e290be01426b80365b34fc8cd7090e60ec0cd
