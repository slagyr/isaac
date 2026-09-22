---
# isaac-xy2i
title: 'isaac-gchat: discover spaces the account belongs to instead of listing every space in config'
status: in-progress
type: feature
priority: high
tags:
    - google
    - comm
created_at: 2026-09-19T21:13:13Z
updated_at: 2026-09-22T22:10:07Z
parent: isaac-bv1l
---

Micah, 2026-09-19 (first yopp rollout): listing every space in comms.gchat.gchat/spaces is the Discord channel-map chore again; the entries exist for two reasons — the gate fails closed on unlisted spaces, and the registration timer subscribes per configured key.

Add `gchat/spaces :all` (or `gchat/discover true`): on each registration tick, `spaces.list` (Chat API, user auth, filter SPACE and DIRECT_MESSAGE as configured) yields the spaces the account is a member of; the timer subscribes to each; the gate routes unlisted-but-discovered spaces with defaults (session gchat-<space>, :respond :mentions in spaces / :all in DMs, crew = gchat/crew default). Explicit entries remain overrides. A space the account leaves is unsubscribed on the next tick (existing delete path). Inviting the account to a space is now granting ingest — document that.

Scenarios (worker writes; registrations.feature + inbound.feature): discovered space subscribed on first tick; a mention in a discovered space starts a turn on the default session; explicit entry overrides crew; leaving a space unsubscribes; discovery off ⇒ unlisted still drops.

## Moved here from isaac-dymn (planner 2026-09-20)

Bumped 2026-09-19 (Micah): DMs are a space too and are not subscribed unless listed, so today a DM to yopp@ is never heard. Discovery (spaces.list, DMs included) is what makes DMs just work.



Micah 2026-09-19: the default is 'a space is a conversation and a conversation is a session' — every space Yopp is a member of (DMs included) routes to a canonical session without any config; entries only override. Canonical session NAME should be readable: the space displayName for named spaces (gchat/yopp-test), the other member's displayName for a DM (gchat/dm/micah-martin), with the space id carried as a session tag (space:AAQA7rg5Uyc) so a rename never orphans the session. spaces.get / spaces.members give the names.



Tenants (isaac-1zkz): discovery runs per tenant with that tenant's token; canonical session names carry the tenant when more than one exists (gchat/tonotop/yopp-test).

## Session ids are slugified (planner, 2026-09-21)

Found while Micah's DM went unheard and he asked where such sessions would
land. `store/impl-common/slugify` lower-cases the identifier and replaces every
run of non-[a-z0-9] with a hyphen, so today's canonical name arrives as
`gchat-spaces-aaqa7rg5uyc` for space `spaces/AAQA7rg5Uyc`.

Consequences for the naming above:

- `gchat/yopp-test` becomes `gchat-yopp-test`, `gchat/dm/micah-martin` becomes
  `gchat-dm-micah-martin`. Both read fine; write them in the form they will
  take rather than assuming the slash survives.
- The space id cannot live in the name: `AAQA7rg5Uyc` slugs to
  `aaqa7rg5uyc` and no longer matches the space. It belongs on the tag
  (`space:AAQA7rg5Uyc`), preserved verbatim, which is what makes a rename
  safe.
- A display name needs deliberate slugging anyway ("Micah Martin" ->
  `micah-martin`), and two spaces with the same display name must not collide
  into one session — fall back to the id, or suffix it.

Acceptance to add: a discovered space named "Yopp Test" routes to
`gchat-yopp-test` tagged `space:AAQA7rg5Uyc`; renaming the space keeps the
session (the tag matches, the name may lag); a DM with Micah routes to
`gchat-dm-micah-martin`; two spaces sharing a display name get distinct
sessions.

## Handoff (worker, 2026-09-22)

Branch: `bean/isaac-xy2i` in **isaac-gchat** only, one commit `c0d916a`.
**isaac-google was not touched** — no pin needs to move.

### Design choice: `gchat/discover true`, not `gchat/spaces :all`

`:gchat/spaces` is a `:map` in the composed comm schema. Letting it also be the
keyword `:all` gives one key two shapes for the same thing — exactly what
isaac-okfj threw out of `:google`. `:gchat/discover` is an orthogonal boolean
that reads next to `gchat/spaces` as what it is: discovery on, entries still
overrides. It covers SPACE and DIRECT_MESSAGE together (the `spaceType` filter
is a constant, not config) — nobody asked for half a mailbox, and a second knob
would have been the channel-map chore again.

### What it does

- **Registration** (`registration/space-keys`): keys are the union of the
  configured entries and, when the organization discovers, every space
  `spaces.list` returns for that tenant's token. Discovery runs inside the
  existing per-tenant `binding`, so it uses that organization's token. A space
  the account leaves drops out of the listing, so the existing `plan` delete
  path unsubscribes it on the next tick.
