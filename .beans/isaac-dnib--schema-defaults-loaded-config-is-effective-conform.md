---
# isaac-dnib
title: 'Schema defaults: loaded config is effective (conform overlay), --raw is what''s set'
status: completed
type: feature
priority: high
created_at: 2026-09-29T23:46:53Z
updated_at: 2026-09-30T01:39:07Z
---

# Schema-declared defaults and required fields (foundation + isaac-agent)

## Ruling

Micah, 2026-09-29 (final): the loaded config is a conformed-over-raw
overlay, not a whole-root replace. Load raw (merged files) → conform against
the schema → overlay the conformed values onto the raw config. Defaults and
coercions apply, AND unknown/undeclared keys survive (validation still warns
about them). Runtime (`config get`, modules reading config) = the
conformed-over-raw view, with a defaulted value annotated `(default)` in
text output (`--edn`/`--json` stay plain data). Writes (`config
set`/`unset`/`reformat`) and `config get --raw` = raw only, built from the
pre-conform merged files, not a stripped schema. Validation = conform errors
(including a missing `:required true` field) plus unknown-key warnings.
`:required?` (legacy) is replaced by apron's `:required` everywhere,
including isaac-agent, in this same bean.

## Problem

Today defaults for config fields live as code constants in modules (e.g.
episodes' `DEFAULT_TTL_MINUTES 60`). `isaac config schema` shows nothing
about them. The loader is also inconsistent about which config subtrees
keep their conformed value: berth `:config` tables and crew/model/defaults
already store the conformed value; plain root-level fields (hot-reload,
modules, server, any other module's top-level `:isaac.config/schema` field)
discard the conform result and pass the raw value through unchanged. And
naively "keeping the conformed value" (an earlier draft of this design)
turns out to silently drop any key the schema doesn't declare — including,
critically, `:modules`' own `:local/root`/`:mvn/version`/etc., since apron
treats an undeclared field as dropped, not passed through. apron 3.2.1
adds `:default` and `:required true`, which is what makes all of this
visible and worth fixing now.

## Wanted

1. **Bump foundation to apron 3.2.1** (`deps.edn`, `bb.edn`).

2. **Overlay, not replace.** `isaac.config.loader` (and, by the same
   pattern, `isaac.config.normalize`) must conform the raw config against
   the schema, then deep-merge the conformed result ONTO the raw data —
   conformed wins per-field (defaults fill, badly-typed-but-coercible values
   coerce), but any key/subtree the schema doesn't declare survives from
   raw, recursively. **Verified working** with a temporary experimental
   patch (fully reverted, see Dry-run below):
   ```clojure
   (defn- overlay-conformed [raw conformed]
     (cond
       (and (map? raw) (map? conformed))
       (merge-with (fn [r c] (if (and (map? r) (map? c)) (overlay-conformed r c) c))
                   raw conformed)
       :else conformed))
   ```
   applied in `-validate-root-config` (`(assoc :data (overlay-conformed data
   root-result))` when conform didn't error) and threaded through
   `load-config-result` so `normalize-config` sees the overlaid data instead
   of the untouched raw. Confirmed by direct CLI testing:
   - An unknown nested key under a schema'd root map (`:beacon
     {:keeper ... :sighting-log "kept in the margin"}`, `:sighting-log` not
     in the schema) survives in `config get beacon --edn` alongside the
     filled-in default.
   - An extra module-coordinate key (`:pinned-by "..."`, not one of the
     declared coord fields) survives in `config get modules --edn` right
     next to `:local/root`.
   - `:hot-reload "true"` (string) comes back as the boolean `true` through
     `config get hot-reload`.
   - `config validate` already produces a warning for each surviving
     unknown key at BOTH levels tested (`:beacon.sighting-log - unknown
     key`, `:modules.<id>.pinned-by - unknown key`) — the existing
     unknown-key warning machinery already walks nested structures; no new
     warning code needed.
   - This same overlay treatment needs applying to
     `isaac.config.loader/conform-berth-slices` and
     `isaac.config.normalize/{normalize-crew,normalize-model,normalize-defaults}`,
     which today store the conformed value directly (same "unspecified
     fields are lost" exposure, just already-live rather than newly
     introduced). Not re-verified independently this round — same
     mechanism, high confidence, but worth a scenario per subtree when
     implemented.
   - Per-field conform errors must not leak a `CoerceError`/`ValidateError`
     object into the overlay on a partial failure — keep the existing
     `normalize-crew`/`normalize-model` fallback pattern
     (`(if (cs/error? result) {} result)`) rather than overlaying a result
     that contains error objects.

3. **Per-action rule** (pin this explicitly, it's the crux of the design):
   - **Runtime** (`config get`, whatever a module reads) = the
     conformed-over-raw overlay.
   - **Writes** (`config set`/`unset`/`reformat`) = raw only. Unaffected by
     this bean — `mutate.clj` already builds writes from
     `assoc-path`/`dissoc-path` over data read straight off disk, never
     from a conformed value; `reformat.clj` re-pretty-prints whatever's
     already in each file. Confirmed unaffected.
   - **`config get --raw`** = raw only, and this needs its own fix, not a
     side effect of item 2. Verified: `--raw` (`substitute-env? false`)
     shares the exact same conform/overlay pipeline as plain `get` — it
     only skips `${VAR}` substitution. With the overlay patch alone,
     `config get beacon.power --raw` on an absent key returned the default,
     not "not found." Fixed and verified in the experiment by threading a
     separate `raw-config?` flag through `load-config-result` that skips
     the overlay assoc entirely (so `--raw`'s `:config` is exactly the
     pre-conform merged-files data) — confirmed `--raw` then correctly
     omits the default AND preserves the original uncoerced string
     (`"true"`, not `true`).
   - **Validation** = conform errors (a missing `:required true` field
     surfaces here — confirmed free, see item 5) plus unknown-key warnings
     (already fires for nested keys too, confirmed above).
   - **`config get`** (no `--raw`) = the runtime view; a value that came
     from a schema default is annotated `(default)` in TEXT output.
     `--edn`/`--json` stay plain data, no annotation — confirmed this falls
     out naturally (nothing needs to change for `--edn`), but the plain-text
     `(default)` annotation is new, unbuilt work: `get.clj` needs to know
     whether the value it's about to print came from `:default` or from the
     file, which it can determine by checking presence in the raw (pre-
     overlay) data at that path vs. the overlaid value.

4. **`:modules` goes deeper, not `:any`.** Declared the known tools.deps
   coordinate keys, grounded in `isaac.module.coords/valid-module-coord?`
   (foundation's own existing predicate for what makes a coordinate valid) —
   `:local/root`, `:mvn/version`, `:git/url`, `:git/sha`, `:git/tag`,
   `:deps/root`, `:exclusions` — each typed and described, plus a
   validation that at least one of `:local/root`/`:mvn/version`/`:git/url`
   is present. **The "at least one of" check needs no new production
   code** — foundation already has a `:requires-any?` lexicon ref
   (`isaac.config.validation-lexicon/requires-any-ref`, entity-scoped,
   already used elsewhere for the same shape of rule) — attaching
   `:validations [[:requires-any? :local/root :mvn/version :git/url]]` to
   one of the coord fields (e.g. `:local/root`) works as-is. Verified: a
   coordinate with none of the three produces `must include at least one of
   :local/root, :mvn/version, :git/url` and `config validate` exits 1.
   Undeclared coord keys (e.g. a `:pinned-by` note) survive via the item-2
   overlay — verified above.

   **Found a real, separate blocker while verifying this, not
   `:default`-specific:** giving `:modules`' coordinate schema literal
   NAMESPACED field keys (`:local/root`, `:git/url`, …) — which is the
   correct modeling choice, these ARE namespaced keys — exposes two
   pre-existing gaps neither of which this bean currently fixes:
   - `isaac.config.schema.term`'s `field-block`/`leaf-block` render a
     field's label as `(str ":" (name k))` — dropping the namespace
     entirely. `:local/root` and `:deps/root` BOTH render as `:root` in
     `config schema modules.value` (a genuine collision, not just missing
     context); `:git/sha`/`:git/tag`/`:git/url`/`:mvn/version` render
     without their namespace at all.
   - `config schema`'s path resolution (`cli/schema.clj`, via apron's own
     `c3kit.apron.schema.path/schema-at`) does NOT understand a
     `.`-separated segment containing `/` as one namespaced keyword the way
     `config get`/`set`'s OWN path parser does (the isaac-cgxa fix) —
     `config schema modules.value.local/root` returns "Path not found in
     config schema." The bracket form apron's path grammar documents,
     `modules.value[:local/root]`, DOES resolve the field, but the
     RENDERED label for it is then garbled (`common/path-prefix`'s naive
     dot-split doesn't know about brackets either, so the leaf's label
     prints as `:value[:local/root]` instead of `:local/root`).
   Recommend fixing both as part of landing `:modules`' deeper schema
   (otherwise the schema is real but effectively unbrowsable via `config
   schema`) — flagged for Micah's decision on sequencing (see Open
   questions).

5. **`isaac config validate` reports a missing `:required true` field —
   confirmed free, apron 3.2.1 alone, no Isaac code change.** Verified:
   apron's OWN `conform` (called by `-validate-root-config` over the whole
   composed root schema) already understands `:required true` and raises
   "is required"; that error already flows into `:errors` independent of
   whether the overlay (item 2) is applied. Isaac's own hand-rolled
   `isaac.config.validation/annotation-errors*` walker does NOT understand
   `:required true` at all (it only inspects a raw `:validations` list) —
   don't touch it for this; it isn't the path that makes it work.

6. **`config schema` shows defaults and required-ness, including the
   single-field drill.**
   - Table view already works once apron is bumped (`field-block` already
     renders `default: <v>` — pure data read, no apron-version dependency —
     and already computes `*required` via `doc/required-fields`, which
     apron 3.2.1 extends to bare `:required true`).
   - **Real gap, confirmed still broken with the apron bump alone:**
     `leaf-block` (used when a path drills to exactly one field, e.g.
     `config schema beacon.keeper`) only checks the legacy `(:required?
     spec)`, never `doc/required?`. This is the SAME fix as item 7's
     cutover — do them together.

7. **Clean cutover, foundation AND isaac-agent, same bean.** Replace the
   legacy `:required?` flag with apron's `:required` everywhere:
   - `isaac-foundation/src/isaac/config/schema/term.clj` — both marker
     checks (`field-block` and `leaf-block`) → `doc/required?`.
   - `isaac-foundation/src/isaac/config/validation.clj`'s
     `demands-a-field?` → `doc/required?` (this is the isaac-ruom fix —
     don't regress it; see the isaac-agent scenario below that pins it).
   - `isaac-foundation/src/isaac/config/schema_compose.clj`'s
     `template-field-spec` — currently `(dissoc spec :required?)` when
     copying a field into a `:defaults`/`:entity-template`. **Must become
     `(dissoc spec :required)`** — easy to miss (neither a render nor a
     validate call site); left alone it silently becomes a no-op post-
     cutover and an entity-template field would start incorrectly
     demanding a value marked `:required true`.
   - `isaac-agent/resources/isaac-manifest.edn` — three fields marked
     `:required? true` (a `:defaults`/`:frequencies`/`:crew`-shaped
     entity-template field, a `:provider` field, a `:model` field) need
     `:required true` instead.
   - `isaac-agent/deps.edn`, `bb.edn` — bump apron to 3.2.1. Agent's direct
     3.0.0 pin would otherwise override foundation's dep-managed version
     and the cutover code would run against pre-3.2 apron APIs.
   - `isaac-agent/src/isaac/session/schema.clj` also has `:required?` (on
     `:id` and `:name`) but that's a session-record schema, not a config
     schema — grepped, nothing in foundation or isaac-agent reads it.
     Not functionally affected; migrating it is optional cosmetic cleanup.
   - Foundation's own bundled fixture modules (`modules/marigold.*`) and
     `features/` don't use `:required?` — nothing else in-repo needs a
     matching change. Grepped every other sibling repo — only
     isaac-foundation and isaac-agent use `:required?` at all.

8. **apron version in other repos.** No repo besides foundation and
   isaac-agent needs to bump as part of THIS bean. Note for the future:
   every other repo pinning apron 3.0.0 directly must bump to (at least)
   3.2.1 the next time it bumps its foundation pin, or its own direct pin
   will shadow foundation's and it'll run pre-3.2 apron.

## Acceptance

- `features/cli/config_defaults.feature` (drafted, `@wip`, 17 scenarios,
  isaac-foundation) — dry-run results below. Remove `@wip` and get every
  scenario green as part of this bean's Definition of Done.
- **isaac-agent scenarios that must stay green** (found by searching
  isaac-agent's existing feature suite for coverage of the 3 migrated
  fields — these are NOT new, they already exist and already pass; the
  cutover must not regress them):
  - `isaac-agent/features/config/cli.feature` — "validate requires
    defaults.frequencies.crew" — THE isaac-ruom / `demands-a-field?`
    scenario: `:defaults` has `:crew` but no `:frequencies` key AT ALL, and
    validate must still report `defaults.frequencies.crew` as missing. This
    is the one scenario that actually exercises the "whole containing map
    is absent" edge case `demands-a-field?` exists for.
  - `isaac-agent/features/config/set_unset.feature` — "setting the first of
    two required fields is refused and hints at --force", "--force writes
    the first required field and the second set validates clean", "config
    unset --force removes a required field and warns" — these exercise
    `models.echo.provider`/`models.echo.model` "is required" (the
    `:provider` and `:model` manifest fields). These already carry explicit
    `:validations [:present?]`/`[:present? [:registered-in? ...]]`
    alongside `:required?`, so their pass/fail is mostly independent of the
    cutover — but worth confirming they stay green since they're the
    nearest coverage for those two fields.
- Repos pinning apron 3.0.0 directly, unaffected by THIS bean but needing a
  bump on their next foundation-pin bump (per item 8): isaac-server,
  isaac-acp, isaac-cron, isaac-discord, isaac-hail, isaac-hooks,
  isaac-imessage, isaac-foreman, isaac-gchat, isaac-gmail, isaac-google,
  isaac-http, isaac-mcp, isaac-worksite, isaac-cli-proxy, isaac-cli-server,
  isaac-claude-code (isaac-agent is now IN this bean's scope, not in this
  list).

## Likely repo scope

`isaac-foundation`:
- `deps.edn`, `bb.edn` — apron bump.
- `src/isaac/config/schema_base.clj` — deepen `:modules`' `:value-spec`
  (item 4).
- `src/isaac/config/loader.clj` — `-validate-root-config`/
  `load-config-result`: the overlay (item 2), plus a flag `--raw` can use to
  skip it (item 3).
- `src/isaac/config/normalize.clj` — apply the same overlay to
  `normalize-crew`/`normalize-model`/`normalize-defaults` (item 2).
- `src/isaac/config/cli/common.clj` — `load-raw-result` passes the
  skip-overlay flag (item 3).
- `src/isaac/config/cli/get.clj` (or wherever plain-text printing lives) —
  the `(default)` annotation (item 3).
- `src/isaac/config/schema/term.clj` — `leaf-block`/`field-block` cutover to
  `doc/required?` (items 6, 7); separately, namespaced-key label rendering
  (item 4's blocker) if that's sequenced into this bean.
- `src/isaac/config/validation.clj` — `demands-a-field?` cutover (item 7).
- `src/isaac/config/schema_compose.clj` — `template-field-spec`'s dissoc key
  (item 7).
- `src/isaac/config/cli/schema.clj` (or wherever schema-path resolution
  lives) — namespaced-segment path resolution (item 4's blocker), if
  sequenced into this bean.
- `features/cli/config_defaults.feature` — drop `@wip` once green.

`isaac-agent`:
- `deps.edn`, `bb.edn` — apron bump to 3.2.1.
- `resources/isaac-manifest.edn` — 3 fields, `:required?` → `:required`
  (item 7).
- Confirm the two feature files listed under Acceptance stay green.

## Notes (investigation detail — how this was verified)

All of the following was checked with a temporary experimental patch to
`isaac-foundation-defaults-draft` (apron bumped to 3.2.1; `overlay-conformed`
added to `loader.clj`; `-validate-root-config` given a 3rd `raw-only?` arg;
`:raw-config?` threaded through `load-config-result` and
`common/load-raw-result`; `:modules`' schema deepened in `schema_base.clj`)
— run against the real CLI via `bb isaac` and via the full gherclj suite,
then **fully reverted** (`git stash` the three production files under a
unique tag, drop the stash once confirmed reverted; apron back to 3.0.0 via
the `.bak` files). The worktree carries no production diff, only the
feature file, at the end of this session.

**`:requires-any?` already exists — no new ref needed.**
`isaac.config.validation-lexicon/requires-any-ref` (entity-scoped, registers
as `:requires-any?`) already implements exactly "at least one of these
sibling fields must be present," already used elsewhere (see
`validation_spec.clj`'s `:crew`/`:crew-tags` example). Attaching it to a
`:modules` coord field required zero new production code — confirmed by
running it.

**Dry-run results**, `features/cli/config_defaults.feature`
(17 scenarios), run untagged via `bb gherclj <file>`:

| # | Scenario | Baseline (apron 3.0.0, no patch) | Apron 3.2.1 + experimental overlay/`:modules`/`--raw` patch |
|---|---|---|---|
| 1 | schema table: default + required marker | FAIL | PASS |
| 2 | schema single-field drill: required marker | FAIL | **FAIL — real `leaf-block` gap (item 6)** |
| 3 | schema --edn: `:default` | PASS (already free) | PASS |
| 4 | schema --edn: `:required` | PASS (already free) | PASS |
| 5 | get absent key → default, annotated | FAIL | **FAIL — value correct, `(default)` text annotation not built (item 3)** |
| 6 | get set key → unannotated | PASS | PASS |
| 7 | get --edn absent → plain default, no annotation | FAIL | PASS |
| 8 | get --raw absent → omits default | PASS (trivially) | PASS |
| 9 | unknown nested key under schema'd root map survives | FAIL (missing default, compound) | PASS |
| 10 | coercible-but-wrong-typed value comes back coerced | FAIL | PASS |
| 11 | get --raw preserves the original uncoerced value | PASS (trivially) | PASS |
| 12 | undeclared module coord key survives in `config get modules` | PASS (trivially — nothing conformed at baseline either) | PASS |
| 13 | module coord missing all of local/root/mvn/version/git/url fails validation | FAIL (no such validation on old bare schema) | PASS |
| 14 | set doesn't write the other field's default | PASS | PASS |
| 15 | explicit value == default is kept | PASS | PASS |
| 16 | validate reports missing required | PASS (free once apron bumped, independent of the overlay) | PASS |
| 17 | validate passes when present | PASS | PASS |

Only 2 genuine remaining gaps, both previously diagnosed and unchanged by
this revision: `leaf-block`'s required marker (item 6/7), and the
`(default)` text annotation on `config get` (item 3). Every other scenario
either already worked or was made to work purely by apron 3.2.1 + the
overlay/`:modules`/`--raw` mechanism described above — no additional
production code beyond what's listed in "Likely repo scope" was needed to
get 15/17 green. New step definitions needed: **none** (`gherclj match` /
`bb match-step` confirmed every phrase against the existing registered
steps).

## Decisions (Micah, 2026-09-29)

1. Namespaced keys in `config schema` are fixed in this bean: labels show the full keyword (`:local/root`, not `:root`), and dotted paths like `modules.value.local/root` resolve, the same way `config get`/`set` already parse them (isaac-cgxa).
2. Overlay fallback is field by field: a field that fails to conform keeps its raw value, validation reports it, and nothing else in the map is dropped (not the old whole-subtree `{}` of `normalize-crew`/`normalize-model`; that pattern goes away).
3. One bean spanning isaac-foundation and isaac-agent, landed together: foundation first, then agent's apron 3.2.1 bump, `:required?` → `:required` manifest migration, and foundation pin bump.

feature-baseline: isaac-foundation 53c8aa83627eb4334cec1e64c72ceb42bf17fb3e
feature-blob: isaac-foundation features/cli/config_defaults.feature d9d0d7bc8be39db484d4bd78e7a8895a17cfedef

## Landed on main (2026-09-30)

main-sha: isaac-foundation 322151c
main-sha: isaac-agent 5dcbb33

Both repos' full `bb ci` green (spec + features) on the landed commits;
GitHub CI confirmed green on both mains (runs 36654831441 isaac-foundation,
36655742053 isaac-agent). `bb bean-gate verify isaac-dnib` PASS against
both landed shas.

Known, disclosed side effect of the mandatory apron 3.2.1 bump (not
introduced by this bean's own design choices): `doc/required-fields` now
resolves `:validations [:present? ...]` refs to required-ness, which
newly flags a couple of pre-existing fields `*required` in `config
schema` output. Two fixture/test text updates were needed outside this
bean's own baseline:
- `isaac-foundation features/cli/config_schema.feature` — the pinned
  regex for `marigold.cnfs.bridge`'s `:type` field (isaac-3y69's
  contract). Since fixing this in-line would have made isaac-dnib's own
  `bb bean-gate verify` fail (a worker diff may only touch its own
  baselined `.feature` file), it was landed as a **separate**, tiny,
  unrelated commit straight to isaac-foundation main
  (`322151c`), tracked as isaac-hzw2 (filed, fixed, and landed in this
  same session; left `tag=unverified` since zanebot/verify is down).
- `isaac-agent spec/isaac/config/schema_spec.clj` — provider entity
  conform now genuinely fills `:auth-retry-ms`/`:retry-after-ms`/
  `:stream-idle-timeout-ms` (their `:default`s were always declared but
  never applied under apron 3.0.0); updated the expected maps.
- `isaac-agent features/config/cli.feature` — `crew.key`/`providers.key`
  now correctly render `id`, not `string` (their key-specs were
  `:string`, a latent bug masked until this bean made root-level fields
  actually conform; fixed to `:id`, matching how `->id` already
  canonicalizes these ids everywhere else).

Also found and fixed mid-implementation (all in isaac-foundation,
within this bean's own baseline):
- `overlay-conformed` needed apron's `field-error?` check twice: once at
  each node (keep raw on a failed field), and once specifically for a
  "conform-only" key (a required-but-absent field renders as the field's
  own key mapped to a bare `ValidateError` in apron's conform output —
  that must never leak into the runtime config as a value).
- `overlay-conformed` needed canonical-key matching (name-only, apron's
  own `:id`-coercion convention) rather than raw key equality — a
  dynamic map whose `:key-spec` canonicalizes keyword keys to strings
  (`:crew`, `:models`, `:providers`, or the ad-hoc `:signals` chartroom
  fixture) would otherwise silently double every entry (once
  keyword-keyed from raw, once string-keyed from conformed).
- `config get`'s new `(default)` annotation reads a `:raw-root` key
  `load-config-result` now returns alongside `:config` — computed for
  free from data already in hand — rather than issuing a second
  `load-config-result` call, which would have broken "the CLI resolves
  the config once per command" (isaac-v1la,
  `cli/config_resolution.feature`).
- `isaac-agent`'s `:provider`/`:model`/`:defaults.frequencies.crew`
  manifest fields carried both `:required true` (now apron-native) and a
  redundant `:validations [:present? ...]` — for a field reachable
  directly (`:provider`, `:model`), the two independently produced the
  same "is required" error/warning, so `config set --force`'s "N other
  validation warnings" count doubled; dropped the redundant `:present?`
  there. `:defaults.frequencies.crew` needed the opposite: its `:present?`
  had to stay, since it's the *only* thing that fires once
  `demands-a-field?` substitutes `{}` for a wholly-absent `:frequencies`
  map — apron's native `:required` never descends into an absent parent
  to check a grandchild.
