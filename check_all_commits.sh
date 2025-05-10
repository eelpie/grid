#!/bin/bash

# Check that every commit compiles with sbt compile
# Checks all commits on current branch not in main

ORIGINAL_BRANCH=$(git symbolic-ref --short HEAD)
LOG_FILE="compile_all_commits.log"
FAIL_FILE="compile_failures.log"

> "$LOG_FILE"
> "$FAIL_FILE"

COMMITS=$(git log --oneline --reverse main..HEAD | awk '{print $1}')
TOTAL=$(echo "$COMMITS" | wc -l | tr -d ' ')
COUNT=0

echo "Checking $TOTAL commits for compilation..."
echo "Checking $TOTAL commits for compilation..." >> "$LOG_FILE"

for COMMIT in $COMMITS; do
  COUNT=$((COUNT + 1))
  MSG=$(git log --oneline -1 "$COMMIT")
  echo ""
  echo "[$COUNT/$TOTAL] Checking: $MSG"
  echo "" >> "$LOG_FILE"
  echo "[$COUNT/$TOTAL] $MSG" >> "$LOG_FILE"

  git checkout -q "$COMMIT"

  # Run sbt compile and capture result
  if sbt compile >> "$LOG_FILE" 2>&1; then
    echo "  PASS"
    echo "  RESULT: PASS" >> "$LOG_FILE"
  else
    echo "  FAIL *** $MSG"
    echo "  RESULT: FAIL" >> "$LOG_FILE"
    echo "$MSG" >> "$FAIL_FILE"
  fi
done

echo ""
echo "Restoring branch: $ORIGINAL_BRANCH"
git checkout -q "$ORIGINAL_BRANCH"

echo ""
echo "Done. $COUNT commits checked."
echo "Failures logged in: $FAIL_FILE"

if [ -s "$FAIL_FILE" ]; then
  echo ""
  echo "FAILED COMMITS:"
  cat "$FAIL_FILE"
else
  echo "All commits compiled successfully!"
fi
