
## Work (scrapper@isaac-work-3, 2026-09-25)

isaac-gchat branch `bean/isaac-mw27` @ ed8fcd4 (not gated — gate exit 2).

- `on-tool-call*` marks the session replied-via-tool when the call is
  `gchat__send` and its space+thread equal the origin's (map or JSON-string
  args); `on-reply*` then posts nothing, logs `:gchat/reply-deduped` at debug,
  still sets ✅. `on-turn-end*` clears the mark. Invited-DM divert unchanged.
- Guidance: answer text is delivered to the addressed thread automatically;
  gchat__send is for other threads or spaces.
- Specs: 6 new in gchat_spec (dedupe, JSON args, no-tool, other thread, other
  space, mark cleared), 1 in guidance_spec. Features: 2 new outbound scenarios
  (DD1 origin-thread → 1 post, DD2 other thread → 2 posts); DD1 fails with the
  src change stashed.
- Version 0.2.13 → 0.2.14. bb ci green (180 specs, 56 features).
- bb lint: 74 errors / 15 warnings, same as main before this change — all
  speclj `:refer :all` unresolved-symbol noise in specs; src is clean. Lint
  was already red on main before this bean.
