---
# isaac-mfdt
title: :prefer-entity-files missing from config schema — validator warns "unknown key"
status: completed
type: bug
priority: normal
created_at: 2026-06-22T19:17:48Z
updated_at: 2026-06-22T19:27:51Z
---

`:prefer-entity-files` is a real, honored top-level config key (it makes `config set` write to entity files like config/crew/*.edn instead of inlining), but it is NOT declared in any module's `isaac.config/schema`. So config validation flags it as `unknown key` on every load / `config set`.

## Evidence
- Consumed by foundation: `isaac.config.mutate` reads `[:prefer-entity-files]` (~line 162); `isaac.cli.registry` init scaffolds new configs with `:prefer-entity-files true` (~line 117); `isaac.config.cli.set` references it.
- Yet the foundation manifest `isaac.config/schema` only declares `:tz` — `:prefer-entity-files` is absent, so the validator doesn't know it.
- Observed on zanebot (foundation 0.1.7): `isaac config set …` prints `warning: :prefer-entity-files - unknown key`. Non-fatal, but noisy and misleading (the key works fine).

## Fix
Register `:prefer-entity-files` in foundation's top-level `:isaac.config/schema` (in `isaac-foundation/src/isaac-manifest.edn`, alongside `:tz`), e.g.:
```clojure
:prefer-entity-files {:schema {:type :boolean
                               :description "When true, config set writes/updates per-entity files (config/<dir>/<id>.edn) instead of inlining into isaac.edn."}}
```

## Acceptance
- A config containing `:prefer-entity-files true` loads and `config set` runs with NO `unknown key` warning for it.
- The key is documented in the schema description.
- Add/adjust a config-validation spec covering the key.

## Notes
Surfaced 2026-06-22 while diagnosing zanebot config warnings. Foundation-level (brew formula) fix — ships in the next foundation release.
Implemented in isaac-foundation @ f64238d.
