---
# isaac-8hi7
title: 'isaac-gmail: attachments — MIME multipart raw message from the delivery''s files'
status: completed
type: feature
priority: high
created_at: 2026-09-25T03:37:51Z
updated_at: 2026-09-25T06:14:10Z
blocked_by:
    - isaac-o9h4
---

## Why

comm__send attachments (agent bean) need each comm to build the message.
Gmail wants a MIME multipart/mixed raw message.

## Design

- Manifest comm entry `:send-attachments? true`.
- `rfc2822` gains a multipart builder: text/plain part + one part per file
  (base64, `Content-Disposition: attachment; filename=…`, content type by
  extension, `application/octet-stream` fallback). `send!*` uses it when
  `:attachments` is present, for new mail and replies alike.
- Size: refuse a delivery whose attachments exceed 25 MB total (Gmail's
  limit) with a clear error — permanent, not retried.

## Acceptance (features/comm/gmail/gmail.feature — baselined)

- [ ] "comm__send with gmail.to, gmail.subject and an attachment sends one
  multipart email carrying the file (isaac-8hi7)": the sent mail decodes to the
  To/Subject/text and lists the attachment filename (extend the
  "sent mail decodes to:" step with an `attachments` row).
- [ ] Version bump; bump the isaac-agent pin to the sha carrying
  `attachments`; bb spec / features / lint green.

Likely repo scope: isaac-gmail (rfc2822.clj, gmail.clj, manifest, feature,
feature-steps).

feature-baseline: isaac-gmail 3b0b4faad16cdae231139cc079ec1bcccf8d5d29
feature-blob: isaac-gmail features/comm/gmail/gmail.feature c9d74d1539c081f9528d14261f735db92b662bf3 239



## Planner note (2026-09-25)

isaac-o9h4 landed: pin `io.github.slagyr/isaac-agent` to `ff62ae76b18eb41cde12ba9f5bcb716ddd41ce9f` (0.1.83) in deps.edn — it carries comm__send `attachments` and the `:send-attachments?` manifest flag. Declare `:send-attachments? true` on this comm's manifest entry.



## Planner note (2026-09-25 05:50Z)

isaac-iwio landed: gmail main e894924 (0.2.6) carries the send-schema (gmail.to/subject/thread) and `send-new!` / `send-on-thread!` in gmail.clj — build the multipart raw on top of those. The recipient-scoped step `the sent mail to "<address>" decodes to:` exists in feature-steps; extend it with an `attachments` row.



## Landed (2026-09-25)

isaac-gmail main 073c137 (0.2.7). Gate PASS; spec 140/0, features 44/0.


## Planner adjustment (2026-09-25, prowl@isaac-plan) — re-baseline on gmail 073c137

CI run 36101677156 failed because this bean was still baselined at isaac-gmail `3b0b4fa`. Planner commit `8b502cc` (isaac-iwio, count-only) removed the three-line `the sent mail to "ada@tonotop.com" decodes to:` table from the iwio scenario that sits inside that blob. isaac-iwio was re-baselined at `8b502cc`; this bean was not. The product is fine. The only feature change from `8b502cc` to landed `073c137` is dropping `@wip`.

Do **not** restore the ada@ decode table. Two mails go to the same address; the contract for that scenario is the count.

**Re-baselined** on isaac-gmail main `073c137` (newest lines in force). The 8hi7 scenario is line 233 there:

    feature-baseline: isaac-gmail 073c137b00996164bec03b1dd862eeea84249f43
    feature-blob: isaac-gmail features/comm/gmail/gmail.feature 02da637dae95c50dbe23bbd3c6b777ec4853bc7d 233

`bb bean-gate verify isaac-8hi7 --ref isaac-gmail=073c137` PASS against this note. CI re-gates completed beans on the push that records this baseline. No worker action. Do not re-open the bean. Do not edit the feature.

This note resets the verify-fail counter.

feature-baseline: isaac-gmail 073c137b00996164bec03b1dd862eeea84249f43
feature-blob: isaac-gmail features/comm/gmail/gmail.feature 02da637dae95c50dbe23bbd3c6b777ec4853bc7d 233

## Planner note (2026-09-25) — gate now PASS

The Bean Gate CI failure on the landing commit was a gate defect: the intact check froze every @wip block in the blob, so the planner's later edit of the sibling isaac-iwio scenario failed this bean. Fixed in isaac-3rbl (7bd196c): the check guards the bean's own named scenarios and every non-@wip block. `bb bean-gate verify isaac-8hi7` → PASS at gmail 073c137. CI-failure hail d4f2977e told to stand down.
