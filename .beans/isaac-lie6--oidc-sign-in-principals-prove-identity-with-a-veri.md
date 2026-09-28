---
# isaac-lie6
title: 'OIDC sign-in: principals prove identity with a verified email from any standard OIDC provider'
status: draft
type: feature
priority: normal
created_at: 2026-09-28T15:26:47Z
updated_at: 2026-09-28T15:26:47Z
parent: isaac-gym1
---

Likely repo: **isaac-http**. Design: Micah + planner, 2026-09-28. Motivated by private cargo assets (isaac-sspg): a person on another computer signs in with a browser instead of holding a bearer secret.

## Decisions (2026-09-28, Micah)

- **Lives in isaac-http.** Everything here is HTTP-request authentication (headers, cookies, redirects, callback routes). Split identity into its own module only if principals ever need to span non-HTTP transports (comm allow lists, Discord/iMessage identities).
- **A principal is a person or client; login providers are ways to prove it.** One principal carries its scopes and every identity that proves it (bearer secret hash, verified emails). Sign in with any configured provider and Isaac knows who you are and what you may reach.
- **Config is maps, not a strategy vector**, and nothing existing moves:

```clojure
{:http {:auth {:principals {:micah   {:scopes [:cargo/read]
                                      :emails ["micah@example.com"]}      ; new
                            :planner {:hash "sha256:…" :scopes [:hail/send]}}
               :identity   {:github {…}}                                   ; unchanged (machine OIDC)
               :login      {:google {:issuer        "https://accounts.google.com"   ; new
                                     :client-id     "…apps.googleusercontent.com"
                                     :client-secret "${GOOGLE_LOGIN_CLIENT_SECRET}"
                                     :token-ttl     "8h"}}}}}               ; optional, default 8h
```

- **Generic OIDC, not Google-specific.** Isaac reads the issuer's discovery document (`/.well-known/openid-configuration`) for the authorize endpoint, token endpoint, and signing keys; ID tokens are verified with the existing JWKS code (`isaac.http.oidc`). Works for Google, Microsoft Entra, Okta, Auth0, Keycloak, GitLab. **Apple** (self-signed client-secret JWT, form-post callback) and **GitHub** (plain OAuth, no ID token) would be separate small modules if ever wanted.
- **`:token-ttl`**: how long a sign-in lasts. Isaac keeps **no server-side session**: the cookie is a signed token carrying the principal and expiry; Isaac generates and keeps its signing key under the root. No cookie config.
- **Routes per login entry:** `/auth/<id>/login` (redirect to the provider, remembering the return URL; state + nonce + PKCE), `/auth/<id>/callback` (exchange the code server-to-server, verify the ID token, require `email_verified`, match an `:emails` entry, set the cookie, redirect back), `/auth/logout`.
- **Browsers vs other clients:** a request without a valid cookie that accepts HTML is redirected to sign in; anything else gets 401. `Authorization: Bearer` keeps working for curl/scripts.
- **Unknown identity:** a verified sign-in that matches no principal gets a plain-text 403 naming the provider, subject, and email (it only shows the viewer their own identity) plus a log line. No HTML pages.
- **Deferred:** `:identities {:apple "<sub>"}` subject matching (for providers without a verified email) and an `isaac auth link` CLI — add when a provider needs it.
- **Provider setup is documentation, not code:** e.g. Google — OAuth client of type Web application, redirect URI `https://<host>/auth/google/callback`, consent screen in Testing mode with the allowed accounts as test users (basic scopes `openid email` need no verification).

## Relationship

- Builds on isaac-pqqm (auth strategies); see the note there about keeping config as maps.
- Unlocks `:private` visibility in isaac-sspg (cargo).

## Scenario plan (to draft)

1. A browser request to a protected route without a cookie is redirected to the provider's sign-in; a non-browser request gets 401.
2. A callback with a valid ID token for a listed verified email sets the cookie and redirects back; the protected route then serves.
3. An unverified or unlisted email gets 403 naming the identity; nothing is granted.
4. An expired token cookie sends a browser back through sign-in.
5. Bearer principals still work alongside sign-in.
6. Config validation: a `:login` entry missing issuer/client-id/client-secret fails; `:emails` on two principals collide loudly.

Draft until scenarios exist.
