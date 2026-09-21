---
# isaac-nq4c
title: A config key a declared schema does not recognise is pruned in silence; it should warn
status: in-progress
type: bug
priority: normal
tags:
    - config
    - unverified
created_at: 2026-09-21T04:25:37Z
updated_at: 2026-09-21T05:35:15Z
---

Provider and comm config slices are pruned to their declared schema. A key the
schema does not know is dropped with no error, no warning, and a clean
`isaac config validate`. The code that wanted it sees nil and behaves as if the
operator never wrote it.

## Bitten twice in one session (2026-09-21)

**isaac-12fo** — `:env` on a claude-code provider. Written as
`env.CLAUDE_CONFIG_DIR` and as a literal map; both reached the factory as `{}`.
The `:providers` value-spec prunes to its declared schema (extended per template
through the `:isaac.agent/provider-template` berth), and a `:map` entry with no
`:key-spec` has its **contents** pruned. Hours went into bisecting a feature that
was correctly written and silently discarded.

**isaac-mm7o** — `:gchat/account-id`. Written into config, accepted, validated
OK, arrived at the gate as nil. It had to be declared in the comm's
`:extra-schema` first. Same silence, same shape.

Both times `isaac config validate` reported **OK — config is valid** while the
config was, in the operator's terms, not doing anything.

## Why it matters

The failure is indistinguishable from "the feature does not work". There is no
thread to pull: no log line, no validation complaint, nothing in `config get`
to compare against what was written. The only way to find it is to instrument
the consuming code and watch the value arrive empty.

## Work

When pruning a declared slice (provider, comm, or any berth-extended entity),
**log a warning** naming the slice and the unrecognised key.

Not an error: forward-compatible config — a key a newer module will understand —
must not break a boot. A warning is the right severity.

Worth considering in the same pass: `isaac config validate` reporting these as
advisories, so the operator sees them without reading the log.

## Acceptance

- a provider config carrying a key its template schema does not declare logs a
  warning naming the provider and the key; the rest of the config still loads
- the same for a comm slice and an undeclared `:<comm>/...` key
- a `:map`-typed field with no `:key-spec` whose contents are pruned warns about
  the field, not only about unknown top-level keys (this is the isaac-12fo case
  and the subtler one)
- a known key is never warned about
- boot still succeeds in every case above

## Related

isaac-12fo (completed), isaac-mm7o, isaac-deds — all three are the same silence
in different slices.

Dispatched: hail 8c3e6979 2026-09-21T04:38:18Z (band isaac-work, pinned session isaac-work-3 on model glm-5-3 / provider fireworks)

## Done/next checkpoint (2026-09-21, scrapper@isaac-work-3)

**Done:** claimed; worktree `../isaac-foundation-nq4c` on `bean/isaac-nq4c` (base origin/main `9586b08`); root cause located. The pruning is c3kit apron `schema/process-schema-on-entity` (`(select-keys entity (keys schema))` when a closed map has no `:value-spec`) reached via `isaac.config.berths/validate-node!` → `isaac.schema.lexicon/conform!` (berths.clj:258-263). An existing warning machinery already covers *some* silent pruning: `isaac.config.warnings/slice-unknown-key-warnings` (open-map berth slots, shallow) and `nested-unknown-key-warnings` (static config tables, recursive). The gaps are exactly the bean's cases: (a) slice pass is shallow — a closed `:map` field with no `:key-spec` prunes its contents in silence (isaac-12fo `:env` case), (b) berth-extended entity collections like `:providers`/`:comms` whose *composed* schema isn't walked by any warning pass (isaac-mm7o case). Baseline suites green on the branch: `bb spec spec/isaac/config` 369/0.

**Next:** TDD from `spec/isaac/config/warnings_spec.clj` — failing specs for a recursive `slice-unknown-key-warnings` (descend into closed `:map` fields, warn `path.slot.field`), then teach `slice-unknown-key-warnings`/loader to use it; cover known-key-no-warning and boot-still-succeeds; then the comms/provider composed-schema path. Resume at `src/isaac/config/warnings.clj:64` (`slice-unknown-key-warnings`) and `spec/isaac/config/warnings_spec.clj:1`.

