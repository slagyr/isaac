---
# isaac-02uu
title: "Cron prompt: :prompt key + markdown companion file support"
status: completed
type: feature
priority: low
created_at: 2026-04-22T17:44:00Z
updated_at: 2026-04-22T18:19:38Z
---

## Description

Two linked changes to cron job config:

1. Rename :input to :prompt in cron job config (isaac-xdlg uses :input; this rename lands post-merge).
2. Allow the prompt to come from a sibling markdown file at ~/.isaac/config/cron/<name>.md (mirrors crew soul pattern). Resolution: if :prompt inline is present, use it; else if the .md file exists, read it; else error at config load. If both present, inline wins and a warn log is emitted. Empty md file errors.

Implementation note: the 'resolve inline field or load from sibling md' pattern is shared with crew soul. Extract to a reusable helper (e.g., isaac.config.companion/resolve-text) rather than duplicating the logic per field.

See features/cron/prompt.feature for the 4 @wip scenarios.

Depends on isaac-xdlg (main cron bead must land first to introduce :input, which this bead renames).

## Acceptance Criteria

1. Rename :input to :prompt throughout cron config code.
2. Extract the md-companion resolution pattern into a reusable helper.
3. Rewire crew soul to use the helper (no regression; existing crew soul scenarios still pass).
4. Add md-companion resolution to cron prompts with validation (missing-both, empty-md).
5. Remove @wip from all 4 scenarios in features/cron/prompt.feature.
6. bb features features/cron/prompt.feature passes.
7. bb features passes overall.
8. bb spec passes.

## Design

Implementation notes:
- Extract a reusable helper: (resolve-text {:inline ... :load-fn (fn [] ...)}) that returns inline if set, else calls load-fn, else nil. Caller decides error vs default.
- Cron prompt: first call resolve-text, then schema-validate that result is non-empty. Missing-everywhere and empty-md both surface as validation errors keyed under cron.<name>.prompt.
- Crew soul refactor: rewire existing soul resolution through the same helper. Regression-tested by existing soul scenarios.
- The helper itself lives in a new file (isaac.config.companion?) so it's findable and reusable.

## Notes

Renamed cron :input to :prompt, added reusable companion text resolution, rewired crew soul loading through the helper, and added cron prompt markdown companion support in commit 52de1fc. Verified with bb features features/cron/prompt.feature features/cron/scheduling.feature, bb features, and bb spec.

