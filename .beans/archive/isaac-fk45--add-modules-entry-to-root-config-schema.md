---
# isaac-fk45
title: "Add :modules entry to root config schema"
status: scrapped
type: task
priority: normal
created_at: 2026-04-30T22:36:26Z
updated_at: 2026-05-04T23:30:40Z
---

## Description

Why: cccs delivers the discover/index/wire flow end-to-end (it reads :modules from cfg, builds the index, attaches :module-index). The remaining gap is that :modules itself is not declared in the root cfg schema, so a config containing :modules currently triggers an 'unknown key' warning. This bead closes that gap and seeds the default starter isaac.edn with explicit module opt-ins where applicable.

## Scope

- isaac.config.schema gains :modules entry: optional vector of keywords (id == directory name; manifest declares own :id form).
- Default starter isaac.edn lists current built-ins explicitly when they graduate to the modules/ directory (separate beads for each migration; this bead just makes the field first-class).

## Out of scope

- Discovery, index shape, manifest validation, hard-error semantics — all delivered by cccs.
- Schema composition (merging :extends fragments into cfg validation) and cross-cutting ref validators — see P2.x.

## Acceptance

- features/lifecycle/discovery.feature scenarios continue to pass (no regression on cccs's surface)
- A config containing :modules does not produce an 'unknown key' validation warning
- A config without :modules continues to load (treated as empty list)

## Notes

Reshaped 2026-05-04 (option B). Original scope absorbed scenarios that cccs already covers end-to-end (happy path, hard-error on invalid manifest, missing module dir, etc.). Disentangled allocation:

cccs (already shipped): discover, build index, attach :module-index, manifest validation, hard-error semantics
P2.x (separate): :extends schema composition; cross-cutting ref validation via custom validators
fk45 (this bead, narrowed): root cfg schema entry for :modules + starter isaac.edn opt-in language

Single scenario suffices: :modules in cfg does not warn as unknown.

