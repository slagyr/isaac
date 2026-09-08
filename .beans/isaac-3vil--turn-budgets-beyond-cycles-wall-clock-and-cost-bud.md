---
# isaac-3vil
title: 'Turn budgets beyond cycles: wall-clock and cost budgets for unattended turns'
status: draft
type: task
priority: normal
created_at: 2026-09-08T15:28:25Z
updated_at: 2026-09-08T15:28:25Z
parent: isaac-ntt6
---

Deferred from isaac-ntt6 decision 6 (2026-09-08). A cycle is a poor budget unit for workers (a two-second read and a four-minute test run count the same). Industry: Codex cloud and Devin bound by wall-clock; SWE-agent by dollars. Proposal: per-crew / per-band `:turn-budget {:cycles N :wall-clock-ms M :cost-usd C}`, any exhausted → the same on-exhausted policy path. Draft until the cycle-limit work (agent + hail children) lands.
