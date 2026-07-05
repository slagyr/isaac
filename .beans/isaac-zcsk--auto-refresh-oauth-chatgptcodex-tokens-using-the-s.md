---
# isaac-zcsk
title: Auto-refresh OAuth (chatgpt/codex) tokens using the stored refresh token
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-05T16:29:17Z
updated_at: 2026-07-05T17:22:41Z
---

## Problem

isaac never refreshes OAuth (chatgpt/codex) access tokens. The refresh token is stored but never used, so when the access token expires (a few hours) every codex request fails with "Missing OpenAI ChatGPT login" until someone runs `isaac auth login --provider chatgpt` MANUALLY. Codex-based crews silently die on token expiry.

## Evidence (2026-07-05, zanebot)

- `~/.isaac/auth.json` -> `chatgpt.expires` was ~23 min in the past; `chatgpt.refresh` (196 chars) present but unused.
- Code: `llm/auth/store.clj` stores `:refresh` and `:expires` and has `token-expired?`, but a full-tree search for any refresh-grant / token-endpoint call using the refresh token found NONE outside the login flow.
- Result: `isaac prompt` (and server turns) hit the expired token and error with "Missing ChatGPT login." This likely explains recurring "codex flakiness" previously blamed on usage limits.

## Feasibility

The token endpoint already exists and is used for login: `device_code.clj` POSTs to `https://auth.openai.com/oauth/token` via `-post-form!`. Refresh is the same endpoint with `grant_type=refresh_token` + the stored `:refresh` token; the response yields a new access token + expires_in, stored the same way login stores them (`store.clj:27-28`).

## Desired behavior

- Before using a chatgpt/codex token (or on a 401), if `token-expired?` (or within a small pre-expiry window), POST the refresh grant to `/oauth/token`, obtain a new access token, persist it (update `:access`, `:id-token` if returned, `:expires`), and proceed — transparently, no manual re-login.
- On refresh failure (invalid/expired refresh token), fall back to the current clear error instructing `isaac auth login --provider chatgpt`.
- Prefer proactive refresh (before the request) with reactive 401-retry as a safety net.

## Scope

isaac-agent: `llm/auth/store.clj` (add refresh-token! using /oauth/token + `-post-form!`, persist), `llm/auth/device_code.clj` (token endpoint already there), and the chatgpt provider path `llm/api/openai/shared.clj` (trigger refresh when `token-expired?` instead of erroring). Guard against concurrent-refresh races (single-flight).

## Acceptance (gherkin, isaac-agent)

- Given a chatgpt token that is expired but has a valid refresh token, when a request is made, then isaac refreshes via /oauth/token and the request proceeds WITHOUT a manual login, and the persisted token has a future `:expires`.
- Given the refresh token itself is invalid, then isaac errors with the clear "run isaac auth login" message (no silent hang).

Priority: HIGH — recurring silent codex outage; every token expiry kills the codex crews.


## Design (approved 2026-07-05)

Proactive refresh (check token-expired? before the request) + reactive 401-retry backstop; single-flight (one refresh, others wait); refresh updates the LIVE in-memory auth store the provider reads AND persists to auth.json (a disk-only refresh reproduces the stale-in-memory failure that forced a restart on 2026-07-05).

## Acceptance (spec-level, spec/isaac/llm/auth/ — stub /oauth/token via with-redefs; matches existing auth-test pattern)

1. Proactive refresh: expired token + valid refresh -> POST grant_type=refresh_token to /oauth/token; store new :access + FUTURE :expires (+ new :refresh if returned).
2. No-op when valid: non-expired token -> no HTTP, unchanged.
3. In-memory + disk (crux): after refresh, both auth.json AND the live auth store the provider reads return the new token (no stale in-memory copy).
4. Refresh failure clear: rejected refresh token -> clear "run isaac auth login --provider chatgpt" error; no hang/loop.
5. Single-flight: two concurrent refresh-if-needed! calls -> exactly one token POST.
6. Provider path integration: chatgpt/responses provider calls refresh-if-needed! before building the request; expired token refreshes transparently and the request uses the refreshed token.

Optional @slow live feature line alongside the existing codex live scenario. Definition of done: the six specs green; bb spec green.

## Scope

isaac-agent: llm/auth/store.clj (refresh-if-needed! using /oauth/token + -post-form!, single-flight, persist + in-memory), llm/api/openai/shared.clj (call refresh before use + 401 retry).
