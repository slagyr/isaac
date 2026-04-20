# Architecture Principles for Isaac

Principles that shape how code is organized in this project. Grows over
time as patterns emerge from the code.

## Screaming Architecture

**The directory layout should reveal what Isaac does, not what
frameworks it uses.** Uncle Bob's principle (2011): a good architecture
"screams" its domain. Your top-level structure should convey "this is a
healthcare system" or "this is an inventory system," not "this is a
Rails app" or "this is a Clojure CLI."

For Isaac, the screaming-architecture question is: when a new
contributor opens `src/isaac/`, do they see *what the system does* or
*how it's plumbed*?

### The lesson

**Frameworks are implementation details.** CLI, ACP, HTTP — these are
ways Isaac is delivered to a user. The domain (chat turns, sessions,
crew, tools, config) is *what* Isaac does. Top-level structure should
privilege domain.

You can never eliminate the framework. But you can keep pushing it
toward the edges, where it belongs, and extracting the business logic
into pure, reusable components that don't know whether they're called
from a CLI, an HTTP handler, or an ACP proxy.

### Concrete example — the config CLI

Before the move:

```
src/isaac/
  cli/config.clj      ← CLI command for config
  config/loader.clj   ← config loader
  config/schema.clj   ← config schema
```

The config command was a *cli* concern with a *config* payload.
Problems: code about config was split across two branches of the tree;
top level had "cli" as a high-signal entry competing with real domain
names.

After the move:

```
src/isaac/
  config/
    loader.clj
    schema.clj
    cli/command.clj   ← same file, now inside the config domain
```

Config now owns all its pieces. `isaac.cli` shrinks by one entry. The
top-level structure leans closer to "this is a system with
domain-centric config, sessions, turns, tools, etc."

### What still doesn't scream

`isaac.config.cli.command` still has "cli" in its path. The framework
is visible — just one level deeper. A truer screaming-architecture move
would expose config's capabilities as a façade (see below) and let the
CLI adapter be a thin translator that isn't architecturally prominent.

This is the pattern: each move pushes the framework a little further
from the center. Perfection isn't the goal; direction is.

## Seams — domain-as-interface

Directories shouldn't just group files. Each top-level domain should
expose a stable **public surface** — a set of functions that describe
what the domain can do — while keeping implementation private and
swappable.

Ports & Adapters (Hexagonal Architecture) at the directory level:

- `isaac.session` is the port (public): `create`, `append`, `load`,
  etc.
- `isaac.session.storage` is the adapter (private): file-based today,
  could be SQL or Dolt tomorrow.
- Callers say `(session/append ...)` — not `(storage/append ...)`.

### Why this matters

1. **Decoupling.** Callers don't know how sessions are stored. Change
   storage without touching any caller.
2. **Testability.** Swap a stub adapter for tests without redefining
   every seam.
3. **Deferrable decisions.** The "which database?" question doesn't
   have to be answered on day one, because the domain's shape doesn't
   depend on it.
4. **Discoverability.** Newcomers find the public API in one place,
   not by grepping consumers.

### Isaac today

Most domains don't have façades yet. Callers reach directly into
`isaac.session.storage`, `isaac.context.manager`, `isaac.tool.registry`.
This couples every caller to the current implementation. Extracting
façades is incremental work — pick one domain at a time, let the
system tell you when the next seam is worth making explicit.

## How this doc grows

Add a principle when the codebase earns it. Every principle comes with:

- The idea, stated once in plain language
- Why it matters (tradeoff the principle resolves)
- At least one concrete example from Isaac
- What it looks like when you've moved closer, even if not perfect

Don't add aspirational principles the code doesn't yet demonstrate.
Keep this file honest to what's actually in the tree.
