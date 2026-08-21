---
# isaac-51xy
title: 'Episodic memory: episodes, scenes, recall — per-crew RAG layer that dissolves sessions'
status: draft
type: epic
created_at: 2026-06-26T04:13:22Z
updated_at: 2026-08-16T00:00:00Z
---

DESIGN. Originally drafted 2026-06-25 as an exploratory RAG layer; substantially redesigned in a planning session 2026-08-16 (Micah). A cross-conversation recall layer so a crew draws on relevant parts of ALL its past conversations when composing a turn — like human associative memory. The design now goes further than the original: it REPLACES sessions rather than augmenting them (see Decision 10). Big.

## Motivation
Sessions are a leaky abstraction — a context-window boundary the user is forced to manage ("new session or reuse? which session for X? right cwd? which model?"). Every one of those questions exists because history is imprisoned in the container: a session is precious, so picking the wrong one loses context. The north star: you just talk to the crew, and the crew remembers. Humans don't replay full history either — they RECALL (associative) + CONSOLIDATE (gist) + FORGET. Recall un-traps history; once context flows to wherever it's relevant, containers stop being precious and need no management.

## Terminology (settled 2026-08-16)
The structural ladder — each level's boundary drawn by a different authority:

| Unit | Composed of | Boundary drawn by | Role |
|---|---|---|---|
| **Message** | — | wire format (one transcript entry: user text, assistant text, tool call, tool result) | atom of storage |
| **Turn** | messages | conversational rhythm (one user message → assistant final response, tool loop included) | atom of ADDRESS (`episode-id + turn-index`); NOT the composition unit of scenes |
| **Scene** | contiguous messages | TOPIC (plus size cap, compaction, episode close) | the unit of embedding AND recall |
| **Episode** | scenes | TIME (cache TTL — a window of continuous engagement) | the operational container; owns its transcript |
| **Arc** | scenes across episodes | topic continuity over weeks | phase-3 concept: cross-episode topic chain (named, not designed) |

Supporting vocabulary: **gist** (per-SCENE summary written at seal — memory-science term), **recall** (retrieval from the index during composition), **router** (decides append / fork / open per incoming message), **open/fork/warm/closed** (episode lifecycle), **index** (per-crew vector file), **consolidation** (writing gists; incremental at scene seal). A "spark" metaphor (spark/burst/ember, from spark-testing steel) was considered and rejected: episode-vocabulary is self-explanatory because it imports the memory-science terms (episodic vs gist memory, consolidation, recall) that describe exactly this architecture.

## Decisions (2026-08-16, Micah)

1. **Prompt caching is a first-class design constraint.** Anthropic caching is a strict prefix match (tools → system → messages); any byte change invalidates everything after it. Cache reads ~0.1× input price, writes 1.25× (5m TTL) / 2× (1h TTL) — a fully-uncached turn costs ~10× a cached one. THEREFORE: recalled content must NEVER be injected into the system prompt or mid-history. Composition order is stability-ordered: crew soul/system (frozen) → crew memory/gists (slow) → episode history (append-only) → recalled scenes at the tail → new message. A crew-prefix cache breakpoint means parallel episodes of the same crew share the warm prefix. The grok/responses-API lane (`previous_response_id` chaining) forces the same shape — history can't be rewritten there at all.

2. **Episode = cache-aligned window of continuous engagement.** Warm while used (TTL refreshes on read — no heartbeat needed), closed when cold. An episode is NEVER resumed: a cold resume re-writes the whole history at full price, while opening a new episode seeded by recall costs a shared-prefix read plus a small recall block. Recall isn't just a memory feature — past the TTL horizon it is the CHEAPER way to continue a conversation. 1h cache TTL fits human-paced chat (bursty, 20-min lulls); 5m breaks even at 2 requests, 1h needs ≥3 reads.

3. **Recall at episode-open, not per-turn.** Recall is an EVENT: the triggering prompt drives retrieval at open; recalled scenes are injected at the then-tail of the prompt and freeze into the cached prefix — recalled once, cached thereafter. A mid-episode topic shift may trigger another recall event (append-only `recalled-scenes` list). Recalled scenes are REFERENCES to other episodes' scenes; only created scenes belong to this episode.

4. **Two shaping forces, strictly separated: time shapes episodes, topic shapes scenes.** Each unit serves ONLY the force that shaped it. Episodes carry everything operational (cache lifetime, model/crew/cwd pins, lineage, transcript); scenes carry everything semantic (embedding, gist, recall). Consequence: the gist is per-SCENE, written at scene seal — not per-episode (a multi-topic sitting would produce a diluted blended vector). Episode close is not a consolidation event; it just seals the final scene. No batch consolidation job, no closed-but-unconsolidated limbo.

5. **Scenes are composed of MESSAGES, not turns.** In chat they coincide; an hour-long bean-driven agentic turn legitimately spans multiple scenes (investigate / implement / test are different topics in one turn). Turn remains the address unit; a scene's span is turn-index + message-index. Scene-seal triggers: topic shift, size cap, COMPACTION, episode close. Compaction is the same operation as consolidation at a different altitude — its boundary is a natural scene seal and its summary is the gist's first draft (essentially free). Compaction never touches the transcript: the transcript stores every message uncompacted; the compaction summary is recorded as an event in it.

