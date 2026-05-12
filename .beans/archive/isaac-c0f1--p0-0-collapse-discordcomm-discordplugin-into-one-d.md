---
# isaac-c0f1
title: "P0.0 Collapse DiscordComm + DiscordPlugin into one DiscordIntegration"
status: completed
type: task
priority: normal
created_at: 2026-05-01T00:40:23Z
updated_at: 2026-05-01T23:11:14Z
---

## Description

Today src/isaac/comm/discord.clj defines two deftypes for one
integration:

  DiscordComm   [channel-id message-cap state-dir token]   ; per-message
  DiscordPlugin [state-dir connect-ws! conn]               ; long-lived

DiscordComm holds channel-id at construction time, so process-message!
constructs a fresh DiscordComm for every incoming Discord message.
That's structural rot: Comm methods already take session-key, so a
singleton can derive channel-id at call time.

We don't want to design the plugin/lifecycle architecture against
this shape. Fix it before P0.1.

## Rebase note — depends on isaac-3qzm

3qzm makes Discord routing pure-config (deletes routing.edn, channels
become first-class config citizens with default session name
discord-<channel-id>). After 3qzm:

- No reverse-index problem — session-key carries channel-id by name
  or is resolvable from config :comms.discord.channels
- Most of the helpers this bead would have touched
  (route-session-name, ensure-session!, write-routing-table!, etc.)
  are deleted by 3qzm
- Token (and message-cap, if it stays) come from cfg, not from a
  per-message construction

Acceptance below assumes the post-3qzm shape. Do not start until
3qzm closes; rebase the file shape mentally before drafting code.

## Scope

Replace the two deftypes with one DiscordIntegration that:

- Holds (state-dir, state-atom) where state-atom carries the
  cfg-derived runtime fields (token, conn, message-cap if applicable,
  any other per-config values)
- Implements Comm — every method takes session-key and resolves
  channel-id either from the session name (discord-<chan-id>) or by
  walking the configured :channels map. No routing.edn lookup.
- Implements the existing Plugin protocol: config-path,
  on-startup!, on-config-change! (the on-startup! method was added
  to plugin_spec.clj as part of this chain)
- Owned by the config-loader/plugin manager as a singleton per cfg

Drop:

- (channel ...) factory in discord.clj
- per-message DiscordComm construction in process-message!

The WS callback constructs/ensures the session, then passes the
singleton DiscordIntegration as :channel to run-turn!. run-turn!
threads session-key through Comm methods unchanged.

## Open question — message-cap placement

Pre-3qzm, message-cap is a top-level :comms.discord.message-cap.
Post-3qzm, channels carry their own (:session, :crew, :model). Does
message-cap stay top-level, or move per-channel? Resolve when
drafting; mention in description rather than forcing a choice now.

## Why now

Designing the lifecycle/plugin architecture (P0.1) requires each
config slice to have at most one owner. With per-message DiscordComm,
Discord has TWO ownership lifetimes (singleton Plugin + ephemeral
Comm), forcing the framework to accept that as first-class. After
P0.0, Discord has one owner; the framework is simpler.

## Anti-criteria (out of scope)

- Don't change the Comm protocol surface
- Don't rename isaac.plugin (that's P0.1)
- Don't introduce the manifest format (P1.1)
- Don't change config schema for :channels (3qzm owns that)
- Don't touch the per-turn context preamble (3qzm owns that too)

## Acceptance

- src/isaac/comm/discord.clj has ONE deftype (DiscordIntegration)
- The Discord plugin spec at spec/isaac/plugin_spec.clj exercises
  lifecycle (start, sync, stop) and passes
- Discord features (lifecycle, routing, intake, turn-context, hot
  reload) all green post-merge
- One DiscordIntegration handles messages on multiple configured
  channels in the same process (no per-message construction)
- Token rotation via on-config-change! is observable in the next
  outbound REST call
- process-message! contains no (channel ...) call and no
  ->DiscordComm construction
- channel-id resolution happens inside Comm methods, not before
  dispatch

## Doesn't include

- Lifecycle-protocol generalization (P0.1)
- Renaming/moving isaac.plugin (P0.1)
- Plugin manifest system (P1+)

## Blocks

- P0.1 — the lifecycle architecture is designed against the cleaned
  shape, not the current two-deftype mess.

