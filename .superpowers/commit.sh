#!/bin/bash
# usage: commit.sh "message" [paths...]
msg="$1"; shift
if [ $# -eq 0 ]; then git add -A; else git add "$@"; fi
git commit -q -m "$msg

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01XPVTeWth8yyKhzWQgi6z18" && git log --oneline -1
