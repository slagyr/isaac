---
# isaac-9jet
title: "Silence timbre output during feature test runs"
status: completed
type: task
priority: low
created_at: 2026-04-20T16:59:30Z
updated_at: 2026-04-20T21:44:19Z
---

## Description

c3kit.apron.app emits timbre INFO logs (>>>>> Stopping App >>>>>) that leak into bb features output. Isaac uses isaac.logger not timbre, so timbre output is pure noise. Silence timbre's println appender at feature test boot so c3kit's internal logs don't pollute test output.

