---
# isaac-vyz5
title: 'config set: consult module manifests for ''unknown key'' warning'
status: todo
type: bug
created_at: 2026-05-18T21:43:31Z
updated_at: 2026-05-18T21:43:31Z
---

## Problem

`isaac config set comms.<slot>.<field> ...` warns "unknown key" for module-provided fields even when the field is valid:

    % isaac config set comms.discord.token "\${DISCORD_TOKEN}"
    warning: :comms.discord.token - unknown key

Root cause: two validation layers in `set-config` (src/isaac/config/mutate.clj:268).

1. **Pre-write** (line 288): `(nil? (schema-for-data-path path))` — consults only the static schema in src/isaac/config/schema.clj. The static `comm-instance` (schema.clj:219) declares only `:type` and `:crew`. Module-provided fields like `:token`, `:loft`, etc. are unknown to this layer.
2. **Post-write** (`validate-plan` → `loader/load-config-result`): full validation. `check-comms` (src/isaac/config/loader.clj:834) consults the module manifest via `find-comm-extension` and accepts/rejects fields against the manifest schema. Already emits its own "unknown key" warnings (loader.clj:830).

The pre-write check is redundant AND wrong: it duplicates the loader's signal but is blind to manifests.

## Fix

Delete the static `unknown-key?` warning from `set-config` (mutate.clj:288–289 and `:warnings` composition on 293–295). Let `validate-plan`'s loader pass be the single source of "unknown key" warnings.

`unset-config` (mutate.clj:302) currently does NOT have the duplicate static check, but verify it still surfaces loader warnings correctly.

## Strict semantics preserved

The loader's manifest pass already warns in these cases (no new code needed):

- Module declared, field unknown to manifest → loader.clj:830 warns.
- `:type` set but module not in `:modules` → `find-comm-extension` returns nil → loader.clj:830 warns.
- `:type` not yet set → `check-comms` skips (slot-cfg has no impl), so set-of `:loft` lands in slot map; loader warns via `:value-spec` mismatch on `comm-instance`. Confirm this path in tests.

## Feature

features/cli/config.feature — four new @wip scenarios after the existing "set on an unknown key warns but still writes" scenario:

- "set on a module-provided comm field does not warn unknown key" (uses telly's `:loft`)
- "set on an unknown comm field still warns via the loader"
- "set warns when the comm's module is not declared"
- "set warns when the comm has no :type yet"

Existing scenario "set on an unknown key warns but still writes" (crew.main.experimental) must continue to pass — loader emits the same warning via entity-level check (loader.clj:131).

## Acceptance

- [ ] Remove the static `unknown-key?` warning composition in `set-config`
- [ ] All four @wip scenarios pass; remove `@wip` tags
- [ ] Existing crew/main/experimental scenario still passes
- [ ] Run: `bb features features/cli/config.feature`

## Related

- B2 (follow-up): `config schema` should also consult module manifests when rendering `comms.value` etc.
- B3 (follow-up): manifest field schemas become first-class c3kit.apron.schema across comm/provider/tool/slash-command surfaces.
