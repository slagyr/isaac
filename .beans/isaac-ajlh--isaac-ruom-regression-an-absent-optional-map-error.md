---
# isaac-ajlh
title: 'isaac-ruom regression: an absent optional map errors when its inner fields are :present?'
status: completed
type: bug
priority: high
created_at: 2026-09-23T21:51:54Z
updated_at: 2026-09-23T22:15:58Z
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

## Implemented (worker, 2026-09-23)

The proposed narrowing was taken as written — confirmed against the three
schemas before implementing, and it holds.

`demands-a-field?` now asks "does this map spec declare a `:required? true`
field?" rather than "does an inner field carry a `:present?` validation?".
Why that marker and not a new one:

- The new rule is a **strict subset** of ruom's. Every `:required? true`
  field in the composed schema also carries `:present?`, so nothing that
  used to be skipped now descends — the change can only remove errors,
  never add them.
- `:required? true` appears in exactly three places across every manifest
  (all in isaac-agent): the `:defaults :frequencies` `:override` on `:crew`,
  and `:provider` / `:model` on the `:models` `:value-spec`. The latter two
  sit under a `:value-spec`, which `demands-a-field?` never inspects, so
  `:defaults :frequencies :crew` is the only field that keeps the descent.
  ruom's intent is preserved exactly and nothing else changes behaviour.
- `schema-compose/template-field-spec` already strips `:required?` and
  `:present?` from every field an `:entity-template` copies in ("a default
  is a template … templates therefore never require a field"). So the only
  `:required?` that survives into `:defaults` is the explicit `:override`
  ruom wrote for `:crew` — the marker is already load-bearing in exactly
  the place this rule needs it.

No c3kit change was needed or made. The whole fix is six lines of
`isaac/config/validation.clj` plus its docstring.

Spec coverage added to `spec/isaac/config/validation_spec.clj`
("absent nested maps", 6 examples): the absent `:present?`-only map, the
absent `:required?`-bearing map, a written map still requiring its
`:present?` fields, a parent that may omit an optional child but not a
demanding one, and the isaac-http principal `:previous` shape. Three of
the six were red before the fix.

### Verification

Downstream ran on their pushed `bean/isaac-0r95` branches with foundation
swapped to `:local/root` in both `bb.edn` and `deps.edn`; the overrides
were reverted afterwards, so those repos are untouched and still need
isaac-0r95's repin.

| repo | before | after |
|------|--------|-------|
| isaac-foundation | — | specs 1218 / 0; features 219 / 2 |
| isaac-episodes | features 85 / **15** | features 85 / 0; specs 215 / 0 |
| isaac-http | specs 193 / **6** | specs 193 / 0; features 111 / 0 |
| isaac-hooks | features 20 / **2** | features 20 / 0; specs 30 / 0 |
| isaac-agent | — | specs 1717 / 0; features 848 / 0 (1 pending) |

Foundation's 2 feature failures are the pre-existing `modules_pins`
stale-`~/.gitlibs` pair, unchanged from main. ruom's intent re-confirmed by
`features/config/cli.feature` "validate requires defaults.frequencies.crew",
green against the new foundation.

Known-red `jvm-spec` unchanged, and none of it is ours: foundation 1218 / 8,
all `module lifecycle` / `isaac.module.protocol` (isaac-jf80, isaac-3rxx);
agent 1717 / 4, all `comm berth`.

### Landed

main-sha: isaac-foundation 9ab25271aedb97c2e2e8abbc6a58955cdff8f274
main-sha: isaac-agent da9214aa72786fd830847c5542f5ea7781a44410

isaac-agent is repinned to the foundation sha in `bb.edn` (5 sites) and
`deps.edn` (13 sites). Note that agent main had also gained
`1d9c49f Merge hotfix/isaac-dgod-agent-0.1.81 into main` while this was in
flight — the repin sits on top of it, and `bb verify` was re-run against
that exact tip. That merge also settles isaac-0r95's "open decision at
step 4: the agent version line".

These are the shas isaac-0r95's remaining three repos should target.

## Verified and closed (2026-09-23, planner)

Checked independently rather than taken on report:

- `demands-a-field?` at isaac-foundation `9ab2527` tests `:required?`, and its
  docstring now states the distinction it got wrong before.
- **The strict-subset claim holds.** `:required? true` appears in exactly three
  places across every manifest in the tree, all in isaac-agent, and each also
  carries `:present?` — so the narrowed rule can only remove errors, never add
  them. Two of the three (`:models` `:provider` / `:model`) sit under a
  `:value-spec`, which `demands-a-field?` never walks, leaving
  `:defaults :frequencies :crew` as the only field that keeps the descent.
  ruom's intent is preserved exactly.
- isaac-agent `da9214a` carries the repin, and `1d9c49f` (the hotfix merge) is
  an ancestor of it — so the agent line still declares 0.1.81 and step 4 of the
  train no longer regresses the version.

The worker ran the three downstream repos **before** the fix as well as after,
reproducing 15 / 6 / 2 and then clearing them. That before-measurement is what
this bean existed to force: isaac-ruom shipped because I verified it on
foundation's and agent's own suites, where the regression is invisible.
