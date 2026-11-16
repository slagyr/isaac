---
# isaac-hu1n
title: 'Named CLI remotes: isaac remote <name> -- runs a command on a host from ~/.config/isaac.edn (token from env, file or command)'
status: in-progress
type: feature
priority: normal
tags:
    - cli
created_at: 2026-11-16T15:36:48Z
updated_at: 2026-11-16T17:22:37Z
---

Repo: **isaac-cli-proxy** only. No foundation change: per Micah's 2026-09-30 ruling (isaac-ewua) foundation never reads a cli-proxy setting and there is no implicit/default routing. A plain `isaac …` always runs locally.

## Why (Micah, 2026-11-16)

Micah keeps hand-written per-host wrappers in `~/.local/bin`: `zane-isaac` / `yopp-isaac` export `ISAAC_REMOTE_TOKEN` from the macOS keychain and exec `isaac remote wss://<host>/cli -- "$@"`; `zane` / `yopp` wrap those for Toad. The hard part is the url + secure token lookup; that moves into Isaac. The wrappers stay as one-liners Micah names himself (`exec isaac remote zane -- "$@"`, `exec toad acp "isaac remote zane -- acp --crew zane" "$@"`).

## Design (approved 2026-11-16)

```clojure
;; ~/.config/isaac.edn
{:root "/Users/micahmartin/.isaac"
 :cli  {:remotes {:zane {:url   "wss://zanebot.example/cli"
                         :token {:command "security find-generic-password -a $USER -s zanebot-cli-token -w"}}
                  :yopp {:url   "wss://yopp.example/cli"
                         :token "${YOPP_TOKEN}"}}}}
```

- `isaac remote <name> -- <command…>` — an argument with `://` is a url (today's form, unchanged); otherwise it is a remote name. Unknown name ⇒ `unknown remote: <name> (configured: …)`, exit 1, no connection.
- Token sources per remote: `${VAR}`, literal (0600 file only, as today), and `{:command "…"}` — run at use, trimmed stdout is the token; nonzero exit or empty output ⇒ `<name>: token command failed`, exit 1, no connection. The token never reaches argv, stdout or stderr. `--token-file` / `--token-env` still override.
- `isaac remote use <name> <url> [--token-env VAR | --token-command CMD | --token-file PATH]` adds/replaces one remote, preserving `:root`, other keys and other remotes (private file when a literal token is written).
- `isaac remote list` — one line per remote: `<name>  <url>  isaac remote <name> -- <command>`; no probe, no token shown.
- `isaac remote remove <name>`; `isaac remote status [<name>]` — target + reachability/auth probe for that remote; `no remotes configured` when empty.
- The single legacy `:cli {:remote {:url :token}}` is still honored for the url-matched token lookup (existing scenario); `remote use`/`off` for it are replaced by the named forms.
- Handbook: update `resources/isaac/cli_proxy/handbook.md` with named remotes and the one-line wrapper examples. No shim generator.

## Acceptance

Scenarios in `isaac-cli-proxy/features/remote.feature` (scenario-level `@wip`; remove each when it passes):

- `bb features features/remote.feature:306` — a named remote runs the command on its host with its token
- `bb features features/remote.feature:325` — a token command supplies the bearer credential
- `bb features features/remote.feature:339` — a failing token command names the remote and connects to nothing
- `bb features features/remote.feature:353` — an unknown remote name lists the configured names
- `bb features features/remote.feature:364` — remote use adds a named remote, keeps :root and other remotes
- `bb features features/remote.feature:380` — remote use with a token file writes a private config
- `bb features features/remote.feature:390` — remote list shows each remote with a ready-to-use command line
- `bb features features/remote.feature:403` — remote remove drops one remote and keeps everything else
- `bb features features/remote.feature:419` — remote status probes a named remote
- `bb features features/remote.feature:436` — remote status with no remotes says so

Run the whole file: `bb features features/remote.feature`.

feature-baseline: isaac-cli-proxy 9a782e621aecf29159cd8d2ed1a16d2536d3b94a
feature-blob: isaac-cli-proxy features/remote.feature c53ef543858b4a41a4ce6f51c115aab3505c78a6 306,325,339,353,364,380,390,403,419,436
