---
# isaac-m8vk
title: 'isaac-gmail: supply the sender as the turn''s :from handle'
status: todo
type: feature
priority: normal
created_at: 2026-11-17T01:22:51Z
updated_at: 2026-11-17T01:22:51Z
parent: isaac-zt1x
---

Part of the contacts epic (isaac-zt1x), the Gmail counterpart of isaac-dlw5. Micah, 2026-11-16: install contacts on yopp and set the Gmail sender handle.

Today a Gmail-originated turn records no sender handle, so isaac-contacts can't name who wrote. Supply `:from` on the submitted turn: `{:kind :handle :comm :gmail :id <from address> :email <from address> :authenticated false}`, plus `:name` when the From header carries a display name. The address is the bare, lower-cased address from the From header. `:authenticated` is false: anyone can write a From header (SPF/DKIM verification is a later question).

Look at how isaac-gchat does it (`isaac-gchat` commit fb17147, features/comm/gchat/inbound.feature 'the turn records who spoke as a verified handle').

Acceptance: the @wip scenario in features/comm/gmail/gmail.feature passes; the rest of the suite stays green.

feature-baseline: isaac-gmail dfb3c0446c97dad24c35fe462c789e6347f8b78c
feature-blob: isaac-gmail features/comm/gmail/gmail.feature e6bb80f8e20bea20307ce59533e376ef35076977 56
