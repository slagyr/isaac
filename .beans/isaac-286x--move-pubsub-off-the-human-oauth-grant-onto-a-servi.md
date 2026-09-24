---
# isaac-286x
title: Move Pub/Sub off the human OAuth grant onto a service account
status: todo
type: feature
priority: high
created_at: 2026-09-24T18:17:37Z
updated_at: 2026-09-24T18:17:37Z
---

Repo: **isaac-google**.

## Why

`resources/isaac-manifest.edn` contributes `https://www.googleapis.com/auth/pubsub`
to the *user* login scopes. Pub/Sub is infrastructure — subscribing to a topic
is not something a person consents to — and requesting it has a cost that is
invisible until it bites:

A Workspace can apply **Google Cloud console and SDK session control** to "apps
requiring Cloud Platform scope", explicitly including non-Google apps. One Cloud
scope therefore drags the *entire* Google grant — Gmail, Chat, directory — under
a reauthentication clock whose default is 16 hours. On yopp this killed the
refresh token every ~15 hours: `gchat/fetch-failed`, `gchat.send/failed`,
dead-lettered replies, and a DM to the operator never seen, while
`systemctl is-active` said `active` and `isaac config validate` said `OK`. See
isaac-ey6q for the full diagnosis.

Isaac cannot fix the policy, and should not need to. It should stop asking a
human to consent to a machine's scope.

## Acceptance

- Pub/Sub subscription/registration authenticates as a **service account**, not
  as the logged-in user.
- `auth/pubsub` is gone from `:isaac.google/scopes`; a user login no longer
  requests any Cloud Platform scope. Verify against the consent screen, not just
  the manifest.
- Service-account credentials are configured like other secrets, absent by
  default, and their absence is a clear startup error rather than a runtime
  surprise on first push.
- Existing deployments keep working across the change, or the upgrade step is
  written down — yopp and zanebot both have live grants.
- A scenario proves the authorization URL carries no Cloud Platform scope.

## Exceptions

- Not about *what* Pub/Sub does, only which identity it does it as.
- The Workspace-side unblocks (marking the app Trusted, or relaxing the reauth
  policy) are operator actions, not code, and belong to isaac-ey6q.
