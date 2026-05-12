---
# isaac-tw3m
title: "Discord on-config-change!: apply allow-from updates without bouncing the gateway"
status: completed
type: bug
priority: normal
created_at: 2026-05-01T00:15:53Z
updated_at: 2026-05-05T23:18:38Z
---

## Description

## Spec

features/comm/discord/lifecycle.feature scenario \"allow-from updates take effect without restart\" (currently @wip).

## What it's asserting

When the only thing that changes in :comms.discord is :allow-from
(or :crew, or future channel-routing), the gateway should NOT
reconnect. The next inbound MESSAGE_CREATE should be filtered
against the *new* allow-from. No :discord.client/started or
:discord.client/stopped events should fire.

Token transitions still trigger lifecycle:
- nil -> token   : start
- token -> nil   : stop
- token -> token (same): no-op
- token -> different token: bounce (stop + start)

Future: intents change (when configurable) also bounces.

## Where the bug is

src/isaac/comm/discord.clj DiscordPlugin/on-config-change! only
handles the nil<->token transitions. Any change with token in
both old and new is silently noop'd — including allow-from
changes — so the running gateway client retains its old
allow-from-users / allow-from-guilds sets baked in at
gateway/connect! time.

## What needs to change

1. on-config-change! must distinguish bounce-required changes
   from in-place changes:
     - bounce: token differs (or nil transitions).
     - in-place: allow-from, crew, message-cap, etc.
2. For in-place changes, push the new values into the running
   client without reconnecting. Most cleanly: have the client
   read allow-from-users / allow-from-guilds from a shared atom
   or from the live cfg-fn rather than capturing them at
   connect! time. Then on-config-change! just updates that
   source-of-truth.
3. Remove the @wip tag from the lifecycle.feature scenario.
4. Verify the existing two scenarios (\"starts mid-run\",
   \"stops mid-run\") still pass — the bounce vs. in-place
   distinction must not regress them.

## Definition of done

- gherclj run features/comm/discord/lifecycle.feature passes all
  scenarios with no @wip tags.
- Manual: edit ~/.isaac/config/isaac.edn to add or remove a user
  in :comms.discord.allow-from.users; the next message from that
  user is accepted/rejected accordingly with no
  :discord.client/started or :discord.client/stopped log lines.

## Notes

Automated acceptance is green: bb features modules/isaac.comm.discord/features/comm/discord/lifecycle.feature -> 4 examples, 0 failures. Direct smoke against the real config-reload path also passed: same client after add/remove reloads, user 456 accepted immediately after adding to :comms.discord.allow-from.users, rejected immediately after removing, and no :discord.client/started or :discord.client/stopped logs during either reload. Smoke result: {:same-client-add? true, :accepted-after-add 1, :bounce-logs-after-add [], :same-client-remove? true, :accepted-after-remove 1, :rejected-after-remove? true, :bounce-logs-after-remove []}.

