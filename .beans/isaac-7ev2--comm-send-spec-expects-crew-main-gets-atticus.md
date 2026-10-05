---
# isaac-7ev2
title: comm_send_spec expects crew main, gets atticus
status: draft
type: bug
priority: normal
created_at: 2026-10-05T14:53:08Z
updated_at: 2026-10-05T14:53:08Z
---

Found 2026-10-05 while landing isaac-l1b6. `bb jvm-spec spec/isaac/agent/tool/comm_send_spec.clj` fails at line 123: expected `{:crew "main" :session "dawn-watch"}`, got `{:crew "atticus" :session "dawn-watch"}`.

Reproduced on clean isaac-agent origin/main `4153a79`, so it is not caused by the streaming-weather work. 1 of 18 examples in that file. Native `bb ci` was green on the same tree.

Out of isaac-l1b6. Do not fold the fix into that bean.
