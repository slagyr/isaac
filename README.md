# Isaac

Isaac is a Clojure-based agent system for running crew-configured assistants
with tools, sessions, config-driven behavior, and multiple interaction
surfaces.

At a high level, Isaac gives you:
- crew-based agent configuration
- persistent sessions and transcripts
- built-in tools for file, shell, web, and memory operations
- terminal chat and one-shot prompt flows
- ACP agent mode over stdio
- an HTTP server mode
- a structured EDN log stream with a colorized viewer

## Requirements

- Java 21+
- [Babashka](https://babashka.org/)

## Quick Start

Run Isaac directly through Babashka:

```bash
bb run isaac help
```

Initialize a default config in `~/.isaac`:

```bash
bb run isaac init
```

List configured crew members:

```bash
bb run isaac crew
```

Launch the chat UI:

```bash
bb run isaac chat
```

Run a single prompt and exit:

```bash
bb run isaac prompt "Summarize the current directory"
```

Validate config:

```bash
bb run isaac config validate
```

Tail Isaac logs:

```bash
bb run isaac logs
```

Start the HTTP server:

```bash
bb run isaac server
```

## Command Surface

Top-level commands currently include:

- `acp` - run Isaac as an ACP agent over stdio
- `auth` - manage authentication credentials
- `chat` - launch the Toad chat UI
- `config` - inspect and validate configuration
- `crew` - list configured crew members
- `init` - scaffold a default Isaac config
- `logs` - tail and colorize the Isaac log file
- `prompt` - run a single prompt turn and exit
- `server` - start the Isaac HTTP server
- `sessions` - list stored conversation sessions

See the live command help for the exact current interface:

```bash
bb run isaac help
bb run isaac help <command>
```

## Configuration

Isaac stores runtime state under `~/.isaac` by default.

- Config lives under `~/.isaac/config/`
- Crew definitions live under `~/.isaac/config/crew/`
- Session and runtime state live under `~/.isaac/`

You can override the home directory at runtime:

```bash
bb run isaac --home /path/to/home chat
```

## Development

Run the unit spec suite:

```bash
bb spec
```

Run the feature suite:

```bash
bb features
```

Run both:

```bash
bb ci
```

Lint the codebase:

```bash
bb lint
```

Install repo-tracked git hooks in a fresh checkout:

```bash
bb hooks:install
```

The `pre-commit` hook scans staged content for secrets using
[gitleaks](https://github.com/gitleaks/gitleaks). Install it once per
machine:

```bash
brew install gitleaks
```

If gitleaks isn't on `PATH` the hook skips with a warning rather than
blocking commits.

## Project Notes

Isaac is heavily config-driven. The repo also contains in-tree modules under
`modules/` and uses manifest-based extension loading for APIs, tools, comms,
slash commands, and providers.

For deeper project-specific context, see:

- `AGENTS.md` - contributor/agent workflow guidance
- `ISAAC.md` - project vocabulary, architecture notes, and traps

## License

MIT

Copyright (c) 2026 Micah Martin

See [`LICENSE`](LICENSE).
