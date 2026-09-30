---
# isaac-h2oo
title: Crew/model existence validation moves to isaac-agent
status: todo
type: task
priority: normal
created_at: 2026-09-30T02:43:45Z
updated_at: 2026-09-30T02:44:57Z
---

## Ruling

Micah, 2026-09-29: isaac-foundation's code must never name another module's
config, berths, ids or concepts. Clean cutover, no back-compat.

## Problem

`isaac-foundation/src/isaac/config/validation_lexicon.clj` hard-codes two
agent-owned entity kinds into apron's shared validations lexicon:

```clojure
(defn known-crew-ids [config]
  (->> (keys (:crew config)) (map ->id) distinct sort vec))     ; line 27-28

(defn known-model-ids [config]
  (->> (keys (:models config)) (map ->id) distinct sort vec))   ; line 30-31

(def ^:private existence-refs
  {:model-exists? (exists-ref :model-exists? known-model-ids "references undefined model")
   :crew-exists?  (exists-ref :crew-exists? known-crew-ids "references undefined crew")
   ...})                                                        ; line 50-54
```

Confirmed by grep across every sibling repo checked out locally
(isaac-agent, isaac-server, isaac-http, isaac-cron, isaac-hail,
isaac-discord, isaac-imessage, isaac-acp, isaac-hooks, isaac-cli-server,
isaac-cli-proxy, isaac-episodes, isaac-foreman, isaac-gchat, isaac-gmail,
isaac-google, isaac-mcp, isaac-worksite, isaac-claude-code): only
`isaac-agent/resources/isaac-manifest.edn` (lines 540, 544, 573) and
`isaac-hooks/resources/isaac-manifest.edn` reference `:model-exists?` /
`:crew-exists?` as validations. `:crew` and `:models` are entity-dir tables
declared entirely by isaac-agent's own manifest
(`:entity-dir "crew"` / `:entity-dir "models"`) — foundation has no
schema knowledge of either concept; it just happens to also read
`(:crew config)`/`(:models config)` by literal key.

`:gauge-exists?`/`known-gauge-ids` (reads `:gauges`) and
`:berth-exists?`/`known-berth-ids` (reads `:berths`) are NOT used by any
manifest in any checked-out repo — `:berths` is arguably foundation's own
concept (module berths); `:gauges` appears nowhere else at all and may be
dead code. Leave both alone in this bean (see Open questions).

