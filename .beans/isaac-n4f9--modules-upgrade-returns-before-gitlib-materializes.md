---
# isaac-n4f9
title: modules upgrade returns before gitlib materializes — first invocation races
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-08-22T21:02:44Z
updated_at: 2026-08-23T02:21:58Z
---

Observed 2026-08-22 on zanebot after upgrading isaac.agent to b44a660: 'isaac modules upgrade' printed Upgraded and exited, but the immediately following CLI invocation (separate process) failed with 'Error building classpath. Manifest file not found for isaac.agent/isaac.agent in coordinate {...b44a660...}' — the gitlib checkout materializes lazily on first use and is not concurrency-safe against a second process reading it mid-checkout. Self-heals on the next invocation. Fix: (a) modules upgrade fully materializes/warms the new checkout (resolve classpath once) before reporting success — 'Upgraded' must mean installed, not will-install-on-demand; (b) optionally, module loader retries once on this specific manifest-not-found error as belt-and-suspenders. Repro: modules upgrade && isaac <anything> in quick succession on a cold pin.
