---
# isaac-hlak
title: 'isaac remote --help shows only the url form: add named remotes and the use/list/remove/status subcommands'
status: draft
type: bug
priority: low
tags:
    - cli
created_at: 2026-11-16T18:30:01Z
updated_at: 2026-11-16T18:30:01Z
---

Repo: **isaac-cli-proxy**. Follows isaac-hu1n (named remotes, landed edaa7b9, released 0.1.8).

## Observed (Micah's Mac, 2026-11-16, cli-proxy 0.1.8)

`isaac remote --help` still shows only the url form:

```
Usage: isaac remote <url>/cli <command...>
```

It doesn't mention `isaac remote <name> -- <command>`, the `use / list / remove / status` subcommands, or where remotes are stored. `--token-command` is listed as "(remote use only)" without saying what `remote use` is.

## Fix

Help shows both forms and the subcommands, e.g.:

```
Usage: isaac remote <name> -- <command...>
       isaac remote <url>/cli -- <command...>
       isaac remote use <name> <url> [--token-env VAR | --token-command CMD | --token-file PATH]
       isaac remote list | remove <name> | status [<name>]

Remotes live in ~/.config/isaac.edn under :cli :remotes.
```

When remotes are configured, help ends with the `isaac remote list` lines so the configured names are right there.

## Acceptance

Draft — scenario to come: `isaac remote --help` stdout contains the name form, each subcommand, and the configured remote names.
