---
# isaac-wv8z
title: 'Release hygiene: sync a module''s inter-module deps.edn pins to the registry on release'
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-20T15:26:29Z
updated_at: 2026-06-20T19:48:26Z
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


## RE-SCOPED 2026-06-20 — the BOM-override premise was WRONG

Worked through with Micah. Conclusions:
• Transitive module versions correctly come from the PARENT's deps.edn — NOT the
  registry. `upgrade` already moves them: bumping an explicit parent to the
  registry's latest pulls whatever that parent release pins; cross-parent
  conflicts are already resolved (newest-wins + the conflict report). The
  registry is IRRELEVANT for transitive modules.
• Forcing transitive X to the registry version (the original BOM-override) would
  risk shoving a parent onto an X it wasn't built against. So DON'T do that.
• The observed "agent stuck at 0.1.0 after upgrade" is therefore NOT a
  consumer-side resolution bug — it's RELEASE HYGIENE: acp@0.1.5 / imessage@0.1.4
  were released still pinning agent@0.1.0, so the agent fix can't reach users
  until the parents re-release with bumped agent pins. The recurring lockstep
  theme.

## Real fix

Make transitive fixes propagate by syncing pins UPSTREAM, at release time:
• A release step / tool (`sync-module-pins`) that, when a module is released /
  registry-published, rewrites its inter-isaac-module deps.edn pins to the
  registry's CURRENT coherent set. Then the next consumer `upgrade` pulls the
  fixed transitive automatically.
• Add to the release checklist (isaac-mdtu): "sync inter-module deps.edn pins to
  the registry before tagging/registering."

## Precedence (agreed, documents intent)
explicit user :modules pin > parent deps.edn (transitive). Registry does NOT
override transitive. Foundation stays seed-authoritative (92p3). Registry
offline -> fall back to parent pins.

## Relationships
• isaac-mdtu (release process). Supersedes the "extend BOM to all modules" idea —
  keep foundation seed-authoritative, do NOT BOM-override transitive modules.



## RESOLVED (work-1, 2026-06-20) — tag: unverified

Worked through the design with Micah; landed Model 1 + doc-only (his call).

**Conclusion:** the registry is NOT authoritative for transitive modules — their
versions correctly come from the depending module deps.edn pins (forcing
registry versions would shove a parent onto an X it was not built against). So
the observed "agent stuck at 0.1.0 after upgrade" is working-as-intended, not a
resolution bug. The fix is release hygiene: a parent must re-release with bumped
pins for a transitive fix to reach users via `modules upgrade`.

**Delivered (doc-only, fast, no resolution):** isaac-foundation `RELEASE.md` ae45f03
— new "Releasing a module" section: step 2 = sync inter-module deps.edn pins to
the registry current versions before releasing; plus the documented
`isaac modules install <name>` workaround for a consumer stuck on an old
transitive module.

**Deliberately NOT done (decided against, in order):**
• `sync-module-pins` tool — Micah chose doc-over-tool (option 2). Tool remains a
  future option if the manual step proves error-prone.
• registry-as-BOM / override-deps (the original bean premise) — rejected:
  forcing transitive versions is unsafe + bets everything on registry coherence
  (the session-long pain). Keep foundation seed-authoritative (92p3).
• manifest semver `:version` validation — explored for a registry-version-compare
  model, then reverted when we settled on Model 1 (it broke discovery.feature
  message expectations and was orphaned).
• conflict-highlighting on install/upgrade — implemented then reverted: it needs
  a full dependency-resolution pass (slow), and those commands must stay fast.
  Version conflicts already surface in `modules list` (where resolution is
  expected). Could be added as an opt-in flag later if wanted.

**Verification:** doc-only change to RELEASE.md; no code/test impact. foundation
working tree otherwise clean.
