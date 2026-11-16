---
# isaac-hu1n
title: 'Named CLI remotes: isaac --remote <name> picks a host from ~/.config/isaac.edn (token from env, file or command)'
status: draft
type: feature
priority: normal
tags:
    - cli
created_at: 2026-11-16T15:36:48Z
updated_at: 2026-11-16T15:36:48Z
---

Repos: **isaac-foundation** (launcher routing, `--remote` flag), **isaac-cli-proxy** (named remotes, token command, `isaac remote` subcommands). No command module changes — routing stays generic over argv, so isaac-agent's `prompt` never learns about the server.

Follows isaac-gar0 (one `:cli :remote` in `~/.config/isaac.edn`, launcher routes every non-local-only command through cli-proxy to the server's `/cli`).

## Why (Micah, 2026-11-16)

Micah keeps hand-written wrappers per host in `~/.local/bin`: `zane-isaac` / `yopp-isaac` export `ISAAC_REMOTE_TOKEN` from the macOS keychain (`security find-generic-password … -w`) and exec `isaac remote wss://<host>/cli -- "$@"`; `zane` / `yopp` wrap those for Toad (`toad acp "zane-isaac acp --crew zane"`). Isaac should know the hosts by name instead.

## Design (proposed 2026-11-16, shape to confirm before scenarios)

```clojure
;; ~/.config/isaac.edn
{:root "/Users/micahmartin/.isaac"
 :cli  {:remotes {:zane {:url   "wss://zanebot.example/cli"
                         :token {:command "security find-generic-password -a $USER -s zanebot-cli-token -w"}}
                  :yopp {:url   "wss://yopp.example/cli"
                         :token "${YOPP_TOKEN}"}}
        :remote  :zane}}   ; optional default; absent ⇒ commands run locally
```

- **Select a host:** `isaac --remote <name> <command> …`. `--local` (and `ISAAC_CLI_LOCAL=1`) still forces local. Unknown name ⇒ error listing the configured names, nothing runs.
- **Token sources:** `${VAR}`, token file, literal (0600 file only, as today), and new `{:command "…"}` — run at use, stdout trimmed is the token, nonzero exit or empty output ⇒ the command fails naming the remote. The token is never written to disk or argv.
- **Compatibility:** the existing single `:cli {:remote {:url … :token …}}` keeps working (it is an unnamed default); `remote use` writes the new shape.
- **Managing:** `isaac remote use <name> <url> [--token-env VAR | --token-command CMD | --token TOKEN]`, `isaac remote list`, `isaac remote default <name>|off`, `isaac remote status [<name>]` (target + reachability/auth probe, as today).
- **No bare `isaac <host> …`** — a host name could shadow a command.
- Failure reporting, `:local-only` commands, reconnect behavior: unchanged from isaac-gar0.

Afterwards Micah's Toad launchers become `toad acp "isaac --remote zane acp --crew zane"`.

## Acceptance

Draft — scenarios after the config shape is confirmed: `--remote zane` routes to zane's url with its token; no default ⇒ local; default set ⇒ routed; `--local` beats the default; unknown name lists names; a token command supplies the bearer and a failing one errors; the old single-remote shape still routes; `remote use/list/default/status` round-trip the pointer file and preserve `:root`.
