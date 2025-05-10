#!/usr/bin/env bash
set -euo pipefail

LOGFILE="/Users/tony/git/grid/compile_check2.log"
ORIGINAL_BRANCH=$(git symbolic-ref --short HEAD 2>/dev/null || git rev-parse HEAD)

echo "Starting compile check (continued) at $(date)" | tee "$LOGFILE"
echo "Original branch: $ORIGINAL_BRANCH" | tee -a "$LOGFILE"
echo "---" | tee -a "$LOGFILE"

cleanup() {
  echo "Restoring branch: $ORIGINAL_BRANCH" | tee -a "$LOGFILE"
  git checkout "$ORIGINAL_BRANCH" 2>/dev/null
}
trap cleanup EXIT

# Start from the commit after the known failing one
COMMITS=$(git log main..multi-tenant --oneline --reverse | awk '{print $1}' | grep -A 10000 "61b01865a" | tail -n +2)

while IFS= read -r HASH; do
  SUBJECT=$(git log -1 --pretty=format:"%s" "$HASH")
  echo "Checking $HASH: $SUBJECT" | tee -a "$LOGFILE"
  git checkout "$HASH" --quiet 2>>"$LOGFILE"
  if sbt compile >> "$LOGFILE" 2>&1; then
    echo "  PASS: $HASH" | tee -a "$LOGFILE"
  else
    echo "  FAIL: $HASH" | tee -a "$LOGFILE"
    echo "FIRST FAILING COMMIT: $HASH ($SUBJECT)" | tee -a "$LOGFILE"
    exit 1
  fi
done <<< "$COMMITS"

echo "All remaining commits compiled successfully." | tee -a "$LOGFILE"
