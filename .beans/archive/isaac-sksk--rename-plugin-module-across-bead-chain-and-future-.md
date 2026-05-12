---
# isaac-sksk
title: "Rename: plugin -> module across bead chain and future implementation"
status: completed
type: task
priority: low
created_at: 2026-05-03T16:32:51Z
updated_at: 2026-05-04T18:17:09Z
---

## Description

Rename "plugin" to "module" across the bead chain and future
implementation. Aligns with the project's spaceship metaphor — ISS
modules are the actual aerospace term for pluggable spacecraft
sections (Unity, Destiny, Harmony, Columbus, Kibo, etc.).

## Why

- "Plugin" is generic software jargon. "Module" is the real-world
  vocabulary for a swappable spacecraft component.
- Reads naturally everywhere: "the discord module", "module
  manifest", "module discovery", "module activation".
- No collision with existing project terms (comm, crew, soul, host).

## Scope

This bead is rename-only. No code changes — implementation hasn't
started. The rename applies as each downstream bead is implemented.

### Bead title/description updates

Update these beads' titles and descriptions to use "module" in
place of "plugin":

- isaac-1861  P1.1  plugin.edn manifest         -> module.edn manifest
- isaac-iw5q  P1.2  Plugin coordinate            -> Module coordinate
- isaac-cccs  P1.3  plugin loader: discovery     -> module loader: discovery
- isaac-fk45  P1.4  :plugins into root config    -> :modules into root config
- isaac-v0uu  P2    Lazy plugin activation       -> Lazy module activation
- isaac-8sih  P3    providers to plugins/        -> providers to modules/
- isaac-qzn0  P4    Comms to plugin subprojects  -> Comms to module subprojects
- isaac-mx1d  P5    Third-party plugin support   -> Third-party module support

### Naming for future implementation

- Namespace: isaac.module.* (manifest reader, loader, discovery,
  activation). NOT isaac.plugin.*.
- Manifest file: module.edn (not plugin.edn).
- Root config key: :modules (not :plugins) — see isaac-fk45.
- Directory layout for third-party modules: modules/<name>/module.edn
  + src/.

### What does NOT rename

- isaac.lifecycle — already the right home (per hx5t).
- isaac.plugin — current namespace; hx5t deletes it. The "plugin"
  word survives only inside hx5t's migration text describing what
  it removes; can stay as historical reference.
- "comm" as the container name — unchanged.

## Acceptance

- All eight beads listed above have updated titles and descriptions.
- This bead serves as the canonical record of the decision so
  future contributors don't re-litigate the name.
- bd search plugin returns only this bead and any beads whose
  descriptions mention "plugin" in a historical context (e.g.
  hx5t describing what it deletes).

## Out of scope

- No code changes. Implementation uses "module" from the start as
  each P-bead lands.

