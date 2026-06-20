---
# isaac-wv8z
title: modules upgrade ignores transitive modules — apply registry versions as a BOM (92p3)
status: todo
type: bug
created_at: 2026-06-20T15:26:29Z
updated_at: 2026-06-20T15:26:29Z
---

`isaac modules upgrade` only refreshes EXPLICIT :modules entries. Transitive
modules (e.g. isaac.agent, pulled by the comms) keep whatever their parents'
deps.edn pin — even when the registry has a newer release. So a fix released in a
TRANSITIVE module NEVER reaches users via `upgrade`.

## Observed (zanebot, 2026-06-20)

After `modules upgrade`: acp 0.1.5, imessage 0.1.4 (explicit, upgraded) but
isaac.agent stayed 0.1.0 (4abb96b, transitive) though the registry has agent
v0.1.5 (the x2r1 grover-gate fix). The fix didn't deploy; grover still registers.
Workaround: `isaac modules install isaac.agent` (pins it explicitly at registry
version).

## Fix

Treat the registry as the authoritative version set (BOM): `upgrade` (and the
launcher's resolve) should apply the registry version to EVERY registry-known
module — transitive or not — overriding the parents' deps.edn pins via
:override-deps, WITHOUT necessarily adding them to :modules. This is the
registry-BOM-override lever flagged-but-deferred in isaac-92p3.
Minimum bar: `upgrade` REPORTS "isaac.agent: registry v0.1.5 available but
transitive — `modules install isaac.agent` to apply" so the gap is visible.

## Relationships
• isaac-92p3 (registry-as-BOM). The recurring transitive-version theme.
