---
# isaac-lqbc
title: 'isaac service on Ubuntu: systemd user unit behind a platform seam (Linux + macOS)'
status: in-progress
type: feature
priority: normal
tags:
    - linux
    - unverified
    - server
created_at: 2026-09-03T20:53:20Z
updated_at: 2026-09-03T23:42:02Z
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


## Scenarios (committed @wip at slagyr/isaac-server 5116743) — supersedes the sketch above

`features/cli/service_linux.feature` — 13 scenarios, whole feature @wip:
packaged install (unit rows + daemon-reload + enable --now), dev-checkout
install, --root, --runtime jvm (+ status reads runtime back), linger warn /
linger quiet, status not-installed / running, uninstall idempotent /
removes, start-stop-restart verbs, logs / logs --follow.

`features/cli/service.feature` (macOS) — shared edits, each @wip:
- "status shows running…" now uses `sh "launchctl" prints to stdout:`.
- three help scenarios reworded: `Install Isaac as a background service` /
  `Remove the Isaac background service` (no "launchd").
- "Linux is not yet supported" scenario DELETED; header paragraph replaced.
The untouched macOS scenarios (14 non-wip, green at 5116743) are the
regression net.

## Step ledger (final)

| step | status |
|------|--------|
| an Isaac root at / the operating system is / {cmd} resolves to / the current process PATH is / isaac is run with / sh was called with / the file … contains: / the file … does not exist / the stdout contains / the stderr contains / the stderr does not contain / the stdout matches / the exit code is | reuse |
| **shell commands are stubbed** | **RENAME of `launchctl is stubbed` — same fn; update the step def AND the macOS Background line (the Linux file already uses the new name)** |
| **sh {cmd:string} prints to stdout:** | **NEW generic canned stdout keyed by argv[0]; REPLACES `launchctl print returns:` (delete the old step)** |
| **the INI file {path:string} matches:** | **NEW — systemd unit / INI parser: `[Section]` + `key=value`; repeated keys collect into a vector, `Section.key[n]` indexes; expected values normalize `~` to the test home like `launchctl was called with` does; missing file fails loudly** |

## Worker notes

- Every subprocess goes through `isaac.shell/sh!` / `exec!` — never
  `clojure.java.shell` directly. The scenarios run on macOS/CI with the shell
  stubbed; a real `systemctl`/`loginctl` call is a defect.
- Unit name is plain `isaac` (`isaac.service`), not the reverse-DNS launchd
  label. stdout line: `Service installed: isaac`.
- `ExecStart` is one space-joined line; the jvm `sh -c` wrapper is a single
  double-quoted argument with inner `\"` and `$$` for the literal dollar
  (systemd substitutes lone `$`). `runtime-from-program-arguments` already
  infers jvm from `/bin/sh` + `clojure` + `-m isaac.main` — reuse it.
- Linger: `loginctl show-user <user> -p Linger`; anything but `Linger=yes`
  (including empty output / error) → stderr warning containing
  "lingering is off" and the literal `loginctl enable-linger` command; exit 0.
- status: `systemctl --user show isaac -p ActiveState -p MainPID -p ExecMainStatus`;
  `active` → `state: running` exit 0; any other state prints it raw, exit 1.
- Log path (process stdout capture, distinct from the `<root>/logs` sink):
  `~/.local/state/isaac/server.log` via `StandardOutput=append:` /
  `StandardError=append:`; `logs` reads that file, `--follow` = `tail -f`.
- Unknown OS still errors ("isaac service is not supported on <OS>") — no
  scenario for it (no absence tests).

## Acceptance

Remove @wip from `features/cli/service_linux.feature` and from the four
@wip scenarios in `features/cli/service.feature`, then in isaac-server:

    bb features features/cli/service.feature features/cli/service_linux.feature
    bb spec spec/isaac/service
    bb lint

0 failures; every new `src/` namespace has a `spec/` twin (platform seam,
linux impl, shared program-args ns). Full `bb features` + `bb spec` green.
Hand off with `--tag=unverified`, status stays in-progress.


## Implementation (2026-09-03, plan@isaac-plan — worked locally after hail 1aeaacc1 died to isaac-jkx7)

Landed on isaac-server main @ e9b1f40 (on top of the @wip scenarios at 5116743).

- `src/isaac/service/manager.clj` — `Manager` protocol (service-name, install!,
  uninstall!, start!, stop!, restart!, status!, logs!). Protocol, not
  multimethod, per project DI convention.
- `src/isaac/service/launch.clj` — shared: `program-arguments`, `service-path`
  (was macos `plist-path`), `default-path` (was `launchd-path`),
  `runtime-from-program-arguments`, `jvm-packaged-exec-cmd`.
