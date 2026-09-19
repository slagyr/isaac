---
# isaac-przv
title: 'Bean Gate: hail-bean-work-gate skill + isaac-work band cutover'
status: draft
type: task
priority: high
tags:
    - process
    - beans
created_at: 2026-09-19T20:43:16Z
updated_at: 2026-09-19T20:43:16Z
parent: isaac-rmq6
blocked_by:
    - isaac-cy85
---

Child 3 of isaac-rmq6. Draft until isaac-cy85 lands.

New Isaac-only worker skill `hail-bean-work-gate` + command `work-bean-gate` in this repo (raw.githubusercontent.com/slagyr/isaac/…); the orchestration copy of hail-bean-work and agent-lib stay untouched. The skill absorbs the verify skill's land steps (squash-merge to one commit, `main-sha:` lines, downstream repin before squash, branch delete) and closes with `bb bean-gate verify <id>` green → `completed`. Gate red → revert the `.feature` or hail plan; never add `## Exceptions`. Beans without `feature-baseline` keep the old unverified → verify path. AGENTS.md `## Bean Workflow` updated to match. Zanebot: `~/.isaac/config/hail/isaac-work.md` names the new skill; the skill is installed under `~/.isaac/prompts/skills/`.
