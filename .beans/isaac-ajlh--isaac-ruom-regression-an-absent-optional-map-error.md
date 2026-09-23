---
# isaac-ajlh
title: 'isaac-ruom regression: an absent optional map errors when its inner fields are :present?'
status: todo
type: bug
priority: high
created_at: 2026-09-23T21:51:54Z
updated_at: 2026-09-23T21:51:54Z
---

Repo: **isaac-foundation** (`src/isaac/config/validation.clj`).

Regression from isaac-ruom, found by the isaac-0r95 worker and confirmed by the
planner. **Blocks isaac-0r95** — three of its seven repos cannot go green.

## What happened

isaac-ruom added `demands-a-field?` and this line to `annotation-errors*`:

    value (if (and (nil? value) (= :map (:type spec)) (demands-a-field? spec)) {} value)

Validation now descends into an **absent** map whenever that map's schema
declares any field carrying a `:present?` validation. The intent is sound and
its docstring states it: omitting `:defaults :frequencies` is omitting the
default crew, so the error should name the missing field rather than pass
silently.

But the rule is **global**, and it conflates two different things:

- *"if this map is present, these fields are required"* — what `:present?` on an
  inner field means;
- *"this map is required"* — which the map spec itself would have to declare.

So every optional nested map whose inner fields are `:present?` now errors when
the map is left out.

## Collateral, confirmed

- **isaac-episodes** — `:episodes :embedding`, described in its own manifest as
  `"Optional embedding capability"`, inner `:api` and `:model` both `:present?`.
  Any config without `:embedding` fails with `episodes.embedding.api is
  required`. 15 feature failures.
- **isaac-http** — `:http :auth :principals <id> :previous`, inner `:hash`
  `:present?`. Every principal without a rotation overlap fails with
  `http.auth.principals.<id>.previous.hash is required`, breaking `auth-cli
  mint!` / `rotate`. 6 spec failures.
- **isaac-hooks** — the same error rejects the hot reload after
  `persist-principal!`, so the two isaac-4o6r scenarios 401 instead of 202/403.

Bisected in isaac-http with only the foundation pin moving: green at `7b2f053`
(isaac-49zp), red at `97da637` (isaac-ruom). Reproduces with the pre-migration
fixtures in place, so it is not the `:defaults` move.

## Live exposure — latent, not yet firing

Both hosts survive by configuration rather than by design, and this was checked
rather than assumed:

- **zanebot** (running foundation `97da637`) configures `episodes.embedding` and
  has no `:principals` block. Validates OK.
- **yopp** configures `episodes.embedding` and has `:auth {:principals {}}` —
  empty, so no entry triggers `:previous`.

Any install that omits `episodes.embedding`, or adds a principal without a
rotation overlap, breaks on load.

## Proposed fix

Narrow `demands-a-field?` from "an inner field carries a `:present?`
validation" to "an inner field is `:required? true`". Verified against all three
schemas:

| schema | marker | descends? |
|--------|--------|-----------|
| `:defaults :frequencies` → `:crew` | `:required? true` **and** `:present?` | yes — ruom's intent preserved |
| `:episodes :embedding` → `:api` / `:model` | `:present?` only | no — fixed |
| `:http … :previous` → `:hash` | `:present?` only | no — fixed |

The alternative — relaxing `:present?` in each module's manifest — is wrong:
those fields genuinely are required *when the map is present*, and it would
spread the workaround across every downstream repo instead of fixing the rule.

## Acceptance

- An absent optional map whose inner fields are only `:present?` produces no
  error.
- An absent map with a `:required? true` inner field still reports that field,
  so omitting `:defaults :frequencies` still names the missing default crew.
- isaac-episodes, isaac-http and isaac-hooks go green against the new
  foundation sha with their isaac-0r95 branches.
- Spec coverage for both shapes in `spec/isaac/config/validation_spec.clj`.

## Landing note

Fixing this re-lands isaac-foundation, which means isaac-agent repins and the
shas isaac-0r95's remaining repos target change. Sequence: land this, repin
agent, then hand the new pair of shas to isaac-0r95.