6. **The scene is the unit of embedding and recall — no sub-scene chunks.** Scenes are topically coherent BY CONSTRUCTION, so a scene vector is faithful; the earlier "chunk = turn + context" idea was small-to-big machinery not needed at this scale — held in reserve as a measured fallback if scene-level retrieval proves too coarse. Two index rows per sealed scene: its TEXT embedding (specific phrasing — "where was this said") and its GIST embedding (broad phrasing — "which conversation was about this"). The open scene keeps one rolling text row, re-embedded per turn (local embeddings make this free). Embedded text is the distilled conversational content — narration, dialogue, discoveries — with tool payloads stripped; an hour of grep output contributes nothing, the sentence "found it — X was eating the retry path" contributes everything. The vector's job is SURFACING the right scene into context, not pinpointing the needle — the model finds the line once the scene is injected.

7. **Recall is hybrid and gist-primed.** Cosine over all rows (scenes + gists) blended with recency decay, PLUS the existing keyword/substring search as the lexical channel (embeddings are bad at exact-token queries like "what port number" — that's grep's job, and isaac already has it). Gist vectors serve three roles: they catch broad queries no single scene matches; a gist hit acts as a PRIOR boosting its episode's scene scores (fixes vocabulary mismatch); and they structure injection — group hits by episode, inject gist-only / scene-only / gist + best scene, fill the token budget breadth-first (gists are cheap) then depth (scenes). Gists play NO role in routing: live episodes have no gist; the router matches against live episodes' scene vectors including the rolling open-scene row.

