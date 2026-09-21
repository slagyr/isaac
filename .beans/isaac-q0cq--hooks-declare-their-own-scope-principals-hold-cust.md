---
# isaac-q0cq
title: Hooks declare their own scope; principals hold custom hook scopes
status: draft
type: feature
priority: normal
tags:
    - hooks
    - security
created_at: 2026-09-21T17:34:41Z
updated_at: 2026-09-21T17:34:41Z
parent: isaac-gym1
---

Micah, 2026-09-21: "hooks should be able to specify their scope and we can
declare auth entries that have access to custom hook scopes."

Today every hook route carries the one scope `:hooks` (isaac-4o6r): a
principal that may fire any hook may fire every hook. A hook that wants a
tighter circle — a deploy hook, a hook wired to a public service — has no
way to say so.

## Shape

- A hook's frontmatter may declare `scope: hooks/deploy` (any keyword;
  default stays `hooks`). The route berth keeps `:scope :hooks` for the
  path family; after path lookup the handler calls `require-scope!` with
  the hook's own scope, so the check happens in the module that knows the
  hook, with isaac-http's existing primitive.
- A principal declares the custom scope like any other:
  `http.auth.principals.deploy-bot {:hash … :scopes #{:hooks/deploy}}`.
  `isaac http auth mint deploy-bot --scope hooks/deploy` accepts it —
  scopes are open keywords, no registry.
- **Decision to confirm:** a hook with a custom scope requires exactly that
  scope. The umbrella `:hooks` does not fire it (otherwise narrowing does
  nothing for principals that already hold `:hooks`); `:*` still does.
- `config validate` warns on a hook whose scope no principal holds
  (nothing could ever fire it).
- Public hooks are isaac-161q's concern (a scope exempted from auth), not
  this bean's.

## Scenarios (isaac-hooks `features/hooks.feature`)

- a hook declaring `scope: hooks/deploy` fires for a principal holding
  `hooks/deploy` (202) and is refused 403 for one holding only `hooks`
- a hook with no `scope` still fires for `hooks`
- an OIDC principal (config rule) holding `hooks/deploy` fires it — same
  path, no special case

Repo: isaac-hooks, with the principal side already in isaac-http.
