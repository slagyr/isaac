---
# isaac-51xy
title: 'Conversation recall (RAG) layer: per-crew embedding cache + retrieval + prompt composition'
status: draft
type: epic
created_at: 2026-06-26T04:13:22Z
updated_at: 2026-06-26T04:13:22Z
---

DESIGN (exploratory, 2026-06-25). A cross-conversation recall layer so a crew can draw on the relevant parts of ALL its past threads when composing a turn — like human associative memory. AUGMENTS the session system; NOT a replacement (Micah). Big.

## Motivation
Sessions are a leaky abstraction — really a context-window boundary the user is forced to manage. The north star: a crew remembers across conversations and you 'just talk' to it. Literal 'all history in the prompt' is impossible (context window); humans don't do that either — they RECALL (associative) + CONSOLIDATE (gist) + FORGET. So: per turn, assemble context from recent-thread + RETRIEVED cross-thread fragments + consolidated memory. That is RAG over the conversation history.

## Architecture (isaac-flavored)
- **Chunk = a turn** (user+assistant exchange), embedded with a little surrounding context (bare single turns retrieve poorly — short, pronoun-heavy). Store {vector, text, thread-id, turn-index, crew, timestamp}. The thread-id link is what powers the hybrid recall + new-thread-seeded-from-old-threads.
- **NO vector DB needed at this scale.** One user, a few crews, thousands (not billions) of turns -> brute-force cosine over per-crew embedding FILES is plenty. No Qdrant/Chroma/sqlite-vec, no server, no ANN index. (Only needed at hundreds-of-thousands of chunks per crew — far off.) Preserves isaac's file-based, no-index philosophy.
- **Transcripts stay source of truth; the embedding store is a DERIVED, REBUILDABLE cache.** Lost/changed model -> re-embed from transcripts. The index can never be a corruption source.
- **Local embeddings.** Use a local model (ollama on nightbird, e.g. nomic-embed-text): every turn embedded with zero API cost/latency, self-hosted. Needs a NEW embedding-provider seam (the LLM-provider abstraction is chat, not embeddings).
- **Indexing hook:** each completed turn -> chunk -> embed -> append to the crew's vector file (write-through, near-real-time so it's recallable).
- **Composition step** in the prompt builder: recent-thread (verbatim) + retrieved cross-thread chunks + consolidated memory, under a TOKEN BUDGET (additive, not a replacement).

## Upgrades the existing memory system
memory_write/get/search exist; search is keyword/substring. This is the same idea done SEMANTICALLY. The index can hold BOTH raw-turn chunks (episodic) AND consolidated memories (gist); retrieval surfaces whichever fits. So this generalizes memory_* into a real recall layer, not a bolt-on.

## The make-or-break: retrieval quality
Conversation RAG is finicky (more than doc RAG). Bake in from the start: embed turn+context (not bare turns); BLEND recency with similarity (old match shouldn't outrank yesterday's relevant exchange); always include the CURRENT thread's recent turns verbatim (don't make the model 'retrieve' what it just said); treat retrieved context as additive to a budget.

## Sequencing (additive-first de-risks everything)
1. **Cross-thread recall, additive** — within TODAY's sessions, inject retrieved fragments from other sessions + memory into each turn. Immediate win (crews remember across conversations); proves the retrieval bar WITHOUT changing the session model.
2. (Later, separate) automatic thread routing — make session selection implicit; sessions dissolve into the background.
3. (Later) consolidation at scale — compress old threads into the index so it stays fast/relevant.

## Open questions
- Chunk window size (turn + how many neighbors?).
- Embedding model + dim (nomic-embed-text vs alternatives); embedding-provider seam shape.
- Recency/similarity blend (decay function).
- Budget split: recent-thread vs retrieved vs consolidated.
- Per-crew vs global index; cross-crew recall ever?
- Rebuild/reindex command; incremental vs batch.

## Relationship
- AUGMENTS sessions (not a replacement). Pairs with the threading/recall hybrid Micah sketched (threads stay for concurrency/isolation; recall makes them porous).
- The taken-to-its-conclusion version of the uniform session-selector bean (manual selection -> implicit recall-driven). 
- Children (later): embedding seam; per-crew vector cache + cosine retriever; indexing hook; prompt composition; consolidation.

Status draft — design only, no scenarios; revisit before promoting.
