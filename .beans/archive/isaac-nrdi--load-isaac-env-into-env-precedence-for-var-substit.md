---
# isaac-nrdi
title: "Load .isaac/.env into env precedence for ${VAR} substitution"
status: completed
type: feature
priority: low
created_at: 2026-04-17T04:22:30Z
updated_at: 2026-04-21T01:12:01Z
---

## Description

c3kit.apron.env loads .env from cwd but not from the Isaac state directory. Operators typically want API keys and other secrets in a dedicated Isaac location, not scattered per-project-cwd.

## Scope
- Load `~/.isaac/.env` (Java Properties format, same as c3kit) as an env source
- Integrate into the env precedence used by config substitution
- Tests use memfs to write the .env file under the virtual state directory

## Precedence (proposed, after integration)
1. c3env overrides (explicit `c3env/override!`)
2. Java system properties
3. OS environment variables
4. cwd `.env` (c3kit default)
5. `~/.isaac/.env` (new)

Order of (4) vs (5) is a minor call — putting cwd first means per-project overrides win, Isaac-level is the baseline. Reasonable.

## Implementation options
1. Wrap `c3env/env` in an isaac function that also checks `~/.isaac/.env`
2. Update c3kit.apron.env upstream to support a configurable .env path or list of paths

(1) is simpler. (2) is nicer for the ecosystem but requires a library change.

## Scenarios (draft)
- Load an API key from ~/.isaac/.env into a ${VAR} reference in config
- .env takes precedence over... what? (define: between OS env and override)
- Missing .env file is fine (no error)

## Depends on
isaac-kh5s (config CLI exists so substitution is exercised end-to-end; isaac-16v already lands substitution at the loader level)

Feature: features/config/env_file.feature (new)

## Acceptance Criteria

1. Implement the isaac .env layer in isaac.config.loader (or a sibling ns), wired into the env lookup chain below cwd .env.
2. Add the 'the isaac .env file contains:' step-def.
3. Remove @wip from all 3 scenarios in features/config/env_file.feature.
4. bb features features/config/env_file.feature passes (3 examples).
5. bb features passes overall — no regression on existing env substitution scenarios in composition.feature.
6. bb spec passes.

## Design

Implementation notes:
- Wrap isaac.config.loader/env to check ~/.isaac/.env after the existing c3env chain. Simpler than pushing a library change into c3kit upstream.
- File path: <isaac-home>/.env where isaac-home defaults to the :home opt passed through load-config-result (which in tests is the in-memory state dir).
- Format: KEY=value per line, Java Properties semantics (delegate to c3env's reader or equivalent).
- Precedence (after integration, highest→lowest):
  1. loader env-overrides* atom (explicit set-env-override!)
  2. c3env/override!
  3. Java system properties
  4. OS environment variables
  5. cwd .env (c3kit default)
  6. ~/.isaac/.env (new)
- Missing file is a no-op (not an error). No debug log either — a user who doesn't use the file shouldn't see noise.

Supporting step-def to add in spec/isaac/features/steps/config.clj:
- 'the isaac .env file contains:' — heredoc body; writes to <state-dir>/.env via the feature-fs.

## Notes

Added Isaac home .env fallback in config loader below c3env, plus config feature step coverage for writing <state-dir>/.env. Verification: bb features features/config/env_file.feature, bb features, bb spec, bb spec spec/isaac/config/loader_spec.clj.

