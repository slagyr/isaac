---
# isaac-524u
title: Custom human-readable EDN pretty-printer (replace clojure.pprint)
status: draft
type: feature
priority: normal
created_at: 2026-07-13T16:03:13Z
updated_at: 2026-07-13T16:03:13Z
---

## Goal

A custom human-readable EDN pretty-printer for Isaac's CLI output, replacing clojure.pprint (Micah dislikes its formatting). Used wherever Isaac prints EDN to a human (config get default view, sources, bean/hail records, etc.).

## Motivation

clojure.pprint/pprint wraps and aligns in ways Micah finds hard to read. Isaac already has opinions about human output (tables in grof, color). EDN output deserves the same care.

## Design questions to settle at spec time (with Micah)

1. What specifically is wrong with pprint output — line width, map-key alignment, vector wrapping, namespace-map (#:git{}) rendering, sorting? Micah to show a before/after example of the desired shape.
2. Sort keys? (deterministic output aids diffing and agent parsing.)
3. One-value-per-line for maps over N keys, inline for small maps?
4. Where it applies: a shared `isaac.util.edn/pretty` used by all EDN-printing CLI paths; --edn flag output stays machine-canonical or also pretty? (Likely: --edn = pretty-human, since --json exists for machines.)
5. Home: isaac-foundation (CLI rendering lives there — brew train).

## Non-goals

Changing --json output; changing on-disk EDN file format (only display).

## Status

Draft per Micah 2026-07-13; spec after he provides the desired-shape example. Candidate as the grok-4.5 shakedown bean for scrapper once promoted.
