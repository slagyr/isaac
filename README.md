<img align="left" width="200" src="https://raw.githubusercontent.com/slagyr/isaac/main/isaac.png" alt="isaac" style="margin-right: 20px; margin-bottom: 10px;">

### Isaac

AI assistant foundation.

<br clear="left">

Isaac is a Clojure-based agent platform for crew-configured assistants with
tools, sessions, config-driven behavior, and multiple interaction surfaces. You
install a small **foundation** seed, declare **modules** in config, and grow a
runtime that fits your setup.

**This repository is the front page of the Isaac suite** — the logo, the index,
planning notes, and the full git history of the original monolith. Production
code, specs, and features now live in focused repos below; nothing in the suite
depends on a monolithic checkout here at runtime or in CI.

## Repositories

| Repository | Role | Build |
|---|---|---|
| [isaac-foundation](https://github.com/slagyr/isaac-foundation) | CLI dispatcher, module loader, config/schema machinery, nexus, scheduler | [![CI](https://github.com/slagyr/isaac-foundation/actions/workflows/ci-tests.yml/badge.svg)](https://github.com/slagyr/isaac-foundation/actions/workflows/ci-tests.yml) |
| [isaac-agent](https://github.com/slagyr/isaac-agent) | Crew, LLM providers, sessions, bridge, tools, comm delivery | [![CI](https://github.com/slagyr/isaac-agent/actions/workflows/ci-tests.yml/badge.svg)](https://github.com/slagyr/isaac-agent/actions/workflows/ci-tests.yml) |
| [isaac-server](https://github.com/slagyr/isaac-server) | HTTP host, boot orchestration, reconciler, `isaac server` | [![CI](https://github.com/slagyr/isaac-server/actions/workflows/ci-tests.yml/badge.svg)](https://github.com/slagyr/isaac-server/actions/workflows/ci-tests.yml) |
| [isaac-acp](https://github.com/slagyr/isaac-acp) | ACP stdio agent, `isaac chat`, `/acp` WebSocket transport | [![CI](https://github.com/slagyr/isaac-acp/actions/workflows/ci-tests.yml/badge.svg)](https://github.com/slagyr/isaac-acp/actions/workflows/ci-tests.yml) |
| [isaac-cron](https://github.com/slagyr/isaac-cron) | Scheduled prompt jobs | [![CI](https://github.com/slagyr/isaac-cron/actions/workflows/ci-tests.yml/badge.svg)](https://github.com/slagyr/isaac-cron/actions/workflows/ci-tests.yml) |
| [isaac-hail](https://github.com/slagyr/isaac-hail) | Out-of-band interrupt delivery | [![CI](https://github.com/slagyr/isaac-hail/actions/workflows/ci-tests.yml/badge.svg)](https://github.com/slagyr/isaac-hail/actions/workflows/ci-tests.yml) |
| [isaac-hooks](https://github.com/slagyr/isaac-hooks) | Webhook ingress | [![CI](https://github.com/slagyr/isaac-hooks/actions/workflows/ci-tests.yml/badge.svg)](https://github.com/slagyr/isaac-hooks/actions/workflows/ci-tests.yml) |
| [isaac-discord](https://github.com/slagyr/isaac-discord) | Discord comm | [![CI](https://github.com/slagyr/isaac-discord/actions/workflows/ci-tests.yml/badge.svg)](https://github.com/slagyr/isaac-discord/actions/workflows/ci-tests.yml) |
| [isaac-imessage](https://github.com/slagyr/isaac-imessage) | iMessage comm | [![CI](https://github.com/slagyr/isaac-imessage/actions/workflows/ci-tests.yml/badge.svg)](https://github.com/slagyr/isaac-imessage/actions/workflows/ci-tests.yml) |

New capability is added by publishing a module with an `isaac-manifest.edn` and
declaring it under `:modules` in your Isaac config.

## Quick start

Requirements: Java 21+ and [Babashka](https://babashka.org/).

1. Clone and build from [isaac-foundation](https://github.com/slagyr/isaac-foundation).
2. Run `isaac init` to scaffold `~/.isaac/config/isaac.edn`.
3. Add modules (at minimum **isaac-agent** for crew/sessions and **isaac-server**
   for the HTTP host):

```clojure
{:modules {:isaac.agent {:git/url "https://github.com/slagyr/isaac-agent.git"
                         :git/sha "<pin>"}
           :isaac.server {:git/url "https://github.com/slagyr/isaac-server.git"
                          :git/sha "<pin>"}}}
```

4. Configure crew, providers, and comms in `~/.isaac/config/`, then run
   `isaac server`, `isaac chat`, or `isaac prompt "..."` as your modules
   expose.

Each module README documents its own `bb ci` workflow and acceptance features.

## What you get

- crew-based agent configuration with soul companions
- persistent sessions and JSONL transcripts
- built-in and module-contributed tools
- terminal chat, one-shot prompts, and ACP agent mode
- HTTP server mode with hot config reload
- structured EDN logs and a colorized viewer

## In this repo

- [`ISAAC.md`](ISAAC.md) — vocabulary, architecture notes, contributor traps
- [`AGENTS.md`](AGENTS.md) — agent/worker workflow for people and coding agents
- [`.beans/`](.beans/) — planning and handoff artifacts
- [`isaac.png`](isaac.png) — suite logo (linked from module READMEs)

Monolith source under `src/`, `spec/`, and `features/` was retired in June 2026
after the split above was verified homed. Git history here is the archive; follow
the module repos for current code.

## License

MIT — Copyright (c) 2026 Micah Martin. See [`LICENSE`](LICENSE).