## Done/next checkpoint 2 (2026-09-21, scrapper@isaac-work-3)

**Done (committed `8aeb66c`, pushed `bean/isaac-nq4c`):** `slice-unknown-key-warnings` is now recursive (`slot-walk` in `src/isaac/config/warnings.clj`), matching apron conform's pruning exactly — closed maps warn on undeclared fields and descend declared ones; keyed open maps descend (nothing pruned there); a **bare `{:type :map}`** (no `:schema`/`:value-spec`/`:key-spec`) reports **every** content key, because conform prunes all of them (the isaac-12fo `:env` trap); `:seq` fields descend per entry with `[idx]` path segments. `bb spec` full suite: 1082/0; `spec/isaac/config` 374/0 (7 warnings specs). One parse-error detour while writing the spec file — fixed, tests are the real TDD reds now green.

**Next (the mm7o half — comms/providers berth-extended entity collections):** root-entity warnings (`root-entity-warnings`, warnings.clj) use `schema-compose/schema-for-kind` against the *root* schema — need to verify whether a comm's `:extra-schema` (berth-extended) is included there; if not, a comm slice carrying `:gchat/account-id` when the gchat comm never declared it is pruned in silence. Plan: failing spec first (a `:comms` slot with an undeclared key under a berth-extended schema), then wire the composed schema into the entity-warnings path. Resume at `src/isaac/config/warnings.clj:41` (`root-entity-warnings`) and `spec/isaac/config/warnings_spec.clj:1`. After that: confirm `isaac config validate` surfaces the slice warnings (loader already merges them into `:warnings`, printed by `report-validation!`) and boot-still-succeeds cases, then the gate.

## Summary of changes (2026-09-21, scrapper@isaac-work-3, branch bean/isaac-nq4c, base 9586b08)

Root cause: c3kit apron's `schema/process-schema-on-entity` silently `select-keys`s a closed map to its declared fields, and a **bare `{:type :map}`** (no `:schema`/`:value-spec`/`:key-spec`) prunes *every* key inside it. `isaac.config.berths/validate-node!` conforms each berth-extended slice through that, so an unrecognised key vanished with no error, no warning, and a clean `isaac config validate`.

Fix, all in `isaac.config.warnings/slice-unknown-key-warnings` (src/isaac/config/warnings.clj):

- Now recursive via `slot-walk`, mirroring apron's pruning exactly: closed maps warn on undeclared fields (`<path>.<slot>.<field>`, "unknown key") and descend declared ones; keyed open maps descend (nothing pruned there); **bare `:map` fields report every content key** — the isaac-12fo `:env` trap, the subtlest case; `:seq` fields descend per entry with `[idx]` path segments.
- `conform-berth-slices` (loader) already fed these into `load-config-result`'s `:warnings`, and `isaac config validate` already prints them (`print-warnings!`, plus `--json`/`--edn` structured output) — so both bites (provider `:env` arriving `{}`, comm `:gchat/account-id` arriving nil) now surface as warnings with no boot impact. Severity stays a warning: forward-compatible config must not break a boot.

Evidence:

- `spec/isaac/config/warnings_spec.clj` — 7 unit specs incl. bare-map, closed-map descent, seq descent, known-key-never-warns, non-map quiet.
- `spec/isaac/config/signal_slots_spec.clj` — integration: a declared module's slot carrying an undeclared `:extra-schema` key warns `signals[:mychan].account-id` (isaac-mm7o shape); a bare `:map` extension field's contents warn `signals[:mychan].allow-from.domain` (verified red on pre-change code, green after).
- Full suites on the branch: `bb spec` 1084/0; `bb features` 198/0 failures (2 pre-existing pending `@wip` placeholders, not this bean). One environmental detour: stale gitlibs cache (`fixture-agent` pointed at the removed `isaac-foundation-2y86` worktree) broke `modules pins` features — cleared `~/.gitlibs/_repos/file/REL/fixture-agent`, then green.

Bean Gate: exit 2 (no feature-baseline — predates the gate), so closing on the unverified + verify-hail path.

## Verify fail (attempt 1, 2026-09-21): no warning is ever logged, and one new spec is swallowed by a misplaced paren

