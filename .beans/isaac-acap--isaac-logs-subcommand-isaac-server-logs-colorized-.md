---
# isaac-acap
title: "isaac logs subcommand + isaac server --logs: colorized log tailing"
status: todo
type: feature
priority: low
created_at: 2026-05-12T14:45:50Z
updated_at: 2026-05-12T14:53:39Z
---

## Description

Isaac's log file is human-unreadable as raw EDN. Each line is a map
that runs the full terminal width and offers no visual anchors for
time, level, or event. Triage is grep + squint.

This bead adds two viewers that tail the configured log file and
print colorized one-line entries to stdout. The log file format is
unchanged; the viewers are read-only.

## Surface

- isaac logs            — standalone tail+colorize, like 'tail -f'
                          on the configured log file.
- isaac server --logs   — run the server AND print the log tail
                          in parallel (same colorization).

Both consult the same configured path (defaults to /tmp/isaac.log,
overridable via config).

## Visual contract

Each entry renders as:

  HH:MM:SS.mmm  LEVEL  event/name  key=value key=value ...

Specifically:

1. Time column: condensed local time, "HH:MM:SS.mmm". Fixed width
   (12 chars). Dim styling.

2. Level column: fixed-width (5 chars; ERROR/WARN /INFO /DEBUG/TRACE).
   Bold, colored by severity:
     :error -> red
     :warn  -> yellow
     :info  -> cyan
     :debug -> dim gray
     :trace -> dim gray

3. Event: prominent. The event's namespace ("acp-proxy" in
   :acp-proxy/session-prompt) gets a stable color derived from a
   hash of the namespace string. All entries with the same event
   namespace share that color. Lets the eye group related entries.

4. Remaining key-value pairs:
     - keys     : dim
     - values   : prominent (full brightness)
     - typed coloring:
         strings   default
         numbers   green
         keywords  magenta
         booleans  yellow
         nil       red

5. :file and :line are dropped from the inline display. Available
   in the raw log file if a stack trace location is needed.

6. :sessionId values get a stable color hashed from the id.
   Quickly distinguishes sessions when interleaved.

7. Optional subtle alternating row background, controlled by a
   flag (off by default; users tend to either love or hate
   zebra striping).

## Flags

  --color always|never|auto    (default: auto = TTY-detected)
  --zebra                      enable alternating row background
  --since DURATION             skip entries older than (e.g. "5m")
                               [stretch goal]
  --level LEVEL                only show this level and above
                               [stretch goal]

## Out of scope (follow-ups)

The following are NOT in this bead — capture as separate beads
if/when desired:

- Truncate-at-terminal-width with "…" indicator.
- URL / filesystem path styling (italic/underline).
- Collapse repeated kv values across consecutive lines.
- Per-event bold-vs-normal weight tuning.
- Filtering by event-name regex.
- Pretty-print one specific entry on demand.

## Implementation outline

1. New ns src/isaac/log_viewer.clj with:
   - format-entry  (entry -> colored string)
   - format-time   (ts string -> "HH:MM:SS.mmm" local)
   - color-for-event-ns  (string -> ANSI sequence; stable hash)
   - color-for-session   (string -> ANSI sequence; stable hash)
   - color-for-value     (value -> ANSI sequence by type)
   - color-for-level     (keyword -> ANSI sequence)
   - tty? (auto color detection)

2. New ns src/isaac/cli/logs.clj wired into isaac.main's
   command dispatch (or wherever subcommands register).
   Reads the log file path from config; tails via line-by-line
   poll loop, formats each line, prints.

3. Modify src/isaac/server/cli.clj to accept --logs and, when
   present, spawn a background thread doing the same tail loop.
   Server runs in parallel; tail prints to stdout.

## Test strategy

- spec/isaac/log_viewer_spec.clj  -- unit tests:
    color-for-level   produces expected ANSI per :error/:warn/etc.
    color-for-value   produces expected ANSI per type
    format-time       formats UTC timestamp to local HH:MM:SS.mmm
    color-for-event-ns is deterministic (same ns -> same color)
    color-for-session  is deterministic
    format-entry      assembles the columns in the right order
                      with the right widths
    --no-color mode emits no ANSI codes

- features/cli/logs.feature -- structural contract:
    "isaac logs" tails the configured log file
    "isaac server --logs" prints colorized lines while the
      server runs
    --color=never emits ANSI-free output (regex assertion)
    Fields :file and :line do not appear in the printed line
    Time column is fixed-width
    Level column is fixed-width

- Manual: open a real log; visually verify readability and that
  session colors are stable across an entire session's entries.

## Acceptance Criteria

- src/isaac/log_viewer.clj exists with the format-entry pipeline.
- isaac logs subcommand tails the configured log file and prints
  colorized one-line entries to stdout.
- isaac server --logs runs the server and tails its own log file
  to stdout in parallel.
- Visual contract holds:
    time column 12 chars, local time, dim
    level column 5 chars, bold, colored per severity
    event namespace gets stable hash color
    keys dim, values bright, values typed-colored
    :file / :line dropped from inline view
    :sessionId values get stable hash color
    --zebra flag toggles alternating row background
    --color=never emits no ANSI
- spec/isaac/log_viewer_spec.clj covers the formatter unit cases.
- features/cli/logs.feature covers structural contract.
- bb spec green; bb features green.
- Manual readability check on a real log: time/level/event align
  vertically across rows; same session entries visually group via
  consistent session color; namespace grouping is visible.

## Notes

Feature spec: features/cli/logs.feature (@wip — 6 scenarios pinning structural contract: column ordering, level fixed-width, :file/:line dropped, raw passthrough for unparseable, follow mode, server --logs wiring). ANSI/color behavior, hash-color determinism, and TTY detection live in spec/isaac/log_viewer_spec.clj instead — they don't fit gherkin well.

New steps needed (none currently exist):
  - 'isaac is run in the background with {args:string}' (variant of 'isaac is run with' that does not block on exit)
  - 'the file {path:string} is appended with {content:string}'

Definition of done includes removing @wip from features/cli/logs.feature.

