---
# isaac-dgod
title: 'Token gauge overflow: orchestration-verify reports 12.0M / 278K (4320%) after one compaction'
status: completed
type: bug
priority: normal
created_at: 2026-09-03T00:00:08Z
updated_at: 2026-09-22T21:46:39Z
---

Observed 2026-09-02 on zanebot: `isaac sessions list` shows orchestration-verify (perceptor, gpt-5.4 chatgpt, 327 turns, 1 compaction) at Context 12,031,158 / 278,528 = 4320%. The session file is 1.0M on disk (~250K tokens plausible), so the gauge is not a real prompt size — last-input-tokens (or whatever feeds the PCT column) has gone cumulative or been fed a non-prompt number. Related: isaac-pqjn / isaac-x2up token accounting. Questions: (1) which provider response field seeded 12M — chatgpt usage totals across a stateful chain? (2) does compaction run against this gauge (it would plan chunks off a fictional size) or refuse? (3) is any other session drifting the same way (all other rows look sane today). Reproduce by inspecting orchestration-verify/current.ednl last-input-tokens entries on zanebot before touching the session.

## Mirrored finding from isaac-vuto (2026-09-03)

Accepted finding from `isaac-vuto`: the 12,031,158 stamp on `orchestration-verify` was provider-reported, not sidecar accumulation.

Evidence recorded there:
- `session.edn` contains `:last-input-tokens 12031158`
- `current.ednl` final assistant message contains usage with:
  - `:input-tokens 12031158`
  - `:output-tokens 19146`
  - `:cache-read 11174912`
- therefore the pre-fix turn-end path persisted a provider-reported prompt value verbatim; decision 2's "last, never sum" guard did not create this number
- accepted disposition: cap implausible provider-reported stamps at `context-window` and log `:session/stamp-implausible`


## Disposition superseded (2026-09-20)

The accepted disposition above — clamp implausible stamps at `context-window` and
log `:session/stamp-implausible` — **shipped**, as isaac-agent `4b7c8ac`
("fix token gauge mid-turn stamping"), `normalized-provider-prompt-tokens` at
`turn.clj:225`. It bounds the displayed number and logs a warn. The defect it was
meant to expose is unchanged, and the warn is now routine noise rather than an alarm.

## Second provider, same defect (2026-09-20, claude-opus-5)

`isaac-verify` on zanebot, claude-code provider:

- 27 x `:session/stamp-implausible :prompt-tokens 802832 :context-window 200000`
- transcript at that moment: 68 entries, 144,628 bytes (~36K tokens plausible)
- same turn: `:session/message-stored {:tokens {:input-tokens 3374284 :output-tokens 31056}}`

The 3,374,284 is `reduce add-usage` over `:cycle-usages` in the claude driver — a
correct **turn total**. The 802,832 is the CLI's aggregate usage for the turn.
Neither is a current prompt size.

Compare the original chatgpt case in this bean: `:input-tokens 12031158` with
`:cache-read 11174912`, accumulated across a stateful `:response-id` chain.

Two providers, two accumulation mechanisms, one misreading.

## Root cause

`stamp-provider-prompt!` (isaac-agent `turn.clj:254`) reads a response's
`:prompt-tokens` as "how full is the window now". That holds only for a stateless,
per-request API. It is false for:

- **claude-code** — the CLI reports aggregate usage for the whole turn
- **chatgpt stateful** — usage accumulates across the `:response-id` chain
  (`provider-stateful?`, `turn.clj:240`)

The gauge is not miscalibrated; it is reading a *total* where it needs an
*instantaneous* value. Clamping a total to the window produces a number that is
wrong but plausible-looking, which is worse than one that is obviously wrong —
that is how work-2 and work-3 reached 295% and 253% while still being dispatched to.

## Decision (2026-09-20): adapters report prompt size explicitly

Each llm-api adapter extracts a real current-prompt figure — not a turn total, not
cache-inclusive — and declares when it cannot.

- add an explicit prompt-size field to the adapter contract, distinct from turn usage
- an adapter that cannot supply one says so; the gauge renders **unknown** rather
  than a fabricated number, and writes nothing
- the clamp stays as a backstop, not as the answer
- `:session/stamp-implausible` becomes a real alarm meaning "an adapter is wrong",
  not routine noise