8. **Episode attributes** (all operational): `id`, `crew`, `model`, `cwd`, `parent-episode-id` (nullable; set on fork), `recalled-scenes` (append-only recall events → foreign scene ids), `scenes` (created, in seal order), `active-scene` (live episodes only), `rolling-timestamp` (last activity; close is DERIVED — `now - rolling-timestamp > TTL` — discovered lazily by the router or a sweep, never an event). `crew` and `model` are pinned because both are cache-key identities (the cached prefix begins with the crew's soul; caches are model-scoped); `cwd` pinned because episodes are cheap enough to pin attributes to — a crew/model/cwd change opens a new episode seeded by recall. Design principle: WHEN IN DOUBT, OPEN A NEW EPISODE — the cost of a wrong container decision drops from "lost context" to "one cold cache write."

9. **Scene attributes** (all semantic): `id`, `episode-id`, `span` (start/end turn+message; open scene has no end), `started-at`/`sealed-at` (nil sealed-at ⇒ open; status derived), `seal-reason` (`topic-shift | size-cap | compaction | episode-close` — recorded for segmentation tuning), `text` (distilled, tool-stripped; both the embedded and the injected payload; rolling while open), `gist` (nil until seal). Scenes are IMMUTABLE after seal — frozen self-contained artifacts everything else points at. Vectors live in the index, not on the scene: rows `{scene-id, kind: text|gist, vector, embedding-model}`; recording embedding-model per row makes re-embedding incremental instead of a flag day. Recall bookkeeping lives on the recalling episodes, not the scene. No `arc-id` yet — arcs will reference scenes, preserving immutability.

10. **Sessions are RETIRED as the default; the episode is the primitive for episodic crews.** (REVISED later same session — see Decisions 14–17: the session policy survives permanently as the lightweight installation tier rather than being deleted.) The episode owns its transcript directly (one append-only message file per episode, in the crew's directory, beside its thin metadata record). Every session-management question dissolves for episodic crews: "new or reuse?" → the router's job (append to warm / fork / open); "which session for X?" → doesn't matter, recall pulls the X scenes into whatever episode you're in; "right cwd? which model?" → pinned per episode, and switching just opens a new one. The only durable user-facing identity is the CREW. This supersedes the uniform-session-selector direction (isaac-4e4b) more decisively than "manual selection made implicit" — for episodic crews the selected thing ceases to exist.

11. **Derivation chain: transcripts → scenes → index.** Transcripts are the sole source of truth; each downstream step is rebuildable from the one before (index by re-embedding scene records; scenes by re-segmenting + re-gisting transcripts — reconstructible in kind, not byte-identical). Episode records are thin (pointers + operational values); the only unrecoverable field if lost is recall provenance, which is why `recalled-scenes` is persisted. Preserves isaac's file-based, no-index philosophy: brute-force cosine over per-crew files, no vector DB (fine to hundreds of thousands of scenes per crew).

12. **Multi-granularity retrieval refinement parked:** embedding each scene-line of a structured gist separately (mid-granularity match) is a cheap later upgrade; starting with three granularities would mean tuning three retrieval knobs before validating one.

13. **Embedding provider: ollama (nomic-embed-text), and embedding is an OPTIONAL capability.** In-process JVM alternatives were compared (langchain4j bundled ONNX artifacts ~25MB one-dep, DJL, raw ONNX Runtime) and rejected: they are JVM-only (a bb runtime can't load ONNX natives — isaac-5zfv), and their small 384-dim models trade away quality. Ollama is plain HTTP (works from bb and JVM alike), higher quality (nomic-embed 768d), and already installed locally; install on zanebot. The cost accepted: ollama is a heavy external dependency — which is exactly why it must be OPTIONAL (Decision 14), and why the embedding seam + per-row `embedding-model` keep the provider swappable. Cloud embedding APIs rejected: transcripts leave the machine, per-turn network cost.

14. **The non-embedding mode is a PERMANENT installation tier, not a transitional state.** Requiring ollama would put isaac installation in a different category of complication. Therefore isaac must always run without an embedding provider — and the session policy survives as that lightweight tier rather than being deleted after cutover. This revises Decision 10's "sessions retired" and justifies real polymorphism: the sessions/episodes split is a durable product axis, not migration scaffolding.

15. **The abstraction: CONVERSATION (core) + CONVERSATION POLICY (pluggable).** A conversation is the shared core both sessions and episodes ARE: append-only transcript, attributes (model, cwd, crew), the one-turn-in-flight lock, turn execution. A conversation policy answers the three questions the core refuses to: **resolve** (incoming message → which conversation: session policy = explicit selectors / isaac-7r3k semantics; episode policy = the router), **observe** (per-turn bookkeeping: sessions = nothing; episodes = rolling timestamp, open-scene re-embed, seal checks), **retire** (sessions = never, resumable forever; episodes = TTL-derived close).

16. **The five seams, physically** (grounded in isaac-agent's current layout):
   - **Conversation core** — extracted from `isaac.session.*` into `isaac.conversation.*` (transcript.clj, store, schema, compaction move; the bulk of today's code, which stops knowing about selection entirely).
   - **Conversation policy** (resolve/observe/retire protocol) — `isaac.session.*` shrinks to the session policy (selection, frequencies); new `isaac.episode.*` is the episode policy (router, episode records, TTL sweep). Callers — hail delivery, CLI chat, channels — call `policy/resolve` instead of doing session selection themselves.
   - **Recall** — `(recall crew query) → recalled content`, called by `isaac.llm.prompt.builder` at composition. Implementations: **null**, **lexical** (existing keyword search — no embeddings), **episodic** (hybrid index).
   - **Embedding provider** — `(embed text) → vector`, an extension of `isaac.llm.providers`; optional capability whose absence is a legal configuration.
   - **Consolidation** — transcript spans → scenes + gists; observes conversations generically under any policy; needs an LLM (gisting) but embeddings only for indexing.
   (REVISED later same session — Decision 18 replaces the "namespace-level split first" stance with a four-module shape; the seam enumeration above still stands.)

17. **Dependency direction and installation tiers.** Each layer is optional to the one below, never required: conversation core ← session policy ← lexical recall ← episodic recall (needs embedding provider) ← episode policy (needs episodic recall — never-resume is only sane if recall bridges). Episodes REQUIRE episodic recall; sessions work at every tier. Supported tiers: **Base** (isaac alone: sessions, null/lexical recall) · **Remembering** (+ LLM gisting: sessions, lexical recall over scenes/gists) · **Episodic** (+ ollama: episode policy, hybrid recall). Policy is per-crew config (`:conversation-policy`), validated at load (episodes without an embedding provider = config error), hot-reloaded per the no-restarts rule. Per-crew selection is also the pilot mechanism: one experimental crew runs episodes against real traffic while other crews stay on sessions, same process, no parallel codebase.

18. **Three-module architecture** (supersedes Decision 16's namespace-first stance; revised same session from an initial four-module proposal after re-examining isaac-recall — see rationale below):
   - **isaac-agent** — keeps the conversation core where it already lives (transcript, store, schema, compaction, turn loop), defines TWO PORTS (conversation-policy: resolve/observe/retire; recall), AND contains the recall engine as `isaac.recall.*` namespaces: scenes, gists, consolidation, index, all three recall modes (null / lexical / episodic), embedding seam + ollama adapter. No extraction of the core; no isaac-conversation module; no isaac-recall module.
   - **isaac-session** — the manual policy as a plugin module (selection, frequencies, resume semantics extracted from isaac-agent in phase 2). Honest name for what permanently survives as the lightweight tier.
   - **isaac-episode** — the router policy as a plugin module (episode records, router, TTL sweep). Depends on isaac-agent only (implements its port, consumes its recall engine).
   **Why recall is namespaces, not a module:** the "keep heavy deps out of isaac-agent" argument died with the ollama decision — ollama is an INFRASTRUCTURE dependency (a daemon), not a code dependency; the adapter is a plain HTTP client and the engine is ordinary namespaces. Module-shape earns its keep where genuine plugin polymorphism lives (session/episode: interchangeable implementations of one port, like the channel modules); recall is one engine with three config-selected modes — namespace-shaped. A module boundary there would also put the deploy train between recall and the prompt builder during exactly the phase-1 tuning loop that needs the fastest iteration. Escape hatch pre-identified: if `isaac.recall.*` bloats isaac-agent's spec, split-production-not-specs fires and isaac-recall is extracted THEN, in response to real pressure.
   Dependency picture: isaac-session → isaac-agent ← isaac-episode. **Recall is deliberately NOT part of the conversation-policy abstraction: recall belongs to the CREW, not the container.** Policy runs at message arrival (which conversation?); recall runs at prompt build (what does this crew remember?). The coupling is one-directional — episodes require episodic recall, but recall serves sessions equally, which is what makes phase 1 and the Remembering tier possible. Tiers: Base and Remembering are config differences (embedding provider + gisting LLM configured or not); Episodic is module-visible (isaac-episode installed). Phase 1 adds NO new module — everything lands as namespaces in isaac-agent. UNVERIFIED assumption to check in phase-2 design: the module loader / modules.edn registry supports register-an-implementation wiring for policy plugins the way it does for channels.

## Architecture (carried forward from original design, still valid)
- **Local embeddings** via ollama (nomic-embed-text) — confirmed as Decision 13: zero API cost, self-hosted, bb-compatible (plain HTTP), optional capability behind the embedding-provider seam (the LLM-provider abstraction is chat, not embeddings; the seam extends `isaac.llm.providers`).
- **Write-through indexing**: near-real-time so material is recallable promptly (now: rolling open-scene re-embed per turn; seal writes the two permanent rows).
- **Retrieval quality is the make-or-break** — conversation RAG is finicky. Baked in: topically-coherent embedding units (scenes), recency/similarity blend, current episode verbatim (never make the model "retrieve" what it just said), recall additive under a token budget, hybrid lexical channel.

## Sequencing (additive-first de-risks everything)
1. **Additive recall inside today's sessions** — scenes + gists + index + recall-at-open, injected tail-side per Decision 1. Immediate win (crews remember across conversations); proves the retrieval bar without touching the session model. This phase is also what MAKES episodes cheap.
2. **Policy ports + plugin modules, piloted per-crew** — define the conversation-policy port in isaac-agent (core stays put per Decision 18), extract the session policy into isaac-session and build isaac-episode beside it (both policies born as modules in the same stroke, so the port gets two real implementations the day it matters), opt one experimental crew into episodes against real traffic while others stay on sessions. Sessions are NOT deleted — they remain the Base/Remembering tier (Decision 14); episodic crews get the router and stop having session-management questions.
3. **Arcs / consolidation at scale** — chain scenes across episodes by topic; arc consolidation merges a chain of scene gists into an arc gist so old material compresses and the index stays fast/relevant.

## Roadmap (2026-08-16; bean ids assigned as created)

**Phase 1 — Recall** (all `isaac.recall.*` in isaac-agent):
| # | Bean | Depends on | Status |
|---|---|---|---|
| 1 | isaac-5lri — embedding seam (`isaac embed`, Embedder protocol, ollama adapter) | — | completed (post-verify float-format fix added on branch: 469a0fc) |
| 2 | isaac-rxr4 — backfill: transcripts → scenes + gists | 1 | completed (+ isaac-80pq defects, isaac-lq7x scene .md files, streaming/token output 0.1.22-26) |
| 3 | isaac-j2p4 — per-crew index + hybrid retrieval + `isaac recall` CLI | 2 | todo — dispatched 2026-08-18 (hail 7a929b24) |
| — | **CHECKPOINT** — retrieval quality on real history; settle blend/budget/gist-prior empirically. Gates everything below. | 3 | gate — next up |
| 4 | recall port + recall-at-open in prompt builder (first live change) | checkpoint | prose until checkpoint (+ obligation: loud server-side model-drift errors, see isaac-j2p4 decision 10) |
| 5 | live scene sealing + write-through (rolling embed; seal on topic-shift/size/compaction; gist-at-seal) | 4 | prose until checkpoint |
| 6 | zanebot deployment: ollama install, :embedding config, backfill on real corpus | 3 | largely done ad hoc 2026-08-17/18 (embedding config live, gist = grok-4.20-non-reasoning, isaac-work-1/orchestration-plan/doughty-birch migrated); remaining: migrate the rest of the sessions + index once bean 3 lands |

**Phase 2 — Episodes** (held as prose until phase 1 proves the retrieval bar):
7. conversation-policy port + extract isaac-session module (both policies born together — Decision 18)
8. isaac-episode module: episode records, open/fork/close lifecycle, TTL sweep, episode-owned transcripts
9. router: message → append/fork/open resolution (live-scene matching, cache cost model)
10. per-crew pilot: `:conversation-policy` config, one experimental crew; retroactive old-sessions-as-closed-episodes migration

**Phase 3 — Arcs**:
11. arc design + consolidation at scale (named concept only — see Terminology)

Beans are created just-in-time: drafts when shape is settled enough, `todo` only after a scenario session. Beans 4–6 stay prose because their shapes depend on checkpoint findings.

## How we begin (agreed 2026-08-16)
Shortest path to MEASURING retrieval quality on real transcripts, before any live behavior change. First child beans, dependency-ordered, all offline:
1. **Embedding seam** — `embed` capability in the provider registry, ollama-backed, optional.
2. **Backfill: transcripts → scenes** — a command that segments existing session transcripts into scene records + gists (LLM one-pass segmentation+gisting for backfill; the corpus is finite and quality is the whole game — cheaper drift heuristics can come later for the live path). Retroactive treatment of old sessions as recall material falls out of this for free.
3. **Index + recall CLI** — embed scenes/gists; hybrid retrieval (cosine × recency + keyword channel); an `isaac recall "query"` command/tool for evaluation.
**Checkpoint (gates everything downstream):** run real queries against real crew history; judge by eye; settle blend/budget/gist-prior empirically. Only then: bean 4 = recall-at-open in the prompt builder (first live change), then live write-through, then phase 2. If recall is mediocre, iterate on scenes/gists with nothing built on the sand.

## DEFERRED — explicitly not decided
- Exact protocol/fn shape of the embedding-provider seam and the conversation-policy seam (model chosen — Decision 13; seams located — Decision 16; signatures settled during bean 1 / phase-2 design).
- Topic-shift detection mechanism for the LIVE path (backfill uses LLM one-pass per "How we begin"; drift heuristics vs LLM for live sealing still open) and the scene size cap value.
- Recency/similarity blend (decay function) and the recall token-budget split (gists vs scenes vs headroom) — to be settled empirically at the recall-CLI checkpoint.
- Router mechanics: how append-vs-fork-vs-open is scored; how the cache cost model informs (not dictates) the decision.
- Gist authorship: which model writes gists at seal; gist structure/length.
- Lexical-recall tier details: what the Remembering tier retrieves and injects without embeddings.
- Arc design entirely (phase 3).
- Per-crew vs any cross-crew recall (original open question; unchanged).

## Relationship
- **Supersedes** the session-selector trajectory (isaac-4e4b / isaac-4puj direction): not implicit selection — elimination of the selected thing.
- **Generalizes** memory_write/get/search into a real recall layer: the index holds episodic material (scene text) AND gist material semantically; the existing keyword search survives as the lexical channel of hybrid recall (Decision 7).
- **Converges with compaction**: compaction and consolidation are the same operation at two altitudes (provider context window vs crew memory) — Decision 5.
- Children (in "How we begin" order, all phase-1 work inside isaac-agent): embedding seam (`isaac.recall.*` + ollama adapter); backfill segmentation + gisting; per-crew index + hybrid retriever + recall CLI; recall port + recall-at-open composition; live write-through + scene sealing; conversation-policy port + isaac-session extraction; isaac-episode (records, router, TTL); arcs.

Status draft — design substantially settled 2026-08-16 (two rounds: episodes/scenes/recall model, then conversation/policy seams + installation tiers); no scenarios yet. Next planning step: draft child bean 1 (embedding seam) with a scenario plan.

## Field notes (2026-08-17, first real migrations)
- isaac-rxr4 + isaac-80pq landed; real sessions migrate locally. Segmentation contract is line-format (`<first>-<last>: <gist>`, prompt states N, open-ended endings resolve to N, prefer-several-scenes nudge).
- **Gist-model quality is the dominant retrieval-bar variable**: llama3.2 (3B) is format-compliant but segments inconsistently (11 sharp scenes one run, 1 lazy blob the next); llama3.3:70b segmented the same session into 6 precise scenes in 92s, first try. Checkpoint should sweep gist models; 70b-class is the local default meanwhile.
- Scene files move to markdown + YAML frontmatter (isaac-lq7x, before bean 3 — the index must not chase a moving format).
- **Phase-2 obligation (do not forget):** migrated episodes carry no transcript — their provenance points at session .jsonl files via :migrated-from + scene span ids. Session retirement (roadmap #7-10) MUST relocate or copy transcripts into episode directories, or migrated provenance dangles.

## Field notes (2026-08-18, hosted gisting)
- **Prompt splitting on large sessions**: prompts are bounded, only call count grows. Transcript splits at compaction boundaries, then 80-message size cap; each span = one LLM call (distilled messages + preceding compaction summary + instructions, ~2-6k tokens in, ~150 out). isaac-work-1 = 23,477 messages / 41 compactions ≈ ~300 calls.
- **Decision (2026-08-18, Micah): try faster serial gisting on a hosted model before parallelizing spans.** Claude models out of bounds (subscription); codex models on zanebot also ride the ChatGPT subscription (no OpenAI API key). Chosen: `grok-4-1-fast-non-reasoning` via the existing xai API-key provider ($0.20/$0.50 per 1M — an isaac-work-1 migration ≈ well under $1).
- Field result (doughty-birch, 26-msg heartbeat, zanebot): qwen2.5:14b 118.9s → grok 1.2-1.4s (~85x), and gist quality jumped from mechanics-narration ("Dropping of memory_get tool result") to recall-worthy summaries. Scene count nondeterministic across runs (5 then 3) — fine, tiling holds.
- Segmentation streaming/usage originally read only ollama chunk shapes; 0.1.25 adds responses-API `[:delta :text]` chunks + normalized kebab usage keys, plus a "retrying span" separator (fixes the both-attempts output artifact). Migrate output now prints per-span and total time + token counts (0.1.24).
- Worst-case input identified: tool-heavy heartbeat sessions (90% tool traffic). Prompt candidate for the retrieval-quality checkpoint: "gist what was accomplished or discussed; tool activity is evidence, not subject." The prefer-several-scenes nudge over-segments these on weak models.
- **Correction (2026-08-18, Micah): codex AND grok subscriptions are in bounds — only claude is out. Preference: subscription over pay-per-token.** grok-4-1-fast rides the xai pay-per-token provider, so it's deprioritized.
- **Gist-model bake-off** (doughty-birch, 26-msg heartbeat, zanebot, one span each): qwen2.5:14b local 118.9s / 7 mechanical scenes; grok-4-1-fast-non-reasoning (xai $) 1.7s, 1168/72 tok, 5 good scenes; gpt-5.4-mini (chatgpt sub) 2.2s, 1010/58 tok, **1 lazy blob** (well-written but no granularity); grok-composer-2.5-fast (grok sub) 12.0s, 1479/689 tok, **6 precise scenes — best segmentation of any model tested**. zanebot gist.edn now points at grok-composer-2.5-fast. Caveat: bulk migrations share grok subscription quota with working crews.
- **CLI oauth fix (0.1.26)**: subscription providers failed with auth-missing from the episodes CLI — the gist provider config lacked `:root`, which oauth token resolution (auth.json) requires; the drive turn path injects it via augment-provider, migrate now does the same.
- **Subscription catalogs re-enumerated (2026-08-18)** — zanebot's model files were a generation stale. grok sub (api.x.ai/v1/language-models): grok-4.20 (non-reasoning/reasoning/multi-agent, price 12500/25000), grok-4.3 (12500/25000), grok-4.5 + grok-4.6 (20000/60000), grok-build-0.1 (10000/20000, = grok-code-fast-1); grok-composer-2.5-fast serves but is UNLISTED (legacy). codex sub (chatgpt.com/backend-api/codex/models?client_version=...): gpt-5.6-sol/terra/luna, gpt-5.5, gpt-5.4, gpt-5.4-mini, gpt-5.3-codex-spark; configured codex.edn's gpt-5.1-codex is no longer in the served list.
- **Gist model settled (2026-08-18): `grok-4.20-non-reasoning` via grok subscription** — bake-off round 2 on doughty-birch: grok-4.20-nr 1.2s, 1168/81 tok, 5 precise scenes (zero reasoning overhead — 81 out IS the boundary list); gpt-5.6-luna 6.4s, 253 out, 4 excellent scenes; gpt-5.3-codex-spark 15.6s, 1691 out (hidden reasoning), 4 narrative scenes. Winner is best on all three axes: speed, quota weight, segmentation precision. isaac-work-1 (~300 spans) projects to ~6-10 min serial.

## Field notes (2026-08-19, first recall field trials)
- **isaac-j2p4 completed + verified** (one verify-fail round: planner's grover char-sum arithmetic was wrong in two fixture cells — worker caught it). 0.1.27 deployed; 0.1.28 fixed corpus-scale indexing (single giant embed request blew the 120s HTTP timeout — now 64-text batches with progress); 0.1.29 added recall timing/memory footer.
- **First real recall is a hit**: "grok oauth refresh token fix" over scrapper's 1,481 scenes returned the July isaac-wpny OAuth saga at ranks 1/2/4/5. Gists ranked AND read well.
- **Junk queries expose four defects** (Micah field-tested "marketing page", "whoville test data" — neither term exists in corpus): (1) no honesty floor — top-N fills with recency-sorted noise; (2) lex counts common words ("test"+"data" = 0.667) — needs IDF from corpus df stats, plus matched-term receipts in output; (3) recency dominates when cosines flatten into the noise band (real nomic cosines cluster 0.33-0.65); (4) latency. Proposed fixes discussed: IDF lex + z-score-based match floor (top hit must be a right-tail outlier of the query's own candidate distribution, or carry a rare-term lex hit); live recall injects NOTHING below floor.
- **Measured recall timing** (zanebot, 2,962 rows): index EDN parse 3457ms, scene .md loads 292ms, query embed 91ms (nightbird), scoring 1011ms; plus ~2.9s bb startup+config. Once loaded, a query costs ~1.1s. Index: 27.1 MB file → ~104 MB heap (boxed doubles, ~10x blowup; float arrays would be ~9-18 MB and much faster cosines). Bean 4's server-resident index eliminates startup+parse per query.
- **Embedding host → nightbird (2026-08-19, Micah)**: zanebot is a 2019 Intel MBP (CPU-only ollama, ~800 tok/s); nightbird embeds 4x+ faster, same nomic model → same vectors, index stayed valid ("0 new rows"). New provider `ollama-nightbird`; local ollama untouched for main-crew chat. Trade-off recorded: embedding now has a LAN dependency — bean 4 must degrade gracefully on embed-unavailable regardless.
- **Deferred (Micah)**: text-row truncation — test the quality effect at the checkpoint before adopting. Neither grok nor codex subscriptions offer embeddings (verified against both live catalogs); OpenAI embeddings would need a new API key + full re-index.

## Field notes (2026-08-20, variety panel — checkpoint corpus assembled)
- **Panel migrated + indexed** (122 sessions, ~8 min, 1 empty-stub rejection): main 102 eps/314 scenes, tempest 9/50, marvin 4/43, prowl 3/523, pinky 2/876, perceptor 1/582; with scrapper = **3,869 scenes across 7 crews**. Per-crew index load 47-145ms, score 105-293ms on the smaller crews (scales with rows as expected).
- **Corpus-composition findings (crap-in diagnosis, Micah's hypothesis confirmed in degrees):** scrapper: 51% of gists are test/spec-running, 15% skill/handoff process. pinky (telemetry): 876 near-identical "processing location updates" scenes — maximal clump, zero recall value; also surfaced scenes whose gist is literally "Tool result dropped" (distillation left only the marker). These are the exhibits for a **recall-worthiness filter** (gist model tags :routine/:substantive at seal; routine stays in the episode, out of the index).
- **Embedding-model probe** (nightbird, same texts): nomic real-vs-noise separation 0.093; embeddinggemma 0.081 raw but with a much lower junk band (0.18-0.20 vs 0.42) and untested task-prefixes — include prefixed gemma in the checkpoint sweep; model swap alone won't fix dilution.
- **Flavor results:** main retrieves excellently ("stuck hails attention needed" → the exact heartbeat distress reports, lex 1.0). pinky junk-probe returns today's empty scenes at rec 1.0 — recency dominates when content is void; floor (broken, see isaac-74ls) would not have saved it. NOTE: the deep design dialogues (terminology, caching) are NOT in any isaac corpus — they live in local Claude Code planning sessions; zanebot's prowl is worker-facing planning traffic only.
- **Checkpoint agenda consolidated:** (1) recall-worthiness filter; (2) gist prompt revision ("tool activity is evidence, not subject"); (3) floor redesign on absolute cosine + lex anchor (z is dead — EVT); (4) model sweep incl. prefixed embeddinggemma; (5) weight sweep with per-flavor query sets; (6) revisit text-row truncation.

## CHECKPOINT (2026-08-20) — query set (written blind, committed before any rankings viewed)

Real queries (expect a relevant scene in top-5; grading = hit@5 + carrying channel):
| # | crew | query | expected memory |
|---|---|---|---|
| R1 | scrapper | grok oauth refresh token fix | July isaac-wpny/tzgb OAuth saga |
| R2 | scrapper | float vector zeroed embedding bug | isaac-5lri float-format defect |
| R3 | scrapper | compaction span segmentation line format | isaac-rxr4 implementation |
| R4 | scrapper | isaac-q3uk session store cheshire | q3uk session-store work (identifier query) |
| R5 | scrapper | packed index vectors json quantized | isaac-74ls packed-store work (very recent) |
| R6 | prowl | verify fail rescope acceptance criteria | planner AC rulings (rxr4/zcb9 era) |
| R7 | prowl | CI regression default branch investigate | orchestration CI-repair scenes |
| R8 | main | stuck hails attention needed | heartbeat distress reports |
| R9 | main | workspace drift uncommitted changes | heartbeat findings |
| R10 | perceptor | missing gherkin coverage oauth rotation | wpny verify-fail content |
| R11 | perceptor | verification audit process fail | verify-session audits |
| R12 | marvin | (exploratory) weekend plans | unknown — graded descriptively |
| R13 | tempest | (exploratory) schedule reminder | unknown — graded descriptively |

Junk controls (expect: nothing relevant exists; today they return noise — floor calibration data):
| # | crew | query |
|---|---|---|
| J1 | scrapper | marketing page |
| J2 | scrapper | whoville test data |
| J3 | main | quarterly revenue forecast |
| J4 | prowl | pizza delivery order |
| J5 | perceptor | birthday cake recipe |

Sweeps per real query: default weights; --w-gist 0 (text-only value); --w-text 0 (gist-only value); --w-recency 0. Floor calibration: record top-1 text/gist cosines for every run, compare real-query vs junk-query distributions, propose an absolute per-model cosine floor for nomic.

## CHECKPOINT VERDICTS (2026-08-20, Micah + planning session) — GATE PASSED

Graded: 9 clear hits, 2 partials (recency pollution), 0 hard misses, floor correctly warned on both genuine no-answer queries (small crews). Beans 4-6 UNLOCKED.

1. **Floor: absolute cosine replaces z entirely.** Junk best-cosines measured 0.39-0.462 across five controls; real winners 0.468-0.76. Rule: match iff max(text-cos, gist-cos) >= :floor-cos OR lex >= 0.5. Default :floor-cos 0.47 (nomic-calibrated; per-model config). z is RETIRED — cos floor is n-independent (it also catches the small-n cases z handled: R12 best cos 0.38). Thin margin (0.462 vs 0.468) noted; revisit with more junk samples.
2. **Recency default: 0.5 parts** (was 1). Both partials were rec-0.95+ receipts crowding older better matches; R6's superior answer was freshness-suppressed. Half-life stays 30d.
3. **Keep both gist and text rows** — winners stable with either zeroed (lex anchors); each channel carries hits the other misses. Routine filter already resolved the cost concern.
4. **Deferred, non-blocking:** prefixed-embeddinggemma sweep; text-row truncation; "User directing continuation N/M" receipt-scenes as a routine-definition revision candidate.

Implementation bean: floor-cos + recency default (small; revises the two 74ls floor scenarios — grover-land tests use --floor-cos 0.999 since grover cosines live at 0.9+). Then bean 4 planning.

## PHASE 1 COMPLETE (2026-08-20)
All phase-1 beans completed + verified + deployed (isaac.agent 0.1.34 on zanebot): 5lri embedding seam, rxr4 migration (+80pq/lq7x), j2p4 index+recall, 74ls honesty+perf, xl6h corpus quality, l1kz cosine floor. Checkpoint run and passed (9/11 hits, junk queries warn honestly, floors calibrated on measured data). Full corpus: 3,869+ scenes, 7 crews, per-crew indexes. Next: bean 4 — recall port + recall-at-open in the prompt builder (first live change; see decisions 1-3 for composition/cache discipline and isaac-j2p4 decision 10 + isaac-74ls for server obligations).

## Decisions (2026-08-20, Micah — bean 4 design session; live episodes pulled forward)

19. **Compaction closes the episode and opens a successor** seeded by the compaction summary. Compaction already breaks the cache, already summarizes, already seals a scene — promoting it to a container boundary removes mid-container history rewriting from the model entirely. Episodes are bounded by time (TTL) AND volume (context pressure).
20. **Router rules (the four cases):** (i) cold prompt, no warm episode -> recall + open new episode; (ii) warm episode -> append, NO recall (the cache payoff); (iii) cooled continuation -> NEW episode (never resume) with recall, predecessor's tail injected directly via **lineage** (successor carries :parent-episode — known pointer, no search needed; "arc" stays reserved for phase-3 topic chains); (iv) explicit "remember that time..." -> a `recall` TOOL usable mid-episode; tool results enter the transcript as the same recall event type.
21. **Recall injection is a transcript EVENT entry** (like compaction): query + scene refs + rendered gists/text, written at the tail, frozen into the append-only cached prefix. Durable and replayable. Never ephemeral prompt-time injection.
22. **No scene duplication:** episode records hold REFERENCES (:recalled-scenes = origin episode-id + scene-id + recalled-at + query). Scene files are never copied; the transcript event carries the frozen rendering only (cache/replay determinism). Created scenes belong to their episode; recalled ones don't.
23. **Injection payload = distilled scene text** (the .md body), never raw transcript slices (tool payloads add nothing per token). **Tiered depth:** strong floor-passers (top-1/2) get gist+text; the rest gist-only; `recall_scene` tool as escape hatch. Budget knobs in :recall config for pilot experimentation.
24. **Pilot switch:** per-crew opt-in, easy on/off, default off. Sessions untouched for non-pilot crews.
25. **Bean 4 splits:** 4a = minimal live episode container (open/warm TTL/close, compaction-close, seal-at-close reusing migration segmentation, lineage) for pilot crews; 4b = recall-at-open + lineage priority + recall tool on top. 4a is testable without recall.

26. **Episodes as managed sessions (4a architecture, Micah 2026-08-20).** Each open episode is backed by an ordinary session named by the episode id. The router sits at dispatch entry: warm -> route into backing session (turn engine/store/in-flight untouched); cold/absent -> close old (segment -> seal scenes -> :closed, flagged/partial machinery reused) and open successor with :parent-episode. Native episode SessionStore impl deferred to phase 2.
27. **Three layers, three names:** THREAD = outward stable handle (Discord channel id, ACP/toad session-id, hail bound-session, CLI --session) — maps to a chain of episodes over time; EPISODE = isaac's bounded internal container; PROVIDER CONVERSATION (previous_response_id chains, cache prefixes) aligns per-EPISODE, never per-thread. Clients keep their handle and never learn episodes rotate beneath it.
28. **Warm TTL: :episodes {:ttl-minutes 60} default** (engagement window, human-paced). Recorded caveat (Micah): cache-paid providers should track their effective cache window — likely becomes a provider/model setting later. Pilot rides grok/codex implicit free caching, so 60 is free there.
29. **4a mechanics confirmed:** :thread lives on the open episode record (router scans crew's open episodes, no registry file); closing is LAZY (next prompt) + compaction + explicit `isaac episodes close`; compaction-close seeds the successor's transcript with the summary as its first entry (episodes are compaction-free by construction -> seal-at-close segments clean spans); switch = :crew {:x {:conversation :episodes}}, default absent = sessions.

30. **Arcs redefined (2026-08-21, Micah): a chain of scenes connected by topic continuity, indifferent to episode boundaries.** The original phase-3 framing (cross-episode topic chain) was just the first motivating case; the second and more common one is INTRA-episode braiding — alternating topics in one conversation (user pivots turn-to-turn; if concurrent turns ever land, mid-thinking pivots too). Scenes stay strictly contiguous (the tiling contract is untouchable — it is what makes segmentation verifiable); arcs are the linking layer above them. Symmetry: lineage chains episodes by time, arcs chain scenes by topic.
31. **Designated cheap entry point: seal-time continuation marks.** The segmentation model already reads the whole span and sees the braid; the line format gains an optional `(cont <first>-<last>)` annotation — `7-8: (cont 1-2) back to the wine pairing` — parsed into `:continues` on the sealed scene. Chains form at seal for free: no clustering job, no extra model call. Cross-episode arc joins remain phase-3 work (need identity beyond span-local ordinals).
32. **Recall follows the arc.** When a scene is retrieved, its arc siblings ride along gist-only (~50 tokens each) — recall injects the THREAD, not the shard. This is the payoff that makes braided conversations whole again at recall time.

33. **Terminology layering (2026-08-21, Micah, "the dream ruling"): industry vocabulary at the boundaries, isaac vocabulary inside.** External surfaces (--session flags, ACP/toad session-ids, comm bindings, hail bound-session) keep saying "session" forever; the boundary translates: for chronicle crews an external session names a chronicle, for episode crews it names a THREAD. No client ever learns internal terms. (Already enacted implicitly by qxvl's `prompt --session reef-chat` scenarios.)
34. **The legacy policy is named :chronicle** — one unbounded, never-closing container holding a single ever-growing transcript. `:conversation :chronicle` (the default when absent) vs `:conversation :episodes`; phase-2 modules become isaac-chronicle / isaac-episode. Underlying insight (Micah): an isaac session is really just a TRANSCRIPT — the pure record; pins (crew/cwd/model) and lifecycle belong to the container; routing identity to the thread. Phase-2 rename candidate: session.store -> transcript store, the shared abstraction under both policies. Open edge: `sessions list` on episode crews shows timestamped backing containers — `episodes list` is the operator lens; phase 2 decides whether sessions list hides or labels them.
35. **Containers are crew-owned; permissions and behavior are the crew's** (2026-08-21, Micah). Evidence: all 153 zanebot sessions are single-crew, zero multi-crew, zero record/message drift — the per-session crew freedom is unused weight; drop the cascade in phase 2. Both policies share the invariant (episodes were already crew-pinned by decision 4). Distinction preserved: per-container MODEL override (session_model, /model) is the crew exercising its own allowance, not being overridden — considered separately.

36. **Phase-2 design note: WORKSPACES (2026-08-21, Micah + planning session).** Sessions have carried three roles: transcript + pins + a concurrency lock (one-turn-per-session x fixed cwd = a directory mutex; hail orchestration leans on this so no two turns mutate one checkout). Data: cwd is mission-scoped, not crew-scoped (scrapper 8 cwds, perceptor 4, prowl 3 — all `agents/<project>/<role>`, role constant per crew, only project varies; chat crews 1-3). Design: (a) raw cwd paths are replaced by WORKSPACE REFERENCES — hails/threads carry logical identity (project/repo URL), a per-host registry resolves paths (precedent: :hail-settings :beans-repos URL->path). Hails become machine-portable; crew tool bounds become "registered workspaces only" (kills the grants-entire-home config warnings); episodes still pin the RESOLVED path at open (boot-file/cache stability, decision 4). (b) **The workspace entry is the mutex**: in-flight tracking re-keys from session to workspace — same in-memory atom, natural key; preserves today's serialization exactly; makes multi-thread-one-checkout expressible; enables POOLS ("any free isaac work checkout") so parallelism scales by registering checkouts, not authoring hails. Crew-wide max-in-flight stays as the separate capacity throttle it always really was. OPEN: per-container model override (crew's own allowance — likely survives as-is).
