# Isaac Metaphor — Working Notes

**Status: Exploratory.** Nothing here is committed. This document
captures a naming discussion to guide future vocabulary choices.

## The Problem

Isaac has components that need names: the layer that triages user
input (slash commands vs prompts), the layer that executes an LLM
turn, and the system as a whole. Good names make the system easier
to reason about and talk about.

## Metaphors Considered

We explored several metaphors and their fit:

- **Office** — Executive, desk, engagement. Accurate but carries
  "work" associations. Nobody gets excited about going to the office.
- **Workshop** — Workbench, craftsman, tools. Warm and creative.
  Resonates with software craftsmanship values. Strong contender.
- **Smithy** — Anvil, forge, temper. Evocative and specific. A smith
  shapes raw material into something useful, which maps well to what
  Isaac does with prompts. Rich vocabulary.
- **Spaceship** — Bridge, helm, engine, comm, crew. Forward-looking,
  creative, room to grow. Connects to the Craftsman fiction series
  (Robert C. Martin's serialized stories in *Software Development*
  magazine — apprentices and journeymen learning to code on a starship).

## Current Leaning: Spaceship

The spaceship metaphor offers the most creative latitude and
forward-thinking vocabulary. It also connects to a meaningful lineage
in software craftsmanship storytelling.

### Candidate Mappings

These are starting points, not decisions:

| Concept | Candidate Term | Notes |
|---|---|---|
| The system | Ship | Isaac is the ship |
| Input triage (commands vs prompts) | Bridge | Where decisions are made |
| LLM turn execution | Helm | Takes the wheel for each exchange |
| LLM call itself | Engine | Raw energy that powers responses |
| Channel (CLI, ACP, web) | Comm | How signals arrive |
| Session | Mission | Ongoing engagement with history |
| Soul | Directive | Standing orders for behavior |
| Agent | Crew | Each with their own role and directive |
| Tools | Modules | Pluggable capabilities |
| Transcript | Ship's log | Record of what happened |
| Config | Ship's rules | How the ship operates |
| Slash commands | (unnamed) | Handled at the bridge |

### Guild Ranks (from the Craftsman series)

The traditional guild hierarchy may inform agent naming:

- **Apprentice** — learning agent, basic capabilities
- **Journeyman** — capable agent, works independently
- **Craftsman** — master agent, orchestrates others

### The Craftsman Connection

Robert C. Martin ("Uncle Bob") wrote a serialized fiction column
called *The Craftsman* for *Software Development* magazine. The
stories followed apprentices (Alphonse), journeymen (Jerry, Jasmine),
and others learning software practices aboard a starship. The
craftsman — the master — was never directly seen.

Articles archived at: http://butunclebob.com/ArticleS.UncleBob.CraftsmanColumn
and https://sites.google.com/site/unclebobconsultingllc/

## Open Questions

- How literally should the metaphor be applied? Naming every concept,
  or just the key architectural layers?
- Should the metaphor show up in namespace names (e.g. `isaac.bridge`,
  `isaac.helm`) or stay informal?
- Does the spaceship metaphor work for user-facing vocabulary too
  (CLI output, docs), or is it internal only?
- Workshop/smithy may better suit the craftsmanship ethos. Spaceship
  may better suit the forward-looking ambition. Can they coexist?
