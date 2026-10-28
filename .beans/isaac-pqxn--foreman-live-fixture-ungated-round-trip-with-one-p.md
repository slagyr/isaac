---
# isaac-pqxn
title: 'Foreman live fixture: ungated round trip with one planned repair (isaac-q6fj)'
status: in-progress
type: task
priority: normal
created_at: 2026-10-28T07:08:34Z
updated_at: 2026-10-28T07:20:43Z
parent: isaac-q3u3
---

A deliberate test bean for isaac-q6fj's live acceptance: one ungated bean through the Foreman machine, with one planned repair. The deliverable is small but real.

## Ungated

orchestration has no feature runner for docs, so there is no gate. Ungated path: the worker ends with `event: unverified` and the verifier decides.

## Work

Add `isaac-beans/features/README.md` to slagyr/orchestration (on `bean/isaac-pqxn`, from origin/main). Three or four plain sentences:
- what the `.feature` files in this folder are (Foreman machine tests for `config/machines/isaac-bean-work.edn`)
- that they run with `isaac foreman test <file>` against an Isaac root that has the Foreman module, the crews the machine names, and the `isaac-worksites` pool
- that machine tests carry no instance data, so `{{data.*}}` renders empty in them

Don't touch README.md, bb.edn, install.sh or anything under config/: isaac-q6fj's branch changes those.

## Verifier: planned repair

This bean tests the repair path. **On the first verification**, don't land it: append a `## Repair 1` note asking the worker to add one sentence saying the tests use a fixed step vocabulary provided by Foreman. Then end with `event: repair fixture: planned first-pass repair (isaac-q6fj)`.

**On any later verification**, verify normally against the Acceptance below, which includes the Repair 1 sentence, and land it.

## Acceptance

- [ ] `isaac-beans/features/README.md` exists on orchestration main and covers the three points under Work, plus the Repair 1 sentence.
- [ ] No other orchestration files changed.
- [ ] `isaac foreman status isaac-bean-work isaac-pqxn` history shows working → verifying → working (repair) → verifying → landed.


## Work checkpoint (2026-10-28)

Added the three requested initial documentation sentences on orchestration `bean/isaac-pqxn` (847578d); only `isaac-beans/features/README.md` changed. README scope/content checks and `git diff --check` passed. `bb bean-gate verify isaac-pqxn` exited 2 (no feature-baseline); awaiting first verification and its planned Repair 1 request.


## Held (awaiting human, 2026-10-28)

Escalated to human by **prowl**@plan-isaac-pqxn. Blocking: verifier turn died on a provider error before the planned first-pass repair; bean is verify-stalled and only a human dispatch resumes it.

Verify session `verify-isaac-pqxn` (request a81544ac) ended `turn/ended` `:error :api-error` — "Our servers are currently overloaded. Please try again later." — with no reply event. Foreman therefore took `foreman/turn-failed` verifying → verify-stalled and alerted this planner. No `## Repair 1` note was written. This is not a work failure and not a conflict in the bean.

Work itself is intact and matches the first-pass contract:
- isaac `bean/isaac-pqxn` HEAD `8eb34ca2` (clean, matches origin). Status in-progress + unverified.
- orchestration `bean/isaac-pqxn` `847578d` — only `isaac-beans/features/README.md` changed vs origin/main. Three sentences cover the feature-folder purpose, `isaac foreman test <file>` against a Foreman root with the named crews and the `isaac-worksites` pool, and empty `{{data.*}}`. The Repair 1 vocabulary sentence is correctly absent; first verification was supposed to request it.

Resume: `isaac foreman signal isaac-bean-work isaac-pqxn dispatch` (valid event from verify-stalled; re-runs verify). Verifier should still treat this as the first verification: append `## Repair 1` asking for one sentence that the tests use a fixed step vocabulary provided by Foreman, then `event: repair fixture: planned first-pass repair (isaac-q6fj)`. Do not land yet.

## Held (awaiting human, 2026-10-28, second stall)

Same blocker, second time. Micah dispatched at 07:19:07Z (verify-stalled to verifying). That re-verify (request 06d3d8de, session verify-isaac-pqxn) was still in flight — cycle 8, no verdict — when the previous planner alert turn ended by reply at 07:19:42Z. Foreman attributed that foreman/turn-ended to the verifying state and transitioned verifying to verify-stalled again. The verifier never got to append a Repair 1 note or emit event: repair.

Not a work failure. README on orchestration bean/isaac-pqxn 847578d is unchanged and still the three first-pass sentences only. No Repair 1 note.

Do not dispatch again until verify-isaac-pqxn's current turn has ended. A dispatch while any session on this instance can still emit foreman/turn-ended will re-stall the same way: the machine treats a quiet end from any session as a verify-side stall while state is verifying. After the verifier turn is dead, `isaac foreman signal isaac-bean-work isaac-pqxn dispatch` is still the resume, and that pass is still the first verification.

## Repair 1 (2026-10-28)

Planned first-pass repair: add one sentence to `isaac-beans/features/README.md` saying that the machine tests use a fixed step vocabulary provided by Foreman. Keep the existing three sentences and change no other orchestration files. Do not land yet; return for another verification.

## Verification failed (planned first pass, 2026-10-28)

HEAD: isaac bean/isaac-pqxn 7d20eec68078b86c254f7f97c153ff76ce0e5b67; orchestration bean/isaac-pqxn 847578da591bbad0b0aae63265b80d6bbb6e13c4.
Working tree: clean in both checkouts; orchestration diff vs origin/main only `isaac-beans/features/README.md`; `git diff --check origin/main...HEAD` passed.
Reproduction: `git show 847578d:isaac-beans/features/README.md` contains the three original requested sentences but no sentence stating that the tests use a fixed step vocabulary provided by Foreman. This is the bean-required first verification repair, not a failed test; no landing or acceptance run is appropriate until the worker adds it.
