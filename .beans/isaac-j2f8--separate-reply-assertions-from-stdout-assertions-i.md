---
# isaac-j2f8
title: "Separate reply assertions from stdout assertions in gherclj steps"
status: draft
type: task
priority: low
tags:
    - "deferred"
created_at: 2026-04-25T10:29:08Z
updated_at: 2026-04-25T10:29:09Z
---

## Description

The feature step named `the reply contains` currently rides the same captured output source used by stdout assertions. Make reply assertions read a seam-appropriate channel-visible reply so bridge/memory-channel scenarios stop depending on CLI-ish output capture.

## Acceptance Criteria

1. `reply` assertions read reply state, not stdout state. 2. CLI scenarios continue using stdout-specific assertions. 3. Bridge or comm scenarios can assert replies without depending on stdout buffering.

## Notes

Deferred cleanup from PLANNING.md step-discipline guidance.

