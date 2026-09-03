---
# isaac-lqbc
title: 'isaac service on Ubuntu: systemd user unit behind a platform seam (Linux + macOS)'
status: draft
type: feature
priority: normal
tags:
    - server
    - linux
created_at: 2026-09-03T20:53:20Z
updated_at: 2026-09-03T20:53:20Z
---

Repo: **isaac-server**. Planning session 2026-09-03 (Micah + plan). Goal: run
Isaac as a background service on Ubuntu with the same `isaac service`
subcommands that work on macOS today.

## Findings

The only macOS-specific code in the stack is `src/isaac/service/macos.clj`
(LaunchAgent plist + launchctl) and the OS guard in `src/isaac/service/cli.clj`
that prints "isaac service is not yet supported on <OS>". Launcher, bb/jvm
runtimes, log sink, and modules are platform-neutral. iMessage is macOS-only
by nature and simply stays out of an Ubuntu box's `:modules`.

## Design (decided)

A systemd **user** unit mirroring the LaunchAgent one-to-one:

| macOS today | Ubuntu |
|---|---|
| `~/Library/LaunchAgents/com.slagyr.isaac.plist` | `~/.config/systemd/user/isaac.service` |
| `launchctl bootstrap gui/<uid> <plist>` | `systemctl --user daemon-reload` + `systemctl --user enable --now isaac` |
| `launchctl bootout gui/<uid>/<label>` | `systemctl --user disable --now isaac` |
| `launchctl kickstart -k` | `systemctl --user restart isaac` |
| `launchctl print` parse (state/pid/last exit) | `systemctl --user show isaac -p ActiveState -p MainPID -p ExecMainStatus` |
| KeepAlive + RunAtLoad | `Restart=always` / `RestartSec=2` / `WantedBy=default.target` |
| StandardOutPath `~/Library/Logs/isaac/server.log` | `StandardOutput=append:~/.local/state/isaac/server.log` (same for stderr) |
| EnvironmentVariables.PATH | `Environment=PATH=...` |

- **Platform seam**: a multimethod keyed on `shell/os-name` (`"Mac OS X"` /
  `"Linux"`) with install!/uninstall!/start!/stop!/restart!/status!/logs!.
  `macos.clj` becomes one implementor; new `src/isaac/service/linux.clj` the
  other. `cli.clj` calls the seam and the OS guard is deleted. Unknown OS
  still errors ("not supported on <OS>").
- **Shared, not copied**: program-argument building (packaged bb, packaged
  jvm `sh -c` wrapper — systemd `ExecStart` does not expand `$()` either, so
  the wrapper is still required — and dev checkout), PATH resolution
  (explicit `--path` → caller PATH → synthesized), and runtime inference from
  the installed args move to a neutral namespace both platforms require.
- **Linger check (Linux-only trap)**: a user unit dies at logout unless
  lingering is on. `install` runs `loginctl show-user <user> -p Linger`; when
  it is not `yes`, print a warning with the exact `loginctl enable-linger`
  command. Warning, not failure.
- **status** maps `ActiveState=active` → `state: running`; `MainPID` → pid;
  `ExecMainStatus` → last exit. Missing unit file → "not installed", exit 1.
- **logs**: same file-slurp / `tail -f` behavior against the Linux log path.
- Help/summaries drop the word "launchd" (platform-neutral wording).
- Clean cutover: the "Linux is not yet supported" scenario is deleted, not
  retained.

## Scenarios (to draft one at a time, then commit @wip in features/cli/service.feature)

Linux counterparts of the existing plist scenarios, under
`Given the operating system is "Linux"` + `And systemctl is stubbed`:

1. packaged install writes the unit: `ExecStart` = launcher + `server`,
   `Environment=PATH=` captured from the caller, `Restart=always`,
   `WantedBy=default.target`; systemctl called with `daemon-reload` and
   `enable --now isaac`.
2. dev-checkout install (`--isaac-dir`) writes `ExecStart` = bb --config … -m isaac.main server.
3. `--root` and `--runtime jvm` reach ExecStart (jvm via the sh -c wrapper).
4. install warns when linger is off (`loginctl show-user` returns `Linger=no`)
   and stays silent when `Linger=yes`; exit 0 either way.
5. status: not installed / running with pid + last exit (from `systemctl show` output).
6. uninstall is idempotent; when installed calls `disable --now` and removes the unit.
7. restart / stop / start call the matching `systemctl --user` verbs.
8. logs prints the file; `--follow` calls `tail -f`.
9. help listing reads platform-neutral (no "launchd").
All existing macOS scenarios stay green unchanged — they are the regression net.

## Step ledger

| step | status |
|------|--------|
| an Isaac root at / the operating system is / "isaac" resolves to / "bb" resolves to / the current process PATH is / isaac is run with / the file … exists / the stdout contains / the stderr contains / the exit code is / sh was called with | reuse |
| **systemctl is stubbed** | **NEW — same shape as `launchctl is stubbed` (records argv, returns exit 0)** |
| **systemctl was called with {expected:string}** | **NEW — count/contains variant of the launchctl step** |
| **systemctl show returns:** | **NEW — doc-string stub for the status query** |
| **loginctl show-user returns:** | **NEW — doc-string stub for the linger check** |
| **the unit file contains:** | **NEW — INI key/value table (`Service.ExecStart`, `Service.Environment`, `Install.WantedBy`); the plist step parses XML and does not apply** |

## Acceptance (fill in once scenarios are committed)

Remove @wip, then in isaac-server:

    bb features features/cli/service.feature
    bb spec spec/isaac/service

0 failures. Existing macOS scenarios unchanged and green. `bb lint` clean.

## Out of scope

- Installing bb / clojure / the isaac launcher on Ubuntu (brew-on-Linux vs
  release tarball — separate ops decision; the brew formula is unverified on
  Linux).
- System-level (root) units. User units only, matching the macOS LaunchAgent
  (per-user) model.
- journald integration beyond what the file redirect gives.
