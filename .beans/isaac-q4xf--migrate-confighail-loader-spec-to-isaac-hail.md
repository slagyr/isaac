---
# isaac-q4xf
title: Migrate config/hail_loader_spec to isaac-hail
status: draft
type: task
priority: normal
created_at: 2026-06-15T16:20:01Z
updated_at: 2026-06-15T16:20:01Z
---

Restore the dropped isaac.config.hail-loader-spec (3 examples).

Baseline: isaac/spec/isaac/config/hail_loader_spec.clj @ 09795481 (ns isaac.config.hail-loader-spec, 3 it):
  - loads hail band files into config
  - loads hail markdown companions as prompts
  - rejects hail bands without any addressing fields
Likely target: isaac-hail.

OPEN (blocks promotion to todo): there is no src/isaac/config/hail_loader.clj — confirm WHERE hail
config-loading lives now (isaac-hail bands/router vs. foundation generic loader applied to a hail
entity-dir). Place the spec with the owner of that behavior; if it's generic foundation loading of a
hail-contributed fragment, it may belong in isaac-hail testing its own fragment (cf. schema_spec decomposition).

Acceptance (gate, once target confirmed): file(it)==executed, 0 failures, zero '(it) inside (it)',
3 examples faithful, conforms/loads against the REAL hail fragment (no inlined fakes).