## Acceptance

- a claude-code turn stamps a prompt size that tracks the transcript's actual size
  and is `<=` context-window, without relying on the clamp
- a chatgpt stateful chain of M turns does not accumulate: the stamp after turn M
  reflects turn M's prompt, not the chain total
- an adapter that cannot report prompt size leaves the stamp untouched and the
  gauge renders unknown — no zero is written (see isaac-166j, where a failed
  request's usage zeros reset the gauge)
- `:session/stamp-implausible` does not fire in normal operation on any configured
  provider

## Not in scope

The claude-cli **replay burst** — N identical stamps and N full transcript reads
per turn — is isaac-8cur. That bean owns the frequency; this one owns the value.
Split because they live in different modules with different baselines
(isaac-claude-code vs isaac-agent).

## Decision (2026-09-22, Micah): a stamp above the window is over budget, not implausible

Evidence, zanebot 2026-09-22 13:50–13:57Z, `isaac-work-2` on claude-code /
claude-opus-5 (model entry `:context-window 200000`; the CLI itself runs the
model at its native 1M window and is told nothing about 200k):

- 19 stamps in one turn, 271,329 → 304,187 prompt tokens, growing 1–8k per
  stamp. That is a per-request size, not a running sum — isaac-8cur (replay
  burst) is landed and deployed, so the "claude-code reports a turn total"
  premise above is stale for this provider.
- Every one of the 19 was discarded by `normalized-provider-prompt-tokens`
  (over the window ⇒ report nothing). The gauge fell back to the chars/4
  estimate of transcript entries (~120–130k), under the 0.8 × 200k = 160k
  trigger, so compaction never fired while the real request was ~290k.
- Three workers cycling at ~290k each closed the seat's 5-hour window in
  ~17 minutes.

Rule: a provider stamp larger than the configured window means the working
context is **over budget**. It is the loudest possible compaction trigger, not
a value to throw away. Keep `:context-window` as the working budget (200k on
zanebot stays); stop treating a stamp above it as impossible.

### Acceptance (supersedes the clamp/discard backstop above)

- A per-request stamp above `:context-window` is recorded as the gauge value
  and `should-compact?` is true on the next check; compaction runs before the
  next request. `:session/stamp-implausible` is not logged for this case.
- A stamp that is a running sum (chatgpt stateful chain; any adapter that
  still reports turn totals) is still rejected and still warns — the adapter
  contract distinguishes per-request prompt size from turn usage, as decided
  2026-09-20.
- The chars/4 estimate is the fallback when an adapter reports no per-request
  size, never the primary when one is available.
- One-time on zanebot after deploy: a work turn whose first stamp exceeds 200k
  compacts within that turn; the following stamps are below 160k.

## Evidence update and worker brief (2026-09-22 evening, planner; Micah: GO)

Corrections to the text above, from measurements taken today:

1. **claude-code stamps are per request now, not turn totals.** isaac-8cur
   landed (claude-code 34dbfa7 deployed): the driver fires one cycle per
   `tool_use` block and each cycle carries the usage of the assistant event that
   held the block. That usage is per API request. The one wrinkle: when a
   model message carries several `tool_use` blocks, those cycles share one
   usage (identical prompt-tokens). Treat a repeated identical stamp inside a
   turn as the same request, not a new one. The 19 stamps on isaac-work-2 this
   morning had distinct values, so they were 19 requests.
2. **The discard is what blinded the gauge.** Every real stamp (271k–304k)
   was over the 200k window → dropped → gauge fell back to the chars/4 tally
   (~125k) → never crossed 0.8 × 200k → no compaction while the real request
   was ~290k. Three workers closed the org seat's 5h window in 17 min, twice
   today.
3. **Fresh-session floor on the claude-code lane is ~55k per request.**
   pn98-opus-personal-2013 (3 file reads, fresh session, reset mode) spent
   prompt 293,046 (cache-read 153,042, cache-write 139,982) over 4–5
   requests. Cache-write ≈ cache-read within one turn is not a clean prefix
   chain. Out of scope here but note anything you learn.
4. **Zanebot's `:context-window` for claude-opus-5 stays 200000** as the
   working budget; the CLI runs the model at 1M and is told nothing.

Rules to implement (decided 2026-09-22):

- A per-request stamp **above** `:context-window` is written to the gauge as
  is and makes `should-compact?` true on the next check; compaction runs before
  the next request. No `:session/stamp-implausible` for this case.
- The adapter contract distinguishes **per-request prompt size** from **turn
  usage**. Stateless adapters (chat-completions, messages, ollama, grover,
  claude-code via the sibling repo) already report per-request; the Responses
  API stateful `:response-id` chain (`responses.clj`, `provider-stateful?` in
  turn.clj) reports a running sum and must either extract the per-request
  figure or declare none.
- An adapter that declares none leaves the stamp untouched and the gauge
  renders **unknown**; no zero is written (isaac-166j). The chars/4 tally is
  the fallback only when no per-request figure exists.
- `:session/stamp-implausible` remains only for the running-sum case (an
  adapter that still reports totals) and means "adapter bug".

Worker constraints (local Opus subagent on the planner's box, not zanebot):

- Worktree `isaac-agent-isaac-dgod` on `bean/isaac-dgod` from main d2db8c7.
  Never touch the shared `isaac-agent` checkout. TDD: failing spec first.
  `bb lint` after each edit, `bb spec`, then the features that cover
  compaction/gauge (`bb features features/session/`) and anything the
  changed namespaces are exercised by. `bb ci` before handoff.
- Files in play: `src/isaac/drive/turn.clj` (`normalized-provider-prompt-tokens`,
  `stamp-provider-prompt!`, `provider-stateful?`), `src/isaac/session/compaction.clj`
  (`context-gauge`, `should-compact?`), `src/isaac/llm/api/protocol.clj` +
  `responses.clj` (contract field), `spec/…` for each, CHANGELOG `## Unreleased`.
- isaac-claude-code: change only if the contract forces it; if so, a worktree
  there with `:dev-local` pointing at the agent worktree, and say so in the
  handoff. Do not bump any pins.
- One squash-style commit on the bean branch, pushed. Do NOT push to main and
  do NOT open the PR: the planner does. Hand off with `## Handoff` on this
  bean: what changed, test results (command + counts), open questions. Bean
  stays `in-progress`; the planner adds `unverified`.

## Handoff (worker, 2026-09-22)

Branch `bean/isaac-dgod` in isaac-agent, one commit `98e1a96` on top of main
`d2db8c7` (force-pushed over the superseded `9a9bfed`, as briefed). No pins
touched, isaac-claude-code untouched — the contract did not force it.

### What changed

- `src/isaac/drive/turn.clj` — `normalized-provider-prompt-tokens` (clamp/discard)
  replaced by `prompt-scope` + `provider-prompt-tokens`: the declared scope
  decides. `:request` → the figure is written as it stands, window or no window.
  `:running-sum` → nothing written, `:session/stamp-implausible` warns.
  `:unknown` → nothing written, silent. `provider-stateful?` moved above them.
- `src/isaac/llm/api/protocol.clj` — `prompt-scopes` (`#{:request :running-sum
  :unknown}`) and an optional `:prompt-scope` on the `usage` schema. Absent
  means `:request`, so every stateless adapter (chat-completions, messages,
  ollama, grover, claude-code in the sibling repo) is already correct with no
  change. An undeclared scope on a **stateful** provider is inferred
  `:running-sum` — that is the only way the warn fires now, and it means an
  adapter that has not been taught the contract.
- `src/isaac/llm/api/responses.clj` — `stateful-request?` / `chained-request?`
  extracted from `->responses-request`; the result's usage declares
  `:prompt-scope :unknown` on a chained `previous_response_id` request (the
  chain is billed cumulatively, so the figure is a running sum) and `:request`
  otherwise. It declares rather than infers, so the first request of a chain —
  which does carry a real per-request figure — is not thrown away.
- `src/isaac/llm/tool_loop.clj` — `response-usage` drops `:prompt-scope` before
  the turn total. Found the hard way: `add-usage` is `merge-with +`, so the
  second cycle of any stateful turn did `(+ :request :request)` and killed the
  turn. Three feature scenarios caught it; see below.
- `spec/isaac/drive/turn_spec.clj` — four new `process-response!` examples:
  over-budget stamp written + `should-compact?` true + no warn; `:unknown`
  leaves the tally; undeclared-on-stateful warns and leaves the tally; declared
  `:request` on a stateful provider is trusted.
- `spec/isaac/llm/responses_spec.clj` — two new `chat` examples: unchained
  declares `:request` with the real figure, chained declares `:unknown`.
- `spec/isaac/llm/tool_loop_spec.clj` — one new example pinning `:prompt-scope`
  out of the turn total.
- `features/llm/api/response_schema.feature` — **scenario updated** (see below).
- `features/session/compaction_overflow.feature` — new scenario "a prompt above
  the window compacts before the next request (isaac-dgod)": a 1400-token stamp
  against a 1000 window is recorded, and the next send compacts before the
  request. Would have failed before the change (the stamp was discarded and
  `last-input-tokens` stayed 0).
- `CHANGELOG.md` — one entry under `## Unreleased` naming isaac-dgod.

### Scenario changed, and why

`features/llm/api/response_schema.feature`, "a prompt size larger than the
window leaves the gauge alone instead of clamping to the window (isaac-dgod)"
pinned the behaviour this bean reverses: it asserted the 900000 stamp was
discarded (`last-input-tokens` stayed 1000) and that
`:session/stamp-implausible` fired. Renamed to "...is over budget, and the
gauge takes it as it stands (isaac-dgod)" and flipped to assert
`last-input-tokens 900000` and **no** `:session/stamp-implausible` entry. The
narrative paragraph now carries the isaac-work-2 measurements. Nothing was
deleted or weakened; the running-sum alarm it used to guard is now covered by
the turn_spec example and by the responses adapter's `:unknown` declaration.

### Test commands and results

| command | result |
|---|---|
| `bb lint <each edited file>` | 0 errors (pre-existing warnings only: unused requires/vars in `turn.clj`, `:refer :all` kondo noise in `responses_spec.clj`) |
| `bb spec` | **1710 examples, 0 failures, 3566 assertions** |
| `bb features features/session/` | **847 examples, 0 failures, 2036 assertions, 1 pending** (the path argument does not narrow — `bb features` always runs the whole suite) |
| `bb features features/llm/api/response_schema.feature` | same full run, 0 failures |
| `bb ci` | config-bypass-lint ok, lint-cli-host ok, 1710 spec / 847 feature examples, 0 failures |

The 1 pending is pre-existing (`compaction_mid_turn` rubberband hail, not yet
implemented). Baseline on `d2db8c7` was 846 features / 0 failures; the extra
example is the new compaction scenario.

### Out of scope, noticed

- **cache-write vs cache-read on the claude-code lane.** Nothing in this repo
  reads them apart from `extract-tokens` → session totals, and the messages
  adapter's `parse-usage` folds both into `:prompt-tokens`
  (`input + cache_read + cache_write`). That fold is *correct* for a prompt size
  — all three describe bytes in the request — so the 293,046 figure from
  pn98-opus-personal-2013 (cache-read 153,042, cache-write 139,982) is a real
  prompt, not double counting. What it says is that the lane re-writes nearly
  the whole prefix each request instead of reading it back, which is a
  prompt-construction or cache-breakpoint question in isaac-claude-code, not a
  gauge question. Untouched here.
- `bb features <path>` ignores the path and runs everything. Worth a note
  somewhere; every "narrow the suite" instruction is silently a full run.

### Open questions

1. **Can the Responses chain be made to report per request?** I took the
   "declare none" branch the bean allows, because nothing in the repo or in the
   captured wire responses shows a per-request input figure on a chained
   `response.completed`. If OpenAI does report one (or if `input_tokens` on a
   chained response is in fact per request and the 12,031,158 was something
   else), flipping `chained-request?` to `:request` is a one-line change. A
   live capture from zanebot's chatgpt lane would settle it.
2. **Should stateless adapters declare `:request` explicitly?** Today absent
   means `:request`, which is what keeps isaac-claude-code and the four in-repo
   adapters working untouched. The cost is that a future stateless adapter that
   *does* report a turn total looks correct. The inference only covers stateful
   providers.
3. **Zanebot verification.** The bean's one-time check — a work turn whose first
   stamp exceeds 200k compacts within that turn, following stamps below 160k —
   is not something I can run; no ssh from here.

## Planner check (2026-09-22)

Reran on bean/isaac-dgod 98e1a96: `bb spec` 1710/0, `bb features` 847/0 (1 pending, pre-existing). Diff reviewed. PR opened from `bean/isaac-dgod` to isaac-agent main; tagged `unverified` for /verify. Open item from the worker: the Responses chain declares `:unknown` on chained requests — a live chatgpt capture could show a per-request figure and flip it in one line. One-time zanebot check (first stamp over 200k compacts within the turn) still pending deploy.

## Deployed and tried on zanebot (2026-09-22 20:59–21:04Z, planner)

PR slagyr/isaac-agent#3 squash-merged → main 1afd3dc. Deployed as
`hotfix/isaac-dgod-agent-0.1.81` @ 831c5cf (= 0.1.80 hotfix e0ced4d + this
commit; main's continuations stay undeployed while scrapper/perceptor run
`:context-mode :reset`). Registry isaac 547cc7d2. Boot clean (6 requeued, 0
dropped, Discord ready +2s).

One-time check, session isaac-work-2 (826 KB transcript), temporary model
`dgod-trial` = grok-4.7 with `:context-window 200000`, prompt "Reply with the
single word pong." three times with `--with-crew main --with-context-mode full`:

| turn | pre-request gauge | compaction | stamp after |
| --- | --- | --- | --- |
| 1 (scrapper, reset won — see note) | 127,665 | skipped (:context-reset) | 5,827 |
| 2 (crew main, full) | 5,836 | none | **242,134** recorded as-is, no warn |
| 3 (crew main, full) | **242,143** | started 21:01:20, completed 21:03:38 (count 102→103) | **15,028** |

`:session/stamp-implausible` count since deploy: 0. Acceptance's one-time line
is met: a stamp over 200k is recorded and compacts before the next request.

Two observations, not blockers:
- The chars/4 `tokens-before` estimate read 127,536 while the provider said
  242,134 — the fallback undercounts a code/EDN-heavy transcript by ~1.9×.
- `--with-context-mode full` alone did not override scrapper's `:context-mode
  :reset` (behavior-resolved still said :reset); `--with-crew main` did. Filed
  separately.

Trial model entry removed after the run. Bean stays `unverified` for /verify.


## Verify pass (2026-09-22)

HEAD checked: isaac-agent 1afd3dc (on origin/main), fresh detached worktree.
No `## Exceptions` section on this bean.

- `bb spec`: 1710 examples, 0 failures (matches worker/planner-reported count)
- `bb features`: 847 examples, 0 failures, 1 pending (pre-existing "Mid-turn compaction keeps the request in flight" — matches worker/planner-reported count exactly)
- Clean output: only the project's structured JSON log lines; no stray println
- `features/llm/api/response_schema.feature` scenario change (dae97fa -> 1afd3dc) reviewed: it flips the pinned behaviour from "clamp/discard a stamp above the window" to "record it and compact." This is the bean's own 2026-09-22 decision (Micah: GO), fully explained in the handoff's "Scenario changed, and why" section — not an undocumented weakening. Nothing deleted; the running-sum alarm it used to guard is now covered by `turn_spec.clj` and the responses adapter's `:unknown` declaration.
- `features/session/compaction_overflow.feature` change is a clean additive scenario, no existing scenario touched.
- Acceptance met: a per-request stamp above `:context-window` is recorded and triggers `should-compact?`; `:session/stamp-implausible` reserved for running-sum only; chained Responses requests declare `:unknown` (no fabricated number); the one-time zanebot check (first stamp over 200k compacts within the turn, following stamps below 160k) was already run and recorded in the bean by the planner on 2026-09-22 20:59-21:04Z with `:session/stamp-implausible` count 0 since deploy.
- No smell-pattern hits in the diff's spec files (`turn_spec.clj`, `responses_spec.clj`, `tool_loop_spec.clj`); `System/currentTimeMillis` in `responses_spec.clj` is stubbed OAuth-token fixture setup, not production hidden-time-dependence.



main-sha: isaac-agent 1afd3dc
