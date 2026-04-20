# Architecture Principles

Principles that shape how code is organized. Grows over time as
patterns emerge from the code. Examples use a fictional spaceship
system — substitute your own domain as you go.

## Screaming Architecture

**The directory layout should reveal what the system does, not what
frameworks it uses.** Uncle Bob's principle (2011): a good architecture
"screams" its domain. Your top-level structure should convey "this is
a healthcare system" or "this is an inventory system" — not "this is a
Rails app" or "this is a Clojure CLI."

Imagine opening a spaceship codebase. Which layout tells you more?

```
ship/                              ship/
  bridge/         ← "the UI"         life-support/     ← "breathing"
  network/        ← "the pipe"       propulsion/       ← "going places"
  persistence/    ← "the DB"         navigation/       ← "where are we"
  controllers/    ← "wiring"         weapons/          ← "pew pew"
  models/         ← "data shapes"    cargo/            ← "what we carry"
                                     crew/             ← "who's aboard"
```

Left side: "I could be any app. What do I do?" Right side: "Oh, this
is a ship."

### The lesson

**Frameworks are implementation details.** A bridge console, a
subspace radio, a tactical display — these are ways the ship is
delivered to its operator. The domain (life support, propulsion,
navigation) is *what* the ship does. Top-level structure should
privilege domain.

You can never eliminate the framework. But you can keep pushing it
toward the edges, where it belongs, and extracting the business logic
into pure, reusable components that don't know whether they're invoked
from a bridge button, a voice command, or a scripted autopilot.

### Concrete example

Suppose `set-oxygen-level` has a bridge-console UI. A framework-first
layout looks like:

```
ship/
  bridge/life-support.clj     ← bridge command for life-support
  life-support/controller.clj ← life-support logic
  life-support/scrubber.clj   ← O2 scrubber
```

The bridge command is a *bridge* concern with a *life-support*
payload. Problems: life-support code is split across two branches; top
level has "bridge" as a high-signal entry competing with real domain
names.

A domain-first layout:

```
ship/
  life-support/
    controller.clj
    scrubber.clj
    bridge/command.clj        ← same file, now inside the domain
```

Life-support owns all its pieces. Top-level "bridge" shrinks by one
entry. The structure leans closer to "this is a ship of these
domains."

### What still doesn't scream

`ship.life-support.bridge.command` still has "bridge" in its path. The
framework is visible — just one level deeper. A truer move exposes
life-support as a façade (see below) and lets the bridge adapter be a
thin translator that isn't architecturally prominent.

This is the pattern: each move pushes the framework a little further
from the center. Perfection isn't the goal; direction is.

## Seams — domain-as-interface

Directories shouldn't just group files. Each top-level domain should
expose a stable **public surface** — a set of functions that describe
what the domain can do — while keeping implementation private and
swappable.

Ports & Adapters (Hexagonal Architecture) at the directory level:

- `ship.life-support` is the port (public): `set-oxygen`,
  `set-pressure`, `status`.
- `ship.life-support.scrubber` is the adapter (private): chemical
  scrubber today, bio-recycler tomorrow.
- Callers say `(life-support/set-oxygen 21)` — not
  `(scrubber/set-o2 0.21)`.

### Why this matters

1. **Decoupling.** Callers don't know whether the scrubber is chemical
   or biological. Swap the adapter without touching any caller.
2. **Testability.** Swap a stub adapter for tests without redefining
   every seam.
3. **Deferrable decisions.** The "which storage?" or "which
   transport?" question doesn't have to be answered on day one,
   because the domain's shape doesn't depend on it.
4. **Discoverability.** Newcomers find the public API in one place,
   not by grepping consumers.

### Starting state is rarely pure

Most projects begin with direct coupling — callers reach into
implementation namespaces (`ship.life-support.scrubber`) because
that's the easiest thing that works. Extracting façades is incremental
work: pick one domain at a time, let the system tell you when the next
seam is worth making explicit. You don't earn it all on day one, and
you don't have to.

## How this doc grows

Add a principle when the codebase earns it. Every principle comes
with:

- The idea, stated once in plain language
- Why it matters (the tradeoff the principle resolves)
- At least one concrete example from the code
- What it looks like when you've moved closer, even if not perfect

Don't add aspirational principles the code doesn't yet demonstrate.
Keep this file honest to what's actually in the tree.
