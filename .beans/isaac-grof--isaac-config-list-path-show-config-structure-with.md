---
# isaac-grof
title: 'isaac config list <path>: show config structure with secret values masked'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-07-12T18:10:28Z
updated_at: 2026-07-12T19:22:28Z
---

## Goal

A read-side companion to isaac config set: 'isaac config list providers' (or any dot-path) prints the keys and shape of a config map WITHOUT dumping secret values. Requested by Micah 2026-07-12 after a week of hand-rolled sed-masking to inspect providers safely.

## Motivating examples

- isaac config list providers -> the provider ids, and per provider its keys (:type, :api, :base-url, :auth, :api-key) with sensitive values masked
- isaac config list providers.xai -> that one map
- isaac config list attention -> notify/break-glass structure

## Settled design (specced with Micah, 2026-07-12)

Two subcommands, clean division:
- **`isaac config keys <path>`** — bare key names at the path, one level. Minimal, pipeable.
- **`isaac config list <path>`** — keys + config source file per entry, table by default.
- Both support `--edn` / `--json` (`keys` -> `["xai","grok"]`; `list` -> `[{"key":"xai","source":"config/providers/xai.edn"}]`).
- **Leaf paths print nothing**, exit 0. Values are NEVER printed by either command (no masking policy needed — nothing to mask).
- **Family retrofit beachhead**: `config validate --json` emits warnings/errors as structured data; the pattern extends to the rest of the config family in follow-ups.
- Home: isaac-foundation (config CLI lives there — ships via the brew train, not a module upgrade).

## Approved scenarios

```gherkin
Scenario: config keys prints bare key names at a path
  Given config file "providers/xai.edn" containing:
    """
    {:api "responses" :base-url "https://api.x.ai/v1" :auth "api-key" :api-key "sk-real-secret-value"}
    """
  And config file "providers/grok.edn" containing:
    """
    {:type :grok}
    """
  When isaac is run with "config keys providers"
  Then the stdout contains "xai"
  And the stdout contains "grok"
  And the stdout does not contain "config/providers"
  And the stdout does not contain "https://api.x.ai/v1"
  And the exit code is 0

Scenario: config list prints keys with their config source
  (same fixture)
  When isaac is run with "config list providers"
  Then the stdout contains "xai"
  And the stdout contains "config/providers/xai.edn"
  And the stdout does not contain "sk-real-secret-value"
  And the exit code is 0

Scenario: a leaf path prints nothing
  (same fixture)
  When isaac is run with "config keys providers.xai.base-url"
  Then the stdout is empty
  And the exit code is 0

Scenario: keys and list emit structured output under --json
  (same fixture)
  When isaac is run with "config keys providers --json"
  Then the stdout contains "[\"grok\", \"xai\"]" (order: sorted; worker may adjust exact whitespace assertion)
  When isaac is run with "config list providers --json"
  Then the stdout contains "\"source\""
  And the stdout does not contain "sk-real-secret-value"

Scenario: config validate --json emits structured warnings
  Given a config with one known-warning entry (e.g. an unknown key)
  When isaac is run with "config validate --json"
  Then the stdout parses as JSON with a warnings array naming the offending path
  And the exit code is 0
```

New steps: `the stdout does not contain "…"` (negative assertion), `the stdout is empty` (worker confirms whether it already exists). Values never printed is load-bearing: both benign and secret values asserted absent.

## Non-goals

Editing (config set exists); printing secrets under any flag.

## Status

Draft per Micah: spec after the claude cli provider issue settles.
