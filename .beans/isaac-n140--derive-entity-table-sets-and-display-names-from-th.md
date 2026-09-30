---
# isaac-n140
title: Derive entity-table sets and display names from the schema itself
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-30T02:43:45Z
updated_at: 2026-09-30T03:27:57Z
blocked_by:
    - isaac-v38i
---

## Ruling

Micah, 2026-09-29: isaac-foundation's code must never name another module's
config, berths, ids or concepts — including via a hand-maintained set of
"known entity table" names that has to be kept in sync by hand every time a
module adds one.

## Problem

Foundation ALREADY has the generic machinery to answer "which top-level
keys are entity tables" from the composed schema — it's just not used
everywhere:

- `isaac.config.schema-compose/entity-dir-names` (line 208-210): distinct
  `:entity-dir` values across all descriptors.
- `isaac.config.schema-compose/merge-root-entity-kinds` (line 220-224):
  kinds whose descriptor sets `:merge-root-entity? true`.
- `isaac.config.mutate/entity-sections` (line 30-36) ALREADY uses
  `entity-dir-names` generically, with an explicit comment: "a key is a
  directory because a directory exists, not because a module declared
  :entity-dir — foundation names no kind (isaac-49zp)."

But three call sites still hard-code a fixed set of entity-collection names
instead, and the two of them that should agree with each other don't:

- `src/isaac/config/cli/validate.clj:23`:
  `#{:berths :gauges :foundries :crew :models :providers}`
- `src/isaac/config/schema/resolve.clj:12`:
  `#{:berths :gauges :foundries :crew :hail :models :providers}`
  — **note `:hail` is present here but missing from validate.clj's set.
  This is a live inconsistency, not just a naming violation**: a path like
  `hail.<id>.crew` parses differently for `config schema` vs
  `config validate`'s stdin-overlay path parsing today.
- `src/isaac/config/entities.clj:307-312`, `dangling-entry-kind`:
  ```clojure
  (defn- dangling-entry-kind [kind]
    (case kind
      :hooks "hook"
      :models "model"
      :providers "provider"
      (name kind)))
  ```
  Hard-codes the singular display form for three kinds.

Confirmed each manifest that declares an entity-dir ALSO declares the
singular display name right there, already, as `[:schema :value-spec
:name]` — e.g. isaac-agent's `:crew` descriptor's `:value-spec {:name
:crew ...}`, `:providers`' `:value-spec {:name :provider ...}`,
isaac-hooks' `:hooks`' `:value-spec {:name :hooks ...}` (check exact value
— hooks declares `:name :hooks` on the table wrapper, not the entry; verify
which level carries the SINGULAR form for hooks specifically before
generalizing, since the grep sample showed `:hooks {... :schema {:name
:hooks ...} :value-spec {:name :hook ...}}` — the entry-level `:value-spec
:name` is the singular one to use). This is exactly the data
`dangling-entry-kind` needs and doesn't currently read.

`entity-collections` in both `validate.clj` and `resolve.clj` is used to
decide, for a 2nd path segment after a table name, whether to treat it as
an entity id (rewrite to `[:key :value]`, descending into `:value-spec`)
rather than a literal declared field name. This is also structurally
derivable: any schema node with BOTH a `:key-spec` and a `:value-spec` IS,
by definition, a dynamic-key entity table — no name list needed at all.
`schema/resolve.clj`'s own `key-segment-for-schema` (line 64-74) already
does exactly this kind of structural check for the `.value`/`.key` special
segments; the fix is to reuse that same structural test instead of a name
set.

## Wanted

1. `entities.clj`'s `dangling-entry-kind`: replace the `case` with a schema
   lookup — `(or (some-> (schema-for kind) :value-spec :name name) (name
   kind))` (confirm the exact accessor path against the composed schema
   shape; `schema-for` already exists in this ns). Delete the `case`.
