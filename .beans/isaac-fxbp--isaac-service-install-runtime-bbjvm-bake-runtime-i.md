---
# isaac-fxbp
title: isaac service install --runtime bb|jvm — bake runtime into the launchd plist
status: draft
type: feature
created_at: 2026-06-21T01:09:18Z
updated_at: 2026-06-21T01:09:18Z
parent: isaac-5zfv
blocked_by:
    - isaac-hzi0
---

Add `--runtime bb|jvm` (default `bb`) to `isaac service install` (`isaac.service.cli`
/ macos.clj). The service layer just records the choice; all runtime logic lives in
`isaac server` (isaac-hzi0). Part of epic isaac-5zfv; blocked by hzi0.

## Behavior
- `service install --runtime <X>` writes the launchd plist ProgramArguments as
  `<isaac> --root <root> server --runtime <X>`. So the plist entry point stays the
  bb `isaac` binary, which bounces to clojure when X=jvm.
- Default `bb` -> `... server` (or `... server --runtime bb`), today's behavior.
- `service status` reports the installed runtime.
- (Ties to sme9: the plist must find both `bb` AND, for jvm, `clojure` on PATH.)

## Scenarios (DRAFT — pending Micah review; do not generate feature file yet)
```gherkin
Scenario: install --runtime jvm bakes the flag into the plist
  When isaac is run with "service install --runtime jvm"
  Then the written launchd plist ProgramArguments contains, in order,
       "server" then "--runtime" then "jvm"
  And  the exit code is 0

Scenario: default install uses the bb runtime
  When isaac is run with "service install"
  Then the written launchd plist ProgramArguments runs "server"
       without "--runtime jvm"

Scenario: service status reports the installed runtime
  Given the service was installed with "--runtime jvm"
  When isaac is run with "service status"
  Then the stdout indicates the runtime is "jvm"
```

## Acceptance
- Installed plist carries the chosen runtime; default unchanged (bb).
- `service status` surfaces the runtime.
- A jvm-installed service actually starts on the JVM end-to-end (plist -> isaac
  server --runtime jvm -> clojure). @slow / manual verification on a host with
  clojure present.
