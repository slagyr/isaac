---
# isaac-2nkg
title: Format EDN written by config mutations
status: todo
type: task
priority: normal
created_at: 2026-09-03T15:45:13Z
updated_at: 2026-09-03T15:45:13Z
---

Make every EDN write performed by `isaac config set` and `isaac config unset` use `isaac.util.edn/pretty`, rather than `pr-str`. This is a clean cutover: newly changed EDN files should have Foundation’s human-readable layout and one trailing newline.

## Acceptance criteria

- `isaac config set` rewrites the affected root or entity `.edn` file with `isaac.util.edn/pretty` formatting and exactly one trailing newline.
- `isaac config unset` does the same for its affected `.edn` file.
- Mutations that target companion Markdown retain their existing Markdown behavior; only EDN serialization changes.
- Existing config mutation validation, atomic staging, and file-selection behavior remain unchanged.
- Focused mutation specs cover both set and unset output; the Foundation config CLI suite passes.
