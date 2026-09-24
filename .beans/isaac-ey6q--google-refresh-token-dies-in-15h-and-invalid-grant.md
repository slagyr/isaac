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
