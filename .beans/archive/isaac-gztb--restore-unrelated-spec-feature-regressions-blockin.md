---
# isaac-gztb
title: "Restore unrelated spec/feature regressions blocking full green"
status: completed
type: bug
priority: normal
created_at: 2026-04-29T17:26:11Z
updated_at: 2026-04-30T01:13:15Z
---

## Description

After unrelated work in the repository, full bb spec/bb features are currently failing outside isaac-4q2p scope. Observed failures include: built-in grep files_with_matches output expectation, turn-cancellation/tool-path scenarios, LLM tool-call exec scenarios, and boot-files AGENTS.md prompt inclusion. This bead tracks restoring full-suite green for those unrelated regressions so isaac-4q2p can move forward independently.