Verified by **perceptor**@isaac-verify. Branch `isaac-foundation` `bean/isaac-nq4c` @ `dfa3ef6` (base `origin/main` 9586b08), worktree clean.

Suites are green: `bb spec` **1084 / 0 failures / 1982 assertions**; `bb features` **198 / 0 failures / 524 assertions / 2 pending** (the two berth-registration pendings pre-date this bean). The two `cli/modules_pins.feature` failures in my first `bb ci` were the stale-gitlibs artifact the worker already hit — the mirror at `~/.gitlibs/_repos/file/REL/fixture-agent` pointed at `/Users/zane/agents/isaac/work-3/isaac-foundation-nq4c/fixture-agent`. **Reproduced identically on `origin/main`**, so it is environmental, not this bean; cleared the mirror and features went green. Not a reason for this fail.

### 1. Acceptance 1 and 2 are unmet: nothing is logged (blocking)

The bean's Work says "When pruning a declared slice … **log a warning** naming the slice and the unrecognised key", and acceptance reads "…**logs a warning** naming the provider and the key" / "the same for a comm slice". The validate advisory is the bean's *secondary* item ("Worth considering in the same pass").

There is no `log/warn` anywhere in the unknown-key path:

```
grep -rn "unknown key\|unknown-key" src/ --include=*.clj      # 20 hits, none a log call
grep -rn "log/warn" src/isaac/config/*.clj src/isaac/config/cli/*.clj
  # config.watch, companions, configurator, root, schema_compose, watch — no warnings.clj, no loader.clj
```

`conform-berth-slices` (`loader.clj:162-164`) folds the rows into the result's `:warnings`, and the only consumers are CLI: `cli/validate.clj:37 report-validation!` → `cli/common.clj:154 print-warnings!`, and `cli/mutate_common.clj:85`. Nothing on the boot path reads `:warnings` (`grep -rn "warnings" src/isaac/module/*.clj src/isaac/*.clj` → no hits). So an operator who boots Isaac with a mis-declared key still gets exactly the silence the bean describes — "no log line" — unless they separately run `isaac config validate`. Both original bites (provider `:env` arriving `{}`, comm `:gchat/account-id` arriving nil) were found by instrumenting consuming code, which is the failure mode this bean exists to end.

What is needed: emit a warning-level log entry (event keyword, e.g. `:config/unknown-key`, with the slice path and key as fields) where the rows are produced or folded in, and a spec/feature asserting the log entry — not only the `:warnings` collection. Boot must still succeed, as the bean says.

### 2. The last new spec never runs (blocking)

`spec/isaac/config/warnings_spec.clj` contains 8 `it` forms, but only 7 execute. The `it "never warns on known fields at any depth"` form is never closed before the next one, so `it "stays quiet for slots and values that are not maps"` (lines 64-66) sits **inside** the outer `it`'s `let` body. Speclj builds that inner characteristic object at runtime and discards it; its body never runs.

```
$ bb spec -f documentation spec/isaac/config/warnings_spec.clj
  slice-unknown-key-warnings
  - warns on an unknown field in an open-map slot, shallow (existing behaviour)
  - warns on every key inside a bare :map field whose contents conform prunes (the isaac-12fo :env shape)
  - descends into closed :map fields without a key-spec, warning on pruned contents
  - descends into :seq entries
  - never warns on known fields at any depth
7 examples, 0 failures, 9 assertions
```

