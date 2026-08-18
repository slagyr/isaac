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
| 2 | isaac-rxr4 — backfill: transcripts → scenes + gists | 1 | draft |
| 3 | isaac-j2p4 — per-crew index + hybrid retrieval + `isaac recall` CLI | 2 | draft |
| — | **CHECKPOINT** — retrieval quality on real history; settle blend/budget/gist-prior empirically. Gates everything below. | 3 | gate |
| 4 | recall port + recall-at-open in prompt builder (first live change) | checkpoint | prose until checkpoint |
| 5 | live scene sealing + write-through (rolling embed; seal on topic-shift/size/compaction; gist-at-seal) | 4 | prose until checkpoint |
| 6 | zanebot deployment: ollama install, :embedding config, backfill on real corpus | 3 | prose until checkpoint |

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
