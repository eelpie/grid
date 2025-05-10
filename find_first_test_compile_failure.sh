#!/bin/bash
set -euo pipefail

REPO=/Users/tony/git/grid
RESULTS="$REPO/test_compile_results.log"
CURRENT_BRANCH=$(git -C "$REPO" rev-parse --abbrev-ref HEAD)
START_COMMIT=$(git -C "$REPO" merge-base main containerised)

cd "$REPO"

echo "Test compile check - $(date)" > "$RESULTS"
echo "Starting from: $START_COMMIT" >> "$RESULTS"
echo "---" >> "$RESULTS"

FIRST_FAIL=""

COMMITS=$(git log --reverse --format="%H %s" "${START_COMMIT}..HEAD")

while IFS= read -r line; do
  HASH=$(echo "$line" | cut -d' ' -f1)
  MSG=$(echo "$line" | cut -d' ' -f2-)
  SHORT="${HASH:0:9}"

  echo -n "Checking $SHORT $MSG ... "
  git checkout "$HASH" --quiet 2>/dev/null

  if /opt/homebrew/bin/sbt "Test/compile" > "/tmp/sbt_test_${SHORT}.log" 2>&1; then
    echo "PASS"
    echo "PASS: $SHORT $MSG" >> "$RESULTS"
  else
    EXIT_CODE=$?
    echo "FAIL (exit $EXIT_CODE)"
    echo "FAIL($EXIT_CODE): $SHORT $MSG" >> "$RESULTS"
    if [ -z "$FIRST_FAIL" ]; then
      FIRST_FAIL="$SHORT $MSG"
      echo ""
      echo ">>> FIRST FAILURE FOUND: $SHORT"
      echo ">>> $MSG"
      echo ">>> Log: /tmp/sbt_test_${SHORT}.log"
      # Restore branch before exiting
      git checkout "$CURRENT_BRANCH" --quiet
      echo "$FIRST_FAIL" >> "$RESULTS"
      echo "First failure: $FIRST_FAIL" >> "$RESULTS"
      exit 0
    fi
  fi
done <<< "$COMMITS"

echo ""
echo "Restoring branch: $CURRENT_BRANCH"
git checkout "$CURRENT_BRANCH" --quiet

if [ -n "$FIRST_FAIL" ]; then
  echo "First failure: $FIRST_FAIL" >> "$RESULTS"
  echo "First failure: $FIRST_FAIL"
else
  echo "No test compile failures found." | tee -a "$RESULTS"
fi
