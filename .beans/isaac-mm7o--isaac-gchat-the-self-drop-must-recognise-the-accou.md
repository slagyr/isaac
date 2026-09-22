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
