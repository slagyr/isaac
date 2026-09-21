---
# isaac-deds
title: 'One name for the comm berth: :isaac.agent/comm, no fallbacks, wrong name is an error'
status: completed
type: bug
priority: high
tags:
    - comm
    - config
created_at: 2026-09-21T04:25:21Z
updated_at: 2026-09-21T14:57:50Z
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

## Verified (2026-09-21, planner)

All four acceptance criteria hold at current `origin/main` in every repo.
Verification worktrees at `~/agents/isaac/verify-deds` (detached, `origin/main`).

| repo | verified sha | specs | features | GitHub CI Tests |
|------|--------------|-------|----------|-----------------|
| isaac-foundation | e97c51d | 1098 / 0 | 198 / 2 env | success |
| isaac-agent | 510d5b8 | 1682 / 0 | 843 / 0 (1 pending) | success |
| isaac-http | 71a0413 | 189 / 0 | 107 / 0 | success |
| isaac-discord | 8256b22 | 52 / 0 | 68 / 0; JVM 105 / 1 | failure (pre-deds) |
| isaac-imessage | f1cba0a | 41 / 0 | 15 / 0 | success |
| isaac-gmail | 30c5cf1 | 48 / 0 | 13 / 0 | success |
| isaac-gchat | 8ce6fd7 | 84 / 0 | 27 / 0 | success |

### Acceptance

- **all four comms visible to factory, comm-kinds, checks, comm-send** — every
  reader is a single exact lookup: `factory.clj:29`, `comm_kinds.clj:15`,
  `checks.clj:20,53,81`, `comm_send.clj:16`, `http/app.clj:23`,
  `http/module.clj:17`. All four comm manifests declare `:isaac.agent/comm` and
  nothing else. Discord's duplicate key is gone.
- **wrong key fails to load naming module + key** — `retired-berth-messages`
  (`berths.clj:220`) feeds `unknown-berth-error`, raised from
  `validate-contributions!` (`berths.clj:288`), which `discovery.clj:439` folds
  into the load's `:errors` — a hard load error, not a warning.
  `retired_berth_spec` 3/0 covers both retired keys and the error shape.
- **comm-reserved-schema-errors runs for every comm** — `checks.clj:53` reads
  one key; imessage is no longer blind.
- **no `or` fallback on a berth key** — grep across all seven worktrees clean.

### Failures examined, neither attributable to deds

- **foundation features 2/198** — `cli/modules_pins.feature:32,55`. A stale
  `~/.gitlibs` cache entry points at
  `plan/isaac-foundation-berthfix/fixture-agent`, a worktree that no longer
  exists. Local environment only; GitHub CI is green on e97c51d.
- **discord JVM 1/105** — "connects Discord gateway when token is added via
  config hot-reload". Byte-identical at d92b94e (2026-09-19, pre-deds) and at
  8256b22: `105 examples, 1 failures, 240 assertions`, same scenario. Red since
  55f73cd on 2026-09-18. Tracked as isaac-b809.

### Open nit (not blocking)

`isaac-agent/modules/isaac.comm.telly/resources/isaac-manifest.edn:6` still
reads "the :isaac.agent/comm berth declared by isaac-http". isaac-agent declares
it now. Comment only.

### Deployed (2026-09-21, planner) — the rename is live on zanebot

Shipped as one train. zanebot had already drifted past the note above
(agent was 1390334, not 53a1f0e); foundation was still the 9586b08 keg.

| piece | was | now |
|-------|-----|-----|
| foundation (brew HEAD keg) | 9586b08 | e97c51d |
| isaac.agent | 1390334 | a0a4180 |
| isaac.http | 32603e6 | 71a0413 |
| isaac.cron | ba64518 | 19c958f |
| isaac.comm.discord | d92b94e | 8256b22 |
| isaac.comm.imessage | 0422f6d | f1cba0a |

Registry (`isaac/modules.edn` 1df394d) and zanebot's
`~/.isaac/config/isaac.edn` `:modules` moved together; keg via
`brew upgrade --fetch-HEAD slagyr/tap/isaac`; service restarted with
`launchctl kickstart -k gui/<uid>/com.slagyr.isaac`. Config backup left at
`~/.isaac/config/isaac.edn.pre-deds`.

Post-restart: `config validate` → OK; `modules list` → all `ok`;
`lifecycle/started` for both `comms.discord` and `comms.imessage`; a live
discord delivery succeeded (`comm.delivery/delivered id 7469`). No
retired-berth or unknown-berth entries in the log. The only `:level :error`
entries are provider-side (chatgpt 429s, one claude-binary failure),
unrelated to the train.

### The sweep missed isaac-cron (fixed in this train, isaac-cron 19c958f)

`isaac-cron/resources/isaac-manifest.edn:36` still pointed its cron-job
`:comm` validation at `:isaac.http/comm`. It escaped this bean's acceptance
grep because the retired-key load error only fires on top-level
**contribution** keys — a `[:registered-in? <berth>]` reference to a berth
nobody declares just fails at validation time with "unknown berth". It was
latent, not a boot failure: the ref is wrapped in `:nil-or?` and zanebot's
only cron job (`:heartbeat`) sets no `:comm`. The first cron job given a
`:comm` would have hit it.

Collateral the pin jump forced on cron (all pre-existing on its main):

- **isaac-g71i seam again** — cron's `turn-content` never tried
  `[:response :content]`, so a targeted cron job resolved an empty body and
  silently enqueued **no delivery**. Same bug discord and imessage were
  fixed for; cron was missed then too.
- `charge/build`'s `behavior-opts` no longer forwards `:config`, so
  `resolve-behavior` reads the global snapshot — cron's grover spec now
  installs it.
- isaac-agent registers a `"changes to:"` gherkin step that also sweeps
  weather-suspended turns; it collided with cron's identically-phrased
  hot-reload step, which is now `"is rewritten to:"`.
- the two isaac-7ngj failure scenarios encoded old agent behavior:
  "context length exceeded" is now classified as overflow and
  auto-compacted, and an empty terminal reply is retried once with a nudge.
  Both now use inputs the agent still surfaces as failures.

`bb ci` on cron: 23 specs / 0, 22 features / 0; GitHub CI Tests green.

### Not deployed: gmail and gchat

Neither is installed on zanebot, so this bean's "all four comms visible"
acceptance is observable in-repo only — zanebot runs two comms (discord,
imessage) plus acp, which contributes no comm berth. Their registry pins
moved anyway (gmail 30c5cf1, gchat 8ce6fd7): leaving them pre-deds would
hand anyone who installs them a module the new foundation refuses to load.
Standing them up on zanebot is separate work (isaac-google + OAuth/Pub-Sub).
