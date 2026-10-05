---
# isaac-ka10
title: Episodes runs as a context mode plus a session observer
status: todo
type: feature
priority: normal
created_at: 2026-10-05T01:58:10Z
updated_at: 2026-10-05T18:21:52Z
blocked_by:
    - isaac-c52a
---

Micah, 2026-10-04. Follows the context-mode + observer berths bean. Moves episodes off SessionPolicy.

## Shape
- isaac-episodes contributes an `:episodes` **context mode** (cold open at episode boundaries, lineage + continuation seeding, compaction splice) declaring `:requires {:observers #{:episodes}}`, and an `:episodes` **observer** (episode open/seal, scene feeding, turn-marker bookkeeping, index rows).
- The episodes context mode waits for the episodes observer to catch up on the session before a cold open.
- Observer without the context mode is valid: a crew can run `:observers [:episodes]` with `:context-mode :full` or `:reset` and still produce scenes/episodes (for vault sync, search, recall tools), just not use them for its own context.
- Episodes reads the session store directly (no `chronicle-transcript` through a policy). The `:session-policy` field in episodes index rows goes away.
- **Migration is manual** (Micah 2026-10-04: no legacy awareness in code). At deploy, crews on `:session-policy :episodes` are hand-edited to `:context-mode :episodes` + `:observers [:episodes]`; `:session-policy` keys and session stamps are removed by hand.

## Acceptance (scenarios TBD, gated)
- Existing episodes scenarios (cold open, lineage, continuation from isaac-mwqs, recall) pass on the new berths.
- A crew with the episodes observer and `:context-mode :full` produces scenes and episodes, and its prompts carry no cold-open/lineage block.
- A crew with `:context-mode :episodes` and no episodes observer fails config validation naming both.
- A per-turn `--with-context-mode :full` on an episodes session builds full context and the observer keeps recording.
- Handbook (episodes chapter) rewritten for the two settings.
- `bb ci` green; pins coherent with the agent bean's sha.

Likely repo scope: isaac-episodes (+ zanebot/yopp config at deploy).

## Baseline plan (2026-10-04)
Scenarios signed off (draft: features/episodes/context_mode_and_observer.feature, 5 scenarios). Baseline waits for isaac-mwqs to complete: its baselined continuation.feature carries a `session-policy` Background row. At baseline, the planner also rewrites the `session-policy` rows in the 11 other episodes feature files (~75 scenarios; layout.feature asserts the index field itself) to `:context-mode :episodes` + `:observers [:episodes]`, marks the touched scenarios @wip, and includes them in the baseline.

Storage check (2026-10-04): live episodes do not hold transcripts. episode.edn + scenes/ (start-id/end-id ranges into the session transcript, plus a scene text copy for recall); the session transcript is written once. The 25 episodes/<cid>/current.ednl on zanebot are b6w0 migration leftovers. So episodes are already ranges; the only on-path work (warm/cold decision + recall injection at append) moves to the episodes context mode at turn prep. layout.feature description line about episodes/<cid>/current.ednl is stale; fix at baseline.

feature-baseline: isaac-episodes e9a1405665d465e4d7639d6804e5c0247e0a4701

