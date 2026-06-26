---
# isaac-vhyw
title: 'Discord comm: comm_send target resolves by channel :name or numeric ID'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-26T15:38:46Z
updated_at: 2026-06-26T15:54:53Z
---

## Context
When using `comm_send` (or the delivery path) to send outbound messages over the Discord comm, the `target` (or `:discord/target`) must currently be the channel snowflake ID string. Configs often declare friendly names via the per-channel `:name` in `:discord/channels`. Being able to address by name (e.g. "announcements" or "isaac") is more convenient and less error-prone, especially for hail notifications, cron deliveries, and agent-driven sends.

This was surfaced during the `orc1` orchestration smoke test for hail bands (isaac-work etc.) that notify humans via Discord.

Related: isaac-2s0b (comm_send), isaac-97bf (send-schema migration to namespaced keys like :discord/target).

## The update
Enhance the Discord comm to resolve the provided target value:
- If it matches a key in the configured `:discord/channels` (ID), use as-is.
- Else if it matches a channels `:name`, resolve to that channels ID.
- Fall back to treating it as ID (existing behavior).

Apply in the outbound send path (`send!` and `try-send-or-enqueue!` etc.) so both comm_send and other writers benefit.

The change is small, local to discord, and preserves backward compat.

## Acceptance
- `comm_send` (and direct delivery records) can target a Discord channel using either its numeric ID or the `:name` declared in the channels map.
- Existing ID-based sends continue to work unchanged.
- A comm_send feature scenario exercises sending by name (add to isaac-agent/features/tool/comm_send.feature or discord-specific).
- Unit coverage in discord (e.g. spec for resolve helper or send with name).
- Update discord README example or docs to show sending to a named channel.
- No breakage to inbound routing, session names, or other comms.
- The `orc1` (or similar) process test can exercise a name-based send via hail if desired.
- `bb spec` and relevant `bb features` green.

## Implementation notes (for worker)
- Add a small pure helper (e.g. `resolve-target-channel` or inline in lookup) that takes the discord-cfg (channels map) and target string.
- Update the `send!` impl (and any enqueue paths that resolve channel) to use the resolved ID before calling rest/post-message!.
- Ensure it works for both bare `:target` (pre-migration state) and namespaced `:discord/target`.
- Consider caching or just linear scan (small map).
- Add to send-schema description if helpful.

## Notes
This makes the per-channel `:name` (already used for labels in prompts) also usable for addressing.
Gaps found during orc1 test: hail bands were using non-existent name "Isaac" instead of ID; this change makes name usage viable once the band is updated to a real name or ID.

## Handoff
Worker can implement the resolve + tests. Planner can update band examples/docs later if needed. Verify via the comm_send feature and a Discord round-trip if possible.
