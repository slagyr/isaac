---
# isaac-ncrz
title: 'Ollama streaming drops tool calls: only the final chunk is kept, and the tool call is never in it'
status: completed
type: bug
priority: high
created_at: 2026-09-20T20:51:52Z
updated_at: 2026-09-20T23:27:45Z
---

Qwen can call tools. Isaac throws the call away.

Ollama on nightbird (0.32.15) returns a correct tool call for
`qwen3-coder-next`, in both streaming and non-streaming mode:

    "message": {"role":"assistant","content":"",
                "tool_calls":[{"id":"call_c9tm61hz",
                               "function":{"index":0,"name":"count_lines",
                                           "arguments":{"path":"/etc/hosts"}}}]},
    "done": false

The tool call arrives in a chunk with `"done": false`. The final chunk
(`"done": true`) carries `content: ""` and **no** `tool_calls`.

`isaac.llm.http/post-ndjson-stream!` returns only the LAST chunk:

    (let [result (loop [last-chunk nil]
                   (if-let [line (.readLine rdr)] ... (recur chunk)
                     last-chunk))]

`ollama/chat-stream` then hands that final chunk to `normalize-response`,
which looks for `[:message :tool_calls]` — gone — and `[:message :content]` —
empty. The turn ends `:empty-terminal-response`, twice, and the model looks
broken.

Text survives only because `chat-stream`'s `on-chunk` forwards
`[:message :content]` deltas as `{:text-delta ...}` and the drive accumulates
them. Tool calls have no such path, so they are lost in the streaming case
only; the non-streaming `chat` fn reads them fine.

Seen 2026-09-20 while trying the first non-frontier model on bean work: plain
questions answered, anything needing a file read returned nothing.

Work: accumulate tool calls across chunks in `ollama/chat-stream` (and the
usage counts, which live on the final chunk) and merge them into the response
handed to `normalize-response`. A chunk-level `on-chunk {:tool-call ...}` is
not required — the drive reads tool calls off the returned response.

Scenario: a streamed ollama response whose tool call arrives in a non-final
chunk yields a response with that tool call and `:stop-reason :tool-use`.
A streamed text response still accumulates its content.

This is the same mistake as isaac-srz1's two bugs: a consumer reading a
provider shape the contract has moved past. Blocks putting any ollama-hosted
model on bean work (isaac-2y86 is queued for exactly that).

## Fixed and landed (planner, 2026-09-20)

`chat-stream` now folds every chunk as it arrives — content and thinking
concatenated, tool calls appended, the closing chunk's model, stop reason and
token counts merged — and normalizes the whole thing instead of the last chunk
alone. The SSE adapters already accumulated; ollama was the only one trusting
the reader's return value.

Proved end to end on zanebot with qwen3-coder-next: `isaac prompt --crew qwen`
asked for a line count, `fs__read` fired, and the model answered 87.

**The spec was guarding the bug.** The existing `chat-stream` example fed two
chunks ("Hi", then "!") and asserted the content was "!" — true only if you
discard everything but the last chunk. It failed the moment the code became
correct. Replaced with one that asserts the folded content and the counts,
plus two new examples: a tool call arriving before the closing chunk, and
several spread across chunks.

main-sha: isaac-agent 1dbd68dc5f3fb68a4e3c196b5aa29c84c232dd72 (0.1.74)
1669 specs / 0, 836 features / 0. Deployed to zanebot.

See also isaac-srz1: the same mistake in isaac-episodes, twice.
