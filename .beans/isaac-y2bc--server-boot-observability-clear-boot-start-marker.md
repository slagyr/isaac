---
# isaac-y2bc
title: 'Server boot observability: clear boot-start marker + per-module load/activate (incl agent/server) in order'
status: todo
type: feature
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-19T22:22:26Z
---

Micah, reviewing zanebot's boot log: the boot is hard to read. Issues:
• No clear boot-start marker; :server/started is logged LAST (after all
  activation), so there's no demarcation of "boot begins".
• isaac.agent and isaac.server LOAD but emit NO :module/activated — they're
  invisible (agent inferred from its API registrations; server from
  :server/started + its berths). Can't confirm load order or that they loaded.
• Only the comms emit :module/activated; cron/hooks use :lifecycle/started — no
  uniform per-module signal.

Add:
• a :server/boot-starting marker at the top of boot.
• a uniform per-module load+activate event for EVERY module (incl isaac.agent,
  isaac.server, isaac.hail) in dependency order — so the load order is legible.
• phase boundaries (discover -> activate -> start) and a boot summary (N modules
  loaded, M activated, failures).
Goal: the questions "did agent/server load? in what order? what failed?" are
answerable directly from the log.
