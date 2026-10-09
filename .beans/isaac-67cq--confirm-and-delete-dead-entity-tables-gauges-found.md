---
# isaac-67cq
title: Confirm and delete dead entity tables (gauges, foundries, berths)
status: completed
type: task
priority: low
created_at: 2026-09-30T02:44:10Z
updated_at: 2026-10-01T05:47:59Z
blocked_by:
    - isaac-on0o
---

Found during the cleanup survey (2026-09-30). Foundation keeps entity tables `:gauges`, `:foundries` and `:berths` in its hard-coded entity-collection sets, but no manifest in any repo checked out locally contributes or uses them.

## Wanted

Confirm they are dead (grep every sibling repo and zanebot/skiff config), then delete them. If one is alive, document its owner instead.

## Decision (Micah, 2026-09-30)

Approved: delete them (likely test leftovers). Still grep every sibling repo and zanebot/skiff config first; if one turns out alive, stop and report.

## STOP — alive, not deleted (worker, 2026-09-30)

All three are owned and actively used by isaac-foundation's own config-spec
test fixture. Not deleted. Evidence:

**No real manifest anywhere declares them as config tables.** Grepped every
canonical sibling checkout under `~/agents/isaac/plan/` (isaac, isaac-acp,
isaac-agent, isaac-claude-code, isaac-cli-proxy, isaac-cli-server, isaac-cron,
isaac-discord, isaac-episodes, isaac-foreman, isaac-foundation, isaac-gchat,
isaac-gmail, isaac-google, isaac-hail, isaac-handbook, isaac-hooks, isaac-http,
isaac-imessage, isaac-mcp, isaac-server, isaac-worksite — skipping worktree
dirs) for `:gauges|:foundries|:berths|gauges/|foundries/|berths/` across
manifests, src and features. No hit declares `:gauges`/`:foundries`, or `:berths`
as an `:isaac.config/schema` entity table outside isaac-foundation's own
test tree. `ssh zane@zanebot.<tailnet>.ts.net` and `ssh skiff@skiff` greps of
`~/.isaac/config` for the same patterns: zero hits on both hosts.

**But they are foundation's own, deliberate self-test fixture, not leftovers:**

- `isaac-foundation/spec/isaac/foundation/config/marigold.clj` defines a
  fictional `:marigold.chartroom` module (the same "invented module" pattern
  as the real `modules/marigold.*` fixtures used repo-wide) whose
  `:isaac.config/schema` deliberately declares `:gauges`, `:foundries`, and
  `:berths` as entity-dir tables (plus `:station`, `:relay`, `:watch`,
  `:signals`, `:kit`), each wired to real generic machinery: `:merge-root-entity?`,
  `:entity-dir`, companion resolution (`:berths` has `{:field :ledger :mode
  :exclusive}`), `:dynamic-schema`, and `:registered-in?` cross-references
  (`:gauges.foundry` → `:foundries`). This is the load-bearing fixture for
  ~20 spec files that test foundation's generic entity-table/companion/schema
  machinery: `parse_spec.clj`, `normalize_spec.clj`, `companions_spec.clj`,
  `loader_spec.clj`, `foundry_schema_spec.clj`, `module_discovery_spec.clj`,
  `signal_slots_spec.clj`, `entities_spec.clj`, `semantic_errors_spec.clj`,
  `kit_schema_spec.clj`, `load_result_spec.clj`, `validation_spec.clj`,
  `config/cli/get_spec.clj`, `config/cli/validate_spec.clj`,
  `config/cli/common_spec.clj`, `config/schema/term_spec.clj`,
  `config/mutate_spec.clj`. Deleting the fixture would gut this coverage.

