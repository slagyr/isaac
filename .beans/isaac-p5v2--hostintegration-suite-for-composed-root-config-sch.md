---
# isaac-p5v2
title: Host/integration suite for composed-root config-schema + reconciler tests
status: draft
type: task
priority: normal
created_at: 2026-06-15T16:18:04Z
updated_at: 2026-06-15T16:18:04Z
---

When the monolith config/schema_spec was decomposed by fragment owner
(isaac-{agent,server,cron,foundation,acp}/spec/isaac/config/schema_spec.clj — 20
examples, all green), 3 assertions could NOT move to any single module because they
exercise the *composed* root schema, which only exists when every module fragment is
loaded together. They currently live nowhere and will vanish when the monolith drains.

## Homeless tests to re-home

Composed-root config-schema (baseline: isaac/spec/isaac/config/schema_spec.clj @ 09795481):
1. "each entity is a named wrapped map spec" — shape invariant over all fragments incl. sut/root
2. "root conforms a complete config" — full multi-fragment config conforms against composed root
3. "root rejects invalid types with per-field errors" — cross-fragment per-field message-map

Reconciler/berths integration (deliberately relocated out of isaac-agent during extraction;
assert against server.app registries / live composed config):
- isaac.config.berths   reconcile! cases
- isaac.config.configurator component reconciliation + schema ownership

## Excluded — do NOT re-home
- "gateway ignores retired auth keys during conformance" — gateway is legacy trash; let it
  die with the monolith.

## Why these are host-side
Each asserts behavior of the *assembled* schema or the *live reconciler* — no single module
sees all fragments. Needs a host that boots every module and composes the real root
(isaac.config.schema.root/root; source currently in isaac-agent).

## Open design decision (BLOCKS promotion to todo)
Where does the host/integration suite live? Likely the top-level isaac assembly repo (depends
on all modules). Decide repo + harness before a worker picks this up.

## Acceptance (once the host suite exists)
- The 3 composed-root assertions pass against the real composed root with all module fragments registered.
- The berths/configurator reconciler tests pass against the host's live registries.
- gateway test is NOT included.
