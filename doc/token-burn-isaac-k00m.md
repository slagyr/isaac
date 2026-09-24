# Where 35.6M tokens went on one bean

Measured 2026-09-24, on zanebot, for bean **isaac-k00m** (make the iMessage
comm outbound-only). Written down because the number is surprising, because a
chunk of it is still unexplained, and because the unexplained part is the
actionable finding.

## The raw measurement

Isaac records per-turn usage in the session transcript:
`~/.isaac/sessions/scrapper/isaac-work-1/current.ednl`. The record stamped
**19:21:26**, matching the hail's `:outcome :delivered` exactly:

```clojure
:usage {:prompt-tokens      35473023
        :output-tokens         83571
        :total-tokens       35556594
        :cache-read-tokens  34834966
        :cache-write-tokens   637409}
```

These are **per-turn, not cumulative** — the series across this session's
recent turns is 2.98M, 4.13M, 6.69M, 3.11M, 3.59M, then this one. Not
monotonic, so each record stands alone.

Derived:

| quantity | tokens |
|---|---|
| total | 35,556,594 |
| cache reads | 34,834,966 |
| cache writes | 637,409 |
| output | 83,571 |
| genuinely fresh input | **648** |
| non-cache-read subtotal | **721,628** |

Fresh input is `prompt − cache_read − cache_write` = 648. Practically
everything sent was either already cached or being written to cache.

## Wall clock and shape

- Dispatched 19:09:12, delivered 19:21:26 — **12m14s**
- **99 assistant entries** in the turn (≈ 99 model requests), 95 tool results,
  5 user entries
- Cycle limit is 250, so it finished well inside budget
- 34,834,966 cache reads ÷ 99 requests = **~351,868 average context per
  request**

## What that average is *not*

The turn's own content is far smaller than the context it was sent with:

- transcript bytes for the whole turn: **347,927** (≈ 87k tokens at ~4 B/tok)
- Isaac's own `:tokens` fields for the turn sum to **145,260**
- largest single entries are ~20KB (file reads, suite output) — nothing
  pathological

Growing 0 → ~145k over 99 requests averages ~72k. Measured average prompt was
~358k. **So roughly 285k per request is something other than this bean's
work**, paid 99 times ≈ 28M of the 35M.

It is also not stale conversation history, which was my first guess.
`:context-mode :reset` genuinely trims (isaac-agent
`src/isaac/drive/turn.clj:1150`):

```clojure
transcript (if (= :reset context-mode)
             (if-let [current-user (last transcript)] [current-user] [])
             transcript)
```

Nor is it boot files: the worker's `AGENTS.md` and `README.md` total ~9KB.

## The lead I could not close

The server log carries a context gauge per request:

```clojure
:event :session/compaction-skipped
:reason :context-reset
:total-tokens 938870
:context-window 1000000
:session "isaac-work-1"
```

**938,870 tokens of stored session against a 1,000,000 window** — 94% full,
and compaction is *skipped* precisely because the session is `:reset` mode, on
the assumption that reset will trim it at build time.

So there are two facts that do not sit together:

1. `:reset` trims the transcript to the last user message at request-build time
2. the session's gauge is 938k, and the average request was ~358k — neither
   ~72k (fully trimmed) nor ~938k (not trimmed at all)

Something between Isaac's trim and what the provider actually sends is carrying
several hundred thousand tokens per request. I ruled out one hypothesis —
that the Claude Code SDK keeps its own local session state and replays it —
because `~/.tono-claude/sessions/` is empty. It could still be held
provider-side, keyed by the `:session-key` Isaac passes in every request
(`:request-keys [:effort :messages :model :session-key :tools]`).

**This needs instrumentation, not more remote archaeology.** Isaac records what
a turn cost but nothing about what the request was *made of*.

## Why my estimate was wrong, and why the comparison I drew was misleading

I predicted 200–400k for zanebot. Two errors:

**1. I compared incomparable numbers.** I put zanebot's 35.6M next to a
subagent's 243,612 and called it a 100× gap. The subagent figure almost
certainly excludes cache reads — a 91-tool-call agent cannot total 185k
including them, since every tool call is another request re-reading the whole
context. The fair comparison is like-for-like on non-cache-read tokens:

| | non-cache-read | bean |
|---|---|---|
| zanebot scrapper | **~722k** | isaac-k00m |
| subagent | **~244k** | isaac-286x (larger bean) |

≈ **3×**, not 100×. Still a real gap, and still worse than my estimate, but an
honest one. Both figures should be treated as approximate until the
`subagent_tokens` definition is confirmed.

**2. I estimated the work, not the harness.** A bean's difficulty barely
predicts its cost. 99 requests × a ~358k context is the cost, and neither
factor is about how hard k00m was. The same bean in a session with a small
context would be a fraction of this.

## What is actually expensive

Cache reads are billed at a fraction of input (~10%, less on newer Opus), so
35.6M "total" is not 35.6M of full-rate spend. The full-rate portion is
~722k — cache writes 637k, output 84k, fresh input 648.

Note what that says: **cache writes are 88% of the real cost, and output is
12%.** The model barely wrote anything (84k over 12 minutes). The spend is
almost entirely in assembling and re-assembling context.

## Follow-ups worth filing

1. **Account for tokens on the claude-code provider.** Isaac logs
   `:total-tokens` per turn for chatgpt but the composition of a request is
   invisible everywhere. A basic question — "what did this bean cost, and
   why?" — took a transcript dig and still ended in an unexplained 285k.
2. **Explain the 938k gauge on a `:reset` session.** Either reset is not
   reaching the provider, or the gauge measures something the request does not.
   Both are worth knowing; one is a leak.
3. **Consider a fresh session per bean.** `isaac-work-1` has been accumulating
   since at least 2026-09-21 and never compacts because it is `:reset`.
4. **Re-examine cycle economics.** 99 requests for a bean whose whole diff is
   one flag, one guard and three step definitions suggests the loop re-reads
   more than it needs to between cycles.

## Correction recorded here deliberately

While setting up isaac-286x I said marking the app Trusted would work, then
that the OAuth client ID would appear in the app list. Both were wrong —
Isaac is an **Internal** app and Google's Trusted/Limited/Blocked control is
for third-party apps. The fix that worked did not involve the console at all:
remove the Cloud Platform scope from the user's grant.

**And no service account is in use.** Key creation was blocked by
`constraints/iam.disableServiceAccountKeyCreation`, so the `isaac-pubsub`
service account that was created — and its `roles/pubsub.publisher` binding on
the topic — is **inert**. Nothing authenticates as it. yopp has no
`google.tonotop.pubsub` config at all and `health.heartbeat.enabled false`.
What actually fixed the 16-hour expiry was dropping `auth/pubsub` from
`:isaac.google/scopes` and re-consenting; the service-account machinery exists
in the code but is unused on every host. See isaac-clly for the path that
brings the heartbeat back without a key (Cloud Scheduler publishes, no
credential ever exported).

---

# Follow-up measurement, same day: the gauge is a red herring, and the gap is narrower than it looked

## The 938k gauge is explained, and it is not the leak

`run-compaction-check!` computes the gauge over the **full stored transcript**:

```clojure
gauge (compaction/context-gauge entry tx (:input opts))
;; tx = (session-transcript session-key opts)  — the whole stored session
```

and then skips compaction when the session is `:reset`. So the number measures
something the request never contains. It is a **misleading log line**, not a
leak: on a `:reset` session the gauge will climb toward the window forever
while the actual requests stay small.

Both build paths do trim correctly — the main path (`turn.clj:1565`) and
`rebuild-chat-request` (`turn.clj:1150`) both reduce the transcript to the last
entry when `context-mode` is `:reset`.

## Fixed per-request overhead, measured directly: 32k

One minimal prompt in a **fresh** scrapper session on zanebot
(`isaac prompt --with-crew scrapper --session probe-tokens -m "Reply with
exactly: OK"`), 3 transcript entries:

```clojure
:usage {:prompt-tokens 32199, :output-tokens 9,
        :total-tokens 32208, :cache-read-tokens 0, :cache-write-tokens 32193}
```

