---
# isaac-g7g5
title: 'foundation test-support: fixed 60s with-timeout! budget makes green module suites exit 124'
status: draft
type: bug
tags:
    - foundation
    - gate
created_at: 2026-09-04T14:06:43Z
updated_at: 2026-09-04T14:06:43Z
---

`spec-support/src/bb/test_timeout.clj:8` — `(def ^:const test-timeout-ms 60000)`, no env/config override. isaac-discord's default `bb features` finished in 42.8s (67/0) and still exited 124 ("jvm-features timed out after 60s") because JVM boot + run exceed the budget; isaac-agent's full suite needs ~60-230s and the verifier saw exit 124 on jarr too. A gate whose exit code lies on a green run gets waived (jarr, vuto) and then trusted less. Fix: budget from env (`ISAAC_TEST_TIMEOUT_MS`) or per-task arg, default 300s; print the actual wall time; exit 0 when the suite finished green before the deadline. Related: isaac-jndk.
