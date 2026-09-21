---
# isaac-nq4c
title: A config key a declared schema does not recognise is pruned in silence; it should warn
status: in-progress
type: bug
priority: normal
tags:
    - config
created_at: 2026-09-21T04:25:37Z
updated_at: 2026-09-21T04:42:41Z
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
