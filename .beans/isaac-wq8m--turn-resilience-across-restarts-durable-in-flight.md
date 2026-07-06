---
# isaac-wq8m
title: 'Turn resilience across restarts: durable in-flight turns, drain, resume'
status: draft
type: epic
created_at: 2026-07-06T15:43:46Z
updated_at: 2026-07-06T15:43:46Z
---

Any server restart (deploys included) kills active turns dead — they are never resumed. Hail-driven turns partially recover via inflight re-delivery (isaac-0tf3), but interactive/cron turns are lost, and even hail recovery re-drives against a possibly-dirty transcript tail. This epic makes turns first-class recoverable units: (1) a durable per-turn marker that generalizes hail/inflight to all turn sources and persists the initiating charge routing, (2) a graceful shutdown that drains in-flight turns to a clean step boundary, (3) a unified startup resume that repairs the transcript tail and re-drives interrupted turns.