So soul + boot files + rules + skill menu + tool schemas = **~32k**, not the
~285k my earlier subtraction implied. Boot files are not the problem, and the
earlier estimate of the fixed part was simply wrong.

## What that leaves — the real question, now sharply defined

| quantity | tokens |
|---|---|
| fixed overhead (measured) | ~32,000 |
| turn's stored transcript | ~87,000–145,000 |
| expected average request (32k + half of transcript) | **~104,000** |
| expected turn total (99 × 104k) | **~10.3M** |
| **actual average request** | **~358,000** |
| **actual turn total** | **35.5M** |

**Roughly 3.4× more context was sent per request than Isaac stored.** That is
the whole finding, and it is now a narrow one. Ruled out by measurement:

- the context gauge (measures the stored transcript, not the request)
- stale conversation history (`:reset` genuinely trims, both paths)
- boot files (~9KB on disk; whole fixed preamble is 32k)
- a local Claude Code SDK session store (`~/.tono-claude/sessions/` is empty)

Still open, and worth instrumenting rather than guessing:

1. Tool results may be capped when **stored** (`:max-lines` / `:max-bytes` from
   `defaults/tool-caps`) while the model receives them in full — that would
   make the stored transcript a systematic undercount of the sent context, and
   the discrepancy would grow with the number of tool calls, which matches.
2. The claude-code provider may add per-request content of its own that Isaac
   never sees or records.

Both are testable by logging the actual request size at send time, which is
precisely what isaac-5nx5 asks for. The point is no longer "where did 35M go" —
it is "why is a request 3.4× its transcript", and that is one measurement away.

---

# Third measurement, 2026-09-24: the instrumentation exists, and chars/4 is the first lie

isaac-5nx5 added per-request accounting (`:turn/request-sent` with the
composition, `:turn/request-measured` with the provider's own figure beside
it). These numbers come from running the **real `claude` binary** through the
claude-cli adapter with that instrumentation wrapped around it, one
`--print --tools ""` invocation per row.

## Isaac's token estimate is optimistic by 1.5×–2.1×

| body | chars | isaac estimate (chars/4) | provider reported | chars per real token |
|---|---|---|---|---|
| minimal control | ~57 | 22 | 852 | — (fixes the preamble) |
| English prose | 25,199 | 6,329 | 10,055 | **2.73** |
| English prose, doubled | 50,399 | 12,629 | 19,255 | **2.73** |
| JSON tool-call lines | 34,769 | 10,147 | 19,154 | **1.90** |
| real Clojure source | 30,000 | 7,718 | 12,053 | **2.68** |

Two things fall out of this and both matter.

**1. The provider adds a fixed preamble, not a proportional one.** Prose at
25k and at 50k chars sit on the same line to three significant figures:
`reported = 837 + chars/2.73`. Doubling the content doubles the reported
tokens exactly. So the claude-code provider is **not** injecting content that
grows with the request — the one candidate hypothesis left standing in the
previous section is wrong as stated. What it adds is a constant ~840 tokens
per `--print` invocation with `--tools ""` (its own system prompt). A driven
turn's preamble is larger — MCP tool schemas and the agentic system prompt —
and is the 32k measured earlier, not this 840.

**2. Four characters per token is wrong for the content Isaac actually
sends.** Every estimate in the codebase — the context gauge, the per-entry
`:tokens` stamp, `estimate-prompt-tokens`, compaction planning — divides
characters by four. Measured against the real provider, the true divisor is
2.7 for prose and code and **1.9 for JSON-shaped transcript content**: tool
calls, tool results, file reads. A worker turn's transcript is mostly that.

## This resolves the 1.7× disagreement between Isaac's own two measures

The isaac-k00m turn measured 347,927 stored bytes (≈87k tokens at chars/4)
while its own `:tokens` stamps summed to 145,260 — a 1.67× disagreement that
made both numbers untrustworthy. 1.67 sits inside the measured 1.5–2.1 band.
At the real ratio for that content mix, 347,927 bytes is **130k–183k tokens**,
not 87k. The stamps were closer to the truth than the byte count, and the byte
count is the one the 3.4× was computed from.

