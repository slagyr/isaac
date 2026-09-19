---
# isaac-cy85
title: 'bb bean-gate: planner baseline + contract/feature gate (Bean Gate child 1)'
status: completed
type: task
priority: high
tags:
    - process
    - beans
created_at: 2026-09-19T20:42:56Z
updated_at: 2026-09-19T20:57:41Z
parent: isaac-rmq6
---

Repo: **isaac** (this repo, the beans tracker). Child 1 of isaac-rmq6 (Bean Gate). Do **not** change the `beans` CLI. Do not touch `.github/workflows/ci-tests.yml` (frozen) or any skill/command file — later children own those.

Work on `bean/isaac-cy85` in a worktree of the isaac clone (`git worktree add ../isaac-isaac-cy85 -b bean/isaac-cy85 origin/main`); bean notes stay on main as usual. This bean itself runs the **old** path (unverified → isaac-verify); it has no `feature-baseline`.

## What to build

`bb bean-gate baseline …` and `bb bean-gate verify …`, pure babashka + the `git` CLI, in `scripts/isaac/bean_gate.clj` (or similar) with speclj specs under `spec/`.

A new `bb.edn` at the repo root (the monolith's was deleted in 8fceeb17) with: `bean-gate` (the command), `spec` (speclj, same 3.13.0 as the modules), `ci` (= spec; the tracked `.githooks/pre-push` already runs `bb ci` whenever a push touches `.clj`/`.edn`, so it must exist and be green), and `hooks:install` (`git config core.hooksPath .githooks`, as in isaac-foundation's bb.edn — AGENTS.md already tells fresh checkouts to run it).

## Bean fields (appended lines in the bean body)

```
feature-baseline: <repo> <module-main-sha>
feature-blob: <repo> <path> <blob-sha>
feature-blob: <repo> <path> <blob-sha> <line>,<line>
```

- `feature-baseline` — the module's `origin/main` sha when the planner baselined.
- `feature-blob` — the git blob id of `<sha>:<path>`. Optional trailing `<line>,<line>` names this bean's scenarios by the line of their `Scenario:`/`Scenario Outline:` keyword **in that blob** (gherclj `file:line` convention). No line list = every `@wip` scenario in the file (and a feature-level `@wip`) belongs to the bean.
- Several repos / files → several lines. A re-baseline appends new lines; the **last** `feature-baseline` per repo and the **last** `feature-blob` per (repo, path) are in force.

## `bb bean-gate baseline <bean-id> <repo>:<path>[:<line>…] …`

Run by the planner from the isaac clone after the `@wip` scenarios are pushed to the module's `main`.

1. Locate the bean file `.beans/<id>--*.md` (error if none).
2. Locate each module checkout: default `../<repo>` beside the isaac clone; `--dir <repo>=<path>` overrides (worktrees). `git fetch origin` there.
3. sha = `origin/main`. For each path: the file must exist at that sha (error otherwise) and the selected scenarios must carry `@wip` (a baseline with nothing to implement is a planner error — refuse, naming the path/line). Blob = `git rev-parse <sha>:<path>`.
4. Append one `feature-baseline` line per repo and one `feature-blob` line per path to the bean file (direct file append, no beans CLI), and print the appended lines. Do not commit — the planner commits the bean.

## `bb bean-gate verify <bean-id> [--dir <repo>=<path>] [--ref <repo>=<ref>]`

Exit 0 = pass, 1 = gate fail (print every failure, not just the first), 2 = not gated (no `feature-baseline` line — print `no feature-baseline: use the verify path`) or usage error.

**Which commit is checked, per repo:** if the bean has a `main-sha: <repo> <sha>` line, that commit, and the "worker diff" is `<sha>^..<sha>` (one diff per `main-sha` line when there are several for a repo; the file checks run at the one that descends from the others). Otherwise the checkout's `HEAD` (or `--ref`), with worker diff `merge-base(origin/main, ref)..ref`. So the same command serves the worker on the bean branch before landing, anyone after landing, and CI.

**Checks** (report all failures):

