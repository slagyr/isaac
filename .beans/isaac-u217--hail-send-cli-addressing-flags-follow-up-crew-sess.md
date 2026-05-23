---
# isaac-u217
title: 'Hail send CLI: addressing flags follow-up (--crew, --session, --crew-tag, --session-tag, --from-json)'
status: draft
type: feature
priority: normal
created_at: 2026-05-23T21:51:36Z
updated_at: 2026-05-23T22:05:40Z
parent: isaac-ugx7
blocked_by:
    - isaac-vduq
---

## Motivation

vduq's v1 substrate CLI ships `--band` addressing only. The locked
Hail design treats all addressing forms equally; the other flags
(`--crew`, `--session`, `--crew-tag`, `--session-tag`) need to be
wired in. Same CLI structure — no re-architecture; the addressing
flags just become more options the existing parser handles.

Also adds `--from-json` for whole-hail stdin input in JSON format
(EDN remains the default for `-`).

## Scope

### New addressing flags on `isaac hail send`

```
--crew <id>           repeatable (OR-list)
--session <id>        repeatable (OR-list)
--crew-tag <tag>      repeatable (AND-set)
--session-tag <tag>   repeatable (AND-set)
--from-json           interpret stdin (`-`) as JSON instead of EDN
```

Examples:

```
isaac hail send --crew marvin --prompt "..." --payload '{:n 1}'
isaac hail send --crew marvin --session-tag project/chess --prompt "..."
isaac hail send --crew-tag role/worker --crew-tag wip --reach one --prompt "..."
echo '{"frequency":{"band":"bean-pickup"}}' | isaac hail send - --from-json
```

### Persisted address map

Each flag contributes its key to the hail's `:frequency` map:

```clojure
;; --crew marvin --session-tag project/chess
{:frequency {:crew         [:marvin]
             :session-tags [:project/chess]}
 :prompt    "..."
 :payload   {...}}
```

Multiple values on the same flag become a list (or set, for tag
flags). Multiple distinct addressing flags coexist (intersection
semantics at fan-out time).

### Validation

- At least one addressing flag (or whole-hail stdin) required.
- `--prompt` required for non-band addressing (band's `.md`
  provides the prompt; direct addressing has no `.md`).
- `--reach` accepted on hail only for direct/tag addressing (band's
  `:reach` wins for band sends).

## Out of scope (deferred)

- **YAML-stdin or other format inputs** — JSON is the most
  universal non-EDN format; others wait for demand.
- **`isaac hail send -c <path>` from a config file** — defer.
- **Per-hail `:reach` overrides for band sends** — band's
  declaration wins.

## Feature scenarios

Extend `features/hail/send.feature` (or split into a new file like
`features/hail/send-addressing.feature` if the original file grows
too long). Each new flag gets ~1–2 scenarios:

- `--crew` writes `:crew [:marvin]` into `:frequency`.
- `--session` writes `:session [:tidy-cavern]`.
- `--crew-tag` repeated gathers tags into a set.
- `--session-tag` similarly.
- `--crew` + `--session-tag` combine (intersection).
- `--from-json -` parses JSON stdin into a hail record.
- Missing `--prompt` for direct addressing errors clearly.

## Acceptance

- All five new flags accepted by `isaac hail send`.
- Persisted records correctly populate the address-map keys.
- Validation rejects under-specified hails (no addressing) and
  missing-prompt for non-band addressing.
- `--from-json -` reads JSON stdin and constructs the address map.
- Existing scenarios continue to pass.

## Verification

@wip scenarios under the existing or split feature file. Speclj
specs for the CLI argv parser additions (each flag's binding,
validation rules, JSON-vs-EDN stdin dispatch).

## Relationship to other beans

- **Parent: isaac-ugx7 (Hail epic).**
- **Blocked by isaac-vduq (substrate + base CLI).** The CLI must
  exist with `--band` working before adding more flags.
