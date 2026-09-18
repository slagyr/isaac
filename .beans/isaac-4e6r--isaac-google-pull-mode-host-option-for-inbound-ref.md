---
# isaac-4e6r
title: isaac-google pull mode (host option for inbound-refusing hosts)
status: draft
type: feature
priority: low
tags:
    - google
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T04:12:15Z
parent: isaac-bv1l
blocked_by:
    - isaac-1jep
---

Host option for machines that refuse inbound: the same inbox worker fed by a Pub/Sub **pull** subscription instead of the door. `:google {:pull {:subscription "…"}}` turns it on; the door stays off. Everything downstream (dedupe, inbox, handlers) is unchanged.

Parked until a host needs it. Scenarios when picked up: a pulled message lands in the inbox exactly like a pushed one; ack after persist, not after processing.
