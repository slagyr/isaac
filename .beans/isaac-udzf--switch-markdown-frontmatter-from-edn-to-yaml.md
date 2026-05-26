---
# isaac-udzf
title: Switch markdown frontmatter from EDN to YAML
status: in-progress
type: task
priority: normal
created_at: 2026-05-26T05:06:59Z
updated_at: 2026-05-26T05:22:03Z
---

Isaac's markdown frontmatter (the cron single-md job-config variant) uses **EDN**; almost every other agent (Claude, agent-lib, toolbox) uses **YAML**. Switch all md frontmatter to YAML for interoperability — so Isaac reads foreign command/skill files natively and the `user-invocable` signal is just a YAML field.

## Scope
- Switch the shared md-frontmatter parsing helper to **YAML** (bb ships `clj-yaml` — no new dependency).
- Migrate the existing **cron single-md-with-frontmatter** loader and update `features/cron/prompt.feature` (the EDN-frontmatter scenario -> YAML).
- Audit any other md-frontmatter usages and migrate.

## Coercion nuance (main risk)
YAML yields strings/plain values where EDN gave keywords (e.g. cron `:crew :main` -> YAML `crew: main` parses as the string `"main"`). The loader must coerce known fields (crew name, type, etc.) so behavior is preserved.

## Why now
Unblocks the prepared-prompts work (isaac-8qd5) to parse YAML, and dissolves most foreign-format-ingestion concerns (frontmatter becomes the common YAML format, so the 3-signal disambiguation `type` > `user-invocable` > path is all MVP-viable).

## Relationship
Standalone refactor (touches cron, not only prepared-prompts). **Blocks isaac-8qd5.**


## Feature spec

`features/cron/prompt.feature` — the "single markdown file" scenario converted EDN -> **YAML** frontmatter and tagged `@wip` (the other cron/prompt scenarios use inline/body-only prompts, unaffected). Run:

```
bb features features/cron/prompt.feature
```

**Definition of done:** remove `@wip` and that scenario is green.

## Implementation scope

- `src/isaac/config/loader.clj`: `read-frontmatter-file` (~line 390) — swap `read-edn-string` for a **YAML** parse (bb `clj-yaml`); update the "missing EDN frontmatter" error; **coerce** known fields (YAML gives strings where EDN gave keywords — e.g. `crew: main` -> the main crew). `split-frontmatter` (the `---` fences) is unchanged.
- Update CLJ specs that assume EDN frontmatter (`loader_spec`, `config_steps`).
- Audit for any other frontmatter-md usages.
