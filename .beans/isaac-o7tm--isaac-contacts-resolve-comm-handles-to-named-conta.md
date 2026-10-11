---
# isaac-o7tm
title: 'isaac-contacts: a handle becomes the contact it belongs to'
status: in-progress
type: feature
priority: normal
created_at: 2026-10-08T20:41:16Z
updated_at: 2026-10-11T21:05:59Z
parent: isaac-zt1x
blocked_by:
    - isaac-v403
    - isaac-s715
---

Part of the contacts epic (isaac-zt1x). Planned with Micah 2026-10-08 to 10-11. Its blockers have landed: turn attribution (isaac-v403) and the identifiers berth (isaac-s715).

## What this is

A new module, `isaac-contacts` (`:isaac.contacts`), that knows who the outside parties are. A comm records a sender as a handle, e.g. `{:kind :handle :comm :logbook :id "cordelia-7"}`. This module turns a handle it knows into one named contact, so the same person is the same contact on every comm.

The repo exists with the common infrastructure: `https://github.com/slagyr/isaac-contacts` — deps, `bb ci`, CI workflows, manifest, an empty module, a stub handbook chapter, and the `@wip` scenarios.

## Design

A contact is one file under `config/contacts/`, named for its id:

```clojure
;; config/contacts/cordelia.edn
{:name    "Cordelia Vane"
 :title   "Harbormaster"
 :weight  3
 :handles [{:comm :logbook :id "cordelia-7"}
           {:email "cordelia@marigold.example"}]}
```

- `:name` — display name. Required.
- `:title`, `:weight` — optional, free-form string and number. Nothing reads them yet; dreaming (isaac-b1ir) will use them to rank whose word counts more.
- `:handles` — each entry is either `{:comm <keyword> :id <string>}` or `{:email <string>}`.

The module declares one identifier on `:isaac.agent/identifiers`. Given a handle:

1. A contact that lists the handle's `:comm` and `:id` wins.
2. Otherwise a contact that lists the handle's `:email` (compared case-insensitively) wins.
3. Otherwise nil, and the agent stores the handle as it arrived.

A match returns:

```clojure
{:kind :contact :id "cordelia" :name "Cordelia Vane"
 :comm :logbook :handle "cordelia-7"}
```

`:comm` and `:handle` are the arriving handle's own `:comm` and `:id`, kept so the turn still says how the party arrived. `:title` and `:weight` are not copied onto the turn; a consumer looks the contact up by id.

## Rules

- **No comm knowledge.** No code for Chat, Gmail, iMessage or any comm. Handles are data.
- **Naming is not vouching.** The module never sets `:authenticated`; the agent carries the comm's claim onto the replacement (isaac-s715).
- **Plain lookup.** The identifier reads the loaded config snapshot. No I/O, nothing that blocks, per the berth's contract.
- **One handle, one contact.** Two contacts listing the same comm-and-id handle, or the same email, is a config validation error on `contacts` that names both contact ids and the handle.
- **People only.** The berth is only asked about `:kind :handle`. HTTP principals, crews and cron jobs are not contacts in this bean.
- **Placeholders only.** The repo is public. Features, specs, docs and examples use the Marigold cast (Cordelia Vane, Hieronymus Finch, Bartholomew Quill, `marigold.example`). No real names, hosts or addresses.

## Out of scope

- `isaac contacts list|show`.
- A record of unmatched handles for an operator to claim.
- Per-contact notes, permissions, or recall scoping.
- Service contacts (machines).

## Notes for the implementer

- Declare the table with `:isaac.config/schema {:contacts …}` in `resources/isaac-manifest.edn`; `isaac-gmail`'s `:gmail-routes` is a one-file-per-entry table to model on.
- `the stdout matches:` skips its first table row as a header, so the scenarios lead with a `| pattern |` row. The contact id rows are anchored (`(?m)^from\.id: cordelia$`) because an unanchored `cordelia` also matches the raw handle `cordelia-7`.
- The scenario "a handle no contact lists is stored as it arrived" already passes on the skeleton. It is there to stay passing.
- `config.feature` uses foundation's `config file … containing:` steps and no Grover setup. If the module's schema is not active under that harness, wire it in the step support, not in the feature text.
- Pins: `isaac-foundation` is pinned to the sha `isaac-agent` main pins, and `isaac-agent` to its main at planning time. Keep them coherent if you bump.
- Fill in the handbook chapter at `resources/isaac/contacts/handbook.md`: what a contact is, the file format, match order, what lands on the turn, and that an unmatched handle is left alone.
- Update the README's "What's here".

## Likely repo scope

`isaac-contacts`, plus one registry entry in `isaac` `modules.edn`.

## Acceptance

Run from `isaac-contacts`, with `@wip` removed from both feature files:

- `bb features features/contacts/identify.feature` — 5 examples, 0 failures.
- `bb features features/contacts/config.feature` — 1 example, 0 failures.
- `bb ci` green locally, and the `CI Tests` workflow green on `main` after landing.
- Unit specs cover the match order, case-insensitive email, a contact with no handles, and both duplicate cases (comm-and-id, email).
- `resources/isaac/contacts/handbook.md` documents the contact file, match order and the stored contact reference.
- `isaac` `modules.edn` gains an `:isaac.contacts` entry at the landed sha, with a one-line description.
- `git grep -i` in `isaac-contacts` for any real person, company or host name prints nothing beyond the LICENSE copyright.

feature-baseline: isaac-contacts d82cb4b321f6178735f16d2affa70d9220e3ff84
feature-blob: isaac-contacts features/contacts/identify.feature c3476a9fec3cd7d45383415c9755249e5f75b7e7
feature-blob: isaac-contacts features/contacts/config.feature f224d701e6b1462f098c21fe37e95db50e883547

## Checkpoint (2026-10-11)

Done: contacts schema, identifier, duplicate validation, specs, features, docs and module registry landed on main. `bb ci` passed in isaac-contacts and isaac; `bb bean-gate verify isaac-o7tm` passed after squash. Next: confirm CI Tests on isaac-contacts main, record main-sha lines below, delete bean branches, complete bean. Resume at `resources/isaac-manifest.edn:8` in isaac-contacts (identifier declaration), or run `gh run list -R slagyr/isaac-contacts --branch main --workflow ci-tests.yml --limit 3`.
