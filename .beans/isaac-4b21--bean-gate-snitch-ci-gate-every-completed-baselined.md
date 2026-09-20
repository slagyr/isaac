---
# isaac-4b21
title: 'Bean Gate: snitch CI — gate every completed baselined bean on main'
status: todo
type: task
priority: high
tags:
    - process
    - beans
created_at: 2026-09-19T20:43:16Z
updated_at: 2026-09-20T04:35:22Z
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
