---
# isaac-ey6q
title: Google refresh token dies in ~15h, and invalid-grant-message blames a cause it never checks
status: todo
type: bug
priority: high
created_at: 2026-09-24T16:16:34Z
updated_at: 2026-09-24T16:16:34Z
---

Repo: **isaac-google** (`src/isaac/google/oauth.clj`, `src/isaac/google/token.clj`).

## Two problems, and the second is why the first went undiagnosed

### 1. A refresh token dies in ~15 hours

yopp's Google login history and the first failure that followed it:

    2026-09-23T15:05Z  google/login-completed  (tenant :tonotop)
    2026-09-24T05:55Z  invalid_grant           — 14h50m later
    2026-09-24T15:41Z  google/login-completed  (re-login)

A refresh token should persist indefinitely — until revoked, unused for six
months, or capped by a publishing-status policy. None of those applies here
(see below). It lasted under fifteen hours.

While it was dead, yopp could neither fetch nor send: `gchat/fetch-failed`,
five `gchat.send/failed` and a `comm.delivery/dead-lettered` on a reply to
`spaces/26gscq…`. A DM sent to it was never seen. `systemctl is-active` said
`active` and `isaac config validate` said `OK` throughout.

**Leading suspect: rotated-token persistence.** Google may return a *new*
refresh token in a refresh response; the old one then dies. `token.clj:69-70`
handles the case where the response carries *no* refresh token, carrying the
stored one forward:

    (not (:refresh_token response))
    (assoc :refresh_token (:refresh tokens))

Whether the opposite case — a response that *does* carry a new refresh token —
is persisted is the thing to verify first. A failure to store a rotation would
produce exactly this shape: works now, `invalid_grant` hours later, fixed by a
fresh login, recurring on the same cycle.

Other candidates, not yet excluded: a concurrent refresh race between the
scheduler and a live turn, each writing `auth.json`; or a revocation triggered
by another consent against the same client.

### 2. `invalid-grant-message` asserts a cause it never checks

`oauth.clj:55`:

    (defn invalid-grant-message []
      (str "Google rejected the refresh token (invalid_grant). "
           "The consent screen is still in Testing — publish the app or "
           "re-run `isaac google login`."))

Unconditional. Every `invalid_grant` is reported as a Testing-mode expiry
regardless of the project's actual publishing status.

**For this project that is impossible.** The `tonotop-yopp` consent screen is
**User type: Internal**. Internal apps have no Testing publishing state and no
7-day refresh-token cap. The message named a cause that cannot occur in this
deployment, sent the operator to a console page with nothing to change, and
cost real time — it is the reason the ~15-hour interval went unexamined for
half a day.

The codebase can already tell the difference: `cli.clj:189` checks
`oauth/seven-day-token?` against `expires_in` at login and only then says
"Google returned a 7-day refresh token." The honest signal exists; the error
path ignores it.

**Change:** say what is known — the grant was rejected — and name causes only
when detected. Something like "Google rejected the refresh token
(invalid_grant); re-run `isaac google login`", plus the Testing hint *only*
when a 7-day token was actually observed at login.

## Acceptance

- A refresh response carrying a new refresh token persists it; a spec proves
  the stored token changes.
- Concurrent refreshes cannot leave a stale token on disk (or the race is
  documented as impossible, with reasoning).
- `invalid-grant-message` states no cause it has not detected. Spec coverage for
  the detected-7-day-token case and the unknown-cause case.
- yopp survives more than 24 hours without re-login. That is the real proof and
  cannot be met from the suite.

## Evidence trail

Both hosts run isaac.google `48625f2`. zanebot configures no Google tenant, so
this is yopp-only today. yopp's `auth.json` stores `expires` = the *access*
token's 1-hour expiry; nothing on disk records the refresh token's intended
lifetime, which is part of why this is hard to observe.

## Planner findings 2026-09-24 — the leading suspect is disproved, and so is its replacement

