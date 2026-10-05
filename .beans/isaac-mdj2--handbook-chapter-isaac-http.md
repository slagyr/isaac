---
# isaac-mdj2
title: 'Handbook chapter: isaac-http'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-10-05T14:38:34Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-http` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

- Cover the concepts THIS module owns (its config tables and keys, tools, comms, commands, behaviors), written for a MODEL operating Isaac, not a developer: what it is, how to change it with `handbook__configure` (config paths), how to verify, then a `### Troubleshooting` subsection under each concept. No source-code walkthroughs.
- Ground every claim in the module's code, manifest and feature scenarios. Mark anything you can't verify with `[verify]` for Micah.
- Refer to other modules' concepts by one line plus their topic id (e.g. crews → `isaac.agent`); don't re-document them. Use foundation's chapter (`isaac-foundation/src/isaac/foundation/handbook.md`) as the pattern and vocabulary.
- Schema `:description`s: any config key this module declares without a `:description` gets one (the config reference is generated from them).

## Acceptance

- Manifest `:handbook` names the chapter; loading config raises no "handbook ... not found" warning.
- A lint spec like foundation's `spec/isaac/foundation/handbook_chapter_spec.clj` (backticked `config:<path>` refs resolve in the composed schema; `isaac <command>` refs exist).
- The repo's full CI green; landed on main with a single squash commit.

## Ungated

Documentation plus a lint spec; no behavior change. Worker hands off `tag=unverified`; Micah reviews the chapter text.

## Landed on main

main-sha: isaac-http 42e302f3586737543fd8eb3393ae2d9bfcdad977

Handbook chapter at `src/isaac/http/handbook.md` (manifest `:handbook` key
added), lint spec at `spec/isaac/http/handbook_chapter_spec.clj`. Bumped
isaac-foundation/isaac-agent pins to origin/main tip (required for
`:handbook` manifest support) and fixed fallout: test-resources fixture's
now-duplicate `:crew`/`:models`/`:providers`/`:comms` fragments dropped,
`:crew-exists?` existence-ref registration added to two specs that walk
`berths/config-paths` directly, `:modules`/`:resource-pools` exempted from
the "every entity collection has an owner" check, and the retired `:server`
migration shim (which foundation's tip dropped) re-declared in isaac-http's
own manifest. `bb spec` (192 examples) and `bb features` (117 examples)
green; GitHub CI green on isaac-http and its isaac-server mirror.

`[verify]` items left in the chapter text for Micah:
- Whether isaac-http's non-loopback-bind startup gate should also accept a
  configured `:http :auth :principals` map, not just the legacy `:token`
  (currently it does not — confirmed by direct invocation, not just tests).
- `http.oidc.jwks-alert-threshold` is read by `isaac.http.audit` but not
  declared in this module's config schema; left undeclared (no schema
  change) and flagged in the chapter rather than silently adding it.
