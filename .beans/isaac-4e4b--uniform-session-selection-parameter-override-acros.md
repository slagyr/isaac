---
# isaac-4e4b
title: Uniform session selection + parameter override across tools (hail/prompt/acp/chat)
status: draft
type: epic
priority: normal
created_at: 2026-06-26T04:14:17Z
updated_at: 2026-06-26T19:39:26Z
---

DESIGN (exploratory, 2026-06-25). Session SELECTION and PARAMETER OVERRIDE are a cross-cutting concern shared by hail, the prompt command, ACP, and chat. Each rolls its own. Unify into one mechanism.

## Finding (grounded in code)
- Override half is ALREADY shared. All tools funnel session creation through session.context/create-with-resolved-behavior! (agent), which applies a fixed set of behavioral-keys (crew, model, effort, tags, context-mode, ...). hail, cron, prompt all use it. So "override session parameters" is already common — just not surfaced consistently per tool.
- Selection half is DIVERGENT — the real gap.
  - hail has the richest selector: the FREQUENCY — :session ids, :session-tags, :crew (sessions-of-crew, per kt1m), :reach :one/:all, :spawn-session. Resolves to 0..N sessions, create-if-spawn.
  - prompt (bridge/prompt_cli) is ad-hoc: explicit :session id, else most-recent, else "prompt-default" key. No tags, no crew, no attribute selection.
  - ACP / chat each have their own attach logic.
  So prompt/acp/chat cannot even say "the session of crew main" or "a session tagged X" — capabilities hail's frequency already has. Unifying LEVELS THEM UP, not just consolidates.

## Proposed shape
Extract a tool-agnostic session selector (generalize the hail frequency) paired with the already-shared override:
  :select   = {:session [...] :session-tags [...] :crew X :reach (:one|:all) :create (:always|:if-missing|:never)}
  :override = {:crew ... :model ... :effort ... :context-mode ...}   (the behavioral-keys)
- hail IS this already (frequency = :select; async fan-out back-end).
- prompt/acp/chat swap ad-hoc logic for resolve(:select) + create-with-resolved-behavior!(:override), with per-tool DEFAULTS (prompt -> :reach :one, :create :if-missing, recent-fallback).
- A shared resolver: (resolve-session-targets select store) -> sessions (create per policy). Each tool keeps only its back-end (hail delivers async to N; prompt/chat/acp attach to the 1).

## Differences to absorb cleanly
- :reach :all (only hail fans out; others constrain to :one).
- create-policy (hail spawn is opt-in via :spawn-session; prompt creates-by-default) -> the :create knob.
- prompt recent/default fallback -> a selector mode (:recent) or a per-tool default layered on top? (decide)

## Open questions
- Naming/home: "frequency" is a hail-ism (radio). Generalized = session selector / target / address. Lives in foundation (every module shares) or agent?
- Is :recent a first-class selector mode or a tool default?
- Migration order (prompt first — simplest), and whether hail frequency literally becomes the shared type or maps to it.

## Sequencing / relationship
- Do AFTER ebm2 + kt1m (they just reshaped the frequency; generalize the SETTLED shape). Both landed.
- Incremental: extract shared selector + resolver, migrate one tool at a time (prompt -> chat/acp -> hail), not big-bang.
- This is the MANUAL precursor to the conversation-recall (RAG) vision: unify selection first (explicit), then make it implicit/automatic (recall-driven), and the session dissolves into the background.

Status draft — design only, no scenarios; revisit before promoting. Children (later): shared selector type + resolver; migrate prompt; migrate chat/acp; map hail frequency onto it.

Related: the conversation-recall (RAG) bean isaac-51xy is the taken-to-its-conclusion version of this (manual selection -> implicit recall-driven).


## SETTLED flag design (2026-06-26, Micah) + home = isaac-agent

