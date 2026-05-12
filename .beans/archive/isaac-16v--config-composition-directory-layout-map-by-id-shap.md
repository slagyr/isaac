---
# isaac-16v
title: "Config composition: directory layout, map-by-id shape, schemas, env substitution"
status: completed
type: feature
priority: high
created_at: 2026-04-17T01:13:50Z
updated_at: 2026-04-17T01:47:40Z
---

## Description

Refactor configuration loading to support composable files under ~/.isaac/config/ and migrate internal shape to map-by-id throughout.

## Layout
- ~/.isaac/config/isaac.edn — root
- ~/.isaac/config/crew/<id>.edn — one crew member per file; optional <id>.md companion for soul prose
- ~/.isaac/config/models/<id>.edn — one model per file
- ~/.isaac/config/providers/<id>.edn — one provider per file

## Shape change (breaking)
Everything keyed by id:
{:defaults  {:crew :main :model :llama}
 :crew      {:main {...} :marvin {...}}
 :models    {:llama {...} :grover {...}}
 :providers {:ollama {...} :anthropic {...}}}

The :crew :list vector, :crew :models map, and :models :providers vector all disappear. Top-level :defaults {:crew :id :model :id} replaces :crew :defaults.

## Rules
- Filename = id. Explicit :id in file must match filename (error on mismatch).
- Strict additive merge across isaac.edn + per-entity files. Duplicate id across sources = hard error (no silent override).
- Soul in both :soul (edn) and companion .md = error. One or the other.
- Unknown keys = warning (not failure).
- Semantic validation: :defaults :crew/:model references must exist; crew.model references must exist in :models; model.provider references must exist in :providers.
- Model refs are keywords pointing into :models. No provider/model string shorthand.
- Built-in default config bundled (llama3.3:1b via ollama) — returned when no files exist.
-  substitution uses c3kit.apron.env precedence (overrides, sysprops, OS env, .env).

## Namespace
Reorganize under isaac.config:
- isaac.config.schema — c3kit.apron.schema definitions per entity
- isaac.config.loader — composition, merge, validation
- Replaces src/isaac/config/resolution.clj

## Callers to update
- isaac.cli.crew (crew-list iteration)
- isaac.cli.chat, isaac.cli.prompt (model/soul resolution)
- isaac.context.manager (model context windows)
- All feature steps that build config (session.clj, providers.clj, etc.)

## Step definitions (new spec/isaac/features/steps/config.clj)
- 'an empty Isaac state directory {home:string}' — activates memfs
- 'config file {path:string} containing:' — writes to <home>/.isaac/config/<path>
- 'environment variable {name:string} is {value:string}' — c3env/override! with cleanup
- 'the loaded config has:' — implicit load, asserts dotted paths match values
- 'the config has validation errors matching:' — implicit load, asserts errors by (key, value-regex)
- 'the config has validation warnings matching:' — implicit load (success), asserts warnings by (key, value-regex)

Feature: features/config/composition.feature

## Acceptance Criteria

1. Remove @wip from all scenarios in features/config/composition.feature
2. bb features features/config/composition.feature passes (20 examples, 0 failures)
3. bb features passes (no regression in other features — existing feature files that reference old config shape must be migrated)
4. bb spec passes (no regression in unit specs — existing specs using old shape must be migrated)
5. Default config (no files) produces a working Isaac that can chat via ollama/llama3.3:1b

