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
updated_at: 2026-09-20T04:36:48Z
parent: isaac-rmq6
blocked_by:
    - isaac-cy85
    - isaac-jp4v
---

Repo: **isaac** (this repo). Child 3 of isaac-rmq6. Blocked by isaac-jp4v — both edit `AGENTS.md`, and the overlay's wording is the base this builds on.

Do **not** edit agent-lib or the fetched toolbox copies (`.toolbox/commands/{work,verify,plan}.md`, `.toolbox/skills/*` other than the new one). The local `hail-bean-work` skill stays exactly as it is: in-flight beans and the orchestration project still use it.

## Why

The gate is only worth having if the worker uses it. Today a worker hands off `unverified` and a second full session re-reads everything to land it. With `bb bean-gate verify` green, the worker can land its own bean and CI re-checks the contract afterwards.

## Change 1 — the new skill and command

Add, as local toolbox entries registered in `.toolbox/toolbox.json` and listed in `AGENTS.md`:

- `.toolbox/skills/hail-bean-work-gate/SKILL.md`
- `.toolbox/commands/work-bean-gate.md`

Start from the existing `hail-bean-work` skill and `work.md`; keep the bootstrap, claim, checkpoint and "never hail yourself a continuation" material. What changes is the close:

1. Implement; keep `@wip` removal as the **only** edit the worker makes to any `.feature` file.
2. `bb bean-gate verify <bean-id>` from the isaac clone.
   - **exit 0** → land it: rebase, squash-merge to `main` as one commit with `Isaac-Bean` / `Isaac-Session` trailers, rewrite any downstream sibling pin to the squashed sha and re-run that repo's `bb ci` before its own squash, append `## Landed on main` with one `main-sha: <repo> <sha>` per repo, delete the bean branch locally and on the remote, then `beans update <id> --status=completed`, commit and push. No `unverified` tag, no hail to `isaac-verify`.
   - **exit 1** → the contract moved. Revert the `.feature` to the baselined text, or hail the plan band explaining the conflict. The worker **never** adds a `## Exceptions` entry and never re-baselines; both are the planner's.
   - **exit 2** (`no feature-baseline`) → the bean predates the gate: old path, `--tag=unverified` plus the verify hail, unchanged.
3. Landing rules carried over from the verify skill so nothing is lost: a merge conflict is a stop-and-hail, not something to resolve; a pin must be an ancestor of the sibling's `origin/main`; a bean without a `main-sha:` line is not `completed`.

## Change 2 — AGENTS.md `## Bean Workflow`

Replace the single flow with the two it now has: gated beans (`feature-baseline` present) are `todo → in-progress → completed` by the worker; ungated beans keep `todo → in-progress → +unverified → completed` through the verifier. Point at the new skill by name. Keep the pin rule and the cross-repo rule, which apply to both.

## Change 3 — rollout on zanebot (planner step, list it, do not attempt it)

The worker cannot reach the live config. Record in the bean exactly what the planner must run:

- install the skill at `~/.isaac/prompts/skills/hail-bean-work-gate/SKILL.md`
- change `~/.isaac/config/hail/isaac-work.md` to load `hail-bean-work-gate`
- leave `isaac-verify` and the `orchestration-*` bands untouched

## Acceptance

- Both new files exist, are registered in `.toolbox/toolbox.json`, and appear in the AGENTS.md Skills and Commands lists.
- `git diff --name-only origin/main` touches only the two new files, `.toolbox/toolbox.json`, `AGENTS.md` and the bean.
- Every command quoted in the skill runs as written: `bb bean-gate verify <id>` for all three exits, and the squash/pin sequence matches what verify.md §6a does today.
- A reader following the skill alone can take a gated bean from claim to `completed` without consulting the verify command.

Process bean: no product code, no scenarios.
