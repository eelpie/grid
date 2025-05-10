#!/bin/bash
set -euo pipefail

START_COMMIT="c0899959abcac6a1d597c900238a7ab8d6c447ff"
LOG_FILE="step_commits_$(date +%Y%m%d_%H%M%S).log"
ORIGINAL_HEAD=$(git rev-parse HEAD)

echo "Starting commit-by-commit compile check" | tee "$LOG_FILE"
echo "Starting from: $START_COMMIT" | tee -a "$LOG_FILE"
echo "Current HEAD: $ORIGINAL_HEAD" | tee -a "$LOG_FILE"
echo "Log: $LOG_FILE" | tee -a "$LOG_FILE"
echo "---" | tee -a "$LOG_FILE"

# Get commits oldest-first
COMMITS=$(git log --format="%H %s" --reverse "${START_COMMIT}..HEAD")

TOTAL=$(echo "$COMMITS" | wc -l | tr -d ' ')
COUNT=0

# Trap to restore HEAD on exit
cleanup() {
  echo "" | tee -a "$LOG_FILE"
  echo "Restoring HEAD to $ORIGINAL_HEAD" | tee -a "$LOG_FILE"
  git checkout "$ORIGINAL_HEAD" --quiet 2>/dev/null || true
  git checkout multi-tenant --quiet 2>/dev/null || true
}
trap cleanup EXIT INT TERM

while IFS= read -r line; do
  HASH=$(echo "$line" | awk '{print $1}')
  MSG=$(echo "$line" | cut -d' ' -f2-)
  COUNT=$((COUNT + 1))

  echo "" | tee -a "$LOG_FILE"
  echo "[$COUNT/$TOTAL] Checking out $HASH: $MSG" | tee -a "$LOG_FILE"

  git checkout "$HASH" --quiet

  echo "  Running sbt compile..." | tee -a "$LOG_FILE"
  if sbt compile >> "$LOG_FILE" 2>&1; then
    echo "  ✓ PASSED" | tee -a "$LOG_FILE"
  else
    echo "" | tee -a "$LOG_FILE"
    echo "  ✗ FAILED at commit $COUNT/$TOTAL" | tee -a "$LOG_FILE"
    echo "  Hash:    $HASH" | tee -a "$LOG_FILE"
    echo "  Message: $MSG" | tee -a "$LOG_FILE"
    echo "" | tee -a "$LOG_FILE"
    echo "FIRST FAILING COMMIT: $HASH ($MSG)" | tee -a "$LOG_FILE"
    exit 1
  fi
done <<< "$COMMITS"

echo "" | tee -a "$LOG_FILE"
echo "All $TOTAL commits compiled successfully." | tee -a "$LOG_FILE"
