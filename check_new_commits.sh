#!/bin/bash

# Check that commits 78-end compile with sbt compile
# Picks up where compile_all_commits.log left off (77 commits checked)

ORIGINAL_BRANCH=$(git symbolic-ref --short HEAD)
LOG_FILE="compile_new_commits.log"
FAIL_FILE="compile_new_failures.log"

> "$LOG_FILE"
> "$FAIL_FILE"

# Get all commits in reverse order (oldest first), skip the first 77 already checked
ALL_COMMITS=$(git log --oneline --reverse main..HEAD | awk '{print $1}')
TOTAL_ALL=$(echo "$ALL_COMMITS" | wc -l | tr -d ' ')
SKIP=77

COMMITS=$(echo "$ALL_COMMITS" | tail -n +$((SKIP + 1)))
TOTAL=$(echo "$COMMITS" | wc -l | tr -d ' ')
COUNT=0

echo "Total commits in branch: $TOTAL_ALL"
echo "Already checked: $SKIP"
echo "Checking remaining $TOTAL commits (commits $((SKIP+1)) to $TOTAL_ALL)..."
echo "Total commits in branch: $TOTAL_ALL" >> "$LOG_FILE"
echo "Already checked: $SKIP" >> "$LOG_FILE"
echo "Checking remaining $TOTAL commits..." >> "$LOG_FILE"

for COMMIT in $COMMITS; do
  COUNT=$((COUNT + 1))
  OVERALL=$((SKIP + COUNT))
  MSG=$(git log --oneline -1 "$COMMIT")
  echo ""
  echo "[$OVERALL/$TOTAL_ALL] Checking: $MSG"
  echo "" >> "$LOG_FILE"
  echo "[$OVERALL/$TOTAL_ALL] $MSG" >> "$LOG_FILE"

  git checkout -q "$COMMIT"

  # Run sbt compile and capture result
  if sbt compile >> "$LOG_FILE" 2>&1; then
    echo "  RESULT: PASS"
    echo "  RESULT: PASS" >> "$LOG_FILE"
  else
    echo "  *** RESULT: FAIL *** $MSG"
    echo "  RESULT: FAIL" >> "$LOG_FILE"
    echo "$MSG" >> "$FAIL_FILE"
  fi
done

echo ""
echo "Restoring branch: $ORIGINAL_BRANCH"
git checkout -q "$ORIGINAL_BRANCH"

echo ""
echo "Done. $COUNT new commits checked (commits $((SKIP+1)) to $TOTAL_ALL)."
echo "Failures logged in: $FAIL_FILE"

if [ -s "$FAIL_FILE" ]; then
  echo ""
  echo "FAILED COMMITS:"
  cat "$FAIL_FILE"
else
  echo "All $COUNT commits compiled successfully!"
fi