- **Gate**: with discovery on, the `:space` drop no longer fires for an
  unlisted space — belonging to the space is the grant. Without discovery it
  still fails closed.
- **Canonical session**: `gchat-<display-name slug>` for a space,
  `gchat-dm-<member slug>` for a DM, `gchat-<tenant>-<…>` when the host carries
  more than one organization, falling back to the space resource
  (`gchat-spaces-eng`) when Chat has not named it. The space id rides verbatim
  on a `space:<id>` tag. The tag is what matches, so a rename keeps the session
  and two spaces with one display name get `gchat-yopp-test` and
  `gchat-yopp-test-<id slug>`. An entry that pins `:session` keeps it and does
  **not** claim the tag.

### Files changed (isaac-gchat)

New: `src/isaac/comm/gchat/canon.clj` (naming + tag + collision, pure),
`src/isaac/comm/gchat/spaces.clj` (paged `spaces.list`, per-space memo),
`spec/.../canon_spec.clj`, `spec/.../spaces_spec.clj`.
Changed: `gate.clj` (discovery opens the space gate; `session-name` replaced by
`canon/canonical-name`; `space-name` → public `space-of`; `respond-policy` now
takes the entry), `handler.clj` (`decide-opts`, `settle-session`, `-sessions`
seam, tags on create), `registration.clj`, `tenant.clj` (`discovering?`),
`resources/isaac-manifest.edn` (`:gchat/discover`, version 0.2.0, reworded
`gchat/spaces`), `README.md` (discovery section; invite = ingest),
`feature-steps/isaac/gchat_steps.clj`, `features/comm/gchat/registrations.feature`,
`features/comm/gchat/inbound.feature`, and the gate/handler/registration/tenant
specs.

### Tests (isaac-gchat)

| command | result |
|---------|--------|
| `bb spec` | 109 examples, 0 failures, 203 assertions (was 84) |
| `bb features` | 34 examples, 0 failures, 76 assertions (was 27) |
| `bb ci` | green (config-bypass-lint + both suites) |
| `bb lint src feature-steps` | 0 errors, 0 warnings |

`bb lint spec/` is red on every spec file in the repo, new and old — clj-kondo
cannot see speclj's macros here. Pre-existing; `bb ci` does not run it.

### Scenarios added

**registrations.feature**
- with discovery on, the first tick subscribes every space the account belongs
  to (asserts the `spaces.list` GET carries `Bearer at-1` and the
  SPACE/DIRECT_MESSAGE filter, and that the configured-but-undiscovered
  `spaces/PROD` is still subscribed)
- a space the account has left is no longer listed and is unsubscribed

**inbound.feature**
- a mention in a discovered space starts a turn on its canonical session
  (`gchat-yopp-test`, tagged `space:AAQA7rg5Uyc`)
- renaming a discovered space keeps its session — one session, both turns
- a DM routes to a session named for the other member (`gchat-dm-micah-martin`)
- two spaces sharing a display name get two sessions
- an explicit entry overrides the session discovery would have chosen

"discovery off ⇒ unlisted still drops" is already covered by the existing
"unconfigured spaces and unknown senders fail closed".

New steps: `Given the Chat API lists the account's spaces:` (also takes the
Chat HTTP seam for the whole scenario — the registration timer ticks inside
isaac-google's step, outside any `with-redefs` of ours) and
`Then session "<key>" is tagged "<tag>"`. Both were negative-checked: a wrong
tag and a wrong filter each fail.

### Clean cutover taken

`gate/session-name` is deleted, and the canonical fallback name is now
lowercase (`gchat-spaces-eng`, not `gchat-spaces-ENG`) — same session id, since
the store slugifies either way. gate/handler specs updated; no alias kept.

### Open questions

1. **Legacy untagged sessions.** A session created before this bean has no
   `space:<id>` tag. It is adopted by name (an untagged same-name session is
   taken as this space's), but the tag is only written at create time, so those
   sessions stay untagged until someone renames the space. Worth a follow-up
   that stamps the tag on adoption if that matters.
2. **`spaces.list` cost on the inbound path.** Naming only costs a listing when
   no session carries the space tag yet, and a space Chat does not list is
   asked after once and then memoized as missing. The memo is process-lifetime
   and refreshed by each registration tick — no TTL. Fine at tick cadence;
   flag it if a long-lived process must see renames sooner.
3. **Health.** Discovered spaces now flow into `health/evaluate`'s key set, so
   a quiet DM counts as a key with no events. Nothing failed, but the silence
   thresholds were written for a handful of configured spaces.
