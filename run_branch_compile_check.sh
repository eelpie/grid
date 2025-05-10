#!/bin/bash
# Step through every commit in libvips branch (not in main) oldest-first,
# run sbt compile, stop at first failure.

ORIGINAL_HEAD=$(git rev-parse HEAD)
LOG_FILE="/Users/tony/git/grid/branch_compile_check_result.txt"

echo "Branch compile check started at $(date)" | tee "$LOG_FILE"
echo "Original HEAD: $ORIGINAL_HEAD" | tee -a "$LOG_FILE"
echo "========================================" | tee -a "$LOG_FILE"

# Commits oldest-first that are in branch but not in main
COMMITS=$(git log main..HEAD --oneline --reverse | awk '{print $1}')
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
echo "Completed at $(date)" | tee -a "$LOG_FILE"
git checkout --quiet "$ORIGINAL_HEAD"
