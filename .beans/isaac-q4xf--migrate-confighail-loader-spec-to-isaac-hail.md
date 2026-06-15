---
# isaac-q4xf
title: Migrate config/hail_loader_spec to isaac-hail
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-15T16:20:01Z
updated_at: 2026-06-15T16:35:50Z
---

Restore the dropped isaac.config.hail-loader-spec (3 examples) into isaac-hail.

Baseline: isaac/spec/isaac/config/hail_loader_spec.clj @ 09795481 (ns isaac.config.hail-loader-spec, 3 it):
  - loads hail band files into config
  - loads hail markdown companions as prompts
  - rejects hail bands without any addressing fields

TARGET (confirmed): isaac-hail. Its resources/isaac-manifest.edn owns the whole :hail fragment —
entity-dir "hail", :prompt companion (:mode :optional), and addressing validation
[[:requires-any? :crew :crew-tags :session :session-tags]].

APPROACH (decided): FRAGMENT-ONLY conformance — mirror the schema_spec decomposition. Do NOT rebuild an
end-to-end loader harness; foundation's loader_spec already covers generic entity-dir discovery + markdown
companion loading. Test only what is hail-specific and currently uncovered:
  - slurp the REAL :hail fragment from isaac-hail's manifest (get-in [:isaac.config/schema :hail :schema]);
    do NOT inline a copy of the schema.
  - conform a valid band -> asserts it conforms (crew-tags / reach shape, :prompt companion field).
  - conform a band with NO addressing fields -> asserts the requires-any? addressing rejection (error).

Intentionally dropped: the literal end-to-end "load-config-result picks up config/hail/*.edn" assertion —
that generic loader path is already proven in isaac-foundation/spec/isaac/config/loader_spec.clj.

Acceptance (gate): bb spec -> file (it)==executed (no dead examples), 0 failures, SCRAP structure-errors
zero '(it) inside (it)', conforms against the REAL hail manifest fragment (no inlined fakes).