- `src/isaac/service/linux.clj` — `SystemdManager`: unit at
  `~/.config/systemd/user/isaac.service`; `quote-arg` (`$`→`$$`, C-escaped
  double quotes) + `exec-start-args` inverse for runtime inference; install =
  write + daemon-reload + enable --now + `loginctl show-user <user> -p Linger`
  → `{:linger? bool}`; status via `systemctl --user show isaac -p ActiveState
  -p MainPID -p ExecMainStatus` (active→running, else raw; MainPID 0 dropped);
  logs at `~/.local/state/isaac/server.log`.
- `macos.clj` — `LaunchdManager` record; program-args/PATH moved to launch;
  install! returns `{}`.
- `cli.clj` — `manager-for` (Mac OS X / Linux / nil); all subcommands take the
  manager; "Service installed: <service-name>"; linger warning on stderr
  ("lingering is off" + `loginctl enable-linger <user>`); unsupported OS →
  "isaac service is not supported on <OS>" exit 1; summaries drop "launchd".
- Steps (`service_steps.clj`): `shell commands are stubbed` (renamed),
  `sh {cmd} prints to stdout:` (generic, keyed by argv[0]; replaced
  `launchctl print returns:`), `the INI file {path} matches:` (repeated keys →
  vector, `Section.key[n]`, `~` normalized). `launchctl was called with` kept
  for the macOS feature.
- Specs: new `launch_spec`, `manager_spec`, `linux_spec`; `macos_spec` lost
  the moved PATH examples; `cli_spec` "unsupported OS" now uses "Windows 11"
  and a new "on Linux" describe covers install/linger/status/restart.
- Scenario 1 retitled "…inheriting the CLI root…": a packaged install passes
  the invoking CLI's `--root` into ExecStart (the harness runs with
  `/target/test-state`), same as the macOS behavior — not a harness leak.

Gate (bb, native): `bb spec spec/isaac/service` 97/0; `bb features
features/cli/service.feature features/cli/service_linux.feature` 31/0;
`bb verify` 214 spec / 67 feature examples, 0 failures; `bb lint src` 0
errors (the spec-file `unresolved symbol` lint errors are pre-existing speclj
noise — untouched `runtime_spec.clj` reports the same 6).

## Verify fail (attempt 1, 2026-09-03): explicit acceptance is still unmet (`bb lint` and full native `bb features` are not green as written)

Evidence on `isaac-server` `origin/main` `e9b1f400d834f18a8ba23efd0fa4bf3020fa9d9e`:
- acceptance-specific scenarios are no longer `@wip`:
  - `features/cli/service.feature` has no `@wip`
  - `features/cli/service_linux.feature` has no `@wip`
- acceptance-targeted commands are green:
  - `bb features features/cli/service.feature features/cli/service_linux.feature` → `31 examples, 0 failures, 122 assertions`
  - `bb spec spec/isaac/service` → `97 examples, 0 failures, 178 assertions`
- new service namespaces do have spec twins:
  - `src/isaac/service/manager.clj` ↔ `spec/isaac/service/manager_spec.clj`
  - `src/isaac/service/launch.clj` ↔ `spec/isaac/service/launch_spec.clj`
  - `src/isaac/service/linux.clj` ↔ `spec/isaac/service/linux_spec.clj`
- but the bean's explicit broader acceptance is still red in verify:
  - `bb lint` exits `1` with `clj-kondo: 126 error(s), 37 warning(s)`
    - includes unresolved Speclj DSL symbols across `spec/`, including touched service specs; worker only proved `bb lint src`, not the accepted `bb lint`
  - full native `bb features` exits `124` with terminal output `features timed out after 180s`
    - full JVM equivalent is green (`clojure -M:test:features` → `67 examples, 0 failures, 184 assertions`, `113.38 real`), but the acceptance text still requires full native `bb features` green
  - full native `bb spec` is green (`214 examples, 0 failures, 388 assertions`)

This bean cannot pass until the accepted commands are green as written, or planning amends the lint/full-suite requirements.

## Worker follow-up (2026-09-03, scrapper)

Resumed from verify fail on fresh `isaac-server` main @ `e59f2b9` (which includes `e9b1f40` plus subsequent suite fixes).

To satisfy the accepted lint gate durably, added tracked `.clj-kondo/config.edn` in `isaac-server` commit `9dbd8fd` teaching `bb lint` about the Speclj DSL symbols used across `spec/`.

Full native gates are now green:
- `bb lint` → `clj-kondo: 0 error(s), 16 warning(s)`
- `bb features` → `67 examples, 0 failures, 184 assertions`
- `bb spec` → `214 examples, 0 failures, 388 assertions`

Acceptance-targeted checks remain green:
- `bb features features/cli/service.feature features/cli/service_linux.feature` → `31 examples, 0 failures, 122 assertions`
- `bb spec spec/isaac/service` → `97 examples, 0 failures, 178 assertions`

Ready to re-verify.
