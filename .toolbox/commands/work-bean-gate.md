---
name: work-bean-gate
description: Pick up the next ready bean, implement it, and land it yourself when `bb bean-gate verify` is green. Use when the user says "/work-bean-gate" or when a gated bean is assigned by hail.
user-invocable: true
---

# Work a Gated Bean

Same as `/work` through implementation; the difference is the close. When
`bb bean-gate verify <id>` exits **0** you land the bean on `main` and mark it
`completed` yourself — no `unverified` tag, no verify hail. Isaac CI re-runs the
same gate on every completed baselined bean that reaches main, so the contract
is still checked by something other than you.

Full mechanics (bootstrap, branch protocol, landing, exit 1 / exit 2 paths,
notifications) live in
[`.toolbox/skills/hail-bean-work-gate/SKILL.md`](../skills/hail-bean-work-gate/SKILL.md).
This file is the short path.

## Hail-driven bootstrap

If you arrived via hail (band/skill) rather than `/work-bean-gate`:

1. **Locate the isaac clone** — the git repo with `.beans/`. Session cwd may be
   your role home, not this repo.
2. **`git pull --rebase`** in that clone before any `beans` read. Run it alone;
   a parallel `beans show` can race the pull and return stale data.
3. **Skills fallback** — if `list_skills` is empty or `load_skill` fails, read
   `isaac/.toolbox/skills/hail-bean-work-gate/SKILL.md` and this file directly;
   do not stop.
4. Continue below from the isaac clone (claim beans here; edit module repos per
   bean scope).

## Steps

1. `git pull --rebase` (alone).
2. Branch on `$ARGUMENTS`:
   - **A bean ID was provided** → work that exact bean. Never substitute
     another, not its dependencies, not the next ready bean. If it cannot be
     worked (`completed` / `scrapped` / already `in-progress` / `draft` /
     blocked), **stop and report** — let the user or planner decide.
   - **No argument** → `beans list --ready`, take the highest priority
     (`critical` > `high` > `normal` > `low` > `deferred`).
3. `beans show <id>` — read the whole body: acceptance, `feature-baseline:` /
   `feature-blob:` lines, any `## Exceptions`, and any planner or fail notes.
4. `beans update <id> --status=in-progress`, then commit + push the claim:
   `git add .beans && git commit --trailer "Isaac-Session: <session-id>" --trailer "Isaac-Bean: <id>" -m "<id>: claim" && git push`.
5. Implement on a `bean/<id>` branch in the repo the bean names, TDD per the
   project skills. **Removing `@wip` is the only edit you make to a `.feature`
   file.** Commit and push the branch after every green run.
6. Run the bean's acceptance suites green (`bb ci`, else `bb spec` and
   `bb features`).
7. Close by the gate, from the isaac clone:

   ```sh
   bb bean-gate verify <id>
   ```

   | Exit | Meaning | Close |
   |------|---------|-------|
   | 0 | PASS | Land it (below), then `--status=completed` |
   | 1 | FAIL — contract moved | Revert the `.feature` to the baselined text and re-run, or hail the plan band |
   | 2 | not gated / usage error | `--tag=unverified` (stay `in-progress`) + hail the verify band |

## Landing (exit 0)

Upstream repo first, then each downstream repo:

1. `git fetch origin && git checkout bean/<id> && git rebase origin/main`, then
   `bb ci`. A rebase conflict is a **stop-and-hail** (plan band), not something
   to resolve.
2. `git checkout main && git pull --ff-only origin main && git merge --squash bean/<id>`,
   then commit with `Isaac-Bean` / `Isaac-Session` trailers and record
   `git rev-parse HEAD` as that repo's `main-sha`. A merge conflict is also a
   stop-and-hail: `git reset --hard origin/main` and leave the branch alone.
3. **Downstream: repin before its own squash.** Rewrite any `deps.edn` /
   `bb.edn` pin still naming the upstream pre-squash sha to the upstream
   `main-sha`, commit on the bean branch, re-run that repo's `bb ci`, and only
   then squash it. A pin must be reachable from that repo's `main`
   (`git merge-base --is-ancestor <sha> origin/main`) — never a bean-branch sha.
4. Re-run `bb bean-gate verify <id>` on the squash commits before pushing, in
   case main moved under the branch. Non-zero → `git reset --hard origin/main`
   and treat it as exit 1. Then push each repo's `main`.
5. Append to the bean and commit:

   ```
   ## Landed on main (<YYYY-MM-DD>)

   main-sha: <repo> <sha>
   ```

   One line per repo. **No `main-sha:` line → the bean cannot be `completed`.**
6. Delete the branch (`git branch -D bean/<id>`,
   `git push origin --delete bean/<id>`, remove any worktree first).
7. `beans update <id> --status=completed`, commit + push `.beans/`.

## Common Traps

- **Premature close.** The gate checks the contract, not whether you built the
  thing. Run the suites and confirm `@wip` is gone before you even run the gate.
- **Editing the contract to make the gate pass.** Reverting a feature file to
  its baselined text is allowed; rewording it, re-baselining it, or adding a
  `## Exceptions` entry is the planner's job and a gate failure from you.
- **Landing without rebasing.** A squash of a stale branch silently drops
  someone else's landed work out of the tree you tested.
- **Multi-worker collisions.** If another worker claimed the bean while you
  read it, your push is rejected: `git pull --rebase`, look at the new state,
  and back off or continue. Never force-push.

## Arguments

$ARGUMENTS - Optional: a specific bean ID. When provided it is a hard
constraint — work that exact bean or stop and report why it cannot be worked.
