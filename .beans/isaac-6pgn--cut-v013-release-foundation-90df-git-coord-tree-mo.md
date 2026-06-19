---
# isaac-6pgn
title: 'Cut v0.1.3 release (foundation: 90df git-coord tree + modules upgrade + show)'
status: todo
type: task
created_at: 2026-06-19T19:01:52Z
updated_at: 2026-06-19T19:01:52Z
---

Ship the post-v0.1.2 foundation work to brew users / zanebot. Everything in this
batch is FOUNDATION code, so this is a LIGHT release — likely foundation-only.

## Contents (all completed on foundation main since v0.1.2)

• isaac-90df — git-coord transitive discovery (the tree / REQUIRED BY / yi82
  conflict warnings now work for PUBLISHED modules, not just :local/root).
• isaac-qqor — `modules upgrade` (refresh installed modules to latest registry
  coords).
• isaac-7e60 — `modules show <name>` (full coord detail).
• plus any other foundation main merges since v0.1.2.

## 92p3 makes this light

Seed foundation is authoritative, so a FOUNDATION-ONLY release is enough — no
lockstep re-pinning of module repos. Re-release a module repo + bump its
registry sha ONLY if that module's own code changed this cycle (for the
90df/upgrade/show batch, foundation-only — modules unchanged).

## Checklist

1. Bump foundation manifest :version -> 0.1.3 (on main).
2. Tag foundation v0.1.3 — IMMUTABLE; never move (7tle / 5h15 lesson).
3. Auto-bump: foundation's release workflow fires the `foundation-release`
   dispatch -> 7tle's bump-isaac.yml rewrites the formula url+sha -> 0.1.3.
   VERIFY the formula bumped (no manual redeploy needed).
4. (Only if any module code changed this cycle) re-release those repos + bump
   their registry shas. Skip for a foundation-only batch.
5. Verify on a CLEAN install: brew upgrade -> isaac 0.1.3; `modules upgrade` and
   `modules show` work; on a GIT-coord install the tree surfaces transitive
   modules with REQUIRED BY and the yi82 conflicts table fires (proves 90df).
6. zanebot: brew upgrade -> 0.1.3, then `isaac modules upgrade` to refresh the
   stale comms; `modules list` shows the full tree.

## Relationships

• Follows isaac-mdtu (v0.1.2). Ships 90df/qqor/7e60.
• Uses 7tle auto-bump (no manual formula edit). Unblocked (all contents done).
