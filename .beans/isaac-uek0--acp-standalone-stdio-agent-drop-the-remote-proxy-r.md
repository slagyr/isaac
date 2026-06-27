---
# isaac-uek0
title: 'ACP: standalone stdio agent; drop the --remote proxy (replaced by remote-cli)'
status: draft
type: feature
created_at: 2026-06-27T16:01:15Z
updated_at: 2026-06-27T16:01:15Z
parent: isaac-ec9q
---

Now that remote-CLI (isaac-ec9q) carries acp over the generic /cli channel (isaac remote .../cli acp), remove acp's bespoke proxy: the --remote/-r/-t flags + the ACP websocket CLIENT code. acp becomes a pure LOCAL stdio agent. The /acp server route may stay or be reconsidered separately.

Pairs with the xkc9 repurpose (acp accepting the frequencies CLI args). This bean is the proxy-removal cleanup that ec9q enables.
