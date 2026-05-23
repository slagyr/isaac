---
# isaac-3but
title: Unknown-crew message is CLI-specific (suggests --crew on every channel)
status: in-progress
type: bug
priority: normal
created_at: 2026-05-21T19:49:27Z
updated_at: 2026-05-23T17:18:45Z
---

## Gap

`bridge/core.clj` `unknown-session-crew-message` (around line 39-41)
unconditionally appends `"pass --crew to override"` to its error text.
That suggestion is correct for the CLI (`isaac prompt` / `isaac chat`)
but nonsensical for non-CLI channels — ACP, Discord, iMessage, HTTP —
where the user has no `--crew` flag to pass.

The message fires from `route-charge!` when a charge has
`:charge/reason :unknown-crew`, regardless of which comm originated
the charge.

## Origin

Surfaced during the verifier review of `isaac-a9y0` (charge refactor).
Predates that refactor; the CLI-specific wording was carried forward
unchanged. Verifier flagged it against `isaac-a9y0` but the issue is
out of scope for that bean — captured here instead.

## Proposed change

Make the unknown-crew message channel-aware. Two reasonable shapes:

1. **Charge carries `:origin :kind`** (already does, per `charge/build`'s
   `:origin` arg). Branch on it: `:cli` → keep current text;
   `:acp`/`:discord`/`:imessage`/`:http` → suggest the appropriate
   remediation for that channel (e.g., "set the crew in the comm
   config" or "/crew <name>").
2. **Comm-supplied remediation text.** The comm registers a "how to
   change crew" string when it builds the charge; bridge uses it
   verbatim.

Option 1 is simpler and centralizes the policy in the bridge. Option 2
is cleaner separation but spreads the text. Implementer to choose.

## Surface

- `src/isaac/bridge/core.clj` — `unknown-session-crew-message` and its
  call site in `route-charge!`.
- Feature scenario(s) under `features/bridge/` exercising the message
  for at least two channels (CLI and one non-CLI). Existing CLI
  unknown-crew coverage should be preserved.

## Acceptance

- For a CLI-originated charge with an unknown crew, the message
  includes the `--crew` suggestion (unchanged from today).
- For a non-CLI charge (ACP / Discord / iMessage / HTTP) with an
  unknown crew, the message does NOT suggest `--crew` and instead
  offers a remediation appropriate to that channel.
- Feature scenarios cover at least two distinct channel kinds.
