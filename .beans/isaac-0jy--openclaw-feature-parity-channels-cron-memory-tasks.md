---
# isaac-0jy
title: "OpenClaw feature parity: channels, cron, memory, tasks, delivery"
status: draft
type: feature
priority: low
tags:
    - "deferred"
created_at: 2026-04-15T21:08:35Z
updated_at: 2026-04-17T04:29:19Z
---

## Description

Epic: features needed to replace OpenClaw based on actual usage analysis of the zanebot installation.

## Actively Used (must have)

1. **Channels** — Discord, iMessage, Google Chat integrations. Route inbound messages to crew members, deliver responses back. This is the biggest gap. OpenClaw has credentials/pairing per channel with allowFrom filters.

2. **Cron/Scheduled Tasks** — Recurring agent turns on a schedule. 3 active jobs on zanebot: git-backup, health-checkin, tempest-vault-sync. Jobs define agent, schedule, session target, wake mode, payload message, and delivery channel.

3. **Memory** — Persistent per-crew knowledge store (sqlite). 12MB for main agent. Separate from session transcripts — survives compaction and session boundaries.

4. **Tasks** — Background task runner (sqlite, 4MB, actively used). Executes agent turns asynchronously.

5. **Delivery Queue** — Reliable message delivery to channels with retry on failure. 3 failed deliveries sitting in queue on zanebot.

## Lightly Used (defer)

6. Flows — workflow orchestration (sqlite, last used Apr 7)
7. Subagents — spawn sub-agents for parallel work (0 runs)
8. Canvas — visual workspace (3.8KB static HTML)
9. Browser — chrome extension integration
10. Devices — remote device pairing (0 paired)

## Not Used

11. Voice calls — empty
12. Braids — plugin system (config exists, unclear if active)

## Already Beaded

- Guardrails/exec approvals (isaac-mrn)
- MCP client (isaac-dcl)
- Inter-session messaging (isaac-gw5)

## Acceptance Criteria

Isaac can replace OpenClaw for the zanebot use case: channels, cron, memory, tasks, delivery all functional.

