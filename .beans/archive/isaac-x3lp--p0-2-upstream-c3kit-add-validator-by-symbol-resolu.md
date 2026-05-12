---
# isaac-x3lp
title: "P0.2 (upstream c3kit) Add validator-by-symbol resolution to apron.schema"
status: completed
type: task
priority: normal
created_at: 2026-04-30T22:35:41Z
updated_at: 2026-05-01T05:02:22Z
---

## Description

This work happens in c3kit (cleancoders/c3kit-apron), not in Isaac.

## What's needed in c3kit

Today c3kit.apron.schema entries embed :validate / :coerce as
inline functions. For Isaac plugins to ship schema fragments in
plugin.edn (data only, no Clojure code loaded at manifest-read
time), c3kit needs to accept a fully-qualified symbol as the
:validate or :coerce value and resolve it at validate time.

  {:type     :ignore
   :validate cwd-or-string?              ; today: a fn value
   :message  \"...\"}

becomes

  {:type     :ignore
   :validate 'isaac.config.validators/cwd-or-string?  ; data
   :message  \"...\"}

c3kit.apron.schema would (requiring-resolve sym) when invoking the
validator.

## Why it can't live in Isaac

Schema entries are evaluated by c3kit's coerce!/conform! pipeline.
Isaac would have to fork or wrap c3kit to add the resolution
behavior. Cleaner to upstream.

## What unblocks if c3kit ships this

- P2.x: cross-plugin schema validation. Plugin manifests can
  contribute schema slices that reference plugin-owned validators
  by symbol; Isaac's startup composes them; validation paths
  resolve and run them.

## Status

Defer the bead until either:
- c3kit accepts a PR adding this
- Isaac chooses an alternative path (e.g., switching to malli for
  plugin-shareable schemas, while keeping c3kit for core)

## Note

P1.1 does NOT depend on this. Plugin manifests can be DEFINED as
data without validator-by-symbol resolution — the manifest reader
just stores the :schema fragment as-is. The dep moves to Phase 2,
where actual validation across composed schemas would need this.

