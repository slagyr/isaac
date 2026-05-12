---
# isaac-nwc9
title: "Config validation: warn on dangling .md files"
status: completed
type: feature
priority: low
created_at: 2026-04-22T17:48:22Z
updated_at: 2026-04-22T19:53:11Z
---

## Description

Today isaac.config.loader scans crew/*.edn, models/*.edn, providers/*.edn and picks up companion .md files (e.g. crew/<id>.md for soul). A lone crew/foo.md without a crew/foo.edn is silently ignored — a typo or half-done config drops through with no signal.

Scope:
- At config load, scan crew/, models/, providers/, cron/ for .md files whose basename doesn't match an existing .edn (for crew/models/providers) or config entry (for cron).
- Emit a warning per dangling file, keyed under the directory (e.g., warnings entry 'crew.foo.md: no matching crew entry').
- Warnings surface through the same path validate/compose currently uses — visible via isaac config validate output.

Does NOT create entity entries from dangling .md files (too magic). Does NOT error (user may be in the middle of editing); warning is strong enough.

Future (separate bead): strict mode that promotes these to errors.

## Acceptance Criteria

1. Extend config loader to scan .md files and warn on dangling ones across crew/, models/, providers/, cron/.
2. Warnings surface through the existing stderr path used by config validate/print.
3. No auto-creation of entities; no errors for dangling .md.
4. Remove @wip from both scenarios in features/config/dangling_md.feature.
5. bb features features/config/dangling_md.feature passes (2 examples).
6. bb features passes overall.
7. bb spec passes.

## Design

Implementation notes:
- Extend isaac.config.loader's directory-scanning step to also enumerate .md files alongside .edn. When a .md exists whose basename has no matching .edn (for crew/models/providers) or :cron entry (for cron/), append a warning to :warnings in the load result.
- Warning shape: {:key 'crew/ghost.md' :value 'dangling: no matching crew entry'} or similar — reuse the existing warnings mechanism that surfaces via isaac config output.
- Dirs to check: crew/, models/, providers/, cron/. Each maps to a different 'matching entry' rule:
  - crew/<id>.md needs crew/<id>.edn OR :crew {<id> ...} inline
  - models/<id>.md needs models/<id>.edn OR :models {<id> ...} (not sure if models even use companion md — validate scope)
  - providers/<id>.md likewise
  - cron/<id>.md needs :cron {<id> ...} in isaac.edn
- Don't auto-create entity entries from dangling .md files. Don't error — warn only.

Test infra: existing 'config file "<path>" containing:' step already seeds files; no new step-defs needed.

## Notes

Added dangling .md companion warnings across crew/models/providers/cron during config load, surfaced through existing warnings output, with focused loader spec coverage and feature coverage in commit 330cbf3. Verified with bb features features/config/dangling_md.feature, bb features, and bb spec.

