---
# isaac-ane7
title: 'Suite health (isaac-server): reconciler ''Two comms run independently when both slots are present at boot'' fails under dev-local against agent 0.1.61+'
status: scrapped
type: bug
priority: normal
created_at: 2026-09-11T01:17:55Z
updated_at: 2026-09-18T05:13:43Z
---

Repo: isaac-server. `clojure -M:dev-local:test:features` against local isaac-agent at 0.1.61 and 0.1.62: 72 examples, 1 failure — Lifecycle reconciler 'Two comms run independently when both slots are present at boot': comm "bert" exists with state expected true, got nil. Green under `bb features` (pinned agent). Determine whether the agent's boot/session changes moved the contract or the server regressed; pin the current agent and make it green. Acceptance: server features 0 failures under both the pinned and dev-local aliases; CI green.


## Scrapped (planner, 2026-09-18) — folded into isaac-lsz2
The isaac-server repin to agent main is isaac-lsz2's server leg, and that repin is what makes this scenario (and three siblings the planner observed today: Comm extension "Multiple comm instances of the same :type coexist", Module activation ×2) red. They are fixed together there; the pin cannot move without fixing them and they cannot be fixed without moving the pin.
