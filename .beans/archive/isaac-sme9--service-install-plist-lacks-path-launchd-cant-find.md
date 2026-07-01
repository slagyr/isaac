---
# isaac-sme9
title: service install plist lacks PATH — launchd can't find bb (service won't start)
status: completed
type: bug
priority: high
tags:
    - in-progress
created_at: 2026-06-19T21:54:36Z
updated_at: 2026-06-23T00:03:14Z
---

Follow-up to isaac-r7z5. The original bug was real: launchd could not find `bb`
because the generated plist omitted PATH. A minimal fix has landed, but the real
operator intent is broader: `isaac service install` should capture the caller
shell's PATH by default so the service and crew execs inherit the same tool
surface (`bb`, `git`, `beans`, etc.), while still allowing an explicit override.

## Proven on zanebot (2026-06-19)

• `isaac service install` -> plist runs `/usr/local/bin/isaac --root ... server`.
• launchctl: no PID, exit 1. Not listening.
• Running the SAME command manually WITH `PATH=/usr/local/bin:$PATH` -> server
  boots, loads comms, listens on :6674 ("Isaac server running on 127.0.0.1:6674").
• Adding `EnvironmentVariables -> PATH = /usr/local/bin:/usr/bin:/bin` to the
  plist and reloading -> service comes up healthy (PID, exit 0, listening).
  (Workaround applied on zanebot.)

Note: `service install` even prints "Resolved bb: /usr/local/bin/bb" but does not
put it on the plist PATH.

## Intent

Finish `sme9` as "service install PATH should match the caller shell unless the
operator says otherwise", not merely "synthesize a minimal launchd PATH".

## Approved feature contract

Feature file: `isaac-server/features/cli/service.feature`

The existing PATH scenario should be changed, and new behavior added, as `@wip`
scenarios using Marigold-style fictional paths:

### 1. Replace the current minimal-PATH scenario

```gherkin
  @wip
  Scenario: install captures the caller PATH for packaged installs
    Given "isaac" resolves to "/opt/marigold/bin/isaac"
    And "bb" resolves to "/opt/marigold/bin/bb"
    And the current process PATH is "/opt/marigold/bin:/opt/starboard/bin:/usr/bin:/bin:/Users/cordelia/.longwave/bin"
    When isaac is run with "service install"
    Then the plist contains:
      | path                      | value                                                                    |
      | EnvironmentVariables.PATH | /opt/marigold/bin:/opt/starboard/bin:/usr/bin:/bin:/Users/cordelia/.longwave/bin |
    And the exit code is 0
```

### 2. Add dev-checkout parity

```gherkin
  @wip
  Scenario: install captures the caller PATH for dev-checkout installs
    Given "bb" resolves to "/opt/marigold/bin/bb"
    And the current process PATH is "/opt/marigold/bin:/opt/starboard/bin:/usr/bin:/bin:/Users/oscar/.signal-kit/bin"
    When isaac is run with "service install --isaac-dir /projects/marigold-bridge"
    Then the file "~/Library/LaunchAgents/com.slagyr.isaac.plist" exists
    And the plist contains:
      | path                      | value                                                                 |
      | ProgramArguments[0]       | /opt/marigold/bin/bb                                                  |
      | ProgramArguments[4]       | isaac.main                                                            |
      | ProgramArguments[5]       | server                                                                |
      | EnvironmentVariables.PATH | /opt/marigold/bin:/opt/starboard/bin:/usr/bin:/bin:/Users/oscar/.signal-kit/bin |
    And the exit code is 0
```

### 3. Add explicit override behavior

```gherkin
  @wip
  Scenario: --path overrides the caller PATH
    Given "isaac" resolves to "/opt/marigold/bin/isaac"
    And "bb" resolves to "/opt/marigold/bin/bb"
    And the current process PATH is "/opt/marigold/bin:/usr/bin:/bin"
    When isaac is run with "service install --path /opt/quartz/bin:/usr/bin:/bin"
    Then the plist contains:
      | path                      | value                         |
      | EnvironmentVariables.PATH | /opt/quartz/bin:/usr/bin:/bin |
    And the exit code is 0
```

## Step reuse / new step

Reused:
- `{cmd:string} resolves to {path:string}`
- `isaac is run with {args:string}`
- `the file {path:string} exists`
- `the plist contains:`
- `the exit code is {int}`

New:
- `Given the current process PATH is {path:string}`

## Spec-only coverage

Keep the edge-case fallback in unit specs, not Gherkin:

- when the current PATH is missing or blank, `service install` falls back to the
  synthesized launchd PATH (`launchd-path`)
- `service install --help` documents `--path`

## Acceptance

- Default `service install` bakes the caller PATH into
  `EnvironmentVariables.PATH`
- `--path` overrides the caller PATH exactly
- Dev and packaged install paths behave the same
- Missing/blank PATH still falls back safely in unit specs
- A fresh `isaac service install` -> `service start` still brings the server up
  under launchd with no manual plist edit

## Relationships
• Follow-up to isaac-r7z5 (same `service install`, isaac-server). Completes the
  monolith->packaged service cutover (done on zanebot via the PATH workaround).

## Worker commands

After committing the `@wip` feature update:

- `clojure -M:test:features features/cli/service.feature`
- `bb spec spec/isaac/service/cli_spec.clj spec/isaac/service/macos_spec.clj`
- `bb lint src/isaac/service/cli.clj src/isaac/service/macos.clj spec/isaac/service/service_steps.clj`

Definition of done:

- approved `@wip` scenarios are committed, then removed once passing
- `bb spec` and `bb features` are green in `isaac-server`
- bean is handed off `status=in-progress` with `tag=unverified`
