---
# isaac-h34o
title: Expose resolved commands over ACP (available-commands advertisement)
status: draft
type: feature
priority: normal
created_at: 2026-05-26T04:21:33Z
updated_at: 2026-05-26T04:21:33Z
parent: isaac-nwj3
blocked_by:
    - isaac-8qd5
---

Deferred from isaac-nwj3. Expose the resolved command set over ACP (available-commands advertisement) so ACP clients (Zed, etc.) can list/invoke commands with CLI parity.

Enumerate resolved commands (global UNION project, precedence) from the registry; advertise name + params/usage (prompt-template commands take args, so convey argument hints). Refresh when the catalog changes.

Parent: isaac-nwj3. Builds on the discovery/registry.
