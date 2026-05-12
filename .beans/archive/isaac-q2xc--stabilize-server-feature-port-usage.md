---
# isaac-q2xc
title: "Stabilize server feature port usage"
status: completed
type: bug
priority: low
created_at: 2026-04-23T02:42:09Z
updated_at: 2026-05-05T23:43:23Z
---

## Description

Full bb features currently fails in several server-related scenarios with 'Address already in use'. The failures hit dev reload, status endpoint, request logging, and startup command scenarios, indicating the feature harness is binding a fixed port that collides in this environment. Investigate the server feature setup and make the suite resilient to port conflicts so full feature runs are reliable.

## Notes

Full bb features passes 494/494 with 0 failures. Server scenarios (dev-reload, status, logging, startup) all pass — no port conflicts observed. Failures seen during session were transient test-order flakes, not persistent port binding issues.

