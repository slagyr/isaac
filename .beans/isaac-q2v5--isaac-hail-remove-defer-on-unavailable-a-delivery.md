---
# isaac-q2v5
title: 'isaac-hail: remove defer-on-unavailable; a delivery stays claimed through a suspended turn and completes when the turn does'
status: draft
type: feature
priority: high
tags:
    - hail
created_at: 2026-09-18T14:42:12Z
updated_at: 2026-09-18T14:42:12Z
parent: isaac-ugpq
blocked_by:
    - isaac-nqeq
---

Child 2 of isaac-ugpq. Deletes defer-delivery! for :unavailable? results and the retry-after bookkeeping (delivery_worker.clj ~435-447); delivery.feature scenarios re-cut: a walled turn is :suspended on the delivery, not deferred; attempts untouched; completion moves it to delivered/. hail show prints the suspended state. Hails-never-die semantics are now the turn layer's (child 1).
