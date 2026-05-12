---
# isaac-dwk
title: "Fix --session with nonexistent key crashes"
status: completed
type: task
priority: normal
created_at: 2026-04-02T00:10:48Z
updated_at: 2026-04-04T00:47:11Z
---

## Description

--session agent:main:cli:direct:nobody crashes with FileNotFoundException because create-or-resume-session assumes the key exists in sessions.json.

## Bug
The session-key path in create-or-resume-session just returns the key without checking if it exists. When get-transcript tries to read it, sessionFile is nil and the path resolves to the sessions directory.

## Fix
In create-or-resume-session, when session-key is provided:
1. Check if the key exists in sessions.json
2. If yes, resume it
3. If no, create a new session with that key

## Feature File
features/chat/options.feature — "Session flag with nonexistent key creates new session"