`isaac.config.validation`'s `validation-context` (line ~24-29) wires all
four into `:known-values`/`:known-sets` unconditionally — this stays in
foundation (it's the generic "known-value sets for exists? refs" plumbing);
only the crew/model-specific `known-*-ids` functions and their lexicon
registrations move.

## Wanted

1. Move `known-crew-ids`, `known-model-ids`, and the `:crew-exists?`/
   `:model-exists?` entries out of `isaac.config.validation-lexicon` into a
   new isaac-agent leaf namespace (mirrors the existing pattern exactly:
   a namespace with no cycle risk, registering into apron's shared
   lexicon via `cs/update-lexicon!` at load time — this is apron's own
   public API, not a foundation-provided one, so isaac-agent can call it
   directly with zero foundation involvement).
2. **Load-order is the open design question this bean must resolve**:
   foundation's `isaac.config.schema-compose` currently `:require`s
   `isaac.config.validation-lexicon` directly as a "load for side effect"
   comment explains — guaranteeing it's registered before the first
   schema compose. Foundation cannot do the analogous direct `:require`
   of an isaac-agent namespace (that's exactly the naming violation this
   cleanup removes). Investigate whether isaac-agent's manifest
   `:bootstrap` key (a symbol `isaac.module.lifecycle/activate!` resolves
   and requires per-module — see `isaac-foundation/src/isaac/module/
   lifecycle.clj` lines ~96-131) runs early enough, for every path that
   composes a schema (`isaac config schema`, `isaac config validate`,
   normal boot), to have the ref registered before the first compose. If
   `:bootstrap` isn't guaranteed early enough, isaac-agent's own core
   namespace (something already required unconditionally whenever agent
   loads at all, e.g. wherever its `:factory` module-entrypoint symbol
   lives) is the fallback anchor — require the validation-lexicon ns
   from there instead of introducing a new mechanism.
3. `isaac.config.validation`'s `validation-context` keeps calling
   `vlex/known-crew-ids`/`vlex/known-model-ids` — but `vlex` here becomes
   whichever ns those functions now live in... **except foundation can't
   require an isaac-agent namespace for THIS either.** Resolve by making
   `:known-values`/`:known-sets` generic: instead of a hard-coded map of
   4 known predicate keys, derive the set of "existence-ref" predicates
   to compute from the *lexicon itself* (apron's `active-lexicon` already
   tags each ref; `existence-refs` in validation_lexicon.clj already sets
   `:reference? true` on each one — that flag is already there to find
   them generically). `validation-context` should walk the active lexicon
   for entries with `:reference? true` and call each one's own `:known`
   thunk (already present on every existence-ref, see `exists-ref`) rather
   than pre-building `known-values` from a fixed map of named functions.
   This removes the `:crew`/`:models`/`:gauges`/`:berths` names from
   `validation.clj` entirely — it becomes ref-key agnostic.
4. Update `isaac-hooks`' manifest reference to `:crew-exists?` — no code
   change needed there (it references the validation by keyword, resolved
   from the ambient lexicon at validate time), but confirm in acceptance
   that hooks' own feature suite still passes once the ref is
   agent-registered instead of foundation-registered (agent must be
   installed alongside hooks for `:crew-exists?` to resolve — true in
   every real deployment, but a hooks-only feature-test fixture that
   doesn't load agent would need a stub ref or an agent fixture module;
   check hooks' existing test setup).

## Acceptance

- `grep -rn "crew\|models" isaac-foundation/src/isaac/config/validation_lexicon.clj` shows nothing left referencing those keys (only `:gauges`/`:berths` remain, or neither if Open Questions below get resolved to delete them too).
- `isaac.config.validation/validation-context` contains no literal
  `:crew`/`:models`/`:providers` keyword.
- isaac-agent's own `demands-a-field?`/`crew-exists?`/`model-exists?`
  scenarios (isaac-dnib pins some of these — see Notes) stay green.
- isaac-hooks' feature suite (wherever it exercises `:crew-exists?` on
  its `:crew` field) stays green.
- A fresh `isaac config validate` against a config with agent installed
  and an undefined crew reference still reports "references undefined
  crew" exactly as today.

## Likely repo scope

`isaac-foundation` (`validation_lexicon.clj`, `validation.clj`,
`schema_compose.clj`'s "load for side effect" comment/require),
`isaac-agent` (new leaf ns + manifest `:bootstrap` wiring if needed),
`isaac-hooks` (verify only, probably no code change).

## Notes

- **Sequencing vs. isaac-dnib**: isaac-dnib is IN PROGRESS and has already
  pushed changes to `isaac-foundation/src/isaac/config/validation.clj`
  (bean/isaac-dnib is 27 lines changed there as of this planning session,
  landing `demands-a-field?`'s `:required?`→`:required` cutover). This
  bean also touches `validation.clj` (item 3, `validation-context`).
  **Land after isaac-dnib merges to main; rebase item 3's diff onto
  isaac-dnib's landed `validation.clj`, don't draft against pre-dnib
  line numbers.**
- Not blocked by, and doesn't block, cleanup-bean-1 (comms) — different
  files entirely.

## Open questions

- Is `:gauges`/`gauge-exists?`/`known-gauge-ids` dead code? It appears in
  isaac-foundation's validation_lexicon.clj, validation.clj,
  cli/validate.clj, and schema/resolve.clj's hard-coded entity-collection
  sets, but no manifest in any checked-out repo declares a `:gauges`
  entity-dir or references `:gauge-exists?`. If dead, a separate small
  cleanup bean should delete it outright rather than migrate it anywhere
  — flagging rather than deleting speculatively in this bean.
- Confirm with Micah: is `:berths` (the entity-collection, i.e. user-
  configured berth instances — NOT the module-berth mechanism) foundation's
  own concept, or does it also belong to some module? `:berth-exists?` is
  unused by any manifest checked so far; same "possibly dead" flag applies.

## Decision (planner recommendation; Micah approved the cleanup 2026-09-30)

How agent's validations reach foundation: foundation declares a berth for config validation refs (lexicon entries); isaac-agent contributes `:crew-exists?` and `:model-exists?` through its manifest. No special loading, no foundation reference to agent namespaces.

## Ungated

Refactor with no new user-visible behavior, so no new scenarios: acceptance is both repos' full CI green (existing scenarios are the regression net), the grep checks named in this bean, and planner verification. Worker hands off with `tag=unverified`.
