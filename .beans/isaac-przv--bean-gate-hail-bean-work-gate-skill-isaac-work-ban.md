---
# isaac-przv
title: 'Bean Gate: hail-bean-work-gate skill + isaac-work band cutover'
status: completed
type: task
priority: high
tags:
    - process
    - beans
created_at: 2026-09-19T20:43:16Z
updated_at: 2026-09-20T18:03:45Z
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

Dispatched: hail c9bee05a 2026-09-20T04:58:37Z (band isaac-work)


## Rollout on zanebot (planner step — NOT done by this bean)

The worker cannot reach the live config. A planner (or the human) must run, on
zanebot:

1. Install the skill for the band loader:

       mkdir -p ~/.isaac/prompts/skills/hail-bean-work-gate
       cp <isaac-clone>/.toolbox/skills/hail-bean-work-gate/SKILL.md \
          ~/.isaac/prompts/skills/hail-bean-work-gate/SKILL.md

2. Edit `~/.isaac/config/hail/isaac-work.md` so its prompt loads
   `hail-bean-work-gate` instead of `hail-bean-work` (one word in the
   `Load and follow the "…" skill.` line). Config hot-reloads — no service
   restart.
3. Leave `~/.isaac/config/hail/isaac-verify.md` and every `orchestration-*`
   band untouched: the orchestration project and in-flight ungated beans still
   use `hail-bean-work` / `isaac-verify`.

Nothing else on zanebot changes. `~/.isaac/prompts/skills/hail-bean-work/SKILL.md`
stays installed.

## Handoff (2026-09-20)

branch: bean/isaac-przv @ 0f65b62e (base origin/main@7c80ca43)

`bb bean-gate verify isaac-przv` → **exit 2** (this bean has no
`feature-baseline:`), so per its own exit-2 rule this bean takes the old path:
`unverified` + hail `isaac-verify`.

Delivered:

- `.toolbox/skills/hail-bean-work-gate/SKILL.md` — bootstrap/claim/checkpoint/
  workspace material carried over from `hail-bean-work`; new close: gate →
  exit 0 land (rebase, `bb ci`, squash-merge with trailers, downstream repin +
  re-`bb ci` before its squash, gate re-run on the squash commit,
  `## Landed on main` + `main-sha:` per repo, delete branch,
  `--status=completed`), exit 1 revert-or-hail-plan (never `## Exceptions`,
  never re-baseline), exit 2 old path. Carries the verify.md landing rules:
  conflict = stop-and-hail, a pin must be an ancestor of the sibling's
  `origin/main`, no `main-sha:` line → not completed. Self-contained:
  notifications, limbo/HOLD rules, trailers.
- `.toolbox/commands/work-bean-gate.md` — short path (`/work-bean-gate`),
  targeted-vs-queue rules, gate exit table, landing steps, traps.
- `.toolbox/toolbox.json` — both registered as local entries (relative `url`,
  sha256), like the existing `hail-bean-work`.
- `AGENTS.md` — Skills + Commands lists updated; `## Bean Workflow` now has two
  flows (gated: worker lands and completes; ungated: `unverified` → verifier);
  the dual-run paragraph rewritten as the verifier's role during the drain.

Untouched, as the bean requires: `.toolbox/skills/hail-bean-work/`,
`.toolbox/commands/{work,verify,plan,plan-with-features,todo}.md`, agent-lib.

Evidence — every command quoted in the skill was run, all three exits, on a
scratch harness (a throwaway isaac copy plus an `isaac-mod` module repo with a
bare remote, under /tmp; removed afterwards):

- planner `bb bean-gate baseline scratch-gate isaac-mod:features/marigold.feature --dir isaac-mod=…` → exit 0, appended `feature-baseline:` / `feature-blob:`
- worker branch with `@wip` removed → `bb bean-gate verify scratch-gate --dir …` → **exit 0**, `PASS (isaac-mod @ HEAD d665111)`
- weakened a `Then` step → **exit 1**, two FAIL lines (changed baselined block + worker diff beyond `@wip` removal)
- the quoted revert recipe (`git checkout <feature-baseline sha> -- <path>`, re-remove `@wip`) → back to **exit 0**
- landing steps as written (`git fetch` / `rebase`, `merge --squash`, commit with trailers, `git rev-parse HEAD`) → gate re-run on the squash commit exit 0, `PASS (isaac-mod @ HEAD 9072bb7)`; after appending `main-sha:` to the bean → `PASS (isaac-mod @ main-sha 9072bb7)`, confirming the skill's claim that a recorded `main-sha` is the commit checked
- real ungated bean: `bb bean-gate verify isaac-przv` → **exit 2**, `no feature-baseline: use the verify path`

`bb ci` in isaac on the branch: 45 examples, 0 failures.

Verify hail: 1d18a9fe 2026-09-20T05:40:50Z (band isaac-verify; prior verify turn b96bd90f ended without updating the bean)
