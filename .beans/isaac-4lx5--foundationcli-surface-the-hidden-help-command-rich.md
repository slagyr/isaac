---
# isaac-4lx5
title: 'foundation/cli: surface the hidden ''help'' command + richer topic help'
status: draft
type: feature
created_at: 2026-06-18T18:07:35Z
updated_at: 2026-06-18T18:07:35Z
---

`isaac help [target]` already works (src/isaac/main.clj:107-112 — prints usage,
or per-command help via registry/command-help) but is INVISIBLE: usage() builds
its menu from registry/all-commands (main.clj:63), which doesn't include `help`.
So we have a help command nobody can discover.

## Two parts

1. SURFACE it: list `help` in the top-level Commands menu (e.g. a synthetic
   entry, or register it as a real command) with a summary like "Show help for
   a command or topic".

2. RICHER topic help (git-style `isaac help <topic>`): today `isaac help <cmd>`
   resolves only registered COMMANDS. Extend to non-command TOPICS:
   • `isaac help root`   — root + config-source precedence (the detail the
     trim+relocate bean moves out of the front door).
   • `isaac help config` / `modules` etc. already reachable via the command
     path; keep those.
   Build on the existing help infra (registry :help-text, print-subcommand-help!,
   the `config help <sub>` / `modules help <sub>` patterns) rather than a new
   system.

## Open questions (decide during design)

• Topic registry: a simple map of topic -> help-text, or fold topics into the
  command registry as hidden/virtual entries?
• Scope of topics for v1 — just `root`, or a small curated set (root, modules,
  config)?

## Acceptance (sketch — feature-test)

• `isaac` menu lists `help`.
• `isaac help` prints usage; `isaac help <command>` prints that command's help
  (unchanged); `isaac help root` prints the root/source-precedence topic.
• `isaac help <unknown>` -> friendly "Unknown command or topic", exit 1.

## Relationships

• Complements the trim+relocate bean: that one moves root detail to `config
  sources`; this one ALSO exposes it as `isaac help root`.
• Man page (`man isaac`) considered separately and DEFERRED — not in scope here.
