---
# isaac-cahv
title: Merge bean/isaac-wpny into isaac-agent main (integration only)
status: todo
type: task
priority: high
created_at: 2026-07-10T11:54:14Z
updated_at: 2026-07-10T11:54:14Z
---

## Goal

Land isaac-wpny's VERIFIED work (grok provider template + OAuth descriptors) on isaac-agent main. Integration only — do NOT re-verify.

## Situation

- origin/bean/isaac-wpny (8514042) carries 2 commits: OAuth provider descriptors + refresh acceptance coverage.
- origin/main has advanced 9 commits since the branch point, including OVERLAPPING oauth work: isaac-zcsk (chatgpt token auto-refresh in the store/device-code path) plus isaac-auws (claude-code provider), la8h, k1po, 08r9, dark.
- Reconcile wpny's per-provider oauth descriptors with zcsk's auto-refresh so both behaviors survive: descriptors parameterize client-id/endpoints per provider; zcsk's refresh flows through them (single-use refresh-token persistence rules from the wpny bean apply).
- Run isaac-agent bb spec + bb features under pinned siblings; fix only integration fallout. Push main.
- Do NOT bump the manifest version (release train pending on this merge).
