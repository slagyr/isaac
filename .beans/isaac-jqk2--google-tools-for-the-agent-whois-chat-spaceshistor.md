---
# isaac-jqk2
title: 'Google tools for the agent: whois, chat spaces/history/send, gmail search/read/send — Isaac''s token and scopes, no gws shell'
status: todo
type: feature
priority: high
tags:
    - google
    - comm
    - tools
created_at: 2026-09-20T00:28:14Z
updated_at: 2026-09-20T00:30:42Z
parent: isaac-bv1l
---

Micah 2026-09-19: rather than caches, ask Google — and give the agent tools to do the same. Today the yopp crew reaches Gmail/Drive by shelling out to the gws CLI via exec with its own OAuth grant and a keyring env dance. Isaac tools use Isaac's token (per tenant, isaac-1zkz), Isaac's scopes, and the per-crew tool allow-list, deterministically.

Contributed through :isaac.agent/tools by the module that owns each API:
- isaac-google: google__whois — users/<id> → {name email domain}; email → id. Read-only (People API, directory.readonly).
- isaac-gchat: gchat__spaces (spaces the account is in), gchat__history (space or thread, since a time or Isaac's last reply, paged), gchat__send (space/thread/DM — the comm-send path; side-effecting, opt-in per crew like comm-send).
- isaac-gmail: gmail__search (q=…), gmail__read (message/thread, decoded), gmail__send (reply on thread or new; side-effecting, opt-in), gmail__labels.

Keep deterministic regardless of tools: the inbound gate (who may start a turn) and a minimal context injection on mention (isaac-tund: the thread since Isaac's last reply) — a model should not have to remember to fetch context. Tools cover everything beyond that; gchat__history lets it reach further back on demand.

Open for Micah: which tools are on by default for the yopp crew (whois/history/search/read likely yes; send probably yes for gchat, gmail send as today's gws usage suggests yes); whether gws stays installed on yopp once these exist.



**Decided 2026-09-19 (Micah): all tools on for the yopp crew** — whois, gchat spaces/history/send, gmail search/read/send/labels. Sends included. Keep the per-crew allow-list as the mechanism (a cautious crew can drop the sends); default set for yopp = all. gws on yopp: revisit once these ship (tonotop root doc records that gws also covers Drive/Calendar, which these tools do not).
