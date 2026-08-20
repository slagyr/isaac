---
# isaac-l1kz
title: Cosine match floor + recency default 0.5 (checkpoint verdicts)
status: todo
type: task
created_at: 2026-08-20T20:40:32Z
updated_at: 2026-08-20T20:40:32Z
parent: isaac-51xy
---

Implements CHECKPOINT verdicts 1-2 from isaac-51xy (2026-08-20). Floor: match iff max(text,gist) cosine >= :recall {:floor-cos} (default 0.47) OR lex >= 0.5; z-score floor retired (EVT-blind at corpus scale, cos floor covers small-n too). Warning becomes 'weak matches — nothing stands out (best cos 0.41)'. Flag renamed --floor-cos; 0 disables; precedence defaults -> config -> flag unchanged. Recency default weight 0.5 parts (was 1), half-life 30d unchanged. Clean cutover: --floor flag and z machinery removed, the two isaac-74ls floor scenarios rewritten (grover fixtures use --floor-cos 0.999 overrides since grover cosines live at 0.9+). Spec obligations: default-weights map asserts recency 0.5; floor evaluation on raw cosines independent of channel weights.
