---
# isaac-sklm
title: "Delivery: move under isaac.comm, generalize via Comm protocol, Discord-specific code moves to Discord module"
status: completed
type: feature
priority: normal
created_at: 2026-05-07T16:32:54Z
updated_at: 2026-05-07T17:20:00Z
---

## Description

Today's isaac.delivery.* (queue, worker, backoff) is structured as a peer of comms but is really a *feature* of comms — durable best-effort outbound delivery with retry/backoff. The worker also hard-codes Discord (discord-rest/post-message!, discord-config), which leaks Discord into a supposedly generic facility. This bead reshapes delivery as a shared facility that comms opt into.

Six interlocking changes (one bead because they have to ship together to be coherent):

1. Namespace move + backoff fold:
   - isaac.delivery.queue → isaac.comm.delivery.queue
   - isaac.delivery.worker → isaac.comm.delivery.worker
   - isaac.delivery.backoff → folded into worker (it's 11 lines: one map + one lookup fn)
   - Result: 2 files, not 3.

2. State folder path move:
   - .isaac/delivery/{pending,failed}/ → .isaac/comm/delivery/{pending,failed}/
   - No migration needed (clean slate on main since 2026-03-23).

3. Drop state-dir threading:
   - queue.clj and worker.clj currently take state-dir as a positional arg.
   - With the npkc ambient seam landed, both should read state-dir from the seam (home/*state-dir* or the resolver).

4. Comm protocol gets a send! method:
   - (defprotocol Comm ... (send! [comm record] ...))
   - delivery.worker/send! becomes a thin (comm/send! (registry/comm-for (:comm record)) record).
   - Discord, Telly, ACP, etc. each implement send! in their own way.

5. Move Discord-specific code out of worker:
   - discord-config and the discord-rest/post-message! call live in the Discord module.
   - Worker no longer requires isaac.comm.discord.rest or isaac.config.loader.
   - Discord's send! impl handles its own config lookup, message-cap, transient-response classification.

6. bound-fn audit in worker/start!:
   - The (future ...) in start! escapes the request thread — wrap with bound-fn (or capture state-dir explicitly) so any ambient bindings survive. This is the audit npkc deferred.

Result: any comm that wants durable outbound opts in by implementing send!. Worker is generic. Discord owns its surface logic. State folder name matches the namespace name. No state-dir threading.

## Acceptance Criteria

isaac.delivery.* gone (moved to isaac.comm.delivery.*); backoff folded into worker; .isaac/comm/delivery/ used for new state; worker has zero Discord references; Comm protocol has send! and Discord/Telly impls have it; worker/start!'s future preserves ambient bindings; bb spec and bb features green.

## Notes

Verification failed: tests are green, and src/isaac/comm.clj defines Comm/send!, but modules/isaac.comm.telly/src/isaac/comm/telly.clj only implements api/Reconfigurable and does not implement Comm or send!. The acceptance criterion requires send! impls on both Discord and Telly.

