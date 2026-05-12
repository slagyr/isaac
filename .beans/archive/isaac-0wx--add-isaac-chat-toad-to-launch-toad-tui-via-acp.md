---
# isaac-0wx
title: "Add isaac chat --toad to launch Toad TUI via ACP"
status: completed
type: feature
priority: normal
created_at: 2026-04-11T17:41:31Z
updated_at: 2026-04-11T17:51:37Z
---

## Description

Add --toad flag to isaac chat that launches Toad as a subprocess with Isaac registered as the ACP agent. Toad connects to Isaac over stdio; Isaac is configured as an agent in Toad's config.

## Command surface

  isaac chat --toad           Launch Toad with Isaac as the ACP agent
  isaac chat --toad --dry-run Print the command that would launch Toad (no spawn)

## Behavior

- Check if 'toad' is on the PATH using isaac.util.shell/cmd-available?
  - If not available: print clear error pointing at batrachian.ai/install, exit 1
- Build the spawn command: toad with args configuring Isaac as the ACP agent
- In --dry-run mode: print the command and exit 0, no spawn
- In normal mode: exec toad and block until it exits

## Generic PATH testing mechanism

Add isaac.util.shell/cmd-available? as a shared predicate that wraps a PATH check
(via 'which' or similar). Any code in Isaac that needs to check for an external
command should use this function.

New step definitions (generic — reusable beyond toad):
- Given the command {string} is available
- Given the command {string} is not available

Both use with-redefs on cmd-available? to control the test. Scenarios can verify
behavior for any external command (ollama, docker, git, toad, etc.) by name.

## Implementation

- isaac.util.shell namespace with cmd-available? predicate
- isaac.cli.chat.toad namespace with build-toad-command (pure fn) and spawn-toad!
- build-toad-command returns {:command "toad" :args [...] :env {...}}
- --toad and --dry-run flag parsing added to chat command
- New step: 'the command {string} is/is not available' → with-redefs cmd-available?

## Feature file

features/chat/toad.feature (2 @wip scenarios)

## Acceptance

Remove @wip from both scenarios and verify:
  bb features features/chat/toad.feature:11
  bb features features/chat/toad.feature:19

The step 'the command {string} is/is not available' works for any command name
(not just toad). Demonstrate by adding a quick test in a spec.

Manual verification: bb isaac chat --toad --dry-run prints the real command.
With Toad installed locally, bb isaac chat --toad launches Toad with Isaac
working as the agent.

Full suite: bb features and bb spec pass.

