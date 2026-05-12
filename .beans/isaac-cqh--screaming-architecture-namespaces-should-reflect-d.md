---
# isaac-cqh
title: "Screaming architecture: namespaces should reflect domain, not framework"
status: draft
type: task
priority: deferred
tags:
    - "deferred"
created_at: 2026-04-13T02:59:23Z
updated_at: 2026-04-17T04:29:23Z
---

## Description

## Summary

Uncle Bob's screaming architecture principle: the top-level structure of the codebase should scream what the system does, not what framework it uses. Isaac's current namespaces are organized around technical layers (`cli`, `server`, `acp`, `llm`, `config`) rather than domain concepts.

With the spaceship metaphor and domain concepts solidifying (bridge, drive, session, channel, agent/crew), revisit the namespace structure to reflect what Isaac IS rather than how it's built.

## Examples of potential changes
- `isaac.cli.chat.single-turn` → `isaac.drive`
- `isaac.cli.chat.dispatch` → `isaac.drive.dispatch` or `isaac.engine`
- `isaac.session.bridge` → `isaac.bridge`
- `isaac.acp.server` → could stay (ACP is both domain and protocol)

## Defer until
The metaphor and domain language need to settle first. Premature restructuring would just create churn.

## Acceptance Criteria

Namespace structure reflects Isaac's domain concepts. Top-level dirs tell you what the system does.

