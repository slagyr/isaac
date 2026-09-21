---
# isaac-rxun
title: 'Unresolvable ${VAR} and ${file:…} references: warn, treat as unset, never send the literal'
status: in-progress
type: bug
priority: normal
created_at: 2026-09-21T16:28:51Z
updated_at: 2026-09-21T17:09:53Z
---

Repo: **isaac-foundation** (`src/isaac/config/parse.clj`).

## The footgun

`substitute-env` (`parse.clj:42`) replaces `${VAR}` with the variable's value,
or **leaves the literal text** when the variable is unset:

```clojure
(or (env/env var-name) match)
```

So `:api-key "${OPENAI_ZANE_EMBEDDING_API_KEY}"` with the variable missing from
the server's environment becomes the literal string
`${OPENAI_ZANE_EMBEDDING_API_KEY}`. Validation passes, the literal goes out as
the key, and the provider answers 401. The error blames auth, not the missing
variable. The usual cause: the variable is set in the operator's shell, so
manual tests work, but not in the launchd/systemd environment the service runs
under.

`${file:…}` references (isaac-jl9p) have the same problem with a missing file.

## Decision (Micah, 2026-09-21)

Report it; **don't block writing config** because a variable or file is
missing.

## Design

1. **Never pass the literal through.** A reference that can't be resolved
   resolves to *absent*, as if the field weren't set. The literal looks like
   a real value and gets sent. It's the worst of both outcomes.
2. **Always warn**, naming the field and the reason:
   `OPENAI_ZANE_EMBEDDING_API_KEY is not set (used by :episodes :embedding :api-key)`,
   `prompts/glm-batching.md not found (used by :models :glm-5-3 :extra-system-prompt)`.
3. **Whether it becomes an error is decided by the field, not the
   reference.** An unresolvable reference is exactly an unset field, so:
   - a **required** field gets the existing required-field error, with the
     reason attached ("unset because `${X}` is not set")
   - an **optional** field is simply absent, and the feature that needs it
     reports at the point of use. Providers already do this:
     `missing-auth-error` (`isaac-agent/src/isaac/llm/api/openai/shared.clj:106`)
     would say "no API key" instead of the provider's 401. Make that message
     carry the variable name.

   No new error class; the warning always fires.
4. **Where it's checked:**
   - **Writing config** (`config set`, edits): a warning only. Never refuse. The
     writer's environment isn't the server's, so a missing variable in the
     CLI's shell proves nothing. Say so in the warning ("not set in this
     shell; the server's environment may differ"). A file reference may be
     written before the file is created.
   - **`config validate`**: the same warning, with the same caveat for env
     vars.
   - **Server boot and hot reload**: the server's environment is the one that
     counts. Log `:warn` with field and reason. A missing optional value must
     not fail the reload or the boot. That would take down unrelated config
     over one missing key.
5. **Raw reads are unaffected.** `config set` reads with substitution off
   (`config/cli/common.clj:269`), so references survive rewrites as they do
   today.

## Relation to isaac-jl9p

jl9p's rule 2 ("a missing file is a validation error") is superseded by this
bean: files follow the same warn-and-treat-as-unset rule as env vars.

## Done when

- an unset `${VAR}` resolves to absent, never the literal (spec)
- a missing `${file:…}` resolves to absent (spec, once jl9p lands)
- each case warns with field path and reason, in the CLI and in server logs
  (specs)
- `config set` succeeds with an unresolvable reference and warns (spec)
- a required field whose reference can't be resolved gets the required-field
  error with the reason attached (spec)
- server boot and hot reload succeed with an optional unresolvable reference
  (spec)
- a provider with an unresolvable `:api-key` reports the missing variable by
  name rather than letting the provider return 401 (spec)
- `bb verify` and `bb jvm-spec` are both green

## Implemented, one blocking scenario conflict (2026-09-21, scrapper@isaac-work-1)

Both branches are pushed and green apart from the single scenario in section
**C** below, which contradicts the bean's Design point 1 and which a worker may
not recut.

- `isaac-foundation` `bean/isaac-rxun` @ **`d66310d`** (base `origin/main` `e97c51d`)
- `isaac-agent` `bean/isaac-rxun` @ **`ef55977`** (base `origin/main` `a0a4180`)

### A. What is built (isaac-foundation)

