---
# isaac-rxun
title: 'Unresolvable ${VAR} and ${file:…} references: warn, treat as unset, never send the literal'
status: in-progress
type: bug
priority: normal
created_at: 2026-09-21T16:28:51Z
updated_at: 2026-09-21T18:17:32Z
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

## Planner return resolved — handed to verify (2026-09-21, scrapper@isaac-work-1)

The `composition.feature` conflict is gone. Planner recut it on isaac-agent main
(`753b131`, beans `6eb7242f`): the scenario now gives `:api-key` a literal
`sk-ant-test` in both `providers/anthropic.edn` and the `Then` table, because it
tests additive composition, not reference passthrough — design point 1 forbids
asserting an unresolved `${CONFIG_TEST_ANTHROPIC_API_KEY}`.

**Branches** (both pushed, no implementation change since the conflict report —
the agent branch was rebased only):

- isaac-foundation `bean/isaac-rxun` @ `d66310d`, base `origin/main` `e97c51d`
  (still tip of main; no rebase needed).
- isaac-agent `bean/isaac-rxun` @ `5d4a655`, rebased onto `origin/main`
  `cabfdf2` (which is `753b131` + the isaac-xpkf/f3hq/6doh `@wip` scenarios).
  Clean rebase, no conflicts.

**Gates** (planner ruling 1: isaac-foundation has no `bb verify`; `bb ci` is the
controlling gate, and `bb jvm-spec`'s 8 failures are pre-existing on
`origin/main`, so they are not a bar here):

| Repo | Command | Result |
|---|---|---|
| isaac-foundation | `bb ci` | **exit 0** — spec 1130/0/2044, features 198/0/524, 2 pre-existing pending |
| isaac-agent | `bb features features/config/composition.feature` | **21/0/35** |
| isaac-agent | `bb features` | **838/0/1994**, 1 pre-existing pending |
| isaac-agent | `bb spec` | **1686/0/3486** |

The agent example counts moved 843 → 838 because the recut folded the two
reference rows out of the composition table; the new `@wip` scenarios on
`cabfdf2` are skipped, as intended.

One flake seen and cleared: `spec/isaac/session/session_steps_spec.clj:230`
("parks a slow tool-loop send so a later cancel can still fire") failed once on
`(should-not (realized? (g/get :turn-future)))` and passed on re-run. It is a
timing assertion on a `sleep 0.05` tool call, unrelated to this bean.

**Scope held as ruled:** `${file:…}` stays with isaac-jl9p (still `todo`); no
live-API `${VAR}` input was recut.

**Gate:** `bb bean-gate verify isaac-rxun` → exit **2**, `no feature-baseline:
use the verify path`. Tagged `unverified` and handed to the verify band.

**Landing order for whoever lands this** (unchanged): squash isaac-foundation
first, then rewrite the agent's `bb.edn` **and** `deps.edn` pins off
`{:local/root "../isaac-foundation-rxun"}` to the landed foundation main sha (5
entries in `bb.edn`, 13 in `deps.edn`: `isaac-foundation`, `-spec`,
`-test-support`, `marigold.bridge`, `marigold.longwave`), re-run the agent
gates, then squash isaac-agent.

## Verify fail (attempt 1, 2026-09-21): the agent's `unresolved-ref` lookup pulls the ambient config snapshot in-flight and reds isaac-agent's full feature suite

Verified by **perceptor**@isaac-verify.

**The foundation half is good and is now on main.** The agent half is red and was reverted off main. Read the landing state below before doing anything.

### Landing state — read this first

| repo | state |
|---|---|
| **isaac-foundation** | **LANDED.** `bean/isaac-rxun` @ `d66310d` squash-merged to main as **`22694fc711a2d399606e2d78d26b843fdb9c5363`**. The branch is deleted. Do not re-land it; branch fresh off main for any foundation follow-up. |
| **isaac-agent** | **NOT landed.** I squashed it, discovered the red on the squash commit, and reverted it off main (`00503cd`). `bean/isaac-rxun` @ `cc44840` is left in place for you — it is your `5d4a655` plus my repin of the 18 foundation coordinates to `22694fc`. Keep the repin. |

### What is green

isaac-foundation `bean/isaac-rxun` @ `d66310d`, `bb ci` exit 0: config-bypass-lint ok, lint-cli-host ok, **1130 spec / 0 failures / 2044 assertions**, **198 features / 0 failures / 524 assertions / 2 pending** (the two berth-registration pendings pre-date this bean). Squash tree byte-identical to the gated tree.

I did not take the specs at face value. Two mutations, each restored afterwards:

- `attach-reference-reasons` → `vec` in the loader pipeline: `load_result_spec` reds **45 / 1 failure** (line 338, the required-field reason). Load-bearing.
- `unresolved-reference-paths` → `#{}`: `mutate_spec` reds **32 / 1 failure** ("writes a required field and warns instead of refusing"). Load-bearing.

Every added `it` executes — no swallowed examples: `parse_spec` 16 forms/16 examples, `mutate_spec` 32/32, `cli/common_spec` 12/12, `load_result_spec` 45/45, all confirmed with `bb spec -f documentation`.

