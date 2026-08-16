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

10. **Sessions are RETIRED; the episode is the primitive.** The episode owns its transcript directly (one append-only message file per episode, in the crew's directory, beside its thin metadata record). No session files, no session layer. Every session-management question dissolves: "new or reuse?" → the router's job (append to warm / fork / open); "which session for X?" → doesn't matter, recall pulls the X scenes into whatever episode you're in; "right cwd? which model?" → pinned per episode, and switching just opens a new one. The only durable user-facing identity is the CREW. This supersedes the uniform-session-selector direction (isaac-4e4b) more decisively than "manual selection made implicit" — the selected thing ceases to exist.

11. **Derivation chain: transcripts → scenes → index.** Transcripts are the sole source of truth; each downstream step is rebuildable from the one before (index by re-embedding scene records; scenes by re-segmenting + re-gisting transcripts — reconstructible in kind, not byte-identical). Episode records are thin (pointers + operational values); the only unrecoverable field if lost is recall provenance, which is why `recalled-scenes` is persisted. Preserves isaac's file-based, no-index philosophy: brute-force cosine over per-crew files, no vector DB (fine to hundreds of thousands of scenes per crew).

12. **Multi-granularity retrieval refinement parked:** embedding each scene-line of a structured gist separately (mid-granularity match) is a cheap later upgrade; starting with three granularities would mean tuning three retrieval knobs before validating one.

## Architecture (carried forward from original design, still valid)
- **Local embeddings** (ollama on nightbird, e.g. nomic-embed-text): zero API cost/latency, self-hosted. Needs a NEW embedding-provider seam (the LLM-provider abstraction is chat, not embeddings).
- **Write-through indexing**: near-real-time so material is recallable promptly (now: rolling open-scene re-embed per turn; seal writes the two permanent rows).
- **Retrieval quality is the make-or-break** — conversation RAG is finicky. Baked in: topically-coherent embedding units (scenes), recency/similarity blend, current episode verbatim (never make the model "retrieve" what it just said), recall additive under a token budget, hybrid lexical channel.

## Sequencing (additive-first de-risks everything)
1. **Additive recall inside today's sessions** — scenes + gists + index + recall-at-open, injected tail-side per Decision 1. Immediate win (crews remember across conversations); proves the retrieval bar without touching the session model. This phase is also what MAKES episodes cheap.
2. **Retire sessions** — promote episodes to the storage primitive, put the router in front (append/fork/open). Sessions dissolve; users just talk to crews.
3. **Arcs / consolidation at scale** — chain scenes across episodes by topic; arc consolidation merges a chain of scene gists into an arc gist so old material compresses and the index stays fast/relevant.

## DEFERRED — explicitly not decided
- Embedding model + dims (nomic-embed-text vs alternatives); exact shape of the embedding-provider seam.
- Topic-shift detection mechanism (embedding drift vs LLM judgment vs heuristics) and the scene size cap value.
- Recency/similarity blend (decay function) and the recall token-budget split (gists vs scenes vs headroom).
- Router mechanics: how append-vs-fork-vs-open is scored; how the cache cost model informs (not dictates) the decision.
- Gist authorship: which model writes gists at seal; gist format (one line per... n/a now that gists are per-scene — but structure/length still open).
- Migration path from session files to episode files; what happens to existing session history (re-segment retroactively?).
- Arc design entirely (phase 3).
- Per-crew vs any cross-crew recall (original open question; unchanged).

## Relationship
- **Supersedes** the session-selector trajectory (isaac-4e4b / isaac-4puj direction): not implicit selection — elimination of the selected thing.
- **Generalizes** memory_write/get/search into a real recall layer: the index holds episodic material (scene text) AND gist material semantically; the existing keyword search survives as the lexical channel of hybrid recall (Decision 7).
- **Converges with compaction**: compaction and consolidation are the same operation at two altitudes (provider context window vs crew memory) — Decision 5.
- Children (later): embedding seam; scene segmentation + seal; gist writing; per-crew index + hybrid retriever; composition changes; episode records + router; session retirement; arcs.

Status draft — design substantially settled 2026-08-16; no scenarios yet. Next planning step: settle the deferred questions that block phase 1 (embedding seam, topic-shift detection, blend/budget), then scenario plan.