**`isaac.config.parse`** — the substitution primitive now refuses to pass a
literal through. `unresolved-references` names every `${...}` in a string with
no value; `substitute-env` returns **nil** when any of them is unresolvable (a
partly-resolvable string counts — a half-substituted literal still gets sent);
`substitute-env-recursive` **drops** the field, recording `{:path [...] :ref
"VAR"}` in the new dynamic `*unresolved-refs*`. An explicit nil is kept; an
unresolvable reference is the only thing it drops. A dropped seq entry records
its `[idx]`. `*reference-path*` lets an entity file report its full path.

**`isaac.config.entities`** — `read-entity-entry` binds `*reference-path*` to
`[kind id]`, so `crew/main.edn` reports `crew.main.<field>`, not a bare field.

**`isaac.config.warnings`** — `reference-warnings` builds the rows
(`{:key "foundries.helm.api-key" :value "RXUN_MISSING is not set"
:unresolved-ref "RXUN_MISSING"}`), `unresolved-ref-index` reads them back
**after** `berths/normalize-errors` so the paths match the keys validation
errors use, `attach-reference-reasons` appends the reason to any error on such
a field, and `log-unresolved-refs!` warn-logs `:config/unresolved-reference`
with `:path` and `:ref`.

**`isaac.config.loader`** — one collector (`unresolved*`) bound across the whole
load, so every read site is covered by one seam. The result carries
`[:config :unresolved-refs]` (only when non-empty), errors get the reason
attached, and the warnings thread logs both unknown keys (isaac-nq4c) and
unresolved references. New public `loader/unresolved-ref` (+ `config.api`
re-export) is how the point of use names the variable.

