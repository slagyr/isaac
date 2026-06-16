# isaac — RETIRED (2026-06-16)

The isaac monolith has been retired. Its production code, Speclj unit specs, and Gherkin
feature tests were extracted into focused module repositories and **verified homed** before
removal — no module depends on this repository at runtime *or* in tests.

## Where the code lives now

| was here | now |
|---|---|
| CLI, module loader, config machinery, nexus, schema | **isaac-foundation** (the seed) |
| crew, LLM providers, sessions, drive/bridge, tools, comms | **isaac-agent** |
| HTTP server, service (launchd), reconciler | **isaac-server** |
| ACP agent | **isaac-acp** |
| cron / hail / hooks | **isaac-cron**, **isaac-hail**, **isaac-hooks** |
| Discord / iMessage comms | **isaac-discord**, **isaac-imessage** |

New functionality is bolted onto **isaac-foundation** through modules.

## Retirement record

- `src/`, `spec/`, `features/`, and the in-tree fixture `modules/` were removed in this commit.
- All of it was verified to have a home in the module repos (src 100%; features accounted —
  redundant `@wip` provider features scrapped, `component_hot_reload` relocated to cron/hooks;
  spec all homed except the monolith-only combined-suite harness, which has no meaning outside
  this repo).
- CI is frozen (`.github/workflows/ci-tests.yml` → manual dispatch only).
- Full history remains in git.
- Tracked in bean `isaac-e89r`.
