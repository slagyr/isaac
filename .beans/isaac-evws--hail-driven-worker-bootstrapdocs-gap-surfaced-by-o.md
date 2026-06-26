---
# isaac-evws
title: Hail-driven worker bootstrap/docs gap surfaced by orchestration smoke test
status: in-progress
type: task
priority: low
tags:
    - unverified
created_at: 2026-06-26T16:39:58Z
updated_at: 2026-06-26T20:15:00Z
---

## Summary
Hail-driven worker bootstrap for process/bean work is underspecified when the promised checkout/session path does not exist locally and referenced skills are not discoverable in-session.

## Problem
During the orc1 process-smoke run, the worker hail implied a ready checkout/session context, but the stated `isaac-1` / `quarters` path was not present. The worker had to manually discover the actual repo checkout before claiming the bean. Also, `list_skills` returned no discovered skills even though the workflow references hail/bean-work skills.

## Desired outcome
A hail-driven worker should be able to start from the hail alone and know:
- the authoritative repo checkout path or how to derive it
- whether the referenced skills are expected to be available
- the documented fallback path when skill discovery fails
- the minimal expected procedure for no-op process-test beans

## Acceptance ideas
- Worker docs or skill specify authoritative repo/session discovery.
- Worker docs include a fallback when `list_skills` returns empty.
- No-op orchestration/process-test beans have an explicit worker checklist.

## Additional context (added during planner review of orc1 run)

The "real work" (bean edits, `beans` CLI, git add/commit/push) was performed via explicit `exec` tool calls with `workdir: "/Users/zane/Projects/isaac/isaac-live"`, even though the session metadata and hail targeting used a different CWD.

Observed structure on zanebot:
- Session for isaac-work-1 (scrapper, :isaac) has `cwd: "/Users/zane/agents/isaac/work-1"` (sometimes surfaces as older `.isaac/crew/scrapper/isaac-1`).
- `/Users/zane/agents/isaac/` (and parallel local `/Users/micahmartin/agents/`) contains lightweight named work areas: `plan/`, `work-1/`, `verify/`, etc. These appear intended as the "shared agents/isaac-* checkout" or "quarters" for :isaac-tagged sessions (see init message: "This is the isaac-1 checkout in quarters").
- The full git clone with isaac source, `.beans/` (isaac- prefixed beans), `.beans.yml`, and where actual development + commits happen lives at `/Users/zane/Projects/isaac/isaac-live` (remote: git@github.com:slagyr/isaac.git, on main).
- The isaac server/process is often launched from within a Projects/isaac/* worktree, causing default/process CWDs to bleed through to sessions (see prior beans: isaac-j1ju, isaac-29y5 documenting cwd resolution bugs where `/status` and hooks report the launch dir instead of session cwd).

The hail-bean-work skill (in ~/.isaac/prompts and the band) assumes: "the session cwd is set to the shared agents/isaac-* checkout."

In the run, the worker had to manually discover (pwd, ls, git rev-parse, glob, read dirs) and then chdir via workdir= to isaac-live for every git/beans operation.

This is a distinct bootstrap mismatch beyond the earlier "path not present" + skills issues.

The evws bean itself was created inside the isaac-live `.beans/` and pushed from there (commits visible in the worker transcript).

## Follow-up actions for next planner/worker
- Clarify the intended relationship between `agents/*/isaac*` work areas vs `Projects/isaac/*-live` clones.
- Ensure hail targeting for :isaac (work/verify/plan) reliably sets session CWD to the correct full checkout.
- Update skill docs, init messages, and any session provisioning (ACP, hooks, quarters setup) to match reality.
- Decide whether work areas should be full clones, git worktrees, or symlinks into the live clone.
- Re-test orc1 after any changes to confirm the worker lands in the right place without manual chdir discovery.

Added planner context on dual-checkout structure (agents work areas vs Projects/isaac/isaac-live).

## Resolution (work-3, 2026-06-26)

Documented the bootstrap path surfaced by `isaac-orc1`:

- **`agents/AGENTS.md`** — agent homes vs repo checkouts table; hail-driven worker
  section with `list_skills` fallback.
- **`isaac/.toolbox/skills/hail-bean-work/SKILL.md`** — bootstrap checklist,
  cwd vs worktree layout, normal vs process-test beans, verify handoff.
- **`isaac/.toolbox/commands/work.md`** — hail bootstrap + process-test sections;
  note that `beans list --all` does not exist.
- **`isaac/AGENTS.md`** — skill registered in toolbox list.
- **`work-3/AGENTS.md`** — pointer for hail-driven workers.

No product code changes (docs-only bean). Re-test `isaac-orc1` after deploy to
confirm a hail worker can follow the checklist without manual discovery.
