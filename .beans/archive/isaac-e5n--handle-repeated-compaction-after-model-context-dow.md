---
# isaac-e5n
title: "Handle repeated compaction after model context downgrade"
status: completed
type: bug
priority: high
created_at: 2026-04-09T05:05:47Z
updated_at: 2026-04-09T05:24:01Z
---

## Description

When a session accumulated under a large context window is resumed with a much smaller model (e.g. 1M -> 32k), a single compaction may not reduce token usage enough. Implement iterative compaction in chat flow so compaction repeats until prompt fits or a safe stop condition is reached, then continue normal response generation.\n\nFeature: features/context/compaction.feature\nScenario: @wip Switching to a smaller-context model runs compaction repeatedly until chat can continue\n\nDefinition of Done:\n- Remove @wip from the scenario in features/context/compaction.feature\n- Compaction loop runs multiple passes as needed for reduced context windows\n- Loop has a safe termination strategy (max attempts and/or no-progress guard)\n- Chat produces assistant response after successful compaction\n- bb features and bb spec pass

