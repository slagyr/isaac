---
# isaac-srz1
title: 'isaac-episodes drifted off isaac-agent: 23 feature scenarios fail against agent main, sealing yields 0 scenes'
status: todo
type: bug
priority: high
created_at: 2026-09-20T07:02:13Z
updated_at: 2026-09-20T07:02:13Z
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
