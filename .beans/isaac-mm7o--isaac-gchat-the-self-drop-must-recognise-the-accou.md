---
# isaac-mm7o
title: 'isaac-gchat: the self-drop must recognise the account by users/<id>, not only by email'
status: in-progress
type: bug
priority: normal
tags:
    - google
    - comm
created_at: 2026-09-19T23:48:52Z
updated_at: 2026-09-22T22:10:07Z
parent: isaac-bv1l
---

2026-09-19 23:47Z on yopp: after Isaac replied in the thread, Google pushed the reply back as an event; the gate dropped it — but as :sender (yopp's Chat user id users/101936183306307394083 is not in allow-from), not as :self, because gchat/account is an email and the sender carries none. Right outcome by luck: an operator who allows domain:<id> would let Isaac's own replies through and create an echo loop.

Do: learn the account's users/<id> (spaces.members or people/me on first use; cache in state) and check :self against it; scenario: Isaac's own reply, with a domain allow-list, drops :self.

## Handoff (worker, 2026-09-22)

The prior commit (unpushed, left by a 2026-09-20 worker, rebased onto main by
the time I picked this up) already had the whole shape: `isaac.comm.gchat.self`
caches Isaac's own `users/<id>` in an atom (not config), `:gchat/account-id` in
config short-circuits it, the gate matches sender `:user` against it (drop
`:self`), the manifest declares the key in `:extra-schema` (undeclared config
keys get pruned to nil before the gate sees them), and the mm7o scenario in
`features/comm/gchat/inbound.feature` was present and green.

Design note: instead of calling `spaces.members`/`people/me`, it learns the id
opportunistically from the response of Isaac's own outbound `create-message!`
calls (`isaac.comm.gchat/post-chunks!` now wraps the call in
`self/learn-from-send!`). That's sound for the bug as reported — the send that
triggers Chat's echo-back always happens before the pushback event arrives — so
I kept it rather than widening scope to add a proactive lookup call.

What I changed: `src/isaac/comm/gchat/self.clj` had `[clojure.string :as str]`
required but never used (`bb lint` flagged it) — removed the require, kept the
docstring. No other production/test changes; scope stayed to the self-drop.

Verified: `bb lint` clean on the touched files; `bb spec` → 91 examples, 0
failures, 164 assertions; `bb features` → 28 examples, 0 failures, 64
assertions; `bb ci` exit 0, same counts. (Full unscoped `bb lint` still flags
51 pre-existing errors in `spec/isaac/comm/gchat_spec.clj`, a file this bean
never touches — untouched by this diff, left alone.)

Squashed to one commit on `bean/isaac-mm7o` (`d723e6a`, amended over the prior
worker's commit, same message) and pushed with `--force-with-lease` (remote
held the pre-rebase `eb1b621`, as expected).

isaac-google worktree: not touched — the id is learned entirely in
isaac-gchat's own send/gate/self layer; no isaac-google client-layer change
was needed.
