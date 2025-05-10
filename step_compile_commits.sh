#!/bin/bash
set -e

START_COMMIT="e8b3124b29471955dc267e1a6fabea88da701e68"
ORIGINAL_HEAD=$(git rev-parse HEAD)
LOG_FILE="/Users/tony/git/grid/step_compile_commits_result.txt"

echo "Starting compile check from $START_COMMIT" | tee "$LOG_FILE"
echo "Original HEAD: $ORIGINAL_HEAD" | tee -a "$LOG_FILE"
echo "========================================" | tee -a "$LOG_FILE"

# Get commits in chronological order (oldest first), including the start commit
COMMITS=$(git log --oneline --reverse "${START_COMMIT}^..HEAD" | awk '{print $1}')

TOTAL=$(echo "$COMMITS" | wc -l | tr -d ' ')
echo "Total commits to check: $TOTAL" | tee -a "$LOG_FILE"
echo "" | tee -a "$LOG_FILE"

COUNT=0
for COMMIT in $COMMITS; do
  COUNT=$((COUNT + 1))
  SUBJECT=$(git log --oneline -1 "$COMMIT")
  echo "[$COUNT/$TOTAL] Checking: $SUBJECT" | tee -a "$LOG_FILE"

  git checkout --quiet "$COMMIT"

  if sbt compile >> "$LOG_FILE" 2>&1; then
    echo "  ✅ PASS" | tee -a "$LOG_FILE"
  else
    echo "  ❌ FAIL" | tee -a "$LOG_FILE"
    echo "" | tee -a "$LOG_FILE"
    echo "========================================" | tee -a "$LOG_FILE"
    echo "FIRST FAILURE at commit $COUNT/$TOTAL:" | tee -a "$LOG_FILE"
    echo "  $SUBJECT" | tee -a "$LOG_FILE"
    echo "========================================" | tee -a "$LOG_FILE"
    echo "Restoring to original HEAD..." | tee -a "$LOG_FILE"
    git checkout --quiet "$ORIGINAL_HEAD"
    exit 1
  fi
  echo "" | tee -a "$LOG_FILE"
done

echo "========================================" | tee -a "$LOG_FILE"
echo "All $TOTAL commits compiled successfully!" | tee -a "$LOG_FILE"
git checkout --quiet "$ORIGINAL_HEAD"
