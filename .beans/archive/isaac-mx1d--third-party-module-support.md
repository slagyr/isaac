---
# isaac-mx1d
title: "Third-party module support"
status: scrapped
type: epic
priority: deferred
created_at: 2026-04-30T22:37:14Z
updated_at: 2026-05-11T18:41:06Z
---

## Description

Once built-ins are pluginized and the manifest/discovery/activation
machinery is solid, support plugins that live OUTSIDE the Isaac repo.

## Two pieces

- P5.1: classpath augmentation
  - At startup (after P1 discovery), compute each third-party
    plugin's src/ directory path.
  - Under bb: babashka.deps/add-deps with :local/root
  - Under JVM clj: pomegranate or equivalent runtime classpath add
  - Built-ins continue to live on bb.edn :paths directly.
- P5.2: 'isaac plugin install <id> <source>' CLI
  - Source can be a local path or a git URL
  - Fetches into <state-dir>/plugins/<id>/
  - Validates the manifest before declaring success
  - Writes a lockfile entry so reproducible installs are possible
    (similar to deps.edn lock)

## Trust model

Third-party plugins get full SCI/JVM access. This is opt-in by the
user; document it explicitly. No sandboxing in the initial
implementation.

## Depends on

- P3.7 (the layout has to be stable before third parties build on it)

