---
# isaac-524u
title: Custom human-readable EDN pretty-printer (replace clojure.pprint)
status: draft
type: feature
priority: normal
created_at: 2026-07-13T16:03:13Z
updated_at: 2026-07-13T16:27:42Z
---

## Goal

A custom human-readable EDN pretty-printer for Isaac's CLI output, replacing clojure.pprint (Micah dislikes its formatting). Used wherever Isaac prints EDN to a human (config get default view, sources, bean/hail records, etc.).

## Motivation

clojure.pprint/pprint wraps and aligns in ways Micah finds hard to read. Isaac already has opinions about human output (tables in grof, color). EDN output deserves the same care.

## Desired formatting (Micah, 2026-07-13)

The unifying rule Micah's preferences reduce to: **width decides inline vs multi-line.** A collection renders inline when it fits the line budget; otherwise it breaks. Everything below follows from that plus alignment.

- **Values start on the SAME LINE as their key** (map key-value on one line; multi-line only when the VALUE itself is a large nested structure).
- **Nested structures are indented** under their key.
- **Values within a map are vertically aligned** to the column of the largest key (zprint `:map {:justify? true}`).
- **Wrapping continues at the value's indentation column**, not back at the key.
- **Lists/vectors are single-line unless they exceed width**, then wrap (aligned).
- **Maps: inline if they fit, else multi-line+indented** — the width rule, not a per-map flag.
- **Strings should wrap too** — but this is the hard/unusual part (pretty-printers treat strings as atomic; wrapping string CONTENT with continuation is non-standard). Micah notes he can't even express it in a .edn source file without reader warnings. Treat as a STRETCH goal: get the collection formatting right first; string-content wrapping is a separate, optional layer (likely only for known long-text fields, not arbitrary strings).

Micah started before.edn/after.edn examples but found hand-authoring the target tedious and his preferences width/context-dependent — which confirms this should be a WIDTH-DRIVEN formatter, not a fixed template.

## Settled decisions (Micah, 2026-07-13)

- **No commas** between map entries.
- **Sort map keys** (deterministic output).
- **Block style for broken collections** (NOT align-under-brace — that cascades indentation rightward on deep nesting): opening `{`/`[` ends the first line, each entry on its own line indented **2 spaces per nesting level**, closing brace alone on the last line. A nested collection's opening brace stays on its key's line (so 'value on same line as key' holds for structured values too).
- **Values still vertically aligned** to the largest key within a map (justify), on the same line as the key.
- **Inline when it fits** the width budget (small maps/lists stay single-line, no braces-on-own-line); block only when it exceeds width.
- **Width budget** (settled): `width = clamp(terminal-columns, 40, 80)`. 80 is a HARD MAX (never wrap wider even on a 200-col terminal — readability cap). 40 is the floor (narrower terminals clamp up; below 40 alignment is pointless). Track the terminal between. Non-TTY (piped/redirected) uses 80.

### Example 1 (approved shape — block style)

input:  `{:api "responses" :base-url "https://api.x.ai/v1" :auth "api-key" :api-key "${XAI_API_KEY}"}`
output (width 80):
```
{
  :api      "responses"
  :api-key  "${XAI_API_KEY}"
  :auth     "api-key"
  :base-url "https://api.x.ai/v1"
}
```

(NOTE: this block style may or may not be reachable via zprint config — the zprint evaluation must confirm zprint can do brace-on-own-line 2-space block with justify; if not, hand-roll. This is now a firmer gate on adopting zprint.)

## Evaluate zprint FIRST (likely already does this)

zprint is a mature, highly-configurable Clojure/EDN formatter whose options map almost 1:1 onto the above: `:justify?` (value alignment), width-based hang/flow (inline-if-fits), same-line key-values, nested indentation, list wrapping. **First acceptance task: prototype an isaac output sample through zprint with a tuned config and see how close it gets** — if a config nails it, this bean becomes 'adopt zprint + ship the config' rather than a hand-rolled printer.

Two gates on adopting zprint:
1. **babashka compatibility** — isaac runs on bb (the CLI is bb-interpreted). zprint must load/run under bb acceptably (and not add to the 1.3s bb-load floor — see isaac-ogiu profile). If zprint is too heavy for bb, that argues for a trimmed custom printer.
2. **String wrapping** — zprint won't wrap string content; that stretch goal needs a custom layer regardless.

If zprint fits: adopt it as `isaac.util.edn/pretty` with the tuned config. If not: hand-roll a width-driven printer implementing the rules above.

## Design questions remaining for spec time

- Line-width budget (80? terminal width? configurable?).
- Sort map keys? (deterministic output aids diffing/agent parsing — recommend yes.)
- Where it applies: shared `isaac.util.edn/pretty` for all human EDN output; --edn flag uses it (or stays canonical). --json unaffected.
- Home: isaac-foundation (brew train).

## Non-goals

Changing --json output; changing on-disk EDN file format (only display).

## Status

Draft per Micah 2026-07-13; spec after he provides the desired-shape example. Candidate as the grok-4.5 shakedown bean for scrapper once promoted.