2. `cli/validate.clj`'s `entity-collections` and `schema/resolve.clj`'s
   `entity-collections`: replace both with one shared, schema-derived
   predicate — "does the root schema's node at this head key have both a
   `:key-spec` and a `:value-spec`?" — rather than a maintained name set.
   This is the fix for the `:hail` inconsistency too (it'll just work once
   both call sites ask the schema instead of consulting a list). Consider
   whether this predicate belongs in `schema-compose.clj` or
   `schema-base.clj` as a shared helper both `validate.clj` and
   `resolve.clj` call, rather than duplicating it.
3. Once cleanup-bean-3 lands, `normalize.clj`'s surviving (post-legacy-
   deletion) per-kind conform dispatch (`normalize-crew-config`/
   `normalize-model-config`/`normalize-provider-config`/
   `normalize-config`'s hard-coded `{:crew ... :models ... :providers
   ...}` result map) is the same underlying problem — it hard-codes which
   three top-level keys get schema-conform treatment. Investigate whether
   this can iterate `schema-compose/entity-dir-names` (or
   `merge-root-entity-kinds`) generically instead of naming `:crew`/
   `:models`/`:providers` explicitly. This may or may not fit cleanly in
   THIS bean vs. staying scoped to normalize.clj's own structure — worker's
   call once cleanup-bean-3's deletion is landed and the surviving shape is
   clear; if it doesn't generalize cleanly, split it into its own follow-up
   bean rather than force it.

## Acceptance

- `isaac config schema hail.value` and `isaac config validate` on a config
  with a `hail.<id>.crew` path behave consistently (both parse the 2nd
  segment as an entity id) — add a scenario pinning this if one doesn't
  already exist (check `isaac-hail`'s own feature coverage first; if hail
  isn't cloned/available, use a Marigold fixture entity table with a
  `:key-spec`+`:value-spec` shape instead, per the existing
  `config_schema.feature`/`config_resolution.feature` pattern).
- `grep -n "entity-collections" isaac-foundation/src` shows either one
  shared definition (not two divergent ones) or none (folded into a
  schema-compose helper).
- `grep -n ":hooks \"hook\"\|:models \"model\"\|:providers \"provider\"" isaac-foundation/src/isaac/config/entities.clj` returns nothing.
- Existing dangling-md scenarios (including the ones cleanup-bean-6a moves
  to foundation) still pass with the schema-derived display name.

## Likely repo scope

`isaac-foundation` only: `src/isaac/config/entities.clj`,
`src/isaac/config/cli/validate.clj`, `src/isaac/config/schema/resolve.clj`,
possibly `src/isaac/config/schema_compose.clj` (new shared helper) and
`src/isaac/config/normalize.clj` (item 3, only if it fits cleanly).

## Notes

- **Sequence after cleanup-bean-3** (item 3 depends on bean-3's deletion
  landing first) **and after isaac-dnib** (dnib is also mid-flight on
  `schema_base.clj`/`normalize.clj`; this bean's item 3 touches
  `normalize.clj` too — same file three efforts touch in sequence:
  dnib → bean-3 → bean-4). Items 1-2 (entities.clj, validate.clj,
  resolve.clj) don't touch any file dnib or bean-3 touches and could land
  independently/earlier if Micah wants to unblock the lint (bean-5) sooner
  — worker's/planner's call on whether to split 1-2 from 3 into separate
  beans at baseline time.

## Open questions

- Confirm hooks' exact `:value-spec :name` for the `:hooks` entity
  ("hook") before generalizing — the manifest grep in this planning
  session showed the table wrapper's OWN `:name` as `:hooks` (matching the
  kind, not singular) with the singular `:hook` living one level down at
  `:value-spec :name`. Make sure the accessor path reads the right level;
  a wrong accessor would silently regress the dangling-md message text.

## Ungated (planner, 2026-09-30)

Refactor plus one bug fix (schema vs validate disagree on `:hail` paths). The worker writes the regression scenario named in Acceptance (Marigold fixture entity table, unique module id, manifest-only); the planner reviews it at verification. Hand off with `tag=unverified`.

## Worker notes (2026-09-30)

Items 1-2 landed. Item 3 (normalize.clj's hard-coded `:crew`/`:models`/
`:providers` dispatch) investigated but NOT folded in: `normalize-provider-
config` skips schema conform entirely (just `->id`s the key) while
`normalize-crew`/`normalize-model` run `lexicon/conform` per entity — the
three kinds are not just named differently, they're handled differently, so
a generic `entity-dir-names`/`merge-root-entity-kinds` iteration would need
to unify that behavior first (a bigger, riskier change than this bean's
scope). Recommend a follow-up bean if Micah wants item 3 pursued.

Collateral fix: making `schema.resolve`'s structural check actually correct
exposed a real bug in `cli/mutate_common.clj`'s `set-config!` — spec
resolution for a field reached through an entity table's `.value` now
succeeds where it previously silently returned nil, so a `:registered-in?`
validation on that field started firing for real, but `*module-index*`/
`*config*` weren't bound around the conform call (only `cli/schema.clj`
bound them, for display). Fixed by binding them from the already-computed
`schema-context`, matching `cli/schema.clj`'s pattern. Caught by the
pre-existing `isaac-a5dx` feature (`config_set_undeclared_key.feature`),
which went red and is green again.

Investigated whether `config validate --as <table>.<id>.<field> -` can
observably distinguish correct (entity-id) vs incorrect (keywordized-field)
path parsing for a manifest-only, non-factory table: it can't currently —
`:key-spec` types aren't enforced on open dynamic maps anywhere in the
load/validate pipeline (confirmed empirically), and non-factory tables never
reach `conform-berth-slices`. The new `config_validate_entity_collections
.feature` scenario is therefore a structural consistency pin (both `config
schema` and `config validate` resolve the same fixture table the same way),
not a fails-under-old-code regression test — the real fails-under-old-code
coverage for this bug lives in the `config set` path via `isaac-a5dx`'s
existing feature, which the resolve.clj fix now makes take the real,
schema-checked branch for the first time.

## Landed on main

main-sha: isaac-foundation 4d0cd8bf8a4ac01608caffdb4e3cf591f9076a55

## Follow-up (planner note, 2026-09-30): config set on a fresh entity-dir table

A scenario drafter flagged that `isaac.config.schema.resolve/schema-for-
data-path` (used by `config set`/`unset` to find a path's field spec) needed
checking against a module-declared entity-dir table not in the old
hard-coded list. Verified: the landed fix already covers this — I removed
`entity-collections` from `schema/resolve.clj` itself (not just
`validate.clj`), replacing it with the same `entity-collection-key?`
structural check. Confirmed empirically with a throwaway probe spec (since
deleted): `config set <fresh-entity-dir-table>.<id>.<keyword-field> <val>`
now correctly resolves the field's real spec and coerces the value (e.g.
`"admin"` -> `:admin`); reverting `schema/resolve.clj` to its pre-bean
version reproduces the bug (value stored as a raw string, no coercion — spec
resolution silently fell back to a guess).

Added a genuinely falsifiable regression scenario to
`features/cli/config_validate_entity_collections.feature` ("config set
finds a field's real spec inside a fresh module-declared entity-dir table")
that fails against the pre-fix `schema/resolve.clj` and passes with it —
confirmed both ways. Committed locally in the `isaac-foundation-n140`
worktree (branch `bean/isaac-n140-followup`) on top of the landed commit:

    c4522ccc7c9bd41b3d03a0f6c61e8f56f9079066  isaac-n140: add config set regression scenario for module-declared entity-dir tables

**Not yet pushed** — `git push` to `isaac-foundation` main was denied by
the permission classifier ("Out-of-Place Publication"), coinciding with
this session's primary working directory moving to `isaac-handbook`
mid-task. No workaround attempted per the tool's own instruction. The
worktree/branch/commit are intact and ready to push
(`git push origin c4522ccc7c9bd41b3d03a0f6c61e8f56f9079066:main` from
`/Users/micahmartin/agents/isaac/plan/isaac-foundation-n140`) once someone
with permission does it, or the classifier is satisfied.
