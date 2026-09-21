---
# isaac-htix
title: sessions list does not show that a session is blocked
status: todo
type: bug
priority: normal
created_at: 2026-09-21T22:17:56Z
updated_at: 2026-09-21T22:17:56Z
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
