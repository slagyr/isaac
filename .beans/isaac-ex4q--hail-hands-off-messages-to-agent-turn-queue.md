---
# isaac-ex4q
title: Hail hands off messages to Agent turn queue
status: in-progress
type: feature
priority: normal
created_at: 2026-09-27T22:33:12Z
updated_at: 2026-09-28T19:16:31Z
parent: isaac-q3u3
blocked_by:
    - isaac-l3vb
    - isaac-70cr
    - isaac-5gu1
    - isaac-d6pw
---

Likely repos: **isaac-hail** and **isaac-agent**. Design: Micah + planner, 2026-09-27. This is the Hail half formerly bundled into isaac-l3vb.

## Contract to plan

- Hail remains the out-of-band message surface: send tool, HTTP/CLI ingress, band naming and prompt/data expansion, `reply_to` threading, message IDs, and message receipts.
- Once a message resolves to a turn request, submit it to Agent using the hail ID as an idempotency/source key. Record the Agent request ID only after Agent durably accepts it. Hail's receipt means accepted for delivery, not that the turn completed.
- Agent owns session candidate selection, capacity waiting, pool leases, charge construction, turn starts, and recovery. Remove Hail's independent delivery polling/binding/retry policy for these conditions. Hail must not call the drive directly.
- **"Hails never die" moves to Agent.** Infrastructure-failure deferral with attention and auto-resume on recovery is now Agent's (isaac-70cr). Hail drops its own deferral/attention path for turn failures; it may relay Agent's attention observation to a human or to `reply_to`, but it never re-submits or retries. Dead-letter stays for poison messages only.
- Preserve direct-session and band addressing plus undeliverable-message diagnosis. On restart at either side of the handoff, one hail produces at most one Agent request.

## Scenario plan to review

1. A band hail expands its prompt and submits one Agent request preserving hail/thread identity.
2. Busy session or pool remains in Agent's queue; Hail has no second capacity wait.
3. A crash/retry at the handoff does not create duplicate requests or turns.
4. A provider auth failure parks the request in Agent with attention; Hail neither retries nor dead-letters it, and the turn runs on recovery.
5. A direct-session hail and an undeliverable address retain their expected message outcomes.

Draft until scenarios are committed and baselined. Foreman's ordinary `:turn` action uses Agent directly, without a Foreman-to-Hail dependency.


## Decisions (2026-09-27, Micah + planner)

1. **Fan-out is killed** (Micah): no `:reach :all`, and the `:reach` key is removed everywhere in isaac-5gu1, which lands first. This bean drops Hail's broadcast path with the rest of delivery.
2. **Hail tracks outcomes with its own turn observer** (Micah: "makes a lot of sense"). Lifecycle in `hail_get`: pending → submitted (Agent request id) → delivered | failed (from the observer) | undeliverable (Agent refused).
3. **Submit = queue.** At send time, after the hail record is persisted, Hail expands the band and submits the turn request to Agent's queue keyed by hail id. Agent queues it; it never runs inline, so a crew's `hail-send` never runs another session's turn inside its own tool call. A server-start sweep submits any persisted hail without a request id.
4. **Retries belong to the drive** (Micah). Hail never retries a turn. Dead-letter stays only for poison (unparseable/invalid) hails.
5. **Deploy:** drain in-flight hails on zanebot before cutover; `hail/deliveries` goes away.
6. **One bean** — splitting would leave two delivery paths.

## Scenario survey (2026-09-27)

Of 160 Hail scenarios: 71 keep, 20 rewrite ("the hail delivery worker ticks" → "the turn queue ticks at …"; the turn still runs, so assertions mostly stand), 69 removed as Agent-owned delivery (session selection, bind, busy deferral, retry/dead-letter, weather, marker claim/resume, context-window guard, slash commands) — Agent has its own scenarios for these. `delivery_worker.clj` and the selection half of `router.clj` are deleted.

- Remove: bound_unclaimed (all 7), commands (1), context_window_guard (3), dead_letter_resurrection (3), delivery (23 of 25; keep+rewrite :354, :384), explicit-session-routing (4), hail-band-prompts :114, router (15; keep :238, :273, :424), session-create (6), turn-marker-claim (3), turn-resume (3).
- Rewrite: band-inheritance :21; delivery :354, :384; hail-band-data :11, :49, :101; hail-band-prompts :30; hail-get :91, :114; hail-metadata (all 6); hail-naming :40; hail-threading :31, :71; http :58, :161.


## Redesign (2026-09-27, Micah) — supersedes Decisions 2–3 above and the survey verdicts

