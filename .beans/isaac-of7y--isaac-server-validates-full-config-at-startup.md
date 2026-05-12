---
# isaac-of7y
title: "isaac server validates full config at startup"
status: todo
type: feature
priority: low
created_at: 2026-05-11T23:21:42Z
updated_at: 2026-05-11T23:21:42Z
---

## Description

Today isaac server boots without validating the merged config.
Configuration errors only surface lazily — when a turn dispatches
to a bad provider, when a tool is requested with bad arguments,
etc. The user's first interaction with a misconfigured Isaac is
\"Hi\" → cryptic error.

This bead wires config validation into the server boot path so the
server refuses to start (or starts in degraded mode with loud
warnings) when config is broken.

## Scope

- Run schema validation against the merged config on boot. Any
  validation error -> structured log + exit non-zero, with the
  offending file path and field name in the message.
- Run :api + :auth semantic compatibility check on every provider:
  oauth-device + openai-completions wire is a known misconfiguration,
  warn or block.
- Run self-consistency: every declared :isaac/factory in the merged
  manifest resolves (catches typos shipped via modules).
- Surface unknown manifest extension kinds (already enforced by
  manifest schema but worth boot-time visibility).

## Depends on

- isaac-yonq (manifest promotion).
- The :api validation bead (registered-set check) — overlaps but
  this bead is broader: catches schema violations beyond just :api.

## Acceptance Criteria

- isaac server fails fast on schema violations with a useful error
  pointing at the offending file path and field.
- :api + :auth combination check fires; flagged combinations include
  oauth-device + openai-completions and oauth-device +
  openai-compatible-style aliases.
- Manifest :isaac/factory self-consistency runs at boot (or in CI
  via the spec from isaac-yonq).
- Scenarios in features/server/ or features/cli/ asserting the
  startup-validation failure paths.
- bb features green.

## Notes

- Originally proposed during the openai-compatible incident as a way
  to surface config errors at the right time. Tracked separately
  from the registered-set :api validation since it covers a broader
  surface (full config tree, semantic checks, manifest consistency).

