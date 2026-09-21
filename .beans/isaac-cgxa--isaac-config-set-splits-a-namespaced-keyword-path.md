---
# isaac-cgxa
title: isaac config set splits a namespaced-keyword path segment (gchat/allow-from) into two nested keys
status: in-progress
type: bug
priority: high
tags:
    - foundation
    - config
created_at: 2026-09-19T23:44:40Z
updated_at: 2026-09-21T03:23:45Z
---

Found 2026-09-19 on yopp: `isaac config set comms.gchat.gchat/allow-from …` and `… comms.gchat.gchat/spaces.spaces/AAQA….respond all` wrote `{:comms {:gchat {:gchat {:allow-from …, :spaces {:spaces {:AAQA… {:respond "all"}}}}}}}` — the / in a namespaced keyword is treated as a path separator — and `config validate` passed, so the stray map sat there silently while the real keys were untouched. Comm extra-schema keys are all namespaced (gchat/…, gmail/…, discord/…), so this affects every comm slot; the feature harness's config table step parses these paths correctly, the CLI does not.

Do: the path parser treats a segment containing / as a namespaced keyword (`gchat/allow-from` → :gchat/allow-from; `spaces/AAQA7rg5Uyc` → :spaces/AAQA7rg5Uyc). Escape hatch if a literal / in a key name is ever needed. Also: a set that would create a key the schema does not know inside a schema'd map (`:gchat` under comms.gchat) should be refused, not written — that is why this stayed invisible.

Scenarios (foundation features/config): set comms.x.x/field writes the namespaced key; set comms.x.x/map.ns/key.field writes nested namespaced keys; get reads them back; a set creating an unknown key under a schema'd map is refused.
