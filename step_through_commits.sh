#!/usr/bin/env bash
set -euo pipefail

START_COMMIT="655d096861c05cb7bd810257e0af64aac7d771bc"
LOG_FILE="$(pwd)/step_through_commits.log"
RESULT_FILE="$(pwd)/step_through_result.txt"

# Get commits from start to HEAD, oldest first
COMMITS=$(git log --reverse --format="%H %s" "${START_COMMIT}..HEAD")

if [ -z "$COMMITS" ]; then
  echo "No commits found from ${START_COMMIT} to HEAD" | tee -a "$LOG_FILE"
  exit 1
fi

TOTAL=$(echo "$COMMITS" | wc -l | tr -d ' ')
echo "Starting compile check of $TOTAL commits from ${START_COMMIT}" | tee "$LOG_FILE"
echo "Started at: $(date)" | tee -a "$LOG_FILE"
echo "---" | tee -a "$LOG_FILE"

ORIGINAL_BRANCH=$(git rev-parse --abbrev-ref HEAD)
COUNT=0

# Trap to restore branch on exit
trap 'echo "Restoring to $ORIGINAL_BRANCH..."; git checkout "$ORIGINAL_BRANCH" 2>/dev/null || true' EXIT

while IFS= read -r line; do
  HASH=$(echo "$line" | awk '{print $1}')
  MSG=$(echo "$line" | cut -d' ' -f2-)
  COUNT=$((COUNT + 1))

  echo "[$COUNT/$TOTAL] Checking out $HASH: $MSG" | tee -a "$LOG_FILE"
  git checkout --quiet "$HASH" 2>>"$LOG_FILE"

  echo "  Running sbt compile..." | tee -a "$LOG_FILE"
  if sbt compile >> "$LOG_FILE" 2>&1; then
    echo "  ✓ PASS" | tee -a "$LOG_FILE"
  else
    echo "  ✗ FAIL" | tee -a "$LOG_FILE"
    echo "" | tee -a "$LOG_FILE"
    echo "=== FIRST FAILING COMMIT ===" | tee -a "$LOG_FILE" "$RESULT_FILE"
    echo "Commit $COUNT of $TOTAL" | tee -a "$LOG_FILE" "$RESULT_FILE"
    echo "Hash:    $HASH" | tee -a "$LOG_FILE" "$RESULT_FILE"
    echo "Message: $MSG" | tee -a "$LOG_FILE" "$RESULT_FILE"
    echo "Time:    $(date)" | tee -a "$LOG_FILE" "$RESULT_FILE"
    echo "" | tee -a "$LOG_FILE" "$RESULT_FILE"
    echo "Previous commit (last passing):" | tee -a "$LOG_FILE" "$RESULT_FILE"
    git log --oneline -2 | tail -1 | tee -a "$LOG_FILE" "$RESULT_FILE"
    exit 1
  fi
  echo "" | tee -a "$LOG_FILE"
done <<< "$COMMITS"

echo "=== ALL $TOTAL COMMITS PASSED ===" | tee -a "$LOG_FILE" "$RESULT_FILE"
echo "Completed at: $(date)" | tee -a "$LOG_FILE" "$RESULT_FILE"