## Decision + Acceptance (Micah signed off 2026-10-04/05; gated)
Decisions 2026-10-05: no automatic migration (no legacy awareness); `episodes migrate-layout` is legacy and is deleted with its scenarios; `sessions list` replaces the POLICY column with CONTEXT (the session's resolved context mode).
- The @wip scenarios in the 13 isaac-episodes feature files baselined below pass with @wip removed: the new `features/episodes/context_mode_and_observer.feature` (5), and every scenario whose crew row moved from `:session-policy :episodes` to `:context-mode :episodes` + `:observers [:episodes]` (layout, live, idle_seal, migrate_session, provider_attention, recall_logging, session_naming, continuation, embedding, implicit_tools, ledger, live_tools).
- The @wip scenario in isaac-agent `features/session/cli.feature` (CONTEXT column) passes with @wip removed.
- One-time: the `episodes migrate-layout` command and its code are gone; no `session-policy` / `:session-policy` reference remains in isaac-episodes src or spec; episodes index rows carry no policy field.
- isaac-episodes pins the isaac-agent sha that carries isaac-c52a (pins coherent).
- Handbook (episodes chapter) rewritten for `:context-mode :episodes` + `:observers [:episodes]`, including running the observer without the context mode.
- `bb ci` green in isaac-episodes and isaac-agent.
- Deploy note (manual, not this bean): hand-edit zanebot/yopp crews from `:session-policy :episodes` to the two settings and strip `:session-policy` from session.edn files.

feature-baseline: isaac-agent 2346f1cd3917937b9c8eb7fe20311178d0ab4fba
feature-blob: isaac-episodes features/episodes/context_mode_and_observer.feature d5d208018130e80fced901848712869c7c3ef0af
feature-blob: isaac-episodes features/episodes/idle_seal.feature e38d19c1520213796283b08eda7c2aec06dbe109
feature-blob: isaac-episodes features/episodes/layout.feature ea3da876133cbd6877347e6ce7280c8b640647b2
feature-blob: isaac-episodes features/episodes/live.feature 3cd76ea7e910caff044e4e87970db2279672830e
feature-blob: isaac-episodes features/episodes/migrate_session.feature eede1eac1a4de200573194e4191117dba695c2ca
feature-blob: isaac-episodes features/episodes/provider_attention.feature 2ea70661da61a86fafb54ee116d170d6fdb1789a
feature-blob: isaac-episodes features/episodes/recall_logging.feature 91357700a8d3dd675a420e9ed02e76a3934276d4
feature-blob: isaac-episodes features/episodes/session_naming.feature 88f4deb1c806f6b459ae1d616ed646064fb1adc4
feature-blob: isaac-episodes features/recall/continuation.feature 30b3ce5021b305d9321db543785821b33dcbea3c
feature-blob: isaac-episodes features/recall/embedding.feature a74d8f6e42f86223104e151a5b22baabfc5a0507
feature-blob: isaac-episodes features/recall/implicit_tools.feature f454fe00a3f7d15d8fb87c91f7ec156e09c996c9
feature-blob: isaac-episodes features/recall/ledger.feature db0415c7b9217e8dd4406698a641a5b930213670
feature-blob: isaac-episodes features/recall/live_tools.feature dde1e695b8a37665faec494bf4dc5dccd523a940
feature-blob: isaac-agent features/session/cli.feature 18c75424e758019aed10554e5729a726aef9f55e

## Checkpoint (2026-10-05, scrapper@isaac-work-2)

Done:
- Deleted SessionPolicy (`policy.clj`) and `migrate-layout` (cli + `layout.clj` + spec).
- Episodes contributes `:isaac.agent/context-mode :episodes` (requires observer, wait-for, prepare) and `:isaac.agent/session-observer :episodes`.
- Observer: resolve-thread / maybe-seal / chain-on-compaction. Context mode: slice + recall prefix on the model-facing transcript; session transcript is not rotated on TTL.
- Handbook rewritten for the two settings. `bb spec` was 234 green before the last-input-tokens tweak; observer_spec compaction stamp is the remaining unit red.
- `@wip` stripped on the 13 episodes feature files + agent `features/session/cli.feature`.
- Worktrees: `isaac-episodes-ka10`, `isaac-agent-ka10` on `bean/isaac-ka10`. Episodes deps currently `:local/root ../isaac-agent-ka10`.

Next (resume here):
1. `bb spec` — fix `observer_spec.clj:114` closed episode last-input-tokens (got 0, want 85).
2. `bb features features/episodes/context_mode_and_observer.feature features/episodes/layout.feature features/episodes/live.feature:383` — remaining reds:
   - `--with-context-mode full` still contains "Where this conversation left off" (override vs episodes select/prepare).
   - reset-mode crew recall not on the prompt (`live.feature` ~383; wrap-input/:reset prepare).
   - layout session.edn `context-mode` keyword `:episodes` vs table string `"episodes"` (`fs_steps` parse-isaac-value).
   - layout compaction successor last-input-tokens 16 vs 85.
   - layout listing `sessions/main/harbor-log/session.edn` missing.
3. Full `bb features` then `bb ci` in both repos. Pin episodes to landed agent main sha only at landing. Remove `:local/root` before squash.
4. Agent `features/session/cli.feature` CONTEXT scenario — implementation already on c52a; confirm green.

Resume: `src/isaac/session/episodes/context.clj` (prepare/select/wrap-reset) and `src/isaac/session/episodes/observer.clj` (compaction stamp); agent `src/isaac/agent/session/context.clj` create-with-resolved-behavior! persist.

feature-baseline: isaac-episodes b3614595127a0a2fd63aa2806788d3c3bc48db8b
feature-baseline: isaac-agent b7ee4ca7d3728504ee428738665dde37a548496e
feature-blob: isaac-episodes features/episodes/context_mode_and_observer.feature d5d208018130e80fced901848712869c7c3ef0af
feature-blob: isaac-episodes features/episodes/idle_seal.feature e38d19c1520213796283b08eda7c2aec06dbe109
feature-blob: isaac-episodes features/episodes/layout.feature 7d5737bca77c26637d4f1e7cea30304b5dc7d81d
feature-blob: isaac-episodes features/episodes/live.feature 3cd76ea7e910caff044e4e87970db2279672830e
feature-blob: isaac-episodes features/episodes/migrate_session.feature eede1eac1a4de200573194e4191117dba695c2ca
feature-blob: isaac-episodes features/episodes/provider_attention.feature 2ea70661da61a86fafb54ee116d170d6fdb1789a
feature-blob: isaac-episodes features/episodes/recall_logging.feature 91357700a8d3dd675a420e9ed02e76a3934276d4
feature-blob: isaac-episodes features/episodes/session_naming.feature 88f4deb1c806f6b459ae1d616ed646064fb1adc4
feature-blob: isaac-episodes features/recall/continuation.feature 30b3ce5021b305d9321db543785821b33dcbea3c
feature-blob: isaac-episodes features/recall/embedding.feature a74d8f6e42f86223104e151a5b22baabfc5a0507
feature-blob: isaac-episodes features/recall/implicit_tools.feature f454fe00a3f7d15d8fb87c91f7ec156e09c996c9
feature-blob: isaac-episodes features/recall/ledger.feature db0415c7b9217e8dd4406698a641a5b930213670
feature-blob: isaac-episodes features/recall/live_tools.feature dde1e695b8a37665faec494bf4dc5dccd523a940
feature-blob: isaac-agent features/session/cli.feature 18c75424e758019aed10554e5729a726aef9f55e

## Planner adjustment (2026-10-05, layout session.edn)
My rewrite wrongly turned two removed `:session-policy` stamp rows into `context-mode`/`observers` assertions on `session.edn` (layout cold-open and compaction-successor scenarios). Removed in isaac-episodes `b361459`: the session record carries identity and overrides only, never the crew's context settings. That clears the "keyword :episodes vs string" red. Re-baselined. Rebase onto episodes main; drop `@wip` only. Resume per the checkpoint above.

## Checkpoint (2026-10-05 later, scrapper@isaac-work-2)

Done:
- Rebased both bean branches onto origin/main (planner layout baseline).
- `--with-context-mode full`: charge now keeps `:context-mode-override`; dispatch prefers it over a session stamp.
- `:reset` wrap-input survives berth re-registration (`context-mode/register!` merges).
- Continuation last-exchange walks transcript order (UUID ids are not chronological).
- isaac-episodes `bb ci` green: 235 spec, 104 features.
- Do not stamp crew `:context-mode`/`:observers` onto a new session (broke funnel re-cascade and observer override).

Still red:
1. Agent `features/session/cli.feature:126` CONTEXT column — stdout regex does not match. Older list scenario (`cli.feature:90`) is green. Capture `sessions list` stdout and fix the CONTEXT column/regex alignment.
2. Episodes `layout.feature:149` listing — `the user sends` without crew uses `unique-observer-crew-id` (cordelia) so `sessions/main/harbor-log/session.edn` is missing. Skipping unique-observer when defaults.crew is set fixes listing but breaks c52a observer features that rely on it. Planner conflict vs helper convention.

Worktrees: `isaac-episodes-ka10` @ `59c6d48`, `isaac-agent-ka10` @ `ec0fd46`. Episodes still `:local/root ../isaac-agent-ka10`.

Resume: agent `src/isaac/agent/session/cli.clj` `print-session-table` / CONTEXT regex; then decide listing helper vs planner hail. Pin episodes to landed agent main sha only at landing.

feature-baseline: isaac-episodes 1dc26777a31ffe6fdea9b63d98c185caa0e2c61a
feature-baseline: isaac-agent b7ee4ca7d3728504ee428738665dde37a548496e
feature-blob: isaac-episodes features/episodes/context_mode_and_observer.feature d5d208018130e80fced901848712869c7c3ef0af
feature-blob: isaac-episodes features/episodes/idle_seal.feature e38d19c1520213796283b08eda7c2aec06dbe109
feature-blob: isaac-episodes features/episodes/layout.feature 7a8ab55a808db14b38af7e1d746792503a7610e9
feature-blob: isaac-episodes features/episodes/live.feature 3cd76ea7e910caff044e4e87970db2279672830e
feature-blob: isaac-episodes features/episodes/migrate_session.feature eede1eac1a4de200573194e4191117dba695c2ca
feature-blob: isaac-episodes features/episodes/provider_attention.feature 2ea70661da61a86fafb54ee116d170d6fdb1789a
feature-blob: isaac-episodes features/episodes/recall_logging.feature 91357700a8d3dd675a420e9ed02e76a3934276d4
feature-blob: isaac-episodes features/episodes/session_naming.feature 88f4deb1c806f6b459ae1d616ed646064fb1adc4
feature-blob: isaac-episodes features/recall/continuation.feature 30b3ce5021b305d9321db543785821b33dcbea3c
feature-blob: isaac-episodes features/recall/embedding.feature a74d8f6e42f86223104e151a5b22baabfc5a0507
feature-blob: isaac-episodes features/recall/implicit_tools.feature f454fe00a3f7d15d8fb87c91f7ec156e09c996c9
feature-blob: isaac-episodes features/recall/ledger.feature db0415c7b9217e8dd4406698a641a5b930213670
feature-blob: isaac-episodes features/recall/live_tools.feature dde1e695b8a37665faec494bf4dc5dccd523a940
feature-blob: isaac-agent features/session/cli.feature 18c75424e758019aed10554e5729a726aef9f55e

## Planner adjustment (2026-10-05, listing crew)
Layout listing scenario: `When the user sends "Status?" on session "harbor-log" as crew "main"` (isaac-episodes main). The c52a harness resolves a crew-less send to the unique observer crew; keep that convention, do not change the helper. Re-baselined. The agent CONTEXT-column scenario stays as written: capture the real `sessions list` output and make the column render to match. Rebase; drop `@wip` only.
