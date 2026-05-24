---
# isaac-un18
title: Unified collision policy for module extension kinds
status: draft
type: feature
priority: normal
created_at: 2026-05-20T20:44:42Z
updated_at: 2026-05-24T05:18:47Z
---

## Motivation

The "what happens when two contributors register the same name" question
keeps coming up per extension kind. Today the answers diverge:

- **Slash commands** — last-wins with `:slash/override` warning
  (`features/module/slash_extension.feature`).
- **CLI commands** — undefined (the proposed `isaac-vorl` bean surfaces
  this).
- **Tools, providers, comms** — also undefined / per-extension-kind.

Re-litigating per extension means inconsistent behavior, scattered
warnings, and surprise debug sessions. A single policy lets each new
extension kind (`:cli`, future `:transport`, etc.) consume the same
rule.

## Proposed policy

**Collision = error by default. Explicit user opt-in to override.**

- A module's manifest declares the *registered* name (e.g.
  `:cli {:greet {:factory ...}}` registers `greet`).
- Effective name = user config override if present, else registered name.
- If the effective name collides with a built-in or another module's
  effective name AND no explicit user config remap targets it: **reject
  the second registration with `:<kind>/conflict` error and refuse to
  start** (or fail the activation, depending on extension kind's
  lifecycle).
- If a user config explicitly remaps a module's name onto another
  (built-in or module) name: **proceed**, log `:<kind>/override` at
  `:warn`. The override is audible, deliberate, and traceable to the
  user's own config.

Rationale: silent last-wins is friendly until it isn't — replacing
`isaac config` with a module command, or shadowing a tool the LLM has
been trained on, fails closed instead of open. Explicit opt-in keeps
the escape hatch.

## Migration concern

Slash extension currently implements last-wins-with-warn. Switching
to error-by-default is a behavior change for anyone who today has
two modules quietly colliding. Two paths:

1. Migrate slash atomically as part of this bean (one consistent policy,
   one breaking change).
2. Define the policy now for new extension kinds; file a follow-up
   bean to migrate slash.

Lean toward (1) — divergence is precisely the smell this bean is
trying to fix — but flag for explicit Micah call.

## TODOs

- [ ] Pick a single naming convention for the policy events
      (`:<kind>/conflict`, `:<kind>/override`) and document.
- [ ] Decide migration approach for slash (atomic vs follow-up bean).
- [ ] Implement the policy as a shared helper in
      `isaac.module.<something>` so each extension kind calls one
      function rather than reinventing.
- [ ] Update `features/module/slash_extension.feature` if (1) is chosen
      — current "override" scenario stays valid; add a "collision
      rejected" scenario.
- [ ] Document the policy in ISAAC.md so future extension-kind beans
      inherit it.

## Acceptance criteria

- One shared collision-handling helper used by every extension kind
  that supports module contribution.
- Manifest validation / module activation rejects unmitigated name
  collisions with a structured error event.
- User config remap path produces `:<kind>/override` warning and
  proceeds.
- `bb spec` and `bb features` pass; if (1) is chosen, slash override
  feature is updated to match the new policy.

## Notes

Captured from a 2026-05-20 design conversation about CLI command
extension (`isaac-vorl`). That bean depends on this policy so it
doesn't have to invent collision semantics standalone.
