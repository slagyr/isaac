---
# isaac-3ldm
title: Discord module fails to compile (stray log/debug in send! let-binding) — comm_send to discord dead-letters; discord down in prod
status: completed
type: bug
priority: critical
tags:
    - discord
    - comm
created_at: 2026-06-30T18:04:06Z
updated_at: 2026-06-30T18:09:14Z
---

## Symptom (production — discord fully down)

Every `comm_send {:comm "discord" ...}` permanently dead-letters. Confirmed live:

```
:event :delivery/dead-lettered, :reason :permanent, :id "8f23",
  :file "isaac/comm/delivery/worker.clj", :line 61   (2026-06-30T17:57:33Z)
```

from agent `marvin` (session `glimmering-cardinal`) sending content `"test"` to
`:discord.target "pub"`.

## Root cause: discord module fails to COMPILE at boot

Discord never activates:

```
:event :module/activation-failed, :module "isaac.comm.discord",
  :error "Syntax error macroexpanding clojure.core/let at (isaac/comm/discord.clj:311:5)."
:event :module/activation-failed, :service "discord",
  :error "Syntax error compiling at (isaac/comm/discord/service.clj:1:1)."
```

The `send!` method has a stray `(log/debug ...)` form sitting in the `let` **binding
vector** (no binding symbol), so the binding pairs misalign and `let` macroexpansion
fails:

```clojure
(send! [_ record]
  (let [dcfg        (live-discord-cfg state-dir cfg)
        (log/debug :debug/resolve-target :target (:discord/target record)
                   :dcfg-channels (get dcfg :discord/channels))   ; <-- in binding position
        channel-id  (resolve-target-channel dcfg (:discord/target record))
        response    (rest/post-message! { ... })]
    ...))
```

With no active discord comm, the delivery worker has no transport and dead-letters
the record as `:permanent`.

## This is discord main HEAD, NOT a stale pin

Deployed discord SHA `09ff9b04` **== isaac-discord main HEAD** (`compare … → identical`).
The break was introduced by the live-config target-resolution change
(commit "Make outbound comm_send target resolution use live config (runtime-discord-cfg) …",
the `vhyw` work). The foundation 0.1.15 deploy did **not** cause it — the discord SHA is
unchanged across 0.1.14 → 0.1.15, so discord has been failing to activate the whole time.

## Fix

Move the debug log out of the binding vector (bind to `_`, or — better — drop it; it is
clearly leftover scaffolding):

```clojure
(let [dcfg       (live-discord-cfg state-dir cfg)
      channel-id (resolve-target-channel dcfg (:discord/target record))
      response   (rest/post-message! { ... })]
  ...)
```

Then bump the discord module pin and redeploy. **Add a compile/activation smoke check** so a
non-compiling comm module fails CI instead of silently dead-lettering in production — that
is the deeper gap here: a module that doesn't compile shipped to main and a release.

## Related

- isaac-vhyw — the live-config change that introduced this.
- isaac-kxre — the same 0.1.15 deploy's (separate) server-log issue.
- Module activation-failure should be loud/fatal on a comm the config declares active.

## Verification (2026-06-30)
Verified on fetched GitHub `isaac-discord` `main` `15c196f876201a7356cd1c390a9293f35f9bdd3d`.

Focused proof passed:

- `bb spec spec/isaac/module_activation_spec.clj spec/isaac/comm/discord_spec.clj` -> `71 examples, 0 failures, 150 assertions`

That covers both sides of the fix:

- the compile/activation smoke check now catches a broken Discord module at load time
- the outbound `send!` path and live-config target resolution specs are green on the repaired head
