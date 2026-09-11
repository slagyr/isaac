---
# isaac-qgeu
title: 'Conversation seam follow-up: activate bridge scenarios, reject legacy-chronicle collisions, take mode decision out of Discord'
status: draft
type: task
tags:
    - episodes
    - conversation
created_at: 2026-09-03T17:32:38Z
updated_at: 2026-09-03T17:32:38Z
parent: isaac-51xy
---

What isaac-7dkp shipped (0.1.41 train, 2026-09-03) works in the field: Discord and ACP both open marvin episodes and replies use the preserved origin. What it left behind, split out so 7dkp can close:

1. **Bridge acceptance is still @wip.** isaac-agent features/bridge/episode_dispatch.feature (3 scenarios from mrfu) has the file-level @wip and its `When a charge is dispatched with:` step is unimplemented — the seam is covered only by spec/isaac/conversation/router_spec.clj (19 lines). Rewrite the scenarios to the 7dkp shape (explicit `:conversation {:kind :thread :id ...}` request, not a prebuilt charge), implement the step, activate.
2. **Scenario 4 (approved 2026-09-03) is not implemented:** an episodes crew whose thread collides with a same-named legacy chronicle must return a named conflict, never run or silently orphan that chronicle. Today resolve-thread! never looks at the session store for the thread name. Marvin has three such legacy chronicles on zanebot (discord-1471611820048519304 / …0712 / …3745).
3. **The surface still knows the mode.** isaac-discord process-message! calls lifecycle/episodes-crew? to decide whether to send :conversation or :session-key, and on-turn-end falls back to session->channel-id reverse mapping. Micah's decision: the bridge alone chooses. Discord should always send the thread form + origin; the router's [:chronicles :thread] branch already handles chronicle crews.
4. **Router is a rename, not a decision.** route-by-mode returns the same :session-key for both modes; the chronicle/episode fork still lives in ensure-session!'s episodes-crew? branch. Move the fork behind the router so the seam is real.
5. `:conversation` is absent from the charge schema (charge.clj); it works only because routing runs before charge/build. Declare it.

Draft until the scenarios (1, 2, 3) are written; 4 and 5 are refactors verified by the same scenarios.
