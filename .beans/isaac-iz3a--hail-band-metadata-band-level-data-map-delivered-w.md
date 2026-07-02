---
# isaac-iz3a
title: 'Hail band metadata: band-level :data map delivered with every hail, surviving prompt override'
status: todo
type: task
created_at: 2026-07-02T14:48:03Z
updated_at: 2026-07-02T14:48:03Z
---

## Context / Motivation

Hail band files currently conflate two channels: **instructions** (the markdown body, which becomes the delivered prompt) and **data** the recipient needs regardless of instructions (bean-repo, notification-comm, plan-hail/work-hail/verify-hail, human-help-comm, ...). Because the data lives in the body prose, a caller that overrides `:prompt` silently discards all of it. Orchestration crews therefore cannot hail each other through bands with a custom explanatory prompt without losing the coordinates (observed on isaac-3692: verifier had no escalation route).

## Design

- New optional `data:` key in band frontmatter (alongside crew / session-tags / reach): an arbitrary map.
- **Delivery semantics:** prompt = caller `:prompt` override, else rendered band body (existing behavior). Band `data` is carried on the hail record and delivery **always** — independent of which prompt was used.
- **Merge:** effective data = `(merge band-data params)` — per-hail `:params` override/extend band defaults.
- **Interpolation:** `{{param}}` placeholders in data values render from params (covers today's `bean: {{bean-id}}`).
- **Model visibility:** the delivery renders a structured data block alongside the delivered prompt (like `:origin` context today), so the recipient sees the data even with a prompt override. Data is also persisted on the hail record (fetchable later, composes with a future hail_get).

## Acceptance criteria (runnable)

- [ ] A band file with `data:` frontmatter parses; invalid/missing `data:` is tolerated (nil map).
- [ ] `isaac hail send --band X --prompt "override"` → delivered prompt is the override AND the delivery includes the band data block.
- [ ] `isaac hail send --band X` (no prompt) → body renders as prompt (existing) and data block is present.
- [ ] `--params` keys override same-named band data keys; extra params pass through.
- [ ] `{{param}}` interpolation works inside data values.
- [ ] Data appears on the stored hail record (visible via `--edn`/`--json` send output and the persisted file).
- [ ] Specs/features in isaac-hail cover the above (`bb spec` / `bb features` green).

## Follow-up (not this bean)

- Migrate isaac-* / orchistration-* band files: move data from body prose into `data:` frontmatter; slim bodies to pure instructions; update hail-bean-* skills to reference structured band data.
- Possible shared/default data across a project's bands (dedup) — under discussion.
- Hail threading (:thread-id / :reply-to / hail_get) — separate bean.

## Likely repo scope

isaac-hail (band parsing, send, delivery rendering).