**Rotation-persistence (the bean's leading suspect) is not the bug.** Verified
in isaac-agent `src/isaac/llm/auth/store.clj:33` — `save-tokens!` writes
`:refresh (:refresh_token tokens)` from the refresh *response*. The `cond->` at
`token.clj:69-70` only fills the stored token in when the response omits one. A
rotated token does land on disk.

**A single-flight guard was added, but it does not explain the symptom.**
`resolve-tokens` had no lock, unlike the generic
`auth-store/refresh-oauth-tokens!`. Two callers could spend the same refresh
token and both write `auth.json`. That is worth fixing on its own and is fixed.
But the harm it prevents — "the loser writes back a token Google already
rotated away" — requires Google to rotate, and measurement says it does not:

    16:56:13Z  refresh_fp=5169c127552b  access_expires=1790268108185 (16:41:48Z, stale)
    16:56:40Z  refresh_fp=5169c127552b  access_expires=1790272600870 (refreshed)

A real refresh occurred (the access expiry moved) and the refresh token was
**unchanged**. One observation is not proof Google never rotates, but the race
is benign for this client on the evidence available, so **the ~15h cause
remains unidentified.**

(The fingerprint is a truncated sha256, never the token. Probe kept at
`/tmp/tokfp.sh` on yopp.)

## Next hypothesis to test first: a Workspace session-control policy

The consent screen is **Internal**, i.e. the app is owned by the `tonotop.com`
Workspace. Google Workspace admins can set a session/reauthentication policy
(Admin console → Security → Access and data control → Google session control)
that expires OAuth grants on a fixed clock. A ~15h interval that survives a
fresh login and then dies again on the same cycle matches an **admin policy**
far better than it matches anything in this codebase — and would explain why
nothing on disk records the refresh token's intended lifetime.

This is an admin-console check, not a code change. It should be ruled in or out
before more code is written against defect 1.

## Status of the acceptance list

- Rotated refresh token persists, with a spec — **done** (the spec passes
  against unfixed source; it documents existing behaviour rather than fixing it)
- Concurrent refresh cannot leave a stale token — **done in-process**; the
  cross-process case (a separate `isaac` CLI writing the same auth.json) is
  documented in the `refresh-lock` docstring, not fixed. An on-disk lock or
  atomic replace belongs in isaac-agent's `isaac.llm.auth.store`.
- `invalid-grant-message` asserts no undetected cause — **done**. Note the
  7-day branch is effectively unreachable in production: Google's `invalid_grant`
  body carries no `expires_in`, and the login-time observation at `cli.clj:189`
  is not persisted. Making the hint fire for real means changing the auth.json
  entry shape in isaac-agent — out of scope here.
- yopp survives >24h without re-login — **open, and now the only real proof.**
  Re-fingerprint after 2026-09-25T16:00Z.

## ROOT CAUSE FOUND 2026-09-24 — the pubsub scope drags the grant under a Cloud reauth policy

isaac-google's own manifest contributes a **Google Cloud Platform** scope to the
*user* login:

    :isaac.google/scopes ["openid"
                          "https://www.googleapis.com/auth/directory.readonly"
                          "https://www.googleapis.com/auth/pubsub"]

The `tonotop` Workspace has **Google Cloud console and SDK session control**
configured. That page states, verbatim:

> Select how often users are challenged for credentials on apps requiring
> Cloud Platform scope.
> The reauthentication policy above also applies to **non-Google apps**
> requiring Cloud Platform scope.

`auth/pubsub` is a Cloud Platform scope, so yopp's grant is in policy, and the
reauthentication frequency — default **16 hours** — expires it. Measured
interval was **14h50m**. That matches, and it explains every property that made
this confusing:

- survives a fresh login, then dies again on the same clock — it is a *policy
  timer*, not a token defect
- the refresh token on disk is never altered (measured: byte-identical
  fingerprint across a real refresh) — Google revokes it server-side
- Internal publishing status is irrelevant, which is why the Testing-mode
  message was not merely unhelpful but pointed 180° away

This is not a defect in the refresh code. Nothing in isaac-google could have
prevented it.

## The fix is to stop requesting a Cloud scope on a human's grant

Pub/Sub is infrastructure. Subscribing to a topic is not something that should
ride on a person's OAuth consent, and doing so is what pulls the entire Google
grant — Gmail, Chat, directory — under a Cloud Platform reauthentication clock.
A service account is the right credential for Pub/Sub. Tracked separately.

Immediate unblocks available to the operator, in preference order:

1. **Mark the app Trusted** (Apps Access Control) — the session-control page
   offers "Exempt Trusted apps". Scoped to this app; no org-wide weakening.
2. Move Pub/Sub to a service account and drop `auth/pubsub` from the user
   scopes — the real fix, and it removes the policy's grip entirely.
3. Set "Never require reauthentication" — org-wide, weakens posture, not
   recommended.

## Revised status

- Defect 2 (`invalid-grant-message`) — **fixed and landed**, `b793c99`.
- The single-flight lock — landed in the same commit. Correct hygiene, but
  **not** the cause; keep it, do not credit it.
- Defect 1 — **cause identified as an external Workspace policy.** The code
  change that follows is removing the Cloud scope from the user grant.
- "yopp survives >24h" — still the proof, but now predictable: it will fail
  again around 2026-09-25T06:30Z unless option 1 or 2 is applied first.

## Status 2026-09-24 22:5x — the fix is deployed; one observation remains

Deployed to yopp: isaac-google `5cdf807`, which carries this bean's
`invalid-grant-message` fix, isaac-286x's scope removal, and isaac-clly. The
operator re-ran `isaac google login --tenant tonotop` after the scope was
removed, so the live grant no longer carries `auth/pubsub`.

**The remaining check is the whole proof.** The Workspace policy expires grants
holding a Cloud Platform scope on a 16-hour clock; measured failure was 14h50m.
With the scope gone the policy should no longer apply.

- Prior failures: login 2026-09-23T15:05Z → `invalid_grant` 2026-09-24T05:55Z
- Re-login after the scope removal: 2026-09-24, evening
- **If nothing fails by roughly 2026-09-25T15:00Z, the fix is confirmed.**

What to look for on yopp: `invalid_grant`, `gchat/fetch-failed`,
`gchat.send/failed`, or a `comm.delivery/dead-lettered` on a Chat reply. The
failure is silent in every other respect — `systemctl is-active` says active
and `isaac config validate` says OK throughout, which is what made it hard.

A refresh-token fingerprint probe is at `/tmp/tokfp.sh` on yopp; it prints a
truncated sha256 only, never the token. An unchanged fingerprint alongside an
`invalid_grant` means server-side revocation (policy), not a token Isaac
mishandled.

If it **does** fail again on the same clock, the scope was not the whole story
and the next suspect is whatever else in the grant Google treats as Cloud —
check the consent screen's granted scopes directly rather than the manifest.
