#!/usr/bin/env bash
set -euo pipefail

log_file=${1:-verify.log}

short_sha=$(git rev-parse --short "${GITHUB_SHA:-HEAD}")
full_sha=$(git rev-parse "${GITHUB_SHA:-HEAD}")
author_name=$(git show -s --format='%an' "$full_sha")
author_email=$(git show -s --format='%ae' "$full_sha")
assignee=${author_name:-${GITHUB_ACTOR:-unknown}}
branch=${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD)}
repo=${GITHUB_REPOSITORY:-unknown}
run_url="${GITHUB_SERVER_URL:-https://github.com}/${repo}/actions/runs/${GITHUB_RUN_ID:-unknown}"

python_output=$(python3 - "$log_file" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_text() if path.exists() else ""
text = re.sub(r"\x1b\[[0-9;]*[A-Za-z]", "", text)
lines = [line.rstrip() for line in text.splitlines()]
nonempty = [line for line in lines if line.strip()]
summary = None
for line in reversed(nonempty):
    if "examples," in line and "failures" in line:
        summary = line.strip()
        break
if summary is None:
    for line in reversed(nonempty):
        if "Error while executing task" in line:
            summary = line.strip()
            break
if summary is None:
    summary = nonempty[-1].strip() if nonempty else "bb verify failed"
excerpt = "\n".join(lines[-120:]).strip()
if len(excerpt) > 12000:
    excerpt = excerpt[-12000:]
print(summary)
print("---")
print(excerpt)
PY
)

summary=${python_output%%$'\n---\n'*}
excerpt=${python_output#*$'\n---\n'}

title_summary=$(printf '%s' "$summary" | tr '\n' ' ' | cut -c1-180)
description_file=$(mktemp)

cat > "$description_file" <<EOF
CI verification failed on push to main.

- Commit: $full_sha
- Short SHA: $short_sha
- Branch: $branch
- Repository: $repo
- GitHub actor: ${GITHUB_ACTOR:-unknown}
- Commit author: $author_name <$author_email>
- Run: $run_url

Summary:
$summary

Failure excerpt:
EOF
printf '```text\n%s\n```\n' "$excerpt" >> "$description_file"

create_args=(
  create
  --type bug
  --priority 1
  --assignee "$assignee"
  --title "CI red on ${short_sha}: ${title_summary}"
  --body-file "$description_file"
)

if [[ "${BD_CREATE_DRY_RUN:-0}" == "1" ]]; then
  bd "${create_args[@]}" --dry-run
else
  bd "${create_args[@]}"
  bd dolt push
fi