Re-derived with the corrected transcript size: 32k fixed + an average carried
transcript of ~91k (0 → 183k over 99 requests) ≈ **123k expected per request**
against **358k measured**. So the gap is real but it is **2.9×, not 3.4×**,
and about a third of what looked like a leak was an estimator error.

## What is still open, and what will close it

The remaining ~235k per request is Claude Code's own context, not Isaac's. In
driven mode Isaac issues **one** request per turn and Claude Code runs its own
99-cycle loop inside it; Isaac's transcript is a record of that loop, not its
context. Claude Code's per-cycle context additionally carries its agentic
system prompt, the MCP tool schemas, its own thinking blocks, its todo state,
and the `<system-reminder>` blocks it injects each cycle — none of which Isaac
assembles or stores.

That is now one turn away from being a number rather than a hypothesis:
`:turn/request-measured` logs `:estimated-tokens`, `:reported-prompt-tokens`,
`:unaccounted-tokens`, `:ratio` and `:provider-cycles` for every request. Run
one worker turn on zanebot with the instrumentation deployed and the
per-cycle figures say exactly how much of a cycle is Claude Code's and how
much is Isaac's.

## Worth filing separately

Changing the chars/4 divisor is not this bean's business but it is now a
measured defect, not a guess: the compaction gauge under-reads real sessions
by 1.5×–2.1×, which is the same direction as every "compaction fired too
late" symptom.

---

# Resolution: the 3.4× was two thirds estimator error and one third somebody else's loop

isaac-5nx5's instrumentation, measured against the real `claude` binary.

**The provider does not inject content that scales.** Prose at 25k and 50k
chars land on `reported = 837 + chars/2.73` to three significant figures.
Doubling the content doubles the reported tokens exactly. What Claude Code adds
per `--print` invocation is a **constant ~840 tokens**, not a multiplier. The
last standing hypothesis from the section above is dead as stated.

**Isaac's estimator is calibrated wrong.** `estimate-tokens` divides characters
by 4. Measured: **2.73** for prose, **2.68** for Clojure source, **1.90** for
JSON-shaped content — and a worker transcript is mostly JSON. Isaac under-reads
what it sends by 1.5×–2.1×. Filed as isaac-221w.

**That resolves Isaac disagreeing with itself.** The 1.67× between stored bytes
(≈87k at chars/4) and the transcript's own `:tokens` stamps (145,260) sits
inside the measured band. At the real ratio that content is **130k–183k
tokens, not 87k**. The stamps were closer to the truth; the byte count is the
one the 3.4× was computed from.

**So the gap is 2.9×, and the remainder is not Isaac's.** Re-derived: 32k fixed
+ ~91k average carried transcript (0 → 183k) ≈ **123k expected** against
**358k measured**. About a third of the apparent leak was the estimator.

The rest is the shape of the thing. **In driven mode Isaac issues one request
per turn.** The 99 "requests" counted earlier were Claude Code's own cycles,
running its agentic loop inside that single call — carrying its system prompt,
MCP tool schemas, thinking blocks, todo state and per-cycle reminders. Isaac's
transcript is a *record* of that loop, not its context. Nothing was re-sending
context; a different agent was running its own loop and billing for it.

**What that means for the original question.** "Why did one bean cost 35.5M
tokens" has a boring answer: a cycle-based agent with a large preamble ran 99
cycles, and cycle-based agents pay their preamble every cycle. The interesting
answer is that Isaac could not see any of this, and two of the three numbers it
did report were wrong — which is the actual finding, and why isaac-5nx5 was
worth more than the diagnosis it was chasing.

Still inferred, not measured: the ~235k per-cycle Claude Code preamble.
`:turn/request-measured` logs `:provider-cycles`, `:provider-cycle-max` and
`:provider-cycle-min` for exactly this, but a driven-mode turn needs the MCP
listener and a live tool registry, so it is one deployed turn away from being a
number rather than an estimate.
