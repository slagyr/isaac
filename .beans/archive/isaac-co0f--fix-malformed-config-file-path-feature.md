---
# isaac-co0f
title: "Fix malformed config file path feature"
status: completed
type: bug
priority: normal
created_at: 2026-04-23T01:53:13Z
updated_at: 2026-04-23T02:24:17Z
---

## Description

bb features currently fails in features/config/composition.feature on the scenario "malformed EDN in a config file is reported with the file path". Investigate why config validation no longer surfaces the file path and EDN syntax error entry expected by the approved feature, and restore the expected behavior.

## Notes

Fixed config loader handling for malformed crew entity EDN so the original parse error is preserved as the relative file path instead of degrading into "must contain a map". Verified with bb spec spec/isaac/config/loader_spec.clj, bb features features/config/composition.feature, and bb spec. Full bb features still has the unrelated failure tracked by isaac-m2vc.

