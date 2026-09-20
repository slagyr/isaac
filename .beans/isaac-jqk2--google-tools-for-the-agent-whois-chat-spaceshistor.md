---
# isaac-jqk2
title: 'Google tools for the agent: whois, chat spaces/history/send, gmail search/read/send — Isaac''s token and scopes, no gws shell'
status: completed
type: feature
priority: high
tags:
    - google
    - comm
    - tools
created_at: 2026-09-20T00:28:14Z
updated_at: 2026-09-20T07:27:08Z
parent: isaac-bv1l
blocked_by:
    - isaac-8s6s
---

Micah 2026-09-19: rather than caches, ask Google — and give the agent tools to do the same. Today the yopp crew reaches Gmail/Drive by shelling out to the gws CLI via exec with its own OAuth grant and a keyring env dance. Isaac tools use Isaac's token (per tenant, isaac-1zkz), Isaac's scopes, and the per-crew tool allow-list, deterministically.

Contributed through :isaac.agent/tools by the module that owns each API:
- isaac-google: google__whois — users/<id> → {name email domain}; email → id. Read-only (People API, directory.readonly).
- isaac-gchat: gchat__spaces (spaces the account is in), gchat__history (space or thread, since a time or Isaac's last reply, paged), gchat__send (space/thread/DM — the comm-send path; side-effecting, opt-in per crew like comm-send).
- isaac-gmail: gmail__search (q=…), gmail__read (message/thread, decoded), gmail__send (reply on thread or new; side-effecting, opt-in), gmail__labels.

Keep deterministic regardless of tools: the inbound gate (who may start a turn) and a minimal context injection on mention (isaac-tund: the thread since Isaac's last reply) — a model should not have to remember to fetch context. Tools cover everything beyond that; gchat__history lets it reach further back on demand.

Open for Micah: which tools are on by default for the yopp crew (whois/history/search/read likely yes; send probably yes for gchat, gmail send as today's gws usage suggests yes); whether gws stays installed on yopp once these exist.



**Decided 2026-09-19 (Micah): all tools on for the yopp crew** — whois, gchat spaces/history/send, gmail search/read/send/labels. Sends included. Keep the per-crew allow-list as the mechanism (a cautious crew can drop the sends); default set for yopp = all. gws on yopp: revisit once these ship (tonotop root doc records that gws also covers Drive/Calendar, which these tools do not).

## Landed on main (planner, 2026-09-20)

Landed by the planner during the fleet's auth outage. Eight tools, each
contributed through `:isaac.agent/tools` by the module that owns the API, all
on Isaac's own token and scopes and subject to the crew's tool allow-list.

| tool | module | what it does |
| --- | --- | --- |
| `google__whois` | isaac-google | users/<id> → name + email; email → users/<id> |
| `gchat__spaces` | isaac-gchat | the spaces and DMs the account is in |
| `gchat__history` | isaac-gchat | a space or thread, oldest first, optionally since a time |
| `gchat__send` | isaac-gchat | post to a space or thread (side-effecting) |
| `gmail__search` | isaac-gmail | Gmail query syntax → ids + headers |
| `gmail__read` | isaac-gmail | one message, decoded |
| `gmail__send` | isaac-gmail | threaded reply, or a new message (side-effecting) |
| `gmail__labels` | isaac-gmail | the mailbox's labels |

Every tool answers `{:isError true :error …}` rather than throwing, and the
two that leave the building say "Side-effecting" in the description the model
reads.

Deterministic behaviour is untouched, as the bean requires: the inbound gate
still decides who may start a turn, and a mention still arrives with the
conversation since Isaac last spoke (isaac-iv5c). The tools are for reaching
past that.

| repo | suite | result |
| --- | --- | --- |
| isaac-google | `bb spec` / `bb features` | 67 / 0, 23 / 0 |
| isaac-gchat | `bb spec` / `bb features` | 79 / 0, 25 / 0 |
| isaac-gmail | `bb spec` / `bb features` | 46 / 0, 11 / 0 |

main-sha: isaac-google 6c8a29f448bbbb33fb8cfd3eba8b1323b9593c24 (0.1.8)
main-sha: isaac-gchat 5bcaa35f6fdaa69aa3d0b67b0bd42298a367ee13 (0.1.9, google repinned)
main-sha: isaac-gmail 71adceec9fdb49fd1fefd4c26c77fb312c868d6b (0.1.5)

Still for Micah, as the bean says: turning the set on for the yopp crew is a
config change on the host (all of them, per your 2026-09-19 decision), and
whether gws stays installed — these tools do not cover Drive or Calendar.
