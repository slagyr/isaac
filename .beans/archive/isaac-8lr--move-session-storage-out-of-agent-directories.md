---
# isaac-8lr
title: "Move session storage out of agent directories"
status: scrapped
type: task
priority: normal
created_at: 2026-04-13T22:48:31Z
updated_at: 2026-04-13T22:50:57Z
---

## Description

## Problem

Sessions are stored under agent directories: `~/.isaac/agents/main/sessions/`. This couples sessions to agents, but sessions are becoming agent-independent (see isaac-bvl). The crew rename (isaac-3d4) would also need to move sessions if they stay under agent dirs.

## Fix

Move sessions to `~/.isaac/sessions/`. Flat storage, no agent scoping.

- `~/.isaac/sessions/sessions.json` — unified index (or one index, TBD)
- `~/.isaac/sessions/*.jsonl` — transcripts

## Migration

Existing sessions under `~/.isaac/agents/*/sessions/` need to be migrated. A one-time migration on startup or via `isaac migrate` command.

## Impact

- `src/isaac/session/storage.clj` — all path construction changes
- Session listing no longer needs agent-id to find the directory
- `create-session!`, `list-sessions`, `get-transcript` signatures may simplify
- Feature step definitions that set up sessions
- Existing user data needs migration

## Blocks

- isaac-3d4 (crew rename) — do this first so the rename doesn't touch session paths

## Acceptance Criteria

Sessions stored under ~/.isaac/sessions/. Existing sessions migrated. All features and specs pass.