And end to end through the real CLI, on a throwaway root with `:api-key "${RXUN_TOTALLY_MISSING}"`:

    {:level :warn, :event :config/unresolved-reference,
     :path "providers.zane.api-key", :ref "RXUN_TOTALLY_MISSING"}
    warning: :providers.zane.api-key - RXUN_TOTALLY_MISSING is not set (not set in this shell; the server's environment may differ)
    OK - config is valid

Design points 1, 2, 4 and 5 hold, and `config validate` never refuses.

### BLOCKING — `missing-auth-error` reads ambient config from in-flight code

`isaac-agent/src/isaac/llm/api/openai/shared.clj` calls the **one-arity** `loader/unresolved-ref`, which calls `loader/snapshot`, which calls `loader/config-atom`:

    (defn- config-atom []
      (or (nexus/get :config)
          (let [cfg* (atom nil)]
            (nexus/register! [:config] cfg*)     ;; side effect
            cfg*)))

So a read from deep inside provider auth **registers a nil-valued `:config` atom into the ambient nexus** when none is registered yet. A later entry point that expects to own that slot finds it already taken and reads nil config — the module index comes back empty, and a manifest-supplied comm kind disappears from `config schema` output.

That is exactly what happens. `features/config/schema_cli_options.feature` fails on the scenarios that read comm kinds and fields contributed by the `modules/isaac.comm.telly` `:local/root` module:

    1) comm slot :type lists user-configurable comm kinds from manifests   (:33)
       Expected truthy: (re-find (re-pattern "options:.*telly") output) got: nil
    2) config schema comms.value renders every manifest-supplied field inline  (:66)

It is order-dependent, so the failing set moves between runs — which is why it never showed up in a targeted run. Isolated, the file passes (7/0, twice). Only the full suite exposes it.

**Bisect — four full `bb features` runs, isaac-agent:**

| tree | result |
|---|---|
| `origin/main` 984f59a (old foundation pin) | **839 / 0 / 1997** green |
| `origin/main` 984f59a + foundation repinned to the landed `22694fc` | **839 / 0 / 1997** green |
| squash commit `ff0b594` (main + your agent change + repin) | **RED** — 1 failure, then 2 on re-run |
| squash commit `ff0b594` with *only* the `unresolved-ref` lookup disabled (`reference (when false …)`) | **839 / 0 / 1997** green |

So it is neither the foundation change nor a pre-existing red: it is this one lookup. The foundation library is innocent — pinning main to `22694fc` with no agent change is green.

It also breaks foundation's own documented contract, which the worker can read directly above the function being called:

> Reads ambient config; call **ONLY at entry points and wake boundaries** (process start, request/turn entry, a worker waking from sleep) — **in-flight code must receive config as a value**, not pull a fresh snapshot.

`missing-auth-error` is in-flight code. It already receives a `config` argument — but that is the provider's slice, which has no `:unresolved-refs`, which is presumably why the ambient read was reached for. **That is the real design question to solve**, not something to paper over:

- Thread the root config (or just the unresolved-ref index) down to the point of use, so the two-arity `(loader/unresolved-ref config path)` can be used and nothing ambient is touched; or
- have config stamp the reason onto the provider slice itself when it drops the field, so the slice carries its own explanation; or
- if an ambient read really is the only option, make it non-mutating — `config-atom`'s `nexus/register!` is the actual hazard, and a read-only accessor that returns nil instead of registering would be a foundation-side fix with its own scenario.

Whichever you pick, the bar is: **isaac-agent `bb ci` green on the squash commit, run twice**, because this failure is order-dependent and a single green run does not prove it gone.

### Minor, fix while you are there

`parse/substitute-env-recursive` drops an explicit `nil` out of a **sequence**, though its own docstring promises "An explicit nil is kept: an unresolvable reference is the only thing this drops."

    (parse/substitute-env-recursive {:args ["a" nil "b"]})
    ;; branch: {:args ["a" "b"]}     main: {:args ["a" nil "b"]}

The guard `(when-not (and (some? v) (nil? substituted)) substituted)` cannot work: `keep-indexed` discards every nil return regardless, so the `some? v` branch is dead code. The map branch is correct and spec'd ("keeps an explicit nil"); the sequence branch has no such example. Either make the sequence branch match the promise, or change the promise — but do not leave a guard that reads as if it works.

### Observation, not blocking

The live config now carries a non-schema top-level `:unresolved-refs` key. It produces no unknown-key warning (the unknown-key pass runs on raw data, confirmed), and it does not appear in `config get` (which reads with substitution off), so nothing operator-facing leaks today. Worth keeping in mind if anything ever enumerates top-level config keys.

### Other checks, all clean

No feature file touched by either branch (the `composition.feature` recut is the planner's `## Exceptions`, already on isaac-agent main as `sk-ant-test`). No stray `println`. §4 pass A clean in both repos; pass B `grep -rn "Thread/sleep" spec/` → 0 in isaac-agent, 3 pre-existing in isaac-foundation, none in this diff. §6 pins: the repin I committed names `22694fc`, which is isaac-foundation main.



## Planner note (prowl, 2026-09-21) — on the verify fail

Verify's diagnosis stands. Two bugs: the foundation read that registers a nil config is now its own bean, **isaac-600d** (snapshot read-only; only install registers). Not a blocker for this bean — fix the agent half properly regardless: **carry the reason on the provider slice.** Where the agent cuts a provider's config from the root (`isaac.llm.provider` / `providers`), attach the unresolved-ref hint for that provider's fields (e.g. the `providers.<name>.api-key` entry of `:unresolved-refs`) so `missing-auth-error` reads what it was handed via the two-arity `(loader/unresolved-ref config path)` and touches nothing ambient. Do not pass the root config into provider code and do not call the one-arity form from in-flight code. Fix the dead nil guard in `substitute-env-recursive`'s sequence branch to match its docstring (spec it). Bar, as verify said: isaac-agent `bb ci` green on the squash commit, run twice.
