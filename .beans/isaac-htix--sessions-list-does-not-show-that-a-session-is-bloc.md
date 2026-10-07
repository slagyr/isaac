---
# isaac-htix
title: sessions list does not show that a session is blocked
status: todo
type: bug
priority: normal
created_at: 2026-09-21T22:17:56Z
updated_at: 2026-09-30T14:05:33Z
---

Repo: **isaac-foundation** (the `sessions` CLI).
Split out of isaac-sqno.

## Problem

A blocked session is invisible. `isaac sessions list` shows nothing to
distinguish it, so the only way to find one is:

    grep -l ":block {" ~/.isaac/sessions/*/*/session.edn

On 2026-09-21 three sessions sat blocked and the pipeline simply looked quiet —
the operator-visible symptom was a hail retrying forever, not a blocked session.
Finding them required knowing the internal field name.

## Change

Mark blocked sessions in `isaac sessions list` — a status column or flag showing
the block and its reason. `isaac sessions show <id>` should surface `:block`
prominently too.

## Acceptance

- `isaac sessions list` distinguishes a blocked session from a healthy one,
  naming the reason.
- `isaac sessions show <id>` displays the block and its `:at` timestamp.
- A run with no blocked sessions looks exactly as it does today.

## Triage update (2026-09-30, planner, approved by Micah)

Repo correction: the sessions CLI lives in **isaac-agent** (src/isaac/session/cli.clj), not isaac-foundation. Still unfixed: print-session-table, session->row and run-show ignore :block.

## Design (planner, 2026-10-07 — Foreman pilot 2)

- `sessions list`: a BLOCKED column appears when any listed session is blocked (same
  pattern as the TAGS columns), showing `(:reason block)`; unblocked rows show `-`.
  With no blocked session the table is exactly today's — no BLOCKED column (one-time
  check at landing, not a permanent scenario).
- `sessions show <id>`: a `Blocked` line with the reason and `:at`.
- Feature step: `the following sessions exist:` gains `block.reason` / `block.at`
  columns (blank = no block).

## Acceptance

- isaac-agent `features/session/cli.feature:414`.
- One-time at landing: `sessions list` with no blocked session prints no BLOCKED
  column.
- The rest of isaac-agent features stay green.

feature-baseline: isaac-agent a80984537588c7ef6c5d7cfa7d4d04ccdb6a608a
feature-blob: isaac-agent features/session/cli.feature 17aeaf3b6ed9bfc2d10389738e8ee49424882422 414
