---
# isaac-hoe
title: "Migrate CLI parsing to clojure.tools.cli"
status: completed
type: task
priority: low
created_at: 2026-04-12T00:05:50Z
updated_at: 2026-04-12T00:18:21Z
---

## Description

Isaac currently hand-rolls its CLI parsing in src/isaac/main.clj. One global parse-opts function has a single case-block listing every flag known to any command. Adding a flag to one command means editing both the global parser AND that command's :options list — two sources of truth that drift apart (see isaac-ayw for a recent instance).

## Goal
Each command is fully self-contained. It declares its own option spec, and registering the command with isaac.cli.registry is enough for:
- main.clj to dispatch to it by name
- main.clj to print top-level usage listing commands
- The command to parse its own flags
- The command to generate its own help text

main.clj has no knowledge of individual commands' flags.

## Approach
Use clojure.tools.cli (bb-compatible) where the option spec is a single declaration that produces both the parser and the summary help text:

  (def chat-options
    [["-a" "--agent NAME" "Use a named agent" :default "main"]
     [nil  "--toad"        "Launch Toad TUI via ACP"]
     [nil  "--dry-run"     "Print Toad command without spawning"]
     ...])

## Changes
- Add org.clojure/tools.cli to deps.edn and bb.edn
- Each command namespace (chat, agent, auth, server) declares its own option spec
- Each command's run-fn calls tools.cli/parse-opts on its argv
- isaac.cli.registry gains an :option-spec field; :options disappears
- isaac.cli.registry/command-help generates the summary from the spec
- main.clj loses parse-opts entirely — it only dispatches by command name
- Existing scenarios in features/cli/*.feature and features/chat/*.feature should still pass with no changes

## Acceptance
- bb features and bb spec pass
- No global flag list in main.clj
- Adding a new option to a command means editing ONLY that command's namespace
- isaac-ayw's underlying problem (help/parser drift) becomes impossible

