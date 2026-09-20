---
# isaac-4b21
title: 'Bean Gate: snitch CI — gate every completed baselined bean on main'
status: in-progress
type: task
priority: high
tags:
    - process
    - beans
    - unverified
created_at: 2026-09-19T20:43:16Z
updated_at: 2026-09-20T04:47:31Z
parent: isaac-rmq6
blocked_by:
    - isaac-cy85
---

Repo: **isaac** (this repo). Child 4 of isaac-rmq6. Unblocked: isaac-cy85 landed `bb bean-gate` on main (7b3cab5).

Do **not** touch `.github/workflows/ci-tests.yml` (frozen) or `.toolbox/`.

## Why

Under the Bean Gate the worker lands its own bean and marks it `completed`, so nothing re-checks the contract after the fact. CI becomes the snitch: every completed, baselined bean is re-gated on main, where nobody can quietly skip it.

## Change 1 — `bean-gate ci-scan` (keeps the YAML thin and testable)

Add a third subcommand: `bb bean-gate ci-scan <before-sha> <after-sha>`, printing one bean id per line — every bean whose file changed in that range, whose status is now `completed`, and whose body carries a `feature-baseline` line. Exit 0 always (an empty list is normal). `--edn` prints `{:beans [...] :skipped [{:id … :reason :not-completed|:not-gated}]}` so the workflow can log what it ignored.

Specs cover: a bean that became completed and is gated is listed; one that is completed but ungated is skipped as `:not-gated`; one still `in-progress` is skipped; a bean file touched but unchanged in status is still listed when it is completed and gated (a re-push must not slip past); a deleted bean file is ignored; several beans in one push all appear.

## Change 2 — the workflow

`.github/workflows/bean-gate.yml`: on `push` to `main` with `paths: ['.beans/**']`.

- Checkout with `fetch-depth: 0` — the gate walks the bean file's history to prove the contract lines are append-only.
- Set up Java 21 + babashka exactly as the other module workflows do.
- `bb bean-gate ci-scan ${{ github.event.before }} ${{ github.sha }}`. Empty → job succeeds, nothing else runs.
- For each id, clone each repo named by its `feature-baseline` lines to `../<repo>` over **https** (all slagyr/isaac-* repos are public, so no token) with `--filter=blob:none`; that keeps the clone small while leaving every commit reachable, which the baseline sha and the `main-sha^..main-sha` diff both need.
- Run `bb bean-gate verify <id>` per bean. Exit 0 pass, 1 fail the job, 2 impossible here (ci-scan already filtered) — treat it as a failure with a clear message if it happens.
- Job summary lists each bean and its verdict, so a failure reads without opening logs.
- Force-push edge: when `github.event.before` is all zeros or unreachable, fall back to the push's own commit list rather than dying.

## Change 3 — wire the ping

Add this workflow's name to the `workflows:` list in `ci-failure-hail.yml` so a red gate hails Isaac like a red suite does.

**Known gap, do not try to fix here:** that hail currently 401s because the repo secret `ISAAC_SERVER_AUTH_TOKEN` no longer matches zanebot's token. Note it in the bean and move on; Micah resets the secret or mints a `ci` principal (isaac-xo5p).

## Acceptance

```
bb spec           # ci-scan specs green
bb ci
bb bean-gate ci-scan <sha-before-a-known-completed-bean> <sha-after>   # prints that bean id
```

Plus: the workflow file parses (`bb -e "(require '[clj-yaml.core :as y]) (y/parse-string (slurp \".github/workflows/bean-gate.yml\"))"` or equivalent), and a dry narrative in the bean showing the exact commands the job would run for one real completed bean.

Dispatched: hail de9c8021 2026-09-20T04:35:35Z (band isaac-work)

## Handoff (2026-09-20)

branch: `bean/isaac-4b21` @ 3810e11 (base `origin/main`@36bec47)

**Change 1 — `bb bean-gate ci-scan <before> <after> [--edn]`**
`isaac.bean-gate.core/ci-scan` diffs `.beans` across the range
(`git diff --name-status --no-renames`), reads each changed path at the after
sha, and classifies it: completed + `feature-baseline` → listed; otherwise
`:skipped` with `:not-completed` or `:not-gated`. Deletions, non-bean paths,
and paths that no longer exist at the after sha are ignored. Supporting
seams: `git/diff-name-status`, `bean/status` + `bean/completed?` (front-matter
read). Specs: `spec/isaac/bean_gate/ci_scan_spec.clj` (13 examples) covering
became-completed, completed-but-ungated, still-in-progress, touched-but-status-
unchanged, deleted file, several beans in one push, non-`.beans` paths, an
unusable range, plus the command's stdout/exit contract.

