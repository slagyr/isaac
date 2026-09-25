---
# isaac-klye
title: 'isaac-gchat: a mention means the account — a message that @-mentions someone else is not addressed to Yopp'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-09-25T02:35:49Z
updated_at: 2026-09-25T02:40:54Z
---

## Symptom

Marketing space (spaces/AAQASjyfLk4), 2026-09-25 01:12Z and 01:13Z: Yopp
answered two of Chris Sherrick's messages that did not mention him. Both
were `gchat/message-routed`; the policy for the space is :mentions.

## Cause

`isaac.comm.gchat.gate/mentioned?` returns true when the message carries
**any** userMention annotation — it never compares the mentioned user to the
account. A message that @-mentions a colleague reads as a mention of Yopp.

## Design

- Resolve the account's own user resource once per comm (the same identity
  the handler already uses to drop `:self` messages — sender name/email of
  yopp@tonotop.com) and expose it as `self-user` on the decision opts.
- `mentioned?` is true only when a userMention annotation names that user
  (by `users/<id>`, or by email when the annotation carries one). Mentions
  of anyone else are not mentions. `@all`/space-wide mentions: treat as a
  mention (document the choice in the docstring).
- No config change. A DM still answers everything (`:all`).

## Acceptance (isaac-gchat spec + gate feature)

- [ ] Message with a userMention of another user, policy :mentions → :log.
- [ ] Message with a userMention of the account → dispatch; same with the
  account's email in the annotation; same for both annotation shapes the
  gate already handles (map and sequence).
- [ ] Message with no annotations → :log (unchanged). DM → dispatch (unchanged).
- [ ] Version bump, bb spec / bb features / bb lint green.

Likely repo scope: isaac-gchat (gate.clj, handler.clj for self identity).

## Work notes (scrapper@isaac-work-2, 2026-09-24)

Branch: isaac-gchat `bean/isaac-klye` (402af62, 88beea4). Not gated (bean-gate exit 2) → verify path.

- gate.clj: `mentioned?` now takes the account's self identity (`:gchat/account` email, account users/<id> from config `:gchat/account-id` or learned via self/resolve-account-user). An annotation counts only when its user is `users/<account-id>`, or carries the account's email (case-insensitive), or — account id not known yet — the People resolver says that user's email is the account. `users/all` (@all) counts as a mention (docstring). Both annotation shapes (map incl. `{:mention "users/…"}` and sequence) handled.
- handler.clj needed no change: decide-opts already passes `:account-user` + `:resolve-person`.
- Specs: gate_spec context "a mention means the account (isaac-klye)" (other user → :log; account id / email → route in both shapes; learned id; resolver fallback incl. failure; @all; no annotations → :log; DM → route). handler_spec slices declare `:gchat/account-id "users/yopp"`.
- Features: inbound/outbound Backgrounds declare `account-id users/yopp`; new scenario "a message that mentions someone else is heard, not answered (isaac-klye)"; tenant scenario (isaac-mm7o) deletes the configured id and mentions the id tonotop learned (`users/self-at-tonotop`).
- Manifest 0.2.13 → 0.2.14. bb spec 180/0, bb features 55/0, bb lint src 0/0 (spec lint 74 errors pre-existing on main — speclj refer :all).
- Operational note: a deployment with neither `:gchat/account-id` nor a working People directory scope cannot recognise its own mention until its first send teaches it the id — set `:gchat/account-id` on Yopp's comm.