**Hail is stateless.** A send is: expand the band → submit to Agent → return **Agent's turn id**. There is no hail id separate from the turn id.

- **No Hail records.** The `hail/` directories (pending, deliveries, delivered, failed, undeliverable, broadcasts, records), the naming strategies (sequential/uuid), `hail_get`, the delivery worker, the router's selection, and the crash sweep are all removed. Agent's queue is durable at acceptance, so nothing needs sweeping.
- **Origin rides the turn:** Hail passes `{:source :hail :from … :principal … :thread-id … :reply-to … :params … :data …}` as the turn's opaque origin (isaac-d6pw).
- **Inspection is Agent's:** `isaac turns show <id>` and the `turn_get` crew tool (isaac-d6pw) replace `hail_get`. Orchestration prompts/skills that call `hail_get` switch to `turn_get` in the same deploy.
- **Threading:** `reply-to` names a turn id; Hail reads that turn's origin via Agent to inherit its `thread-id`.
- **The hail preamble shows the turn id** where it showed the hail id.
- **Undeliverable is refused at send** (Micah agreed): Agent refuses at submission when nothing could ever match (unknown band → Hail refuses before submitting; no selector; missing explicit session or no matching session with `:create :never`). The sender gets the error immediately — CLI exit 1, tool error, HTTP 4xx — and nothing is queued. Busy matches wait in Agent's queue.
- **Retried sends:** optional caller-supplied `idempotency-key` on send (CLI flag, HTTP body, tool arg), passed to Agent as the submission key. No key → every send is a new turn.
- **Retention:** finished turns kept indefinitely for now (isaac-d6pw).
- **Fan-out scenarios belong to isaac-5gu1**, which lands first: bands:34, delivery:222, explicit-session-routing:37, hail-get:67, hail-get:79, hail-naming:40, router:142, router:313.

The keep/rewrite/remove survey above assumed Hail kept records; it must be redone against this design before scenarios are drafted.


## Survey + decisions (2026-09-28, Micah + planner) — supersede the 09-27 survey above

Survey against the stateless design (Hail on origin/main 2026-09-28): ~20 scenarios keep as-is (band config validation, pre-submit send refusals, HTTP 400s); ~55 are removed (bound_unclaimed, most of delivery, dead_letter_resurrection, context_window_guard, all of hail-get and hail-naming, turn-marker-claim, turn-resume, router frozen-candidates/scheduler/quarantine, hail-metadata reach-one binding); ~75 are rewritten (band inheritance/data/prompts/metadata/threading, send, http, crew-tool, router selector + undeliverable, explicit-session-routing, session-create, send-addressing record checks, delivery `--with-model` and band `cycle`).

Surfaces: keep `hail send`, the `hail-send` tool, `POST /hail/send`. `hail show` → `isaac turns show`; `hail drop` → `isaac turns drop`; `hail-get` tool → `turn__get` (isaac-d6pw). Delete `hail requeue`, the HailRuntime component, the `hail/route` and `hail/deliver` scheduler tasks, and the router / delivery-worker / attention / store namespaces. Keep bands, band-resolve, templates.

1. **Preamble rides the submission.** Hail builds the metadata preamble at send time and passes it as a generic `:preamble` string on the turn request; Agent adds it to that turn's system prompt without knowing it came from Hail (small isaac-agent change in this bean; the drive stays generic).
2. **No delivered-session line in the preamble** — for crew/tag targets the session is chosen at admission, and identity is ambient (isaac-sx4g).
3. **Session creation is Agent's.** Hail passes `:create` with the frequencies; Agent applies it at admission (isaac-l3vb).
4. **Output:** `hail send` prints the turn id; `--edn` / `--json` print the turn id plus what was submitted. `sent-at` becomes the turn's `created-at`.
5. **One new Hail step does most of the rewriting:** `the turn Hail submitted has:` (path/value table over the newest Agent turn record). Mechanical rewrite recipe: drop router/worker ticks; "pending hail EDN contains" → "the turn Hail submitted has"; `hail_get` → `turn__get` / `turns show`; scenarios that check a real turn's transcript get `the turn queue ticks at`. "no pending hails" → `turns list --all` prints nothing.
6. **One bean** (two would leave Hail half-migrated). Micah reviews the pattern — the new step, ~5 new scenarios, 3 representative rewrites — then the planner applies it to the rest.


## Acceptance

Features on isaac-hail main at 5a5e517 (`@wip` per scenario). Remove every `@wip`; all pass:

