---
# isaac-67cq
title: Confirm and delete dead entity tables (gauges, foundries, berths)
status: draft
type: task
priority: low
created_at: 2026-09-30T02:44:10Z
updated_at: 2026-10-01T05:02:13Z
blocked_by:
    - isaac-on0o
---

Found during the cleanup survey (2026-09-30). Foundation keeps entity tables `:gauges`, `:foundries` and `:berths` in its hard-coded entity-collection sets, but no manifest in any repo checked out locally contributes or uses them.

## Wanted

Confirm they are dead (grep every sibling repo and zanebot/yopp config), then delete them. If one is alive, document its owner instead.

## Decision (Micah, 2026-09-30)

Approved: delete them (likely test leftovers). Still grep every sibling repo and zanebot/yopp config first; if one turns out alive, stop and report.

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
test tree. `ssh zane@zanebot.tail66e5f8.ts.net` and `ssh yopp@yopp` greps of
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
