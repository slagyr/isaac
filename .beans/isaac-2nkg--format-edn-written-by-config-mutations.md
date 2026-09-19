---
# isaac-2nkg
title: Format EDN written by config mutations
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-03T15:45:13Z
updated_at: 2026-09-19T00:11:58Z
---

Make every EDN write performed by `isaac config set` and `isaac config unset` use `isaac.util.edn/pretty`, rather than `pr-str`. This is a clean cutover: newly changed EDN files should have Foundation’s human-readable layout and one trailing newline.

## Acceptance criteria

- `isaac config set` rewrites the affected root or entity `.edn` file with `isaac.util.edn/pretty` formatting and exactly one trailing newline.
- `isaac config unset` does the same for its affected `.edn` file.
- Mutations that target companion Markdown retain their existing Markdown behavior; only EDN serialization changes.
- Existing config mutation validation, atomic staging, and file-selection behavior remain unchanged.
- Focused mutation specs cover both set and unset output; the Foundation config CLI suite passes.

Dispatched: hail c9284332 2026-09-19T00:07:21Z (band isaac-work)

## Handoff (scrapper@isaac-work-3)

branch: bean/isaac-2nkg @ f10b37d (base origin/main@0b120cc)

`isaac.config.mutate/update-edn-file` writes `(str (edn-pretty/pretty data) "\n")`. Companion Markdown unchanged. mutate_spec covers set and unset pretty+newline. `bb spec spec/isaac/config/mutate_spec.clj` 22/0; CLI config suite 76/0.