- `isaac-foundation/src/isaac/foundation/config/validation_lexicon.clj` keeps
  its own hard-coded `:gauge-exists?`/`:berth-exists?` existence-ref
  validations (`known-gauge-ids`, `known-berth-ids`, the `existence-refs`
  map) specifically so the marigold fixture can exercise foundation's own
  generic `exists-ref` mechanism from production code — this is intentional
  per commit `2371258` (isaac-h2oo: "validation-lexicon keeps only its own
  :berth-exists?/:gauge-exists? refs, registered as before" after crew/model
  moved out to isaac-agent's module-contributed mechanism). No real module
  uses these two refs; only the fixture does, by design — foundation has no
  real downstream consumer in this repo, same as every other `marigold.*`
  fixture.

- The historical "hard-coded `entity-collections` set (`:berths :gauges
  :foundries :crew :hail :models :providers`)" described in
  `features/cli/config_set_unset.feature`'s narrative is **already fixed**
  (isaac-n140): `isaac.foundation.config.schema-compose/entity-collection-key?`
  is purely schema-shape-based now (`:key-spec` + `:value-spec`), no name
  list. That feature file's prose is a historical note about a bug that no
  longer exists in code — there is nothing left there to delete.

**Conclusion:** the three "tables" are alive as isaac-foundation's own
config-spec fixture vocabulary (owner: `isaac.foundation.config.marigold` /
the fictional `:marigold.chartroom` module), exercising production code in
`validation_lexicon.clj` that is itself only used by that fixture. Micah's
"likely test leftovers" guess was half right — it IS test-only — but it's
active, load-bearing test infrastructure, not dead cruft. Deleting it would
mean either gutting ~20 specs' entity-table/companion/validation coverage, or
a much bigger rewrite (rename the fixture's tables away from gauge/foundry/
berth AND move `:gauge-exists?`/`:berth-exists?` out of foundation into a
module-contributed ref the fixture itself declares, mirroring how isaac-agent
now owns `:crew-exists?`/`:model-exists?`). That's a real refactor, not a
"confirm dead, delete" task — flagging back rather than guessing at scope.
No code changed; moved to `draft` pending a re-scope decision.

## Re-scope (Micah, 2026-10-01)

The tables stay as foundation test-fixture vocabulary. What goes: foundation production code that exists only for that fixture. Move `:gauge-exists?` and `:berth-exists?` (and `known-gauge-ids`/`known-berth-ids`) out of `src/isaac/foundation/config/validation_lexicon.clj`; the marigold.chartroom fixture contributes them itself through `:isaac.config/validation-ref`, the same way isaac-agent contributes `:crew-exists?`/`:model-exists?`. No behavior change; every spec that uses them stays green. Acceptance: grep shows no gauge/berth/foundry vocabulary in foundation `src/`; `bb ci`, `bb features-slow`, `bb jvm-spec` green. Ungated.

## Landed on main (worker, 2026-10-01)

`:gauge-exists?`/`:berth-exists?` and `known-gauge-ids`/`known-berth-ids` are
out of `isaac-foundation/src/isaac/foundation/config/validation_lexicon.clj`.
`spec/isaac/foundation/config/marigold.clj`'s `:marigold.chartroom` fixture now
owns `known-gauge-ids`/`known-berth-ids` and contributes both refs through its
`baseline-chartroom-manifest`'s `:isaac.config/validation-ref` key, exactly
like isaac-agent's `:crew-exists?`/`:model-exists?`. The sibling
`spec/isaac/foundation/marigold.clj` fixture (used by load_result/
semantic_errors/etc.) gained the matching `:isaac.config/validation-ref` berth
declaration so a manifest combining both fixtures still composes.
`semantic_errors_spec.clj`'s `with-redefs-fn` targets were repointed from
`vlex/known-berth-ids`/`vlex/known-gauge-ids` to the fixture's own vars — same
assertions, same behavior.

Also dropped two other gauge/foundry-only spots that existed only for this
fixture: `cli/common.clj`'s `present-identifiers` hard-coded `:foundry`/
`:gauge` keyword-coercion cases (no real module has either field; verified
unused by re-running the full spec suite with them removed — identical
failure count), and two doc-comment examples in `mutate.clj` that cited
`:berth-exists?`/`:gauge-exists?`/`"berths, relays"` as if they were
foundation's own refs or real entity tables — reworded to
`:crew-exists?`/`:model-exists?` and `"crews, models, providers,
resource-pools"`.

**Grep (foundation `src/`), from isaac-foundation-67cq at main-sha 8f57bb6:**

```
$ grep -rniE "gauge|foundry" src/
(no output)
$ grep -rn "gauge-exists\|known-gauge\|berth-exists\|known-berth" src/
src/isaac/foundation/module/berths.clj:220:(defn unknown-berth-error [consumer-id berth-key]
src/isaac/foundation/module/berths.clj:315:                [(unknown-berth-error consumer-id berth-key)]))
```
(those two hits are the berth SYSTEM's own error helper — module-contribution
plumbing, not the entity-table fixture vocabulary; left in place per scope.)

**Test comparison** (isaac-foundation, HOME isolated to an empty tmp dir, vs.
unmodified main at `b133e06`):

- `bb spec`: main 1338 examples / 65 failures; worktree 1338 / 56 failures —
  a strict subset (0 new failures, 9 fewer); re-run twice, stable. The 56
  that remain reproduce identically on unmodified main too (pre-existing,
  order-dependent global-lexicon/schema-cache pollution across spec files
  when the full suite runs in one process — confirmed by running the
  affected files in isolation, where they're all green). Not touched; out
  of scope for this bean.
- `bb jvm-spec`: 1338 examples / 49 failures on both main and worktree —
  exact same failure set (diffed, zero new/fixed). Pre-existing (macOS
  `service.cli` launchctl specs failing under a non-standard `$HOME`),
  unrelated to this change.
- `bb features` (non-wip): 356 examples / 0 failures / 2 pending, identical
  on both.
- `bb features-slow`: both main and worktree abort at the same first
  scenario (`module/modules_deps_emit.feature:83`) with byte-identical
  output regardless of HOME isolation (reproduces even with the real
  `$HOME`) — a pre-existing environmental issue in this sandbox, not
  something this change touches. No `@slow` feature references gauge/berth/
  foundry at all, so this change isn't exercised by that suite anyway.
- `bb lint`: 0 errors / 290 warnings on both.
- `bb ci`'s `config-bypass-lint`, `lint-cli-host`, `lint-pins` steps: all
  `ok` on both; the `pins` step itself fails identically on both (pre-existing,
  needs the real module registry/classpath this sandbox doesn't have).

**CI:** https://github.com/slagyr/isaac-foundation/actions/runs/36821325200 —
all 3 jobs green (`verify`, `Slow features (@slow launcher lane)`, `Server
boot with a module-provided config type`).

main-sha: isaac-foundation 8f57bb6

## Planner verification (2026-10-01)

Verified on 8f57bb6: no gauge/foundry vocabulary in foundation src; refs contributed by the fixture via :isaac.config/validation-ref; CI green.
