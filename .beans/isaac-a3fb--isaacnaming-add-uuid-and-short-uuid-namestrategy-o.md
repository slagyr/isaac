---
# isaac-a3fb
title: 'isaac.naming: add :uuid and :short-uuid NameStrategy (optional prefix)'
status: completed
type: feature
priority: normal
created_at: 2026-06-25T23:43:37Z
updated_at: 2026-06-26T00:06:43Z
---

Add two stateless id strategies to isaac.naming (isaac-foundation/src/isaac/naming.clj), alongside SequentialStrategy + AdjectiveNounStrategy. UUIDs are collision-free without a shared .counter, so concurrent/external producers can mint ids independently (no counter-file race) — enables distributed hail producers (cf isaac-ugx7).

## Strategies (both implement NameStrategy; both stateless — no root/counter/fs)
- **UuidStrategy [prefix]** -> `<prefix?>` + a full random UUID, e.g. `(str (java.util.UUID/randomUUID))` = 550e8400-e29b-41d4-a716-446655440000.
- **ShortUuidStrategy [prefix]** -> `<prefix?>` + the UUID's FIRST 8-hex group (leading segment, e.g. 550e8400). Good entropy for hail volumes; revisit length (12 hex) if collisions ever matter.
- **Optional prefix:** the constructor takes a prefix that may be nil/blank -> no prefix prepended. Hail mints BARE uuids (no prefix — uuids are globally unique, prefix is pointless). Prefix support stays for any domain that wants it.

## Spec examples (spec/isaac/naming_spec.clj — speclj, mirroring the existing SequentialStrategy specs; assert FORMAT + uniqueness + optional prefix since output is random)
UuidStrategy:
- satisfies NameStrategy
- generates a bare full UUID: matches ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ for (->UuidStrategy nil)
- prepends an optional prefix: ^item-<uuid>$ for (->UuidStrategy "item-")
- unique each call: (should-not= (generate s) (generate s))
ShortUuidStrategy:
- satisfies NameStrategy
- generates a bare 8-hex short id: matches ^[0-9a-f]{8}$ for (->ShortUuidStrategy nil)
- prepends an optional prefix: ^item-[0-9a-f]{8}$
- distinct across calls: 5 generates all distinct

NOTE: unlike @wip gherkin, these specs reference not-yet-existing records, so they CANNOT be committed before the impl (would break compilation). Implementer writes the strategy + specs together.

## Acceptance
- isaac.naming exposes UuidStrategy + ShortUuidStrategy, both satisfying NameStrategy, both honoring an optional (nil-able) prefix.
- :uuid -> bare full UUID; :short-uuid -> bare 8-hex; with a prefix -> prefixed.
- naming_spec covers both per the examples; green.

## Scope / follow-up (separate, NOT this bean)
This adds the strategies only. Switching HAIL to use UuidStrategy (bare) instead of SequentialStrategy "hail-" is a deliberate follow-up (changes the on-disk id format; decide coexistence with existing hail-N records). Surfaced 2026-06-25, Micah.

## Worker notes (work-1, 2026-06-25)

isaac-foundation @ `f9a84f8`.

- `src/isaac/naming.clj`: added `UuidStrategy [prefix]` (full random UUID) and `ShortUuidStrategy [prefix]` (UUID's leading 8-hex group) — both stateless `NameStrategy` impls (no root/counter/fs). Shared private `with-prefix` treats nil/blank prefix as bare id.
- `spec/isaac/naming_spec.clj`: 9 new specs (satisfies, bare format via re-matches, optional + blank prefix, uniqueness/distinctness) per the bean examples.
- Strategies only — switching hail to bare uuids is the deferred follow-up (not this bean).
- Tests: `bb spec naming_spec` 23/0; `bb ci` spec 776/0, features 117/0.

## Verification (2026-06-26)
- Current GitHub `isaac-foundation` `main` is `f9a84f8`, and the verifier checkout is now correctly reset to that real origin head.
- The delivered surface is present on current head:
  - [src/isaac/naming.clj](src/isaac/naming.clj) includes `UuidStrategy` and `ShortUuidStrategy`
  - `grep -c UuidStrategy src/isaac/naming.clj` -> `2`
- Proofs are green on current head:
  - `bb spec spec/isaac/naming_spec.clj` -> `23 examples, 0 failures`
  - `bb ci` -> `776` spec examples, `0` failures; `117` feature examples, `0` failures
- My earlier failure note was a verifier-environment mistake caused by the stale local `isaac-foundation` checkout pointing at the wrong origin and old content.
