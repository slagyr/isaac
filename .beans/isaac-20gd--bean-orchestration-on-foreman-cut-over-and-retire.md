---
# isaac-20gd
title: 'Bean orchestration on Foreman: cut over and retire band choreography'
status: draft
type: task
created_at: 2026-09-27T23:09:08Z
updated_at: 2026-09-27T23:09:08Z
parent: isaac-q3u3
blocked_by:
    - isaac-1rtr
    - isaac-ex4q
---

Likely repos: **orchestration** (bands, prompts, crew config) and **isaac** (AGENTS.md bean workflow, dispatch docs, hail-bean-work skills). Last of three migration beans under milestone isaac-q3u3.

## Contract to plan

- All new bean work starts on the Foreman machine; dispatch docs and planner mechanics point at `isaac foreman` instead of hailing `isaac-work` / `isaac-verify`.
- Remove worker-to-worker hail handoff prompts and the bands that exist only for that choreography. Clean cutover, no compatibility aliases.
- Keep Hail for independent messages and explicit human attention where its messaging semantics are useful.
- Hail and Worksite repository retirement are separate decisions based on their remaining uses.

## Acceptance (one-time checks, not permanent scenarios)

- A fresh bean goes todo → completed on zanebot through Foreman with no hail sent by any worker.
- The removed bands are absent from the deployed config and no doc or skill still instructs a worker-to-worker hail.

Draft until the preceding migration bean is complete.
