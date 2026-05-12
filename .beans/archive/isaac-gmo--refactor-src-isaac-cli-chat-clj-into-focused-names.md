---
# isaac-gmo
title: "Refactor src/isaac/cli/chat.clj into focused namespaces"
status: completed
type: task
priority: low
created_at: 2026-04-12T00:06:13Z
updated_at: 2026-04-12T01:44:45Z
---

## Description

chat.clj is a ~600-line file that mixes many concerns: the interactive REPL loop, provider dispatch routing, streaming rendering, compaction trigger logic, tool execution wiring, Toad subprocess launching, session resolution, and the command registry entry. It's the primary coupling point in Isaac and a recurring source of risk — most non-trivial beads need to touch it.

## Goal
Break chat.clj into focused namespaces, each with a single responsibility. The public entry point for the chat command stays in chat.clj (or wherever the registry/register! lives), but the body calls into smaller pieces.

## Proposed split
- isaac.cli.chat — command registration, entry point, dispatches to the right flow
- isaac.cli.chat.loop — interactive REPL loop, session resolution, prompt reading
- isaac.cli.chat.dispatch — provider routing, tool dispatch decision (when to go through chat-with-tools vs chat-stream)
- isaac.cli.chat.toad — Toad subprocess launcher (already its own ns, may need refinement)
- isaac.cli.chat.single-turn — shared single-turn execution used by isaac agent
- isaac.cli.chat.logging — log-compaction-check!, log-message-stored!, log-stream-completed!, etc.

## Constraints
- All existing scenarios must keep passing — this is a structural refactor with no behavior change
- The Channel protocol already gives us the seam for separating orchestration from UI (isaac-qpb landed). This refactor continues that decoupling
- process-user-input! is the current coupling point for many tests and features; it should move to isaac.cli.chat.single-turn or similar

## Dependency
Ideally comes after isaac-hoe (tools.cli migration) so commands are already self-contained when we break chat.clj apart. Not strictly required though.

## Acceptance
- bb features and bb spec pass
- chat.clj is under 150 lines
- Each extracted namespace is single-purpose and testable on its own
- Public API (the chat command behavior) is unchanged