1. **Baseline sanity** — each baseline sha is an ancestor of the module's `origin/main`; each recorded blob equals `git rev-parse <baseline-sha>:<path>`.
2. **Contract lines are append-only** — contract lines are every `feature-baseline:` / `feature-blob:` line and every non-blank line under a `## Acceptance…` or `## Exceptions` heading (section ends at the next `## `). Walk the bean file's git history in the isaac repo (follow renames — the slug changes with the title) starting at the commit that first introduced a `feature-baseline` line; the working-tree file is the newest version. Each version's contract-line set must contain the previous version's. A removed or edited line fails and names the commit that dropped it.
3. **Baselines are planner commits** — fail if the commit that introduced any `feature-baseline`/`feature-blob` line carries an `Isaac-Session: isaac-work…` or `Isaac-Session: isaac-verify…` trailer.
4. **Baselined text is intact** — split both the recorded blob and the file at the checked commit into blocks: the feature header (tag lines + `Feature:` + description), `Background:`, and each scenario (its preceding tag and comment lines, the keyword line, steps, tables, `Examples:`). Normalize: drop blank lines, trim trailing whitespace, remove the `@wip` token from tag lines (and drop a tag line left empty) — on **both** sides, so other beans' `@wip` in the same file never matters. Every baseline block must appear as an identical contiguous block in the checked file. Extra blocks (scenarios other beans added later) are fine. A missing, reworded, re-tagged (`@slow`, `@skip`, …) or re-ordered-steps block fails, printed as a short diff.
5. **The bean's scenarios are live** — none of this bean's scenarios (the line list, or all baseline `@wip` scenarios) still carries `@wip` at the checked commit, and neither does the feature line when the whole feature was `@wip`.
6. **The worker diff touches features only by removing `@wip`** — in every `.feature` file under the module (baselined or not), each changed line in the worker diff is a tag line whose only change is dropping `@wip`, and only in baselined files. Anything else fails, naming file and line. (Planner-authorized feature edits happen on main before a re-baseline, never in the worker diff.)

The gate does **not** run tests; module CI and the worker's `bb ci` own that.

## Specs (speclj, required cases)

Fixtures build throwaway git repos under `target/` (an isaac repo with `.beans/`, a module repo with a bare `origin`), never the real checkouts or network.

- baseline appends one baseline + one blob line per file; line-list form; refuses a path with no `@wip`; refuses a missing file; `--dir` override.
- verify: exit 2 without a baseline; pass on a pure `@wip` removal; pass when another bean later adds a scenario (with or without `@wip`) to the same file; pass when another bean's `@wip` scenario in the file is still `@wip`; fail on a reworded step, a deleted baselined scenario, an added `@slow`, a step inserted into a baselined scenario, an untouched `@wip` on the bean's scenario, a non-`@wip` edit to an unrelated `.feature` in the worker diff; fail when a contract line is edited or removed in a later commit; pass when a line is appended; fail when a baseline line was introduced by an `Isaac-Session: isaac-work-1` commit; re-baseline: the newer blob is in force; `main-sha` mode checks the squash commit's own diff.

## Acceptance

```
cd isaac && bb ci
bb bean-gate --help            # usage for both subcommands, exit 0
bb bean-gate verify isaac-rmq6 # exit 2, "no feature-baseline: use the verify path"
```

## Exceptions

(none)

Dispatched: hail 1542773b 2026-09-19T20:43:42Z (band isaac-work)



## Implementation notes (scrapper@isaac-work-1)

branch: bean/isaac-cy85 @ ff464071 (base origin/main@6b9c4adf)

- `bb.edn` at repo root: bean-gate, spec (speclj 3.13.0), ci (= spec), hooks:install
- `src/isaac/bean_gate.clj` — baseline + verify (git CLI, throwaway fixtures in specs)
- `spec/isaac/bean_gate_spec.clj` — required cases (21 examples)

Acceptance:
- `cd isaac && bb ci` → 21/0
- `bb bean-gate --help` exit 0
- `bb bean-gate verify isaac-rmq6` exit 2, "no feature-baseline: use the verify path"

This bean has no feature-baseline; old unverified path.



## Landed on main (2026-09-19)

main-sha: isaac 53ea1cf63c9d6fbc156f1aea425e49658f6ac325

## Replaced on main (2026-09-19, Micah)

The worker-landed gate crashed on gate lines inside code fences (this bean's own example block). Replaced with the split implementation (`isaac.bean-gate.*`, 32 specs, fenced lines ignored). `bb ci` green; `bb bean-gate verify isaac-cy85` and `isaac-rmq6` exit 2.

main-sha: isaac 7b3cab5a1cc40689d3dc1bed1e69c0e5f96f653b