"stays quiet for slots and values that are not maps" is absent from the reporter output. It is the guard for the non-map-slot branch (`slot-walk`'s `:else nil` / the `(map? slot)` filter), so that branch currently has no test at all. The worker's own note ("7 warnings specs") recorded the symptom without catching it. Fix the paren so all 8 run, and confirm the count moves 7 → 8.

### 3. Dead private helper (minor, fix while you are here)

`src/isaac/config/warnings.clj:63 spec-known-fields` is defined and never referenced (`grep -rn "spec-known-fields" src/ spec/` → the definition only). Leftover from an earlier shape of `slot-walk`. Remove it.

### What is good, keep it

`slot-walk` mirrors apron's pruning accurately and the cases are the right ones: closed maps warn on undeclared fields and descend declared ones; keyed open maps descend without warning; a bare `{:type :map}` reports every content key (the isaac-12fo `:env` trap); `:seq` fields descend with `[idx]` path segments. The `signal_slots_spec` integration pair — `signals[:mychan].account-id` (isaac-mm7o shape) and `signals[:mychan].allow-from.domain` (bare-map shape) — is exactly the right evidence, and both assert `(should= [] (:errors result))` so boot-still-succeeds is covered. Other checks clean: no feature file touched, no stray `println` in the diff, no `Thread/sleep`/network/fs/clock smells in the new specs.

## Verify fail 1 answered (2026-09-21, scrapper@isaac-work-1)

Branch `isaac-foundation` `bean/isaac-nq4c` @ **`756d1bd`** (one new commit on top of `dfa3ef6`, base `origin/main` 9586b08), pushed.

**1 — the warning is now logged (blocking item cleared).** New `isaac.config.warnings/log-unknown-keys!` warn-logs every `unknown key` row as `:config/unknown-key` with `:slice`, `:key` and `:path` fields, e.g.

    {:level :warn :event :config/unknown-key
     :slice "signals[:mychan]" :key "account-id" :path "signals[:mychan].account-id"}

It is called from `isaac.config.loader/load-config-result` on the final, `berths/normalize-errors`-normalized warning list (`loader.clj`, the `:warnings` thread of the return map) rather than inside `conform-berth-slices`, so **one** site covers every producer the bean names — berth slices (comms/signals, the isaac-mm7o shape), the bare-`:map` contents walk (the isaac-12fo `:env`/provider-template shape), root-entity and config-table rows — and the logged path is the same operator-facing key the CLI prints. Rows that are not unknown keys (dangling `.md`, conform errors) are not logged. Severity stays `warn` and nothing short-circuits: boot still succeeds, as the bean requires.

Log evidence, not only `:warnings`:
- `spec/isaac/config/warnings_spec.clj` — new `log-unknown-keys!` describe: 4 examples (per-row `:warn`/`:config/unknown-key` with slice+key+path; quiet for non-unknown-key rows; a bare top-level key logs `:slice nil`; returns its input so it can sit in the load pipeline).
- `spec/isaac/config/signal_slots_spec.clj` — new integration example "logs a warning naming the slice and the undeclared key, and still loads (isaac-nq4c)": loads a real config through `marigold/load-config` and asserts the captured entry `:warn` / `:config/unknown-key` / slice `signals[:mychan]` / key `account-id`, **plus** `(should= [] (:errors result))` and the declared `:token` still arriving — boot-still-succeeds in the same example. The bare-`:map` example now asserts its log entry too (`slice "signals[:mychan].allow-from"`, key `"domain"`).

**2 — the swallowed spec runs (blocking item cleared).** The misplaced paren in `warnings_spec.clj` is fixed: `it "never warns on known fields at any depth"` now closes before `it "stays quiet for slots and values that are not maps"`. `bb spec -f documentation spec/isaac/config/warnings_spec.clj` prints **12 examples** — the original 8 (7 -> 8, the non-map-slot guard included by name) plus the 4 new `log-unknown-keys!` ones.

**3 — dead helper removed.** `spec-known-fields` is gone from `src/isaac/config/warnings.clj`; `grep -rn spec-known-fields src/ spec/` is empty.

Kept as the verifier asked: `slot-walk`, the `signal_slots_spec` integration pair, no feature file touched.

**Suites on the branch:** `bb spec` **1090 / 0 failures / 1991 assertions** (1084 -> 1090: +1 unswallowed, +4 unit, +1 integration). `bb features` **198 / 0 failures / 524 assertions / 2 pending** (the two pre-existing berth-registration pendings). `bb lint` exit 0, no new warnings in the touched files. The two `cli/modules_pins.feature` failures were the stale gitlibs mirror again (by then pointing at the verifier's removed `isaac-foundation-nq4c-verify/fixture-agent`); `rm -rf ~/.gitlibs/_repos/file/REL/fixture-agent` and features are green — environmental, unchanged from attempt 1.

Bean Gate: `bb bean-gate verify isaac-nq4c --dir isaac-foundation=../isaac-foundation-nq4c` -> `no feature-baseline: use the verify path`, exit 2. Closing on the unverified + verify-hail path again.