- [ ] `bb features features/handoff.feature` — 5 new scenarios: submit + turn id, busy target waits in Agent, refusals at send, reply threading through turn ids, idempotency key
- [ ] `bb features` — every rewritten scenario in band-inheritance, commands, crew-tool, delivery (3 left: `--with-model`, band `cycle.limit`, band `cycle` map), explicit-session-routing, hail-band-data, hail-band-prompts, hail-metadata, hail-threading, http, router (retitled "Hail addressing"), send, send-addressing, session-create
- [ ] New step `the turn Hail submitted has:` (path/value over the newest Agent turn record). Delete the removed Hail steps (router/worker ticks, pending/delivery hail EDN, hail_get, broadcast/child, marker/claim, dir-scan, bare hail id).
- [ ] Already done by the planner (5a5e517): deleted bound_unclaimed, context_window_guard, dead_letter_resurrection, hail-get, hail-naming, turn-marker-claim, turn-resume features and the removed scenarios in delivery, router, hail-metadata.
- [ ] isaac-agent: turn requests accept a generic `:preamble` string, added to that turn's system prompt (the drive stays generic); spec + a scenario-free unit spec in agent.
- [ ] Surfaces: `hail show` / `hail drop` / `hail requeue` subcommands and the `hail-get` tool removed (replaced by `turns show` / `turns drop` / `turn__get`); `--idempotency-key` on `hail send`, `idempotency-key` on HTTP and the tool.
- [ ] One-time checks: no `hail/` directory is created by any send; `git grep` finds no `delivery_worker`, `router` scheduler task, `hail.store`, or `hail.attention` in isaac-hail src.
- [ ] Orchestration prompts/skills that call `hail_get` or `isaac hail show` switch to `turn__get` / `isaac turns show` in the same deploy.
- [ ] Deploy note: drain in-flight hails on zanebot before cutover.
- [ ] `bb verify` green in isaac-hail and isaac-agent; version bumps; repin; modules.edn registry.

feature-baseline: isaac-hail 5a5e51720d114dbd5c5b11e7c37f6ac2895628ab
feature-blob: isaac-hail features/band-inheritance.feature 2ee5eabf31cb89fd04c33d8b2390fb2ac1ba2e34
feature-blob: isaac-hail features/explicit-session-routing.feature cd96c66544440d4a227a21ab84491e7d8db8251c
feature-blob: isaac-hail features/delivery.feature 5e21ad17308d321a015eafd3c0e9452a0e19a4a5
feature-blob: isaac-hail features/commands.feature eb3b4309d8b2a473fdce6fc5c96fc938e3cccc3c
feature-blob: isaac-hail features/crew-tool.feature 8c5b786578e74b8b3dafa470b8387bf73e449497
feature-blob: isaac-hail features/hail-threading.feature a0b3084754dbc292001a45d32167d39f28b28d23
feature-blob: isaac-hail features/hail-band-data.feature c3254b8c2d18b08350c172bcb372cc4ec66d1c0d
feature-blob: isaac-hail features/hail-metadata.feature 868055a9bb2df3f4031a11b285e948b4c8781753
feature-blob: isaac-hail features/send-addressing.feature c864ab9677e09fa2943e01d8c81d820fe07693a2
feature-blob: isaac-hail features/handoff.feature f53f61268fd18901791d3944db736ccf9f17bb7b
feature-blob: isaac-hail features/router.feature 569458809792d9b357a7c37fd9fb4ac84c4e5027
feature-blob: isaac-hail features/http.feature 4c125aaf70f0824b8063c7f4455ccb4d622f1ba8
feature-blob: isaac-hail features/hail-band-prompts.feature 6d797c2aeaa90d26a2981aa611974189194d1af7
feature-blob: isaac-hail features/send.feature 3a1e7aa1cf37e94ec8adf45f582e76ad279f929e
feature-blob: isaac-hail features/session-create.feature 1ede1ac7c2bfffd75046e61587534671c70f1b88

## Work checkpoint (2026-09-28, scrapper@isaac-work-1)

Done: Agent generic per-turn preamble propagated through durable submit, worker, charge and provider prompt; caller-provided id accepted atomically. Agent focused specs green, pushed bean/isaac-ex4q at dfb7ce4. Hail stateless queue submission with band expansion, origin and reply threading started; focused queue_handoff_spec green, pushed bean/isaac-ex4q at b2c81fd.
Next: implement CLI/HTTP/tool idempotency and refusal handling, remove old Hail runtime/steps and migrate all @wip scenarios; integrate with Agent branch locally and run bb verify. Resume at isaac-hail-ex4q/src/isaac/hail/cli.clj:201 and isaac-hail-ex4q/src/isaac/hail/queue.clj:62. Agent branch is isaac-agent-ex4q. The last focused tests are green; full suites not yet run. Do not land until acceptance and gate pass.
