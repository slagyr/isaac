---
# isaac-deds
title: 'One name for the comm berth: :isaac.agent/comm, no fallbacks, wrong name is an error'
status: in-progress
type: bug
priority: high
tags:
    - unverified
    - comm
    - config
created_at: 2026-09-21T04:25:21Z
updated_at: 2026-09-21T14:15:50Z
---

The comm berth has three names in circulation. Every reader accepts a different
pair, and **no reader sees all four comms**.

## Who declares what

| module | manifest key |
| --- | --- |
| isaac-discord | `:isaac.agent/comm` **and** `:isaac.http/comm` (both) |
| isaac-imessage | `:isaac.server/comm` |
| isaac-gmail | `:isaac.http/comm` |
| isaac-gchat | `:isaac.http/comm` |

## Who reads what

| reader | primary | fallback | blind to |
| --- | --- | --- | --- |
| `isaac.comm.factory/manifest-comm-contribution` (foundation :29) | `:isaac.server/comm` | `:isaac.agent/comm` | gchat, gmail |
| `isaac.config.comm-kinds/comm-kinds` (foundation :14) | `:isaac.server/comm` | `:isaac.agent/comm` | gchat, gmail |
| `isaac.config.checks` (agent :20, :53, :82) | `:isaac.http/comm` | `:isaac.agent/comm` | imessage |
| `isaac.tool.comm-send` (agent :16) | `:isaac.http/comm` | **none** | imessage |

Consequences already visible:

- the comm **factory** cannot find gchat's or gmail's contribution — discord only
  survives it by declaring two keys
- `comm-reserved-schema-errors` (the check that refuses `:type` as a comm field)
  never runs for imessage
- `comm-kinds`' primary lookup matches nothing any active comm declares; it
  survives on its fallback alone

## Decision (Micah, 2026-09-21)

**`:isaac.agent/comm` is the name.** isaac-agent owns `isaac.comm.protocol/Comm`
— the interface every comm implements — and comm callbacks are emitted by the
turn pipeline (`isaac.drive.turn`). That is agent territory.

`:isaac.http/comm` names the berth after one implementation's transport. HTTP is
not the door for comms generally: Discord is a websocket gateway, iMessage is
local BlueBubbles, ACP is stdio, the memory comm has no door. Only the Pub/Sub
push comms (gchat, gmail) arrive over HTTP.

**One name. No fallbacks.** A manifest declaring a comm under any other key is an
**error**, named and refused at load — not silently skipped, and not quietly
accepted by whichever reader happens to match.

## Work

- rename every declaration to `:isaac.agent/comm` (discord drops its duplicate)
- isaac-agent declares the berth, since it owns the protocol
- every reader looks up exactly one key; delete all `or` fallbacks
- an unknown `:isaac.*/comm` key in a manifest is a load error naming the module
  and the key
- fix the factory docstring, which still says ":isaac.server/comm config berth"

## Acceptance

- all four comms are visible to the factory, comm-kinds, checks and comm-send
- a manifest declaring `:isaac.http/comm` or `:isaac.server/comm` fails to load
  with an error naming the module and the offending key
- `comm-reserved-schema-errors` runs for every comm, imessage included
- no `or` fallback on a berth key remains in either repo

## Work log (2026-09-21, work-2 local, GLM-5.3)

One coordinated train, all pushed, all suites green:

| repo | sha | suites |
|------|-----|--------|
| isaac-foundation | 8fbeed3 | 1085 specs / 0; features 198 (2 pre-existing env failures: git-fixture path from another checkout) |
| isaac-agent | 510d5b8 | 1682 specs / 0; 843 features / 0 |
| isaac-http | 71a0413 | 189 specs / 0; 107 features / 0 |
| isaac-discord | 8256b22 | 52 specs / 0 (native); 68 features / 0; JVM suite 105/1 — pre-existing red, see isaac-b809 |
| isaac-imessage | f1cba0a | 41 specs / 0; 15 features / 0 |
| isaac-gmail | 30c5cf1 | 48 specs / 0; 13 features / 0 |
| isaac-gchat | 8ce6fd7 | 84 specs / 0; 27 features / 0 |

- foundation: factory + comm-kinds read `:isaac.agent/comm` only;
  retired-key load errors for `:isaac.http/comm` / `:isaac.server/comm`
  via the existing retired-berth-messages mechanism; registered-in
  docstring examples updated.
- agent: declares the `:isaac.agent/comm` berth (register/deregister fns
  moved from http's declaration); checks + comm-send single-key;
  test-resources stand-in berth deleted (real manifest owns it now).
- isaac-http: berth declaration dropped; `:comms` slot gathers
  `:isaac.agent/comm`; module.clj/app.clj single-key (the `:isaac.server/comm`
  fallbacks there were dead already).
- comms ×4: manifests on `:isaac.agent/comm`; pins bumped (agent 510d5b8,
  foundation 8fbeed3, http 71a0413 — the comms' test aliases pin http
  directly; stale ones silently strip comm extra-schema fields).

### Discovery: isaac-server is isaac-http

`slagyr/isaac-server` redirects to `isaac-http` (the 3q4m rename
completed); pre-rename fossil checkouts still exist in work dirs and
carry `:isaac.server/comm` readers that no longer exist anywhere live.
Sweep edited only the live repo. Consider deleting stale
isaac-server checkouts from work dirs.

### Collateral fixes forced by the pin jumps (all pre-existing on the comms' mains)

- **isaac-g71i response seam**: discord's `result-content` and
  imessage's `result->reply-text` never tried `[:response :content]`
  (the normalized provider-response shape) — replies arrived empty.
  Discord's splitting feature had been red since Sep 18 on this.
- **55f73cd** (cap sends at two chunks): discord splitting.feature's
  first scenario expected three POSTs; updated to the two-chunk
  contract with a comment.
- **Retired `:server` keys**: imessage's lifecycle setup wrote
  `server.hot-reload`; now `hot-reload` (isaac-tdlz retirement).
- imessage's http pin (11e43014, pre-bbe0) masked all of the above.

### Acceptance mapping

- all four comms visible to factory, comm-kinds, checks, comm-send —
  single-key reads + green suites in every repo above.
- wrong key fails to load naming module + key —
  foundation retired_berth_spec (both retired keys), error shape
  `module-index["<id>"][:isaac.http/comm]` → ":isaac.http/comm is
  retired; use :isaac.agent/comm".
- comm-reserved-schema-errors runs for every comm — checks.clj reads
  one key; imessage covered by agent checks_spec fixtures (no longer
  blind).
- no `or` fallback on a berth key remains — grep across foundation,
  agent, http, and the four comms is clean (isaac-server fossils aside).

### Follow-ups filed

- isaac-b809 — discord JVM hot-reload spec, red since okw1 (pre-deds).

### Deploy note

Train SHAs above need the deploy-train pin bump (isaac monolith pins /
homebrew) the way uxe1's completion did ("deploy train: agent 4cd20fc").
zanebot runs isaac.agent@53a1f0e + foundation@9586b08 + http pins older
still — the comm berth rename is **not live** until that train ships;
until then, deployed manifests still declare `:isaac.http/comm`, which
the deployed (old) readers still accept. Do not bump the deploy pins
piecemeal: foundation/agent/http/comms must move together or comms go
blind at boot.
