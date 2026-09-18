---
# isaac-auie
title: isaac server auth mint|rotate|revoke|list — secret printed once, hash written to config (hot-reload), overlap rotation
status: in-progress
type: feature
priority: high
tags:
    - security
created_at: 2026-09-18T04:13:56Z
updated_at: 2026-09-18T22:09:16Z
parent: isaac-gym1
blocked_by:
    - isaac-bzgw
---

Child 2 of isaac-gym1 (principals epic). Repo: isaac-http. Blocked by child 1.

## Work
`isaac server auth …` (contributed via `:isaac/cli`; `server` is already isaac-http's command):
- `mint <name> --scopes a,b[,…] [--expires YYYY-MM-DD]` — generates 32 random bytes (base64url), prints the plaintext ONCE to stdout (nothing else on stdout so it can be captured), writes `{:hash "sha256:…" :scopes … :expires …}` under `:server :auth :principals <name>` through the config mutation API (hot-reloads; formatted per isaac-2nkg when that lands). Refuses to overwrite an existing name (use rotate).
- `rotate <name> [--overlap 24h]` — mints a new secret for the name; with `--overlap` the old hash stays valid as `<name>@prev` until the window ends (child 1 honours `:expires` on it), so clients update at their own pace.
- `revoke <name>` — removes the principal (and any `@prev`).
- `list` — table: name, scopes, expires, last-used (from child 4's audit store; "never" until then).
- Runs local-only in spirit (admin scope over the pipe; `:read-only #{"list"}` for kjzq).
- The plaintext never touches logs, the cache, or the transcript when run through a crew (stdout of a tool call IS the transcript — document: mint from a shell, not from a crew).

## Scenarios (draft; features/cli/auth_principals.feature)
1. mint prints exactly one line (the secret) and the config gains the hash, never the secret (grep the file for the printed value)
2. mint of an existing name is refused; rotate replaces the hash and (with --overlap) keeps the previous one valid until expiry
3. revoke removes the principal and the next request with its token is 401
4. list shows scopes and expiry; the secret column does not exist
5. the minted token authenticates against a running server without restart (integration, reuses child 1's steps)



## Scenarios (committed @wip — isaac-http `features/cli/auth_principals.feature` @ 0cd9d1d)

| line | scenario |
|------|----------|
| :12 | mint prints the secret once and writes only its hash to config |
| :25 | the minted secret authenticates as that principal |
| :36 | mint with --expires records the expiry |
| :42 | mint refuses an existing name |
| :51 | mint requires at least one scope |
| :58 | rotate replaces the hash and the old secret stops working |
| :71 | rotate with --overlap keeps the old secret valid until the window ends |
| :86 | revoke removes the principal and its overlap twin |
| :95 | revoke of an unknown principal is an error |
| :101 | list shows name, scopes and expiry, never a hash or secret |
| :113 | a running server honours a principal minted from the CLI without a restart |

Overlap twin naming: `<name>@prev` with `:expires` = now + overlap; child 1 already treats `:expires` uniformly, and the request log names the twin (`ci@prev`) so audit can tell old from new. `list` columns: name, scopes, expires (`-` when none), last-used (`never` until isaac-2a2x).

## Step ledger

| step | status |
|------|--------|
| an Isaac root at … / config: / the Isaac server is started / the isaac config is reloaded | reuse |
| isaac is run with … / the exit code is … / the stdout is empty / the stdout does not contain … / the stderr contains … / the stdout lines match: | reuse (foundation cli_steps) |
| the config file … does not contain … / the isaac config path … matches … / the log has (no) entries matching: | reuse (foundation; `matches` lands with isaac-bzgw) |
| principal … is configured with secret … and scopes … (+ expiring …) / a fixture route … requires scope … | reuse (isaac-bzgw) |
| the client sends GET … with header … / the response status is … | reuse |
| **the stdout has exactly {n} line(s)** | **NEW — foundation spec-support (generic)** |
| **the stdout line is a bearer secret of at least {n} characters** | **NEW — captures the printed line as `<the printed secret>` for later steps; asserts length + charset** |
| **`<the printed secret>` placeholder** in `Authorization: Bearer <the printed secret>` / `does not contain the printed secret` / log `#".*<the printed secret>.*"` | **NEW — substitution in the client-header, config-file and log-matcher steps (same pattern as `${stub.url}` in cli-proxy)** |
| **the isaac config path {path} is absent** | **NEW — foundation spec-support (generic)** |

Four new step families (two generic → foundation spec-support, two HTTP-side).

## Acceptance
```
cd isaac-server && bb features features/cli/auth_principals.feature && bb features features/server/principals.feature && bb ci
```
`isaac server auth --help` documents mint/rotate/revoke/list and the one-time-secret rule. Version bump; pin is a train step.

Dispatched: hail 5c00df09 2026-09-18T06:14Z (band isaac-work)

## Checkpoint (scrapper@isaac-work-2, 2026-09-18)

Claimed. Worktree `/Users/zane/agents/isaac/work-2/isaac-server-auie` on `bean/isaac-auie` @ ad4ba5d (origin/main, isaac-bzgw landed). Feature file already committed @wip at `features/cli/auth_principals.feature`.

Resume: implement `isaac server auth mint|rotate|revoke|list` in isaac-http (`src/isaac/http/cli.clj` currently only starts the server). Write unit specs first (auth secret generation + config mutate via `isaac.config.mutate/set-config`/`unset-config`), then un-wip scenarios.

Missing steps named in the bean (must land in foundation spec-support or http spec-support):
- the stdout has exactly {n} line(s)
- the stdout line is a bearer secret of at least {n} characters (captures <the printed secret>)
- substitution of <the printed secret> in Authorization / does not contain / log matchers
- the isaac config path {path} is absent

`server` CLI is registered via `cli-api/run :server` in `isaac.http.cli` (not the http manifest's `:isaac/cli`, which only has mcp-bridge). Subcommands via `cli-api/subcommands :server`. Version currently 0.1.15; bump on green. Pin is a train step.

Dispatched: hail b6b3d9f6 2026-09-18T18:23Z (session isaac-work-2, continuation of 5c00df09 — turn ended at cycle budget without hand-off)

## Checkpoint (scrapper@isaac-work-2, resume 2)

Worktree `/Users/zane/agents/isaac/work-2/isaac-server-auie` `bean/isaac-auie` @ pushed HEAD (base origin/main db2b639 after rebase).

**Done**
- `src/isaac/http/auth_cli.clj` mint/rotate/revoke/list-rows + generate-secret (32-byte unpadded base64url)
- Overlap twin stored as `:prev` on the principal — schema `:id` key-spec cannot hold `ci@prev` as a map key (`path/parse` splits `@`)
- `auth/principals` expands `:prev` to `:ci@prev` so wrap-auth / request log name the twin
- Unit specs in `spec/isaac/http/auth_cli_spec.clj` + overlap example in `auth_spec.clj`

**Red / next**
1. Wire CLI: `cli-api/subcommands :server` + `run-fn` dispatch `auth mint|rotate|revoke|list` (print secret alone on stdout; errors on stderr). Help documents one-time-secret rule. Bump version 0.1.15 → 0.1.16.
2. Feature steps (http `server_steps` unless already in foundation):
   - Then the stdout has exactly {n} line(s)
   - Then the stdout line is a bearer secret of at least {n} characters (capture `:printed-secret`)
   - interpolate `<the printed secret>` in header / config-does-not-contain / log matcher
   - Then the isaac config path {path} is/matches/is absent (Then, not Given)
3. Un-@wip `features/cli/auth_principals.feature`. Overlap scenario asserts `http.auth.principals.ci.prev.expires` (not `ci@prev`) unless we also write a sibling key — prefer updating the planted feature to `.prev` if planner agrees; otherwise keep expanding twins only at auth time and adjust the Then path.
4. `bb features features/cli/auth_principals.feature && bb features features/server/principals.feature && bb ci`

Resume: `src/isaac/http/cli.clj` `run-fn` / new auth subcommand dispatch. Do not start other beans.

Dispatched: hail 1f64d796 2026-09-18T19:58:03Z (session isaac-work-2, continuation of b6b3d9f6 — turn ended at cycle budget without hand-off)

## Checkpoint (scrapper@isaac-work-2, resume 5)

Worktree `/Users/zane/agents/isaac/work-2/isaac-server-auie` `bean/isaac-auie` @ dd69234 (base origin/main@5c8148e). Shared sibling isaac-http left on main.

**Done**
- mint/rotate/revoke/list CLI; overlap twin nested as `:previous`; wrap-auth expands `name@prev`
- Feature steps: printed-secret capture/interpolation, `@prev` rewrite, `#"…"` stdout-matches
- `--dev` logs `:server/dev-mode-enabled`; `auth list` prints last-used from audit store
- Green: `auth_principals.feature` 11/0, `principals.feature` 11/0, `auth_audit.feature` + `dev-reload.feature` (34/0 combined)

**Next**
`bb spec && bb features` (full suite — `bb ci` pins task is red on main: "Unknown modules subcommand: pins"). Then unverified + hail isaac-verify reply_to 1f64d796.

Resume: `bb spec && bb features` from `/Users/zane/agents/isaac/work-2/isaac-server-auie`. Do not start other beans.