The shared selection/override LOGIC (selector type, resolver, override application) lives in **isaac-agent** (decided). Tools (hail/prompt/chat/acp) consume it; each keeps only its back-end. (Naming of the abstraction still open — "session selector"/"target"/"address" — but it's no longer the hail-ism "frequency".)

### SELECT — name OR describe (mutually exclusive)
- `--session <id>` — exact session by NAME. Mutually exclusive with `--crew`, `--session-tag`, and the create flags. (Name a session OR describe one, never both.)
- `--crew <crew>` — describe: sessions whose :crew is <crew>.            ┐ compose (AND)
- `--session-tag <tag>` — describe: sessions with this tag (repeatable). ┘
- Illegal combos fail fast with a clear message (e.g. `--session` + `--crew`/`--session-tag`/`--new`/`--spawn`).
- This makes the "--session bridge --crew main where bridge isn't main's" case a fast rejection, not a silent empty set.

### REACH
- `--reach one|all` is **hail-only**. prompt/chat/acp **assume :one and do NOT expose --reach** (attach to a single session). (No silent coercion needed — the flag simply isn't there for them.)

### CREATE policy (tri-state; combines only with the DESCRIBE flags)
- (default) — match existing only; no match -> error (sync tools) / undeliverable (hail).
- `--spawn` — match-OR-create (get-or-create). == hail's existing :spawn-session; adopt "spawn" everywhere.
- `--new` — ALWAYS create a fresh session (ignore any existing match). New flag (didn't exist).
- `--spawn` and `--new` are DISTINCT (if-missing vs always-fresh), not synonyms — keep both.
- When creating (`--spawn`/`--new`) with describe flags, the describe attributes become the NEW session's identity (e.g. `--crew marvin --new` -> a fresh session whose crew is marvin). "Describe" doubles as "the shape of what gets created."

### OVERRIDE — `--with-*` (NOT `--set`)
- `--with-crew <crew>`, `--with-model <model>`, `--with-effort <n>`, `--with-context-mode <mode>`, ... one flag per behavioral-key (fixed small set). Reads as "for THIS interaction," not a permanent mutation; discoverable/documentable.
- Crew disambiguation falls out cleanly: `--crew` = SELECT (sessions of crew X); `--with-crew` = OVERRIDE the resolved session's crew. Different prefixes, no collision.

### Per-tool surface
- prompt (sync, attach 1):  `isaac prompt [select] [create] [--with-*] -m "..."`   (no --reach)
- chat / acp (interactive, attach 1): `isaac <chat|acp> [select] [create] [--with-*]`   (no --reach)
- hail (async, fan-out):    `isaac hail send [select] [create] [--with-*] [--reach one|all] [--band NAME] [--payload ...] -m "..."`
  hail-only extras: --band (named/saved selector), --reach all (fan-out), --payload + async.

The shared `:select` map every tool builds: `{:session [...] :session-tags [...] :crew X :reach :one :create (:none|:spawn|:new)}` — hail serializes it as its :frequency; the others hand it to the agent-side resolver and attach to the one result.


## REVISION (2026-06-26): create policy is a single `:create` attribute (not --spawn/--new switches)
spawn/new/neither are mutually exclusive, so model them as ONE enum, not two booleans (two booleans can be set to a contradictory pair).
- Attribute: **`:create`** with values **`:never` | `:if-missing` | `:always`**:
  - `:never`      — match an existing session only; never create (no match -> error for sync tools / undeliverable for hail). (the former "neither")
  - `:if-missing` — match, or create if none (get-or-create). (the former "spawn"; hail's :spawn-session migrates to this)
  - `:always`     — always create a fresh session, ignoring any match. (the former "new")
- CLI: a single `--create never|if-missing|always` (NOT --spawn/--new switches).
- **prompt default = `:create :if-missing`** (when not conflicting with --session). hail default = `:never` unless the band/flag says otherwise.
- `--session` (exact address) remains mutually exclusive with `--create` (and the describe flags). When creating with describe flags, those attrs become the new session's identity.
- Reads as a sentence: "create: if-missing" / "create: never" / "create: always". Supersedes the earlier --spawn/--new section.

## REVISION (2026-06-26): flat input map + `--prefer` priority selector

Two contract refinements settled with Micah after B1 (prompt) shipped:

### 1. Flat normalized map (NOT a `:select`/`:override` split)
The adapter<->core contract is ONE flat map, not two. Rationale: override keys are already `--with-*`-prefixed, so in a flat namespace `:crew` (select: the session OF crew X) and `:with-crew` (override: process the turn AS crew X) never collide — the split was guarding a problem the naming already solves.
- Each surface (CLI flags, ACP protocol params, chat) normalizes its input into one flat map: `{:session :session-tags :crew :reach :create :prefer :with-crew :with-model :with-effort :with-context-mode ...}`.
- The CORE does the projection internally: select-keys -> `resolve-session-targets`; `--with-*` keys -> translate to behavioral-keys -> `create-with-resolved-behavior!`.
- This puts the one genuine subtlety (crew dual-meaning + the `--with-crew`->`:crew` translation) ONCE in the core — the reusability goal. `build-select`/`build-override` collapse into the core's internal projection; `selector-cli` keeps only the tools.cli option-specs + string parsing.

### 2. `--prefer recent|oldest` replaces `--resume` (priority/tiebreak selector)
`--resume` was a false friend: in Claude Code / most agents `--resume` = pick a specific prior session = our `--session`. Renamed to reflect what it actually is — the tiebreak that collapses a multi-match set under `:reach :one`.
- `--prefer recent|oldest`, default `recent` (recent = `:updated-at` desc). Extensible vocabulary later (`created`, `name`) if needed.
- Pure tiebreak: a NO-OP when the match is already unambiguous (`--session`, or a single-match `--crew`) — like `ORDER BY` on a one-row result. Only consequential on multi-match. Applies to ANY filter (crew, tags, or unfiltered), so it's orthogonal to the filter, not an empty-case thing.
- `--session` stays the exact selector (== the industry "resume").
- Kills the current `prompt_cli` bypass (`resolve-via-resume` -> `store/most-recent-session`); `--prefer` folds into the shared selector as the multi-match collapse rule.

### Separate, still-open point: empty-filter default
What to target when NO filter is given is a DISTINCT decision (not `--prefer`'s job): named `prompt-default` | most-recent-of-all | new. Keep `prompt-default` as the predictable default; "resume my last session globally" = `--prefer recent` over an all-sessions filter (however we express "all"). Settle on its own before B2/B3.

### Test placement
`--prefer` is a CORE selection concern -> selection-suite, CLI side, and LAST (lowest-risk, mechanical). Per the module-boundary rule: behavior once at the core; wiring/transport per surface (CLI in isaac-agent, server/ACP+chat in isaac-acp), so a future CLI/server module split stays clean.
