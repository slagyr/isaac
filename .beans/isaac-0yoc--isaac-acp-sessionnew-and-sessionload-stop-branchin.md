---
# isaac-0yoc
title: 'isaac-acp: session/new and session/load stop branching on the crew''s mode — the store answers'
status: in-progress
type: task
priority: normal
created_at: 2026-09-09T16:35:02Z
updated_at: 2026-09-10T10:49:51Z
blocked_by:
    - isaac-mmod
---

Repo: isaac-acp. Blocked by isaac-mmod. `server.clj` `session-new-handler` (fresh-mint for episode crews vs create-with-resolved-behavior!) collapses to `default-session`/`open-session!` on the crew's store; `attach-session-result!`/`replay-open-episode!` (find-open-on-thread + active-transcript of the backing session) collapses to `active-transcript` of the session id. Remove the `isaac.episodes.lifecycle` / `isaac.episodes.store` requires. Acceptance: `features/comm/acp/episodes.feature` and `session.feature` unchanged and green (the 6yg0 fresh-thread scenario is the proof); `grep -rn 'isaac\.episodes' src` empty.


## Handoff (scrapper@isaac-work-1)

branch: bean/isaac-0yoc @ 1453a8c6e34f731fd160ec23b5613a4c8709cd4f (base origin/main@79cc310d75555174dc726eaafc30524414ffbf6a)

ACP session/new and session/load no longer branch on isaac.episodes.lifecycle/store. Callers use SessionPolicy (for-crew, default-session, open-session!, active-transcript). grep src isaac.episodes empty.

Promote crew :conversation → :session-policy after normalize so unchanged episodes.feature fixtures still select the episodes policy.

Local: ISAAC_GIT=1 bb jvm-spec ACP cli+server 58 examples / 1 failure (pre-existing cancel tool_call_update, not this bean). jvm-features episodes+session 12 examples / 2 failures: :episodes/opened log still uses :session-id not :thread; warm sessions match episode-id regex vs stable session id reef-chat. Features left unchanged per bean.

Pin agent 837b6d4 (mmod 0.1.58).



## Verify fail (attempt 1, 2026-09-10): episodes.feature still red (2 failures); acceptance requires it green and unchanged

HEAD (beans repo): 1880032a
Working tree: clean
isaac-acp: bean/isaac-0yoc @ 1453a8c6e34f731fd160ec23b5613a4c8709cd4f (base origin/main 79cc310)

Acceptance: features/comm/acp/episodes.feature and session.feature unchanged AND green; grep -rn 'isaac.episodes' src empty.

- Features vs origin/main: unchanged (0 commits). @wip absent.
- grep -rn 'isaac.episodes' src: empty. Pin 837b6d4 present.
- ISAAC_GIT=1 clojure -M:features features/comm/acp/episodes.feature features/comm/acp/session.feature: 12 examples, **2 failures**, 28 assertions (6.76s).

Failures (episodes.feature, ISAAC_GIT=1):

1. session/prompt on an episodes crew opens an episode with the ACP session as thread
   Expected log row `:episodes/opened` with `thread=reef-chat` and `origin.kind=acp`.
   Got: thread nil, origin.kind nil.
   (Worker note: :episodes/opened still uses :session-id not :thread.)

2. a warm second prompt appends to the open episode
   Expected session id matching episode-id regex `#\"\\d{4}-\\d{2}-\\d{2}-\\d{4}-\\w+\"`.
   Got: `reef-chat`.
   (Worker note: warm sessions match episode-id regex vs stable session id reef-chat.)

session.feature scenarios in the same run were green (dots 1–8 and 11–12). The two F dots are the first two episodes.feature scenarios. Chronicle + --crew attach scenarios passed.

ISAAC_GIT=1 clojure -M:spec spec/isaac/comm/acp/server_spec.clj spec/isaac/comm/acp/cli_spec.clj: 58 examples, 1 failure (session/cancel tool_call_update cancelled vs completed) — worker claims pre-existing; not the blocking gate.

Do not land. Bean forbids recutting the features. Implementation must make the *existing* episodes.feature assertions green (6yg0 fresh-thread proof): :episodes/opened still carries :thread + :origin.kind, and the store still exposes the episode container id (date-slug regex), not the ACP session id, as the session row.
