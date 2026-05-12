---
# isaac-4qgv
title: "Cron entity-file support: load cron jobs from config/cron/<name>.edn"
status: completed
type: feature
priority: low
created_at: 2026-04-22T23:12:25Z
updated_at: 2026-04-29T23:13:59Z
---

## Description

Today cron jobs live inline in isaac.edn under :cron {<name> {...}}. To match the :prefer-entity-files? true convention used by crew/models/providers, add entity-file loading for cron.

Shape:
- config/cron/<name>.edn contains the cron job definition: {:expr 'cron-str' :crew :id}
- Loader scans config/cron/*.edn at load time and merges each into the :cron map, keyed by filename
- Duplicate between inline (:cron {<name> ...}) and file (cron/<name>.edn) is an error — same shape as duplicate entity detection for crew/models/providers today
- Works with the prompt-md companion from isaac-02uu: cron/<name>.md provides the prompt

Enables :prefer-entity-files? convention for cron. Also required for isaac-ua1n (init scaffold creates cron/heartbeat.edn + .md).

Depends on isaac-xdlg (cron must exist) and isaac-02uu (prompt-md; both would ideally land together, but this can work independently).

