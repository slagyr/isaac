---
# isaac-grof
title: 'isaac config list <path>: show config structure with secret values masked'
status: draft
type: feature
priority: normal
created_at: 2026-07-12T18:10:28Z
updated_at: 2026-07-12T18:10:28Z
---

## Goal

A read-side companion to isaac config set: 'isaac config list providers' (or any dot-path) prints the keys and shape of a config map WITHOUT dumping secret values. Requested by Micah 2026-07-12 after a week of hand-rolled sed-masking to inspect providers safely.

## Motivating examples

- isaac config list providers -> the provider ids, and per provider its keys (:type, :api, :base-url, :auth, :api-key) with sensitive values masked
- isaac config list providers.xai -> that one map
- isaac config list attention -> notify/break-glass structure

## Design questions to settle at spec time (with Micah, after the claude cli provider work settles)

1. Value display policy: mask only sensitive keys (api-key/token/secret/password patterns) and show benign scalars? Or keys-only / type-only for everything? Note: ${ENV_VAR} interpolation refs are NOT secrets and should print verbatim (they document where the value comes from).
2. Raw file vs effective view: {:type :grok} is nearly empty on disk but resolves against the manifest template — show raw, resolved, or both (flag)?
3. Entity files: providers/models/crew live in per-entity files — should output name the source file per entry?
4. Depth control / recursion for nested maps.

## Non-goals

Editing (config set exists); printing secrets under any flag.

## Status

Draft per Micah: spec after the claude cli provider issue settles.
