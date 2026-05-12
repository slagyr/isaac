---
# isaac-ua1n
title: "isaac init: scaffold a default config for fresh installs"
status: completed
type: feature
priority: low
created_at: 2026-04-22T18:09:55Z
updated_at: 2026-04-23T02:41:43Z
---

## Description

Adds an 'isaac init' command that scaffolds a minimal-but-functional config for a new install. Pairs with updating the 'no config found' error to point users at init.

Scaffolded files (at ~/.isaac/config/ by default; --home or home-pointer respected):

isaac.edn:
  {:defaults {:crew :main :model :default}
   :tz "America/Chicago"
   :prefer-entity-files? true
   :cron {:heartbeat {:expr "0 0 * * *"
                      :crew :main
                      :prompt "Daily heartbeat. Anything worth noting?"}}}

crew/main.edn:
  {:model :default}

crew/main.md:
  You are Isaac, a helpful AI assistant.

models/default.edn:
  {:model "llama3.2" :provider :ollama}

providers/ollama.edn:
  {:base-url "http://localhost:11434" :api "ollama"}

Behavior:
- Refuses if isaac.edn already exists. Clear error: 'config already exists at X; edit it directly, or rm -rf and re-run init.'
- On success, prints short Ollama setup instructions (brew install ollama; ollama serve; ollama pull llama3.2; then isaac 'hello').
- Non-interactive. Flag-driven if any variants are added later (--provider anthropic, etc.); v1 is ollama-only.
- Updates the existing 'no config found' error to point at 'isaac init'.

Depends on isaac-xdlg (main cron bead) — the scaffold references :cron with a heartbeat.

## Acceptance Criteria

1. Implement 'isaac init' per the design above.
2. Refuse on existing isaac.edn with 'config already exists at <path>; edit it directly.' exit 1.
3. Print exact scaffold output per features/cli/init.feature scenario 1.
4. Scaffold 7 files per scenario 2.
5. Update the 'no config found' error to suggest 'isaac init'.
6. Add init to command help listing.
7. Add the 3 step-defs listed above.
8. Remove @wip from all 4 scenarios in features/cli/init.feature.
9. bb features features/cli/init.feature passes (4 examples).
10. bb features passes overall.
11. bb spec passes.

## Design

Implementation notes:
- Register 'init' in isaac.cli.registry (likely new isaac.cli.init namespace).
- Resolution: init uses the resolved home (respects --home flag and home-pointer from isaac-skul).
- Refuse if <home>/config/isaac.edn exists. Stderr format: 'config already exists at <path>; edit it directly.' Exit 1. Do NOT suggest removing/re-running — keep message terse.
- Scaffold writes (all under <home>/config/):
  - isaac.edn: {:defaults {:crew :main :model :llama} :tz "America/Chicago" :prefer-entity-files? true}
  - crew/main.edn: {:model :llama}
  - crew/main.md: 'You are Isaac, a helpful AI assistant.'
  - models/llama.edn: {:model "llama3.2" :provider :ollama}
  - providers/ollama.edn: {:base-url "http://localhost:11434" :api :ollama}
  - cron/heartbeat.edn: {:expr "*/30 * * * *" :crew :main}
  - cron/heartbeat.md: 'Heartbeat. Anything worth noting?'

- Exact stdout format (lines + blank separators as written in features/cli/init.feature):
    Isaac initialized at <home>.

    Created:
      config/isaac.edn
      config/crew/main.edn
      config/crew/main.md
      config/models/llama.edn
      config/providers/ollama.edn
      config/cron/heartbeat.edn
      config/cron/heartbeat.md

    Isaac uses Ollama locally. If you don't have it:

      brew install ollama
      ollama serve &
      ollama pull llama3.2

    Then try:

      isaac prompt -m "hello"

- Update the existing 'no config found' error to suggest 'isaac init'.
- 'isaac help' should list init as a subcommand.

Two new step-defs:
- 'an empty isaac home at "<path>"' — creates empty dir at the resolved path; sets :state-dir in gherclj state.
- 'the state file "<relpath>" exists' — positive existence check, state-relative path.
- 'the state file "<relpath>" contains:' — multi-line content match via heredoc (used by content-check scenario for .md files).

Depends on isaac-xdlg (main cron bead) AND isaac-4qgv (cron entity-file loading) — the scaffold creates cron/heartbeat.edn + .md which require the loader to read from cron/ dir as entity files.

## Notes

Completed the init scaffold implementation to the approved feature contract: exact output, refusal behavior, cron scaffold files, top-level help listing, and feature step support for empty homes and state-file assertions. Verified with bb spec spec/isaac/cli/init_spec.clj, bb features features/cli/init.feature, and bb spec. Full bb features still has the unrelated failure tracked in isaac-m2vc.

