---
# isaac-yfb4
title: "Fold isaac.context.manager into isaac.session.compaction"
status: completed
type: task
priority: normal
created_at: 2026-05-07T17:36:31Z
updated_at: 2026-05-07T18:42:17Z
---

## Description

isaac.context.manager is the lone occupant of src/isaac/context/ and exists to orchestrate session compaction. Its only production caller is isaac.drive.turn (calls compact! and should-compact?). It already requires isaac.session.compaction for the strategy/schema/target functions. Splitting compaction into "policy" (session) and "orchestration" (context.manager) earns nothing — they share data shapes and a single intent.

The name "manager" is squishy; "context" is already overloaded in this codebase (isaac.session.context for per-turn config resolution, context-window for token budget, etc.). Folding everything into isaac.session.compaction collapses the redundancy and gives compaction a single authoritative home.

Changes:

1. Merge file contents:
   - src/isaac/context/manager.clj → src/isaac/session/compaction.clj
     Move public fns (compact!, should-compact?, last-compaction-request) and all private helpers. The current pass-through (defn should-compact? ... (compaction/should-compact? ...)) becomes the real definition.
   - spec/isaac/context/manager_spec.clj → spec/isaac/session/compaction_spec.clj
     Merge tests; update ns form. Keep the existing compaction_spec contents.

2. Update production caller (1 site):
   - src/isaac/drive/turn.clj — change `[isaac.context.manager :as ctx]` to `[isaac.session.compaction :as compaction]` and update the two call sites (ctx/compact!, ctx/should-compact?).

3. Update spec callers (2 files):
   - spec/isaac/bridge/chat_cli_spec.clj — 41 references to ctx/should-compact? and ctx/compact! in with-redefs blocks. Mechanical rename of the alias.
   - spec/isaac/features/steps/session.clj — update ns require and the two ctx/last-compaction-request call sites.

4. Remove src/isaac/context/ and spec/isaac/context/ directories.

5. Internal cross-reference: isaac.session.compaction currently imports nothing from elsewhere in this subtree; the merged ns will pull in isaac.llm.api, isaac.logger, isaac.message.content, isaac.prompt.builder, isaac.session.storage, isaac.tool.builtin, isaac.tool.registry. Verify no circular requires (session.compaction is currently leaf-ish; adding these deps is the main risk).

No behavior change. Compaction triggers, summary prompt, chunking, splice, and token bookkeeping all stay the same.

## Acceptance Criteria

bb spec green; bb features green; grep -rn 'isaac\.context\.manager' src spec returns nothing; src/isaac/context/ and spec/isaac/context/ removed; isaac.drive.turn requires isaac.session.compaction directly; compact!, should-compact?, last-compaction-request are all in isaac.session.compaction.

## Design

Considered isaac.drive.compaction (matches the lone caller) and isaac.drive.context (user's first instinct). Rejected drive.compaction because it splits compaction across drive (orchestration) and session (policy/schema), the worse trade. Rejected drive.context because 'context' already does triple duty in this codebase. If the merged compaction.clj grows uncomfortable, split orchestration into isaac.session.compaction.run later — start by merging.

