#!/bin/bash
set -uo pipefail

REPO=/Users/tony/git/grid
LOG=/tmp/find_first_failure_e836.log
RESULTS="$REPO/compile_results_e836.log"
ORIGINAL=$(git -C "$REPO" rev-parse HEAD)
ORIGINAL_BRANCH=$(git -C "$REPO" rev-parse --abbrev-ref HEAD)

cd "$REPO"

COMMITS=$(git log --reverse --format="%H %s" e83671af63fb6ce1c086fbbb3d7bca9d26a9004c^..HEAD)

echo "Started at $(date)" | tee "$RESULTS"
echo "Original branch: $ORIGINAL_BRANCH" | tee -a "$RESULTS"
echo "---" | tee -a "$RESULTS"

PASS=0
FAIL=0

while IFS= read -r line; do
  HASH=$(echo "$line" | cut -d' ' -f1)
  MSG=$(echo "$line" | cut -d' ' -f2-)
  SHORT="${HASH:0:9}"

  echo "Checking $SHORT: $MSG" | tee -a "$RESULTS"
  git checkout "$HASH" --quiet 2>/dev/null

  if /opt/homebrew/bin/sbt compile > "$LOG" 2>&1; then
    echo "  PASS: $SHORT" | tee -a "$RESULTS"
    PASS=$((PASS+1))
  else
    EXIT=$?
    echo "  FAIL: $SHORT (exit $EXIT)" | tee -a "$RESULTS"
    echo "" | tee -a "$RESULTS"
    echo "FIRST FAILING COMMIT: $SHORT" | tee -a "$RESULTS"
    echo "Subject: $MSG" | tee -a "$RESULTS"
    echo "" | tee -a "$RESULTS"
    echo "--- Last 50 lines of sbt output ---" | tee -a "$RESULTS"
    tail -50 "$LOG" | tee -a "$RESULTS"
    FAIL=$((FAIL+1))
    echo "Restoring: $ORIGINAL_BRANCH"
    git checkout "$ORIGINAL_BRANCH" --quiet 2>/dev/null || git checkout "$ORIGINAL" --quiet 2>/dev/null
    exit 0
  fi
done <<< "$COMMITS"

echo "" | tee -a "$RESULTS"
echo "All commits compiled OK. PASS=$PASS FAIL=$FAIL" | tee -a "$RESULTS"
git checkout "$ORIGINAL_BRANCH" --quiet 2>/dev/null || git checkout "$ORIGINAL" --quiet 2>/dev/null
