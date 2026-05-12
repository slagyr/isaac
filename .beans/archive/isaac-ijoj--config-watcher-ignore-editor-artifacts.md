---
# isaac-ijoj
title: "Config watcher: ignore editor artifacts"
status: completed
type: bug
priority: normal
created_at: 2026-04-28T01:11:31Z
updated_at: 2026-04-28T02:55:24Z
---

## Description

The config file watcher (src/isaac/config/change_source_watch.clj) enqueues
every file event in the config tree. Editor save artifacts get fed to
config validation, producing noisy reload-failed logs:

  :path "crew/.main.edn.swp"   :reason :validation
  :path "crew/.marvin.edn.swp" :reason :validation
  :path "crew/marvin.edn~"     :reason :validation

Patterns to drop at enqueue time (in change_source_watch.clj and the bb
equivalent change_source_bb.clj):

  - *.swp / *.swo / *.swx     (vim swap)
  - *~                        (vim/nano backup)
  - .#*                       (emacs lockfile)
  - #*#                       (emacs autosave)
  - 4913 and similar bare-numeric atomic-rename names

Also worth considering: only enqueue paths matching
  (crew|models|providers|hooks)/<name>.edn
since unknown subpaths produce useless noise too. But err on the side of
allow-list rather than deny-list — easier to extend than to prune.

## Definition of done

- Saving a file in vim/emacs produces exactly one reload event for
  the real file, no events for swap/backup/temp artifacts
- New unit spec in spec/isaac/config/change_source_watch_spec.clj
  covering the filter
- bb features still green

