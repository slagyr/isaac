---
# isaac-0az
title: "Speed up ACP reconnect specs with configurable poll intervals"
status: completed
type: bug
priority: normal
created_at: 2026-04-16T00:38:46Z
updated_at: 2026-04-16T00:40:09Z
---

## Description

The ACP proxy reconnect specs in spec/isaac/cli/acp_spec.clj are still slower than necessary because production polling intervals are fixed at 10ms and 50ms in src/isaac/cli/acp.clj. Make the proxy poll intervals configurable through opts and use lower values in the reconnect-focused specs so the tests complete faster without sleep-based timing hacks.

## Acceptance Criteria

ACP proxy polling intervals are configurable via opts. Reconnect specs in spec/isaac/cli/acp_spec.clj use lower polling values. bb spec spec/isaac/cli/acp_spec.clj passes.

