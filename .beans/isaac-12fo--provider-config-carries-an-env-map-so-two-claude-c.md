---
# isaac-12fo
title: Provider config carries an :env map, so two claude-code providers can hold two subscriptions
status: todo
type: feature
tags:
    - claude-code
    - config
created_at: 2026-09-21T00:10:53Z
updated_at: 2026-09-21T00:10:53Z
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
