---
# isaac-uwv
title: "Change default server port to 6674 (gravitational constant)"
status: completed
type: task
priority: low
created_at: 2026-04-10T14:19:02Z
updated_at: 2026-04-10T14:31:57Z
---

## Description

The default server port is currently 3000, which is a common default that collides with many other services. Change it to 6674 — the first four digits of Newton's gravitational constant (G = 6.6743 × 10⁻¹¹ N·m²/kg²). Isaac Newton, gravity, Isaac. Fitting.

Sites to update:
- src/isaac/server/app.clj:13 — 3000 fallback
- src/isaac/config/resolution.clj:115 — 3000 fallback
- src/isaac/cli/serve.clj:27 — help text '(default: 3000)'

Feature: features/server/command.feature (@wip) 'Default port is 6674 when no port is configured'

## Acceptance Criteria

1. Remove @wip from 'Default port is 6674 when no port is configured'
2. bb features features/server/command.feature:32 passes
3. No references to 3000 as a default port remain
4. bb features and bb spec pass

