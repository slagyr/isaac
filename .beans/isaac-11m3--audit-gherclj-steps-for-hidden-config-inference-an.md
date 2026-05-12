---
# isaac-11m3
title: "Audit gherclj steps for hidden config inference and implicit setup"
status: draft
type: task
priority: low
tags:
    - "deferred"
created_at: 2026-04-25T10:29:10Z
updated_at: 2026-04-25T10:29:11Z
---

## Description

Some step definitions infer crew, model, provider, boot files, time, or channel details that the scenario never states explicitly. Audit those steps and reduce hidden setup so scenarios declare the important setup instead of relying on smart step logic.

## Acceptance Criteria

1. Identify the highest-value hidden-setup steps. 2. Reduce inference where it obscures the product path under test. 3. Preserve helper ergonomics while making scenarios explicit about crew/model/provider/channel when those matter.

## Notes

Deferred cleanup from PLANNING.md step-discipline guidance.

