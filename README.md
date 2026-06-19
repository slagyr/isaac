# 🍏 Isaac - AI Assistant, destined for the stars 🚀⭐

<img align="left" width="200" src="https://raw.githubusercontent.com/slagyr/isaac/main/isaac.png" alt="isaac" style="margin-right: 20px; margin-bottom: 10px;">

Isaac is a modular AI assistant foundation with a spaceship theme. Crews operate with persistent souls and append-only transcripts of their journeys, guided by declarative configuration rather than brittle code. Its berth system lets you extend tools, interfaces, and behaviors cleanly across surfaces while maintaining strict boundaries and verifiable history. Built for the long voyage, not the next hype cycle.

<br>

[![Foundation](https://github.com/slagyr/isaac-foundation/actions/workflows/ci-tests.yml/badge.svg)](https://github.com/slagyr/isaac-foundation/actions/workflows/ci-tests.yml) 
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![GitHub last commit](https://img.shields.io/github/last-commit/slagyr/isaac)
[![Clojure](https://img.shields.io/badge/Clojure-1.11%2B-blue?logo=clojure)](https://clojure.org)
[![Babashka](https://img.shields.io/badge/Babashka-1.3%2B-red?logo=clojure)](https://babashka.org)
[![Java](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk)](https://openjdk.org/)

<br clear="left">

## Module Repositories

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

## License

MIT — Copyright (c) 2026 Micah Martin. See [`LICENSE`](LICENSE).