**`isaac.config.mutate`** — `set-config` / `unset-config` drop any *new* error
whose key is an unresolved-reference path. **Writing never refuses**: the
writer's shell is not the server's. Coercion errors still block. The warning
still fires. Raw reads are untouched (`config set` reads with substitution off,
so the literal survives the rewrite — spec'd).

**`isaac.config.cli.common/print-warnings!`** — a row carrying
`:unresolved-ref` prints the caveat *(not set in this shell; the server's
environment may differ)*. One site, so `config set`, `config unset` and
`config validate` all get it; server logs are unaffected.

### B. What is built (isaac-agent)

`isaac.llm.api.openai.shared/missing-auth-error` asks
`loader/unresolved-ref "providers.<name>.api-key"`; when the field was emptied
by an unresolvable reference the message is *"No API key for chatgpt. :api-key
references ${OPENAI_ZANE_EMBEDDING_API_KEY}, which is not set in this
environment."* instead of the generic "Set CHATGPT_API_KEY" advice — which is
the wrong variable. Falls back to today's wording otherwise.

While in flight the agent's `bb.edn`/`deps.edn` foundation pins are
`{:local/root "../isaac-foundation-rxun"}`. **At landing they must be rewritten
to isaac-foundation's landed main sha and the agent suite re-run before the
agent squashes** (foundation lands first).

### C. CONFLICT — one baselined scenario asserts the literal passthrough

`isaac-agent/features/config/composition.feature:157`, in *"composes providers
from isaac.edn and providers/*.edn additively"*:

```
    Then the loaded config has:
      | key                        | value                  |
      | providers.ollama.base-url   | http://localhost:11434 |
      | providers.anthropic.api    | anthropic              |
      | providers.anthropic.api-key | ${CONFIG_TEST_ANTHROPIC_API_KEY}   |
```

`CONFIG_TEST_ANTHROPIC_API_KEY` is not set, so the scenario asserts exactly the
behaviour Design point 1 deletes. It now fails with
`Expected: "${CONFIG_TEST_ANTHROPIC_API_KEY}" got: ""`. The scenario's subject
is additive composition, not reference passthrough — the row is incidental — but
it is a feature contract and recutting it is the planner's, not mine.

Proposed minimal recut (planner's call): give the field a literal value in the
`providers/anthropic.edn` block and in the table, exactly as I did for the
analogous *spec* example `load_result_spec` "treats camelCase config keys as
unknown after the hard cutover".

**Nothing else in the tree conflicts.** I grepped every sibling checkout's
`features/` for `${...}`:
- `isaac-agent` `llm/api/messages/anthropic_auth_api_key.feature:41`,
  `chat_completions/openai_auth.feature:20`, `chat_completions/grok_auth.feature:40`
  — `@slow` live-API **inputs**, not assertions; the variable is set in a live run.
- `isaac-server/features/server/auth.feature:76` — sets `ISAAC_AUTH_TOKEN`
  first, so it resolves and still passes.
- `isaac-foundation` `cli/config_resolution.feature`, `cli/edn_pretty.feature`,
  `cli/remote_routing.feature` — raw-read / redaction paths; all green.

### D. Suites

isaac-foundation `bean/isaac-rxun` @ `d66310d`:
- `bb spec` **1130 / 0 failures / 2044 assertions** (1098 -> 1130: +32)
- `bb features` **198 / 0 failures / 524 assertions / 2 pending** (the two
  pre-existing berth-registration pendings). The `cli/modules_pins.feature`
  failures were the recurring stale gitlibs mirror
  (`~/.gitlibs/_repos/file/REL/fixture-agent`); `rm -rf` it and they go green.
- `bb lint` exit 0, no new warnings in the touched files.
- `bb jvm-spec` **1129 / 8 failures** — all 8 **pre-existing**: reproduced
  identically on a detached `origin/main` worktree (**1098 / 8**). They are the
  JVM-only module-lifecycle / defrecord-protocol failures, untouched by this bean.
- **There is no `bb verify` task in isaac-foundation** (`bb tasks` lists
  `spec ci lint jvm-spec jvm-features features mutate scrap dry ...`). `bb ci`
  is the equivalent gate; the bean's "Done when" names a task this repo does
  not have.

isaac-agent `bean/isaac-rxun` @ `ef55977`:
- `bb spec` **1686 / 0 / 3486** (one run showed the known flake "session feature
  steps parks a slow tool-loop send so a later cancel can still fire"; green on
  re-run).
- `bb features` **843 / 1 failure** — the failure is section C and nothing else.
- `bb lint` 510 errors vs **507 on the same tree with main's version of the one
  spec file I touched**: the repo's lint config does not know speclj, so every
  `it`/`should-*` is an "Unresolved symbol" — 507 of them pre-existing. My 3 are
  the same class, from 3 added assertions.

### E. Not done, deliberately

`${file:…}` references: **isaac-jl9p is still `todo`**, so the syntax does not
exist yet. The bean's own "Done when" scopes this as *"(spec, once jl9p lands)"*.
`substitute-env-recursive` drops whatever `substitute-env` cannot resolve, so
`${file:…}` inherits the rule for free once jl9p adds it.

Bean Gate: `bb bean-gate verify isaac-rxun` -> no `feature-baseline`, exit 2.



## Exceptions

### composition.feature additive providers (authorized, 2026-09-21, prowl@isaac-plan)

On isaac-agent `features/config/composition.feature` scenario "composes providers from isaac.edn and providers/*.edn additively": replace the unset `${CONFIG_TEST_ANTHROPIC_API_KEY}` with a literal `sk-ant-test` in both `providers/anthropic.edn` and the Then table `providers.anthropic.api-key` row. The scenario's subject is additive composition, not reference passthrough. Design point 1 forbids asserting the unresolved literal.

Landed on isaac-agent main `753b131`.

## Planner adjustment (2026-09-21, prowl@isaac-plan) — recut composition row; bb ci not bb verify; file refs stay jl9p

Conflict: one incidental table row asserted the passthrough this bean deletes. Recut on module main as above.

**Done-when tasks:** isaac-foundation has no `bb verify`. Controlling gate is `bb ci` (and `bb spec` / `bb features`). Do **not** require `bb jvm-spec` 0 — the 8 JVM module-lifecycle/defrecord failures are pre-existing on origin/main.

**file refs:** still isaac-jl9p (todo). No further work here; substitute-env-recursive will drop unresolved file refs when that syntax exists.

### Controlling acceptance

**isaac-foundation** `bean/isaac-rxun` @ `d66310d`:

    bb spec
    bb features
    bb ci

**isaac-agent** `bean/isaac-rxun` rebased onto `753b131`:

    bb spec
    bb features features/config/composition.feature

0 failures on composition. Full `bb features` 0 after the recut (the prior 1 was this row).

Landing: foundation squash first; rewrite agent `bb.edn`/`deps.edn` off `{:local/root "../isaac-foundation-rxun"}` to the landed sha; re-run agent gates; then squash agent. No feature-baseline (gate exit 2) — unverified/verify handoff.

### Worker now

1. Rebase `bean/isaac-rxun` (agent) onto origin/main `753b131`. Keep implementation.
2. Confirm composition.feature green. Do not recut live-API env-ref inputs. Do not implement file refs.
3. Hand to verifier. Do not land until foundation then agent pin rewrite.
