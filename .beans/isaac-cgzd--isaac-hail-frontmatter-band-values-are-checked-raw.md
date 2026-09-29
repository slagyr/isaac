---
# isaac-cgzd
title: 'isaac-hail: frontmatter band values are checked raw — create: :never is rejected and would be sent as a string'
status: completed
type: bug
priority: critical
created_at: 2026-09-29T17:34:04Z
updated_at: 2026-09-29T17:42:44Z
---

Likely repo: **isaac-hail**. Found 2026-09-29 rehearsing the zanebot deploy: `isaac config validate` against the new Hail (3a00323) fails on zanebot's live bands `ci-failure` and `tono-ci-failure` (single .md with frontmatter `create: :never`): `hail.ci-failure.create - must be one of :never, :if-missing [bad value: :never]`. The current zanebot Hail (48faa9f) validates them fine. **Blocks the zanebot deploy** (the new Agent needs the new Hail).

## Cause

isaac-5gu1 (`efa8f19`) made `band_resolve/check-config` and `apply-to-load-result!` read `(merge (:hail config) (get-in result [:raw :hail]))` so a leftover `:reach` key (dropped by conform) can be detected. But the merge lets the **raw, un-coerced** values override the conformed ones for the whole resolution: frontmatter `create: :never` arrives raw as the string ":never", fails `[:one-of? :never :if-missing]`, and at runtime would ride into the submitted turn's frequencies as a string.

## Fix

Use the raw slice only to detect the removed `:reach` key; resolve, validate, and load bands from the conformed `(:hail config)`. Same rule for any future removed-key check: read raw to *detect*, never to *resolve*.

## Scenario plan

1. A frontmatter band with `create: :never` validates (exit 0), and a send to it submits a turn whose `frequencies.create` is the keyword `:never`.

feature-baseline: isaac-hail fbbdef439bc91baba0bbec41ebed9649207b6e1f
feature-blob: isaac-hail features/bands.feature 89e74444480056c787f96a1003d9c37579e84af1 109


## Acceptance

- [ ] `bb features features/bands.feature:109` (isaac-hail, `@wip` on main at fbbdef4) passes with `@wip` removed.
- [ ] `bands.feature:33` (`:reach` still rejected) and the whole Hail suite stay green.
- [ ] Unit spec: `check-config` and `apply-to-load-result!` detect `:reach` from the raw slice but resolve bands from the conformed `(:hail config)`.
- [ ] Version bump; `bb ci` green; bump `modules.edn` `:isaac.hail` to the landed sha.


## Landed on main

main-sha: isaac-hail e95274a3fac7b9285c00bddac21544199624103f