Decisions worth naming:
- **Exit 0 always**, as specified — including an unusable range, which prints
  `bean-gate ci-scan: cannot diff …` on **stderr** so stdout stays a clean id
  list. The workflow resolves the range before calling, so this is a backstop.
- **Only direct children of `.beans/`** are scanned (`<id>--<slug>.md`);
  `.beans/archive/**` is ignored, so archiving never re-gates old beans. The
  gate fires when the bean is completed on main, which precedes archiving.
  Edge case left open on purpose: completing *and* archiving in one push is
  a rename (D + A) and gates nothing.
- Output is sorted and deduped by bean id.

**Change 2 — `.github/workflows/bean-gate.yml`**
push to `main`, `paths: ['.beans/**']`, `fetch-depth: 0`, Java 21 + babashka
as the module workflows do. Steps: resolve the range → `ci-scan` (plain to
`/tmp/bean-gate-ids.txt`, `--edn` to the summary) → clone each repo named by
the beans' `feature-baseline` lines to `../<repo>` over public https with
`--filter=blob:none` → `bb bean-gate verify <id>` per bean, exit 0 PASS,
2 UNEXPECTED (ci-scan already filtered), anything else FAIL; every verdict
lands in the job summary. `ci-tests.yml` and `.toolbox/` untouched.

Force-push edge, all four branches exercised locally against this checkout:
`before` usable → used as is; zeros or unreachable → first id from
`toJSON(github.event.commits)` and its parent; no commit list → `<after>^`;
root commit → the empty tree. Expression values reach the script through
`env:`, never interpolated into the shell, so a commit message with quotes
cannot break it. Baseline tokens that are not repo names (the `<repo>`
placeholder inside cy85's fenced docs) are filtered before cloning.

**Change 3 —** `Bean Gate` added to the `workflows:` list in
`ci-failure-hail.yml`.

**Known gap (not fixed here, per the bean):** that hail 401s — the repo secret
`ISAAC_SERVER_AUTH_TOKEN` no longer matches zanebot's token. Micah resets the
secret or mints a `ci` principal (isaac-xo5p). Until then a red gate is
visible in the Actions tab and the job summary but does not hail.

### Acceptance runs

```
bb spec   # 45 examples, 0 failures, 67 assertions
bb ci     # same (ci = spec here); the pre-push hook ran it again on push
bb -e "(require '[clj-yaml.core :as y]) (y/parse-string (slurp \".github/workflows/bean-gate.yml\"))"
          # parses; keys (:name true :permissions :jobs) — `on:` reads as YAML-1.1 true, as in every other workflow here
```

`ci-scan` on real history, isaac-bsqm's completion push (664fdd93):

```
$ bb bean-gate ci-scan 664fdd93^ 664fdd93 --edn
{:beans [], :skipped [{:id "isaac-bsqm", :reason :not-gated}]}
```

Correctly empty: **no bean in this repo carries a `feature-baseline` yet**, the
Bean Gate having just been built. So the dry narrative below uses a scratch
clone where one real completed bean (isaac-cy85, `status: completed`) is
baselined the way the planner will baseline it.

### Dry narrative — exactly what the job runs

```
$ git clone -q <isaac> /tmp/bean-gate-demo/isaac && cd /tmp/bean-gate-demo/isaac
$ printf '\nfeature-baseline: isaac-foundation 294321def2b201a455758fe476efbe1a736316e9\n' \
    >> .beans/isaac-cy85--bb-bean-gate-planner-baseline-contractfeature-gate.md
$ git commit -qam "plan: baseline isaac-cy85 (demo)"      # the push the gate reacts to

# step "Resolve the pushed range"
range b69042d4cd5e64c581d815ed657419a77d7372d1..70569f49dcfb677858af4edbceb6042db281250f

# step "Scan the push for completed, baselined beans"
$ bb bean-gate ci-scan b69042d4 70569f49 --edn
{:beans ["isaac-cy85"], :skipped []}
$ bb bean-gate ci-scan b69042d4 70569f49
isaac-cy85

# step "Re-gate each bean" — one repo named by the bean's baseline lines
$ git clone --filter=blob:none --quiet https://github.com/slagyr/isaac-foundation.git ../isaac-foundation
$ bb bean-gate verify isaac-cy85
isaac-cy85 bean-gate: PASS (isaac-foundation @ HEAD 80c6c1e)   # exit 0 → ✅ PASS in the job summary
```

(The verify line above was run with `--dir isaac-foundation=<existing checkout>`
instead of the clone; on the runner the clone puts it at `../isaac-foundation`,
which is where `bean-gate` looks by default. Note also that when a bean
baselines `isaac` itself, `../isaac` on the runner *is* the workspace checkout,
so nothing is cloned and the gate reads the pushed sha directly.)
