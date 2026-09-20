---
# isaac-srz1
title: 'isaac-episodes drifted off isaac-agent: 23 feature scenarios fail against agent main, sealing yields 0 scenes'
status: completed
type: bug
priority: high
created_at: 2026-09-20T07:02:13Z
updated_at: 2026-09-20T20:51:34Z
---

isaac-episodes pins isaac-agent at `2ed58f77` and is green there (216 spec / 85
features). Repin it to agent main `4f177932` and 1 spec + 23 feature scenarios
fail — on episodes main WITHOUT isaac-7rce as well, so the drift predates that
bean. Found 2026-09-20 while landing isaac-7rce; the repin was backed out and
the bean landed on the old pin.

What fails, against agent main:

- idle sealing (7 scenarios): "that episode has scenes matching" expected 1,
  got 0 — a seal produces no scenes at all
- recall (3): recall-at-open logging, the best-score log, recall__search
  mid-episode
- provider attention (1): a failing seal does not post attention through the
  seam
- live policy + lifecycle (several): cold prompt chaining, closing seals the
  transcript into scenes, explicit close
- migrate-session, layout (stdout matches)

Why it matters now: the shape of the first failure — a seal that yields no
scenes — is the shape of what Micah sees on yopp, where no scene is ever
sealed. isaac-ddls removed one cause (the provider contract error that failed
the seal with :provider-error); this is a second, independent candidate, and
yopp runs episodes main against agent main, which is exactly the combination no
suite covers.

Work: run the repinned suite, read the first sealing failure to the bottom
(does the agent's seal seam still exist under the name episodes calls, or has
its contract changed?), fix episodes (or the agent, if the agent broke a
published seam), repin to agent main, and land with the suite green on the new
pin. Related: isaac-j4jr (verify repins downstream before squash) — a repin
gate would have caught this drift when it was one scenario wide.

## Fixed and landed (planner, 2026-09-20)

isaac-episodes now pins isaac-agent `e948ce35` and is green there: 216 specs,
85 feature scenarios, zero failures.

Three causes behind the 23 failures:

1. **defaults.crew became required.** isaac-bfwn removed the agent's hardcoded
   "main" crew fallback. Any episodes config that named no default crew now
   fails validation, and the CLI just prints `invalid configuration in <root>`
   — which is why sealing, recall, attention and migration all went silent at
   once rather than failing in some particular place. Every feature config sets
   a default crew now, and migrate_session defines the crew its default names.
2. **The token count read a key that moved.** The agent's response contract
   (isaac-g71i) reports `:prompt-tokens`. `segment/response-usage` checked
   `:input-tokens`, `:input_tokens` and `:prompt_tokens` — every spelling but
   that one — so the migration progress line always said `0 in` while `out`
   was right.
3. **Streamed gists were dropped.** The stream contract hands a consumer
   `{:text-delta "..."}`; segment only read the older `[:message :content]`
   and `[:delta :text]` shapes, so the boundary lines the model writes were
   never echoed.

(2) and (3) are the same mistake as the ollama tool-call bug found the same
hour: a consumer reading a provider shape that the contract has moved on from.

main-sha: isaac-episodes 3fb5a288ae111a23ab3aed104f690a23afbfe55c (0.1.4)

Note for isaac-od6i (drop hardcoded main-crew fallbacks in episodes): the
production fallbacks are still there. This bean only made the suite honest
about the requirement; od6i is still worth doing.
