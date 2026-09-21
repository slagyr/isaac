---
# isaac-12fo
title: Provider config carries an :env map, so two claude-code providers can hold two subscriptions
status: completed
type: feature
priority: normal
tags:
    - claude-code
    - config
created_at: 2026-09-21T00:10:53Z
updated_at: 2026-09-21T01:11:32Z
---

Micah has several Claude Code subscriptions and wants Isaac to drive more than
one at a time. Everything needed is already in place except a per-provider
environment.

## What already works

- **Two named providers of type `claude-code`.** Proven by the passing scenario
  "a provider of type claude-code under another name drives the turn (isaac-ejj3)"
  in `features/llm/api/claude_driver.feature`.
- **Per-subscription auth in the CLI.** `CLAUDE_CONFIG_DIR` isolates the login,
  not merely settings. Verified 2026-09-20 on this laptop, CLI 2.1.278:

      claude mcp list                                 -> the real config, 6 servers
      CLAUDE_CONFIG_DIR=/tmp/cc-alt-probe claude ...  -> "Not logged in - Please run /login"

  The alt dir got its own `.claude.json`, and the keychain already holds six
  separate `Claude Code-credentials` entries, one per config dir.

## What blocks it

`isaac-claude-code/src/isaac/llm/api/claude_cli.clj:435` inherits the server's
environment wholesale and offers no per-provider override:

```clojure
(defn- subprocess-env
  ([] (dissoc (into {} (.environment (ProcessBuilder. []))) "ANTHROPIC_API_KEY"))
  ([nonce] (assoc (subprocess-env) "ISAAC_MCP_NONCE" nonce)))
```

Both providers therefore spawn `claude` with identical credentials, and the
second subscription is unreachable.

## Work

Let a provider's config carry an `:env` map, merged over the inherited
environment when the CLI is spawned. Declare it in the provider-template schema
in `src/isaac-manifest.edn` so `isaac config` knows the shape.

Target config:

```clojure
;; config/providers/claude-a.edn
{:type :claude-code
 :command "claude"
 :env {"CLAUDE_CONFIG_DIR" "/Users/zane/.claude-a"}}
```

`ANTHROPIC_API_KEY` must stay stripped after the merge — an `:env` map must not
be a way to smuggle it back in. `ISAAC_MCP_NONCE` is set by Isaac per turn and
must win over anything in `:env`.

## Acceptance

- a provider with an `:env` map spawns the CLI with those variables set, merged
  over the inherited environment
- two providers of type claude-code with different `:env` maps spawn with their
  own values; neither leaks into the other
- a provider with no `:env` behaves exactly as today
- `ANTHROPIC_API_KEY` is absent from the spawned environment even when `:env`
  supplies it
- `ISAAC_MCP_NONCE` is Isaac's, not `:env`'s

## Operational notes (not code)

- The second config dir must be logged in **on zanebot directly**, not over SSH
  — the keychain is not reachable from an SSH session. One-time, needs Micah at
  the machine.
- `:max-in-flight` is crew-wide, so splitting subscriptions only buys throughput
  if the crews are split too. Otherwise both providers queue behind one limit.


## Partial implementation 2026-09-20 (bean/isaac-12fo @ f2b6a15, local only)

The spawn side is done and green: `subprocess-env` merges the provider's `:env`
over the inherited environment, `ANTHROPIC_API_KEY` stays stripped after the
merge, `ISAAC_MCP_NONCE` outranks config, and `:env` is declared in both the
provider template and the schema in `src/isaac-manifest.edn`.

**The feature is blocked one layer up, and this is the real work.** A provider's
`:env` does not survive config resolution. With
`config/providers/claude-a.edn` setting `env.CLAUDE_CONFIG_DIR`, the factory is
reached as:

    make name=claude-a keys=(:api :auth :command :drives-tool-loop? :env
                             :stream-supports-tool-calls)  env= {}

`:env` is present, but holds `{}` — the template default — never the user's map.
So the value is lost between the provider EDN file and `make`. Ruled out along
the way:

- the table step is fine: `isaac-edn-file-exists` splits a dotted path and
  `assoc-in`s it, so `env.CLAUDE_CONFIG_DIR` does build `{:env {:CLAUDE_CONFIG_DIR ...}}`
- `resolve-provider*` (isaac-agent `llm/providers.clj:63`) merges the inherited
  template under the user entry and drops only `:type`/`:from`, so the user's
  `:env` should win
- `augment-provider` (isaac-agent `drive/turn.clj:1223`) merges the whole
  `api/config`, so it is not the filter
- an EDN map literal in one table cell behaves the same as the dotted form

The scenario "two claude-code providers spawn the CLI with their own
environments" is committed `@wip` against that. Next step is to find where a
provider's non-template keys are dropped between the EDN file and
`make-provider` — likely provider config normalization in isaac-agent — which
means this bean probably needs a sibling there, the way isaac-dgod and
isaac-8cur split.

Nothing deployed. Nothing dispatched.


## Summary of Changes (2026-09-20, main-sha 34dbfa7, claude-code 0.1.18)

`subprocess-env` merges the provider's `:env` over the inherited environment.
`ANTHROPIC_API_KEY` stays stripped **after** the merge, so `:env` cannot smuggle
back the key the provider deliberately removes to force subscription auth;
`ISAAC_MCP_NONCE` outranks any configured value.

**The blocker was the schema, not the code — worth remembering.** The
`:providers` value-spec prunes each provider config to its declared schema,
extended per-template through the `:isaac.agent/provider-template` berth's
`:schema`. **A `:map` entry with no `:key-spec` has its contents pruned**, so
`:env` reached the factory as `{}` however it was written. `:headers` in the
core provider schema already had the right shape. Declaring
`:key-spec {:type :keyword}` and `:value-spec {:type :string}` fixes it, and
keyword keys accept both forms: the dotted `env.CLAUDE_CONFIG_DIR` that
`isaac config set` produces, and a hand-written `{"CLAUDE_CONFIG_DIR" "..."}`
map. The next map-valued config key anyone adds will hit this same trap.

No isaac-agent change was needed; the sibling bean anticipated earlier is not
required.

Ruled out along the way (recorded so nobody repeats it): the table step builds
the nested map correctly; `resolve-provider*` drops only `:type`/`:from`;
`augment-provider` merges the whole config; `extra-args` from a newly named
provider does reach the spawn; the inherited environment does reach the spawn.

Scenario "two claude-code providers spawn the CLI with their own environments"
covers two providers side by side, one written each way, asserting each gets its
own `CLAUDE_CONFIG_DIR`, neither leaks into the other, and `ANTHROPIC_API_KEY`
is absent even when a provider's `:env` sets it. CI green: 84 + 53, 0 failures.

Remaining operational step (Micah): `/login` the second config dir on zanebot
directly — the keychain is not reachable over SSH.
