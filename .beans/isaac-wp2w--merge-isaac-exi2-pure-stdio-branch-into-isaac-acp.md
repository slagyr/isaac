---
# isaac-wp2w
title: Merge isaac-exi2 pure-stdio branch into isaac-acp main, reconciled with the gnji root fix
status: in-progress
type: task
priority: high
created_at: 2026-07-07T16:11:28Z
updated_at: 2026-07-07T16:15:05Z
---

The verified isaac-exi2 work (branch isaac-exi2-pure-stdio, commit a8b26e6f) predates main gaining 3b48d97 (isaac-gnji: honor top-level root in acp cli). A naive merge compiles but its green-ness cannot be adjudicated outside a pinned-siblings environment. Task: merge the branch into main, reconcile with the gnji root-honoring change, prove bb spec + bb features green under proper sibling pinning (per the repo CI convention), push, and note the merge commit here. Do NOT re-verify exi2 itself — it passed; this is integration only. After the merge lands, the acp module gets a version bump + deploy (planner handles the train).
