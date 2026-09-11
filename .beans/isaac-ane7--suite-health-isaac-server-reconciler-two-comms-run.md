---
# isaac-ane7
title: 'Suite health (isaac-server): reconciler ''Two comms run independently when both slots are present at boot'' fails under dev-local against agent 0.1.61+'
status: todo
type: bug
created_at: 2026-09-11T01:17:55Z
updated_at: 2026-09-11T01:17:55Z
---

Repo: isaac-server. `clojure -M:dev-local:test:features` against local isaac-agent at 0.1.61 and 0.1.62: 72 examples, 1 failure — Lifecycle reconciler 'Two comms run independently when both slots are present at boot': comm "bert" exists with state expected true, got nil. Green under `bb features` (pinned agent). Determine whether the agent's boot/session changes moved the contract or the server regressed; pin the current agent and make it green. Acceptance: server features 0 failures under both the pinned and dev-local aliases; CI green.
