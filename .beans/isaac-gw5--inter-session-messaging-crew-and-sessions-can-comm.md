---
# isaac-gw5
title: "Inter-session messaging: crew and sessions can communicate"
status: draft
type: feature
priority: low
tags:
    - "deferred"
created_at: 2026-04-14T19:15:39Z
updated_at: 2026-04-20T22:44:00Z
---

## Description

Sessions and crew members need a way to exchange messages and read each other's state — a planner asking a worker for status, a reviewer reading a worker's transcript, crew members coordinating on shared work.

Use cases:
- Planner reads a worker's session transcript to check progress
- Reviewer reads a worker's session to verify acceptance criteria
- One crew member sends a message to another (async, through session)
- A session references another session's context

Scope includes the user-facing surface: a `message` crew tool (formerly isaac-dfk7) modeled on OpenClaw's delivery-queue pattern. See /Volumes/zane/openclaw for reference. The tool is one of several APIs on top of the underlying messaging infra.

This is the foundation for multi-agent coordination within Isaac itself, not just between Isaac and external clients.

